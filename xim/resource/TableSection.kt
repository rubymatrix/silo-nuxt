package xim.resource

class Table(val entries: List<Int>)

class TableSection(val sectionHeader: SectionHeader): ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val entries = read(byteReader)
        val resource = TableResource(sectionHeader.sectionId, Table(entries))
        return ParserResult.from(resource)
    }

    private fun read(byteReader: ByteReader): List<Int> {
        byteReader.offsetFromDataStart(sectionHeader)

        val numElements = (sectionHeader.sectionSize - 0x10) / 2
        val elements = ArrayList<Int>(numElements)

        for (i in 0 until numElements) {
            elements += byteReader.next16Signed()
        }

        return elements
    }

}