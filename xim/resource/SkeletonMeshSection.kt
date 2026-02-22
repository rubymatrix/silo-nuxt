package xim.resource

import xim.math.Vector3f
import xim.poc.gl.ByteColor
import xim.poc.gl.GlBufferBuilder
import xim.poc.gl.MeshBuffer
import xim.poc.gl.RenderState

enum class MeshType {
    TriStrip,
    TriMesh,
    TriFan,
}

class ClothLinkVertex(val index: Int, val flag: Boolean)

class ClothLink(val vertexA: ClothLinkVertex, val vertexB: ClothLinkVertex, val baseLinkLength: Float)

class ClothProperties(
    val lockedSingleJointVertices: Int,
    val lockedDoubleJointVertices: Int,
    val adjacentLinks: List<ClothLink>,
    val diagonalLinks: List<ClothLink>,
    val adjacentSpringFactor: Float,
    val diagonalSpringFactor: Float,
    val exponentialSpringFactor: Float,
    val gravityFactor: Float,
    val movementFactor: Float,
)

data class RenderProperties (
    val tFactor: ByteColor = ByteColor(0x80, 0x80, 0x80, 0x80),
    val specularHighlightEnabled: Boolean  = false,
    val specularHighlightPower: Float = 0f,
    val displayTypeFlag: Int = 0,
    val ambientMultiplier: Float = 1f,
)

class Vertex {
    lateinit var jointRef0: SkeletonMeshSection.JointRef
    lateinit var jointRef1: SkeletonMeshSection.JointRef

    val p0 = Vector3f()
    val p1 = Vector3f()

    val n0 = Vector3f()
    val n1 = Vector3f()

    var joint0Weight: Float = 1.0f
    var joint1Weight: Float = 0.0f

    var jointIndex0: Int? = null
    var jointIndex1: Int? = null
}

class MeshVertex(val vertex: Vertex, val u: Float, val v: Float, val color: ByteColor = ByteColor.half)

class SkeletonMeshSection(private val sectionHeader: SectionHeader) : ResourceParser {

    companion object {
        private const val discardThreshold = 69f/255f
    }

    lateinit var joints: ArrayList<Int>
    lateinit var vertices: Array<Vertex>

    val meshes = ArrayList<MeshBuffer>()

    var useJointArray = false
    var symmetric = false
    var hasNormals = false
    var occludeType = 0

    override fun getResource(byteReader: ByteReader): ParserResult {
        read(byteReader)
        val meshResource = SkeletonMeshResource(sectionHeader.sectionId, meshes, occludeType)
        return ParserResult.from(meshResource)
    }

    private fun read(byteReader: ByteReader) {
        byteReader.offsetFromDataStart(sectionHeader)

        val flags1 = byteReader.next8()

        val flags2 = byteReader.next8()

        val flags3 = byteReader.next8()

        val clothEffect = (flags3 and 0x01) != 0
        useJointArray = (flags3 and 0x80) != 0

        hasNormals = !clothEffect

        val flags4 = byteReader.next8()
        occludeType = flags4

        val flags5 = byteReader.next8()
        symmetric = flags5 == 0x01

        val flags6 = byteReader.next8()

        // Read some offsets & counts
        val instructionOffset = 2 * byteReader.next32()
        val maybeMeshCount = byteReader.next8()
        val maybeInstructionCount = byteReader.next8()

        val jointArrayOffset = 2 * byteReader.next32()
        val numJoints = byteReader.next16()

        val vertexCountsOffset = 2 * byteReader.next32()
        val numVertexCounts = byteReader.next16()

        val vertexJointMappingOffset = 2 * byteReader.next32()
        val vertexJointMappingCount = byteReader.next16()

        val vertexDataOffset = 2 * byteReader.next32()
        val maybeVertexDataSize = byteReader.next16()

        val endOffset = 2 * byteReader.next32()
        val endOffsetDataSize = byteReader.next16()

        if (clothEffect) { parseClothData(byteReader) }

        // Read some joints
        joints = ArrayList(numJoints)

        byteReader.offsetFromDataStart(sectionHeader, jointArrayOffset)
        for(i in 0 until numJoints) {
            val jointIndex = byteReader.next16()
            joints.add(jointIndex)
        }

        // Read the counts
        byteReader.offsetFromDataStart(sectionHeader, vertexCountsOffset)
        if (numVertexCounts != 2) { throw IllegalStateException("Expected only 2 types of counts") }
        val singleJointedVertexCount = byteReader.next16()
        val doubleJointedVertexCount = byteReader.next16()

        // Start reading the vertex data (position, normal, & joint-attachment)
        vertices = Array(singleJointedVertexCount + doubleJointedVertexCount){ Vertex() }

        byteReader.offsetFromDataStart(sectionHeader, vertexJointMappingOffset)
        parseJointRefs(byteReader, singleJointedVertexCount, doubleJointedVertexCount)

        byteReader.offsetFromDataStart(sectionHeader, vertexDataOffset)
        parsePositionsAndNormals(byteReader, singleJointedVertexCount, doubleJointedVertexCount)

        // Read the actual meshes (index into vertices, UVs, materials, etc)
        byteReader.offsetFromDataStart(sectionHeader, instructionOffset)

        var currentTextureName = ""
        var currentRenderProperties = RenderProperties()

        while (true) {
            val opCode = byteReader.next16()

            if (opCode == 0xFFFF) {
                break
            } else if (opCode == 0x8010) {
                currentRenderProperties = readRenderProperties(byteReader)
            } else if (opCode == 0x8000) {
                currentTextureName = byteReader.nextString(0x10)
            } else if (opCode == 0x5453) {
                parseTriStrip(byteReader, currentTextureName, currentRenderProperties)
            } else if (opCode == 0x0054) {
                parseTriMesh(byteReader, currentTextureName, currentRenderProperties)
            } else if (opCode == 0x0043) {
                parseUntexturedTriMesh(byteReader, currentTextureName, currentRenderProperties)
            } else if (opCode == 0x4353) {
                parseSingleColorUntexturedTriStrip(byteReader, currentTextureName, currentRenderProperties)
            } else {
                throw IllegalStateException("Unknown op-code [${opCode.toString(0x10)}] @ $byteReader")
            }
        }
    }

    private fun parseTriStrip(byteReader: ByteReader, textureName: String, renderProperties: RenderProperties) {
        val numTriangles = byteReader.next16()
        val numVertices = numTriangles + 2

        val meshVertices = ArrayList<MeshVertex>(numVertices)

        val vert0 = vertices[byteReader.next16()]
        val vert1 = vertices[byteReader.next16()]
        val vert2 = vertices[byteReader.next16()]

        val u0 = byteReader.nextFloat()
        val v0 = byteReader.nextFloat()

        val u1 = byteReader.nextFloat()
        val v1 = byteReader.nextFloat()

        val u2 = byteReader.nextFloat()
        val v2 = byteReader.nextFloat()

        meshVertices += MeshVertex(vert0, u0, v0)
        meshVertices += MeshVertex(vert1, u1, v1)
        meshVertices += MeshVertex(vert2, u2, v2)

        for (i in 1 until numTriangles) {
            val vert = vertices[byteReader.next16()]
            val u = byteReader.nextFloat()
            val v = byteReader.nextFloat()
            meshVertices += MeshVertex(vert, u, v)
        }

        val bufferBuilder = GlBufferBuilder(meshVertices.size)
        meshVertices.forEach { bufferBuilder.appendSkinnedMeshVertex(it) }

        val meshBuffer = MeshBuffer(
            numVertices = numVertices,
            meshType = MeshType.TriStrip,
            textureStage0 = TextureLink.of(textureName, sectionHeader.localDir),
            skeletalMeshProperties = renderProperties,
            glBuffer = bufferBuilder.build(),
            renderState = RenderState(discardThreshold = discardThreshold)
        )

        meshes += meshBuffer
        if (symmetric) { meshes += mirrorBuffer(meshVertices, meshBuffer) }
    }

    private fun parseTriMesh(byteReader: ByteReader, textureName: String, renderProperties: RenderProperties) {
        val numTriangles = byteReader.next16()
        val numVertices = numTriangles * 3

        val meshVertices = ArrayList<MeshVertex>(numVertices)

        for (i in 0 until numTriangles) {
            val vert0 = vertices[byteReader.next16()]
            val vert1 = vertices[byteReader.next16()]
            val vert2 = vertices[byteReader.next16()]

            val u0 = byteReader.nextFloat()
            val v0 = byteReader.nextFloat()

            val u1 = byteReader.nextFloat()
            val v1 = byteReader.nextFloat()

            val u2 = byteReader.nextFloat()
            val v2 = byteReader.nextFloat()

            meshVertices += MeshVertex(vert0, u0, v0)
            meshVertices += MeshVertex(vert1, u1, v1)
            meshVertices += MeshVertex(vert2, u2, v2)
        }

        val bufferBuilder = GlBufferBuilder(numVertices)
        meshVertices.forEach { bufferBuilder.appendSkinnedMeshVertex(it) }

        val meshBuffer = MeshBuffer(
            numVertices = numVertices,
            meshType = MeshType.TriMesh,
            textureStage0 = TextureLink.of(textureName, sectionHeader.localDir),
            skeletalMeshProperties = renderProperties,
            glBuffer = bufferBuilder.build(),
            renderState = RenderState(discardThreshold = discardThreshold)
        )

        meshes += meshBuffer
        if (symmetric) { meshes += mirrorBuffer(meshVertices, meshBuffer) }
    }

    private fun parsePositionsAndNormals(byteReader: ByteReader, singleJointedCount: Int, doubleJointedCount: Int) {
        for (i in 0 until singleJointedCount) {
            val vertex = vertices[i]
            vertex.p0.copyFrom(byteReader.nextVector3f())

            if (!hasNormals) { continue }
            vertex.n0.copyFrom(byteReader.nextVector3f())
        }

        for (i in 0 until doubleJointedCount) {
            val vertex = vertices[singleJointedCount + i]
            vertex.p0.x = byteReader.nextFloat()
            vertex.p1.x = byteReader.nextFloat()
            vertex.p0.y = byteReader.nextFloat()
            vertex.p1.y = byteReader.nextFloat()
            vertex.p0.z = byteReader.nextFloat()
            vertex.p1.z = byteReader.nextFloat()

            vertex.joint0Weight = byteReader.nextFloat()
            vertex.joint1Weight = byteReader.nextFloat()

            if (!hasNormals) { continue }

            vertex.n0.x = byteReader.nextFloat()
            vertex.n1.x = byteReader.nextFloat()
            vertex.n0.y = byteReader.nextFloat()
            vertex.n1.y = byteReader.nextFloat()
            vertex.n0.z = byteReader.nextFloat()
            vertex.n1.z = byteReader.nextFloat()
        }
    }

    private fun parseJointRefs(byteReader: ByteReader, singleJointedCount: Int, doubleJointedCount: Int) {
        for (i in 0 until singleJointedCount) {
            val vertex = vertices[i]
            vertex.jointRef0 = unpackJointRef(byteReader.next16())
            vertex.jointIndex0 = if (useJointArray) { joints[vertex.jointRef0.index] } else { vertex.jointRef0.index }

            vertex.jointRef1 = unpackJointRef(byteReader.next16()) // Should just be 0
        }

        for (i in 0 until doubleJointedCount) {
            val vertex = vertices[singleJointedCount + i]
            vertex.jointRef0 = unpackJointRef(byteReader.next16())
            vertex.jointIndex0 = if (useJointArray) { joints[vertex.jointRef0.index] } else { vertex.jointRef0.index }

            vertex.jointRef1 = unpackJointRef(byteReader.next16())
            vertex.jointIndex1 = if (useJointArray) { joints[vertex.jointRef1.index] } else { vertex.jointRef1.index }
        }
    }

    data class JointRef(val index: Int, val flippedIndex: Int, val flipAxis: Int)

    private fun unpackJointRef(data: Int) : JointRef {
        return JointRef(
            index = (data and 0x7F),
            flippedIndex = ((data shr 0x7) and 0x7F),
            flipAxis = ((data shr 0xE) and 0x3)
        )
    }

    private fun flipVertex(original: Vertex) : Vertex {
        val mirrored = Vertex()
        mirrored.p0.copyFrom(flipVector(original.p0, original.jointRef0))
        mirrored.p1.copyFrom(flipVector(original.p1, original.jointRef1))

        mirrored.n0.copyFrom(flipVector(original.n0, original.jointRef0))
        mirrored.n1.copyFrom(flipVector(original.n1, original.jointRef1))

        mirrored.jointIndex0 = if (useJointArray) { joints[original.jointRef0.flippedIndex] } else { original.jointRef0.flippedIndex }
        mirrored.jointIndex1 = if (useJointArray) { joints[original.jointRef1.flippedIndex] } else { original.jointRef1.flippedIndex }

        mirrored.joint0Weight = original.joint0Weight
        mirrored.joint1Weight = original.joint1Weight

        mirrored.jointRef0 = original.jointRef0
        mirrored.jointRef1 = original.jointRef1

        return mirrored
    }

    private fun flipVector(original: Vector3f, flipType: JointRef): Vector3f {
        val vector3f = Vector3f(original)

        when (flipType.flipAxis) {
            1 -> { vector3f.x *= -1 }
            2 -> { vector3f.y *= -1 }
            3 -> { vector3f.z *= -1 }
        }

        return vector3f
    }

    private fun readRenderProperties(byteReader: ByteReader): RenderProperties {
        val tFactor = byteReader.nextBGRA() // confirmed BGRA; only used if specular isn't

        val f0 = byteReader.nextFloat()
        val f1 = byteReader.nextFloat()

        val flag0 = byteReader.next8()
        val displayType = byteReader.next8()
        val flag2 = byteReader.next8()
        val flag3 = byteReader.next8()

        val ambientMultiplier = byteReader.nextFloat()

        val unk0 = byteReader.next32()
        val unk1 = byteReader.next32()
        val unk2 = byteReader.next16()

        val f4 = byteReader.nextFloat()
        val unk3 = byteReader.next16()

        val specularHighlightPower = byteReader.nextFloat()
        val specularHighlightEnabled = byteReader.nextFloat() == 1.0f

        return RenderProperties(
            tFactor = if (specularHighlightEnabled) { ByteColor.half } else { ByteColor.half /* TODO: use t-factor here */ },
            specularHighlightPower = specularHighlightPower,
            specularHighlightEnabled = specularHighlightEnabled,
            displayTypeFlag = displayType,
            ambientMultiplier = ambientMultiplier,
        )
    }

    private fun parseUntexturedTriMesh(byteReader: ByteReader, textureName: String, renderProperties: RenderProperties) {
        val numTriangles = byteReader.next16()
        val numVertices = numTriangles * 3

        val meshVertices = ArrayList<MeshVertex>(numVertices)

        for (i in 0 until numTriangles) {
            val vert0 = vertices[byteReader.next16()]
            val vert1 = vertices[byteReader.next16()]
            val vert2 = vertices[byteReader.next16()]
            val color = byteReader.nextBGRA()

            meshVertices += MeshVertex(vert0, 0f, 0f, color)
            meshVertices += MeshVertex(vert1, 0f, 0f, color)
            meshVertices += MeshVertex(vert2, 0f, 0f, color)
        }

        val bufferBuilder = GlBufferBuilder(numVertices)
        meshVertices.forEach { bufferBuilder.appendSkinnedMeshVertex(it) }

        val meshBuffer = MeshBuffer(
            numVertices = numVertices,
            meshType = MeshType.TriMesh,
            textureStage0 = TextureLink.of(textureName, sectionHeader.localDir),
            skeletalMeshProperties = renderProperties,
            glBuffer = bufferBuilder.build(),
            renderState = RenderState(discardThreshold = discardThreshold)
        )

        meshes += meshBuffer
        if (symmetric) { meshes += mirrorBuffer(meshVertices, meshBuffer) }
    }

    private fun parseSingleColorUntexturedTriStrip(byteReader: ByteReader, textureName: String, renderProperties: RenderProperties) {
        val numTriangles = byteReader.next16()
        val numVertices = numTriangles + 2

        val meshVertices = ArrayList<MeshVertex>(numVertices)

        val vert0 = vertices[byteReader.next16()]
        val vert1 = vertices[byteReader.next16()]
        val vert2 = vertices[byteReader.next16()]
        val color = byteReader.nextBGRA()

        meshVertices += MeshVertex(vert0, 0f, 0f, color)
        meshVertices += MeshVertex(vert1, 0f, 0f, color)
        meshVertices += MeshVertex(vert2, 0f, 0f, color)

        for (i in 1 until numTriangles) {
            meshVertices += MeshVertex(vertices[byteReader.next16()], 0f, 0f, color)
        }

        val bufferBuilder = GlBufferBuilder(numVertices)
        meshVertices.forEach { bufferBuilder.appendSkinnedMeshVertex(it) }

        val meshBuffer = MeshBuffer(
            numVertices = numVertices,
            meshType = MeshType.TriStrip,
            textureStage0 = TextureLink.of(textureName, sectionHeader.localDir),
            skeletalMeshProperties = renderProperties,
            glBuffer = bufferBuilder.build(),
            renderState = RenderState(discardThreshold = discardThreshold)
        )

        meshes += meshBuffer
        if (symmetric) { meshes += mirrorBuffer(meshVertices, meshBuffer) }
    }

    private fun mirrorBuffer(meshVertices: List<MeshVertex>,original: MeshBuffer): MeshBuffer {
        val flippedBuilder = GlBufferBuilder(meshVertices.size)
        meshVertices.forEach { flippedBuilder.appendSkinnedMeshVertex(flipVertex(it.vertex), it.u, it.v, it.color) }
        return original.copy(glBuffer = flippedBuilder.build())
    }

    private fun parseClothData(byteReader: ByteReader) {
        val lockedSingleJointVertices = byteReader.next16()
        val lockedDoubleJointVertices = byteReader.next16()

        val clothLinkDataOffset = 2 * byteReader.next32()
        val clothLinkDataSize = byteReader.next16()

        val adjacentLinksSize = byteReader.next16()
        val diagonalLinksSize = byteReader.next16()

        if (adjacentLinksSize + diagonalLinksSize != clothLinkDataSize) { oops(byteReader, "Expected sub-sizes to add up to total size") }

        val adjacentSpringFactor = byteReader.nextFloat()
        val diagonalSpringFactor = byteReader.nextFloat()
        val exponentialSpringFactor = byteReader.nextFloat()
        val gravityFactor = byteReader.nextFloat()
        val movementFactor = byteReader.nextFloat()

        byteReader.offsetFromDataStart(sectionHeader, clothLinkDataOffset)
        val adjacentLinks = (0 until  adjacentLinksSize/4).map { readClothLink(byteReader) }
        val diagonalLinks = (0 until  diagonalLinksSize/4).map { readClothLink(byteReader) }

        ClothProperties(
            lockedSingleJointVertices = lockedSingleJointVertices,
            lockedDoubleJointVertices = lockedDoubleJointVertices,
            adjacentLinks = adjacentLinks,
            diagonalLinks = diagonalLinks,
            adjacentSpringFactor = adjacentSpringFactor,
            diagonalSpringFactor = diagonalSpringFactor,
            exponentialSpringFactor = exponentialSpringFactor,
            gravityFactor = gravityFactor,
            movementFactor = movementFactor,
        )

        // TODO - implement the cloth engine...
    }

    private fun readClothLink(byteReader: ByteReader): ClothLink {
        val index0AndFlag = byteReader.next16()
        val index1AndFlag = byteReader.next16()
        val baseDistance = byteReader.nextFloat()

        return ClothLink(
            vertexA = ClothLinkVertex(index = index0AndFlag and 0x7FFF, flag = (index0AndFlag and 0x8000) != 0),
            vertexB = ClothLinkVertex(index = index1AndFlag and 0x7FFF, flag = (index1AndFlag and 0x8000) != 0),
            baseLinkLength = baseDistance,
        )
    }

}
