package xim.resource

import xim.poc.gl.GlBufferBuilder
import xim.poc.gl.MeshBuffer

data class SpriteSheet(
    val meshes: ArrayList<MeshBuffer>,
    val offsets: ArrayList<Float>
)

class SpriteSheetSection(val sectionHeader: SectionHeader) : ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val spriteSheet = read(byteReader)
        val resource = SpriteSheetResource(sectionHeader.sectionId, spriteSheet)
        return ParserResult.from(resource)
    }

    private fun read(byteReader: ByteReader): SpriteSheet {
        val unkFlag = byteReader.next16()

        val numMesh = byteReader.next16()
        val meshes = ArrayList<MeshBuffer>()

        // This is only set for lens-flare type effects
        // The sprites are rendered proportionally across a screen-space vector, and this controls the % along the vector
        val lensFlareFlag = byteReader.next8()
        if (lensFlareFlag < 0 || lensFlareFlag > 1) oops(byteReader, "$lensFlareFlag")
        val lensFlareEffect = lensFlareFlag == 1

        byteReader.next8()
        byteReader.next8()

        val normalizationFlag = byteReader.next8()
        val unnormalizedTexCoords = (unkFlag == 1) && (normalizationFlag == 0)
        val texCoordNormalizationFactor = if (unnormalizedTexCoords) { 1f/256f } else { 1f }

        // Texture name
        val textureName = byteReader.nextString(0x10)

        val offsets = ArrayList<Float>()

        // Mesh
        for (i in 0 until numMesh) {
            val unk1 = byteReader.next16()
            if (unk1 != 0x01) oops(byteReader, "Expected unk1 to be 0x1, but was: ${unk1.toString(0x10)}")

            val numQuads = byteReader.next8()
            val unk2 = byteReader.next8()

            if (lensFlareEffect) {
                val distance = byteReader.nextFloat()
                offsets.add(distance)

                // Have seen 0 or NaN...
                byteReader.nextFloat()
                byteReader.nextFloat()
                byteReader.nextFloat()
            }

            val numVerts = 6 * numQuads
            val glBufferBuilder = GlBufferBuilder(numVerts)

            for (j in 0 until numVerts) {
                glBufferBuilder.appendSpriteSheetVertex(
                    byteReader.nextVector3f(),
                    byteReader.nextRGBA(),
                    byteReader.nextFloat() * texCoordNormalizationFactor,
                    byteReader.nextFloat() * texCoordNormalizationFactor,
                )
            }

            meshes.add(MeshBuffer(
                numVertices = numVerts,
                meshType = MeshType.TriMesh,
                textureStage0 = TextureLink.of(textureName, sectionHeader.localDir),
                glBuffer = glBufferBuilder.build()
            ))
        }

        byteReader.align0x10()
        if (byteReader.position != sectionHeader.sectionStartPosition + sectionHeader.sectionSize) {
            oops(byteReader, "[SpriteSheet] Unknown data")
        }

        return SpriteSheet(meshes, offsets)
    }

}
