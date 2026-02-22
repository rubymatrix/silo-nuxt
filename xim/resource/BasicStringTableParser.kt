package xim.resource

object BasicStringTableParser {

    fun read(byteReader: ByteReader, bitMask: Byte): List<String> {
        val tableSize = byteReader.next32()
        val baseOffset = byteReader.position

        if (bitMask != 0.toByte()) {
            while (byteReader.hasMore()) { byteReader.xorNext8(bitMask) }
        }

        byteReader.position = baseOffset
        val firstEntryOffset = baseOffset + byteReader.next32()

        byteReader.position = baseOffset
        val entries = ArrayList<String>()

        while (byteReader.position < firstEntryOffset) {
            val position = byteReader.position
            byteReader.position = baseOffset + byteReader.next32()

            entries += byteReader.nextZeroTerminatedString()
            byteReader.position = position + 0x4
        }

        return entries
    }

}