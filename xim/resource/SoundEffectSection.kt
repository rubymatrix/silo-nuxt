package xim.resource

class SoundEffectPointerSection(val sectionHeader: SectionHeader) : ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val resource = read(byteReader)
        return ParserResult.from(resource)
    }

    private fun read(byteReader: ByteReader): SoundPointerResource {
        byteReader.offsetFromDataStart(sectionHeader, 0x0)
        val type = byteReader.nextString(0x8)
        if (!type.startsWith("SeSep  ")) { oops(byteReader, "Wasn't sesep :( $type") }

        val id = byteReader.next32()
        val folderId = (id / 1000).toString().padStart(3, '0')
        val fileId = id.toString().padStart(6, '0')

        return SoundPointerResource(sectionHeader.sectionId, soundId = id, folderId = folderId, fileId = fileId)
    }

}
