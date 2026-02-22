package xim.resource

object BlockDecoder {

    fun decodeBlock(byteReader: ByteReader, blockSize: Int) {
        val blockStart = byteReader.position

        val factor2 = byteReader.bytes[blockStart + 0x02].countOneBits()
        val factorB = byteReader.bytes[blockStart + 0x0B].countOneBits()
        val factorC = byteReader.bytes[blockStart + 0x0C].countOneBits()

        val factor = (factor2 - factorB + factorC) % 5

        val rotateAmount = when (factor) {
            0 -> 7
            1 -> 1
            2 -> 6
            3 -> 2
            4 -> 5
            else -> 0
        }

        for (i in 0 until blockSize) {
            if (i == 0x02 || i == 0x0B || i == 0x0C) {
                byteReader.position += 1
            } else {
                byteReader.rotateNext8(rotateAmount)
            }
        }
    }

}