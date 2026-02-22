package xim.resource

import xim.math.Matrix3f
import xim.math.Matrix4f
import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.*
import xim.poc.gl.ByteColor
import xim.poc.gl.GlBufferBuilder
import xim.poc.gl.MeshBuffer
import xim.resource.table.ZoneDecrypt
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

typealias ZoneObjId = Int

data class TriFlags(val type: Int) {

    val hitWall = (type and 0x40) != 0

}

enum class TerrainType(val index: Int, val hasFootMark: Boolean, val debugMeshColor: ByteColor) {
    Object(0, hasFootMark = false, debugMeshColor = ByteColor(r = 0x00, g = 0x00, b = 0x00, a = 0x40)),
    Path(1, hasFootMark = false, debugMeshColor = ByteColor(r = 0x40, g = 0x80, b = 0x40, a = 0x40) ),
    Grass(2, hasFootMark = false, debugMeshColor = ByteColor(r = 0x00, g = 0x80, b = 0x00, a = 0x40)),
    Sand(3, hasFootMark = true, debugMeshColor = ByteColor(r = 0x60, g = 0x60, b = 0x00, a = 0x40)),
    Snow(4, hasFootMark = true, debugMeshColor = ByteColor(r = 0x70, g = 0x70, b = 0x70, a = 0x40)),
    Stone(5, hasFootMark = false, debugMeshColor = ByteColor(r = 0x30, g = 0x30, b = 0x30, a = 0x40)),
    Metal(6, hasFootMark = false, debugMeshColor = ByteColor(r = 0x80, g = 0x30, b = 0x20, a = 0x40)),
    Wood(7, hasFootMark = false, debugMeshColor = ByteColor(r = 0x80, g = 0x60, b = 0x30, a = 0x40)),
    ShallowWater(8, hasFootMark = false, debugMeshColor = ByteColor(r = 0x30, g = 0x60, b = 0x80, a = 0x40)),
    DeepWater(9, hasFootMark = false, debugMeshColor = ByteColor(r = 0x00, g = 0x00, b = 0x80, a = 0x40)),
    Unk0xA(10, hasFootMark = false, debugMeshColor = ByteColor(r = 0x80, g = 0x00, b = 0x80, a = 0x40))
    ;

    companion object {
        fun fromFlags(f0: Int, f1: Int, f2: Int, f3: Int): TerrainType {
            val index = (f0 and 0x8 ushr 3) + (f1 and 0x8 ushr 2) + (f2 and 0x8 ushr 1) + (f3 and 0x8)
            return values().firstOrNull { it.index == index }
                ?: throw IllegalStateException("Unknown terrain-type: $index")
        }
    }

    fun toFootEffectId(): DatId {
        val char = index.toString(0x10)
        return DatId("0${char}00")
    }

}

data class CollisionMesh (
    val fileOffset: Int,
    val meshBuffer: MeshBuffer,
    val tris: ArrayList<Triangle>,
    val boundingSphere: Sphere,
)

data class CollisionObject (
    val fileOffset: Int,
    val collisionMesh: CollisionMesh,
    val transformInfo: CollisionTransformInfo,
) {

    val worldSpaceTriangle by lazy {
        val inv = Matrix3f.truncate(transformInfo.toCollisionSpace)
        val invTranspose = inv.transpose()
        collisionMesh.tris.map { it.transform(this, transformInfo.toWorldSpace, invTranspose) }
    }

    val worldSpaceBoundingSphere by lazy {
        val extentsTracker = ExtentsBuilder()
        worldSpaceTriangle.forEach { it.vertices.forEach(extentsTracker::track) }
        extentsTracker.toBoundingSphere()
    }

}

data class CollisionObjectGroup(
    val fileOffset: Int,
    val collisionObjects: ArrayList<CollisionObject>
)

data class CollisionTransformInfo (
    val fileOffset: Int,
    val toWorldSpace: Matrix4f,
    val toCollisionSpace: Matrix4f,
    val cullingTableIndex: Int?,
    val lightIndices: List<Int>,
    val environmentId: DatId?,
    val miscFlags: Int,
    val subAreaLinkId: Int?,
    val mapId: Int,
)

class CollisionMap (
    val numBlocksWide: Int,
    val numBlocksLong: Int,
    val blockWidth: Int,
    val blockLength: Int,
    val subBlocksX: Int,
    val subBlocksZ: Int,
    val collisionEntries: Array<Array<CollisionObjectGroup?>>
) {

    fun getCollisionObjects(position: Vector3f, maxSteps: Int = 1) : ArrayList<CollisionObjectGroup> {
        val (xBlockIndex, zBlockIndex) = positionToBlock(position)
        return getCollisionObjects(xBlockIndex, zBlockIndex, maxSteps)
    }

    private fun getCollisionObjects(xBlockIndex: Int, zBlockIndex: Int, maxSteps: Int): ArrayList<CollisionObjectGroup> {
        val sumList = ArrayList<CollisionObjectGroup>()
        for (x in -maxSteps..maxSteps) {
            for (z in -maxSteps .. maxSteps) {
                val group = getCollisionObjects(xBlockIndex + x, zBlockIndex + z) ?: continue
                sumList.add(group)
            }
        }

        return sumList
    }

    fun positionToBlock(position: Vector3f): Pair<Int, Int> {
        val halfMapWidth = blockWidth * numBlocksWide / 2
        val positiveX = position.x + halfMapWidth
        val xBlockIndex = (positiveX / (blockWidth / subBlocksX)).toInt()

        val halfMapLength = blockLength * numBlocksLong / 2
        val positiveZ = position.z + halfMapLength
        val zBlockIndex = (positiveZ / (blockLength / subBlocksZ)).toInt()

        return xBlockIndex to zBlockIndex
    }

    fun blockToBounds(xBlockIndex: Int, zBlockIndex: Int): Pair<Vector3f, Vector3f> {
        val halfMapWidth = blockWidth * numBlocksWide / 2f
        val positiveX = xBlockIndex * (blockWidth / subBlocksX)
        val positionX = positiveX - halfMapWidth

        val halfMapLength = blockLength * numBlocksLong / 2f
        val positiveZ = zBlockIndex * (blockLength / subBlocksZ)
        val positionZ = positiveZ - halfMapLength

        return Vector3f(positionX, 0f, positionZ) to Vector3f(positionX + blockWidth.toFloat()/subBlocksX, 0f, positionZ + blockLength.toFloat()/subBlocksZ)
    }

    fun getCollisionObjects(xBlock: Int, zBlock: Int): CollisionObjectGroup? {
        if (xBlock < 0 || xBlock >= numBlocksWide * subBlocksX) {
            return null
        }

        if (zBlock < 0 || zBlock >= numBlocksLong * subBlocksZ) {
            return null
        }

        return collisionEntries[zBlock][xBlock]
    }
}

data class ZoneObject(
    val index: ZoneObjId,
    val id: String,
    val fileOffset: Int,
    val position: Vector3f,
    val rotation: Vector3f,
    val scale: Vector3f,
    val highDefThreshold: Float,
    val midDefThreshold: Float,
    val lowDefThreshold: Float,
    val pointLightIndex: List<Int>,
    val skipDuringDecalRendering: Boolean,
    val cullingTableIndex: Int?,
    val effectLink: DatLink<DatResource>?,
    val environmentLink: DatId?,
    val fileIdLink: Int?,
) {

    private val hasLevelOfDetail = ZoneObjectLevelOfDetail.hasLevelOfDetail(this)
    private lateinit var levelOfDetail: ZoneObjectLevelOfDetail

    private var boundingBox: BoundingBox? = null

    fun resolveMesh(distance: Float, localDir: DirectoryResource) : ZoneMeshResource? {
        if (!hasLevelOfDetail) { return localDir.getZoneMeshResourceByNameAs(id) }

        if (!this::levelOfDetail.isInitialized) {
            levelOfDetail = ZoneObjectLevelOfDetail(this, localDir)
        }

        return levelOfDetail.getResource(distance)
    }

    fun getPrecomputedBoundingBox(): BoundingBox? {
        return boundingBox
    }

    fun getBoundingBox(meshBoundingBox: ZoneMeshSection.BoundingBox): BoundingBox {
        val current = boundingBox
        if (current != null) { return current }

        val transform = Matrix4f().translateInPlace(position).rotateZYXInPlace(rotation).scaleInPlace(scale)
        val skewBox = BoundingBox.skewed(meshBoundingBox.p0, meshBoundingBox.p1)
        boundingBox = skewBox.transform(transform)
        return boundingBox!!
    }

}

class SpacePartitioningNode (
    val leafNode: Boolean,
    val containedObjects: Set<ZoneObjId>,
    val boundingBox: AxisAlignedBoundingBox,
    val children: List<SpacePartitioningNode?>
)

class ZoneDefSection(private val sectionHeader: SectionHeader) : ResourceParser {

    // For collision detection, zones are segmented into a grid of rectangles (usually squares)
    var zoneBlocksX: Int = 0
    var zoneBlocksZ: Int = 0

    // Each node in the grid has a world-space size (width x length). Height is ignored for the grid.
    var blockWidth: Int = 0
    var blockLength: Int = 0

    // Each node is divided into a sub-grid, based on its world-space size.
    // These sub-nodes are always (5 x 5) in world-space units
    var subBlocksX: Int = 0
    var subBlocksZ: Int = 0

    private val collisionGroups = ArrayList<CollisionObjectGroup>()
    private val collisionGroupsByIndex = HashMap<Int, Int>()

    private val collisionMeshesByOffset = HashMap<Int, CollisionMesh>()
    private val transformsByOffset = HashMap<Int, CollisionTransformInfo>()

    private val cullingTables = ArrayList<HashSet<ZoneObjId>>()
    private val cullingTableIndicesByOffset = HashMap<Int, Int>()

    private var collisionMap: CollisionMap? = null

    override fun getResource(byteReader: ByteReader): ParserResult {
        ZoneDecrypt.decryptZoneObjects(sectionHeader, byteReader)
        val zoneResource = read(byteReader)
        return ParserResult.from(zoneResource)
    }

    private fun read(byteReader: ByteReader): ZoneResource {
        byteReader.offsetFromDataStart(sectionHeader, 0x0)

        // Header section
        val decryptInfo = byteReader.next32()
        val keyNodeData = byteReader.next32()
        val nodeCount = keyNodeData and 0x00FFFFFF

        val collisionMeshOffset = byteReader.next32()

        zoneBlocksX = byteReader.next8()
        zoneBlocksZ = byteReader.next8()

        blockWidth = byteReader.next8()
        blockLength = byteReader.next8()

        subBlocksX = blockWidth / 4
        subBlocksZ = blockLength / 4

        val spacePartitioningTreeOffset = byteReader.next32()
        val cullingTablesOffset = byteReader.next32()
        val pointLightOffset = byteReader.next32()
        val unk = byteReader.next32()
        val zoneObjectsOffset = byteReader.position

        // Begin parsing the subsections
        byteReader.position = cullingTablesOffset + sectionHeader.dataStartPosition
        parseCullingTables(byteReader)

        byteReader.position = zoneObjectsOffset
        val zoneObjects = parseZoneObjs(byteReader, nodeCount)

        byteReader.position = spacePartitioningTreeOffset + sectionHeader.dataStartPosition
        val rootNode = parseNode(byteReader, sectionHeader)

        byteReader.position = pointLightOffset + sectionHeader.dataStartPosition
        val pointLightIdLinks = readPointLights(byteReader)

        if (collisionMeshOffset != 0) { // The ship zones don't have collision meshes - only the ship itself does
            byteReader.position = collisionMeshOffset + sectionHeader.dataStartPosition
            parseCollisionMeshSection(byteReader, sectionHeader)
        }

        return ZoneResource(
            id = sectionHeader.sectionId,
            zoneObj = zoneObjects,
            zoneCollisionMeshes = collisionGroups,
            zoneCollisionMap = collisionMap,
            zoneCullingTables = cullingTables,
            zoneSpaceTreeRoot = rootNode,
            pointLightLinks = pointLightIdLinks,
        )
    }

    private fun parseZoneObjs(byteReader: ByteReader, nodeCount: Int): List<ZoneObject> {
        val zoneObjects = ArrayList<ZoneObject>(nodeCount)

        for (i in 0 until nodeCount) {
            val fileOffset = byteReader.position

            val id = byteReader.nextString(0x10).trimEnd()
            val position = byteReader.nextVector3f()
            val rotation = byteReader.nextVector3f()
            val scale = byteReader.nextVector3f()

            val effectLink = byteReader.nextDatId()
            val highDefThreshold = byteReader.nextFloat()
            val medDefThreshold = byteReader.nextFloat()
            val drawDistance = byteReader.nextFloat()

            val flags0 = byteReader.next8()
            val flags1 = byteReader.next8()
            val flags2 = byteReader.next8()
            val flags3 = byteReader.next8()

            val cullingTableLink = byteReader.next32()
            val environmentLink = byteReader.nextDatId()
            val fileIdLink = byteReader.next32()

            val pointLightIndex0 = byteReader.next32()
            val pointLightIndex1 = byteReader.next32()
            val pointLightIndex2 = byteReader.next32()
            val pointLightIndex3 = byteReader.next32()

            val evalPointLightIndex = listOf(pointLightIndex0, pointLightIndex1, pointLightIndex2, pointLightIndex3).filter { it > 0 }.map { it - 1 }
            val evalParticleLink = effectLink.toNullIfZero()
            val evalEnvironmentLink = environmentLink.toNullIfZero()
            val evalFileIdLink = if (fileIdLink == 0) { null } else { fileIdLink }
            val cullingTableIndex = cullingTableIndicesByOffset[cullingTableLink]

            zoneObjects.add(ZoneObject(
                index = i,
                id = id,
                fileOffset = fileOffset,
                position = position,
                rotation = rotation,
                scale = scale,
                highDefThreshold = highDefThreshold,
                midDefThreshold = medDefThreshold,
                lowDefThreshold = drawDistance,
                cullingTableIndex = cullingTableIndex,
                effectLink = DatLink.of(evalParticleLink),
                environmentLink = evalEnvironmentLink,
                pointLightIndex = evalPointLightIndex,
                skipDuringDecalRendering = flags1 and 0x2 != 0,
                fileIdLink = evalFileIdLink
            ))
        }

        return zoneObjects
    }

    private fun parseCullingTables(byteReader: ByteReader) {
        val indexTableCount = byteReader.next32()
        if (indexTableCount == 0) {
            expectZero(byteReader.next32())
            return
        }

        for (i in 0 until indexTableCount) {
            val offset = byteReader.position
            cullingTableIndicesByOffset[offset] = i

            val currTableCount = byteReader.next32()
            val cullingTable = HashSet<ZoneObjId>(currTableCount)
            cullingTables.add(cullingTable)

            for (j in 0 until currTableCount) {
                cullingTable.add(byteReader.next32())
            }
        }

    }

    private fun parseNode(byteReader: ByteReader, sectionHeader: SectionHeader): SpacePartitioningNode {
        val extentsBuilder = ExtentsBuilder()
        for (i in 0 until 8) {
            extentsBuilder.track(byteReader.nextVector3f())
        }
        val boundingBox = extentsBuilder.toAxisAlignedBoundingBox()

        val idxRef = byteReader.next32()
        val indexCount = byteReader.next32()

        val isLeafNode = indexCount > 0
        val idxFileRef = if (isLeafNode) { idxRef + sectionHeader.dataStartPosition } else { 0 }

        val childrenOffsets = ArrayList<Int>()
        for (i in 0 until 4) {
            val offset = byteReader.next32()
            childrenOffsets.add(offset)
        }

        expectZero(byteReader.next32())
        expectZero(byteReader.next32())

        val children = ArrayList<SpacePartitioningNode?>()
        for (childOffset in childrenOffsets) {
            if (childOffset == 0) {
                children.add(null)
                continue
            }

            val fileOffset = childOffset  + sectionHeader.dataStartPosition
            byteReader.position = fileOffset
            children.add(parseNode(byteReader, sectionHeader))
        }

        val containedObjects = HashSet<ZoneObjId>()
        if (isLeafNode) {
            byteReader.position = idxFileRef
            for (i in 0 until indexCount) {
                containedObjects.add(byteReader.next32())
            }
        }

        return SpacePartitioningNode(
            leafNode = isLeafNode,
            containedObjects = containedObjects,
            boundingBox = boundingBox,
            children = children
        )
    }

    private fun readPointLights(byteReader: ByteReader): List<DatId> {
        val pointLightIdLinks = ArrayList<DatId>()

        for (i in 0 until 256) {
            val idLink = byteReader.nextDatId()
            if (idLink.isZero()) {
                break
            }

            pointLightIdLinks.add(idLink)

            // Pre-allocated space for point-light particle pointers
            for (j in 0 until 0x12) {
                expectZero(byteReader.next32())
            }
        }

        return pointLightIdLinks
    }

    private fun parseCollisionMeshSection(byteReader: ByteReader, sectionHeader: SectionHeader) {
        val numMeshes = byteReader.next32()
        val firstMeshOffset = byteReader.next32() + sectionHeader.dataStartPosition

        val matrixMeshPairCount = byteReader.next32()
        val matrixMeshPairsOffset = byteReader.next32() + sectionHeader.dataStartPosition

        val collisionMapOffset = byteReader.next32() + sectionHeader.dataStartPosition
        val collisionMeshTransformsOffset = byteReader.next32() + sectionHeader.dataStartPosition

        byteReader.next32() // always matches the num indices for the space-tree; not sure why it's here

        expectZero(byteReader.next32())

        // Populate the matrix/mesh transform map
        byteReader.position = matrixMeshPairsOffset
        parseMeshTransformPairs(byteReader, sectionHeader, matrixMeshPairCount)

        // Collision map
        byteReader.position = collisionMapOffset
        parseCollisionMap(byteReader, sectionHeader, matrixMeshPairCount)
    }

    private fun parseMeshTransformPairs(byteReader: ByteReader, sectionHeader: SectionHeader, totalPairCount: Int) {
        for (i in 0 until totalPairCount) {
            val position = byteReader.position
            collisionGroupsByIndex[position] = i

            val countFlags = byteReader.next32()
            val groupSize = countFlags and 0x7FF // not sure what other bits do

            val grouping = CollisionObjectGroup(fileOffset = position, collisionObjects = ArrayList(groupSize))
            collisionGroups.add(grouping)

            for (j in 0 until groupSize) {
                val fileOffset = byteReader.position

                val matrixOffset = byteReader.next32() + sectionHeader.dataStartPosition
                val transform = transformsByOffset.getOrPut(matrixOffset) { byteReader.wrapped { parseTransform(byteReader, matrixOffset) } }

                val meshOffset = byteReader.next32() + sectionHeader.dataStartPosition
                val mesh = collisionMeshesByOffset.getOrPut(meshOffset) { byteReader.wrapped { parseCollisionMesh(byteReader, meshOffset, sectionHeader) } }

                grouping.collisionObjects.add(CollisionObject(fileOffset, mesh, transform))
            }

            expectZero(byteReader.next32())
        }
    }

    private fun parseCollisionMesh(byteReader: ByteReader, offset: Int, sectionHeader: SectionHeader): CollisionMesh {
        byteReader.position = offset

        val positionOffset = byteReader.next32() + sectionHeader.dataStartPosition
        val directionOffset = byteReader.next32() + sectionHeader.dataStartPosition
        val indexOffset = byteReader.next32() + sectionHeader.dataStartPosition

        val numTris = byteReader.next16()
        val unk = byteReader.next16()

        val debugMeshBuilder = GlBufferBuilder(numTris * 3)
        val triangles = ArrayList<Triangle>()
        val extentsTracker = ExtentsBuilder()

        byteReader.position = indexOffset
        for (i in 0 until numTris) {
            // In the code, they're not all & 0x7FFFF, there's some 0xBFFF and 0x3FFF
            val rawP0 = byteReader.next16()
            val pOff0 = positionOffset + (rawP0 and 0x7FFF) * 4 * 3

            val rawP1 = byteReader.next16()
            val pOff1 = positionOffset + (rawP1 and 0x3FFF) * 4 * 3

            val rawP2 = byteReader.next16()
            val pOff2 = positionOffset + (rawP2 and 0x3FFF) * 4 * 3

            val rawD = byteReader.next16()
            val dOff = directionOffset + (rawD and 0x7FFF) * 4 * 3

            val nextPos = byteReader.position
            val p0 = extentsTracker.track(readVec3(byteReader, pOff0))
            val p1 = extentsTracker.track(readVec3(byteReader, pOff1))
            val p2 = extentsTracker.track(readVec3(byteReader, pOff2))
            val d0 = readVec3(byteReader, dOff)

            val flags = listOf(rawP0, rawP1, rawP2, rawD).map { it ushr 12 } // No idea what the actual right amount is
            val material = TriFlags((flags[0] shl 12) or (flags[1] shl 8) or (flags[2] shl 4) or (flags[3]))
            val type = TerrainType.fromFlags(flags[0], flags[1], flags[2], flags[3])

            val triangle = Triangle(p0, p1, p2, d0, material, type)
            triangles += triangle

            byteReader.position = nextPos

            val color = if (material.hitWall) { ByteColor(0x80, 0x00, 0x00, 0x40) } else { type.debugMeshColor.withVariance(0x08) }
            debugMeshBuilder.appendCollisionVertex(p0, d0, color)
            debugMeshBuilder.appendCollisionVertex(p1, d0, color)
            debugMeshBuilder.appendCollisionVertex(p2, d0, color)
        }

        // Hack to make collision-resolution with stairs easier
        triangles.sortByDescending { abs(it.normal.y) }

        val debugMesh = MeshBuffer(
            numVertices = numTris * 3,
            meshType = MeshType.TriMesh,
            glBuffer = debugMeshBuilder.build(),
            textureStage0 = null,
        )

        return CollisionMesh(
            fileOffset = offset,
            meshBuffer = debugMesh,
            tris = triangles,
            boundingSphere = extentsTracker.toBoundingSphere()
        )
    }

    private fun parseCollisionMap(byteReader: ByteReader, sectionHeader: SectionHeader, expected: Int) {
        val map = Array(zoneBlocksZ * subBlocksZ) { Array<CollisionObjectGroup?>(zoneBlocksX * subBlocksX) { null } }
        var found = 0

        for (z in map.indices) {
            for (x in map[z].indices) {
                val offset = byteReader.next32()
                if (offset == 0) {
                    continue
                }

                found += 1

                val fileOffset = offset + sectionHeader.dataStartPosition
                val index = collisionGroupsByIndex[fileOffset]!!
                val collisionGroup = collisionGroups[index]

                map[z][x] = collisionGroup

                // Some maps seem off-by-one?
                if (found == expected) { break }
            }

            if (found == expected) { break }
        }

        collisionMap = CollisionMap(zoneBlocksX, zoneBlocksZ, blockWidth, blockLength, subBlocksX, subBlocksZ, map)
    }

    private fun readVec3(byteReader: ByteReader, offset: Int): Vector3f {
        byteReader.position = offset
        return Vector3f(byteReader.nextFloat(), byteReader.nextFloat(), byteReader.nextFloat())
    }

    private fun parseTransform(byteReader: ByteReader, offset: Int): CollisionTransformInfo {
        byteReader.position = offset

        val toWorldSpace = Matrix4f()
        for (i in 0 until 16) { toWorldSpace.m[i] = byteReader.nextFloat() }

        val toCollisionSpace = Matrix4f()
        for (i in 0 until 16) { toCollisionSpace.m[i] = byteReader.nextFloat() }

        // TODO Not sure what the remaining data is - seems to be formatted like so
        byteReader.nextVector3f()
        byteReader.nextVector3f()
        byteReader.nextVector3f()

        val miscFlags = byteReader.next32() // Partially zone-map flags
        val cullingGroupOffset = byteReader.next32()
        val rawLightIndices = listOf(byteReader.next8(), byteReader.next8(), byteReader.next8(), byteReader.next8())
        val rawEnvironmentId = byteReader.nextDatId()

        // These two seem to be related to world-space height - (highest, lowest)?
        val unkFloat0 = byteReader.nextFloat()
        val unkFloat1 = byteReader.nextFloat()

        val rawSubAreaLinkId = byteReader.next32()
        val subAreaLinkId = if (rawSubAreaLinkId == 0) { null } else { rawSubAreaLinkId }

        val lightIndices = rawLightIndices.filter { it != 0 }.map { it - 1 }
        val environmentId = if (rawEnvironmentId.isZero()) { null } else { rawEnvironmentId }

        val mapId = 0x8 * ((miscFlags ushr 26) and 0x3) + ((miscFlags ushr 3) and 0x7)

        val cullingGroupIndex = if (cullingGroupOffset == 0) { null } else {
            val fileOffset = cullingGroupOffset + sectionHeader.dataStartPosition
            cullingTableIndicesByOffset[fileOffset] ?: throw IllegalStateException("Couldn't find the culling table")
        }

        return CollisionTransformInfo(
            fileOffset = offset,
            toWorldSpace = toWorldSpace,
            toCollisionSpace = toCollisionSpace,
            environmentId = environmentId,
            lightIndices = lightIndices,
            cullingTableIndex = cullingGroupIndex,
            miscFlags = miscFlags,
            subAreaLinkId = subAreaLinkId,
            mapId = mapId,
        )
    }

}