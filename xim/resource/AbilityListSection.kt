package xim.resource

import xim.poc.ActionTargetFilter

enum class AbilityCostType {
    Tp,
    Mp,
}

data class AbilityCost(val type: AbilityCostType, val value: Int)

enum class AbilityType(val type: Int) {
    None(0),
    JobAbility(1),
    PetCommand(2),
    WeaponSkill(3),
    JobTrait(4),
    PetAbility(6),
    PhantomRoll(8),
    QuickDraw(9),
    PetWard(10),
    Samba(11),
    Waltz(12),
    FlourishI(13),
    Jig(14),
    Step(16),
    FlourishII(17),
    FlourishIII(19),
    ;

    companion object {
        fun from(type: Int): AbilityType {
            return AbilityType.values().firstOrNull { it.type == type } ?: None
        }
    }
}

data class AbilityInfo(
    val fileOffset: Int,
    val index: Int,
    private val rawType: Int,
    val type: AbilityType,
    val recastId: Int,
    val cost: Int,
    val lowResIconId: Int,
    val hiResIconId: Int,
    val targetFlags: Int,
    val aoeSize: Int,
    val aoeType: AoeType,
) {

    val targetFilter by lazy { ActionTargetFilter(targetFlags) }

    fun getNumVariants(): Int {
        return when(index) {
            608 -> 6 // Wild Card
            else -> 1
        }
    }

}

class AbilityListSection(val sectionHeader: SectionHeader) : ResourceParser {

    private val blockSize = 0x30

    override fun getResource(byteReader: ByteReader): ParserResult {
        val numElements = (sectionHeader.sectionSize - 0x10) / blockSize

        byteReader.offsetFromDataStart(sectionHeader)
        decode(byteReader, numElements)

        byteReader.offsetFromDataStart(sectionHeader)
        val abilities = read(byteReader, numElements)

        val abilitiesById = abilities.associateBy { it.index }.toMutableMap()
        return ParserResult.from(AbilityListResource(sectionHeader.sectionId, abilitiesById))
    }

    private fun decode(byteReader: ByteReader, numElements: Int) {
        for (i in 0 until numElements) {
            val start = byteReader.position
            BlockDecoder.decodeBlock(byteReader, blockSize)
            byteReader.position = start + blockSize
        }
    }

    private fun read(byteReader: ByteReader, numElements: Int): List<AbilityInfo> {
        val abilities = ArrayList<AbilityInfo>(numElements)

        for (i in 0 until numElements) {
            val start = byteReader.position
            abilities.add(readElement(byteReader))
            byteReader.position = start + blockSize
        }

        return abilities
    }

    private fun readElement(byteReader: ByteReader): AbilityInfo {
        val fileOffset = byteReader.position

        val index = byteReader.next16()
        val rawType = byteReader.next8()
        val lowResIconId = byteReader.next8()
        val hiResIconId = byteReader.next16()

        val cost = byteReader.next16()
        val recastId = byteReader.next16()

        val targetFlags = byteReader.next16()

        val unk2 = byteReader.next16()
        val unk3 = byteReader.next16()
        val unk4 = byteReader.next8()

        val aoeSize = byteReader.next8()
        val aoeType = byteReader.next8()

        return AbilityInfo(
            fileOffset = fileOffset,
            index = index,
            rawType = rawType,
            type = AbilityType.from(rawType),
            cost = cost,
            recastId = recastId,
            lowResIconId = lowResIconId,
            hiResIconId = hiResIconId,
            targetFlags = targetFlags,
            aoeSize = aoeSize,
            aoeType = AoeType.values()[aoeType],
        )
    }

}