package xim.resource

import xim.poc.gl.ByteColor

data class Blur(val offset: Float, val color: ByteColor)

data class BlurConfig(val blurs: List<Blur>)

class BlurResourceSection(val sectionHeader: SectionHeader): ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val config = read(byteReader)
        return ParserResult.from(BlurResource(sectionHeader.sectionId, config))
    }

    private fun read(byteReader: ByteReader): BlurConfig {
        val unk0 = byteReader.next8() //bit-field?
        val numBlurs = byteReader.next8()
        val unk1 = byteReader.next16()

        val blurs = ArrayList<Blur>(numBlurs)

        // The first blur is defined differently - not sure how it works, these values seem to be ignored?
        byteReader.nextRGBA()
        byteReader.nextRGBA()

        blurs += Blur(offset = 0f, color = ByteColor.half)

        // Normal blur definitions
        for (i in 0 until numBlurs - 1) {
            blurs += Blur(offset = byteReader.nextFloat(), color = byteReader.nextRGBA())
        }

        // There's often remaining data - it seems to be junk?

        return BlurConfig(blurs)
    }

}