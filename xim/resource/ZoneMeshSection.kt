package xim.resource

import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.gl.*
import xim.resource.table.ZoneDecrypt
import kotlin.math.abs

class ZoneMeshSection(private val sectionHeader: SectionHeader) : ResourceParser {

    private lateinit var name: String
    private val buffers = ArrayList<MeshBuffer>()

    private var boundingBox0: BoundingBox? = null
    private var boundingBox1: BoundingBox? = null

    override fun getResource(byteReader: ByteReader): ParserResult {
        ZoneDecrypt.decryptZoneMesh(sectionHeader, byteReader)
        read(byteReader)
        val resource = ZoneMeshResource(sectionHeader.sectionId, buffers, name, boundingBox0, boundingBox1)
        return ParserResult(ParserEntry(resource, name))
    }

    private fun read(byteReader: ByteReader) {
        byteReader.offsetFromDataStart(sectionHeader)

        val decryptInfo = byteReader.next32() // DecodeInfo - not needed here
        val keyConfigData = byteReader.next32() // Decode key + section specific params
        val unkString = byteReader.nextString(0x8) // Author name?

        val configData = keyConfigData and 0x000000FF

        val meshTypeFlag = configData and 0x1
        val meshType = if (meshTypeFlag == 0) { MeshType.TriMesh } else { MeshType.TriStrip }
        val vertexBlendEnabled = (configData and 0x2) != 0

        name = byteReader.nextString(0x10).trimEnd()

        val defStart = byteReader.position

        // Section 1
        val meshCount0 = byteReader.next32()
        boundingBox0 = BoundingBox.read(byteReader)

        val section1DataStart = defStart + byteReader.next32()

        // "hit"-type models, only have a bounding box & no data
        if (meshCount0 == 0) {
            if (byteReader.position != sectionHeader.sectionStartPosition + sectionHeader.sectionSize) {
                throw IllegalStateException("Expected to be at the end")
            }

            return
        }

        // Section 2
        val meshCount1 = byteReader.next32()
        boundingBox1 = BoundingBox.read(byteReader)

        // Not sure what the last 4 bytes are
        val unknown = byteReader.next32()
        if (byteReader.position != section1DataStart) {
            throw IllegalStateException("More defn data than expected")
        }

        for (i in 0 until meshCount1) {
            val meshBuffer = parseMesh(byteReader, name, vertexBlendEnabled, meshType)
            buffers.add(meshBuffer)
            byteReader.align0x04()
        }
    }

    private fun parseMesh(byteReader: ByteReader, name: String, vertexBlendEnabled: Boolean, meshType: MeshType): MeshBuffer {
        val textureName = byteReader.nextString(0x10)
        val numVerts = byteReader.next16()
        val verts = ArrayList<Vertex>(numVerts)

        val flags = byteReader.next16()
        val blendEnabled = (flags and 0x8000) != 0
        val backFaceCulling = (flags and 0x2000) == 0 // by default, culling is enabled to CCW & setting this flag will disable it

        for(i in 0 until numVerts) {
            if (vertexBlendEnabled) {
                verts.add(Vertex(
                    byteReader.nextVector3f(),
                    byteReader.nextVector3f(),
                    byteReader.nextVector3f(),
                    ByteColor(b = byteReader.next8(), g = byteReader.next8(), r = byteReader.next8(), a = byteReader.next8()),
                    byteReader.nextFloat(), byteReader.nextFloat()
                ))
            } else {
                verts.add(Vertex(
                    byteReader.nextVector3f(),
                    Vector3f(),
                    byteReader.nextVector3f(),
                    ByteColor(b = byteReader.next8(), g = byteReader.next8(), r = byteReader.next8(), a = byteReader.next8()),
                    byteReader.nextFloat(), byteReader.nextFloat()
                ))
            }
        }

        val numIndicies = byteReader.next16()
        val unk1 = byteReader.next16()
        val indexedVertices = ArrayList<Vertex>(numIndicies)

        for (i in 0 until numIndicies) {
            val idx = byteReader.next16()
            indexedVertices += verts[idx]
        }

        val glBufferBuilder = GlBufferBuilder(numIndicies)
        for (i in 0 until numIndicies) {
            val tangent = computeTangent(i, indexedVertices, meshType)
            glBufferBuilder.appendZoneMeshVertex(indexedVertices[i], tangent)
        }

        val bufferId = glBufferBuilder.build()

        val discardThreshold = if (name.startsWith("_")) { 0.375f } else { null }

        val zBias = when (blendEnabled) {
            true -> ZBiasLevel.High
            false -> ZBiasLevel.Normal
        }

        return MeshBuffer(
            numVertices = numIndicies,
            meshType = meshType,
            textureStage0 = TextureLink.of(textureName, sectionHeader.localDir),
            renderState = RenderState(blendEnabled = blendEnabled, zBias = zBias, discardThreshold = discardThreshold, useBackFaceCulling = backFaceCulling),
            blendVertexPosition = vertexBlendEnabled,
            glBuffer = bufferId
        )
    }

    private fun computeTangent(i: Int, verts: List<Vertex>, type: MeshType): Vector3f {
        if (i <= 2) { return computeTangent(verts[0], verts[1], verts[2]) }

        return when (type) {
            MeshType.TriMesh -> {
                val i0 = i - (i % 3)
                computeTangent(verts[i0], verts[i0+1], verts[i0+2])
            }
            MeshType.TriStrip -> {
                computeTangent(verts[i-2], verts[i-1], verts[i])
            }
            MeshType.TriFan -> {
                computeTangent(verts[0], verts[i-1], verts[i])
            }
        }
    }

    private fun computeTangent(v0: Vertex, v1: Vertex, v2: Vertex): Vector3f {
        val edge1 = v1.p0 - v0.p0
        val edge2 = v2.p0 - v0.p0

        val dUV1 = Vector2f(v1.texCoordU, v1.texCoordV) - Vector2f(v0.texCoordU, v0.texCoordV)
        val dUV2 = Vector2f(v2.texCoordU, v2.texCoordV) - Vector2f(v0.texCoordU, v0.texCoordV)

        val denom = dUV1.x * dUV2.y - dUV2.x * dUV1.y
        if (abs(denom) < 1e-5) { return Vector3f(1f, 1f, 1f).normalizeInPlace() }

        val f = 1.0f / denom
        return Vector3f(
            x = f * (dUV2.y * edge1.x - dUV1.y * edge2.x),
            y = f * (dUV2.y * edge1.y - dUV1.y * edge2.y),
            z = f * (dUV2.y * edge1.z - dUV1.y * edge2.z),
        )
    }

    data class BoundingBox(
        val p0: Vector3f,
        val p1: Vector3f,
    ) {

        companion object {
            fun read(byteReader: ByteReader): BoundingBox {
                val x0 = byteReader.nextFloat()
                val x1 = byteReader.nextFloat()

                val y0 = byteReader.nextFloat()
                val y1 = byteReader.nextFloat()

                val z0 = byteReader.nextFloat()
                val z1 = byteReader.nextFloat()

                return BoundingBox(Vector3f(x0, y0, z0), Vector3f(x1, y1, z1))
            }
        }
    }

    class Vertex(
        val p0: Vector3f,
        val p1: Vector3f,
        val n0: Vector3f,
        val colorMask: ByteColor,
        val texCoordU: Float,
        val texCoordV: Float,
    )

}
