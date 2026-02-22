package xim.poc.game.configuration.v0

import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.CommonSkillHeuristics.avoidOverwritingBuff
import xim.poc.game.configuration.CommonSkillHeuristics.avoidOverwritingDebuff
import xim.poc.game.configuration.CommonSkillHeuristics.canDispelBuffs
import xim.poc.game.configuration.CommonSkillHeuristics.canEraseDebuffs
import xim.poc.game.configuration.CommonSkillHeuristics.minimumOf
import xim.poc.game.configuration.CommonSkillHeuristics.onlyIfBelowHppThreshold
import xim.poc.game.configuration.CommonSkillHeuristics.onlyIfBelowMppThreshold
import xim.poc.game.configuration.CommonSkillHeuristics.onlyIfFacingTarget
import xim.poc.game.configuration.CommonSkillHeuristics.preferFarAway
import xim.poc.game.configuration.CommonSkillHeuristics.requireAppearanceState
import xim.poc.game.configuration.CommonSkillHeuristics.targetIsBehindSource
import xim.poc.game.configuration.SkillApplierHelper.SwitchSkillResult
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluator.Companion.compose
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluator.Companion.noop
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.MobSkill.Companion.zeroCost
import xim.poc.game.configuration.v0.V0AbilityDefinitions.weaponSkillToEvents
import xim.poc.game.configuration.v0.V0SpellDefinitions.applyBasicBuff
import xim.poc.game.configuration.v0.V0SpellDefinitions.sourcePercentageHealingMagic
import xim.poc.game.configuration.v0.behaviors.MobBriareusController
import xim.poc.game.configuration.v0.behaviors.MobClusterBehavior
import xim.poc.game.configuration.v0.behaviors.YovraSkills
import xim.poc.game.event.*
import xim.resource.*
import xim.resource.table.MobSkillInfo
import xim.resource.table.MobSkillInfoTable
import xim.resource.table.MobSkillNameTable
import xim.util.Fps
import xim.util.PI_f
import xim.util.interpolate
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MobSkill(
    val rangeInfo: SkillRangeInfo,
    val castTime: Duration = 2.seconds,
    val lockTime: Duration = 2.5.seconds,
    val cost: V0AbilityCost = defaultCost,
    val skillChainAttributes: List<SkillChainAttribute> = emptyList(),
    val bypassParalysis: Boolean = false,
    val interruptable: Boolean = true,
    val element: SpellElement = SpellElement.None,
    val canMagicBurst: Boolean = false,
    val broadcastIds: List<DatId>? = null,
) {
    companion object {
        val zeroCost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Tp, value = 0), consumesAll = false)
        val defaultCost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Tp, value = 300), consumesAll = true)
    }
}

object MobSkills {

    private val definitions = HashMap<SkillId, MobSkill>()

    operator fun set(skill: MobSkillId, mobSkill: MobSkill) {
        definitions[skill] = mobSkill
    }

    operator fun get(skill: MobSkillId): MobSkill {
        return definitions[skill] ?: throw IllegalStateException("Mob skill $skill was not defined")
    }

}

object V0MobSkillDefinitions {

    val standardSingleTargetRange = SkillRangeInfo(30f, 0f, AoeType.None)
    val extendedSingleTargetRange = SkillRangeInfo(50f, 0f, AoeType.None)

    val standardSourceRange = SkillRangeInfo(8f, 8f, AoeType.Source)
    val extendedSourceRange = SkillRangeInfo(10f, 10f, AoeType.Source)
    val maxSourceRange = SkillRangeInfo(100f, 100f, AoeType.Source)

    val standardConeRange = SkillRangeInfo(12f, 12f, AoeType.Cone)
    val reverseConeRange = SkillRangeInfo(12f, 12f, AoeType.Cone, fixedRotation = PI_f)

    val smallStaticTargetAoe = SkillRangeInfo(24f, 4f, AoeType.Target, tracksTarget = false)
    val standardStaticTargetAoe = SkillRangeInfo(24f, 6f, AoeType.Target, tracksTarget = false)
    val extendedStaticTargetAoe = SkillRangeInfo(24f, 9f, AoeType.Target, tracksTarget = false)

    fun register() {
        // Rabbit: Foot Kick
        MobSkills[mskillFootKick_1] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFootKick_1] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.2f })

        // Rabbit: Dust Cloud
        MobSkills[mskillDustCloud_2] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillDustCloud_2] = SkillApplier(targetEvaluator = basicPhysicalDamage(
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 50 })
            ) { 2.2f },
        )

        // Rabbit: Whirl Claw
        MobSkills[mskillWhirlClaws_3] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillWhirlClaws_3] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.5f },
        )

        // Rabbit: Wild Carrot
        MobSkills[mskillWildCarrot_67] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillWildCarrot_67] = SkillApplier(targetEvaluator = sourcePercentageHealingMagic(25f))

        // Rabbit: Snow Cloud
        MobSkills[mskillSnowCloud_405] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSnowCloud_405] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 25 })
            ) { 2f },
        )

        // Sheep: Lamb Chop
        MobSkills[mskillLambChop_4] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillLambChop_4] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.5f },
        )

        // Sheep: Rage
        MobSkills[mskillRage_5] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRage_5] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Berserk, duration = 3.minutes),
        )
        SkillHeuristics.register(mskillRage_5, skillHeuristic = avoidOverwritingBuff(statusEffect = StatusEffect.Berserk, score = 1.0))

        // Sheep: Sheep Charge
        MobSkills[mskillSheepCharge_6] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSheepCharge_6] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(
                attackEffects = AttackEffects(knockBackMagnitude = 2)
            ) { 2.25f },
        )

        // Sheep: Sheep Song
        MobSkills[mskillSheepSong_8] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSheepSong_8] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Sleep, baseDuration = 18.seconds)
        ))

        // Ram: Rage
        MobSkills[mskillRage_9] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRage_9] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Berserk, duration = 3.minutes))

        // Ram: Ram Charge
        MobSkills[mskillRamCharge_10] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRamCharge_10] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(
                attackEffects = AttackEffects(knockBackMagnitude = 2)
            ) { 2.5f },
        )

        // Ram: Rumble
        MobSkills[mskillRumble_11] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillRumble_11] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(StatusEffect.AgiDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.66f })
        )

        // Ram: Great Bleat
        MobSkills[mskillGreatBleat_12] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillGreatBleat_12] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(StatusEffect.HPDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.66f })
        )

        // Ram: Petribreath
        MobSkills[mskillPetribreath_13] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillPetribreath_13] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Petrify, baseDuration = 8.seconds)
        ))

        // Tiger Skills
        MobSkills[mskillRoar_14] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillRoar_14] = SkillApplier(targetEvaluator = basicDebuff(attackStatusEffect = AttackStatusEffect(
            statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 50 }
        ))
        SkillHeuristics[mskillRoar_14] = avoidOverwritingDebuff(StatusEffect.Paralysis)

        MobSkills[mskillRazorFang_15] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRazorFang_15] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        MobSkills[mskillClawCyclone_17] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillClawCyclone_17] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        MobSkills[mskillPredatoryGlare_1424] = MobSkill(castTime = 1.5.seconds, rangeInfo = standardConeRange)
        SkillAppliers[mskillPredatoryGlare_1424] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true, attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Terror, baseDuration = 5.seconds))
        )))
        SkillHeuristics[mskillPredatoryGlare_1424] = avoidOverwritingDebuff(StatusEffect.Terror)

        MobSkills[mskillCrossthrash_1425] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillCrossthrash_1425] = SkillApplier(targetEvaluator = basicPhysicalDamage { 3f })

        // Opo-opo: Vicious Claw
        MobSkills[mskillViciousClaw_32] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillViciousClaw_32] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.25f }
        )

        // Opo-opo: Spinning Claw
        MobSkills[mskillSpinningClaw_34] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSpinningClaw_34] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.25f }
        )

        // Opo-opo: Claw Storm
        MobSkills[mskillClawStorm_35] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillClawStorm_35] = SkillApplier(targetEvaluator = basicPhysicalDamage(
                attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
            ) { 1.75f }
        )

        // Opo-opo: Blank Gaze
        MobSkills[mskillBlankGaze_36] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBlankGaze_36] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = AttackEffects(gazeAttack = true, dispelCount = 2)
        ))

        // Opo-opo: Eye Scratch
        MobSkills[mskillEyeScratch_38] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillEyeScratch_38] = SkillApplier(
            targetEvaluator = basicPhysicalDamage (
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 25 })
            ) { 1.75f },
        )

        // Opo-opo: Magic Fruit
        MobSkills[mskillMagicFruit_39] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillMagicFruit_39] = SkillApplier(targetEvaluator = sourcePercentageHealingMagic(25f))

        // Mandragora: Head Butt
        MobSkills[mskillHeadButt_44] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHeadButt_44] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2f },
        )

        // Mandragora: Dream Flower
        MobSkills[mskillDreamFlower_45] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillDreamFlower_45] = SkillApplier(
            targetEvaluator = basicDebuff(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Sleep, baseDuration = 18.seconds))
        )

        // Mandragora: Wild Oats
        MobSkills[mskillWildOats_46] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillWildOats_46] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.VitDown, baseDuration = 15.seconds) {
                    it.statusState.secondaryPotency = 0.8f
                })
            ) { 1f },
        )

        // Mandragora: Photosynthesis
        MobSkills[mskillPhotosynthesis_48] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPhotosynthesis_48] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Regen, duration = 18.seconds) {
                it.status.potency = (it.context.sourceState.getMaxHp() / 20f).roundToInt().coerceAtLeast(1)
            }
        )

        // Mandragora: Leaf Dagger
        MobSkills[mskillLeafDagger_49] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillLeafDagger_49] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
            ) { 1f },
        )

        // Mandragora: Scream
        MobSkills[mskillScream_50] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillScream_50] = SkillApplier(
            targetEvaluator = basicDebuff(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.MndDown, baseDuration = 15.seconds) {
                it.statusState.secondaryPotency = 0.8f
            }),
        )

        // Mandragora: Demonic Flower
        MobSkills[mskillDemonicFlower_2154] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillDemonicFlower_2154] = SkillApplier(
            additionalSelfEvaluator = sourceCurrentHpDamage(percent = 0.5f, damageType = AttackDamageType.Static),
            targetEvaluator = basicMagicalWs(ftp = sourceCurrentHppPower(zeroHpPower = 1f, maxHpPower = 10f))
        )
        SkillHeuristics[mskillDemonicFlower_2154] = onlyIfBelowHppThreshold(0.5)

        // Funguar: Frog Kick
        MobSkills[mskillFrogkick_52] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFrogkick_52] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2f })

        // Funguar: Spore
        MobSkills[mskillSpore_53] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSpore_53] = SkillApplier(targetEvaluator = basicDebuff(StatusEffect.Paralysis, 15.seconds) { it.statusState.potency = 50 })

        // Funguar: Queasyshroom
        MobSkills[mskillQueasyshroom_54] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillQueasyshroom_54] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
        ) { 1f })

        // Funguar: Numbshroom
        MobSkills[mskillNumbshroom_55] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillNumbshroom_55] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 30 },
            AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 5.seconds),
        ))) { 2f })

        // Funguar: Shakeshroom
        MobSkills[mskillShakeshroom_56] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillShakeshroom_56] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
            AttackStatusEffect(statusEffect = StatusEffect.Disease, baseDuration = 15.seconds),
        )) { 3f })

        // Funguar: Silence Gas
        MobSkills[mskillSilenceGas_58] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSilenceGas_58] = SkillApplier(targetEvaluator = basicPhysicalDamage (attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Silence, baseDuration = 15.seconds)
        )) { 2f })

        // Funguar: Dark Spore
        MobSkills[mskillDarkSpore_59] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillDarkSpore_59] = SkillApplier(targetEvaluator = basicPhysicalDamage (attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 50 }
        )) { 2f })

        // Morbol Skills
        MobSkills[mskillImpale_60] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillImpale_60] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 30 })
        ) { 2f })

        MobSkills[mskillVampiricLash_61] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = 3.seconds)
        SkillAppliers[mskillVampiricLash_61] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, absorptionResource = ActorResourceType.HP, proportionalAbsorption = true))
        ) { 2f })

        MobSkills[mskillBadBreath_63] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillBadBreath_63] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            spellDamageDoT(StatusEffect.Poison, 1f, 9.seconds),
            AttackStatusEffect(baseChance = 0.67f, statusEffect = StatusEffect.Bind, baseDuration = 10.seconds),
            AttackStatusEffect(baseChance = 0.67f, statusEffect = StatusEffect.Silence, baseDuration = 9.seconds),
            AttackStatusEffect(baseChance = 0.67f, statusEffect = StatusEffect.Slow, baseDuration = 9.seconds) { it.statusState.potency = 50 },
            AttackStatusEffect(baseChance = 0.67f, statusEffect = StatusEffect.Paralysis, baseDuration = 9.seconds) { it.statusState.potency = 50 },
            AttackStatusEffect(baseChance = 0.67f, statusEffect = StatusEffect.Weight, baseDuration = 9.seconds) { it.statusState.potency = 50 },
            AttackStatusEffect(baseChance = 0.67f, statusEffect = StatusEffect.Blind, baseDuration = 9.seconds) { it.statusState.potency = 50 },
        ))) { 1f })

        MobSkills[mskillSweetBreath_64] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSweetBreath_64] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Sleep, baseDuration = 10.seconds),
        ))) { 2f })

        MobSkills[mskillRancidBreath_3248] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillRancidBreath_3248] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Terror, baseDuration = 10.seconds),
        ))) { 2f })

        // Fly: Somersault
        MobSkills[mskillSomersault_62] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSomersault_62] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2f },
        )

        // Treant: Drillbranch
        MobSkills[mskillDrillBranch_72] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillDrillBranch_72] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.25f },
        )

        // Treant: Pinecone Bomb
        MobSkills[mskillPineconeBomb_73] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillPineconeBomb_73] = SkillApplier(
            targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(StatusEffect.Sleep, baseDuration = 10.seconds))
            )) { 2f },
        )

        // Treant: Leafstorm
        MobSkills[mskillLeafstorm_75] = MobSkill(castTime = 4.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillLeafstorm_75] = SkillApplier(
            targetEvaluator = basicMagicalWs { 3f },
        )

        // Treant: Entangle
        MobSkills[mskillEntangle_76] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillEntangle_76] = SkillApplier(
            targetEvaluator = basicMagicalWs(attackEffects = singleStatus(
                attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 5.seconds)
            )) { 1.5f },
        )

        // Bee: Sharp sting
        MobSkills[mskillSharpSting_78] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSharpSting_78] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.25f },
        )

        // Bee: Pollen
        MobSkills[mskillPollen_79] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPollen_79] = SkillApplier(targetEvaluator = sourcePercentageHealingMagic(25f))

        // Bee: Final Sting
        MobSkills[mskillFinalSting_80] = MobSkill(castTime = 5.seconds, rangeInfo = extendedSingleTargetRange)
        SkillAppliers[mskillFinalSting_80] = SkillApplier(
            additionalSelfEvaluator = compose(changeAppearanceState(1), defeatSelf()),
            targetEvaluator = basicPhysicalDamage(ftp = sourceCurrentHppPower(zeroHpPower = 1f, maxHpPower = 8f))
        )
        SkillHeuristics[mskillFinalSting_80] = onlyIfBelowHppThreshold(0.5)

        // Beetle: Power Attack
        MobSkills[mskillPowerAttack_82] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPowerAttack_82] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.25f },)

        // Beetle: Hi Freq Field
        MobSkills[mskillHiFreqField_83] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHiFreqField_83] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(StatusEffect.AgiDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.66f })
        )
        SkillHeuristics[mskillHiFreqField_83] = avoidOverwritingDebuff(StatusEffect.AgiDown)

        // Beetle: Rhino Attack
        MobSkills[mskillRhinoAttack_84] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = 3.seconds)
        SkillAppliers[mskillRhinoAttack_84] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 4),
        ) { 3f })

        // Beetle: Rhino Guard
        MobSkills[mskillRhinoGuard_85] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRhinoGuard_85] = SkillApplier(applyBasicBuff(
            statusEffect = StatusEffect.EvasionBoost, duration = 15.seconds) { it.status.potency = 50 }
        )
        SkillHeuristics[mskillRhinoGuard_85] = avoidOverwritingDebuff(StatusEffect.EvasionBoost)

        // Beetle: Rhinowrecker
        MobSkills[mskillRhinowrecker_2567] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillRhinowrecker_2567] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 4),
        ) { 3f })

        // Crawler: Sticky Thread
        MobSkills[mskillStickyThread_88] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillStickyThread_88] = SkillApplier(targetEvaluator = basicDebuff(statusEffect = StatusEffect.Slow, duration = 15.seconds) { it.statusState.potency = 30 })
        SkillHeuristics[mskillStickyThread_88] = avoidOverwritingDebuff(StatusEffect.Slow)

        // Crawler: Poison Breath
        MobSkills[mskillPoisonBreath_89] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillPoisonBreath_89] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
        ) { 1.75f })

        // Crawler: Cocoon
        MobSkills[mskillCocoon_90] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillCocoon_90] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.DefenseBoost, duration = 30.seconds) { it.status.potency = 50 })

        // Crawler: Incinerate
        MobSkills[mskillIncinerate_1535] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillIncinerate_1535] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })

        // Diremite Skills
        MobSkills[mskillDoubleClaw_106] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillDoubleClaw_106] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 2) { 1f })

        MobSkills[mskillGrapple_107] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillGrapple_107] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.25f })

        MobSkills[mskillFilamentedHold_108] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillFilamentedHold_108] = SkillApplier(targetEvaluator = basicDebuff(statusEffect = StatusEffect.Slow, duration = 15.seconds) { it.statusState.potency = 50 })
        SkillHeuristics[mskillFilamentedHold_108] = avoidOverwritingDebuff(StatusEffect.Slow)

        MobSkills[mskillSpinningTop_109] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSpinningTop_109] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.25f })

        MobSkills[mskillGeoticSpin_3249] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillGeoticSpin_3249] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(StatusEffect.Petrify, baseDuration = 12.seconds),
                spellDamageDoT(StatusEffect.Rasp, duration = 15.seconds, potency = 0.5f, secondaryPotency = 0.8f),
            ))
        ) { 2f })

        // Raptor Skills
        MobSkills[mskillRipperFang_118] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRipperFang_118] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 6.seconds) { it.statusState.potency = 25 }),
        ) { 2f })

        MobSkills[mskillFrostBreath_121] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillFrostBreath_121] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackStat = CombatStat.int,
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 50 }),
        ) { 2f })

        MobSkills[mskillChompRush_123] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillChompRush_123] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            numHits = 3,
            ftpSpread = true,
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 6.seconds) { it.statusState.potency = 25 }),
        ) { 0.8f })

        MobSkills[mskillScytheTail_124] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillScytheTail_124] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(knockBackMagnitude = 2, attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 3.seconds))
        ) { 2.5f })

        // Wanderer: Vanity Dive
        MobSkills[mskillVanityDive_132] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillVanityDive_132] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2f },
        )

        // Wanderer: Empty Beleaguer
        MobSkills[mskillEmptyBeleaguer_133] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillEmptyBeleaguer_133] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.5f },
        )

        // Wanderer: Mirage
        MobSkills[mskillMirage_134] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillMirage_134] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.EvasionBoost, duration = 30.seconds) { it.status.potency = 40 }
        )

        // Wanderer: Aura of Persistence
        MobSkills[mskillAuraofPersistence_135] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillAuraofPersistence_135] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.DefenseBoost, duration = 30.seconds) { it.status.potency = 40 }
        )

        // Flock Bats: Sonic Boom
        MobSkills[mskillSonicBoom_137] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSonicBoom_137] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.AttackDown, baseDuration = 15.seconds) {
                it.statusState.potency = 50
            }
        )))

        // Flock Bats: Jet Stream
        MobSkills[mskillJetStream_139] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillJetStream_139] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 1f },
        )

        // Leech: Suction
        MobSkills[mskillSuction_158] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSuction_158] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2f })

        // Leech: Acid Mist
        MobSkills[mskillAcidMist_159] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillAcidMist_159] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.StrDown, baseDuration = 18.seconds) {
                it.statusState.secondaryPotency = 0.25f
            }
        )) { 1.5f })

        // Leech: Sand Breath
        MobSkills[mskillSandBreath_160] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSandBreath_160] = SkillApplier(targetEvaluator = basicPhysicalDamage (
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 50 })
            ) { 2f },
        )

        // Leech: Drain Kiss
        MobSkills[mskillDrainkiss_161] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillDrainkiss_161] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true),
        )) { 1.5f })

        // Leech: Regeneration
        MobSkills[mskillRegeneration_162] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRegeneration_162] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Regen, duration = 18.seconds) {
                it.status.potency = (it.context.sourceState.getMaxHp() / 20f).roundToInt().coerceAtLeast(1)
            }
        )

        // Leech: MP Drain Kiss
        MobSkills[mskillMPDrainkiss_165] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillMPDrainkiss_165] = SkillApplier(targetEvaluator = basicMagicalWs(damageCap = 30, attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 1f, absorptionResource = ActorResourceType.MP),
            damageResource = ActorResourceType.MP,
        )) { 1.5f })

        // Worm: Fullforce Blow
        MobSkills[mskillFullforceBlow_168] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFullforceBlow_168] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 3)) { 2.5f },
        )

        // Worm: Gastric Bomb
        MobSkills[mskillGastricBomb_169] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillGastricBomb_169] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.StrDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.8f })
        ) { 2f })

        // Worm: Sandspin
        MobSkills[mskillSandspin_170] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSandspin_170] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 50 })
        ) { 2f })

        // Worm: Tremors
        MobSkills[mskillTremors_171] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillTremors_171] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.DexDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.75f })
        ) { 2.25f })

        // Worm: MP Absorption
        MobSkills[mskillMPAbsorption_172] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillMPAbsorption_172] = SkillApplier(targetEvaluator = basicMagicalWs(damageCap = 30, attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 1f, absorptionResource = ActorResourceType.MP),
            damageResource = ActorResourceType.MP,
        )) { 2f })
        SkillHeuristics[mskillMPAbsorption_172] = onlyIfBelowMppThreshold(0.5)

        // Worm: Sound Vacuum
        MobSkills[mskillSoundVacuum_173] = MobSkill(castTime = 3.seconds, rangeInfo = standardSourceRange)
        SkillAppliers[mskillSoundVacuum_173] = SkillApplier(
            targetEvaluator = basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 12.seconds),
            ))),
        )
        SkillHeuristics[mskillSoundVacuum_173] = avoidOverwritingDebuff(StatusEffect.Silence)

        // Slime: Fluid Spread
        MobSkills[mskillFluidSpread_175] = MobSkill(castTime = 3.seconds, rangeInfo = standardSourceRange)
        SkillAppliers[mskillFluidSpread_175] = SkillApplier(
            targetEvaluator = basicMagicalWs{ 3f },
        )

        // Slime: Digest
        MobSkills[mskillDigest_177] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillDigest_177] = SkillApplier(
            targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
                absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true),
            )) { 1.5f },
        )

        // Hecteyes: Death Ray
        MobSkills[mskillDeathRay_181] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillDeathRay_181] = SkillApplier(targetEvaluator = basicMagicalWs { 3f })

        // Hecteyes: Hex Eye
        MobSkills[mskillHexEye_182] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillHexEye_182] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true, attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Paralysis, baseDuration = 30.seconds) { it.statusState.potency = 50 })
        )))
        SkillHeuristics[mskillHexEye_182] = avoidOverwritingDebuff(StatusEffect.Paralysis)

        // Hecteyes: Petro Gaze
        MobSkills[mskillPetroGaze_183] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillPetroGaze_183] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true, attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Petrify, baseDuration = 12.seconds))
        )))
        SkillHeuristics[mskillPetroGaze_183] = avoidOverwritingDebuff(StatusEffect.Petrify)

        // Hecteyes: Catharsis
        MobSkills[mskillCatharsis_184] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = 3.seconds)
        SkillAppliers[mskillCatharsis_184] = SkillApplier(targetEvaluator = sourcePercentageHealingMagic(25f))
        SkillHeuristics[mskillCatharsis_184] = onlyIfBelowHppThreshold(0.5)

        // Crab: Bubble Shower
        MobSkills[mskillBubbleShower_186] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillBubbleShower_186] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.StrDown, baseDuration = 15.seconds) {
                it.statusState.secondaryPotency = 0.5f
            })
        ) { 1f })

        // Crab: Bubble Curtain
        MobSkills[mskillBubbleCurtain_187] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBubbleCurtain_187] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.MagicDefBoost, 30.seconds) {
            it.status.potency = 50
        })

        // Crab: Big Scissors
        MobSkills[mskillBigScissors_188] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBigScissors_188] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2f })

        // Crab: Scissor Guard
        MobSkills[mskillScissorGuard_189] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillScissorGuard_189] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.DefenseBoost, 30.seconds) {
            it.status.potency = 50
        })

        // Crab: Metallic Body
        MobSkills[mskillMetallicBody_192] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillMetallicBody_192] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Stoneskin, 5.minutes) {
            it.status.counter = it.context.sourceState.getMaxHp() / 4
        })

        // Pugil: Intimidate
        MobSkills[mskillIntimidate_193] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillIntimidate_193] = SkillApplier(
            targetEvaluator = basicDebuff(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 15.seconds) { it.statusState.potency = 30 })
        )

        // Pugil: Aqua Ball
        MobSkills[mskillAquaBall_194] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillAquaBall_194] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.StrDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.8f })
        ) { 2f })

        // Pugil: Splash Breath
        MobSkills[mskillSplashBreath_195] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSplashBreath_195] = SkillApplier(targetEvaluator = basicMagicalWs { 2f })

        // Pugil: Screwdriver
        MobSkills[mskillScrewdriver_196] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillScrewdriver_196] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Pugil: Water Wall
        MobSkills[mskillWaterWall_197] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillWaterWall_197] = SkillApplier(
            targetEvaluator = applyBasicBuff(StatusEffect.DefenseBoost, duration = 18.seconds) { it.status.potency = 50 }
        )

        // Pugil: Water Shield
        MobSkills[mskillWaterShield_198] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillWaterShield_198] = SkillApplier(
            targetEvaluator = applyBasicBuff(StatusEffect.EvasionBoost, duration = 15.seconds) { it.status.potency = 33 }
        )

        // Sea Monk: Tentacle
        MobSkills[mskillTentacle_200] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillTentacle_200] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2f })

        // Sea Monk: Ink Jet
        MobSkills[mskillInkJet_202] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillInkJet_202] = SkillApplier(
            targetEvaluator = basicMagicalWs(
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 20 })
            ) { 2f },
        )

        // Sea Monk: Regeneration
        MobSkills[mskillHardMembrane_203] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHardMembrane_203] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.EvasionBoost, 12.seconds) { it.status.potency = 33 })
        SkillHeuristics[mskillHardMembrane_203] = avoidOverwritingBuff(StatusEffect.EvasionBoost)

        // Sea Monk: Cross Attack
        MobSkills[mskillCrossAttack_204] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillCrossAttack_204] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 2, ftpSpread = true) { 1.2f })

        // Sea Monk: Regeneration
        MobSkills[mskillRegeneration_205] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRegeneration_205] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Regen, 18.seconds) {
            it.status.potency = (it.context.sourceState.getMaxHp() * 0.05f).roundToInt()
        })
        SkillHeuristics[mskillRegeneration_205] = avoidOverwritingBuff(StatusEffect.Regen)

        // Sea Monk: Maelstrom
        MobSkills[mskillMaelstrom_206] = MobSkill(castTime = 3.seconds, rangeInfo = standardSourceRange)
        SkillAppliers[mskillMaelstrom_206] = SkillApplier(
            targetEvaluator = basicMagicalWs(
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.StrDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.8f })
            ) { 2.5f },
        )

        // Sea Monk: Whirlwind
        MobSkills[mskillWhirlwind_207] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillWhirlwind_207] = SkillApplier(
            targetEvaluator = basicMagicalWs(
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.VitDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.8f })
            ) { 2f },
        )

        // Ghost: Grave Reel
        MobSkills[mskillGraveReel_216] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillGraveReel_216] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true),
        )) { 2f })

        // Ghost: Ectosmash
        MobSkills[mskillEctosmash_217] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillEctosmash_217] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Ghost: Fear Touch
        MobSkills[mskillFearTouch_218] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFearTouch_218] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Slow, baseDuration = 15.seconds) { it.statusState.potency = 30 })
        ) { 1.75f })
        SkillHeuristics[mskillFearTouch_218] = avoidOverwritingDebuff(StatusEffect.Slow)

        // Ghost: Terror Touch
        MobSkills[mskillTerrorTouch_219] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillTerrorTouch_219] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.AttackDown, baseDuration = 15.seconds) { it.statusState.potency = 70 })
        ) { 1.75f })
        SkillHeuristics[mskillTerrorTouch_219] = avoidOverwritingDebuff(StatusEffect.AttackDown)

        // Ghost: Curse
        MobSkills[mskillCurse_220] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillCurse_220] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Curse, baseDuration = 18.seconds) { it.statusState.potency = 33 }),
        ))
        SkillHeuristics[mskillCurse_220] = avoidOverwritingDebuff(StatusEffect.Curse)

        // Ghost: Dark Sphere
        MobSkills[mskillDarkSphere_221] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillDarkSphere_221] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 30 })
        ) { 2.25f })

        // Ghost: Perdition
        MobSkills[mskillPerdition_1538] = MobSkill(castTime = 5.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPerdition_1538] = SkillApplier(targetEvaluator = basicMagicalWs { 4f })
        SkillHeuristics[mskillPerdition_1538] = { 0.1 }

        // Coeurl: Petrifactive Breath
        MobSkills[mskillPetrifactiveBreath_224] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillPetrifactiveBreath_224] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Petrify, baseDuration = 12.seconds)
        ))
        SkillHeuristics[mskillPetrifactiveBreath_224] = avoidOverwritingDebuff(StatusEffect.Petrify)

        // Coeurl: Frenzied Rage
        MobSkills[mskillFrenziedRage_225] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFrenziedRage_225] = SkillApplier(targetEvaluator = applyBasicBuff(
            statusEffect = StatusEffect.AttackBoost, duration = 30.seconds
        ) { it.status.potency = 33 })
        SkillHeuristics[mskillFrenziedRage_225] = avoidOverwritingBuff(StatusEffect.AttackBoost)

        // Coeurl: Pounce
        MobSkills[mskillPounce_226] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPounce_226] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.2f })

        // Coeurl: Charged Whisker
        MobSkills[mskillChargedWhisker_227] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillChargedWhisker_227] = SkillApplier(targetEvaluator = basicMagicalWs { 2.8f })

        // Coeurl: Blaster
        MobSkills[mskillBlaster_396] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBlaster_396] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true,
            bypassShadows = true,
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Petrify, baseDuration = 15.seconds),
            ))))
        SkillHeuristics[mskillBlaster_396] = avoidOverwritingDebuff(StatusEffect.Petrify)

        // Coeurl: Chaotic Eye
        MobSkills[mskillChaoticEye_397] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillChaoticEye_397] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true,
            bypassShadows = true,
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 15.seconds),
            ))))
        SkillHeuristics[mskillChaoticEye_397] = avoidOverwritingDebuff(StatusEffect.Silence)

        // Coeurl: Blink of Peril
        MobSkills[mskillBlinkofPeril_1953] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBlinkofPeril_1953] = SkillApplier(targetEvaluator = currentHpDamage(
            percent = 0.95f,
            damageType = AttackDamageType.Static,
            attackEffects = AttackEffects(gazeAttack = true, bypassShadows = true),
        ))

        // Coeurl: Mortal Blast
        MobSkills[mskillMortalBlast_2346] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillMortalBlast_2346] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(gazeAttack = true, bypassShadows = true),
        ) { 4f })

        // Coeurl: Preternatural Gleam
        MobSkills[mskillPreternaturalGleam_2504] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillPreternaturalGleam_2504] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(knockBackMagnitude = 2, attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 6.seconds),
                AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            ))
        ) { 2.25f })

        // Buffalo Skills
        MobSkills[mskillRampantGnaw_237] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRampantGnaw_237] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 6.seconds) { it.statusState.potency = 25 })
        ) { 2.25f })

        MobSkills[mskillBigHorn_238] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBigHorn_238] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 6)) { 2.25f },
        )

        MobSkills[mskillSnort_239] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSnort_239] = SkillApplier(targetEvaluator = basicMagicalWs (
            attackEffects = AttackEffects(knockBackMagnitude = 8)) { 2.5f },
        )

        MobSkills[mskillRabidDance_240] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRabidDance_240] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.EvasionBoost, 30.seconds) { it.status.potency = 33 })
        SkillHeuristics[mskillRabidDance_240] = avoidOverwritingBuff(StatusEffect.EvasionBoost)

        // Buffalo: Lowing
        MobSkills[mskillLowing_241] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillLowing_241] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 18.seconds) {
                it.statusState.potency = 5
            }),
        ))

        // Taurus: Triclip
        MobSkills[mskillTriclip_242] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillTriclip_242] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            numHits = 3,
            ftpSpread = true,
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.DexDown, baseDuration = 12.seconds) { it.statusState.secondaryPotency = 0.75f })
        ) { 0.8f })

        // Taurus: Back Swish
        MobSkills[mskillBackSwish_243] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillBackSwish_243] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 4),
        ) { 2.4f })

        // Taurus: Mow
        MobSkills[mskillMow_244] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillMow_244] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
        ) { 2f })

        // Taurus: Frightful Roar
        MobSkills[mskillFrightfulRoar_245] = MobSkill(rangeInfo = extendedSourceRange)
        SkillAppliers[mskillFrightfulRoar_245] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 15.seconds) { it.statusState.potency = 33 }),
        )
        SkillHeuristics[mskillFrightfulRoar_245] = avoidOverwritingDebuff(StatusEffect.DefenseDown)

        // Taurus: Mortal Ray
        MobSkills[mskillMortalRay_246] = MobSkill(castTime = 3.seconds, rangeInfo = standardConeRange)
        SkillAppliers[mskillMortalRay_246] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(gazeAttack = true)
        ) { 4f })

        // Taurus: Unblest Armor
        MobSkills[mskillUnblestArmor_247] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillUnblestArmor_247] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.DreadSpikes, duration = 30.seconds) {
            it.status.potency = intPotency(it.context.sourceState, 0.1f)
        })
        SkillHeuristics[mskillUnblestArmor_247] = avoidOverwritingBuff(StatusEffect.DreadSpikes)

        // Taurus: Chthonian Ray
        MobSkills[mskillChthonianRay_1103] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillChthonianRay_1103] = SkillApplier(targetEvaluator = noop())

        // Taurus: Lethal Triclip
        MobSkills[mskillLethalTriclip_2133] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds, lockTime = 3.seconds)
        SkillAppliers[mskillLethalTriclip_2133] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            numHits = 3,
            ftpSpread = true,
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.HPDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.66f }))
        { 0.7f })

        // Taurus: Lithic Ray
        MobSkills[mskillLithicRay_2277] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillLithicRay_2277] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true,
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Petrify, baseDuration = 15.seconds),
                spellDamageDoT(statusEffect = StatusEffect.Dia, duration = 15.seconds, potency = 0.25f, secondaryPotency = 0.8f, attackStat = CombatStat.mnd),
        ))))
        SkillHeuristics[mskillLithicRay_2277] = avoidOverwritingDebuff(StatusEffect.Petrify)

        // Bomb: Vulcanian Impact
        MobSkills[mskillVulcanianImpact_86] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillVulcanianImpact_86] = SkillApplier(targetEvaluator = basicMagicalWs { 2f } )

        // Bomb: Berserk
        MobSkills[mskillBerserk_254] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBerserk_254] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Berserk, duration = 3.minutes))
        SkillHeuristics[mskillBerserk_254] = avoidOverwritingBuff(StatusEffect.Berserk)

        // Bomb: Self-destruct
        MobSkills[mskillSelfDestruct_255] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillSelfDestruct_255] = SkillApplier(
            additionalSelfEvaluator = defeatSelf(),
            targetEvaluator = basicMagicalWs(ftp = sourceCurrentHppPower(zeroHpPower = 1f, maxHpPower = 10f))
        )
        SkillHeuristics[mskillSelfDestruct_255] = onlyIfBelowHppThreshold(0.5)

        // Bomb: Heat Wave
        MobSkills[mskillHeatWave_256] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillHeatWave_256] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = singleStatus(attackStatusEffect = spellDamageDoT(StatusEffect.Burn, duration = 15.seconds, potency = 0.2f, secondaryPotency = 0.8f))
        ))

        // Evil Weapon: Smite of Rage
        MobSkills[mskillSmiteofRage_257] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSmiteofRage_257] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Evil Weapon: Whirl of Rage
        MobSkills[mskillWhirlofRage_258] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillWhirlofRage_258] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Magic Pot: Double Ray
        MobSkills[mskillDoubleRay_264] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillDoubleRay_264] = SkillApplier(targetEvaluator = basicMagicalWs { 2.25f })

        // Magic Pot: Spinning Attack
        MobSkills[mskillSpinningAttack_265] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSpinningAttack_265] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.25f })

        // Magic Pot: Spectral Barrier
        MobSkills[mskillSpectralBarrier_266] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSpectralBarrier_266] = SkillApplier(targetEvaluator = applyBasicBuff(
            statusEffect = StatusEffect.MagicDefBoost, duration = 1.minutes) { it.status.potency = 100 }
        )
        SkillHeuristics[mskillSpectralBarrier_266] = avoidOverwritingBuff(StatusEffect.MagicDefBoost)

        // Magic Pot: Mysterious Light
        MobSkills[mskillMysteriousLight_267] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillMysteriousLight_267] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            attackStatusEffects = listOf(AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 50 })
        )) { 2.2f })

        // Magic Pot: Mind Drain
        MobSkills[mskillMindDrain_268] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillMindDrain_268] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.MndDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.8f }
        ))

        // Magic Pot: Battery Charge
        MobSkills[mskillBatteryCharge_269] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBatteryCharge_269] = SkillApplier(targetEvaluator = applyBasicBuff(
            statusEffect = StatusEffect.Refresh, duration = 1.minutes) { it.status.potency = 10 }
        )
        SkillHeuristics[mskillBatteryCharge_269] = avoidOverwritingBuff(StatusEffect.Refresh)

        // Snoll: Berserk
        MobSkills[mskillBerserk_270] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBerserk_270] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Berserk, duration = 3.minutes))
        SkillHeuristics[mskillBerserk_270] = avoidOverwritingBuff(StatusEffect.Berserk)

        // Snoll: Freeze Rush
        MobSkills[mskillFreezeRush_271] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFreezeRush_271] = SkillApplier(targetEvaluator = basicMagicalWs { 2f } )

        // Snoll: Coldwave
        MobSkills[mskillColdWave_272] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillColdWave_272] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = singleStatus(attackStatusEffect = spellDamageDoT(StatusEffect.Frost, duration = 15.seconds, potency = 0.2f, secondaryPotency = 0.8f))
        ))

        // Snoll: Hypothermal Combustion
        MobSkills[mskillHypothermalCombustion_273] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillHypothermalCombustion_273] = SkillApplier(
            additionalSelfEvaluator = defeatSelf(),
            targetEvaluator = basicMagicalWs(ftp = sourceCurrentHppPower(zeroHpPower = 1f, maxHpPower = 10f))
        )
        SkillHeuristics[mskillHypothermalCombustion_273] = onlyIfBelowHppThreshold(0.5)

        // Doll: Kartstrahl
        MobSkills[mskillKartstrahl_278] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillKartstrahl_278] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Sleep, baseDuration = 10.seconds)))
        ) { 2f })

        // Doll: Blitzstrahl
        MobSkills[mskillBlitzstrahl_279] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBlitzstrahl_279] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Stun, baseDuration = 3.seconds)))
        ) { 2f })

        // Doll: Panzerfaust
        MobSkills[mskillPanzerfaust_280] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPanzerfaust_280] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 2),
            numHits = 2,
            ftpSpread = true,
        ) { 1f })

        // Doll: Berserk
        MobSkills[mskillBerserk_281] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBerserk_281] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Warcry, duration = 30.seconds) { it.status.potency = 33 })
        SkillHeuristics[mskillBerserk_281] = avoidOverwritingBuff(StatusEffect.Warcry)

        // Doll: Panzerschreck
        MobSkills[mskillPanzerschreck_282] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPanzerschreck_282] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Doll: Typhoon
        MobSkills[mskillTyphoon_283] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillTyphoon_283] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 4)) { 2.75f })

        // Doll: Gravity Field
        MobSkills[mskillGravityField_285] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillGravityField_285] = SkillApplier(targetEvaluator = basicDebuff(StatusEffect.Slow, duration = 15.seconds) { it.statusState.potency = 40 })
        SkillHeuristics[mskillGravityField_285] = avoidOverwritingDebuff(StatusEffect.Slow)

        // Doll: Meltdown
        MobSkills[mskillMeltdown_287] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillMeltdown_287] = SkillApplier(
            additionalSelfEvaluator = defeatSelf(),
            targetEvaluator = basicMagicalWs(ftp = sourceCurrentHppPower(zeroHpPower = 1f, maxHpPower = 10f))
        )
        SkillHeuristics[mskillMeltdown_287] = onlyIfBelowHppThreshold(0.5)

        // Memory Receptacle: Empty Seed
        MobSkills[mskillEmptySeed_286] = MobSkill(castTime = 3.seconds, cost = zeroCost, rangeInfo = maxSourceRange)
        SkillAppliers[mskillEmptySeed_286] = SkillApplier(
            targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(knockBackMagnitude = 8)) { 1f }
        )

        // Ahriman: Blindeye
        MobSkills[mskillBlindeye_292] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBlindeye_292] = SkillApplier(targetEvaluator = basicPhysicalDamage (
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 25 })
        ) { 1.75f })

        // Ahriman: Eyes on Me
        MobSkills[mskillEyesOnMe_293] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillEyesOnMe_293] = SkillApplier(targetEvaluator = basicMagicalWs { 2.4f })

        // Ahriman: Hypnosis
        MobSkills[mskillHypnosis_294] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillHypnosis_294] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = AttackEffects(attackStatusEffects = listOf(AttackStatusEffect(statusEffect = StatusEffect.Sleep, baseDuration = 15.seconds)))
        ))
        SkillHeuristics[mskillHypnosis_294] = avoidOverwritingDebuff(StatusEffect.Bind)

        // Ahriman: Binding Wave
        MobSkills[mskillBindingWave_296] = MobSkill(rangeInfo = extendedSourceRange)
        SkillAppliers[mskillBindingWave_296] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = AttackEffects(attackStatusEffects = listOf(AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 15.seconds)))
        ))
        SkillHeuristics[mskillBindingWave_296] = avoidOverwritingDebuff(StatusEffect.Bind)

        // Ahriman: Airy Shield
        MobSkills[mskillAiryShield_297] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillAiryShield_297] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.EvasionBoost, 30.seconds) { it.status.potency = 50 })
        SkillHeuristics[mskillAiryShield_297] = avoidOverwritingBuff(StatusEffect.EvasionBoost)

        // Ahriman: Magic Barrier
        MobSkills[mskillMagicBarrier_299] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillMagicBarrier_299] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.MagicDefBoost, 30.seconds) { it.status.potency = 100 })
        SkillHeuristics[mskillMagicBarrier_299] = avoidOverwritingBuff(StatusEffect.MagicDefBoost)

        // Ahriman: Level 5 Petrify
        MobSkills[mskillLevel5Petrify_301] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillLevel5Petrify_301] = SkillApplier(targetEvaluator = basicDebuff(StatusEffect.Petrify, 15.seconds))
        SkillHeuristics[mskillLevel5Petrify_301] = avoidOverwritingDebuff(StatusEffect.Petrify)

        // Ahriman: Deathly Glare
        MobSkills[mskillDeathlyGlare_2512] = MobSkill(castTime = 3.seconds, rangeInfo = standardConeRange)
        SkillAppliers[mskillDeathlyGlare_2512] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(gazeAttack = true)
        ) { 4f })

        // Demon: Soul Drain
        MobSkills[mskillSoulDrain_303] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSoulDrain_303] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true)
        )) { 2f })

        // Demon: Hecatomb Wave
        MobSkills[mskillHecatombWave_304] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillHecatombWave_304] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 30 })
        ) { 2.5f })

        // Demon: Demonic Howl
        MobSkills[mskillDemonicHowl_307] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillDemonicHowl_307] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 18.seconds) {
                it.statusState.potency = 66
            }),
        ))

        // Demon: Condemnation
        MobSkills[mskillCondemnation_892] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillCondemnation_892] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 3.seconds)
        )) { 2.25f })

        // Demon: Quadrastrike
        MobSkills[mskillQuadrastrike_893] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillQuadrastrike_893] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 4, ftpSpread = true) { 0.65f })

        // Demon: Hellborn Yawp
        MobSkills[mskillHellbornYawp_2116] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillHellbornYawp_2116] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(dispelCount = 2, attackStatusEffects = listOf(
                AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 30 }
            ))
        ) { 2.25f })

        // Goblin: Goblin Rush
        MobSkills[mskillGoblinRush_334] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillGoblinRush_334] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 1),
            numHits = 3,
            ftpSpread = true,
        ) { 0.66f })

        // Goblin: Bomb Toss
        MobSkills[mskillBombToss_335] = MobSkill(castTime = 3.seconds, rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillBombToss_335] = SkillApplier(
            targetEvaluator = basicMagicalWs { 2.5f },
            switchSkillEvaluator = {
                val fail = it.sourceState.getHpp() < 0.5 && Random.nextBoolean()
                if (fail) { SwitchSkillResult(newTarget = it.targetState, newSkill = mskillBombToss_336) } else { null }
            }
        )

        // Goblin: Bomb Toss (fail)
        MobSkills[mskillBombToss_336] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillBombToss_336] = SkillApplier(
            additionalSelfEvaluator = defeatSelf(),
            targetEvaluator = basicMagicalWs(ftp = sourceCurrentHppPower(zeroHpPower = 1f, maxHpPower = 8f))
        )

        // Goblin: Frypan
        MobSkills[mskillFrypan_825] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillFrypan_825] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 3.seconds)
        )) { 2.25f })

        // Goblin: Smokebomb
        MobSkills[mskillSmokebomb_826] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSmokebomb_826] = SkillApplier(
            targetEvaluator = basicDebuff(statusEffect = StatusEffect.Blind, duration = 30.seconds) { it.statusState.potency = 33 },
            switchSkillEvaluator = {
                val fail = Random.nextDouble() < 0.25
                if (fail) { SwitchSkillResult(newTarget = it.sourceState, newSkill = mskillSmokebomb_827) } else { null }
            }
        )
        SkillHeuristics[mskillSmokebomb_826] = avoidOverwritingDebuff(StatusEffect.Blind)

        // Goblin: Smokebomb (fail)
        MobSkills[mskillSmokebomb_827] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSmokebomb_827] = SkillApplier(
            targetEvaluator = basicDebuff(statusEffect = StatusEffect.Blind, duration = 30.seconds) { it.statusState.potency = 33 },
        )

        // Goblin: Crispy Candle
        MobSkills[mskillCrispyCandle_828] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillCrispyCandle_828] = SkillApplier(
            targetEvaluator = basicMagicalWs { 2.2f },
            switchSkillEvaluator = {
                val fail = Random.nextDouble() < 0.25
                if (fail) { SwitchSkillResult(newTarget = it.sourceState, newSkill = mskillCrispyCandle_829) } else { null }
            }
        )

        // Goblin: Crispy Candle (fail)
        MobSkills[mskillCrispyCandle_829] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillCrispyCandle_829] = SkillApplier(targetEvaluator = sourceCurrentHpDamage(percent = 0.25f, damageType = AttackDamageType.Static))

        // Goblin: Paralysis Shower
        MobSkills[mskillParalysisShower_830] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillParalysisShower_830] = SkillApplier(
            targetEvaluator = basicDebuff(statusEffect = StatusEffect.Paralysis, duration = 30.seconds) { it.statusState.potency = 33 },
            switchSkillEvaluator = {
                val fail = Random.nextDouble() < 0.25
                if (fail) { SwitchSkillResult(newTarget = it.sourceState, newSkill = mskillParalysisShower_831) } else { null }
            }
        )
        SkillHeuristics[mskillParalysisShower_830] = avoidOverwritingDebuff(StatusEffect.Paralysis)

        // Goblin: Paralysis Shower (fail)
        MobSkills[mskillParalysisShower_831] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillParalysisShower_831] = SkillApplier(
            targetEvaluator = basicDebuff(statusEffect = StatusEffect.Paralysis, duration = 30.seconds) { it.statusState.potency = 33 },
        )

        // Goblin: Saucepan
        MobSkills[mskillSaucepan_2158] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillSaucepan_2158] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
        ) { 2.25f })

        // Snoll Tzar: Berserk
        MobSkills[mskillBerserk_342] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBerserk_342] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Berserk, duration = 3.minutes),
        )

        // Snoll Tzar: Arctic Rush
        MobSkills[mskillArcticImpact_343] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillArcticImpact_343] = SkillApplier(targetEvaluator = basicMagicalWs { 3f } )

        // Snoll Tzar: Coldwave
        MobSkills[mskillColdWave_344] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillColdWave_344] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = singleStatus(attackStatusEffect = spellDamageDoT(StatusEffect.Frost, duration = 15.seconds, potency = 0.2f, secondaryPotency = 0.8f))
        ))

        // Snoll Tzar: Hiemal Storm
        MobSkills[mskillHiemalStorm_345] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillHiemalStorm_345] = SkillApplier(targetEvaluator = basicMagicalWs { 3f } )

        // Snoll Tzar: Hypothermal Combustion
        MobSkills[mskillHypothermalCombustion_346] = MobSkill(castTime = 10.seconds, cost = zeroCost, rangeInfo = maxSourceRange, bypassParalysis = true)
        SkillAppliers[mskillHypothermalCombustion_346] = SkillApplier(
            targetEvaluator = staticDamage(99999)
        )

        // Yagudo: Feather Storm
        MobSkills[mskillFeatherStorm_361] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillFeatherStorm_361] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
        ) { 2f })

        // Yagudo: Double Kick
        MobSkills[mskillDoubleKick_362] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillDoubleKick_362] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 2, ftpSpread = true) { 1.1f })

        // Yagudo: Parry
        MobSkills[mskillParry_363] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillParry_363] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.DefenseBoost, duration = 30.seconds) {
            it.status.potency = 40
        })
        SkillHeuristics[mskillParry_363] = avoidOverwritingBuff(StatusEffect.DefenseBoost)

        // Yagudo: Sweep
        MobSkills[mskillSweep_364] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSweep_364] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
                attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 3.seconds)
        )) { 2.25f })

        // Yagudo: Howl
        MobSkills[mskillHowl_508] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillHowl_508] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Warcry, duration = 30.seconds) { it.status.potency = 25 })
        SkillHeuristics[mskillHowl_508] = avoidOverwritingBuff(StatusEffect.Warcry)

        // Yagudo: Feather Maelstrom
        MobSkills[mskillFeatherMaelstrom_2157] = MobSkill(rangeInfo = standardConeRange, castTime = 3.seconds)
        SkillAppliers[mskillFeatherMaelstrom_2157] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Bio, potency = 0.5f, secondaryPotency = 0.3f, duration = 15.seconds))
        ) { 3f })

        // Orc: Aerial Wheel
        MobSkills[mskillAerialWheel_349] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillAerialWheel_349] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2f })

        // Orc: Shoulder Attack
        MobSkills[mskillShoulderAttack_350] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillShoulderAttack_350] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
            knockBackMagnitude = 2,
            attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 3.seconds),
        )) { 1.75f })

        // Orc: Slam Dunk
        MobSkills[mskillSlamDunk_351] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSlamDunk_351] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 5.seconds))
        ) { 2f })

        // Orc: Arm Block
        MobSkills[mskillArmBlock_352] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillArmBlock_352] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.DefenseBoost, duration = 30.seconds) { it.status.potency = 40 }
        )
        SkillHeuristics[mskillArmBlock_352] = avoidOverwritingBuff(StatusEffect.DefenseBoost)

        // Orc: Battle Dance
        MobSkills[mskillBattleDance_353] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillBattleDance_353] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.DexDown, baseDuration = 15.seconds) {
                it.statusState.secondaryPotency = 0.5f
            })
        ) { 1.5f })

        // Orc: Howl
        MobSkills[mskillHowl_510] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillHowl_510] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Warcry, duration = 30.seconds) { it.status.potency = 25 })
        SkillHeuristics[mskillHowl_510] = avoidOverwritingBuff(StatusEffect.Warcry)

        // Orc: Orcish Counterstance
        MobSkills[mskillOrcishCounterstance_1945] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = 3.seconds)
        SkillAppliers[mskillOrcishCounterstance_1945] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.CounterBoost, duration = 30.seconds) { it.status.potency = 25 })
        SkillHeuristics[mskillOrcishCounterstance_1945] = avoidOverwritingBuff(StatusEffect.CounterBoost)

        // Orc: Phantasmal Dance
        MobSkills[mskillPhantasmalDance_2155] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillPhantasmalDance_2155] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
            knockBackMagnitude = 4,
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 8.seconds))
        ) { 2.5f })

        // Quadav: Ore Toss
        MobSkills[mskillOreToss_355] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillOreToss_355] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2f })

        // Quadav: Head Butt
        MobSkills[mskillHeadButt_356] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHeadButt_356] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
            knockBackMagnitude = 2,
            attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 3.seconds),
        )) { 1.5f })

        // Quadav: Shell Bash
        MobSkills[mskillShellBash_357] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = 3.seconds)
        SkillAppliers[mskillShellBash_357] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
            knockBackMagnitude = 4,
            attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 3.seconds),
        )) { 2.25f })

        // Quadav: Shell Guard
        MobSkills[mskillShellGuard_358] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillShellGuard_358] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.DefenseBoost, duration = 30.seconds) { it.status.potency = 40 }
        )
        SkillHeuristics[mskillShellGuard_358] = avoidOverwritingBuff(StatusEffect.DefenseBoost)

        // Quadav: Howl
        MobSkills[mskillHowl_506] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillHowl_506] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Warcry, duration = 30.seconds) { it.status.potency = 25 })
        SkillHeuristics[mskillHowl_506] = avoidOverwritingBuff(StatusEffect.Warcry)

        // Quadav: Diamond Shell
        MobSkills[mskillDiamondShell_1947] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillDiamondShell_1947] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.DefenseBoost, duration = 30.seconds) { it.status.potency = 40 }
        )
        SkillHeuristics[mskillDiamondShell_1947] = avoidOverwritingBuff(StatusEffect.DefenseBoost)

        // Quadav: Thunderous Yowl
        MobSkills[mskillThunderousYowl_2156] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillThunderousYowl_2156] = SkillApplier(
            targetEvaluator = basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 12.seconds) { it.statusState.potency = 3 },
                AttackStatusEffect(statusEffect = StatusEffect.Curse, baseDuration = 12.seconds) { it.statusState.potency = 33 },
            ))),
        )

        // Behemoth: Wild Horn
        MobSkills[mskillWildHorn_372] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillWildHorn_372] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 4)) { 2.5f })
        SkillHeuristics[mskillWildHorn_372] = onlyIfFacingTarget()

        // Behemoth: Thunderbolt
        MobSkills[mskillThunderbolt_373] = MobSkill(castTime = 3.5.seconds, rangeInfo = extendedSourceRange, lockTime = 3.seconds)
        SkillAppliers[mskillThunderbolt_373] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 5.seconds))
        ) { 3f })

        // Behemoth: Kickout
        MobSkills[mskillKickOut_374] = MobSkill(rangeInfo = reverseConeRange)
        SkillAppliers[mskillKickOut_374] = SkillApplier(targetEvaluator = basicPhysicalDamage { 3f })
        SkillHeuristics[mskillKickOut_374] = targetIsBehindSource()

        // Behemoth: Shockwave
        MobSkills[mskillShockWave_375] = MobSkill(castTime = 1.5.seconds, rangeInfo = standardConeRange)
        SkillAppliers[mskillShockWave_375] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 4)) { 2f })
        SkillHeuristics[mskillShockWave_375] = onlyIfFacingTarget()

        // Behemoth: Flame Armor
        MobSkills[mskillFlameArmor_376] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFlameArmor_376] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.BlazeSpikes, duration = 30.seconds) {
            it.status.potency = intPotency(it.context.sourceState, 0.10f)
        })
        SkillHeuristics[mskillFlameArmor_376] = avoidOverwritingBuff(StatusEffect.BlazeSpikes)

        // Behemoth: Howl
        MobSkills[mskillHowl_377] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHowl_377] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Warcry, duration = 30.seconds) { it.status.potency = 25 })
        SkillHeuristics[mskillHowl_377] = avoidOverwritingBuff(StatusEffect.Warcry)

        // Spider: Sickle Slash
        MobSkills[mskillSickleSlash_554] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = 3.seconds)
        SkillAppliers[mskillSickleSlash_554] = SkillApplier(targetEvaluator = basicPhysicalDamage { 3f })

        // Spider: Acid Spray
        MobSkills[mskillAcidSpray_555] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillAcidSpray_555] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
        ) { 1.75f })

        // Spider: Spider Web
        MobSkills[mskillSpiderWeb_556] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSpiderWeb_556] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 18.seconds) {
                it.statusState.potency = 66
            }),
        ))

        // Behemoth: Amnesiac Blast
        MobSkills[mskillAmnesicBlast_2135] = MobSkill(rangeInfo = standardConeRange, lockTime = 3.seconds)
        SkillAppliers[mskillAmnesicBlast_2135] = SkillApplier(targetEvaluator = basicMagicalWs (
            attackEffects = singleStatus(knockBackMagnitude = 4, attackStatusEffect = AttackStatusEffect(StatusEffect.Amnesia, baseDuration = 15.seconds))
        ) { 2f })
        SkillHeuristics[mskillAmnesicBlast_2135] = onlyIfFacingTarget()

        // Behemoth: Accursed Armor
        MobSkills[mskillAccursedArmor_2134] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillAccursedArmor_2134] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.DreadSpikes, duration = 30.seconds) {
            it.status.potency = intPotency(it.context.sourceState, 0.1f)
        })
        SkillHeuristics[mskillAccursedArmor_2134] = avoidOverwritingBuff(StatusEffect.DreadSpikes)

        // Behemoth: Ecliptic Meteor
        MobSkills[mskillEclipticMeteor_2330] = MobSkill(castTime = 5.seconds, lockTime = 4.seconds, rangeInfo = maxSourceRange)
        SkillAppliers[mskillEclipticMeteor_2330] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            spellDamageDoT(StatusEffect.Bio, duration = 9.seconds, potency = 0.33f, secondaryPotency = 0.8f),
            AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 9.seconds) { it.statusState.potency = 25 },
            AttackStatusEffect(statusEffect = StatusEffect.Amnesia, baseDuration = 9.seconds),
        ))) { 2.5f })

        // Warmachine: Burst
        MobSkills[mskillBurst_379] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBurst_379] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2f })

        // Warmachine: Flame Arrow
        MobSkills[mskillFlameArrow_380] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFlameArrow_380] = SkillApplier(targetEvaluator = basicMagicalWs { 2f })

        // Warmachine: Fire Bomb
        MobSkills[mskillFirebomb_381] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillFirebomb_381] = SkillApplier(targetEvaluator = basicMagicalWs { 2f })

        // Warmachine: Blast Bomb
        MobSkills[mskillBlastbomb_382] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillBlastbomb_382] = SkillApplier(targetEvaluator = basicMagicalWs { 2f })

        // Warmachine: Fountain
        MobSkills[mskillFountain_383] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillFountain_383] = SkillApplier(targetEvaluator = basicMagicalWs { 2f })

        // Dragon Skills
        MobSkills[mskillFlameBreath_386] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillFlameBreath_386] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })
        SkillHeuristics[mskillFlameBreath_386] = onlyIfFacingTarget()

        MobSkills[mskillPoisonBreath_387] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillPoisonBreath_387] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = spellDamageDoT(
                statusEffect = StatusEffect.Poison, duration = 15.seconds, potency = 0.33f,
            ))
        ) { 2f })
        SkillHeuristics[mskillPoisonBreath_387] = onlyIfFacingTarget()

        MobSkills[mskillWindBreath_388] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillWindBreath_388] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })
        SkillHeuristics[mskillWindBreath_388] = onlyIfFacingTarget()

        MobSkills[mskillBodySlam_389] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillBodySlam_389] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        MobSkills[mskillHeavyStomp_390] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillHeavyStomp_390] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 40 })
        ) { 2f })

        MobSkills[mskillChaosBlade_391] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillChaosBlade_391] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Curse, baseDuration = 15.seconds) { it.statusState.potency = 33 },
            ))
        ) { 2f })
        SkillHeuristics[mskillChaosBlade_391] = onlyIfFacingTarget()

        MobSkills[mskillChaosBlade_927] = MobSkill(rangeInfo = standardConeRange, castTime = ZERO, cost = zeroCost)
        SkillAppliers[mskillChaosBlade_927] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Curse, baseDuration = 15.seconds) { it.statusState.potency = 33 },
            ))
        ) { 2f })

        MobSkills[mskillPetroEyes_392] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillPetroEyes_392] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true,
            bypassShadows = true,
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Petrify, baseDuration = 15.seconds),
            ))))
        SkillHeuristics[mskillPetroEyes_392] = minimumOf(avoidOverwritingDebuff(StatusEffect.Petrify), onlyIfFacingTarget())

        MobSkills[mskillVoidsong_393] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillVoidsong_393] = SkillApplier(
            targetEvaluator = basicDebuff(dispelCount = 1),
            additionalSelfEvaluator = expireTargetDebuffs(maxToExpire = 32),
        )

        MobSkills[mskillThornsong_394] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillThornsong_394] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.BlazeSpikes, duration = 30.seconds) {
            it.status.potency = intPotency(it.context.sourceState, 0.10f)
        })
        SkillHeuristics[mskillThornsong_394] = avoidOverwritingBuff(StatusEffect.BlazeSpikes)

        MobSkills[mskillLodesong_395] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillLodesong_395] = SkillApplier(
            targetEvaluator = basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 12.seconds) { it.statusState.potency = 50 },
            ))),
        )
        SkillHeuristics[mskillLodesong_395] = avoidOverwritingDebuff(StatusEffect.Weight)

        MobSkills[mskillNullsong_1536] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillNullsong_1536] = SkillApplier(targetEvaluator = basicMagicalWs(
          attackEffects = AttackEffects(dispelCount = 32)
        ) { 1f + 0.5f * it.defender.getStatusEffects().count { se -> se.canDispel } })

        MobSkills[mskillDiscordantNote_3507] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillDiscordantNote_3507] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 6.seconds),
                AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 15.seconds) { it.statusState.potency = 75 },
                AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 75 },
            )))
        { 2f })

        // Uragnite: Gas Shell
        MobSkills[mskillGasShell_1315] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillGasShell_1315] = SkillApplier(
            targetEvaluator = basicDebuff(
                attackEffects = singleStatus(attackStatusEffect = spellDamageDoT(StatusEffect.Poison, potency = 2f, duration = 30.seconds))
            ),
        )

        // Uragnite: Venom Shell
        MobSkills[mskillVenomShell_1316] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillVenomShell_1316] = SkillApplier(
            targetEvaluator = basicDebuff(
                attackEffects = singleStatus(attackStatusEffect = spellDamageDoT(StatusEffect.Poison, potency = 0.75f, duration = 15.seconds))
            ),
        )

        // Uragnite: Painful Whip
        MobSkills[mskillPainfulWhip_1318] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPainfulWhip_1318] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 0.7f },
        )

        // Uragnite: Suct.
        MobSkills[mskillSuctorialTentacle_1319] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSuctorialTentacle_1319] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 12.seconds),
                spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 12.seconds),
            ))) { 2f },
        )

        // Eft: Toxic Spit
        MobSkills[mskillToxicSpit_259] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillToxicSpit_259] = SkillApplier(
            targetEvaluator = basicMagicalWs(
                attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
            ) { 1f },
        )

        // Eft: Geist Wall
        MobSkills[mskillGeistWall_260] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillGeistWall_260] = SkillApplier(targetEvaluator = basicDebuff(dispelCount = 2))

        // Eft: Numbing Noise
        MobSkills[mskillNumbingNoise_261] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillNumbingNoise_261] = SkillApplier(
            targetEvaluator = basicDebuff(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 5.seconds)),
        )

        // Eft: Nimble Snap
        MobSkills[mskillNimbleSnap_262] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillNimbleSnap_262] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 0.85f },
        )

        // Eft: Cyclotail
        MobSkills[mskillCyclotail_263] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillCyclotail_263] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.25f },
        )

        // Cluster: Sling Bomb
        MobSkills[mskillSlingBomb_311] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSlingBomb_311] = SkillApplier(targetEvaluator = MobClusterBehavior.slingBomb())

        // Cluster: Formation Attack
        MobSkills[mskillFormationAttack_312] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFormationAttack_312] = SkillApplier(targetEvaluator = MobClusterBehavior.formationAttack())

        // Cluster: Refueling
        MobSkills[mskillRefueling_313] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRefueling_313] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Haste) {
            it.status.remainingDuration = Fps.secondsToFrames(300)
            it.status.potency = 30
        })

        // Cluster: Circle of Flames
        MobSkills[mskillCircleofFlames_314] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillCircleofFlames_314] = SkillApplier(targetEvaluator = MobClusterBehavior.circleOfFlames())

        // Cluster: Self-Destruct (3 bombs -> 2 bombs)
        MobSkills[mskillSelfDestruct_315] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillSelfDestruct_315] = MobClusterBehavior.selfDestructSingle(destState = 1, hppUsed = 1/3f)

        // Cluster: Self-Destruct (3 bombs -> 0 bombs)
        MobSkills[mskillSelfDestruct_316] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillSelfDestruct_316] = MobClusterBehavior.selfDestructFull()

        // Cluster: Self-Destruct (2 bombs -> 1 bombs)
        MobSkills[mskillSelfDestruct_317] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillSelfDestruct_317] = MobClusterBehavior.selfDestructSingle(destState = 2, hppUsed = 1/2f)

        // Cluster: Self-Destruct (2 bombs -> 0 bombs)
        MobSkills[mskillSelfDestruct_318] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillSelfDestruct_318] = MobClusterBehavior.selfDestructFull()

        // Cluster: Self-Destruct (1 bombs -> 0 bombs)
        MobSkills[mskillSelfDestruct_319] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillSelfDestruct_319] = MobClusterBehavior.selfDestructFull()

        // Vulture: Hell Dive
        MobSkills[mskillHelldive_366] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHelldive_366] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 1)) { 2.25f },
        )

        // Vulture: Wing Cutter
        MobSkills[mskillWingCutter_367] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillWingCutter_367] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.5f },
        )

        // Pugil: Recoil Dive
        MobSkills[mskillRecoilDive_385] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillRecoilDive_385] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(2, ftpSpread = true) { 1.2f }
        )

        // Slime: Mucus Spread
        MobSkills[mskillMucusSpread_1061] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillMucusSpread_1061] = SkillApplier(
            targetEvaluator = basicMagicalWs(
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Slow, baseDuration = 15.seconds) { it.statusState.potency = 30 })
            ) { 2.5f },
        )

        // Slime: Epoxy Spread
        MobSkills[mskillEpoxySpread_1063] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillEpoxySpread_1063] = SkillApplier(
            targetEvaluator = basicMagicalWs(
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Bind, baseDuration = 15.seconds))
            ) { 2f },
        )

        // Limule: Blazing Bound
        MobSkills[mskillBlazingBound_2308] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBlazingBound_2308] = SkillApplier(targetEvaluator = noop())

        // Limule: Molting Burst
        MobSkills[mskillMoltingBurst_2309] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillMoltingBurst_2309] = SkillApplier(targetEvaluator = noop())

        // Fly: Cursed Sphere
        MobSkills[mskillCursedSphere_403] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillCursedSphere_403] = SkillApplier(
            targetEvaluator = basicMagicalWs { 2f }
        )

        // Fly: Venom
        MobSkills[mskillVenom_404] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillVenom_404] = SkillApplier(
            targetEvaluator = basicDebuff(attackStatusEffect = spellDamageDoT(StatusEffect.Poison, potency = 1f, duration = 15.seconds))
        )

        // Gigas: Catapult
        MobSkills[mskillCatapult_402] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = ZERO)
        SkillAppliers[mskillCatapult_402] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2f })
        SkillHeuristics[mskillCatapult_402] = preferFarAway(distanceScore = 2.0, nearDistance = 4f, farDistance = 8f)

        // Gigas: Lightning Roar
        MobSkills[mskillLightningRoar_406] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillLightningRoar_406] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })

        // Gigas: Ice Roar
        MobSkills[mskillIceRoar_407] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillIceRoar_407] = SkillApplier(targetEvaluator = basicMagicalWs (
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 25 })
        ) { 2f })

        // Gigas: Impact Roar
        MobSkills[mskillImpactRoar_408] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillImpactRoar_408] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Gigas: Grand Slam
        MobSkills[mskillGrandSlam_409] = MobSkill(castTime = 3.5.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillGrandSlam_409] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Gigas: Power Attack (H2H)
        MobSkills[mskillPowerAttack_410] = MobSkill(castTime = 3.5.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPowerAttack_410] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Gigas: Power Attack (Weapon)
        MobSkills[mskillPowerAttack_411] = MobSkill(castTime = 3.5.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPowerAttack_411] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Gigas: Moribund Hack
        MobSkills[mskillMoribundHack_2111] = MobSkill(castTime = 2.5.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillMoribundHack_2111] = SkillApplier(targetEvaluator = basicMagicalWs { 4f })

        // Golem: Crystal Shield
        MobSkills[mskillCrystalShield_418] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillCrystalShield_418] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Protect, duration = 3.minutes) { it.status.potency = 40 })
        SkillHeuristics[mskillCrystalShield_418] = avoidOverwritingBuff(StatusEffect.Protect)

        // Golem: Heavy Strike
        MobSkills[mskillHeavyStrike_419] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHeavyStrike_419] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 4)
        ) { 2.5f })

        // Golem: Ice Break
        MobSkills[mskillIceBreak_420] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillIceBreak_420] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 10.seconds))
        ) { 2.25f })

        // Golem: Thunder Break
        MobSkills[mskillThunderBreak_421] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillThunderBreak_421] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 5.seconds))
        ) { 2.25f })

        // Golem: Crystal Rain
        MobSkills[mskillCrystalRain_422] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillCrystalRain_422] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })

        // Golem: Crystal Weapon
        listOf(mskillCrystalWeapon_423, mskillCrystalWeapon_424, mskillCrystalWeapon_425, mskillCrystalWeapon_426).forEach {
            MobSkills[it] = MobSkill(rangeInfo = standardSingleTargetRange)
            SkillAppliers[it] = SkillApplier(targetEvaluator = basicMagicalWs { 2f })
            SkillHeuristics[it] = SkillHeuristic { 0.5 / 4 }
        }

        // Golem: Volcanic Wrath
        MobSkills[mskillVolcanicWrath_2679] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillVolcanicWrath_2679] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.HPDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.75f },
                spellDamageDoT(StatusEffect.Burn, duration = 15.seconds, potency = 0.2f, secondaryPotency = 0.8f),
            )),
        ) { 2f })

        // Hound Skills
        MobSkills[mskillHowling_209] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillHowling_209] = SkillApplier(targetEvaluator = basicDebuff(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) {
            it.statusState.potency = 50
        }))
        SkillHeuristics[mskillHowling_209] = avoidOverwritingDebuff(StatusEffect.Paralysis)

        MobSkills[mskillPoisonBreath_210] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillPoisonBreath_210] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
        ) { 1.75f })

        MobSkills[mskillRotGas_211] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillRotGas_211] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = singleStatus(
            AttackStatusEffect(statusEffect = StatusEffect.Disease, baseDuration = 15.seconds),
        )) { 2f })

        MobSkills[mskillDirtyClaw_212] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillDirtyClaw_212] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.75f })

        MobSkills[mskillShadowClaw_213] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillShadowClaw_213] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 30 })
        ) { 2f })

        MobSkills[mskillMethaneBreath_214] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillMethaneBreath_214] = SkillApplier(targetEvaluator = basicMagicalWs { 2.75f })

        // Skeleton Skills
        MobSkills[mskillHellSlash_222] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHellSlash_222] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        MobSkills[mskillHorrorCloud_223] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHorrorCloud_223] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 15.seconds) { it.statusState.potency = 50 }),
        )

        MobSkills[mskillBlackCloud_228] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillBlackCloud_228] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 30 })
        ) { 2.5f })

        MobSkills[mskillBloodSaber_229] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillBloodSaber_229] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true),
        )) { 2f })

        // Sapling Skills
        MobSkills[mskillSproutSpin_429] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSproutSpin_429] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 4),
        ) { 2f })

        MobSkills[mskillSlumberPowder_430] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSlumberPowder_430] = SkillApplier(
            targetEvaluator = basicDebuff(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Sleep, baseDuration = 18.seconds))
        )

        MobSkills[mskillSproutSmack_431] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSproutSmack_431] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 15.seconds) { it.statusState.potency = 40 },
            ))
        ) { 2f })

        // SP Abilities
        MobSkills[mskillHundredFists_434] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = ZERO, cost = zeroCost, lockTime = 3.seconds)
        SkillAppliers[mskillHundredFists_434] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.HundredFists, duration = 20.seconds))
        SkillHeuristics[mskillHundredFists_434] = avoidOverwritingBuff(StatusEffect.HundredFists)

        MobSkills[mskillManafont_435] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = ZERO, cost = zeroCost, lockTime = 3.seconds)
        SkillAppliers[mskillManafont_435] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Manafont, duration = 15.seconds))
        SkillHeuristics[mskillManafont_435] = avoidOverwritingBuff(StatusEffect.Manafont)

        MobSkills[mskillChainspell_436] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = ZERO, cost = zeroCost, lockTime = 3.seconds)
        SkillAppliers[mskillChainspell_436] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Chainspell, duration = 15.seconds))
        SkillHeuristics[mskillChainspell_436] = avoidOverwritingBuff(StatusEffect.Chainspell)

        MobSkills[mskillMeikyoShisui_474] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = ZERO, cost = zeroCost, lockTime = 3.seconds)
        SkillAppliers[mskillMeikyoShisui_474] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.MeikyoShisui, duration = 15.seconds))
        SkillHeuristics[mskillMeikyoShisui_474] = avoidOverwritingBuff(StatusEffect.MeikyoShisui)

        // Gorger: Quadratic Continuum
        MobSkills[mskillQuadraticContinuum_485] = MobSkill(castTime = 4.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillQuadraticContinuum_485] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 4, ftpSpread = true) { 0.8f }
        )

        // Gorger: Spirit Absorption
        MobSkills[mskillSpiritAbsorption_488] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSpiritAbsorption_488] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
                absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true),
        )) { 2f })

        // Gorger: Vanity Dive
        MobSkills[mskillVanityDrive_491] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillVanityDrive_491] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Gorger: Stygian Flatus
        MobSkills[mskillStygianFlatus_494] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillStygianFlatus_494] = SkillApplier(
            targetEvaluator = basicDebuff(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 40 }),
        )

        // Gorger: Promy. Barrier
        MobSkills[mskillPromyvionBarrier_496] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPromyvionBarrier_496] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.DefenseBoost, duration = 30.seconds) { it.status.potency = 40 }
        )

        // Gorger: Fission
        MobSkills[mskillFission_499] = MobSkill(rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskillFission_499] = SkillApplier(targetEvaluator = noop())

        // Wyvern skills
        MobSkills[mskillDispellingWind_557] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillDispellingWind_557] = SkillApplier(targetEvaluator = basicDebuff(dispelCount = 2))

        MobSkills[mskillDeadlyDrive_558] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillDeadlyDrive_558] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        MobSkills[mskillWindWall_559] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillWindWall_559] = SkillApplier(targetEvaluator = applyBasicBuff(
            statusEffect = StatusEffect.EvasionBoost, duration = 30.seconds) { it.status.potency = 40 }
        )
        SkillHeuristics[mskillWindWall_559] = avoidOverwritingBuff(StatusEffect.EvasionBoost)

        MobSkills[mskillFangRush_560] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFangRush_560] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 2, ftpSpread = true) { 1.33f })

        MobSkills[mskillDreadShriek_561] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillDreadShriek_561] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 50 }),
        )
        SkillHeuristics[mskillDreadShriek_561] = avoidOverwritingDebuff(StatusEffect.Paralysis)

        MobSkills[mskillTailCrush_562] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillTailCrush_562] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
        ) { 1.75f })

        MobSkills[mskillBlizzardBreath_563] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillBlizzardBreath_563] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })

        MobSkills[mskillThunderBreath_564] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillThunderBreath_564] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })

        MobSkills[mskillRadiantBreath_565] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillRadiantBreath_565] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 6.seconds),
                AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 15.seconds) { it.statusState.potency = 40 },
            ))
        ) { 2f })

        MobSkills[mskillChaosBreath_566] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillChaosBreath_566] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })

        MobSkills[mskillHurricaneBreath_1966] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillHurricaneBreath_1966] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(knockBackMagnitude = 4)
        ) { 2.5f })

        MobSkills[mskillBlazingShriek_2678] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillBlazingShriek_2678] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(knockBackMagnitude = 4, attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            ))
        ) { 2.5f })

        // Eald: Warp in
        MobSkills[mskillEald2WarpIn_732] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = ZERO, cost = zeroCost, lockTime = ZERO)
        SkillAppliers[mskillEald2WarpIn_732] = SkillApplier(targetEvaluator = noop())

        // Earl: Warp out
        MobSkills[mskillEald2WarpOut_733] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = ZERO, cost = zeroCost, lockTime = ZERO)
        SkillAppliers[mskillEald2WarpOut_733] = SkillApplier(targetEvaluator = noop())

        // Weeper: Empty Cutter
        MobSkills[mskillEmptyCutter_961] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillEmptyCutter_961] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.75f }
        )

        // Weeper: Vacuous Osculation
        MobSkills[mskillVacuousOsculation_962] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillVacuousOsculation_962] = SkillApplier(
            targetEvaluator = basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 12.seconds) { it.statusState.potency = 3 },
                spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 12.seconds),
            ))),
        )

        // Weeper: Hexagon Belt
        MobSkills[mskillHexagonBelt_963] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHexagonBelt_963] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.DefenseBoost, duration = 30.seconds) { it.status.potency = 40 }
        )

        // Weeper: Auroral Drape
        MobSkills[mskillAuroralDrape_964] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillAuroralDrape_964] = SkillApplier(
            targetEvaluator = basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 12.seconds),
                AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 12.seconds) { it.statusState.potency = 50 },
            ))),
        )

        // Weeper: Memories of Elements
        listOf(
            mskillMemoryofFire_965,
            mskillMemoryofIce_966,
            mskillMemoryofWind_967,
            mskillMemoryofLight_968,
            mskillMemoryofEarth_969,
            mskillMemoryofLightning_970,
            mskillMemoryofWater_971,
            mskillMemoryofDark_972,
        ).forEach {
            MobSkills[it] = MobSkill(rangeInfo = standardSourceRange)
            SkillAppliers[it] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })
        }

        // Craver: Brain Spike
        MobSkills[mskillBrainSpike_973] = MobSkill(castTime = 4.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBrainSpike_973] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
                AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 50 }
            )) { 2.75f }
        )

        // Craver: Empty Thrash
        MobSkills[mskillEmptyThrash_974] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillEmptyThrash_974] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.5f },
        )

        // Craver: Promy. Brume
        MobSkills[mskillPromyvionBrume_975] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillPromyvionBrume_975] = SkillApplier(
            targetEvaluator = basicMagicalWs(
                attackEffects = singleStatus(spellDamageDoT(statusEffect = StatusEffect.Poison, duration = 15.seconds, potency = 0.4f))
            ) { 1.75f }
        )

        // Craver: Murk
        MobSkills[mskillMurk_976] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillMurk_976] = SkillApplier(
            targetEvaluator = basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 12.seconds) { it.statusState.potency = 50 },
                AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 12.seconds) { it.statusState.potency = 75 },
            ))),
        )

        // Craver: Material Fend
        MobSkills[mskillMaterialFend_977] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillMaterialFend_977] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.EvasionBoost, duration = 30.seconds) { it.status.potency = 40 }
        )

        // Craver: Carousel
        MobSkills[mskillCarousel_978] = MobSkill(castTime = 4.seconds, rangeInfo = SkillRangeInfo(12f, 12f, AoeType.Source))
        SkillAppliers[mskillCarousel_978] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 4)) { 2.25f },
        )

        // Thinker: Empty Cutter
        MobSkills[mskillEmptyCutter_986] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillEmptyCutter_986] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.5f }
        )

        // Thinker: Negative Whirl
        MobSkills[mskillNegativeWhirl_987] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillNegativeWhirl_987] = SkillApplier(
            targetEvaluator = basicMagicalWs(attackStat = CombatStat.mnd, defendStat = CombatStat.mnd,
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Slow, baseDuration = 15.seconds) { it.statusState.potency = 75 })
            ) { 2.5f },
        )

        // Thinker: Stygian Vapor
        MobSkills[mskillStygianVapor_988] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillStygianVapor_988] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 18.seconds) {
                it.statusState.potency = 5
            }),
        ))

        // Thinker: Winds of Promy.
        MobSkills[mskillWindsofPromyvion_989] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillWindsofPromyvion_989] = SkillApplier(targetEvaluator = expireTargetDebuffs(maxToExpire = 1))

        // Thinker: Spirit Absorption
        MobSkills[mskillSpiritAbsorption_990] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSpiritAbsorption_990] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true),
        )) { 2f })

        // Thinker: Binary Absorption
        MobSkills[mskillBinaryAbsorption_991] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBinaryAbsorption_991] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true),
        ), numHits = 2, ftpSpread = true) { 1f })

        // Thinker: Trinary Absorption
        MobSkills[mskillTrinaryAbsorption_992] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillTrinaryAbsorption_992] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true),
        ), numHits = 3, ftpSpread = true) { 0.67f })

        // Thinker: Spirit Tap
        MobSkills[mskillSpiritTap_993] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSpiritTap_993] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true, absorbBuffs = 1, absorbBuffsNegatesAttack = true))
        ) { 2f })

        // Thinker: Binary Tap
        MobSkills[mskillBinaryTap_994] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBinaryTap_994] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true, absorbBuffs = 2, absorbBuffsNegatesAttack = true)
        ), numHits = 2, ftpSpread = true) { 1f })

        // Thinker: Trinary Tap
        MobSkills[mskillTrinaryTap_995] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillTrinaryTap_995] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true, absorbBuffs = 3, absorbBuffsNegatesAttack = true)
        ), numHits = 3, ftpSpread = true) { 0.67f })

        // Thinker: Shadow Spread
        MobSkills[mskillShadowSpread_996] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillShadowSpread_996] = SkillApplier(targetEvaluator =
            basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 100 },
                AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 15.seconds),
            ))),
        )

        // Seether: Vanity Strike
        MobSkills[mskillVanityStrike_997] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillVanityStrike_997] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 5.seconds))) { 2f },
        )

        // Seether: Wanion
        MobSkills[mskillWanion_998] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillWanion_998] = SkillApplier(
            targetEvaluator = copyDebuffsToTarget(),
            postEvaluation = expireSourceDebuffs(maxToExpire = 999),
        )

        // Seether: Occultation
        MobSkills[mskillOccultation_999] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillOccultation_999] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Blink, duration = 5.minutes) { it.status.counter = 10 }
        )

        // Seether: Lamentation
        MobSkills[mskillLamentation_1002] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillLamentation_1002] = SkillApplier(
            targetEvaluator = basicMagicalWs(attackStat = CombatStat.mnd, defendStat = CombatStat.mnd,
                attackEffects = singleStatus(spellDamageDoT(statusEffect = StatusEffect.Dia, duration = 15.seconds, potency = 0.25f, secondaryPotency = 0.8f, attackStat = CombatStat.mnd)
            )) { 2.2f }
        )

        // Red Wyrm
        MobSkills[mskillInfernoBlast_1022] = MobSkill(rangeInfo = standardSingleTargetRange, cost = zeroCost, castTime = ZERO)
        SkillAppliers[mskillInfernoBlast_1022] = SkillApplier(targetEvaluator = basicMagicalWs { 1f })
        SkillHeuristics[mskillInfernoBlast_1022] = requireAppearanceState(1)

        MobSkills[mskillTebbadWing_1023] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillTebbadWing_1023] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 15.seconds) { it.statusState.potency = 10 },
            )))
        { 2.5f })
        SkillHeuristics[mskillTebbadWing_1023] = requireAppearanceState(0)

        MobSkills[mskillSpikeFlail_1024] = MobSkill(rangeInfo = reverseConeRange, castTime = 1.seconds)
        SkillAppliers[mskillSpikeFlail_1024] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 6),
        ) { 3f })
        SkillHeuristics[mskillSpikeFlail_1024] = minimumOf(requireAppearanceState(0), targetIsBehindSource())

        MobSkills[mskillFieryBreath_1025] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillFieryBreath_1025] = SkillApplier(targetEvaluator = basicMagicalWs { 2.75f })
        SkillHeuristics[mskillFieryBreath_1025] = minimumOf(requireAppearanceState(0), onlyIfFacingTarget())

        MobSkills[mskillTouchdown_1026] = MobSkill(rangeInfo = extendedSourceRange, castTime = ZERO, cost = zeroCost)
        SkillAppliers[mskillTouchdown_1026] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 6),
        ) { 1f })

        MobSkills[mskillInfernoBlast_1027] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillInfernoBlast_1027] = SkillApplier(targetEvaluator = basicMagicalWs { 2f })
        SkillHeuristics[mskillInfernoBlast_1027] = requireAppearanceState(1)

        MobSkills[mskillTebbadWing_1028] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillTebbadWing_1028] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 15.seconds) { it.statusState.potency = 10 },
            )))
        { 2.5f })
        SkillHeuristics[mskillTebbadWing_1028] = requireAppearanceState(1)

        MobSkills[mskillAbsoluteTerror_1029] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillAbsoluteTerror_1029] = SkillApplier(targetEvaluator = basicDebuff(statusEffect = StatusEffect.Terror, duration = 9.seconds))
        SkillHeuristics[mskillAbsoluteTerror_1029] = minimumOf(requireAppearanceState(0), avoidOverwritingDebuff(StatusEffect.Terror))

        MobSkills[mskillHorridRoar_1030] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillHorridRoar_1030] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = AttackEffects(dispelCount = 32)
        ))
        SkillHeuristics[mskillHorridRoar_1030] = minimumOf(requireAppearanceState(0), canDispelBuffs { 0.0 }, onlyIfFacingTarget())

        // Benediction
        MobSkills[mskillBenediction_1230] = MobSkill(rangeInfo = extendedSourceRange, cost = zeroCost, castTime = ZERO)
        SkillAppliers[mskillBenediction_1230] = SkillApplier(targetEvaluator = sourcePercentageHealingMagic(100f))

        // Gigas: Trebuchet
        MobSkills[mskillTrebuchet_1380] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillTrebuchet_1380] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(dispelCount = 1)) { 2f })

        // Yovra: Vitriolic Barrage
        MobSkills[mskillVitriolicBarrage_1114] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillVitriolicBarrage_1114] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, duration = 15.seconds, potency = 0.3f))
        ) { 2f })

        // Yovra: Primal Drill
        MobSkills[mskillPrimalDrill_1115] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillPrimalDrill_1115] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 5.seconds))
        ) { 2.5f })

        // Yovra: Concussive Oscillation
        MobSkills[mskillConcussiveOscillation_1116] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillConcussiveOscillation_1116] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 6,
            attackStatusEffects = listOf(AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 75 })),
        ) { 2.5f })

        // Yovra: Ion Shower
        MobSkills[mskillIonShower_1117] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillIonShower_1117] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 5.seconds))
        ) { 2.5f })

        // Yovra: Torrential Torrent
        MobSkills[mskillTorrentialTorment_1118] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillTorrentialTorment_1118] = SkillApplier(targetEvaluator = basicMagicalWs { 3f })

        // Yovra: Asthenic Fog
        MobSkills[mskillAsthenicFog_1119] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillAsthenicFog_1119] = SkillApplier(targetEvaluator = basicDebuff(
            spellDamageDoT(StatusEffect.Drown, duration = 15.seconds, potency = 0.5f, secondaryPotency = 0.8f)
        ))

        // Yovra: Luminous Drape
        MobSkillInfoTable.mutate(mskillLuminousDrape_1120) { it.copy(type = 6) }
        MobSkills[mskillLuminousDrape_1120] = MobSkill(castTime = ZERO, rangeInfo = SkillRangeInfo(25f, 25f, AoeType.Source))
        SkillAppliers[mskillLuminousDrape_1120] = SkillApplier(targetEvaluator = YovraSkills.luminousDrape())

        // Yovra: Fluorescence
        MobSkills[mskillFluorescence_1121] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFluorescence_1121] = SkillApplier(targetEvaluator = applyBasicBuff(
            statusEffect = StatusEffect.Boost, duration = 30.seconds) { it.status.counter = 1; it.status.potency = 300 }
        )

        // Orobon Skills
        MobSkills[mskillGnash_1437] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillGnash_1437] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2f })

        MobSkills[mskillVileBelch_1438] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillVileBelch_1438] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 9.seconds),
                AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 15.seconds) { it.statusState.potency = 10 },
            ))
        ))
        SkillHeuristics[mskillVileBelch_1438] = minimumOf(avoidOverwritingDebuff(StatusEffect.Plague), onlyIfFacingTarget())

        MobSkills[mskillHypnicLamp_1439] = MobSkill(rangeInfo = extendedSourceRange)
        SkillAppliers[mskillHypnicLamp_1439] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = AttackEffects(gazeAttack = true, attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Sleep, baseDuration = 9.seconds) { it.statusState.counter = 1 },
            ))
        ))
        SkillHeuristics[mskillHypnicLamp_1439] = minimumOf(requireAppearanceState(0), avoidOverwritingDebuff(StatusEffect.Sleep))

        MobSkills[mskillSeismicTail_1440] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSeismicTail_1440] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 4),
        ) { 2.5f })

        MobSkills[mskillSeaspray_1441] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSeaspray_1441] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 12.seconds) { it.statusState.potency = 50 },
            )
        )) { 2.25f })
        SkillHeuristics[mskillSeaspray_1441] = onlyIfFacingTarget()

        MobSkills[mskillLeechingCurrent_1442] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillLeechingCurrent_1442] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, absorptionResource = ActorResourceType.HP, proportionalAbsorption = true))
        ) { 2f })

        MobSkills[mskillDeathgnash_1721] = MobSkill(rangeInfo = standardSingleTargetRange, lockTime = 4.seconds)
        SkillAppliers[mskillDeathgnash_1721] = SkillApplier(targetEvaluator = currentHpDamage(percent = 0.90f, damageType = AttackDamageType.Static))

        MobSkills[mskillMayhemLantern_2383] = MobSkill(rangeInfo = extendedSourceRange)
        SkillAppliers[mskillMayhemLantern_2383] = SkillApplier(noop())
        SkillHeuristics[mskillMayhemLantern_2383] = requireAppearanceState(0)

        // Apkallu: Yawn
        MobSkills[mskillYawn_1457] = MobSkill(rangeInfo = extendedSourceRange)
        SkillAppliers[mskillYawn_1457] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true, attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Sleep, baseDuration = 18.seconds))
        )))

        // Apkallu: Wing Slap
        MobSkills[mskillWingSlap_1458] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillWingSlap_1458] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 4, ftpSpread = true,
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 5.seconds))
        ) { 0.6f })

        // Apkallu: Beak Lunge
        MobSkills[mskillBeakLunge_1459] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBeakLunge_1459] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 2)
        ) { 2.25f })

        // Apkallu: Frigid Shuffle
        MobSkills[mskillFrigidShuffle_1460] = MobSkill(rangeInfo = extendedSourceRange)
        SkillAppliers[mskillFrigidShuffle_1460] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true, attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Paralysis, baseDuration = 18.seconds) { it.statusState.potency = 30 })
        )))

        // Apkallu: Wing Whirl
        MobSkills[mskillWingWhirl_1461] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillWingWhirl_1461] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Troll: Potent Lunge
        listOf(mskillPotentLunge_1485, mskillPotentLunge_1638).forEach {
            MobSkills[it] = MobSkill(rangeInfo = standardSingleTargetRange)
            SkillAppliers[it] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 4)) { 2f })
        }

        // Troll: Overthrow
        listOf(mskillOverthrow_1486, mskillOverthrow_1639).forEach {
            MobSkills[it] = MobSkill(rangeInfo = standardSingleTargetRange)
            SkillAppliers[it] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 4)) { 2f })
        }

        // Troll: Rock Smash
        listOf(mskillRockSmash_1487, mskillRockSmash_1640).forEach {
            MobSkills[it] = MobSkill(castTime = 4.seconds, rangeInfo = standardSingleTargetRange)
            SkillAppliers[it] = SkillApplier(targetEvaluator = basicPhysicalDamage(
                attackEffects = AttackEffects(attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Petrify, 5.seconds)))
            ) { 2f })
        }

        // Troll: Diamondhide
        listOf(mskillDiamondhide_1488, mskillDiamondhide_1641).forEach {
            MobSkills[it] = MobSkill(rangeInfo = standardSingleTargetRange)
            SkillAppliers[it] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Stoneskin, 5.minutes)
                { se -> se.status.counter = se.context.sourceState.getMaxHp() / 5 }
            )
            SkillHeuristics[it] = avoidOverwritingBuff(StatusEffect.Stoneskin)
        }

        // Troll: Enervation
        listOf(mskillEnervation_1489, mskillEnervation_1642).forEach {
            MobSkills[it] = MobSkill(rangeInfo = standardSourceRange)
            SkillAppliers[it] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOf(
                    AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 15.seconds) { se -> se.statusState.potency = 50 },
                    AttackStatusEffect(statusEffect = StatusEffect.MagicDefDown, baseDuration = 15.seconds) { se -> se.statusState.potency = 50 },
                )),
            ))
        }

        // Troll: Quake Stomp
        listOf(mskillQuakeStomp_1490, mskillQuakeStomp_1643).forEach {
            MobSkills[it] = MobSkill(rangeInfo = standardSingleTargetRange)
            SkillAppliers[it] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Boost, duration = 30.seconds) { se ->
                se.status.counter = 1
                se.status.potency = 100
            })
        }

        // Troll: Zarraqa
        MobSkills[mskillZarraqa_1491] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = ZERO)
        SkillAppliers[mskillZarraqa_1491] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2f })
        SkillHeuristics[mskillZarraqa_1491] = preferFarAway(distanceScore = 2.0, nearDistance = 4f, farDistance = 8f)

        // Troll: Zarbzan
        MobSkills[mskillZarbzan_1492] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillZarbzan_1492] = SkillApplier(targetEvaluator = basicMagicalWs { 2.25f })

        // Cerberus: Lava Spit
        MobSkills[mskillLavaSpit_1529] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillLavaSpit_1529] = SkillApplier(targetEvaluator = basicMagicalWs { 2f })

        // Cerberus: Sulfurous Breath
        MobSkills[mskillSulfurousBreath_1530] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSulfurousBreath_1530] = SkillApplier(targetEvaluator = basicPhysicalDamage { 3f })
        SkillHeuristics[mskillSulfurousBreath_1530] = onlyIfFacingTarget()

        // Cerberus: Scorching Lash
        MobSkills[mskillScorchingLash_1531] = MobSkill(rangeInfo = reverseConeRange, castTime = 1.seconds)
        SkillAppliers[mskillScorchingLash_1531] = SkillApplier(targetEvaluator = basicPhysicalDamage { 3f })
        SkillHeuristics[mskillScorchingLash_1531] = targetIsBehindSource()

        // Cerberus: Ululation
        MobSkills[mskillUlulation_1532] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillUlulation_1532] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 50 }),
        )

        // Cerberus: Magma Hoplon
        MobSkills[mskillMagmaHoplon_1533] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillMagmaHoplon_1533] = SkillApplier(targetEvaluator = compose(
            applyBasicBuff(StatusEffect.BlazeSpikes, duration = 30.seconds) {
                it.status.potency = intPotency(it.context.sourceState, 0.10f)
            },
            applyBasicBuff(StatusEffect.DefenseBoost, duration = 30.seconds) {
                it.status.potency = 33
            },
        ))
        SkillHeuristics[mskillMagmaHoplon_1533] = avoidOverwritingBuff(listOf(StatusEffect.BlazeSpikes, StatusEffect.DefenseBoost))

        // Cerberus: Gates of Hades
        MobSkills[mskillGatesofHades_1534] = MobSkill(castTime = 4.seconds, rangeInfo = extendedSourceRange, bypassParalysis = true)
        SkillAppliers[mskillGatesofHades_1534] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Burn, duration = 15.seconds, potency = 0.2f, secondaryPotency = 0.8f))
        ) { 3f })

        // Cerberus: Howl (unnamed)
        MobSkills[mskill_1636] = MobSkill(rangeInfo = extendedSourceRange, castTime = ZERO, lockTime = 5.seconds, bypassParalysis = true)
        SkillAppliers[mskill_1636] = SkillApplier(targetEvaluator = noop())

        /// ???: Provoke
        MobSkills[mskillProvoke_1635] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = ZERO, cost = zeroCost)
        SkillAppliers[mskillProvoke_1635] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            bypassShadows = true,
            attackStatusEffects = listOf(AttackStatusEffect(statusEffect = StatusEffect.Provoke, baseDuration = 15.seconds)),
        )))

        // Wamouracampa: Amber Scutum
        MobSkills[mskillAmberScutum_1559] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillAmberScutum_1559] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.DefenseBoost, duration = 30.seconds) { it.status.potency = 40 }
        )
        SkillHeuristics[mskillAmberScutum_1559] = avoidOverwritingBuff(StatusEffect.DefenseBoost)

        // Wamouracampa: Vitriolic Spray
        MobSkills[mskillVitriolicSpray_1560] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillVitriolicSpray_1560] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
                attackStatusEffect = spellDamageDoT(StatusEffect.Burn, duration = 15.seconds, potency = 0.2f, secondaryPotency = 0.8f)
        )) { 2.25f })

        // Wamouracampa: Thermal Pulse
        MobSkills[mskillThermalPulse_1561] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillThermalPulse_1561] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 30 })
        ) { 2.5f })

        // Wamouracampa: Cannonball
        MobSkills[mskillCannonball_1562] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillCannonball_1562] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2f })

        // Wamouracampa: Heat Barrier
        MobSkills[mskillHeatBarrier_1563] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHeatBarrier_1563] = SkillApplier(targetEvaluator = compose(
            applyBasicBuff(StatusEffect.BlazeSpikes, duration = 30.seconds) {
                it.status.potency = intPotency(it.context.sourceState, 0.10f)
            },
            applyBasicBuff(StatusEffect.Enfire, duration = 30.seconds) {
                it.status.potency = intPotency(it.context.sourceState, 0.1f)
            },
        ))
        SkillHeuristics[mskillHeatBarrier_1563] = avoidOverwritingBuff(StatusEffect.BlazeSpikes)

        // Flan: Amplification
        MobSkills[mskillAmplification_1565] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillAmplification_1565] = SkillApplier(
            targetEvaluator = compose(
                applyBasicBuff(statusEffect = StatusEffect.MagicAtkBoost, duration = 1.minutes) { it.status.potency = 30 },
                applyBasicBuff(statusEffect = StatusEffect.MagicDefBoost, duration = 1.minutes) { it.status.potency = 30 },
            )
        )
        SkillHeuristics[mskillAmplification_1565] = avoidOverwritingBuff(StatusEffect.MagicAtkBoost)

        // Flan: Boiling Point
        MobSkills[mskillBoilingPoint_1566] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillBoilingPoint_1566] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.MagicDefDown, baseDuration = 18.seconds) { it.statusState.potency = 50 }),
        ) { 2f })

        // Flan: Xenoglossia
        MobSkills[mskillXenoglossia_1567] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillXenoglossia_1567] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Spontaneity, duration = 1.minutes) { it.status.counter = 1 },
        )
        SkillHeuristics[mskillXenoglossia_1567] = avoidOverwritingBuff(StatusEffect.Spontaneity)

        // Flan: Amorphic Spikes
        MobSkills[mskillAmorphicSpikes_1568] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillAmorphicSpikes_1568] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 5, ftpSpread = true) { 0.50f })

        // Mine: Mineblast
        MobSkills[mskillMineBlast_1582] = MobSkill(rangeInfo = SkillRangeInfo( 20f, 20f, AoeType.Source), cost = zeroCost)
        SkillAppliers[mskillMineBlast_1582] = SkillApplier(
            additionalSelfEvaluator = defeatSelf(),
            targetEvaluator = basicMagicalWs { 2.5f }
        )

        // Wamoura: Magma Fan
        MobSkills[mskillMagmaFan_1695] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillMagmaFan_1695] = SkillApplier(targetEvaluator = basicMagicalWs(CombatStat.mnd) { 2.5f })

        // Wamoura: Erratic Flutter
        MobSkills[mskillErraticFlutter_1696] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillErraticFlutter_1696] = SkillApplier(
            targetEvaluator = basicMagicalWs { 2.5f },
            additionalSelfEvaluator = applyBasicBuff(StatusEffect.Haste, duration = 1.minutes) { it.status.potency = 25 },
        )

        // Wamoura: Proboscis
        MobSkills[mskillProboscis_1697] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillProboscis_1697] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 1f, absorptionCap = 30, absorptionResource = ActorResourceType.MP, absorbBuffs = 1, absorbBuffsNegatesAttack = false),
        )) { 1f })

        // Wamoura: Erosion Dust
        MobSkills[mskillErosionDust_1698] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillErosionDust_1698] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackStat = CombatStat.mnd, defendStat = CombatStat.mnd, attackEffects = singleStatus(
                spellDamageDoT(statusEffect = StatusEffect.Dia, duration = 15.seconds, potency = 0.25f, secondaryPotency = 0.8f, attackStat = CombatStat.mnd))
        ) { 2.2f })

        // Wamoura: Exuviation
        MobSkills[mskillExuviation_1699] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillExuviation_1699] = SkillApplier(targetEvaluator = exuviation())
        SkillHeuristics[mskillExuviation_1699] = canEraseDebuffs(baseScore = 0.0)

        // Wivre Skills
        MobSkills[mskillBatterhorn_1843] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillBatterhorn_1843] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 4),
        ) { 2.5f })
        SkillHeuristics[mskillBatterhorn_1843] = onlyIfFacingTarget()

        MobSkills[mskillClobber_1844] = MobSkill(rangeInfo = reverseConeRange, castTime = 1.seconds)
        SkillAppliers[mskillClobber_1844] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 4),
        ) { 2.5f })
        SkillHeuristics[mskillClobber_1844] = targetIsBehindSource()

        MobSkills[mskillDemoralizingRoar_1845] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillDemoralizingRoar_1845] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.AttackDown, baseDuration = 15.seconds) {
                it.statusState.potency = 50
            }
        )))
        SkillHeuristics[mskillDemoralizingRoar_1845] = avoidOverwritingDebuff(StatusEffect.AttackDown)

        MobSkills[mskillBoilingBlood_1846] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBoilingBlood_1846] = SkillApplier(targetEvaluator = compose(
            applyBasicBuff(statusEffect = StatusEffect.Haste, duration = 3.minutes) { it.status.potency = 33 },
            applyBasicBuff(statusEffect = StatusEffect.AttackBoost, duration = 3.minutes) { it.status.potency = 33 },
        ))
        SkillHeuristics[mskillBoilingBlood_1846] = avoidOverwritingBuff(statusEffect = StatusEffect.Berserk)

        MobSkills[mskillGraniteSkin_1847] = MobSkill(standardSingleTargetRange)
        SkillAppliers[mskillGraniteSkin_1847] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Stoneskin, 5.minutes) {
            it.status.counter = it.context.sourceState.getMaxHp() / 4
        })

        MobSkills[mskillCripplingSlam_1848] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillCripplingSlam_1848] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 4, attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            ))
        ) { 2.25f })

        // Vampyr: Bloodrake
        MobSkills[mskillBloodrake_1850] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBloodrake_1850] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true),
        ), numHits = 3, ftpSpread = true) { 0.8f })

        // Vampyr: Decollation
        MobSkills[mskillDecollation_1851] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillDecollation_1851] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Vampyr: Nosferatu's Kiss
        MobSkills[mskillNosferatusKiss_1852] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillNosferatusKiss_1852] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true),
        )) { 2.25f })

        // Vampyr: Heliovoid
        MobSkills[mskillHeliovoid_1853] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillHeliovoid_1853] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0f, absorbBuffs = 1))
        ) { 2f })

        // Vampyr: Wings of Gehenna
        MobSkills[mskillWingsofGehenna_1854] = MobSkill(castTime = 3.5.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillWingsofGehenna_1854] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(knockBackMagnitude = 4, attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 3.seconds))
        ) { 3f })

        // Vampyr: Eternal Damnation
        MobSkills[mskillEternalDamnation_1855] = MobSkill(castTime = 3.seconds, rangeInfo = standardConeRange)
        SkillAppliers[mskillEternalDamnation_1855] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(gazeAttack = true)
        ) { 4f })

        // Vampyr: Minax Glare
        MobSkills[mskillMinaxGlare_2278] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillMinaxGlare_2278] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(gazeAttack = true, attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 15.seconds) { it.statusState.potency = 75 },
            ))
        ) { 1.5f })

        // Rafflesia: Seed spray
        MobSkills[mskillSeedspray_1907] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSeedspray_1907] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            numHits = 3,
            ftpSpread = true,
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 15.seconds) { it.statusState.potency = 33 },
            )),
        ) { 0.75f })

        // Rafflesia: Viscid Emission
        MobSkills[mskillViscidEmission_1908] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillViscidEmission_1908] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(StatusEffect.Amnesia, baseDuration = 8.seconds),
            ))) { 2.25f },
        )

        // Rafflesia: Rotten Stench
        MobSkills[mskillRottenStench_1909] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillRottenStench_1909] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.IntDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.6f },
                AttackStatusEffect(statusEffect = StatusEffect.StrDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.6f },
            )
        )))

        // Rafflesia: Floral Bouquet
        MobSkills[mskillFloralBouquet_1910] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillFloralBouquet_1910] = SkillApplier(targetEvaluator = compose(
            setTp(0),
            basicDebuff(StatusEffect.Sleep, 15.seconds),
        ))

        // Rafflesia: Bloody Caress
        MobSkills[mskillBloodyCaress_1911] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBloodyCaress_1911] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            numHits = 3,
            ftpSpread = true,
            attackEffects = AttackEffects(absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true)),
        ) { 0.85f })

        // Gnole: Fevered Pitch
        MobSkills[mskillFeveredPitch_1914] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFeveredPitch_1914] = SkillApplier(targetEvaluator = basicPhysicalDamage(
                attackEffects = AttackEffects(attackStatusEffects = listOf(
                    AttackStatusEffect(StatusEffect.Stun, 3.seconds),
                    AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 15.seconds) { it.statusState.potency = 33 },
                ))
        ) { 2.35f })
        SkillHeuristics[mskillFeveredPitch_1914] = requireAppearanceState(0)

        // Gnole: Call of the Moon
        val callOfTheMoonApplier = SkillApplier(targetEvaluator = applyBasicBuff(
            statusEffect = StatusEffect.EvasionBoost, duration = 18.seconds
        ) { it.status.potency = 50 })

        MobSkills[mskillCalloftheMoon_1915] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillCalloftheMoon_1915] = callOfTheMoonApplier
        SkillHeuristics[mskillCalloftheMoon_1915] = minimumOf(
            requireAppearanceState(appearanceState = 1),
            avoidOverwritingBuff(StatusEffect.EvasionBoost),
        )

        MobSkills[mskillCalloftheMoon_1916] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillCalloftheMoon_1916] = callOfTheMoonApplier
        SkillHeuristics[mskillCalloftheMoon_1916] = minimumOf(
            requireAppearanceState(appearanceState = 0),
            avoidOverwritingBuff(StatusEffect.EvasionBoost),
        )

        // Gnole: Plenilune Embrace
        val pleniluneApplier = SkillApplier(targetEvaluator = compose(
            sourcePercentageHealingMagic(10f),
            expireTargetDebuffs(maxToExpire = 3),
        ))

        MobSkills[mskillPleniluneEmbrace_1917] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPleniluneEmbrace_1917] = pleniluneApplier
        SkillHeuristics[mskillPleniluneEmbrace_1917] = minimumOf(
            requireAppearanceState(appearanceState = 1),
            onlyIfBelowHppThreshold(0.75),
        )

        MobSkills[mskillPleniluneEmbrace_1918] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPleniluneEmbrace_1918] = pleniluneApplier
        SkillHeuristics[mskillPleniluneEmbrace_1918] = minimumOf(
            requireAppearanceState(appearanceState = 0),
            onlyIfBelowHppThreshold(0.75),
        )

        // Gnole: Nox Blast
        MobSkills[mskillNoxBlast_1919] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillNoxBlast_1919] = SkillApplier(targetEvaluator = compose(
            basicMagicalWs(attackEffects = AttackEffects(knockBackMagnitude = 4)) { 2.35f },
            setTp(0),
        ))
        SkillHeuristics[mskillNoxBlast_1919] = requireAppearanceState(1)

        // Gnole: Asuran Claws
        MobSkills[mskillAsuranClaws_1920] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillAsuranClaws_1920] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            numHits = 6,
            ftpSpread = true
        ) { 0.45f })
        SkillHeuristics[mskillAsuranClaws_1920] = requireAppearanceState(1)

        // Gnole: Cacophony
        MobSkills[mskillCacophony_1921] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillCacophony_1921] = SkillApplier(targetEvaluator = compose(
            applyBasicBuff(statusEffect = StatusEffect.CounterBoost, duration = 21.seconds) { it.status.potency = 25 },
            applyBasicBuff(statusEffect = StatusEffect.Haste, duration = 21.seconds) { it.status.potency = 25 },
        ))
        SkillHeuristics[mskillCacophony_1921] = minimumOf(
            requireAppearanceState(appearanceState = 1),
            avoidOverwritingBuff(StatusEffect.Haste),
        )

        // Slug: Fuscous Ooze
        MobSkills[mskillFuscousOoze_1927] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillFuscousOoze_1927] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            )),
        ) { 2.2f })

        // Slug: Purulent Ooze
        MobSkills[mskillPurulentOoze_1928] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillPurulentOoze_1928] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.HPDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.75f },
                spellDamageDoT(StatusEffect.Bio, duration = 15.seconds, potency = 0.2f, secondaryPotency = 0.8f),
            )),
        ) { 2.2f })

        // Slug: Corrosive Ooze
        MobSkills[mskillCorrosiveOoze_1929] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillCorrosiveOoze_1929] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.AttackDown, baseDuration = 15.seconds) { it.statusState.potency = 33 },
                AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 15.seconds) { it.statusState.potency = 33 },
            )),
        ) { 2.5f })

        // Pixie Skills
        MobSkills[mskillZephyrArrow_1937] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillZephyrArrow_1937] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
                knockBackMagnitude = 3,
                attackStatusEffect = AttackStatusEffect(StatusEffect.Bind, baseDuration = 8.seconds))
        ) { 2.25f })

        MobSkills[mskillLetheArrows_1938] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillLetheArrows_1938] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 3, attackStatusEffects = listOf(
                AttackStatusEffect(StatusEffect.Bind, baseDuration = 8.seconds),
                AttackStatusEffect(StatusEffect.Amnesia, baseDuration = 8.seconds)
            ))
        ) { 2.25f })

        MobSkills[mskillSpringBreeze_1939] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSpringBreeze_1939] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            damageResource = ActorResourceType.TP,
            attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Sleep, baseDuration = 10.seconds))
        )) { 50f })

        MobSkills[mskillSummerBreeze_1940] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSummerBreeze_1940] = SkillApplier(targetEvaluator = compose(
            expireTargetDebuffs(maxToExpire = 999),
            applyBasicBuff(statusEffect = StatusEffect.Regain, duration = 15.seconds) { it.status.potency = 90 }
        ))
        SkillHeuristics[mskillSummerBreeze_1940] = canEraseDebuffs()

        MobSkills[mskillAutumnBreeze_1941] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillAutumnBreeze_1941] = SkillApplier(targetEvaluator = sourcePercentageHealingMagic(33f))
        SkillHeuristics[mskillAutumnBreeze_1941] = CommonSkillHeuristics.healingBasedOnHpp()

        MobSkills[mskillWinterBreeze_1942] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillWinterBreeze_1942] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(dispelCount = 1)
        ) { 2.2f })

        MobSkills[mskillCyclonicTurmoil_1943] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillCyclonicTurmoil_1943] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(knockBackMagnitude = 6, dispelCount = 4)) { 2.5f }
        )

        MobSkills[mskillCyclonicTorrent_1944] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillCyclonicTorrent_1944] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(knockBackMagnitude = 6, attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 10.seconds)
            ))
        ) { 2.5f })

        MobSkills[mskillNornArrows_2262] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillNornArrows_2262] = SkillApplier(targetEvaluator = basicMagicalWs { 3f })

        MobSkills[mskillCyclonicBlight_2438] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillCyclonicBlight_2438] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = singleStatus(
            knockBackMagnitude = 6,
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Sleep, baseDuration = 10.seconds),
        )) { 2.5f })

        MobSkills[mskillEldritchWind_2566] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillEldritchWind_2566] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Addle, baseDuration = 15.seconds) { it.statusState.potency = 40 },
            AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 15.seconds) { it.statusState.potency = 5 },
        ))) { 2.5f })

        // Seed Crystal: Auto-Attack
        MobSkills[mskillSeedAutoAttack_2159] = MobSkill(rangeInfo = SkillRangeInfo(maxTargetDistance = 100f, effectRadius = 0f, type = AoeType.None), cost = zeroCost, castTime = ZERO)
        SkillAppliers[mskillSeedAutoAttack_2159] = SkillApplier(targetEvaluator = basicPhysicalDamage { 1f })

        // Seed Crystal: Seed of Deception
        MobSkills[mskillSeedofDeception_2160] = MobSkill(rangeInfo = SkillRangeInfo(maxTargetDistance = 100f, effectRadius = 0f, type = AoeType.None), castTime = 3.5.seconds)
        SkillAppliers[mskillSeedofDeception_2160] = SkillApplier(targetEvaluator = noop())

        // Seed Crystal: Seed of Deference
        MobSkills[mskillSeedofDeference_2161] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSeedofDeference_2161] = SkillApplier(targetEvaluator = {
            AttackContext.compose(it.context) {
                val costume = it.targetState.gainStatusEffect(StatusEffect.CostumeDebuff, duration = 6.seconds)
                costume.counter = 0x930
            }
            emptyList()
        })
        SkillHeuristics[mskillSeedofDeference_2161] = avoidOverwritingDebuff(StatusEffect.CostumeDebuff)

        // Seed Crystal: Seed of Nihility
        MobSkills[mskillSeedofNihility_2162] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSeedofNihility_2162] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Amnesia, baseDuration = 15.seconds))
        ) { 2f })

        // Seed Crystal: Seed of Judgement
        MobSkills[mskillSeedofJudgment_2163] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillSeedofJudgment_2163] = SkillApplier(targetEvaluator = basicMagicalWs { 3.5f })

        // Gargouille: Dark Orb
        MobSkills[mskillDarkOrb_2165] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillDarkOrb_2165] = SkillApplier(targetEvaluator = basicMagicalWs() { 2.2f })
        SkillHeuristics[mskillDarkOrb_2165] = requireAppearanceState(1)

        // Gargouille: Dark Mist
        MobSkills[mskillDarkMist_2166] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillDarkMist_2166] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Weight, 15.seconds) { it.statusState.potency = 50 })
        ) { 2f })
        SkillHeuristics[mskillDarkMist_2166] = requireAppearanceState(1)

        // Gargouille: Triumphant Roar
        MobSkills[mskillTriumphantRoar_2167] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillTriumphantRoar_2167] = SkillApplier(targetEvaluator = applyBasicBuff(
            statusEffect = StatusEffect.AttackBoost, duration = 30.seconds
        ) { it.status.potency = 33 })
        SkillHeuristics[mskillTriumphantRoar_2167] = minimumOf(requireAppearanceState(0), avoidOverwritingBuff(StatusEffect.AttackBoost))

        // Gargouille: Terror Eye
        MobSkills[mskillTerrorEye_2168] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillTerrorEye_2168] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true, attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Stun, 9.seconds))
        )))
        SkillHeuristics[mskillTerrorEye_2168] = requireAppearanceState(0)

        // Gargouille: Bloody Claw
        MobSkills[mskillBloodyClaw_2169] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBloodyClaw_2169] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true, absorbBuffs = 1))
        ) { 2f })
        SkillHeuristics[mskillBloodyClaw_2169] = requireAppearanceState(0)

        // Gargouille: Shadow Burst
        MobSkills[mskillShadowBurst_2170] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillShadowBurst_2170] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Curse, baseDuration = 15.seconds) { it.statusState.potency = 33 },
            ))
        ) { 2.2f })
        SkillHeuristics[mskillShadowBurst_2170] = requireAppearanceState(0)

        // Amphiptere: Tail Lash
        MobSkills[mskillTailLash_2171] = MobSkill(rangeInfo = reverseConeRange, castTime = 1.seconds)
        SkillAppliers[mskillTailLash_2171] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Amnesia, baseDuration = 15.seconds))
        ) { 2f })
        SkillHeuristics[mskillTailLash_2171] = targetIsBehindSource()

        // Amphiptere: Bloody Beak
        MobSkills[mskillBloodyBeak_2172] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillBloodyBeak_2172] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true),
        ), numHits = 3, ftpSpread = true) { 0.7f })
        SkillHeuristics[mskillBloodyBeak_2172] = onlyIfFacingTarget()

        // Amphiptere: Feral Peck
        MobSkills[mskillFeralPeck_2173] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillFeralPeck_2173] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 0.85f })
        SkillHeuristics[mskillFeralPeck_2173] = onlyIfFacingTarget()

        // Amphiptere: Warped Wail
        MobSkills[mskillWarpedWail_2174] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillWarpedWail_2174] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.HPDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.66f },
                AttackStatusEffect(statusEffect = StatusEffect.MPDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.66f }
            )
        )) { 1.5f })

        // Amphiptere: Reaving Wind
        MobSkills[mskillReavingWind_2175] = MobSkill(castTime = 1.seconds, rangeInfo = extendedSourceRange, bypassParalysis = true)
        SkillAppliers[mskillReavingWind_2175] = SkillApplier(targetEvaluator = setTp(0))

        // Amphiptere: Storm Wing
        MobSkills[mskillStormWing_2176] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillStormWing_2176] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = singleStatus(
            knockBackMagnitude = 6,
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 5.seconds),
        )) { 2.4f })
        SkillHeuristics[mskillStormWing_2176] = onlyIfFacingTarget()

        // Amphiptere: Calamitous Wind
        MobSkills[mskillCalamitousWind_2177] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillCalamitousWind_2177] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(dispelCount = 4, knockBackMagnitude = 8)
        ) { 3f })

        // Amphiptere: (Aura knock-back)
        MobSkills[mskill_2178] = MobSkill(castTime = ZERO, cost = zeroCost, rangeInfo = standardSingleTargetRange, lockTime = ZERO)
        SkillAppliers[mskill_2178] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(knockBackMagnitude = 4)
        ) { 1f })

        // Corpselight: Corpse Breath
        MobSkills[mskillCorpseBreath_2255] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillCorpseBreath_2255] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 50 },
        ))) { 2.25f })

        // Corpselight: Louring Skies
        MobSkills[mskillLouringSkies_2569] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillLouringSkies_2569] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 33 },
            AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 15.seconds),
        ))) { 2.75f })

        // Shadow Lord (S): Vicious Kick
        MobSkills[mskillViciousKick_2279] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillViciousKick_2279] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(
                knockBackMagnitude = 4,
                attackStatusEffect = AttackStatusEffect(StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 40 }
            )
        ) { 2f })

        // Shadow Lord (S): Boon Void
        MobSkills[mskillBoonVoid_2280] = MobSkill(castTime = 2.seconds, rangeInfo = standardConeRange)
        SkillAppliers[mskillBoonVoid_2280] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(knockBackMagnitude = 6, dispelCount = 2)
        ) { 2.25f })

        // Shadow Lord (S): Cruel Slash
        MobSkills[mskillCruelSlash_2281] = MobSkill(castTime = 2.5.seconds, rangeInfo = standardSourceRange)
        SkillAppliers[mskillCruelSlash_2281] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 12.seconds),
            AttackStatusEffect(statusEffect = StatusEffect.Amnesia, baseDuration = 12.seconds),
        ))) { 2.25f })

        // Shadow Lord (S): Spell Wall
        MobSkills[mskillSpellWall_2282] = MobSkill(rangeInfo = standardSingleTargetRange, bypassParalysis = true)
        SkillAppliers[mskillSpellWall_2282] = SkillApplier(targetEvaluator = noop())

        // Shadow Lord (S): Implosion
        MobSkills[mskillImplosion_2283] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds, lockTime = 3.seconds)
        SkillAppliers[mskillImplosion_2283] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = singleStatus(
            knockBackMagnitude = 6,
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.HPDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.66f },
        )) { 2.75f })

        // Shadow Lord (S): Umbral Orb
        MobSkills[mskillUmbralOrb_2284] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillUmbralOrb_2284] = SkillApplier(targetEvaluator = basicMagicalWs { 2.25f })

        // Shadow Lord (S): Cross Smash
        MobSkills[mskillCrossSmash_2285] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillCrossSmash_2285] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.DefenseDown, baseDuration = 15.seconds) { it.statusState.potency = 33 }
        )) { 2.25f })

        // Shadow Lord (S): Blighting Blitz
        MobSkills[mskillBlightingBlitz_2286] = MobSkill(castTime = 2.5.seconds, rangeInfo = standardConeRange)
        SkillAppliers[mskillBlightingBlitz_2286] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 4, ftpSpread = true) { 0.75f })

        // Shadow Lord (S): Spawn Shadow
        MobSkills[mskillSpawnShadow_2287] = MobSkill(castTime = 4.seconds, cost = zeroCost, rangeInfo = standardSingleTargetRange, bypassParalysis = true)
        SkillAppliers[mskillSpawnShadow_2287] = SkillApplier(targetEvaluator = noop())

        // Shadow Lord (S): Soma Wall
        MobSkills[mskillSomaWall_2288] = MobSkill(rangeInfo = standardSingleTargetRange, bypassParalysis = true)
        SkillAppliers[mskillSomaWall_2288] = SkillApplier(targetEvaluator = noop())

        // Shadow Lord (S): Doom Arc
        MobSkills[mskillDoomArc_2289] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.5.seconds, lockTime = 3.seconds, bypassParalysis = true)
        SkillAppliers[mskillDoomArc_2289] = SkillApplier(targetEvaluator = noop())

        // Briareus: Mercurial Strike (Normal)
        MobSkills[mskillMercurialStrike_2320] = MobSkill(castTime = 3.seconds, rangeInfo = SkillRangeInfo(50f, 8f, AoeType.Target, tracksTarget = true))
        SkillAppliers[mskillMercurialStrike_2320] = SkillApplier(MobBriareusController.getMercurialStrikeTargetEvaluator())

        // Briareus: Mercurial Strike (777)
        MobSkills[mskillMercurialStrike_2321] = MobSkill(castTime = 3.seconds, rangeInfo = SkillRangeInfo(50f, 8f, AoeType.Target, tracksTarget = true))
        SkillAppliers[mskillMercurialStrike_2321] = SkillApplier(MobBriareusController.getMercurialStrikeTargetEvaluator())

        // Briareus: Colossal Slam
        MobSkills[mskillColossalSlam_2322] = MobSkill(castTime = 3.seconds, rangeInfo = SkillRangeInfo(12f, 12f, AoeType.Source))
        SkillAppliers[mskillColossalSlam_2322] = SkillApplier(basicPhysicalDamage { 3f } )

        // Harpeia: Auto Attack (Kick)
        MobSkills[mskillHarpeiaAutoAttack_2466] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskillHarpeiaAutoAttack_2466] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        // Harpeia: Auto Attack (Swipe)
        MobSkills[mskillHarpeiaAutoAttack_2467] = MobSkill(castTime = ZERO, rangeInfo = standardConeRange, cost = zeroCost)
        SkillAppliers[mskillHarpeiaAutoAttack_2467] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        // Harpeia: Auto Attack (Spin)
        MobSkills[mskillHarpeiaAutoAttack_2468] = MobSkill(castTime = ZERO, rangeInfo = standardSourceRange, cost = zeroCost)
        SkillAppliers[mskillHarpeiaAutoAttack_2468] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        // Harpeia: Rending Talons
        MobSkills[mskillRendingTalons_2469] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillRendingTalons_2469] = SkillApplier(targetEvaluator = compose(
            setTp(0),
            basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 4)) { 2.5f },
        ))

        // Harpeia: Shrieking Gale
        MobSkills[mskillShriekingGale_2470] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillShriekingGale_2470] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(knockBackMagnitude = 6, dispelCount = 3))
        { 2.5f })

        // Harpeia: Wings of Woe
        MobSkills[mskillWingsofWoe_2471] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillWingsofWoe_2471] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 6.seconds),
                AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 15.seconds),
                AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 15.seconds) { it.statusState.potency = 5 },
            )))
        { 2.25f })

        // Harpeia: Wings of Agony
        MobSkills[mskillWingsofAgony_2472] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillWingsofAgony_2472] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Sleep, baseDuration = 15.seconds),
                AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            )))
        { 2.25f })

        // Harpeia: Typhoean Rage
        MobSkills[mskillTyphoeanRage_2473] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillTyphoeanRage_2473] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(
                knockBackMagnitude = 6,
                attackStatusEffects = listOf(AttackStatusEffect(statusEffect = StatusEffect.Amnesia, baseDuration = 15.seconds)),
            ))
        { 2.75f })

        // Harpeia: Ravenous Wail
        MobSkills[mskillRavenousWail_2474] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillRavenousWail_2474] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 4.seconds),
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 6.seconds),
            )))
        { 2.5f })

        // Harpeia: Kaleidoscopic Fury
        MobSkills[mskillKaleidoscopicFury_2502] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillKaleidoscopicFury_2502] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                spellDamageDoT(statusEffect = StatusEffect.Dia, duration = 15.seconds, potency = 0.66f, secondaryPotency = 0.6f, attackStat = CombatStat.mnd),
            )))
        { 2f })

        // Harpeia: Keraunos Quill
        MobSkills[mskillKeraunosQuill_2555] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillKeraunosQuill_2555] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 3.seconds),
                AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            )))
        { 2.75f })

        // Belladonna: Auto attack (Slam)
        MobSkills[mskillBelladonnaAutoAttack_2621] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskillBelladonnaAutoAttack_2621] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        // Belladonna: Auto attack (Swipe)
        MobSkills[mskillBelladonnaAutoAttack_2622] = MobSkill(castTime = ZERO, rangeInfo = standardConeRange, cost = zeroCost)
        SkillAppliers[mskillBelladonnaAutoAttack_2622] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(StatusEffect.Weight, 5.seconds) { it.statusState.potency = 33 })
        ))

        // Belladonna: Auto attack (Spin)
        MobSkills[mskillBelladonnaAutoAttack_2623] = MobSkill(castTime = ZERO, rangeInfo = standardSourceRange, cost = zeroCost)
        SkillAppliers[mskillBelladonnaAutoAttack_2623] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 2)
        ))

        // Belladonna: Night Stalker
        MobSkills[mskillNightStalker_2624] = MobSkill(rangeInfo = standardConeRange, castTime = 1.5.seconds)
        SkillAppliers[mskillNightStalker_2624] = SkillApplier(targetEvaluator = compose(
            setTp(0),
            basicPhysicalDamage { 2.5f },
        ))

        // Belladonna: Atropine Spore
        MobSkills[mskillAtropineSpore_2625] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillAtropineSpore_2625] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 9.seconds),
            AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 9.seconds) { it.statusState.potency = 33 },
            AttackStatusEffect(statusEffect = StatusEffect.Sleep, baseDuration = 9.seconds),
        ))) { 2.5f })

        // Belladonna: Frond Fatale
        MobSkills[mskillFrondFatale_2626] = MobSkill(castTime = 2.5.seconds, rangeInfo = standardConeRange)
        SkillAppliers[mskillFrondFatale_2626] = SkillApplier(targetEvaluator = noop())

        // Belladonna: Full Bloom
        MobSkills[mskillFullBloom_2627] = MobSkill(rangeInfo = extendedSourceRange)
        SkillAppliers[mskillFullBloom_2627] = SkillApplier(targetEvaluator = noop())

        // Belladonna: Deracinator
        MobSkills[mskillDeracinator_2628] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillDeracinator_2628] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 9.seconds) { it.statusState.potency = 33 },
            AttackStatusEffect(statusEffect = StatusEffect.Amnesia, baseDuration = 9.seconds),
        ))) { 3f })

        // Belladonna: Beautiful Death
        MobSkills[mskillBeautifulDeath_2629] = MobSkill(rangeInfo = standardSourceRange, bypassParalysis = true)
        SkillAppliers[mskillBeautifulDeath_2629] = SkillApplier(targetEvaluator = noop())

        // Marolith: Metamorphic Blast
        MobSkills[mskillMetamorphicBlast_2671] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillMetamorphicBlast_2671] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            knockBackMagnitude = 4,
            attackStatusEffects = listOf(
                spellDamageDoT(StatusEffect.Rasp, duration = 15.seconds, potency = 0.5f, secondaryPotency = 0.8f),
            ))
        ) { 2.75f })

        // Marolith: Enervating Grasp
        MobSkills[mskillEnervatingGrasp_2672] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = 3.seconds)
        SkillAppliers[mskillEnervatingGrasp_2672] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 12.seconds) { it.statusState.potency = 33 },
            AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 12.seconds) { it.statusState.potency = 50 },
            AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 12.seconds),
        ))) { 2.5f })

        // Marolith: Orogenic Storm
        MobSkills[mskillOrogenicStorm_2673] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillOrogenicStorm_2673] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            knockBackMagnitude = 4,
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 12.seconds) { it.statusState.potency = 33 },
                AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 12.seconds) { it.statusState.potency = 50 },
            )
        )) { 3f })

        // Marolith: Subduction
        MobSkills[mskillSubduction_2674] = MobSkill(rangeInfo = standardStaticTargetAoe, castTime = 1.5.seconds)
        SkillAppliers[mskillSubduction_2674] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 12.seconds) { it.statusState.potency = 50 },
        ))) { 2f })

        // Twitherym: Tempestuous Upheaval
        MobSkills[mskillTempestuousUpheaval_2694] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillTempestuousUpheaval_2694] = SkillApplier(
            targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(knockBackMagnitude = 4)) { 2.75f }
        )

        // Twitherym: Slice'n'Dice
        MobSkills[mskillSlicenDice_2695] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSlicenDice_2695] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
                attackStatusEffect = spellDamageDoT(StatusEffect.Choke, duration = 15.seconds, potency = 0.2f, secondaryPotency = 0.8f),
            )) { 2.25f }
        )

        // Twitherym: Black Out
        MobSkills[mskillBlackout_2696] = MobSkill(castTime = 3.seconds, rangeInfo = standardSourceRange)
        SkillAppliers[mskillBlackout_2696] = SkillApplier(
            targetEvaluator = basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 10.seconds) { it.statusState.potency = 50 },
                AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 10.seconds) { it.statusState.potency = 50 },
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 10.seconds) { it.statusState.potency = 50 },
            )))
        )

        // Craklaw: Auto-attack Triple Snip
        MobSkills[mskill_2698] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskill_2698] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 0.33f })

        // Craklaw: Auto-attack Swipe
        MobSkills[mskill_2699] = MobSkill(castTime = ZERO, rangeInfo = standardConeRange, cost = zeroCost)
        SkillAppliers[mskill_2699] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(
                statusEffect = StatusEffect.VitDown, baseDuration = 3.seconds) { it.statusState.secondaryPotency = 0.8f },
            )
        ) { 1f })

        // Craklaw: Auto-attack Spin
        MobSkills[mskill_2700] = MobSkill(castTime = ZERO, rangeInfo = standardSourceRange, cost = zeroCost)
        SkillAppliers[mskill_2700] = SkillApplier(targetEvaluator = basicPhysicalDamage { 1f })

        // Craklaw: Impenetrable Carapce
        MobSkills[mskillImpenetrableCarapace_2701] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillImpenetrableCarapace_2701] = SkillApplier(targetEvaluator = compose(
            applyBasicBuff(statusEffect = StatusEffect.MagicDefBoost, duration = 18.seconds) { it.status.potency = 50 },
            applyBasicBuff(statusEffect = StatusEffect.DefenseBoost, duration = 18.seconds) { it.status.potency = 50 },
        ))

        // Craklaw: Rending Deluge
        MobSkills[mskillRendingDeluge_2702] = MobSkill(castTime = 3.seconds, rangeInfo = standardSourceRange)
        SkillAppliers[mskillRendingDeluge_2702] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(dispelCount = 2)
        ) { 3f })

        // Craklaw: Sundering Snip
        MobSkills[mskillSunderingSnip_2703] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSunderingSnip_2703] = SkillApplier(targetEvaluator = maxHpDamage(
            percent = 0.5f,
            damageType = AttackDamageType.Physical,
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.MagicDefDown, baseDuration = 18.seconds) { it.statusState.potency = 50 }),
        ))

        // Craklaw: Viscid Spindrift
        MobSkills[mskillViscidSpindrift_2704] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillViscidSpindrift_2704] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.AttackDown, baseDuration = 15.seconds) { it.statusState.potency = 33 },
                AttackStatusEffect(statusEffect = StatusEffect.MagicAtkDown, baseDuration = 15.seconds) { it.statusState.potency = 33 },
                AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 33 }
            )),
        ) { 2f })

        // Umbril: Paralyzing Triad
        MobSkills[mskillParalyzingTriad_2714] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillParalyzingTriad_2714] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            numHits = 3,
            ftpSpread = true,
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 12.seconds) { it.statusState.potency = 66 })
        ) { 0.8f })

        // Umbril: Crepuscular Grasp
        MobSkills[mskillCrepuscularGrasp_2715] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillCrepuscularGrasp_2715] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 12.seconds),
            spellDamageDoT(statusEffect = StatusEffect.Bio, duration = 12.seconds, potency = 0.33f, secondaryPotency = 0.8f),
            AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 12.seconds) { it.statusState.potency = 50 },
        ))) { 2.5f })

        // Umbril: Necrotic Brume
        MobSkills[mskillNecroticBrume_2716] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillNecroticBrume_2716] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 12.seconds) { it.statusState.potency = 25 },
            AttackStatusEffect(statusEffect = StatusEffect.Curse, baseDuration = 12.seconds) { it.statusState.potency = 33 },
            AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 12.seconds) { it.statusState.potency = 50 },
        ))) { 2.5f })

        // Umbril: Terminal Bloom
        MobSkills[mskillTerminalBloom_2717] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillTerminalBloom_2717] = SkillApplier(targetEvaluator = basicMagicalWs(
            ftp = sourceCurrentHppPower(zeroHpPower = 1f, maxHpPower = 8f))
        )
        SkillHeuristics[mskillTerminalBloom_2717] = onlyIfBelowHppThreshold(0.5)

        // Acuex: Foul Waters
        MobSkills[mskillFoulWaters_2718] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillFoulWaters_2718] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                spellDamageDoT(StatusEffect.Drown, duration = 15.seconds, potency = 0.5f, secondaryPotency = 0.8f),
                AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            ))
        ) { 2f })

        // Acuex: PestilentPlume
        MobSkills[mskillPestilentPlume_2719] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillPestilentPlume_2719] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 15.seconds) { it.statusState.potency = 5 },
                AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 33 },
                AttackStatusEffect(statusEffect = StatusEffect.MagicDefDown, baseDuration = 15.seconds) { it.statusState.potency = 33 },
            ))
        ) { 2f })

        // Acuex: Deadening Haze
        MobSkills[mskillDeadeningHaze_2720] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillDeadeningHaze_2720] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 10.seconds))
        ) { 2f })
        SkillHeuristics[mskillDeadeningHaze_2720] = requireAppearanceState(appearanceState = 0)

        // Bztavian: Auto-attack Bite
        MobSkills[mskill_2743] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskill_2743] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        // Bztavian: Auto-attack Scratch
        MobSkills[mskill_2744] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskill_2744] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 2, ftpSpread = true, attackEffects = AttackEffects(knockBackMagnitude = 2)) { 0.5f },
        )

        // Bztavian: Auto-attack Spray
        MobSkills[mskill_2745] = MobSkill(castTime = ZERO, rangeInfo = standardSourceRange, cost = zeroCost)
        SkillAppliers[mskill_2745] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        // Bztavian: Mandibular Lashing
        MobSkills[mskillMandibularLashing_2746] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillMandibularLashing_2746] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
                attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 3.seconds)
            )) { 2.5f },
        )

        // Bztavian: Vespine Hurricane
        MobSkills[mskillVespineHurricane_2747] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillVespineHurricane_2747] = SkillApplier(
            targetEvaluator = basicMagicalWs(attackEffects = singleStatus(
                knockBackMagnitude = 2,
                attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 3.seconds)
            )) { 2f },
        )

        // Bztavian: Stinger Volley
        MobSkills[mskillStingerVolley_2748] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillStingerVolley_2748] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
                attackStatusEffect = spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds)
            )) { 2f },
        )

        // Bztavian: Droning Whirlwind
        MobSkills[mskillDroningWhirlwind_2749] = MobSkill(castTime = 3.seconds, rangeInfo = maxSourceRange)
        SkillAppliers[mskillDroningWhirlwind_2749] = SkillApplier(
            targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(knockBackMagnitude = 8)) { 2f }
        )

        // Bztavian: Incisive Denouement
        MobSkills[mskillIncisiveDenouement_2750] = MobSkill(rangeInfo = SkillRangeInfo( 80f, 0f, AoeType.None))
        SkillAppliers[mskillIncisiveDenouement_2750] = SkillApplier(targetEvaluator = basicPhysicalDamage { 1.25f })

        // Rockfin: Auto-attack Bite
        MobSkills[mskill_2752] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskill_2752] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 2, ftpSpread = true) { 0.5f },
        )

        // Rockfin: Auto-attack Charge
        MobSkills[mskill_2753] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskill_2753] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 1)) { 1f }
        )

        // Rockfin: Auto-attack Spin
        MobSkills[mskill_2754] = MobSkill(castTime = ZERO, rangeInfo = standardSourceRange, cost = zeroCost)
        SkillAppliers[mskill_2754] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 2)) { 1f }
        )

        // Rockfin: Protolithic Puncture
        MobSkills[mskillProtolithicPuncture_2755] = MobSkill(castTime = 4.seconds, rangeInfo = extendedSingleTargetRange)
        SkillAppliers[mskillProtolithicPuncture_2755] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 4f }
        )

        // Rockfin: Aquatic Lance
        MobSkills[mskillAquaticLance_2756] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillAquaticLance_2756] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.VitDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.75f })
            ) { 3f }
        )

        // Rockfin: Pelagic Cleaver
        MobSkills[mskillPelagicCleaver_2757] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillPelagicCleaver_2757] = SkillApplier(
            targetEvaluator = basicMagicalWs(
                attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.HPDown, baseDuration = 9.seconds) { it.statusState.secondaryPotency = 0.75f })
            ) { 2.5f }
        )

        // Rockfin: Carcharian Verve
        MobSkills[mskillCarcharianVerve_2758] = MobSkill(rangeInfo = standardSingleTargetRange, interruptable = false)
        SkillAppliers[mskillCarcharianVerve_2758] = SkillApplier(
            targetEvaluator = compose(
                applyBasicBuff(statusEffect = StatusEffect.MagicAtkBoost, duration = 1.minutes) { it.status.potency = 50 },
                applyBasicBuff(statusEffect = StatusEffect.AttackBoost, duration = 1.minutes) { it.status.potency = 50 },
            )
        )

        // Rockfin: Tidal Guillotine
        MobSkills[mskillTidalGuillotine_2759] = MobSkill(rangeInfo = SkillRangeInfo(20f, 20f, AoeType.Cone))
        SkillAppliers[mskillTidalGuillotine_2759] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 4f }
        )

        // Rockfin: Marine Mayhem
        MobSkills[mskillMarineMayhem_2760] = MobSkill(rangeInfo = maxSourceRange)
        SkillAppliers[mskillMarineMayhem_2760] = SkillApplier(targetEvaluator = marineMayhem())

        // Obstacle (Blossom)
        MobSkills[mskillBlossomSpore_2771] = MobSkill(castTime = ZERO, cost = zeroCost, rangeInfo = standardSourceRange)
        SkillAppliers[mskillBlossomSpore_2771] = SkillApplier(targetEvaluator = {
            AttackContext.compose(it.context) {
                val statusState = it.targetState.gainStatusEffect(status = StatusEffect.Paralysis, duration = 15.seconds)
                statusState.potency = 80
            }
            emptyList()
        })

        // Obstacle (Fungus)
        MobSkills[mskillFungusSpore_2772] = MobSkill(castTime = ZERO, cost = zeroCost, rangeInfo = standardSourceRange)
        SkillAppliers[mskillFungusSpore_2772] = SkillApplier(targetEvaluator = {
            AttackContext.compose(it.context) {
                val attackStatusEffect = spellDamageDoT(StatusEffect.Poison, potency = 1f, duration = 15.seconds)
                val statusState = it.targetState.gainStatusEffect(status = attackStatusEffect.statusEffect, duration = attackStatusEffect.baseDuration)
                attackStatusEffect.decorator(AttackStatusEffectContext(it.sourceState, it.targetState, statusState, it.skill))
            }
            emptyList()
        })

        // Panopt: Retinal Glare
        MobSkills[mskillRetinalGlare_2774] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillRetinalGlare_2774] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Flash, baseDuration = 12.seconds) { it.statusState.potency = 90 },
            ))
        ) { 2.5f })

        // Panopt: Sylvan Slumber
        MobSkills[mskillSylvanSlumber_2775] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSylvanSlumber_2775] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = AttackEffects(gazeAttack = true, attackStatusEffects = listOf(
                    AttackStatusEffect(statusEffect = StatusEffect.Flash, baseDuration = 12.seconds) { it.statusState.potency = 95 },
            ))
        ))

        // Panopt: Crushing Gaze
        MobSkills[mskillCrushingGaze_2776] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillCrushingGaze_2776] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = AttackEffects(gazeAttack = true, attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 12.seconds) { it.statusState.potency = 66 },
                AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 12.seconds) { it.statusState.potency = 66 },
            ))
        ))

        // Panopt: Vaskania
        MobSkills[mskillVaskania_2777] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillVaskania_2777] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Curse, baseDuration = 12.seconds) { it.statusState.potency = 33 },
            ))
        ) { 2.5f })

        // Giant Gnat skills
        MobSkills[mskillGiantGnatAutoAttack_2778] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskillGiantGnatAutoAttack_2778] = SkillApplier(targetEvaluator = basicPhysicalDamage { 1f })

        MobSkills[mskillGiantGnatAutoAttack_2779] = MobSkill(castTime = ZERO, rangeInfo = standardConeRange, cost = zeroCost)
        SkillAppliers[mskillGiantGnatAutoAttack_2779] = SkillApplier(targetEvaluator = basicPhysicalDamage { 1f })

        MobSkills[mskillGiantGnatAutoAttack_2780] = MobSkill(castTime = ZERO, rangeInfo = standardSourceRange, cost = zeroCost)
        SkillAppliers[mskillGiantGnatAutoAttack_2780] = SkillApplier(targetEvaluator = basicPhysicalDamage { 1f })

        MobSkills[mskillFleshSyphon_2781] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = 3.seconds)
        SkillAppliers[mskillFleshSyphon_2781] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(
                knockBackMagnitude = 4,
                absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, absorptionResource = ActorResourceType.HP, proportionalAbsorption = true),
            )
        ) { 2f })

        MobSkills[mskillUmbralExpunction_2782] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillUmbralExpunction_2782] = SkillApplier(targetEvaluator = basicMagicalWs {
            1.5f + (0.5f * it.attacker.getStatusEffects().count { se -> se.statusEffect.debuff })
        })

        MobSkills[mskillStickySituation_2783] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillStickySituation_2783] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 6.seconds),
                AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 12.seconds) { it.statusState.potency = 50 },
                AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 12.seconds) { it.statusState.potency = 5 },
            ))
        ) { 2f })

        MobSkills[mskillAbdominalAssault_2784] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillAbdominalAssault_2784] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 6, attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 12.seconds) { it.statusState.potency = 50 },
            ))
        ) { 2.5f })

        MobSkills[mskillMandibularMassacre_2785] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillMandibularMassacre_2785] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 4, attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 12.seconds) { it.statusState.potency = 50 },
            ))
        ) { 2.5f })

        // Yggdreant: Auto Attack (Poke)
        MobSkills[mskillYggdreantAutoAttack_2798] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskillYggdreantAutoAttack_2798] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(absorptionEffect = AttackAbsorptionEffect(absorption = 3f, absorptionResource = ActorResourceType.TP))
        ) { 0.8f })

        // Yggdreant: Auto Attack (Sweep)
        MobSkills[mskillYggdreantAutoAttack_2799] = MobSkill(castTime = ZERO, rangeInfo = standardConeRange, cost = zeroCost)
        SkillAppliers[mskillYggdreantAutoAttack_2799] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(StatusEffect.AgiDown, 5.seconds) { it.statusState.secondaryPotency = 0.9f })
        ) { 1f })

        // Yggdreant: Auto Attack (Spin)
        MobSkills[mskillYggdreantAutoAttack_2800] = MobSkill(castTime = ZERO, rangeInfo = standardConeRange, cost = zeroCost)
        SkillAppliers[mskillYggdreantAutoAttack_2800] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 2)
        ))

        // Yggdreant: Root of the Problem
        MobSkills[mskillRootoftheProblem_2801] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRootoftheProblem_2801] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 3f, absorbBuffs = 1, absorptionResource = ActorResourceType.TP))
        ) { 2.25f })

        // Yggdreant: Potted Plant
        MobSkills[mskillPottedPlant_2802] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillPottedPlant_2802] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 12.seconds),
                AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 12.seconds) { it.statusState.potency = 33 },
            ))
        ) { 2.5f })

        // Yggdreant: Uproot
        MobSkills[mskillUproot_2803] = MobSkill(
            castTime = 5.seconds,
            rangeInfo = maxSourceRange,
            bypassParalysis = true,
        )
        SkillAppliers[mskillUproot_2803] = SkillApplier(targetEvaluator = compose(
            basicDebuff(StatusEffect.Slow, 15.seconds) { it.statusState.potency = 50 },
            {
                listOf(ActorDamagedEvent(
                    sourceId = it.sourceState.id,
                    targetId = it.targetState.id,
                    amount = it.targetState.getHp() - 1,
                    actionContext = it.context,
                    damageType = AttackDamageType.Static,
                ))
            }
        ), additionalSelfEvaluator = expireTargetDebuffs(maxToExpire = 999))

        // Yggdreant: Canopierce
        MobSkills[mskillCanopierce_2804] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillCanopierce_2804] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                spellDamageDoT(StatusEffect.Rasp, duration = 15.seconds, potency = 0.5f, secondaryPotency = 0.8f),
            ))
        ) { 2f })

        // Yggdreant: Firefly Fandango
        MobSkills[mskillFireflyFandango_2805] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillFireflyFandango_2805] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 12.seconds) { it.statusState.potency = 33 },
                AttackStatusEffect(statusEffect = StatusEffect.Flash, baseDuration = 12.seconds) { it.statusState.potency = 90 },
                AttackStatusEffect(statusEffect = StatusEffect.MPDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.5f },
            ))
        ) { 2.65f })

        // Yggdreant: Timber
        MobSkills[mskillTiiimbeeer_2806] = MobSkill(
            rangeInfo = maxSourceRange,
            bypassParalysis = true,
        )
        SkillAppliers[mskillTiiimbeeer_2806] = SkillApplier(targetEvaluator = noop())


        // Tulfaire Skills
        MobSkills[mskillMoltingPlumage_2807] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillMoltingPlumage_2807] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(dispelCount = 2, knockBackMagnitude = 4)
        ) { 2.25f })

        MobSkills[mskillPentapeck_2808] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPentapeck_2808] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            numHits = 5,
            ftpSpread = true,
            attackEffects = AttackEffects(attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Amnesia, baseDuration = 10.seconds)))
        ) { 0.6f })

        MobSkills[mskillSwoopingFrenzy_2809] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSwoopingFrenzy_2809] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            knockBackMagnitude = 4,
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 15.seconds) { se -> se.statusState.potency = 50 },
                AttackStatusEffect(statusEffect = StatusEffect.MagicDefDown, baseDuration = 15.seconds) { se -> se.statusState.potency = 50 },
            )
        )) { 2.25f })

        MobSkills[mskillFromtheSkies_2810] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillFromtheSkies_2810] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            knockBackMagnitude = 6,
            attackStatusEffects = listOf(spellDamageDoT(StatusEffect.Choke, duration = 15.seconds, potency = 0.4f, secondaryPotency = 0.7f)),
        )) { 2.25f })

        // Fake skill - unused animation?
        MobSkillNameTable.mutate(mskill_2811.id, "Freezing Gale")
        MobSkillInfoTable.create(mskill_2811, MobSkillInfo(id = 2811, animationId = 0x903, type = 4))
        MobSkills[mskill_2811] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskill_2811] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Terror, baseDuration = 5.seconds),
                spellDamageDoT(StatusEffect.Frost, duration = 15.seconds, potency = 0.4f, secondaryPotency = 0.7f),
            ),
        )) { 2.25f })

        // Snapweed: Tickling Tendrils
        MobSkills[mskillTicklingTendrils_2841] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillTicklingTendrils_2841] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            numHits = 5,
            ftpSpread = true,
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 3.seconds))
        ) { 0.6f })

        // Snapweed: Stink Bomb
        MobSkills[mskillStinkBomb_2842] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillStinkBomb_2842] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 9.seconds) { it.statusState.potency = 50 },
            AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 9.seconds) { it.statusState.potency = 33 },
        ))) { 2f })

        // Snapweed: Nectarous Deluge
        MobSkills[mskillNectarousDeluge_2843] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillNectarousDeluge_2843] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
        ) { 2f })

        // Snapweed: Nepenthic Plunge
        MobSkills[mskillNepenthicPlunge_2844] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillNepenthicPlunge_2844] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                spellDamageDoT(StatusEffect.Drown, duration = 15.seconds, potency = 0.5f, secondaryPotency = 0.8f),
                AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            ))
        ) { 2f })

        // Snapweed: Infaunal Flop
        MobSkills[mskillInfaunalFlop_2845] = MobSkill(castTime = 3.seconds, rangeInfo = standardConeRange)
        SkillAppliers[mskillInfaunalFlop_2845] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.HPDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.5f },
                AttackStatusEffect(statusEffect = StatusEffect.MPDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.5f },
                AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            ))
        ) { 3f })

        // Ladybug Skills
        MobSkills[mskillSuddenLunge_1922] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSuddenLunge_1922] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(knockBackMagnitude = 2, attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 3.seconds))
        ) { 1.5f })

        MobSkills[mskillNoisomePowder_1923] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillNoisomePowder_1923] = SkillApplier(targetEvaluator = basicDebuff(statusEffect = StatusEffect.AttackDown, duration = 15.seconds) {
            it.statusState.potency = 40
        })
        SkillHeuristics[mskillNoisomePowder_1923] = avoidOverwritingDebuff(StatusEffect.AttackDown)

        MobSkills[mskillNepentheanHum_1924] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillNepentheanHum_1924] = SkillApplier(targetEvaluator = basicDebuff(statusEffect = StatusEffect.Amnesia, duration = 15.seconds))
        SkillHeuristics[mskillNepentheanHum_1924] = avoidOverwritingDebuff(StatusEffect.Amnesia)

        MobSkills[mskillSpiralSpin_1925] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSpiralSpin_1925] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 33 })
        ) { 2.2f })

        // Chapuli Skills
        MobSkills[mskillNaturesMeditation_2689] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillNaturesMeditation_2689] = SkillApplier(targetEvaluator = applyBasicBuff(
            statusEffect = StatusEffect.AttackBoost, duration = 30.seconds
        ) { it.status.potency = 33 })
        SkillHeuristics[mskillNaturesMeditation_2689] = avoidOverwritingBuff(StatusEffect.AttackBoost)

        MobSkills[mskillSensillaBlades_2690] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSensillaBlades_2690] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = AttackEffects(knockBackMagnitude = 3)
        ) { 2.5f })

        MobSkills[mskillTegminaBuffet_2691] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillTegminaBuffet_2691] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            numHits = 3,
            ftpSpread = true,
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Choke, duration = 15.seconds, potency = 0.25f, secondaryPotency = 0.8f)),
        ) { 0.7f })

        MobSkills[mskillSanguinarySlash_2692] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillSanguinarySlash_2692] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
            knockBackMagnitude = 4,
            attackStatusEffect = AttackStatusEffect(StatusEffect.HPDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.66f }
        )) { 2f })

        MobSkills[mskillOrthopterror_2693] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillOrthopterror_2693] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
            knockBackMagnitude = 4,
            attackStatusEffect = AttackStatusEffect(StatusEffect.Terror, baseDuration = 6.seconds)
        )) { 2.5f })

        // Bugbear Skills
        MobSkills[mskillHeavyBlow_101] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHeavyBlow_101] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        MobSkills[mskillHeavyWhisk_102] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillHeavyWhisk_102] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            knockBackMagnitude = 4,
        )) { 2.5f })

        MobSkills[mskillBionicBoost_103] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = 3.seconds)
        SkillAppliers[mskillBionicBoost_103] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.CounterBoost, duration = 30.seconds) { it.status.potency = 33 })
        SkillHeuristics[mskillBionicBoost_103] = avoidOverwritingBuff(StatusEffect.CounterBoost)

        MobSkills[mskillFlyingHipPress_104] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillFlyingHipPress_104] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })

        MobSkills[mskillEarthshock_105] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillEarthshock_105] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 5.seconds),
        ))) { 2f })

        // Bat Skills
        MobSkills[mskillUltrasonics_136] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillUltrasonics_136] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.AgiDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = 0.50f }
        )))

        MobSkills[mskillBloodDrain_138] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBloodDrain_138] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true),
        )) { 2f })

        MobSkills[mskillSubsonics_899] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSubsonics_899] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.DefenseDown, baseDuration = 15.seconds) { it.statusState.potency = 50 }
        )))

        MobSkills[mskillMarrowDrain_900] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillMarrowDrain_900] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, absorptionCap = 50, absorptionResource = ActorResourceType.MP),
        )) { 2f })

        // Dhalmel Skills
        MobSkills[mskillSonicWave_24] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSonicWave_24] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 15.seconds) { it.statusState.potency = 40 }),
        )
        SkillHeuristics[mskillSonicWave_24] = avoidOverwritingDebuff(StatusEffect.DefenseDown)

        MobSkills[mskillStomping_25] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillStomping_25] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        MobSkills[mskillColdStare_28] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillColdStare_28] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true, attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Silence, baseDuration = 15.seconds)))
        ))
        SkillHeuristics[mskillColdStare_28] = avoidOverwritingDebuff(StatusEffect.Silence)

        MobSkills[mskillWhistle_29] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillWhistle_29] = SkillApplier(applyBasicBuff(
            statusEffect = StatusEffect.EvasionBoost, duration = 15.seconds) { it.status.potency = 50 }
        )
        SkillHeuristics[mskillWhistle_29] = avoidOverwritingDebuff(StatusEffect.EvasionBoost)

        MobSkills[mskillBerserk_30] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBerserk_30] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Berserk, duration = 3.minutes),
        )
        SkillHeuristics[mskillBerserk_30] = avoidOverwritingBuff(statusEffect = StatusEffect.Berserk, score = 1.0)

        MobSkills[mskillHealingBreeze_31] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHealingBreeze_31] = SkillApplier(targetEvaluator = sourcePercentageHealingMagic(25f))
        SkillHeuristics.register(mskillHealingBreeze_31, skillHeuristic = onlyIfBelowHppThreshold(0.75))

        // Corse Skills
        MobSkills[mskillMementoMori_274] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillMementoMori_274] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.MagicAtkBoost, duration = 60.seconds) { it.status.potency = 50 },
        )
        SkillHeuristics[mskillMementoMori_274] = avoidOverwritingBuff(statusEffect = StatusEffect.Berserk, score = 1.0)

        MobSkills[mskillSilenceSeal_275] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSilenceSeal_275] = SkillApplier(targetEvaluator = basicDebuff(singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Silence, baseDuration = 15.seconds)
        )))
        SkillHeuristics[mskillSilenceSeal_275] = avoidOverwritingDebuff(StatusEffect.Silence)

        MobSkills[mskillEnvoutement_276] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillEnvoutement_276] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Curse, baseDuration = 15.seconds) { it.statusState.potency = 33 },
            ))
        ) { 2f })

        MobSkills[mskillDanseMacabre_277] = MobSkill(rangeInfo = standardSingleTargetRange, castTime = 3.seconds)
        SkillAppliers[mskillDanseMacabre_277] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.CostumeDebuff, baseDuration = 8.seconds) { it.statusState.counter = 0x23C },
            ))
        ) { 0.5f })
        SkillHeuristics[mskillDanseMacabre_277] = avoidOverwritingDebuff(StatusEffect.CostumeDebuff)

        // Goobbue Skills
        MobSkills[mskillBlow_325] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBlow_325] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 3.seconds))
        ) { 1.75f })

        MobSkills[mskillBeatdown_327] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBeatdown_327] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 5.seconds))
        ) { 2f })

        MobSkills[mskillUppercut_328] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillUppercut_328] = SkillApplier(targetEvaluator = basicPhysicalDamage(
                attackEffects = AttackEffects(knockBackMagnitude = 2)
        ) { 2.5f })

        MobSkills[mskillBlankGaze_330] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBlankGaze_330] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true,
            bypassShadows = true,
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 15.seconds),
            ))))
        SkillHeuristics[mskillBlankGaze_330] = avoidOverwritingDebuff(StatusEffect.Silence)

        MobSkills[mskillAntiphase_331] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillAntiphase_331] = SkillApplier(targetEvaluator = basicDebuff(singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Silence, baseDuration = 15.seconds)
        )))
        SkillHeuristics[mskillAntiphase_331] = avoidOverwritingDebuff(StatusEffect.Silence)

        // Troll King Skills
        MobSkills[mskillArcaneStomp_1550] = MobSkill(castTime = 3.seconds, rangeInfo = standardSourceRange)
        SkillAppliers[mskillArcaneStomp_1550] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.MagicDefBoost, 15.seconds) {
            it.status.potency = 90
        })
        SkillHeuristics[mskillArcaneStomp_1550] = avoidOverwritingBuff(StatusEffect.MagicDefBoost)

        MobSkills[mskillHaymaker_1548] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillHaymaker_1548] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(knockBackMagnitude = 4, attackStatusEffect = AttackStatusEffect(StatusEffect.Amnesia, baseDuration = 15.seconds))
        ) { 2f })

        MobSkills[mskillHeadSnatch_1547] = MobSkill(castTime = 2.5.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillHeadSnatch_1547] = SkillApplier(targetEvaluator = currentHpDamage(percent = 0.90f, damageType = AttackDamageType.Static))

        MobSkills[mskillIncessantFists_1549] = MobSkill(castTime = 4.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillIncessantFists_1549] = SkillApplier(targetEvaluator = basicPhysicalDamage (numHits = 5) { 0.5f })

        MobSkills[mskillPleiadesRay_1551] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillPleiadesRay_1551] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 9.seconds),
            AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 9.seconds) { it.statusState.potency = 25 },
            AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 9.seconds) { it.statusState.potency = 50 },
        ))) { 2.5f })

        MobSkills[mskillSledgehammer_1546] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSledgehammer_1546] = SkillApplier(targetEvaluator = basicPhysicalDamage (
            attackEffects = singleStatus(knockBackMagnitude = 4, attackStatusEffect = AttackStatusEffect(StatusEffect.Petrify, baseDuration = 5.seconds))
        ) { 2f })

        // Bugard Skills
        MobSkills[mskillTailRoll_126] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillTailRoll_126] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        MobSkills[mskillTusk_127] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillTusk_127] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 3)) { 2.5f })

        MobSkills[mskillScutum_128] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillScutum_128] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.DefenseBoost, duration = 30.seconds) { it.status.potency = 50 })
        SkillHeuristics[mskillScutum_128] = avoidOverwritingBuff(StatusEffect.DefenseBoost)

        MobSkills[mskillBoneCrunch_129] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillBoneCrunch_129] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            attackStatusEffects = listOf(AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 15.seconds) { it.statusState.potency = 10 })
        )) { 2f })

        MobSkills[mskillAwfulEye_130] = MobSkill(rangeInfo = extendedSourceRange)
        SkillAppliers[mskillAwfulEye_130] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true,
            attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.AttackDown, baseDuration = 15.seconds) { it.statusState.potency = 50 })
        )))
        SkillHeuristics[mskillAwfulEye_130] = avoidOverwritingDebuff(StatusEffect.AttackDown)

        MobSkills[mskillHeavyBellow_131] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillHeavyBellow_131] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = singleStatus(AttackStatusEffect(StatusEffect.Stun, baseDuration = 8.seconds))
        ))
        SkillHeuristics[mskillHeavyBellow_131] = avoidOverwritingDebuff(StatusEffect.Stun)

        // Yztarg Skills
        MobSkills[mskillYztargAutoAttack_2663] = MobSkill(castTime = ZERO, rangeInfo = standardConeRange, cost = zeroCost)
        SkillAppliers[mskillYztargAutoAttack_2663] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 1)))

        MobSkills[mskillYztargAutoAttack_2664] = MobSkill(castTime = ZERO, rangeInfo = standardConeRange, cost = zeroCost)
        SkillAppliers[mskillYztargAutoAttack_2664] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        MobSkills[mskillYztargAutoAttack_2665] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskillYztargAutoAttack_2665] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true, attackEffects = AttackEffects(knockBackMagnitude = 1)) { 1f/3f })

        MobSkills[mskillSoulshatteringRoar_2666] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillSoulshatteringRoar_2666] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Terror, baseDuration = 8.seconds),
        ))) { 2f })

        MobSkills[mskillCalcifyingClaw_2667] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillCalcifyingClaw_2667] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(knockBackMagnitude = 3, attackStatusEffect = AttackStatusEffect(StatusEffect.Petrify, baseDuration = 6.seconds))
        ) { 2.3f })

        MobSkills[mskillDivestingStampede_2668] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillDivestingStampede_2668] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            knockBackMagnitude = 4,
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 15.seconds) { it.statusState.potency = 33 },
                AttackStatusEffect(statusEffect = StatusEffect.MagicDefDown, baseDuration = 15.seconds) { it.statusState.potency = 33 }
            ),
        )) { 2f })

        MobSkills[mskillBonebreakingBarrage_2669] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillBonebreakingBarrage_2669] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 50 }),
        ) { 2.5f })

        MobSkills[mskillBeastruction_2670] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillBeastruction_2670] = SkillApplier(targetEvaluator = currentHpDamage(
            percent = 0.99f,
            damageType = AttackDamageType.Physical,
            attackEffects = AttackEffects(
                knockBackMagnitude = 6,
                attackStatusEffects = listOf(
                    AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 15.seconds),
                    AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 50 }
                )),
        ))

        MobSkills[mskillHellfireArrow_3250] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillHellfireArrow_3250] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(
            knockBackMagnitude = 4,
            attackStatusEffects = listOf(spellDamageDoT(StatusEffect.Burn, duration = 15.seconds, potency = 0.2f, secondaryPotency = 0.8f)),
        )) { 3f })

        MobSkills[mskillIncensedPummel_3251] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillIncensedPummel_3251] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            numHits = 5,
            ftpSpread = true,
            attackEffects = AttackEffects(attackStatusEffects = allStatsDown(duration = 9.seconds, potency = 0.8f)),
        ) { 0.6f })

        // Pteraketos Skills
        MobSkills[mskillPteraketosAutoAttack_2606] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskillPteraketosAutoAttack_2606] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        MobSkills[mskillPteraketosAutoAttack_2607] = MobSkill(castTime = ZERO, rangeInfo = standardConeRange, cost = zeroCost)
        SkillAppliers[mskillPteraketosAutoAttack_2607] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 2)))

        MobSkills[mskillPteraketosAutoAttack_2608] = MobSkill(castTime = ZERO, rangeInfo = standardConeRange, cost = zeroCost)
        SkillAppliers[mskillPteraketosAutoAttack_2608] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        MobSkills[mskillPteraketosAutoAttack_2609] = MobSkill(castTime = ZERO, rangeInfo = standardStaticTargetAoe, cost = zeroCost)
        SkillAppliers[mskillPteraketosAutoAttack_2609] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 2)))

        MobSkills[mskillPteraketosAutoAttack_2610] = MobSkill(castTime = ZERO, rangeInfo = standardStaticTargetAoe, cost = zeroCost)
        SkillAppliers[mskillPteraketosAutoAttack_2610] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 2)))

        MobSkills[mskillPteraketosAutoAttack_2611] = MobSkill(castTime = ZERO, rangeInfo = reverseConeRange, cost = zeroCost)
        SkillAppliers[mskillPteraketosAutoAttack_2611] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = AttackEffects(knockBackMagnitude = 3)))

        MobSkills[mskillEcholocation_2612] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillEcholocation_2612] = SkillApplier(targetEvaluator = basicMagicalWs (attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Silence, baseDuration = 15.seconds)
        )) { 2f })
        SkillHeuristics[mskillEcholocation_2612] = onlyIfFacingTarget()

        MobSkills[mskillDeepSeaDirge_2613] = MobSkill(castTime = 2.5.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillDeepSeaDirge_2613] = SkillApplier(targetEvaluator = basicPhysicalDamage (attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Amnesia, baseDuration = 15.seconds)
        )) { 2.5f })

        MobSkills[mskillCaudalCapacitor_2614] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillCaudalCapacitor_2614] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 5.seconds))
        ) { 2f })

        MobSkills[mskillBaleenGurge_2615] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillBaleenGurge_2615] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(absorptionEffect = AttackAbsorptionEffect(absorption = 0.25f, proportionalAbsorption = true))
        ) { 2f })

        MobSkills[mskillDepthCharge_2616] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillDepthCharge_2616] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            knockBackMagnitude = 6,
            attackStatusEffects = listOf(
                spellDamageDoT(statusEffect = StatusEffect.Shock, potency = 0.5f, duration = 15.seconds),
                AttackStatusEffect(StatusEffect.Stun, baseDuration = 6.seconds),
            )
        )) { 2.2f })
        SkillHeuristics[mskillDepthCharge_2616] = onlyIfFacingTarget()

        MobSkills[mskillBlowholeBlast_2617] = MobSkill(castTime = 2.5.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillBlowholeBlast_2617] = SkillApplier(targetEvaluator = basicPhysicalDamage(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 50 }
        )) { 2.5f })

        MobSkills[mskillWaterspout_2618] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillWaterspout_2618] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            knockBackMagnitude = 6,
            attackStatusEffects = allStatsDown(duration = 9.seconds, potency = 0.8f)
        )) { 2.2f })
        SkillHeuristics[mskillWaterspout_2618] = onlyIfFacingTarget()

        MobSkills[mskillAngrySeas_2619] = MobSkill(castTime = 2.5.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillAngrySeas_2619] = SkillApplier(targetEvaluator = basicPhysicalDamage (attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 50 }
        )) { 2.5f })

        MobSkills[mskillTharSheBlows_2620] = MobSkill(castTime = 3.seconds, rangeInfo = standardConeRange)
        SkillAppliers[mskillTharSheBlows_2620] = SkillApplier(targetEvaluator = basicMagicalWs { 5f })
        SkillHeuristics[mskillTharSheBlows_2620] = onlyIfFacingTarget()

        MobSkills[mskillAethericPull_3308] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillAethericPull_3308] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(absorptionEffect = AttackAbsorptionEffect(absorption = 1f, absorptionResource = ActorResourceType.MP, absorptionCap = 50))
        ) { 2f })

        // Mantid Skills
        MobSkills[mskillMantidAutoAttack_2492] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskillMantidAutoAttack_2492] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        MobSkills[mskillMantidAutoAttack_2493] = MobSkill(castTime = ZERO, rangeInfo = standardConeRange, cost = zeroCost)
        SkillAppliers[mskillMantidAutoAttack_2493] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        MobSkills[mskillMantidAutoAttack_2494] = MobSkill(castTime = ZERO, rangeInfo = standardStaticTargetAoe, cost = zeroCost)
        SkillAppliers[mskillMantidAutoAttack_2494] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        MobSkills[mskillSlicingSickle_2495] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillSlicingSickle_2495] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(knockBackMagnitude = 4, attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 15.seconds) { it.statusState.potency = 50 })
        ) { 2f })

        MobSkills[mskillRaptorialClaw_2496] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillRaptorialClaw_2496] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(knockBackMagnitude = 4, attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.MagicDefDown, baseDuration = 15.seconds) { it.statusState.potency = 50 })
        ) { 2f })

        MobSkills[mskillPhlegmExpulsion_2497] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillPhlegmExpulsion_2497] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 25 },
            AttackStatusEffect(statusEffect = StatusEffect.Amnesia, baseDuration = 15.seconds),
        ))))

        MobSkills[mskillMaceratingBile_2498] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillMaceratingBile_2498] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            attackStatusEffects = allStatsDown(duration = 9.seconds, potency = 0.8f) + spellDamageDoT(StatusEffect.Bio, duration = 15.seconds, potency = 0.5f, secondaryPotency = 0.8f)
        )))

        MobSkills[mskillPreyingPosture_2499] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPreyingPosture_2499] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Warcry, duration = 30.seconds) { it.status.potency = 50 })
        SkillHeuristics[mskillPreyingPosture_2499] = avoidOverwritingBuff(StatusEffect.Warcry)

        MobSkills[mskillDeathProphet_2500] = MobSkill(rangeInfo = standardConeRange, castTime = 3.seconds)
        SkillAppliers[mskillDeathProphet_2500] = SkillApplier(targetEvaluator = basicPhysicalDamage { 5f })

        MobSkills[mskillImmolatingClaw_2553] = MobSkill(rangeInfo = standardConeRange)
        SkillAppliers[mskillImmolatingClaw_2553] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Plague, baseDuration = 15.seconds) { it.statusState.potency = 5 },
                spellDamageDoT(StatusEffect.Burn, duration = 15.seconds, potency = 0.2f, secondaryPotency = 0.8f),
            )),
        ) { 2f })

        MobSkills[mskillExorender_2630] = MobSkill(rangeInfo = extendedSourceRange, castTime = 3.seconds)
        SkillAppliers[mskillExorender_2630] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        // Gallu Skills
        MobSkills[mskillGalluAutoAttack_2525] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, cost = zeroCost)
        SkillAppliers[mskillGalluAutoAttack_2525] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 1f/3f })

        MobSkills[mskillGalluAutoAttack_2526] = MobSkill(castTime = ZERO, rangeInfo = standardConeRange, cost = zeroCost)
        SkillAppliers[mskillGalluAutoAttack_2526] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        MobSkills[mskillGalluAutoAttack_2527] = MobSkill(castTime = ZERO, rangeInfo = standardSourceRange, cost = zeroCost)
        SkillAppliers[mskillGalluAutoAttack_2527] = SkillApplier(targetEvaluator = basicPhysicalDamage())

        MobSkills[mskillDiluvialWake_2528] = MobSkill(rangeInfo = standardConeRange, element = SpellElement.Water)
        SkillAppliers[mskillDiluvialWake_2528] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 15.seconds) { it.statusState.potency = 50 },
                AttackStatusEffect(statusEffect = StatusEffect.MagicDefDown, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            )
        )) { 2.25f })

        MobSkills[mskillKurnugiCollapse_2529] = MobSkill(rangeInfo = standardSourceRange, element = SpellElement.Earth)
        SkillAppliers[mskillKurnugiCollapse_2529] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            )
        )) { 2.25f })

        MobSkills[mskillSearingHalitus_2530] = MobSkill(rangeInfo = standardSourceRange, element = SpellElement.Fire)
        SkillAppliers[mskillSearingHalitus_2530] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            attackStatusEffects = listOf(
                spellDamageDoT(StatusEffect.Burn, duration = 15.seconds, potency = 0.2f, secondaryPotency = 0.8f),
            )
        )) { 2.25f })

        MobSkills[mskillDivestingGale_2531] = MobSkill(rangeInfo = standardSourceRange, element = SpellElement.Wind)
        SkillAppliers[mskillDivestingGale_2531] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            knockBackMagnitude = 6,
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 50 },
            )
        )) { 2.25f })

        MobSkills[mskillBoltofPerdition_2532] = MobSkill(rangeInfo = standardConeRange, element = SpellElement.Lightning)
        SkillAppliers[mskillBoltofPerdition_2532] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            knockBackMagnitude = 4,
            attackStatusEffects = listOf(
                AttackStatusEffect(statusEffect = StatusEffect.Amnesia, baseDuration = 15.seconds),
            )
        )) { 2.25f })

        MobSkills[mskillCripplingRime_2533] = MobSkill(rangeInfo = standardConeRange, element = SpellElement.Ice)
        SkillAppliers[mskillCripplingRime_2533] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            attackStatusEffects = allStatsDown(duration = 9.seconds, potency = 0.8f)
        )) { 2.25f })

        MobSkills[mskillOblivionsMantle_2534] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillOblivionsMantle_2534] = SkillApplier(targetEvaluator = noop())

        MobSkills[mskillUnrelentingBrand_2676] = MobSkill(castTime = 3.seconds, rangeInfo = extendedSourceRange)
        SkillAppliers[mskillUnrelentingBrand_2676] = SkillApplier(targetEvaluator = noop())

        // Elemental Skills
        MobSkills[mskillSearingTempest_2479] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSearingTempest_2479] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Burn, duration = 15.seconds, potency = 0.2f, secondaryPotency = 0.8f))
        ) { 2.5f })

        MobSkills[mskillBlindingFulgor_2480] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillBlindingFulgor_2480] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Flash, baseDuration = 15.seconds) { it.statusState.potency = 95 })
        ) { 2.5f })

        MobSkills[mskillSpectralFloe_2481] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSpectralFloe_2481] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Terror, baseDuration = 9.seconds))
        ) { 2.5f })

        MobSkills[mskillScouringSpate_2482] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillScouringSpate_2482] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.AttackDown, baseDuration = 15.seconds) { it.statusState.potency = 50 })
        ) { 2.5f })

        MobSkills[mskillAnvilLightning_2483] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillAnvilLightning_2483] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 6.seconds))
        ) { 2.5f })

        MobSkills[mskillSilentStorm_2484] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillSilentStorm_2484] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Silence, baseDuration = 9.seconds))
        ) { 2.5f })

        MobSkills[mskillEntomb_2485] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillEntomb_2485] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Petrify, baseDuration = 9.seconds))
        ) { 2.5f })

        MobSkills[mskillTenebralCrush_2486] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillTenebralCrush_2486] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.DefenseDown, baseDuration = 15.seconds) { it.statusState.potency = 30 })
        ) { 2.5f })

        // Trust Selh'teus: Luminous Lance
        MobSkills[mskillLuminousLance_3365] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillLuminousLance_3365] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 4f + 2f * it.excessTp }
        )

        // Trust Selh'teus: Rejuvenation
        MobSkills[mskillRejuvenation_3366] = MobSkill(rangeInfo = SkillRangeInfo(50f, 50f, AoeType.Source), castTime = ZERO)
        SkillAppliers[mskillRejuvenation_3366] = SkillApplier(targetEvaluator = sourcePercentageHealingMagic(50f))

        // Trust Selh'teus: Revelation
        MobSkills[mskillRevelation_3367] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRevelation_3367] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 3f + it.excessTp }
        )

        // Misc. Skills
        MobSkills[mskillFrizz_2871] = MobSkill(castTime = ZERO, rangeInfo = standardSingleTargetRange, element = SpellElement.Fire, canMagicBurst = true)
        SkillAppliers[mskillFrizz_2871] = SkillApplier(targetEvaluator = noop())

        avatarSkills()
    }

    private fun avatarSkills() {
        // Ifrit
        MobSkills[mskillPunch_584] = MobSkill(castTime = 1.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPunch_584] = SkillApplier(targetEvaluator = basicPhysicalDamage { 1.75f })

        MobSkills[mskillBurningStrike_586] = MobSkill(castTime = 1.5.seconds, rangeInfo = standardConeRange, broadcastIds = listOf(DatId("hit0")))
        SkillAppliers[mskillBurningStrike_586] = SkillApplier(targetEvaluator = basicPhysicalDamage { 1.6f })

        MobSkills[mskillDoublePunch_587] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillDoublePunch_587] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 2))

        MobSkills[mskillCrimsonRoar_588] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillCrimsonRoar_588] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Warcry, duration = 30.seconds) { it.status.potency = 33 })
        SkillHeuristics[mskillCrimsonRoar_588] = avoidOverwritingBuff(StatusEffect.Warcry)

        MobSkills[mskillFlamingCrush_590] = MobSkill(rangeInfo = standardConeRange, lockTime = 3.5.seconds, broadcastIds = listOf(
            DatId("hit0"),
            DatId("hit1"),
            DatId("hit2"),
        ))
        SkillAppliers[mskillFlamingCrush_590] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 0.8f },
        )

        MobSkills[mskillMeteorStrike_591] = MobSkill(rangeInfo = standardStaticTargetAoe, lockTime = 3.5.seconds, broadcastIds = listOf(DatId("hit0")))
        SkillAppliers[mskillMeteorStrike_591] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })

        MobSkills[mskillInferno_592] = MobSkill(castTime = 4.seconds, rangeInfo = maxSourceRange, bypassParalysis = true)
        SkillAppliers[mskillInferno_592] = SkillApplier(targetEvaluator = basicMagicalWs { 3f })

        // Titan
        MobSkills[mskillRockThrow_593] = MobSkill(castTime = 1.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRockThrow_593] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 15.seconds) { it.statusState.potency = 33 }),
        ) { 1.25f })

        MobSkills[mskillRockBuster_595] = MobSkill(rangeInfo = standardConeRange, broadcastIds = listOf(DatId("hit0")))
        SkillAppliers[mskillRockBuster_595] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 15.seconds)),
        ) { 2.5f })

        MobSkills[mskillMegalithThrow_596] = MobSkill(rangeInfo = standardStaticTargetAoe, lockTime = 5.seconds, broadcastIds = listOf(DatId("trg0")))
        SkillAppliers[mskillMegalithThrow_596] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Slow, baseDuration = 15.seconds) { it.statusState.potency = 66 }),
        ) { 2.5f })

        MobSkills[mskillEarthenWard_597] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillEarthenWard_597]  = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Stoneskin, 5.minutes) {
            it.status.counter = it.context.sourceState.getMaxHp() / 4
        })
        SkillHeuristics[mskillEarthenWard_597] = avoidOverwritingBuff(StatusEffect.Stoneskin)

        MobSkills[mskillMountainBuster_599] = MobSkill(castTime = 3.seconds, rangeInfo = standardConeRange, broadcastIds = listOf(DatId("hit0")))
        SkillAppliers[mskillMountainBuster_599] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.75f })

        MobSkills[mskillGeocrush_600] = MobSkill(castTime = 2.seconds, rangeInfo = standardSourceRange, broadcastIds = listOf(DatId("trg0")))
        SkillAppliers[mskillGeocrush_600] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.5f })

        MobSkills[mskillEarthenFury_601] = MobSkill(castTime = 4.seconds, rangeInfo = maxSourceRange, bypassParalysis = true)
        SkillAppliers[mskillEarthenFury_601] = SkillApplier(targetEvaluator = basicMagicalWs { 3f })

        // Leviathan
        MobSkills[mskillBarracudaDive_602] = MobSkill(rangeInfo = standardConeRange, broadcastIds = listOf(DatId("tgt0")))
        SkillAppliers[mskillBarracudaDive_602] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.25f })

        MobSkills[mskillTailWhip_604] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillTailWhip_604] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 50 }),
        ) { 2.5f })

        MobSkills[mskillSpringWater_605] = MobSkill(lockTime = 4.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillSpringWater_605] = SkillApplier(targetEvaluator = compose(
            sourcePercentageHealingMagic(20f),
            expireTargetDebuffs(maxToExpire = 4),
        ))
        SkillHeuristics[mskillSpringWater_605] = canEraseDebuffs(baseScore = 0.0)

        MobSkills[mskillSpinningDive_608] = MobSkill(castTime = 3.seconds, rangeInfo = standardConeRange, broadcastIds = listOf(DatId("tgt0")))
        SkillAppliers[mskillSpinningDive_608] = SkillApplier(targetEvaluator = basicPhysicalDamage { 2.75f })

        MobSkills[mskillGrandFall_609] = MobSkill(castTime = 3.seconds, lockTime = 3.seconds, rangeInfo = extendedSourceRange, broadcastIds = listOf(DatId("tgt0")))
        SkillAppliers[mskillGrandFall_609] = SkillApplier(targetEvaluator = basicMagicalWs { 2.75f })

        MobSkills[mskillTidalWave_610] = MobSkill(castTime = 4.seconds, rangeInfo = maxSourceRange, bypassParalysis = true)
        SkillAppliers[mskillTidalWave_610] = SkillApplier(targetEvaluator = basicMagicalWs { 3f })

        // Garuda
        MobSkills[mskillClaw_611] = MobSkill(castTime = 1.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillClaw_611] = SkillApplier(targetEvaluator = basicPhysicalDamage { 1.75f })

        MobSkills[mskillWhisperingWind_613] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillWhisperingWind_613] = SkillApplier(targetEvaluator = sourcePercentageHealingMagic(20f))
        SkillHeuristics[mskillWhisperingWind_613] = onlyIfBelowHppThreshold(0.25)

        MobSkills[mskillAerialArmor_615] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillAerialArmor_615] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Blink, duration = 3.minutes) { it.status.counter = 5 })
        SkillHeuristics[mskillAerialArmor_615] = avoidOverwritingBuff(StatusEffect.Blink)

        MobSkills[mskillPredatorClaws_617] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPredatorClaws_617] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 0.8f })

        MobSkills[mskillWindBlade_618] = MobSkill(castTime = 1.5.seconds, rangeInfo = standardStaticTargetAoe, broadcastIds = listOf(DatId("tgt0")))
        SkillAppliers[mskillWindBlade_618] = SkillApplier(targetEvaluator = basicMagicalWs { 2f })

        MobSkills[mskillAerialBlast_619] = MobSkill(castTime = 4.seconds, rangeInfo = maxSourceRange, bypassParalysis = true)
        SkillAppliers[mskillAerialBlast_619] = SkillApplier(targetEvaluator = basicMagicalWs { 3f })

        // Shiva
        MobSkills[mskillAxeKick_620] = MobSkill(castTime = 1.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillAxeKick_620] = SkillApplier(targetEvaluator = basicPhysicalDamage { 1.75f })

        MobSkills[mskillFrostArmor_622] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillFrostArmor_622] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.IceSpikes, duration = 30.seconds) {
            it.status.potency = intPotency(it.context.sourceState, 0.10f)
            it.status.secondaryPotency = 1f
        })
        SkillHeuristics[mskillFrostArmor_622] = avoidOverwritingBuff(StatusEffect.IceSpikes)

        MobSkills[mskillDoubleSlap_624] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillDoubleSlap_624] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 2))

        MobSkills[mskillRush_626] = MobSkill(rangeInfo = standardConeRange, lockTime = 3.5.seconds, broadcastIds = listOf(DatId("hit0")))
        SkillAppliers[mskillRush_626] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 0.8f })

        MobSkills[mskillHeavenlyStrike_627] = MobSkill(rangeInfo = standardStaticTargetAoe, broadcastIds = listOf(DatId("hit1")))
        SkillAppliers[mskillHeavenlyStrike_627] = SkillApplier(targetEvaluator = basicMagicalWs { 2.3f })

        MobSkills[mskillDiamondDust_628] = MobSkill(castTime = 4.seconds, rangeInfo = maxSourceRange, bypassParalysis = true)
        SkillAppliers[mskillDiamondDust_628] = SkillApplier(targetEvaluator = basicMagicalWs { 3f })

        // Ramuh
        MobSkills[mskillShockStrike_629] = MobSkill(castTime = 1.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillShockStrike_629] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 3.seconds)),
        ) { 1.25f })

        MobSkills[mskillRollingThunder_631] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillRollingThunder_631] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Enthunder, duration = 30.seconds) {
            it.status.potency = intPotency(it.context.sourceState, 0.2f)
        })
        SkillHeuristics[mskillRollingThunder_631] = avoidOverwritingBuff(StatusEffect.Enthunder)

        MobSkills[mskillThunderspark_632] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillThunderspark_632] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 5.seconds)),
        ) { 2f })

        MobSkills[mskillLightningArmor_633] = MobSkill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillLightningArmor_633] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.ShockSpikes, duration = 30.seconds) {
            it.status.potency = intPotency(it.context.sourceState, 0.10f)
            it.status.secondaryPotency = 1f
        })
        SkillHeuristics[mskillLightningArmor_633] = avoidOverwritingBuff(StatusEffect.ShockSpikes)

        MobSkills[mskillChaoticStrike_635] = MobSkill(rangeInfo = standardConeRange, lockTime = 3.5.seconds, broadcastIds = listOf(DatId("hit0")))
        SkillAppliers[mskillChaoticStrike_635] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 0.8f })

        MobSkills[mskillThunderstorm_636] = MobSkill(castTime = 3.seconds ,rangeInfo = extendedStaticTargetAoe)
        SkillAppliers[mskillThunderstorm_636] = SkillApplier(targetEvaluator = basicMagicalWs(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 5.seconds)),
        ) { 2.3f })

        MobSkills[mskillJudgmentBolt_637] = MobSkill(castTime = 4.seconds, rangeInfo = maxSourceRange, bypassParalysis = true)
        SkillAppliers[mskillJudgmentBolt_637] = SkillApplier(targetEvaluator = basicMagicalWs { 3f })

        // Carbuncle
        MobSkills[mskillPoisonNails_651] = MobSkill(castTime = 1.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillPoisonNails_651] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds))
        ) { 1.5f })

        MobSkills[mskillShiningRuby_652] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillShiningRuby_652] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.ShiningRuby, duration = 18.seconds) { it.status.potency = 50 }
        )
        SkillHeuristics[mskillShiningRuby_652] = avoidOverwritingBuff(StatusEffect.ShiningRuby)

        MobSkills[mskillGlitteringRuby_653] = MobSkill(rangeInfo = standardSourceRange)
        SkillAppliers[mskillGlitteringRuby_653] = SkillApplier(targetEvaluator = compose(
            applyBasicBuff(StatusEffect.MagicAtkBoost, duration = 30.seconds) { it.status.potency = 50 },
        ))
        SkillHeuristics[mskillGlitteringRuby_653] = avoidOverwritingBuff(StatusEffect.MagicAtkBoost)

        MobSkills[mskillMeteorite_654] = MobSkill(castTime = 1.5.seconds, rangeInfo = smallStaticTargetAoe)
        SkillAppliers[mskillMeteorite_654] = SkillApplier(targetEvaluator = basicMagicalWs { 2.2f })

        MobSkills[mskillHealingRubyII_655] = MobSkill(castTime = 3.seconds, rangeInfo = standardSourceRange)
        SkillAppliers[mskillHealingRubyII_655] = SkillApplier(targetEvaluator = sourcePercentageHealingMagic(20f))
        SkillHeuristics[mskillHealingRubyII_655] = onlyIfBelowHppThreshold(0.25)

        MobSkills[mskillSearingLight_656] = MobSkill(castTime = 4.seconds, rangeInfo = maxSourceRange)
        SkillAppliers[mskillSearingLight_656] = SkillApplier(targetEvaluator = basicMagicalWs { 3.5f })

        MobSkills[mskillHolyMist_3297] = MobSkill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[mskillHolyMist_3297] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })

        // Fenrir
        MobSkills[mskillMoonlitCharge_575] = MobSkill(rangeInfo = standardConeRange, lockTime = 3.seconds, broadcastIds = listOf(DatId("tgt0")))
        SkillAppliers[mskillMoonlitCharge_575] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Blind, baseDuration = 15.seconds) { it.statusState.potency = 50 }),
        ) { 2.2f })

        MobSkills[mskillCrescentFang_576] = MobSkill(castTime = 1.5.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillCrescentFang_576] = SkillApplier(targetEvaluator = basicPhysicalDamage(
            attackEffects = singleStatus(AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 30 }),
        ) { 1.5f })

        MobSkills[mskillLunarCry_577] = MobSkill(rangeInfo = extendedSourceRange)
        SkillAppliers[mskillLunarCry_577] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOf(
            AttackStatusEffect(StatusEffect.Terror, baseDuration = 6.seconds),
            AttackStatusEffect(StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 50 },
        ))))

        MobSkills[mskillEclipticGrowl_578] = MobSkill(rangeInfo = extendedSourceRange)
        SkillAppliers[mskillEclipticGrowl_578] = SkillApplier(targetEvaluator = applyBasicBuff(allStatsUp(duration = 15.seconds, potency = 1.33f)))
        SkillHeuristics[mskillEclipticGrowl_578] = avoidOverwritingBuff(StatusEffect.StrBoost)

        MobSkills[mskillLunarRoar_579] = MobSkill(rangeInfo = extendedSourceRange)
        SkillAppliers[mskillLunarRoar_579] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(dispelCount = 2)))
        SkillHeuristics[mskillLunarRoar_579] = canDispelBuffs(baseScore = 0.0) { 0.5 * it.coerceAtMost(1) }

        MobSkills[mskillEclipseBite_580] = MobSkill(castTime = 3.seconds, rangeInfo = standardSingleTargetRange)
        SkillAppliers[mskillEclipseBite_580] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 0.8f })

        MobSkills[mskillEclipticHowl_581] = MobSkill(rangeInfo = extendedSourceRange)
        SkillAppliers[mskillEclipticHowl_581] = SkillApplier(targetEvaluator = applyBasicBuff(listOf(
            AttackStatusEffect(StatusEffect.Haste, baseDuration = 30.seconds) { it.statusState.potency = 30 },
            AttackStatusEffect(StatusEffect.EvasionBoost, baseDuration = 30.seconds) { it.statusState.potency = 50 },
        )))
        SkillHeuristics[mskillEclipticHowl_581] = avoidOverwritingBuff(StatusEffect.EvasionBoost)

        MobSkills[mskillHowlingMoon_582] = MobSkill(castTime = 4.seconds, rangeInfo = maxSourceRange)
        SkillAppliers[mskillHowlingMoon_582] = SkillApplier(targetEvaluator = basicMagicalWs { 3f })

        MobSkills[mskillLunarBay_3295] = MobSkill(castTime = 2.5.seconds, lockTime = 3.5.seconds, rangeInfo = extendedStaticTargetAoe)
        SkillAppliers[mskillLunarBay_3295] = SkillApplier(targetEvaluator = basicMagicalWs { 2.5f })

        MobSkills[mskillImpact_3296] = MobSkill(castTime = 2.5.seconds, lockTime = 3.5.seconds, rangeInfo = extendedStaticTargetAoe)
        SkillAppliers[mskillImpact_3296] = SkillApplier(targetEvaluator = basicMagicalWs(attackEffects = AttackEffects(
            attackStatusEffects = allStatsDown(duration = 9.seconds, potency = 0.8f)
        )) { 2f })

    }

    fun basicPhysicalDamage(numHits: Int,
                            ftpSpread: Boolean = false,
                            attackEffects: AttackEffects = AttackEffects(),
                            ftp: TpScalingFn = TpScalingFn { 1f },
    ): SkillApplierHelper.TargetEvaluator {
        return basicPhysicalDamage(numHits = { numHits }, ftpSpread = ftpSpread, attackEffects = attackEffects, ftp = ftp)
    }

    fun basicPhysicalDamage(numHits: (SkillApplierHelper.TargetEvaluatorContext) -> Int = { 1 },
                            ftpSpread: Boolean = false,
                            attackEffects: AttackEffects = AttackEffects(),
                            ftp: TpScalingFn = TpScalingFn { 1f },
    ): SkillApplierHelper.TargetEvaluator {
         return SkillApplierHelper.TargetEvaluator {
            val wsResult = WeaponSkillDamageCalculator.physicalWeaponSkill(
                skill = it.skill,
                attacker = it.sourceState,
                defender = it.targetState,
                numHits = numHits.invoke(it),
                ftpSpread = ftpSpread,
                context = it.context,
                ftp = ftp,
            )

            weaponSkillToEvents(
                context = it,
                wsResult = wsResult,
                damageType = AttackDamageType.Physical,
                attackEffects = attackEffects,
            )
        }
    }

    fun setTp(amount: Int = 0): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            it.targetState.setTp(0)
            emptyList()
        }
    }

    fun basicDebuff(statusEffect: StatusEffect, duration: Duration, decorator: (AttackStatusEffectContext) -> Unit = {}): SkillApplierHelper.TargetEvaluator {
        return basicDebuff(attackStatusEffect = AttackStatusEffect(statusEffect, duration, decorator = decorator))
    }

    fun basicDebuff(attackStatusEffect: AttackStatusEffect? = null, dispelCount: Int = 0): SkillApplierHelper.TargetEvaluator {
        return basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOfNotNull(attackStatusEffect), dispelCount = dispelCount))
    }

    fun basicDebuff(attackEffects: AttackEffects): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            listOf(ActorAttackedEvent(
                sourceId = it.sourceState.id,
                targetId = it.targetState.id,
                damageAmount = listOf(0),
                damageType = AttackDamageType.StatusOnly,
                attackEffects = attackEffects,
                skill = it.skill,
                actionContext = it.context,
            ))
        }
    }

    fun basicMagicalWs(attackStat: CombatStat = CombatStat.int,
                       defendStat: CombatStat = attackStat,
                       attackEffects: AttackEffects = AttackEffects(),
                       damageCap: Int? = null,
                       ftp: TpScalingFn = TpScalingFn { 1f },
    ): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            val wsResult = WeaponSkillDamageCalculator.magicalWeaponSkill(
                skill = it.skill,
                attacker = it.sourceState,
                defender = it.targetState,
                attackStat = attackStat,
                defendStat = defendStat,
                damageCap = damageCap,
                ftp = ftp,
            )

            weaponSkillToEvents(
                context = it,
                wsResult = wsResult,
                damageType = AttackDamageType.Magical,
                attackEffects = attackEffects,
            )
        }
    }

    fun defeatSelf(): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            listOf(ActorDamagedEvent(
                sourceId = it.sourceState.id,
                targetId = it.sourceState.id,
                amount = it.sourceState.getMaxHp(),
                actionContext = it.context,
                skill = it.skill,
                damageType = AttackDamageType.Static,
                emitDamageText = false,
            ))
        }
    }

    fun sourceCurrentHpDamage(percent: Float, damageType: AttackDamageType, attackEffects: AttackEffects = AttackEffects()): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            val damage = (it.sourceState.getHp() * percent).roundToInt()

            listOf(ActorAttackedEvent(
                sourceId = it.sourceState.id,
                targetId = it.targetState.id,
                damageAmount = listOf(damage),
                damageType = damageType,
                actionContext = it.context,
                skill = it.skill,
                attackEffects = attackEffects,
            ))
        }
    }

    fun sourceCurrentHppPower(zeroHpPower: Float, maxHpPower: Float): TpScalingFn {
        return TpScalingFn { zeroHpPower.interpolate(maxHpPower, it.attacker.getHpp()) }
    }

    fun maxHpDamage(percent: Float, damageType: AttackDamageType, attackEffects: AttackEffects = AttackEffects()): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            val damage = (it.targetState.getMaxHp() * percent).roundToInt()

            listOf(ActorAttackedEvent(
                sourceId = it.sourceState.id,
                targetId = it.targetState.id,
                damageAmount = listOf(damage),
                damageType = damageType,
                actionContext = it.context,
                skill = it.skill,
                attackEffects = attackEffects,
            ))
        }
    }

    fun currentHpDamage(percent: Float, damageType: AttackDamageType, attackEffects: AttackEffects = AttackEffects()): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            val damage = (it.targetState.getHp() * percent).roundToInt()

            listOf(ActorAttackedEvent(
                sourceId = it.sourceState.id,
                targetId = it.targetState.id,
                damageAmount = listOf(damage),
                damageType = damageType,
                actionContext = it.context,
                skill = it.skill,
                attackEffects = attackEffects,
            ))
        }
    }

    private fun marineMayhem(): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            val distance = it.sourceState.getTargetingDistance(it.targetState)
            if (distance > 8f) {
                listOf(ActorDamagedEvent(
                    sourceId = it.sourceState.id,
                    targetId = it.targetState.id,
                    amount = 99999,
                    actionContext = it.context,
                    skill = it.skill,
                    damageType = AttackDamageType.Static,
                ))
            } else {
                basicMagicalWs { 3f }.getEvents(it)
            }
        }
    }

    fun staticDamage(amount: Int): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator { staticDamage(amount, it) }
    }

    fun staticDamage(amount: Int, context: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        return listOf(ActorDamagedEvent(
            sourceId = context.sourceState.id,
            targetId = context.targetState.id,
            amount = amount,
            actionContext = context.context,
            skill = context.skill,
            damageType = AttackDamageType.Static,
        ))
    }

    fun copyDebuffsToTarget(): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            val debuffs = it.sourceState.getStatusEffects().filter { se -> se.statusEffect.debuff }
            for (debuff in debuffs) {
                val copyEffect = it.targetState.gainStatusEffect(debuff.statusEffect, sourceId = it.sourceState.id)
                copyEffect.copyFrom(debuff)
            }
            emptyList()
        }
    }

    fun expireTargetDebuffs(maxToExpire: Int): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator { expireDebuffs(maxToExpire, it.targetState) }
    }

    fun expireSourceDebuffs(maxToExpire: Int): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator { expireDebuffs(maxToExpire, it.sourceState) }
    }

    private fun expireDebuffs(maxToExpire: Int, actorState: ActorState): List<Event> {
        actorState.getStatusEffects()
            .filter { it.statusEffect.debuff && (it.canErase || it.canEsuna) }
            .take(maxToExpire)
            .forEach { actorState.expireStatusEffect(it.statusEffect) }

        return emptyList()
    }

    fun singleStatus(attackStatusEffect: AttackStatusEffect, knockBackMagnitude: Int = 0): AttackEffects {
        return AttackEffects(knockBackMagnitude = knockBackMagnitude, attackStatusEffects = listOf(attackStatusEffect))
    }

    fun intPotency(attacker: ActorState, scale: Float): Int {
        return ceil(attacker.combatStats[CombatStat.int] * scale).roundToInt()
    }

    fun spellDamageDoT(statusEffect: StatusEffect, potency: Float, duration: Duration, attackStat: CombatStat = CombatStat.int, defendStat: CombatStat = attackStat, secondaryPotency: Float = 0f): AttackStatusEffect {
        return AttackStatusEffect(statusEffect = statusEffect, baseDuration = duration) {
            val spellResult = SpellDamageCalculator.computeDamage(
                skill = null,
                attacker = it.sourceState,
                defender = it.targetState,
                attackStat = attackStat,
                defendStat = defendStat,
            ) { potency }

            it.statusState.potency = spellResult.damage.firstOrNull() ?: 0
            it.statusState.secondaryPotency = secondaryPotency
        }
    }

    fun sourceAppearanceStateChange(destinationState: Int): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            it.sourceState.appearanceState = destinationState
            emptyList()
        }
    }

    fun exuviation(): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            val removableStatusEffects = it.sourceState.getStatusEffects().filter { se -> se.canErase || se.canEsuna }
            removableStatusEffects.forEach { se -> it.sourceState.expireStatusEffect(se.statusEffect) }
            val healAmount = (it.sourceState.getMaxHp() * (0.1f * removableStatusEffects.count()).coerceAtMost(1f)).roundToInt()
            listOf(ActorHealedEvent(sourceId = it.sourceState.id, targetId = it.sourceState.id, amount = healAmount, actionContext = it.context))
        }
    }

    fun changeAppearanceState(destinationState: Int): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            it.sourceState.appearanceState = destinationState
            emptyList()
        }
    }

    fun allStatsDown(duration: Duration, potency: Float): List<AttackStatusEffect> {
        return listOf(
            AttackStatusEffect(statusEffect = StatusEffect.StrDown, baseDuration = duration) { it.statusState.secondaryPotency = potency},
            AttackStatusEffect(statusEffect = StatusEffect.VitDown, baseDuration = duration) { it.statusState.secondaryPotency = potency},
            AttackStatusEffect(statusEffect = StatusEffect.AgiDown, baseDuration = duration) { it.statusState.secondaryPotency = potency},
            AttackStatusEffect(statusEffect = StatusEffect.DexDown, baseDuration = duration) { it.statusState.secondaryPotency = potency},
            AttackStatusEffect(statusEffect = StatusEffect.IntDown, baseDuration = duration) { it.statusState.secondaryPotency = potency},
            AttackStatusEffect(statusEffect = StatusEffect.MndDown, baseDuration = duration) { it.statusState.secondaryPotency = potency},
        )
    }

    private fun allStatsUp(duration: Duration, potency: Float): List<AttackStatusEffect> {
        return listOf(
            AttackStatusEffect(statusEffect = StatusEffect.StrBoost, baseDuration = duration) { it.statusState.secondaryPotency = potency},
            AttackStatusEffect(statusEffect = StatusEffect.VitBoost, baseDuration = duration) { it.statusState.secondaryPotency = potency},
            AttackStatusEffect(statusEffect = StatusEffect.AgiBoost, baseDuration = duration) { it.statusState.secondaryPotency = potency},
            AttackStatusEffect(statusEffect = StatusEffect.DexBoost, baseDuration = duration) { it.statusState.secondaryPotency = potency},
            AttackStatusEffect(statusEffect = StatusEffect.IntBoost, baseDuration = duration) { it.statusState.secondaryPotency = potency},
            AttackStatusEffect(statusEffect = StatusEffect.MndBoost, baseDuration = duration) { it.statusState.secondaryPotency = potency},
        )
    }

}