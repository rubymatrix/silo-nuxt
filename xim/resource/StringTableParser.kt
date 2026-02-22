package xim.resource

data class StringTableBlock(val entries: List<StringTableEntry>) {
    fun first(): String {
        return getString(index = 0) ?: ""
    }

    fun getString(index: Int): String? {
        return entries.getOrNull(index)?.string
    }

    fun getStringOrBlank(index: Int): String {
        return getString(index) ?: ""
    }

    operator fun get(index: Int) = getStringOrBlank(index)

}

data class StringTableEntry(val flag: Int, val string: String)

object StringTableParser {

    fun read(byteReader: ByteReader, bitMask: Byte): List<StringTableBlock> {
        val start = byteReader.position

        val header = byteReader.nextZeroTerminatedString()
        if (header != "d_msg") { throw IllegalStateException("Unexpected header: $header") }

        byteReader.position = start + 0x10
        val unk0 = byteReader.next32()
        val fileSize = byteReader.next32()

        val tableOffset = byteReader.next32()
        val tableSize = byteReader.next32() // Table is optional, so this can be 0

        val stringBlockSize = byteReader.next32()
        val stringSectionSize = byteReader.next32() // If table isn't set, this should be provided
        val numStrings = byteReader.next32()

        val unk1 = byteReader.next32()

        if (bitMask != 0.toByte()) {
            byteReader.position = start + tableOffset
            for (i in 0 until fileSize - tableOffset) {
                byteReader.xorNext8(bitMask)
            }
        }

        byteReader.position = start + tableOffset
        return if (tableSize == 0) {
            parseStringsWithoutTable(byteReader, numStrings, stringBlockSize)
        } else {
            parseStringsWithTable(byteReader, numStrings)
        }
    }

    private fun parseStringsWithTable(byteReader: ByteReader, numStrings: Int): List<StringTableBlock> {
        val offsets = ArrayList<Int>()
        for (i in 0 until numStrings) {
            offsets += byteReader.next32()
            val unk = byteReader.next32()
        }

        val stringStart = byteReader.position
        val strings = ArrayList<StringTableBlock>(numStrings)

        for (i in 0 until numStrings) {
            byteReader.position = stringStart + offsets[i]
            strings += parseStringBlock(byteReader)
        }

        return strings
    }

    private fun parseStringsWithoutTable(byteReader: ByteReader, numStrings: Int, stringBlockSize: Int): List<StringTableBlock> {
        val start = byteReader.position
        val strings = ArrayList<StringTableBlock>(numStrings)

        for (i in 0 until numStrings) {
            byteReader.position = start + stringBlockSize * i
            strings += parseStringBlock(byteReader)
        }

        return strings
    }

    private fun parseStringBlock(byteReader: ByteReader): StringTableBlock {
        val start = byteReader.position
        val stringsInBlock = byteReader.next32()
        val strings = ArrayList<StringTableEntry>(stringsInBlock)

        val offsets = ArrayList<Int>()
        for (i in 0 until stringsInBlock) {
            offsets += byteReader.next32()
            val unkFlag = byteReader.next32()
        }

        for (i in 0 until stringsInBlock) {
            byteReader.position = start + offsets[i]

            val marker = byteReader.next32()
            val string = if (marker == 1) {
                byteReader.position += 0x18
                byteReader.nextZeroTerminatedString()
            } else {
                ""
            }

            strings += StringTableEntry(marker, string)
        }

        return StringTableBlock(strings)
    }
}