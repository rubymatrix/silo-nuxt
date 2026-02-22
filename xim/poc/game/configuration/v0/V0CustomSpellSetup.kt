package xim.poc.game.configuration.v0

import xim.poc.MobSkillToBlueMagicOverride
import xim.poc.game.configuration.constants.*
import xim.resource.AoeType
import xim.resource.MagicType
import xim.resource.SpellElement
import xim.resource.SpellInfo
import xim.resource.table.*
import xim.resource.table.MobSkillInfoTable.toMobSkillInfo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val spellFeralPeck_516 = spell_516
val spellDiamondShell_518 = spell_518
val spellRockSmash_520 = spell_520
val spellFluorescence_523 = spell_523
val spellRejuvenation_525 = spell_525
val spellRevelation_526 = spell_526
val spellAutumnBreeze_528 = spell_528
val spellNightStalker_546 = spell_546
val spellSanguinarySlash_550 = spell_550
val spellMow_552 = spell_552
val spellMemoryofEarth_553 = spell_553
val spellZephyrArrow_556 = spell_556
val spellHellbornYawp_562 = spell_562
val spellFeatherMaelstrom_600 = spell_600
val spellPhantasmalDance_601 = spell_601
val spellSeedofJudgement_602 = spell_602
val spellViciousKick_691 = spell_691
val spellVolcanicWrath_754 = spell_754
val spellMagmaHoplon_755 = spell_755
val spellBloodyCaress_756 = spell_756
val spellPredatoryGlare_757 = spell_757
val spellLethalTriclip_758 = spell_758
val spellKaleidoscopicFury_759 = spell_759
val spellRhinowrecker_760 = spell_760
val spellCatharsis_761 = spell_761
val spellFreezingGale_762 = SpellSkillId(762)

private class SpellOverride(
    val spellId: SpellSkillId,
    val mobSkillId: MobSkillId,
    val element: SpellElement,
    val castingLockTime: Duration = 1.seconds,
    val movementLockTime: Int? = null,
    val standardJointRemap: Map<Int, Int> = emptyMap(),
)

object V0CustomSpellSetup {

    private val overrides = mapOf(
        spellFeralPeck_516 to SpellOverride(spellId = spellFeralPeck_516, mobSkillId = mskillFeralPeck_2173, element = SpellElement.None),
        spellDiamondShell_518 to SpellOverride(spellId = spellDiamondShell_518, mobSkillId = mskillDiamondShell_1947, element = SpellElement.Earth, movementLockTime = 0),
        spellRockSmash_520 to SpellOverride(spellId = spellRockSmash_520, mobSkillId = mskillRockSmash_1487, element = SpellElement.Earth),
        spellFluorescence_523 to SpellOverride(spellId = spellFluorescence_523, mobSkillId = mskillFluorescence_1121, element = SpellElement.Fire),
        spellRejuvenation_525 to SpellOverride(spellId = spellRejuvenation_525, mobSkillId = mskillRejuvenation_3366, element = SpellElement.Light, castingLockTime = 2.seconds),
        spellRevelation_526 to SpellOverride(spellId = spellRevelation_526, mobSkillId = mskillRevelation_3367, element = SpellElement.None, movementLockTime = 0),
        spellAutumnBreeze_528 to SpellOverride(spellId = spellAutumnBreeze_528, mobSkillId = mskillAutumnBreeze_1941, element = SpellElement.Earth, movementLockTime = 0),
        spellNightStalker_546 to SpellOverride(spellId = spellNightStalker_546, mobSkillId = mskillNightStalker_2624, element = SpellElement.Earth, movementLockTime = 0),
        spellSanguinarySlash_550 to SpellOverride(spellId = spellSanguinarySlash_550, mobSkillId = mskillSanguinarySlash_2692, element = SpellElement.Earth, movementLockTime = 0),
        spellMow_552 to SpellOverride(spellId = spellMow_552, mobSkillId = mskillMow_244, element = SpellElement.None, castingLockTime = 2.seconds),
        spellMemoryofEarth_553 to SpellOverride(spellId = spellMemoryofEarth_553, mobSkillId = mskillMemoryofEarth_969, element = SpellElement.Earth),
        spellZephyrArrow_556 to SpellOverride(spellId = spellZephyrArrow_556, mobSkillId = mskillZephyrArrow_1937, element = SpellElement.Wind),
        spellHellbornYawp_562 to SpellOverride(spellId = spellHellbornYawp_562, mobSkillId = mskillHellbornYawp_2116, element = SpellElement.Dark),
        spellFeatherMaelstrom_600 to SpellOverride(spellId = spellFeatherMaelstrom_600, mobSkillId = mskillFeatherMaelstrom_2157, element = SpellElement.None, movementLockTime = 144),
        spellPhantasmalDance_601 to SpellOverride(spellId = spellPhantasmalDance_601, mobSkillId = mskillPhantasmalDance_2155, element = SpellElement.None, movementLockTime = 144),
        spellSeedofJudgement_602 to SpellOverride(spellId = spellSeedofJudgement_602, mobSkillId = mskillSeedofJudgment_2163, element = SpellElement.Light, movementLockTime = 144),
        spellViciousKick_691 to SpellOverride(spellId = spellViciousKick_691, mobSkillId = mskillViciousKick_2279, element = SpellElement.None, movementLockTime = 0),
        spellVolcanicWrath_754 to SpellOverride(spellId = spellVolcanicWrath_754, mobSkillId = mskillVolcanicWrath_2679, element = SpellElement.Fire, castingLockTime = 2.seconds),
        spellMagmaHoplon_755 to SpellOverride(spellId = spellMagmaHoplon_755, mobSkillId = mskillMagmaHoplon_1533, element = SpellElement.Fire),
        spellBloodyCaress_756 to SpellOverride(spellId = spellBloodyCaress_756, mobSkillId = mskillBloodyCaress_1911, element = SpellElement.Dark, standardJointRemap = mapOf(24 to 7)),
        spellPredatoryGlare_757 to SpellOverride(spellId = spellPredatoryGlare_757, mobSkillId = mskillPredatoryGlare_1424, element = SpellElement.Earth),
        spellLethalTriclip_758 to SpellOverride(spellId = spellLethalTriclip_758, mobSkillId = mskillLethalTriclip_2133, element = SpellElement.None, castingLockTime = 2.5.seconds),
        spellKaleidoscopicFury_759 to SpellOverride(spellId = spellKaleidoscopicFury_759, mobSkillId = mskillKaleidoscopicFury_2502, element = SpellElement.Light, castingLockTime = 2.seconds),
        spellRhinowrecker_760 to SpellOverride(spellId = spellRhinowrecker_760, mobSkillId = mskillRhinowrecker_2567, element = SpellElement.None, movementLockTime = 100, standardJointRemap = mapOf(24 to 23, 32 to 23)),
        spellCatharsis_761 to SpellOverride(spellId = spellCatharsis_761, mobSkillId = mskillCatharsis_184, element = SpellElement.Light, movementLockTime = 20),
        spellFreezingGale_762 to SpellOverride(spellId = spellFreezingGale_762, mobSkillId = mskill_2811, element = SpellElement.Ice, movementLockTime = 100),
    )

    fun setup() {
        for ((_, override) in overrides) {
            val mobSkillInfo = override.mobSkillId.toMobSkillInfo()
            val mobSkillAnimationIndex = MobSkillInfoTable.getFileTableOffset(mobSkillInfo.animationId)

            SpellAnimationTable.mutate(override.spellId.id, mobSkillAnimationIndex - SpellAnimationTable.fileTableOffset)
            SpellInfoTable.mutate(override.spellId.id, makeSpellInfo(override, mobSkillInfo))

            val mobSkillName = MobSkillNameTable[override.mobSkillId.id]
            SpellNameTable.mutate(override.spellId.id, listOf(mobSkillName))
        }
    }

    fun getCastingLockTime(spellId: SpellSkillId): Duration? {
        return overrides[spellId]?.castingLockTime
    }

    fun getOverrideInfo(skillId: SkillId): MobSkillToBlueMagicOverride? {
        val override = overrides[skillId] ?: return null
        return MobSkillToBlueMagicOverride(
            movementLockDuration = override.movementLockTime,
            standardJointRemap = override.standardJointRemap,
        )
    }

    private fun makeSpellInfo(override: SpellOverride, mobSkillInfo: MobSkillInfo): SpellInfo {
        val simpleIconId = when (override.element) {
            SpellElement.None -> 64
            else -> 56 + override.element.index
        }

        return SpellInfo(
            fileOffset = -1,
            index = override.spellId.id,
            spellId = -1,
            simpleIconId = simpleIconId,
            iconId = -1,
            targetFlags = mobSkillInfo.targetFlag,
            magicType = MagicType.BlueMagic,
            element = override.element,
            castTime = 0,
            recastDelay = 0,
            mpCost = 0,
            aoeType = AoeType.None,
            aoeSize = 0,
        )
    }

}