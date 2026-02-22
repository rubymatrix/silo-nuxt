package xim.resource

data class KeyFrameEntry(val time: Float, val value: Float)

class ParticleKeyFrameData(val data: List<KeyFrameEntry>) {

    fun getCurrentValue(lifeProgress: Float, initialValueOverride: Float? = null): Float {
        if (lifeProgress >= 1f) { return data.last().value }

        val nextIndex = data.indexOfFirst { it.time > lifeProgress }
        val prevIndex = nextIndex - 1

        val nextEntry = data[nextIndex]
        val prevEntry = data[prevIndex]

        val nextValue = nextEntry.value
        val prevValue = if (prevIndex == 0 && initialValueOverride != null) { initialValueOverride } else { prevEntry.value }

        val interpolationFactor = (lifeProgress - prevEntry.time) / (nextEntry.time - prevEntry.time)
        return (1f - interpolationFactor) * prevValue + interpolationFactor * nextValue
    }

}

class ParticleKeyFrameValueSection(private val sectionHeader: SectionHeader) : ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val data = read(byteReader)
        val particleKeyFrameResource = KeyFrameResource(sectionHeader.sectionId, data)
        return ParserResult.from(particleKeyFrameResource)
    }

    private fun read(byteReader: ByteReader): ParticleKeyFrameData {
        byteReader.offsetFrom(sectionHeader, 0x10)

        val values = ArrayList<KeyFrameEntry>()

        while (true) {
            val time = byteReader.nextFloat()
            val value = byteReader.nextFloat()
            values += KeyFrameEntry(time = time, value = value)
            if (time == 1.0f) { break }
        }

        return ParticleKeyFrameData(values)
    }

}
