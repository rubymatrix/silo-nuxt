package xim.resource

import xim.poc.ActionTargetFilter
import xim.util.Fps.secondsToFrames

enum class AoeType(val index: Int) {
    None(0),
    Target(1),
    Cone(2),
    Source(3);

    fun isSourceCentered(): Boolean {
        return this == Cone || this == Source
    }

}

enum class MagicType {
    None,
    WhiteMagic,
    BlackMagic,
    Summoning,
    Ninjutsu,
    Songs,
    BlueMagic,
    Geomancy,
    Trust,
}

enum class SpellElement(val index: Int) {
    Fire(0),
    Ice(1),
    Wind(2),
    Earth(3),
    Lightning(4),
    Water(5),
    Light(6),
    Dark(7),
    None(15);

    companion object {
        operator fun get(index: Int): SpellElement {
            return SpellElement.values().firstOrNull { it.index == index } ?: None
        }
    }

}

enum class TargetFlag(val flag: Int) {
    None(0x00),
    Self(0x01),
    Player(0x02),
    Party(0x04),
    Ally(0x08),
    Npc(0x10),
    Enemy(0x20),
    Corpse(0x80),
    ;

    fun match(flags: Int): Boolean {
        return (this.flag and flags) != 0
    }

}

data class SpellInfo(
    val fileOffset: Int,
    val index: Int,
    val spellId: Int,
    val simpleIconId: Int,
    val iconId: Int,
    val targetFlags: Int,
    val magicType: MagicType,
    val element: SpellElement,
    val castTime: Int,
    val recastDelay: Int,
    val mpCost: Int,
    val aoeType: AoeType,
    val aoeSize: Int,
) {

    val targetFilter by lazy { ActionTargetFilter(targetFlags) }

    fun castTimeInFrames(): Float {
        return toFrames(castTime)
    }

    fun recastDelayInFrames(): Float {
        return toFrames(recastDelay)
    }

    private fun toFrames(time: Int): Float {
        // times are in units of 0.25 seconds
        val castTimeInSeconds = time.toFloat() / 4f
        return secondsToFrames(castTimeInSeconds)
    }

}

class SpellListSection(val sectionHeader: SectionHeader) : ResourceParser {

    private val blockSize = 0x64

    override fun getResource(byteReader: ByteReader): ParserResult {
        val numElements = (sectionHeader.sectionSize - 0x10) / blockSize

        byteReader.offsetFromDataStart(sectionHeader)
        decode(byteReader, numElements)

        byteReader.offsetFromDataStart(sectionHeader)
        val spells = read(byteReader, numElements)

        val spellsById = spells.associateBy { it.index }.toMutableMap()
        return ParserResult.from(SpellListResource(sectionHeader.sectionId, spellsById))
    }

    private fun decode(byteReader: ByteReader, numElements: Int) {
        for (i in 0 until numElements) {
            val start = byteReader.position
            BlockDecoder.decodeBlock(byteReader, blockSize)
            byteReader.position = start + blockSize
        }
    }

    private fun read(byteReader: ByteReader, numElements: Int): List<SpellInfo> {
        val spells = ArrayList<SpellInfo>(numElements)

        for (i in 0 until numElements) {
            val start = byteReader.position
            spells.add(readElement(byteReader))
            byteReader.position = start + blockSize
        }

        return spells
    }

    private fun readElement(byteReader: ByteReader): SpellInfo {
        val fileOffset = byteReader.position
        val index = byteReader.next16()
        val magicType = byteReader.next16()
        val element = byteReader.next16()
        val targetFlags = byteReader.next16()
        val skillType = byteReader.next16()
        val mpCost = byteReader.next16()
        val castTime = byteReader.next8()
        val recastDelay = byteReader.next8()

        val requiredLevelPerJob = ArrayList<Int>(24)
        for (i in 0 until 24) {
            requiredLevelPerJob.add(byteReader.next16Signed())
        }

        val id = byteReader.next16()
        val simpleIconId = byteReader.next16()
        val iconId = byteReader.next16Signed()

        val unk1 = byteReader.next8()
        val unk2 = byteReader.next8()

        val aoeSize = byteReader.next8()
        val aoeType = byteReader.next8()

        // TODO Remaining data

        return SpellInfo(
            fileOffset = fileOffset,
            index = index,
            spellId = id,
            simpleIconId = simpleIconId,
            iconId = iconId,
            targetFlags = targetFlags,
            castTime = castTime,
            recastDelay = recastDelay,
            mpCost = mpCost,
            magicType = MagicType.values()[magicType],
            element = SpellElement[element],
            aoeSize = aoeSize,
            aoeType = AoeType.values()[aoeType],
        )
    }

}