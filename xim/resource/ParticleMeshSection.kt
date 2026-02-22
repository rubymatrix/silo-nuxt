package xim.resource

import xim.math.Vector3f
import xim.poc.browser.ParserContext
import xim.poc.gl.ByteColor
import xim.poc.gl.GlBufferBuilder
import xim.poc.gl.MeshBuffer

data class ParticleMeshVertex(
    val position: Vector3f,
    val normal: Vector3f,
    val colorMask: ByteColor,
    val texCoordU: Float,
    val texCoordV: Float,
)

data class ParticleMeshData(
    val vertices: List<ParticleMeshVertex>,
    val textureName: String?,
)

data class ParticleDef(
    val particleMeshes: ArrayList<MeshBuffer> = ArrayList(),
    val particleMeshData: ArrayList<ParticleMeshData> = ArrayList(),
)

class ParticleMeshSection(private val sectionHeader: SectionHeader, private val parserContext: ParserContext) : ResourceParser {

    private val particleDef = ParticleDef()

    override fun getResource(byteReader: ByteReader): ParserResult {
        read(byteReader)
        val particleMeshResource = ParticleMeshResource(sectionHeader.sectionId, particleDef)
        return ParserResult.from(particleMeshResource)
    }

    private fun read(byteReader: ByteReader) {
        byteReader.offsetFrom(sectionHeader, 0x10)

        val version = byteReader.next32() // TODO - need more samples of 0x3 and 0x05
        if (version != 0x3 && version != 0x6 && version != 0x05) { oops(byteReader, "Unknown version for ParticleDef: $version") }

        val numMeshesWithTextures = byteReader.next8()
        val numMeshesWithoutTextures = byteReader.next8()
        val totalNumMeshes = numMeshesWithTextures + numMeshesWithoutTextures

        val numTriangles = byteReader.next16()

        val triArraySize = when {
            numMeshesWithTextures <= 3 -> 3
            numMeshesWithTextures <= 8 -> 8
            numMeshesWithTextures <= 11 -> 11
            numMeshesWithTextures <= 15 -> 15
            else -> throw IllegalStateException("Too many meshes...? $numMeshesWithTextures.")
        }

        val numTriesInMeshes = (0 until  triArraySize).map { byteReader.next16() }

        if (version == 0x03) {
            expectZero(byteReader.next16())
        }

        val textureNames = ArrayList<String>(numMeshesWithTextures)

        val iterations = if (version == 0x03) { 4 } else { numMeshesWithTextures }
        for (i in 0 until iterations) {
            textureNames.add(byteReader.nextString(0x10))
        }

        // See notes: [ParserContext - color scaling factor]
        val colorScalingFactor = if (parserContext.zoneResource) { 2 } else { 1 }

        for (k in 0 until totalNumMeshes) {
            val numVerts = 3 * numTriesInMeshes[k]
            val glBufferBuilder = GlBufferBuilder(numVerts)
            val meshVertices = ArrayList<ParticleMeshVertex>(numVerts)

            for (i in 0 until numVerts) {
                val position = Vector3f(byteReader.nextFloat(), byteReader.nextFloat(), byteReader.nextFloat())
                val normal = Vector3f(byteReader.nextFloat(), byteReader.nextFloat(), byteReader.nextFloat())
                val color = byteReader.nextBGRA().multiply(colorScalingFactor).clamp(0xFF)
                val texCoordU = byteReader.nextFloat()
                val texCoordV = byteReader.nextFloat()

                val vertex = ParticleMeshVertex(position, normal, color, texCoordU, texCoordV)
                meshVertices.add(vertex)
                glBufferBuilder.appendParticleVertex(vertex)
            }

            val textureName = if (k < numMeshesWithTextures) { textureNames[k] } else { null }

            val webGLBuffer = glBufferBuilder.build()
            particleDef.particleMeshData.add(ParticleMeshData(meshVertices, textureName))
            particleDef.particleMeshes.add(MeshBuffer(
                numVertices = numVerts,
                meshType = MeshType.TriMesh,
                textureStage0 = TextureLink.of(textureName, sectionHeader.localDir),
                glBuffer = webGLBuffer
            ))
        }
    }

}
