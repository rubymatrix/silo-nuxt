package xim.poc.game.configuration.v0

import xim.poc.game.*
import xim.poc.game.configuration.CommonSkillHeuristics.avoidOverwritingBuff
import xim.poc.game.configuration.CommonSkillHeuristics.avoidOverwritingDebuff
import xim.poc.game.configuration.CommonSkillHeuristics.canInterruptTarget
import xim.poc.game.configuration.CommonSkillHeuristics.onlyIfBelowHppThreshold
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluator
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluator.Companion.compose
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluator.Companion.noop
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluatorContext
import xim.poc.game.configuration.SkillAppliers
import xim.poc.game.configuration.SkillHeuristics
import xim.poc.game.configuration.SkillRangeInfo
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.allStatsDown
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.basicDebuff
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.expireTargetDebuffs
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.extendedSourceRange
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.intPotency
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.singleStatus
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.spellDamageDoT
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.standardConeRange
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.standardSingleTargetRange
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.standardSourceRange
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.standardStaticTargetAoe
import xim.poc.game.event.*
import xim.poc.ui.ChatLog
import xim.resource.*
import xim.resource.table.SpellInfoTable.toSpellInfo
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class StatusEffectContext(val status: StatusEffectState, val context: TargetEvaluatorContext)

object V0SpellDefinitions {

    private val spellMetadata = HashMap<SkillId, V0Skill>()

    fun register() {
        // Cure
        listOf(spellCure_1, spellCureII_2, spellCureIII_3, spellCureIV_4, spellCureV_5, spellCureVI_6).forEachIndexed { index, spellSkillId ->
            SkillAppliers[spellSkillId] = SkillApplier(targetEvaluator = targetPercentageHealingMagic(10f + index * 2))
            SkillHeuristics[spellSkillId] = onlyIfBelowHppThreshold(0.75)
        }

        // Curaga
        listOf(spellCuraga_7, spellCuragaII_8, spellCuragaIII_9, spellCuragaIV_10, spellCuragaV_11).forEachIndexed { index, spellSkillId ->
            spellMetadata[spellSkillId] = V0Skill(castTime = 3.seconds, cost = V0AbilityCost(baseCost = AbilityCost(AbilityCostType.Mp, 15)), rangeInfo = standardStaticTargetAoe,)
            SkillAppliers[spellSkillId] = SkillApplier(targetEvaluator = targetPercentageHealingMagic(10f + index * 2))
            SkillHeuristics[spellSkillId] = onlyIfBelowHppThreshold(0.75)
        }

        // Dia
        spellMetadata[spellDia_23] = V0Skill(castTime = 1.seconds)
        SkillAppliers[spellDia_23] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(potency = 0.1f, attackStat = CombatStat.mnd, attackEffects = AttackEffects(
            attackStatusEffects = listOf(AttackStatusEffect(statusEffect = StatusEffect.Dia, baseDuration = 30.seconds) {
                it.statusState.potency = 1
                it.statusState.secondaryPotency = 0.90f
            }))
        ))

        // Dia II
        spellMetadata[spellDiaII_24] = V0Skill(castTime = 1.seconds)
        SkillAppliers[spellDiaII_24] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(potency = 0.2f, attackStat = CombatStat.mnd, attackEffects = AttackEffects(
            attackStatusEffects = listOf(AttackStatusEffect(statusEffect = StatusEffect.Dia, baseDuration = 30.seconds) {
                it.statusState.potency = 3
                it.statusState.secondaryPotency = 0.85f
            }))
        ))
        SkillHeuristics[spellDiaII_24] = avoidOverwritingDebuff(StatusEffect.Dia)

        // Basic Divine Magic
        listOf(spellBanish_28, spellBanishII_29, spellBanishIII_30, spellBanishIV_31, spellBanishV_32)
            .forEach { registerDivineMagic(it) }

        // Basic Divine Aga Magic
        listOf(spellBanishga_38, spellBanishgaII_39, spellBanishgaIII_40, spellBanishgaIV_41, spellBanishgaV_42)
            .forEach { registerDivineAgaMagic(it) }

        // Holy
        listOf(spellHoly_21, spellHolyII_22)
            .forEach { registerHoly(it) }

        // Protect II
        SkillAppliers[spellProtectII_44] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Protect, duration = 3.minutes) { it.status.potency = 20 })
        SkillHeuristics[spellProtectII_44] = avoidOverwritingBuff(StatusEffect.Protect)

        // Protect IV
        SkillAppliers[spellProtectIV_46] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Protect, duration = 3.minutes) { it.status.potency = 40 })
        SkillHeuristics[spellProtectIV_46] = avoidOverwritingBuff(StatusEffect.Protect)

        // Shell II
        SkillAppliers[spellShellII_49] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Shell, duration = 3.minutes) { it.status.potency = 20 })
        SkillHeuristics[spellShellII_49] = avoidOverwritingBuff(StatusEffect.Shell)

        // Shell IV
        SkillAppliers[spellShellIV_51] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Shell, duration = 3.minutes) { it.status.potency = 40 })
        SkillHeuristics[spellShellIV_51] = avoidOverwritingBuff(StatusEffect.Shell)

        // Blink
        SkillAppliers[spellBlink_53] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Blink, duration = 3.minutes) { it.status.counter = 5 })
        SkillHeuristics[spellBlink_53] = avoidOverwritingBuff(StatusEffect.Blink)

        // Aquaveil
        SkillAppliers[spellAquaveil_55] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Aquaveil, duration = 3.minutes) { it.status.counter = 5 })
        SkillHeuristics[spellAquaveil_55] = avoidOverwritingBuff(StatusEffect.Aquaveil)

        // Haste
        SkillAppliers[spellHaste_57] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Haste, duration = 3.minutes) { it.status.potency = 10 })
        SkillHeuristics[spellHaste_57] = avoidOverwritingBuff(StatusEffect.Haste)

        // Paralyze
        SkillAppliers[spellParalyze_58] = SkillApplier(targetEvaluator = basicDebuff(
            AttackStatusEffect(statusEffect = StatusEffect.Paralysis, baseDuration = 15.seconds) { it.statusState.potency = 30 })
        )
        SkillHeuristics[spellParalyze_58] = avoidOverwritingDebuff(statusEffect = StatusEffect.Paralysis)

        // Slow
        SkillAppliers[spellSlow_56] = SkillApplier(targetEvaluator = basicDebuff(statusEffect = StatusEffect.Slow, duration = 15.seconds) { it.statusState.potency = 20 })
        SkillHeuristics[spellSlow_56] = avoidOverwritingDebuff(StatusEffect.Slow)

        // Slow II
        SkillAppliers[spellSlowII_79] = SkillApplier(targetEvaluator = basicDebuff(statusEffect = StatusEffect.Slow, duration = 15.seconds) { it.statusState.potency = 40 })
        SkillHeuristics[spellSlowII_79] = avoidOverwritingDebuff(StatusEffect.Slow)

        // Elemental En-spells
        val enspells = listOf(
            spellEnfire_100 to StatusEffect.Enfire,
        )

        for ((spellId, status) in enspells) {
            SkillAppliers[spellId] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = status, duration = 3.minutes) {
                it.status.potency = intPotency(it.context.sourceState, scale = 0.1f)
            })
            SkillHeuristics[spellId] = avoidOverwritingBuff(status)
        }

        // Regen
        SkillAppliers[spellRegen_108] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Regen, 1.minutes) {
            it.status.potency = (it.context.sourceState.getMaxHp() * 0.02f).roundToInt().coerceAtLeast(1)
        })
        SkillHeuristics[spellRegen_108] = avoidOverwritingBuff(StatusEffect.Regen)

        // Regen II
        SkillAppliers[spellRegenII_110] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Regen, 1.minutes) {
            it.status.potency = (it.context.sourceState.getMaxHp() * 0.03f).roundToInt().coerceAtLeast(1)
        })
        SkillHeuristics[spellRegenII_110] = avoidOverwritingBuff(StatusEffect.Regen)

        // Flash
        spellMetadata[spellFlash_112] = V0Skill(castTime = 1.seconds)
        SkillAppliers[spellFlash_112] = SkillApplier(targetEvaluator = basicDebuff(statusEffect = StatusEffect.Flash, duration = 5.seconds) {
            it.statusState.potency = 90
        })

        // Drain II
        spellMetadata[spellDrainII_246] = V0Skill(castTime = 3.seconds)
        SkillAppliers[spellDrainII_246] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(potency = 1f, attackEffects = AttackEffects(
            damageResource = ActorResourceType.HP,
            absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, proportionalAbsorption = true)),
        ))

        // Aspir II
        SkillAppliers[spellAspirII_248] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(potency = 1f, damageCap = 40, attackEffects = AttackEffects(
            damageResource = ActorResourceType.MP,
            absorptionEffect = AttackAbsorptionEffect(absorption = 1f, absorptionResource = ActorResourceType.MP)),
        ))

        // Stun
        spellMetadata[spellStun_252] = V0Skill(castTime = 1.seconds)
        SkillAppliers[spellStun_252] = SkillApplier(targetEvaluator = basicDebuff(
            AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 5.seconds))
        )
        SkillHeuristics[spellStun_252] = canInterruptTarget()

        // Break
        SkillAppliers[spellBreak_255] = SkillApplier(targetEvaluator = basicDebuff(
            AttackStatusEffect(statusEffect = StatusEffect.Petrify, baseDuration = 10.seconds))
        )
        SkillHeuristics[spellBreak_255] = avoidOverwritingDebuff(statusEffect = StatusEffect.Petrify)

        // Breakga
        spellMetadata[spellBreakga_365] = V0Skill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[spellBreakga_365] = SkillApplier(targetEvaluator = basicDebuff(
            AttackStatusEffect(statusEffect = StatusEffect.Petrify, baseDuration = 10.seconds))
        )
        SkillHeuristics[spellBreakga_365] = avoidOverwritingDebuff(statusEffect = StatusEffect.Petrify)

        // Death
        spellMetadata[spellDeath_367] = V0Skill(rangeInfo = standardSingleTargetRange)
        SkillAppliers[spellDeath_367] = SkillApplier(targetEvaluator = noop())

        // Elemental Magic
        (spellFire_144.id .. spellWaterV_173.id).map { SpellSkillId(it) }.forEach { registerElementalMagic(it) }
        (spellFireVI_849.id .. spellWaterVI_854.id).map { SpellSkillId(it) }.forEach { registerElementalMagic(it) }

        // AoE Elemental Magic (-aga)
        (spellFiraga_174.id .. spellWatergaV_203.id).map { SpellSkillId(it) }.forEach { registerAgaElementalMagic(it) }
        (spellFiraja_496.id .. spellWaterja_501.id).map { SpellSkillId(it) }.forEach { registerAgaElementalMagic(it) }

        // AoE Elemental Magic (-ara)
        (spellFira_828.id .. spellWateraII_839.id).map { SpellSkillId(it) }.forEach { registerAraElementalMagic(it) }
        (spellFiraIII_865.id .. spellWateraIII_870.id).map { SpellSkillId(it) }.forEach { registerAraElementalMagic(it) }

        // Ancient Magic
        (spellFlare_204.id .. spellFloodII_215.id).map { SpellSkillId(it) }.forEach { registerAncientMagic(it) }

        // Bio II
        SkillAppliers[spellBioII_231] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(
            potency = 1f,
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Bio, duration = 1.minutes, potency = 0.1f, secondaryPotency = 0.7f)),
        ))

        // Elemental DoTs
        val elementalDoTs = listOf(
            spellBurn_235 to StatusEffect.Burn,
            spellFrost_236 to StatusEffect.Frost,
            spellChoke_237 to StatusEffect.Choke,
            spellRasp_238 to StatusEffect.Rasp,
            spellShock_239 to StatusEffect.Shock,
            spellDrown_240 to StatusEffect.Drown,
        )

        elementalDoTs.forEach {
            SkillAppliers[it.first] = SkillApplier(targetEvaluator = basicDebuff(
                attackEffects = singleStatus(spellDamageDoT(it.second, potency = 0.5f, secondaryPotency = 0.8f, duration = 18.seconds))
            ))
        }

        // Blaze Spikes
        SkillAppliers[spellBlazeSpikes_249] = SkillApplier(targetEvaluator = applyBasicBuff(
            statusEffect = StatusEffect.BlazeSpikes, duration = 2.minutes) { it.status.potency = intPotency(it.context.sourceState, 0.10f) }
        )
        SkillHeuristics[spellBlazeSpikes_249] = avoidOverwritingBuff(statusEffect = StatusEffect.BlazeSpikes)

        // Ice Spikes
        SkillAppliers[spellIceSpikes_250] = SkillApplier(targetEvaluator = applyBasicBuff(
            statusEffect = StatusEffect.IceSpikes, duration = 2.minutes) { it.status.potency = intPotency(it.context.sourceState, 0.05f)
        })
        SkillHeuristics[spellIceSpikes_250] = avoidOverwritingBuff(statusEffect = StatusEffect.IceSpikes)

        // Gravity
        SkillAppliers[spellGravity_216] = SkillApplier(targetEvaluator = basicDebuff(statusEffect = StatusEffect.Weight, duration = 15.seconds) { it.statusState.potency = 25 })
        SkillHeuristics[spellGravity_216] = avoidOverwritingDebuff(StatusEffect.Weight)

        // Meteor
        spellMetadata[spellMeteor_218] = V0Skill(
            castTime = 4.seconds,
            cost = V0AbilityCost(baseCost = AbilityCost(AbilityCostType.Mp, 15), consumesAll = false),
            rangeInfo = SkillRangeInfo(maxTargetDistance = 24f, effectRadius = 12f, type = AoeType.Target, tracksTarget = false)
        )
        SkillAppliers[spellMeteor_218] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(potency = 3f))

        // Comet
        spellMetadata[spellComet_219] = V0Skill(castTime = 3.seconds)
        SkillAppliers[spellComet_219] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(potency = 2.5f))

        // Meteor II
        spellMetadata[spellMeteorII_244] = V0Skill(
            castTime = 4.seconds,
            cost = V0AbilityCost(baseCost = AbilityCost(AbilityCostType.Mp, 15)),
            rangeInfo = SkillRangeInfo(maxTargetDistance = 24f, effectRadius = 12f, type = AoeType.Target, tracksTarget = false)
        )
        SkillAppliers[spellMeteorII_244] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(potency = 3f))

        // Sleepga
        spellMetadata[spellSleepga_273] = V0Skill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[spellSleepga_273] = SkillApplier(targetEvaluator = basicDebuff(StatusEffect.Sleep, duration = 15.seconds))
        SkillHeuristics[spellSleepga_273] = avoidOverwritingDebuff(StatusEffect.Sleep)

        // Paralyga
        spellMetadata[spellParalyga_356] = V0Skill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[spellParalyga_356] = SkillApplier(targetEvaluator = basicDebuff(StatusEffect.Paralysis, duration = 15.seconds) { it.statusState.potency = 33 })
        SkillHeuristics[spellParalyga_356] = avoidOverwritingDebuff(StatusEffect.Paralysis)

        // Slowga
        spellMetadata[spellSlowga_357] = V0Skill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[spellSlowga_357] = SkillApplier(targetEvaluator = basicDebuff(statusEffect = StatusEffect.Slow, duration = 15.seconds) { it.statusState.potency = 20 })
        SkillHeuristics[spellSlowga_357] = avoidOverwritingDebuff(StatusEffect.Slow)

        // Silencega
        spellMetadata[spellSilencega_359] = V0Skill(rangeInfo = standardStaticTargetAoe)
        SkillAppliers[spellSilencega_359] = SkillApplier(targetEvaluator = basicDebuff(StatusEffect.Silence, duration = 15.seconds))
        SkillHeuristics[spellSilencega_359] = avoidOverwritingDebuff(StatusEffect.Silence)

        // Mage's Ballad
        SkillAppliers[spellMagesBalladII_387] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Ballad, duration = 2.minutes) { it.status.potency = 2 })
        SkillHeuristics[spellMagesBalladII_387] = avoidOverwritingBuff(StatusEffect.Ballad)

        // Victory March
        SkillAppliers[spellVictoryMarch_420] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.March, duration = 2.minutes) { it.status.potency = 10 })
        SkillHeuristics[spellVictoryMarch_420] = avoidOverwritingBuff(StatusEffect.March)

        // Carnage Elegy
        SkillAppliers[spellCarnageElegy_422] = SkillApplier(targetEvaluator = basicDebuff(StatusEffect.Elegy, duration = 2.minutes) { it.statusState.potency = 10 })
        SkillHeuristics[spellCarnageElegy_422] = avoidOverwritingDebuff(StatusEffect.Elegy)

        // Temper
        SkillAppliers[spellTemper_493] = SkillApplier(targetEvaluator = applyBasicBuff(
            statusEffect = StatusEffect.MultiStrikes, duration = 2.minutes
        ) {
            it.status.counter = 2
            it.status.potency = 20
        })
        SkillHeuristics[spellTemper_493] = avoidOverwritingBuff(statusEffect = StatusEffect.MultiStrikes)

        // Impact
        SkillAppliers[spellImpact_503] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(attackEffects = AttackEffects(
            attackStatusEffects = allStatsDown(duration = 9.seconds, potency = 0.8f)
        )) { 2f })

        // Haste II
        SkillAppliers[spellHasteII_511] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Haste, duration = 3.minutes) { it.status.potency = 25 })
        SkillHeuristics[spellHasteII_511] = avoidOverwritingBuff(StatusEffect.Haste)

        // Feral Peck (override)
        spellMetadata[spellFeralPeck_516] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 12)),
            rangeInfo = standardSingleTargetRange,
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 15.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Fragmentation, SkillChainAttribute.Impaction),
            description = "Four-hit attack. Power: 5.0"
        )
        SkillAppliers[spellFeralPeck_516] = SkillApplier(
            targetEvaluator = applyBasicPhysicalMagic(numHits = 4) { 5.0f },
        )

        // Metallic Body
        spellMetadata[spellMetallicBody_517] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 8)),
            castTime = 2.5.seconds,
            recast = V0Recast(baseTime = 1.minutes),
            description = "Stoneskin (5m): 30% Max HP"
        )
        SkillAppliers[spellMetallicBody_517] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Stoneskin, 5.minutes) {
            it.status.counter = (it.context.sourceState.getMaxHp() * 0.3).roundToInt()
        })

        // Diamond Shell (override)
        spellMetadata[spellDiamondShell_518] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 8)),
            castTime = 2.5.seconds,
            recast = V0Recast(baseTime = 1.minutes),
            rangeInfo = standardSingleTargetRange,
            description = "Stoneskin (5m): 50% Max HP"
        )
        SkillAppliers[spellDiamondShell_518] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Stoneskin, 5.minutes) {
            it.status.counter = (it.context.sourceState.getMaxHp() / 2)
        })

        // Screwdriver
        spellMetadata[spellScrewdriver_519] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 7)),
            skillChainAttributes = listOf(SkillChainAttribute.Reverberation, SkillChainAttribute.Scission),
            description = "Single-hit attack. Power: 3.0"
        )
        SkillAppliers[spellScrewdriver_519] = SkillApplier(targetEvaluator = applyBasicPhysicalMagic { 3.0f })

        // Rock Smash (override)
        spellMetadata[spellRockSmash_520] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 12)),
            rangeInfo = standardSingleTargetRange,
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 30.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Scission),
            description = "Single-hit magic attack.\nPetrify: 30%. Power: 9.0"
        )
        SkillAppliers[spellRockSmash_520] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(potency = 9f, attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Petrify, baseDuration = 10.seconds, baseChance = 0.30f)
        )))

        // MP Drainkiss
        spellMetadata[spellMPDrainkiss_521] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 0)),
            skillChainAttributes = listOf(SkillChainAttribute.Compression),
            description = "Drains MP from target. Base max: 30 MP"
        )
        SkillAppliers[spellMPDrainkiss_521] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(potency = 1.5f, damageCap = 30, attackEffects = AttackEffects(
            damageResource = ActorResourceType.MP,
            absorptionEffect = AttackAbsorptionEffect(absorption = 1f, absorptionResource = ActorResourceType.MP)),
        ))

        // Death Ray
        spellMetadata[spellDeathRay_522] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 18)),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation),
            description = "Single-hit magic attack. Power: 25.0"
        )
        SkillAppliers[spellDeathRay_522] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(attackStat = CombatStat.int, potency = 25f)
        )

        // Fluorescence (override)
        spellMetadata[spellFluorescence_523] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 8)),
            rangeInfo = standardSingleTargetRange,
            castTime = 0.5.seconds,
            recast = V0Recast(baseTime = 60.seconds, fixedTime = true),
            description = "Boost (60s): +50% power on next skill or attack."
        )
        SkillAppliers[spellFluorescence_523] = SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Boost, duration = 1.minutes) {
            it.status.potency = 50
            it.status.counter = 1
        })

        // Revelation (override)
        spellMetadata[spellRevelation_526] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 15)),
            rangeInfo = standardSingleTargetRange,
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 45.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Liquefaction, SkillChainAttribute.Transfixion),
            description = "Four-hit attack. Power: 2.5"
        )
        SkillAppliers[spellRevelation_526] = SkillApplier(
            targetEvaluator = applyBasicPhysicalMagic(numHits = 4) { 2.5f },
        )

        // Smite of Rage
        spellMetadata[spellSmiteofRage_527] = V0Skill(
            skillChainAttributes = listOf(SkillChainAttribute.Fragmentation, SkillChainAttribute.Detonation),
            cost = V0AbilityCost(baseCost = AbilityCost(AbilityCostType.Mp, 5), consumesAll = false),
            recast = V0Recast(baseTime = 5.seconds),
            description = "Single-hit attack. Power: 8.0"
        )
        SkillAppliers[spellSmiteofRage_527] = SkillApplier(
            targetEvaluator = applyBasicPhysicalMagic { 8f },
        )

        // Autumn Breeze (override)
        spellMetadata[spellAutumnBreeze_528] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 10)),
            rangeInfo = standardSourceRange,
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 8.seconds),
            description = "Healing power: 1.0 (up to 30% Max HP)\nRemoves 1 debuff."
        )
        SkillAppliers[spellAutumnBreeze_528] = SkillApplier(targetEvaluator = compose(
            mndHealingMagic(potency = 1f, maxPercentage = 0.3f),
            expireTargetDebuffs(maxToExpire = 1),
        ))

        // Refueling
        spellMetadata[spellRefueling_530] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 12)),
            description = "Haste (5m): +10%"
        )
        SkillAppliers[spellRefueling_530] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Haste, 5.minutes) {
            it.status.potency = 10
        })

        // Cold Wave
        spellMetadata[spellColdWave_535] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 12)),
            description = "Frost (60s): -10% AGI. Tick power: 0.5"
        )
        SkillAppliers[spellColdWave_535] = SkillApplier(targetEvaluator = basicDebuff(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Frost, potency = 0.5f, secondaryPotency = 0.9f , duration = 60.seconds)
        )))

        // Digest
        spellMetadata[spellDigest_542] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 8)),
            skillChainAttributes = listOf(SkillChainAttribute.Compression),
            description = "Single-hit magic attack. Power: 3.0\nDrains HP from target. Base max: 100 HP."
        )
        SkillAppliers[spellDigest_542] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(potency = 3f,
            attackEffects = AttackEffects(absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, absorptionCap = 100))
        ))

        // Cursed Sphere
        spellMetadata[spellCursedSphere_544] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 8)),
            castTime = 1.5.seconds,
            recast = V0Recast(baseTime = 20.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Reverberation),
            description = "Single-hit magic attack. Power: 4.0"
        )
        SkillAppliers[spellCursedSphere_544] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(CombatStat.int, potency = 4f))

        // Night Stalker (override)
        spellMetadata[spellNightStalker_546] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 16)),
            rangeInfo = standardSingleTargetRange,
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 30.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation, SkillChainAttribute.Scission),
            description = "Two-hit magic attack. Power: 8.0\nAttack Down (60s): 10%.\nDefense Down (60s): 10%."
        )
        SkillAppliers[spellNightStalker_546] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(attackStat = CombatStat.int, numHits = 2, potency = 8f, attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(StatusEffect.AttackDown, baseDuration = 60.seconds) { it.statusState.potency = 10 },
                AttackStatusEffect(StatusEffect.DefenseDown, baseDuration = 60.seconds) { it.statusState.potency = 10 },
            )))
        )

        // Pollen
        spellMetadata[spellPollen_549] = V0Skill(
            description = "Healing power: 1.5 (up to 36 HP)."
        )
        SkillAppliers[spellPollen_549] = SkillApplier(targetEvaluator = mndHealingMagic(potency = 1.5f, maxAmount = 36))

        // Sanguinary Slash (override)
        spellMetadata[spellSanguinarySlash_550] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 6)),
            rangeInfo = standardSingleTargetRange,
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 30.seconds),
            description = "Single-hit magic attack. Power: 3.0"
        )
        SkillAppliers[spellSanguinarySlash_550] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(attackStat = CombatStat.int, potency = 3f))

        // Mow (Override)
        spellMetadata[spellMow_552] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 10)),
            skillChainAttributes = listOf(SkillChainAttribute.Induration, SkillChainAttribute.Compression),
            rangeInfo = standardSourceRange,
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 20.seconds),
            description = "Three-hit attack. Power: 2.5\nPoison (30s): Tick power: 0.3"
        )
        SkillAppliers[spellMow_552] = SkillApplier(targetEvaluator = applyBasicPhysicalMagic(numHits = 3, attackEffects = singleStatus(
            attackStatusEffect = spellDamageDoT(StatusEffect.Poison, potency = 0.3f, duration = 30.seconds)
        )) { 2.5f })

        // Memory (Override)
        spellMetadata[spellMemoryofEarth_553] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 16)),
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 20.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Compression),
            rangeInfo = standardSourceRange,
            description = "Single-hit magic attack. Power: 10.0"
        )
        SkillAppliers[spellMemoryofEarth_553] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(attackStat = CombatStat.int, potency = 10f)
        )

        // Zephyr Arrow (override)
        spellMetadata[spellZephyrArrow_556] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 14)),
            rangeInfo = standardSingleTargetRange,
            castTime = 0.5.seconds,
            recast = V0Recast(baseTime = 60.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Fragmentation, SkillChainAttribute.Detonation),
            description = "Single-hit magic attack. Power: 10.0\nKnock-back: 2.0"
        )
        SkillAppliers[spellZephyrArrow_556] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(potency = 10f, attackEffects = AttackEffects(knockBackMagnitude = 2))
        )

        // Hellborn Yawp (Override)
        spellMetadata[spellHellbornYawp_562] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 12)),
            castTime = 3.seconds,
            recast = V0Recast(baseTime = 30.seconds),
            rangeInfo = standardSourceRange,
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation, SkillChainAttribute.Compression),
            description = "Single-hit magic attack. Power: 16.0\nDispels 1 buff."
        )
        SkillAppliers[spellHellbornYawp_562] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(attackStat = CombatStat.int, potency = 16f, attackEffects = AttackEffects(dispelCount = 1))
        )

        // Radiant Breath
        spellMetadata[spellRadiantBreath_565] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 26)),
            skillChainAttributes = listOf(SkillChainAttribute.Fusion),
            description = "Single-hit magic attack. Power: 20.0\nSlow (60s): 5%\nSilence (3s)",
            rangeInfo = standardConeRange,
        )
        SkillAppliers[spellRadiantBreath_565] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(attackStat = CombatStat.int, potency = 20f, attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(StatusEffect.Slow, baseDuration = 60.seconds) { it.statusState.potency = 5 },
                AttackStatusEffect(StatusEffect.Silence, baseDuration = 3.seconds),
            )))
        )

        // Jet Stream
        spellMetadata[spellJetStream_569] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 14)),
            skillChainAttributes = listOf(SkillChainAttribute.Impaction, SkillChainAttribute.Compression),
            description = "Three-hit attack. Power: 2.5"
        )
        SkillAppliers[spellJetStream_569] = SkillApplier(
            targetEvaluator = applyBasicPhysicalMagic(numHits = 3) { 2.5f },
        )

        // Foot Kick
        spellMetadata[spellFootKick_577] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 4)),
            skillChainAttributes = listOf(SkillChainAttribute.Detonation),
            description = "Single-hit attack. Power: 2.0"
        )
        SkillAppliers[spellFootKick_577] = SkillApplier(
            targetEvaluator = applyBasicPhysicalMagic { 2f },
        )

        // Wild Carrot
        spellMetadata[spellWildCarrot_578] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 10)),
            description = "Healing power: 1.5 (up to 120 HP)."
        )
        SkillAppliers[spellWildCarrot_578] = SkillApplier(targetEvaluator = mndHealingMagic(potency = 1.5f, maxPercentage = 0.5f, maxAmount = 120))

        // Lowing
        spellMetadata[spellLowing_588] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 12)),
            description = "Plague (60s): -3MP per tick. -30TP per tick."
        )
        SkillAppliers[spellLowing_588] = SkillApplier(targetEvaluator = basicDebuff(attackStatusEffect =
            AttackStatusEffect(StatusEffect.Plague, baseDuration = 1.minutes) { it.statusState.potency = 3 }
        ))

        // Pinecone Bomb
        spellMetadata[spellPineconeBomb_596] = V0Skill(
            skillChainAttributes = listOf(SkillChainAttribute.Liquefaction),
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 10)),
            description = "Single-hit attack. Power: 3.0\nSleep (18s)."
        )
        SkillAppliers[spellPineconeBomb_596] = SkillApplier(targetEvaluator = applyBasicPhysicalMagic(attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Sleep, baseDuration = 18.seconds)
        )) { 3f })

        // Magic Fruit
        spellMetadata[spellMagicFruit_593] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 12)),
            castTime = 2.seconds,
            description = "Healing power: 1.5 (up to 300 HP)."
        )
        SkillAppliers[spellMagicFruit_593] = SkillApplier(targetEvaluator = mndHealingMagic(potency = 1.5f, maxPercentage = 0.5f, maxAmount = 300))

        // Queasyshroom
        spellMetadata[spellQueasyshroom_599] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 12)),
            skillChainAttributes = listOf(SkillChainAttribute.Compression, SkillChainAttribute.Scission),
            description = "Single-hit attack. Power: 2.0\nPoison (30s): Tick power: 1.0"
        )
        SkillAppliers[spellQueasyshroom_599] = SkillApplier(targetEvaluator = applyBasicPhysicalMagic(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Poison, duration = 30.seconds, potency = 1f)),
        ) { 2f })

        // Feather Maelstrom (override)
        spellMetadata[spellFeatherMaelstrom_600] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 18)),
            rangeInfo = standardSingleTargetRange,
            castTime = 2.seconds,
            recast = V0Recast(baseTime = 45.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Fragmentation, SkillChainAttribute.Detonation),
            description = "Single-hit attack. Power: 14.0\nBio (60s): -10% STR. Tick power: 0.5"
        )
        SkillAppliers[spellFeatherMaelstrom_600] = SkillApplier(targetEvaluator = applyBasicPhysicalMagic(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Bio, potency = 0.5f, secondaryPotency = 0.9f, duration = 60.seconds))
        ) { 14f })

        // Phantasmal Dance (override)
        spellMetadata[spellPhantasmalDance_601] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 18)),
            rangeInfo = standardSourceRange,
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 45.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Fusion, SkillChainAttribute.Impaction),
            description = "Four-hit attack. Power: 4.0\nBind (15s).\nKnock-back: 4.0"
        )
        SkillAppliers[spellPhantasmalDance_601] = SkillApplier(targetEvaluator = applyBasicPhysicalMagic(numHits = 4, attackEffects = AttackEffects(
            knockBackMagnitude = 4,
            attackStatusEffects = listOf(AttackStatusEffect(statusEffect = StatusEffect.Bind, baseDuration = 15.seconds)),
        )) { 4.0f })

        // Seed of Judgement (override)
        spellMetadata[spellSeedofJudgement_602] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 16)),
            rangeInfo = extendedSourceRange,
            castTime = 2.seconds,
            recast = V0Recast(baseTime = 60.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Fusion, SkillChainAttribute.Transfixion),
            description = "Single-hit magic attack. Power: 18.0\nKnock-back: 4.0"
        )
        SkillAppliers[spellSeedofJudgement_602] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(potency = 18f, attackEffects = AttackEffects(knockBackMagnitude = 4))
        )

        // Bad Breath
        spellMetadata[spellBadBreath_604] = V0Skill(
            rangeInfo = standardConeRange,
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 26)),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation),
            description = "Single-hit magic attack. Power: 20.0\nSlow (60s): 5%.\nWeight (60s): 10%\nSleep (30s)"
        )
        SkillAppliers[spellBadBreath_604] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(attackStat = CombatStat.int, potency = 20f, attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(StatusEffect.Slow, baseDuration = 60.seconds) { it.statusState.potency = 5 },
                AttackStatusEffect(StatusEffect.Weight, baseDuration = 60.seconds) { it.statusState.potency = 10 },
                AttackStatusEffect(statusEffect = StatusEffect.Sleep, baseDuration = 30.seconds),
            )))
        )

        // Geist Wall
        spellMetadata[spellGeistWall_605] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 8)),
            description = "Dispels 1 buff."
        )
        SkillAppliers[spellGeistWall_605] = SkillApplier(targetEvaluator = basicDebuff(dispelCount = 1))

        // Frost Breath
        spellMetadata[spellFrostBreath_608] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 10)),
            rangeInfo = standardConeRange,
            skillChainAttributes = listOf(SkillChainAttribute.Induration),
            description = "Single-hit magic attack. Power: 9.0\nParalysis (60s): 10% chance."
        )
        SkillAppliers[spellFrostBreath_608] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(potency = 9f, attackEffects = singleStatus(
            attackStatusEffect = AttackStatusEffect(StatusEffect.Paralysis, baseDuration = 60.seconds) { it.statusState.potency = 10 }
        )))

        // Blastbomb
        spellMetadata[spellBlastbomb_618] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 12)),
            skillChainAttributes = listOf(SkillChainAttribute.Liquefaction),
            description = "Single-hit magic attack. Power: 6.0\nBind (12s)."
        )
        SkillAppliers[spellBlastbomb_618] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(
            CombatStat.int, potency = 6f, attackEffects = singleStatus(
                attackStatusEffect = AttackStatusEffect(StatusEffect.Bind, baseDuration = 12.seconds)
            )
        ))

        // Grand Slam
        spellMetadata[spellGrandSlam_622] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 15)),
            skillChainAttributes = listOf(SkillChainAttribute.Induration),
            rangeInfo = standardSourceRange,
            description = "Single-hit attack. Power: 10.0"
        )
        SkillAppliers[spellGrandSlam_622] = SkillApplier(targetEvaluator = applyBasicPhysicalMagic { 10f })

        // Diamondhide
        spellMetadata[spellDiamondhide_632] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 8)),
            castTime = 2.5.seconds,
            recast = V0Recast(baseTime = 1.minutes),
            rangeInfo = standardSingleTargetRange,
            description = "Stoneskin (5m): 40% Max HP"
        )
        SkillAppliers[spellDiamondhide_632] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Stoneskin, 5.minutes) {
            it.status.counter = (it.context.sourceState.getMaxHp() * 0.4).roundToInt()
        })

        // Amplification
        spellMetadata[spellAmplification_642] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 16)),
            description = "Magic Atk Boost (5m): +10\nMagic Def Boost (5m): +10"
        )
        SkillAppliers[spellAmplification_642] = SkillApplier(
            targetEvaluator = compose(
                applyBasicBuff(statusEffect = StatusEffect.MagicAtkBoost, duration = 5.minutes) { it.status.potency = 10 },
                applyBasicBuff(statusEffect = StatusEffect.MagicDefBoost, duration = 5.minutes) { it.status.potency = 10 },
            )
        )

        // Corrosive Ooze
        spellMetadata[spellCorrosiveOoze_651] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 16)),
            skillChainAttributes = listOf(SkillChainAttribute.Reverberation),
            description = "Single-hit magic attack. Power: 6.0\nAttack Down (60s): 10%\nDefense Down (60s): 10%"
        )
        SkillAppliers[spellCorrosiveOoze_651] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(attackStat = CombatStat.int, potency = 6f, attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(StatusEffect.AttackDown, baseDuration = 60.seconds) { it.statusState.potency = 10 },
                AttackStatusEffect(StatusEffect.DefenseDown, baseDuration = 60.seconds) { it.statusState.potency = 10 },
            )))
        )

        // Plenilune Embrace
        spellMetadata[spellPleniluneEmbrace_658] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 14)),
            rangeInfo = standardSingleTargetRange,
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 8.seconds),
            description = "Healing power: 1.2 (up to 30% Max HP)\nRemoves 2 debuffs."
        )
        SkillAppliers[spellPleniluneEmbrace_658] = SkillApplier(targetEvaluator = compose(
            mndHealingMagic(potency = 1.2f, maxPercentage = 0.3f),
            expireTargetDebuffs(maxToExpire = 2),
        ))

        // Demoralizing Roar
        spellMetadata[spellDemoralizingRoar_659] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 12)),
            description = "Attack Down (60s): 15%.\nDefense Down (60s): 15%."
        )
        SkillAppliers[spellDemoralizingRoar_659] = SkillApplier(
            targetEvaluator = basicDebuff(attackEffects = AttackEffects(attackStatusEffects = listOf(
                AttackStatusEffect(StatusEffect.AttackDown, baseDuration = 60.seconds) { it.statusState.potency = 15 },
                AttackStatusEffect(StatusEffect.DefenseDown, baseDuration = 60.seconds) { it.statusState.potency = 15 },
            )))
        )

        // Battery Charge
        spellMetadata[spellBatteryCharge_662] = V0Skill(
            V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 0)),
            description = "Refresh (5m): +1 MP per tick"
        )
        SkillAppliers[spellBatteryCharge_662] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Refresh, 5.minutes) {
            it.status.potency = 1
        })

        // Regeneration
        spellMetadata[spellRegeneration_664] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 10)),
            description = "Regen (2m): +3% of Max HP per tick"
        )
        SkillAppliers[spellRegeneration_664] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Regen, 2.minutes) {
            it.status.potency = (it.context.sourceState.getMaxHp() / 30f).roundToInt()
        })

        // Vanity Dive
        spellMetadata[spellVanityDive_667] = V0Skill(
            skillChainAttributes = listOf(SkillChainAttribute.Compression, SkillChainAttribute.Detonation),
            cost = V0AbilityCost(baseCost = AbilityCost(AbilityCostType.Mp, 4), consumesAll = false),
            recast = V0Recast(baseTime = 5.seconds),
            description = "Single-hit attack. Power: 5.0"
        )
        SkillAppliers[spellVanityDive_667] = SkillApplier(
            targetEvaluator = applyBasicPhysicalMagic { 5f },
        )

        // Thermal Pulse
        spellMetadata[spellThermalPulse_675] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 15)),
            skillChainAttributes = listOf(SkillChainAttribute.Liquefaction),
            description = "Single-hit magic attack. Power: 12.0\nBlind (30s): -10% accuracy."
        )
        SkillAppliers[spellThermalPulse_675] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(
            potency = 12f,
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Blind, 30.seconds) { it.statusState.potency = 10 } )),
        )

        // Empty Thrash
        spellMetadata[spellEmptyThrash_677] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 10)),
            skillChainAttributes = listOf(SkillChainAttribute.Compression, SkillChainAttribute.Scission),
            rangeInfo = standardConeRange,
            description = "Single-hit attack. Power: 8.0"
        )
        SkillAppliers[spellEmptyThrash_677] = SkillApplier(
            targetEvaluator = applyBasicPhysicalMagic { 8.0f },
        )

        // Dream Flower
        spellMetadata[spellDreamFlower_678] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 12)),
            description = "Sleep (30s)."
        )
        SkillAppliers[spellDreamFlower_678] = SkillApplier(targetEvaluator = basicDebuff(
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Sleep, baseDuration = 30.seconds)
        ))

        // Occultation
        spellMetadata[spellOccultation_679] = V0Skill(V0AbilityCost(AbilityCost(
            type = AbilityCostType.Mp, value = 18)),
            castTime = 1.5.seconds,
            recast = V0Recast(baseTime = 60.seconds),
            description = "Blink: Gain 3 shadows.\nEach shadow blocks one single-target attack.\nAuto-Attack block-rate: 50%\nSkill block-rate: 100%"
        )
        SkillAppliers[spellOccultation_679] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Blink) {
            it.status.counter = 3
        })

        // Charged Whisker
        spellMetadata[spellChargedWhisker_680] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 18)),
            recast = V0Recast(baseTime = 85.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Detonation),
            description = "Single-hit magic attack. Power: 23.0"
        )
        SkillAppliers[spellChargedWhisker_680] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(attackStat = CombatStat.int, potency = 23f)
        )

        // Winds of Promy.
        spellMetadata[spellWindsofPromy_681] = V0Skill(
            V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 10)),
            description = "Removes 1 debuff."
        )
        SkillAppliers[spellWindsofPromy_681] = SkillApplier(targetEvaluator = expireTargetDebuffs(maxToExpire = 1))

        // Vicious Kick (Override)
        spellMetadata[spellViciousKick_691] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 12)),
            rangeInfo = standardSingleTargetRange,
            castTime = 0.5.seconds,
            recast = V0Recast(baseTime = 10.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation, SkillChainAttribute.Impaction),
            description = "Single-hit attack. Power: 12.0\nStun (3s)\nKnock-back: 2.0"
        )
        SkillAppliers[spellViciousKick_691] = SkillApplier(targetEvaluator = applyBasicPhysicalMagic(attackEffects = singleStatus(
            knockBackMagnitude = 2,
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 3.seconds),
        )) { 12f })

        // Sudden Lunge
        spellMetadata[spellSuddenLunge_692] = V0Skill(
            skillChainAttributes = listOf(SkillChainAttribute.Impaction),
            recast = V0Recast(baseTime = 10.seconds),
            castTime = 0.5.seconds,
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 12)),
            description = "Single-hit attack. Power: 1.0\nStun (3s)\nKnock-back: 1.0"
        )
        SkillAppliers[spellSuddenLunge_692] = SkillApplier(targetEvaluator = applyBasicPhysicalMagic(attackEffects = singleStatus(
            knockBackMagnitude = 1,
            attackStatusEffect = AttackStatusEffect(statusEffect = StatusEffect.Stun, baseDuration = 3.seconds),
        )) { 1f })

        // Amorphic Spikes
        spellMetadata[spellAmorphicSpikes_697] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 20)),
            recast = V0Recast(baseTime = 60.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation, SkillChainAttribute.Transfixion),
            description = "Five-hit attack. Power: 2.4"
        )
        SkillAppliers[spellAmorphicSpikes_697] = SkillApplier(
            targetEvaluator = applyBasicPhysicalMagic(numHits = 5) { 2.4f },
        )

        // Tempestuous Upheaval
        spellMetadata[spellTemUpheaval_701] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 16)),
            skillChainAttributes = listOf(SkillChainAttribute.Detonation),
            description = "Single-hit magic attack. Power: 8.0"
        )
        SkillAppliers[spellTemUpheaval_701] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(attackStat = CombatStat.int, potency = 8f)
        )

        // Rending Deluge
        spellMetadata[spellRendingDeluge_702] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 18)),
            skillChainAttributes = listOf(SkillChainAttribute.Reverberation),
            description = "Single-hit magic attack. Power: 8.0\nDispels 1 buff."
        )
        SkillAppliers[spellRendingDeluge_702] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(attackStat = CombatStat.int, potency = 8f, attackEffects = AttackEffects(dispelCount = 1))
        )

        // Paralyzing Triad
        spellMetadata[spellParalyzingTriad_704] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 10)),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation, SkillChainAttribute.Scission),
            description = "Three-hit attack. Power: 4.5\nParalysis (10s): 12% chance."
        )
        SkillAppliers[spellParalyzingTriad_704] = SkillApplier(targetEvaluator = applyBasicPhysicalMagic(
            numHits = 3, attackEffects = singleStatus(AttackStatusEffect(StatusEffect.Paralysis, 10.seconds) { it.statusState.potency = 12 })
        ) { 4.5f })

        // Subduction
        spellMetadata[spellSubduction_708] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 6)),
            skillChainAttributes = listOf(SkillChainAttribute.Fragmentation, SkillChainAttribute.Detonation),
            description = "Single-hit magic attack. Power: 12.0\nWeight (8s): -50% movement speed."
        )
        SkillAppliers[spellSubduction_708] = SkillApplier(
            targetEvaluator = applyBasicOffenseMagic(attackStat = CombatStat.int, potency = 12f, attackEffects = singleStatus(
                AttackStatusEffect(StatusEffect.Weight, 8.seconds) { it.statusState.potency = 50 }
            ))
        )

        // Erratic Flutter
        spellMetadata[spellErraticFlutter_710] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 12)),
            description = "Haste (5m): +20%"
        )
        SkillAppliers[spellErraticFlutter_710] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Haste, 5.minutes) {
            it.status.potency = 20
        })

        // Thunderbolt
        spellMetadata[spellThunderbolt_736] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 20)),
            rangeInfo = SkillRangeInfo(maxTargetDistance = 20f, effectRadius = 20f, AoeType.Source),
            castTime = 0.5.seconds,
            recast = V0Recast(baseTime = 1.minutes, fixedTime = true),
            skillChainAttributes = listOf(SkillChainAttribute.Fragmentation, SkillChainAttribute.Impaction),
            description = "Single-hit magic attack. Power: 6.0+\nStun (5s)."
        )
        SkillAppliers[spellThunderbolt_736] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(
            attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(StatusEffect.Stun, 5.seconds)),
            potencyFn = levelScalingPotency(basePotency = 6f) { it / 2f }
        ))

        // Gates of Hades
        spellMetadata[spellGatesofHades_739] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 20)),
            rangeInfo = SkillRangeInfo(maxTargetDistance = 20f, effectRadius = 20f, AoeType.Source),
            recast = V0Recast(baseTime = 1.minutes, fixedTime = true),
            skillChainAttributes = listOf(SkillChainAttribute.Fusion, SkillChainAttribute.Liquefaction),
            description = "Single-hit magic attack. Power: 6.0+\nBurn (60s): -20% INT. Tick power: 0.5"
        )
        SkillAppliers[spellGatesofHades_739] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Burn, potency = 0.5f, secondaryPotency = 0.8f, duration = 1.minutes)),
            potencyFn = levelScalingPotency(basePotency = 6f) { it / 2f }
        ))

        // Droning Whirlwind
        spellMetadata[spellDroningWhirlwind_744] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 20)),
            rangeInfo = SkillRangeInfo(maxTargetDistance = 20f, effectRadius = 20f, AoeType.Source),
            recast = V0Recast(baseTime = 1.minutes, fixedTime = true),
            skillChainAttributes = listOf(SkillChainAttribute.Fragmentation, SkillChainAttribute.Detonation),
            description = "Single-hit magic attack. Power: 6.0+\nDispels 2 buffs.\nKnock-back: 4.0"
        )
        SkillAppliers[spellDroningWhirlwind_744] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(
            attackStat = CombatStat.int,
            attackEffects = AttackEffects(dispelCount = 2, knockBackMagnitude = 4),
            potencyFn = levelScalingPotency(basePotency = 6f) { it / 2f }
        ))

        // Carcharian Verve
        spellMetadata[spellCarcharianVerve_745] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 18)),
            recast = V0Recast(baseTime = 1.minutes, fixedTime = true),
            description = "Aquaveil (10m): Prevents next 5 interrupts."
        )
        SkillAppliers[spellCarcharianVerve_745] = SkillApplier(
            targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.Aquaveil, duration = 10.minutes) { it.status.counter = 5 },
        )

        // Uproot
        spellMetadata[spellUproot_747] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 20)),
            recast = V0Recast(baseTime = 1.minutes, fixedTime = true),
            skillChainAttributes = listOf(SkillChainAttribute.Light, SkillChainAttribute.Transfixion),
            description = "Single-hit magic attack. Power: 6.0+\nRemoves all debuffs."
        )
        SkillAppliers[spellUproot_747] = SkillApplier(
            additionalSelfEvaluator = expireTargetDebuffs(maxToExpire = 999),
            targetEvaluator = applyBasicOffenseMagic(potencyFn = levelScalingPotency(basePotency = 6f) { it / 2f })
        )

        // Volcanic Wrath (Override)
        spellMetadata[spellVolcanicWrath_754] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 20)),
            rangeInfo = extendedSourceRange,
            castTime = 3.5.seconds,
            recast = V0Recast(baseTime = 1.minutes),
            skillChainAttributes = listOf(SkillChainAttribute.Fusion, SkillChainAttribute.Liquefaction),
            description = "Single-hit magic attack. Power: 20.0\nBurn (60s): -10% INT. Tick power: 1.0"
        )
        SkillAppliers[spellVolcanicWrath_754] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Burn, potency = 1.0f, secondaryPotency = 0.9f, duration = 1.minutes)),
        ) { 20f })

        // Magma Hoplon (override)
        spellMetadata[spellMagmaHoplon_755] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 8)),
            castTime = 2.5.seconds,
            recast = V0Recast(baseTime = 1.minutes),
            rangeInfo = standardSingleTargetRange,
            description = "Stoneskin (5m): 33% Max HP\nBlaze Spikes (1m): Power 3.0"
        )
        SkillAppliers[spellMagmaHoplon_755] = SkillApplier(targetEvaluator = compose(
            applyBasicBuff(StatusEffect.Stoneskin, 5.minutes) {
                it.status.counter = (it.context.sourceState.getMaxHp() / 3)
            },
            applyBasicBuff(StatusEffect.BlazeSpikes, 1.minutes) {
                it.status.potency = intPotency(it.context.sourceState, 3f)
            },
        ))

        // Bloody Caress (override)
        spellMetadata[spellBloodyCaress_756] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 18)),
            castTime = 4.seconds,
            recast = V0Recast(120.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation, SkillChainAttribute.Scission),
            description = "Three-hit magic attack. Power: 6.0\nDrains HP from target. Base max: 250 HP."
        )
        SkillAppliers[spellBloodyCaress_756] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(
            numHits = 3, attackEffects = AttackEffects(absorptionEffect = AttackAbsorptionEffect(absorption = 0.5f, absorptionCap = 250))
        ) { 6f })

        // Predatory Glare (Override)
        spellMetadata[spellPredatoryGlare_757] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 10)),
            rangeInfo = standardSingleTargetRange,
            castTime = 0.5.seconds,
            recast = V0Recast(15.seconds),
            description = "Gaze: Stun (3s)"
        )
        SkillAppliers[spellPredatoryGlare_757] = SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            gazeAttack = true, attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Stun, baseDuration = 3.seconds))
        )))

        // Lethal Triclip (Override)
        spellMetadata[spellLethalTriclip_758] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 18)),
            skillChainAttributes = listOf(SkillChainAttribute.Distortion, SkillChainAttribute.Gravitation),
            rangeInfo = standardSourceRange,
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 20.seconds),
            description = "Three-hit attack. Power: 7.0\nPoison (30s): Tick power: 1.0"
        )
        SkillAppliers[spellLethalTriclip_758] = SkillApplier(targetEvaluator = applyBasicPhysicalMagic(numHits = 3, attackEffects = singleStatus(
            attackStatusEffect = spellDamageDoT(StatusEffect.Poison, potency = 1f, duration = 30.seconds)
        )) { 7.0f })

        // Kaleidoscopic Fury (override)
        spellMetadata[spellKaleidoscopicFury_759] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 20)),
            rangeInfo = standardSourceRange,
            castTime = 2.seconds,
            recast = V0Recast(baseTime = 60.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Fusion, SkillChainAttribute.Transfixion),
            description = "Single-hit magic attack. Power: 20.0\nDia (60s): -10% VIT. Tick power: 0.3"
        )
        SkillAppliers[spellKaleidoscopicFury_759] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(
            attackEffects = singleStatus(spellDamageDoT(StatusEffect.Dia, potency = 0.3f, secondaryPotency = 0.9f, duration = 60.seconds))
        ) { 20f })

        // Rhinowrecker (override)
        spellMetadata[spellRhinowrecker_760] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 16)),
            rangeInfo = standardConeRange,
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 60.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Fragmentation, SkillChainAttribute.Scission),
            description = "Single-hit attack. Power: 23.0\nKnock-back: 2.0"
        )
        SkillAppliers[spellRhinowrecker_760] = SkillApplier(targetEvaluator = applyBasicPhysicalMagic(
            attackEffects = AttackEffects(knockBackMagnitude = 2)
        ) { 23f })

        // Catharsis (override)
        spellMetadata[spellCatharsis_761] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = 15)),
            castTime = 2.seconds,
            recast = V0Recast(baseTime = 30.seconds),
            description = "Healing power: 1.5 (up to 500 HP)."
        )
        SkillAppliers[spellCatharsis_761] = SkillApplier(targetEvaluator = mndHealingMagic(potency = 1.5f, maxPercentage = 0.5f, maxAmount = 500))

        // Freezing Gale (override)
        spellMetadata[spellFreezingGale_762] = V0Skill(
            cost = V0AbilityCost(AbilityCost(type = AbilityCostType.Mp, value = 19)),
            rangeInfo = standardConeRange,
            castTime = 1.seconds,
            recast = V0Recast(baseTime = 60.seconds),
            skillChainAttributes = listOf(SkillChainAttribute.Distortion, SkillChainAttribute.Transfixion),
            description = "Single-hit magic attack. Power: 20.0\nFrost (60s): -10% AGI. Tick power: 0.5"
        )
        SkillAppliers[spellFreezingGale_762] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(attackEffects = singleStatus(
            spellDamageDoT(StatusEffect.Frost, duration = 60.seconds, potency = 0.5f, secondaryPotency = 0.9f),
        )) { 20f })

    }

    fun getCost(skill: SpellSkillId): V0AbilityCost {
        val customCost = spellMetadata[skill]?.cost
        if (customCost != null) { return customCost }

        val basicCost = if (isBlueMagic(skill) || isSong(skill)) { skill.toSpellInfo().mpCost } else { 10 }
        return V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = basicCost))
    }

    fun getRecast(skill: SkillId): V0Recast? {
        val basicRecast = if (isBlueMagic(skill)) { null } else { V0Recast(5.seconds) }
        return spellMetadata[skill]?.recast ?: basicRecast
    }

    fun getCastTime(skill: SkillId): Duration? {
        val basicCast = if (isBlueMagic(skill) || isSong(skill)) { null } else { 2.seconds }
        return spellMetadata[skill]?.castTime ?: basicCast
    }

    fun getSkillChainAttributes(source: ActorState, skill: SpellSkillId): List<SkillChainAttribute> {
        val spellMetadata = spellMetadata[skill] ?: return emptyList()

        val info = skill.toSpellInfo()
        val hasImmanence = source.hasStatusEffect(StatusEffect.Immanence)

        return if (info.element == SpellElement.None || hasImmanence) {
            spellMetadata.skillChainAttributes
        } else {
            emptyList()
        }
    }

    fun getRange(skill: SkillId): SkillRangeInfo? {
        return spellMetadata[skill]?.rangeInfo
    }

    fun getMovementLock(skill: SkillId): Int? {
        return spellMetadata[skill]?.movementLockOverride
    }

    fun getDescription(skill: SkillId): String {
        return spellMetadata[skill]?.description ?: "TBA"
    }

    fun mndHealingMagic(potency: Float, maxPercentage: Float = 1f, maxAmount: Int = Int.MAX_VALUE): TargetEvaluator {
        return TargetEvaluator {
            val cappedHealAmount = (it.sourceState.getMaxHp() * maxPercentage).roundToInt()
            val healAmount = DamageCalculator.getHealAmount(it.sourceState, it.targetState, potency, minOf(cappedHealAmount, maxAmount))
            val outputEvents = ActorHealedEvent(sourceId = it.sourceState.id, targetId = it.targetState.id, amount = healAmount, actionContext = it.context)
            listOf(outputEvents)
        }
    }

    fun sourcePercentageHealingMagic(percent: Float): TargetEvaluator {
        return TargetEvaluator {
            val healAmount = (it.sourceState.getMaxHp() * (percent / 100f)).roundToInt()
            val outputEvents = ActorHealedEvent(sourceId = it.sourceState.id, targetId = it.targetState.id, amount = healAmount, actionContext = it.context)
            listOf(outputEvents)
        }
    }

    fun targetPercentageHealingMagic(percent: Float): TargetEvaluator {
        return TargetEvaluator {
            val healAmount = (it.targetState.getMaxHp() * (percent / 100f)).roundToInt()
            val outputEvents = ActorHealedEvent(sourceId = it.sourceState.id, targetId = it.targetState.id, amount = healAmount, actionContext = it.context)
            listOf(outputEvents)
        }
    }

    private fun isBlueMagic(skill: SkillId): Boolean {
        return skill is SpellSkillId && skill.toSpellInfo().magicType == MagicType.BlueMagic
    }

    private fun isSong(skill: SkillId): Boolean {
        return skill is SpellSkillId && skill.toSpellInfo().magicType == MagicType.Songs
    }

    fun applyBasicBuff(statusEffect: StatusEffect, duration: Duration? = null, statusStateSetter: (StatusEffectContext) -> Unit = { }): TargetEvaluator {
        return TargetEvaluator {
            val state = it.targetState.gainStatusEffect(statusEffect, duration)
            state.linkedSkillId = it.skill
            statusStateSetter.invoke(StatusEffectContext(state, it))
            AttackContext.compose(it.context) { ChatLog.statusEffectGained(it.targetState.name, statusEffect, it.context) }
            emptyList()
        }
    }

    fun applyBasicBuff(attackEffects: List<AttackStatusEffect>): TargetEvaluator {
        return TargetEvaluator {
            for (attackEffect in attackEffects) {
                val state = it.targetState.gainStatusEffect(attackEffect.statusEffect, attackEffect.baseDuration)
                state.linkedSkillId = it.skill
                attackEffect.decorator.invoke(AttackStatusEffectContext(it.sourceState, it.targetState, state, it.skill))
                AttackContext.compose(it.context) { ChatLog.statusEffectGained(it.targetState.name, attackEffect.statusEffect, it.context) }
            }
            emptyList()
        }
    }


    private fun applyBasicPhysicalMagic(
        numHits: Int = 1,
        attackEffects: AttackEffects = AttackEffects(),
        potencyFn: SpellPotencyFn,
    ): TargetEvaluator {
        return TargetEvaluator {
            applyBasicOffenseMagic(
                context = it,
                attackStat = CombatStat.str,
                defendStat = CombatStat.vit,
                numHits = numHits,
                attackEffects = attackEffects,
                potencyFn = potencyFn,
                damageType = AttackDamageType.Physical,
            )
        }
    }

    private fun applyBasicOffenseMagic(
        attackStat: CombatStat = CombatStat.int,
        defendStat: CombatStat = attackStat,
        numHits: Int = 1,
        potency: Float = 1f,
        attackEffects: AttackEffects = AttackEffects(),
        damageCap: Int? = null,
    ): TargetEvaluator {
        return TargetEvaluator {
            applyBasicOffenseMagic(
                context = it,
                attackStat = attackStat,
                defendStat = defendStat,
                numHits = numHits,
                attackEffects = attackEffects,
                damageCap = damageCap,
                potencyFn = { potency })
        }
    }

    private fun applyBasicOffenseMagic(
        attackStat: CombatStat = CombatStat.int,
        defendStat: CombatStat = attackStat,
        numHits: Int = 1,
        attackEffects: AttackEffects = AttackEffects(),
        damageCap: Int? = null,
        potencyFn: SpellPotencyFn,
    ): TargetEvaluator {
        return TargetEvaluator {
            applyBasicOffenseMagic(
                context = it,
                attackStat = attackStat,
                defendStat = defendStat,
                damageType = AttackDamageType.Magical,
                numHits = numHits,
                attackEffects = attackEffects,
                damageCap = damageCap,
                potencyFn = potencyFn,
            )
        }
    }

    private fun applyBasicOffenseMagic(
        context: TargetEvaluatorContext,
        attackStat: CombatStat,
        defendStat: CombatStat = attackStat,
        damageType: AttackDamageType = AttackDamageType.Magical,
        numHits: Int = 1,
        attackEffects: AttackEffects = AttackEffects(),
        damageCap: Int? = null,
        potencyFn: SpellPotencyFn,
    ): List<Event> {
        return spellResultToEvents(
            context, SpellDamageCalculator.computeDamage(
                potencyFn = potencyFn,
                skill = context.skill,
                attacker = context.sourceState,
                defender = context.targetState,
                originalTarget = context.primaryTargetState,
                attackStat = attackStat,
                defendStat = defendStat,
                numHits = numHits,
                damageCap = damageCap,
                context = context.context,
                damageType = damageType,
            ), attackEffects = attackEffects
        )
    }

    fun spellResultToEvents(
        context: TargetEvaluatorContext,
        spellResult: OffenseMagicResult,
        attackEffects: AttackEffects = AttackEffects(),
    ): List<Event> {
        val outputEvents = ArrayList<Event>()

        outputEvents += ActorAttackedEvent(
            sourceId = context.sourceState.id,
            targetId = context.targetState.id,
            damageAmount = spellResult.damage,
            damageType = spellResult.damageType,
            sourceTpGain = spellResult.sourceTpGained,
            targetTpGain = spellResult.targetTpGained,
            attackEffects = attackEffects,
            skill = context.skill,
            actionContext = context.context,
        )

        return outputEvents
    }

    private fun registerHoly(spellSkillId: SpellSkillId) {
        spellMetadata[spellSkillId] = V0Skill(
            castTime = 3.seconds,
            cost = V0AbilityCost(baseCost = AbilityCost(AbilityCostType.Mp, 10), consumesAll = false),
        )
        SkillAppliers[spellSkillId] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(CombatStat.mnd, potency = 2.5f))
    }

    private fun registerDivineMagic(spellSkillId: SpellSkillId, potency: Float = 1.5f) {
        spellMetadata[spellSkillId] = V0Skill(
            castTime = 1.5.seconds,
            cost = V0AbilityCost(baseCost = AbilityCost(AbilityCostType.Mp, 10), consumesAll = false),
        )
        SkillAppliers[spellSkillId] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(CombatStat.mnd, potency = potency))
    }

    private fun registerDivineAgaMagic(spellSkillId: SpellSkillId) {
        spellMetadata[spellSkillId] = V0Skill(
            castTime = 1.5.seconds,
            cost = V0AbilityCost(baseCost = AbilityCost(AbilityCostType.Mp, 15), consumesAll = false),
            rangeInfo = standardStaticTargetAoe
        )

        SkillAppliers[spellSkillId] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(CombatStat.mnd, potency = 1.8f))
    }


    private fun registerElementalMagic(spellSkillId: SpellSkillId) {
        spellMetadata[spellSkillId] = V0Skill(
            castTime = 2.seconds,
            cost = V0AbilityCost(baseCost = AbilityCost(AbilityCostType.Mp, 10), consumesAll = false),
        )
        SkillAppliers[spellSkillId] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(CombatStat.int, potency = 2f))
    }

    private fun registerAgaElementalMagic(spellSkillId: SpellSkillId) {
        spellMetadata[spellSkillId] = V0Skill(
            castTime = 2.seconds,
            cost = V0AbilityCost(baseCost = AbilityCost(AbilityCostType.Mp, 15), consumesAll = false),
            rangeInfo = standardStaticTargetAoe
        )

        SkillAppliers[spellSkillId] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(CombatStat.int, potency = 2.5f))
    }

    private fun registerAraElementalMagic(spellSkillId: SpellSkillId) {
        spellMetadata[spellSkillId] = V0Skill(
            castTime = 2.seconds,
            cost = V0AbilityCost(baseCost = AbilityCost(AbilityCostType.Mp, 15), consumesAll = false),
            rangeInfo = standardSourceRange
        )
        SkillAppliers[spellSkillId] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(CombatStat.int, potency = 2.5f))
    }

    private fun registerAncientMagic(spellSkillId: SpellSkillId) {
        spellMetadata[spellSkillId] = V0Skill(
            castTime = 5.seconds,
            cost = V0AbilityCost(baseCost = AbilityCost(AbilityCostType.Mp, 0), consumesAll = false),
            recast = V0Recast(20.seconds),
        )
        SkillAppliers[spellSkillId] = SkillApplier(targetEvaluator = applyBasicOffenseMagic(potency = 4f))
    }

    private fun levelScalingPotency(basePotency: Float, levelFn: (Int) -> Float): SpellPotencyFn {
        return SpellPotencyFn { basePotency + levelFn.invoke(it.attacker.getMainJobLevel().level) }
    }

}