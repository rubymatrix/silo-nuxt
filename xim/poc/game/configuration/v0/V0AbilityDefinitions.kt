package xim.poc.game.configuration.v0

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.CombatStat
import xim.poc.game.GameEngine.displayName
import xim.poc.game.SkillChainAttribute
import xim.poc.game.StatusEffect
import xim.poc.game.actor.components.getEquipment
import xim.poc.game.configuration.*
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluator.Companion.compose
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluator.Companion.noop
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluatorContext
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.basicMagicalWs
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.basicPhysicalDamage
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.expireTargetDebuffs
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.extendedSourceRange
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.standardSingleTargetRange
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.standardSourceRange
import xim.poc.game.configuration.v0.V0SpellDefinitions.applyBasicBuff
import xim.poc.game.configuration.v0.V0SpellDefinitions.mndHealingMagic
import xim.poc.game.configuration.v0.pet.PetWyvernBehavior
import xim.poc.game.configuration.v0.pet.WyvernBreathCommand
import xim.poc.game.event.*
import xim.poc.ui.ChatLog
import xim.poc.ui.ShiftJis
import xim.resource.*
import xim.resource.table.AbilityInfoTable
import xim.resource.table.AbilityNameTable
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class V0AbilityCost(val baseCost: AbilityCost, val consumesAll: Boolean = false)

data class V0Recast(val baseTime: Duration, val fixedTime: Boolean = false)

data class V0Skill(
    val cost: V0AbilityCost? = null,
    val skillChainAttributes: List<SkillChainAttribute> = emptyList(),
    val rangeInfo: SkillRangeInfo? = null,
    val castTime: Duration? = null,
    val recast: V0Recast? = null,
    val movementLockOverride: Int? = null,
    val description: String? = null,
    val actionLockTime: Duration = 1.seconds,
)

object V0AbilityDefinitions {

    private val abilityMetadata = HashMap<SkillId, V0Skill>()

    fun register() {
        SkillAppliers[rangedAttack] = SkillApplier(
            validEvaluator = {
                val castingState = it.sourceState.getCastingState() ?: return@SkillApplier null

                val currentRanged = it.sourceState.getEquipment(EquipSlot.Range)
                val rangedMatch = currentRanged?.internalId == castingState.context.rangedItemId

                val currentAmmo = it.sourceState.getEquipment(EquipSlot.Ammo)
                val ammoMatch = currentAmmo?.internalId == castingState.context.ammoItemId

                if (rangedMatch && ammoMatch) { null } else { SkillFailureReason.GenericFailure }
            },
            targetEvaluator = {
                listOf(AutoAttackEvent(sourceId = it.sourceState.id, targetId = it.targetState.id, rangedAttack = true))
            },
        )

        // Dagger skills
        abilityMetadata[skillCyclone_20] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 300), consumesAll = false),
            skillChainAttributes = listOf(SkillChainAttribute.Compression, SkillChainAttribute.Detonation),
            rangeInfo = SkillRangeInfo(maxTargetDistance = 30f, effectRadius = 6f, type = AoeType.Target),
            description = "Single-hit long-ranged magic attack.\nPower: 2.5"
        )
        SkillAppliers[skillCyclone_20] = SkillApplier(
            targetEvaluator = basicMagicalWs { 2.5f }
        )

        // Sword skills
        abilityMetadata[skillFastBlade_32] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 300)),
            skillChainAttributes = listOf(SkillChainAttribute.Scission),
            description = "Two-hit attack.\nPower: 1.0"
        )
        SkillAppliers[skillFastBlade_32] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 2))

        abilityMetadata[skillBurningBlade_33] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 300)),
            skillChainAttributes = listOf(SkillChainAttribute.Liquefaction),
            description = "Single-hit magic attack.\nPower: 2.5"
        )
        SkillAppliers[skillBurningBlade_33] = SkillApplier(targetEvaluator = magicSwordWs(numHits = 1, attackStat = CombatStat.int, defendStat = CombatStat.int) { 2.5f })

        abilityMetadata[skillRedLotusBlade_34] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 300), consumesAll = true),
            skillChainAttributes = listOf(SkillChainAttribute.Detonation, SkillChainAttribute.Liquefaction),
            description = "Single-hit magic attack.\nBase power: 2.5\nPower Scaling: +1.0 per 300TP"
        )
        SkillAppliers[skillRedLotusBlade_34] = SkillApplier(targetEvaluator = magicSwordWs(numHits = 1, attackStat = CombatStat.int, defendStat = CombatStat.int) { 2.5f + 1f * it.excessTp })

        abilityMetadata[skillShiningBlade_36] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 300)),
            skillChainAttributes = listOf(SkillChainAttribute.Transfixion, SkillChainAttribute.Scission),
            description = "Two-hit magic attack.\nPower: 1.0"
        )
        SkillAppliers[skillShiningBlade_36] = SkillApplier(
            targetEvaluator = magicSwordWs(numHits = 2, attackStat = CombatStat.mnd, defendStat = CombatStat.mnd)
        )

        abilityMetadata[skillSeraphBlade_37] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 300), consumesAll = true),
            skillChainAttributes = listOf(SkillChainAttribute.Detonation, SkillChainAttribute.Liquefaction),
            movementLockOverride = 110,
            description = "Two-hit magic attack.\nBase power: 2.5 (first hit only)\nPower Scaling: +2.0 per 300TP (first hit only)"
        )
        SkillAppliers[skillSeraphBlade_37] = SkillApplier(targetEvaluator = magicSwordWs(
            numHits = 2, attackStat = CombatStat.int, defendStat = CombatStat.int) { 2.5f + 2f * it.excessTp }
        )

        abilityMetadata[skillCircleBlade_38] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 300), consumesAll = false),
            skillChainAttributes = listOf(SkillChainAttribute.Reverberation, SkillChainAttribute.Impaction),
            rangeInfo = SkillRangeInfo(maxTargetDistance = 30f, effectRadius = 6f, type = AoeType.Target),
            description = "Single-hit long-ranged attack.\nPower: 2.5"
        )
        SkillAppliers[skillCircleBlade_38] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 2.5f }
        )

        abilityMetadata[skillSpiritsWithin_39] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 600)),
            skillChainAttributes = listOf(SkillChainAttribute.Fusion, SkillChainAttribute.Liquefaction),
            movementLockOverride = 100,
            description = "Three-hit magic attack.\nPower: 6.0 (first hit only)"
        )
        SkillAppliers[skillSpiritsWithin_39] = SkillApplier(targetEvaluator = magicSwordWs(
            numHits = 3,
            ftpSpread = false,
            attackStat = CombatStat.mnd,
            defendStat = CombatStat.mnd,
        ) { 6f })

        abilityMetadata[skillVorpalBlade_40] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 300), consumesAll = true),
            skillChainAttributes = listOf(SkillChainAttribute.Detonation, SkillChainAttribute.Liquefaction),
            movementLockOverride = 110,
            description = "Four-hit physical attack.\nBase power: 1.0\nPower Scaling: +2.0 per 300TP (first hit only)"
        )
        SkillAppliers[skillVorpalBlade_40] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 4) { 1f + 2f * it.excessTp })

        abilityMetadata[skillSwiftBlade_41] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 600)),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation, SkillChainAttribute.Scission),
            movementLockOverride = 115,
            description = "Three-hit attack.\nPower: 2.0"
        )
        SkillAppliers[skillSwiftBlade_41] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 2f }
        )

        abilityMetadata[skillSavageBlade_42] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 1000), consumesAll = true),
            skillChainAttributes = listOf(SkillChainAttribute.Fragmentation, SkillChainAttribute.Scission),
            description = "Two-hit attack\nPower: 8.0 (first hit only)\nPower Scaling: +12.0 per 1000TP (first hit only)"
        )
        SkillAppliers[skillSavageBlade_42] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 2, ftpSpread = false) { 8f + 12f * it.excessTp }
        )

        AbilityInfoTable.mutate(skillMistralAxe_71) { it.copy(lowResIconId = 46, hiResIconId = 598) }
        AbilityNameTable.mutate(skillMistralAxe_71.id, listOf("Mistral Blade"))
        abilityMetadata[skillMistralAxe_71] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 600)),
            skillChainAttributes = listOf(SkillChainAttribute.Distortion, SkillChainAttribute.Compression),
            description = "Single-hit long-ranged attack.\nPower: 7.0",
            rangeInfo = standardSingleTargetRange,
            movementLockOverride = 105,
        )
        SkillAppliers[skillMistralAxe_71] = SkillApplier(targetEvaluator = basicPhysicalDamage { 7f })

        AbilityInfoTable.mutate(skillBoraAxe_75) { it.copy(lowResIconId = 46, hiResIconId = 598) }
        AbilityNameTable.mutate(skillBoraAxe_75.id, listOf("Bora Blade"))
        abilityMetadata[skillBoraAxe_75] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 600)),
            skillChainAttributes = listOf(SkillChainAttribute.Distortion, SkillChainAttribute.Impaction),
            description = "Single-hit long-ranged magic attack.\nPower: 8.0",
            rangeInfo = standardSingleTargetRange,
            movementLockOverride = 105,
        )
        SkillAppliers[skillBoraAxe_75] = SkillApplier(targetEvaluator = basicMagicalWs { 8f })

        AbilityInfoTable.mutate(skillCloudsplitter_76) { it.copy(lowResIconId = 44, hiResIconId = 600) }
        abilityMetadata[skillCloudsplitter_76] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 1000), consumesAll = true),
            skillChainAttributes = listOf(SkillChainAttribute.Fragmentation, SkillChainAttribute.Impaction),
            description = "Single-hit magic attack.\nBase power: 12.0\nPower Scaling: +8.0 per 1000TP"
        )
        SkillAppliers[skillCloudsplitter_76] = SkillApplier(targetEvaluator = basicMagicalWs { 12f + 8f * it.excessTp })

        AbilityInfoTable.mutate(skillFlashNova_172) { it.copy(lowResIconId = 47, hiResIconId = 601) }
        abilityMetadata[skillFlashNova_172] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 600), consumesAll = false),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation, SkillChainAttribute.Scission),
            description = "Single-hit magic attack.\nBase power: 8.0",
            movementLockOverride = 90,
        )
        SkillAppliers[skillFlashNova_172] = SkillApplier(targetEvaluator = basicMagicalWs { 8f })

        abilityMetadata[skillChainAffinity_606] = V0Skill(
            recast = V0Recast(baseTime = 60.seconds),
            movementLockOverride = 60,
            description = "Enhances the potency of the next \"physical\" Blue Magic spell.",
            actionLockTime = 0.3.seconds,
        )
        SkillAppliers[skillChainAffinity_606] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.ChainAffinity, duration = 60.seconds) {
            applyAffinityBonus(it)
        })

        abilityMetadata[skillBurstAffinity_607] = V0Skill(
            recast = V0Recast(baseTime = 60.seconds),
            movementLockOverride = 60,
            description = "Enhances the potency of the next \"magical\" Blue Magic spell.",
            actionLockTime = 0.3.seconds,
        )
        SkillAppliers[skillBurstAffinity_607] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.BurstAffinity, duration = 60.seconds) {
            applyAffinityBonus(it)
        })

        // Great Axe skills
        abilityMetadata[skillKeenEdge_84] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 600)),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation, SkillChainAttribute.Impaction),
            description = "Single-hit attack.\nPower: 6.0"
        )
        SkillAppliers[skillKeenEdge_84] = SkillApplier(targetEvaluator = basicPhysicalDamage { 6f })

        abilityMetadata[skillRagingRush_86] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 600)),
            skillChainAttributes = listOf(SkillChainAttribute.Fragmentation, SkillChainAttribute.Scission),
            description = "Three-hit attack.\nPower: 1.5"
        )
        SkillAppliers[skillRagingRush_86] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 1.5f }
        )

        abilityMetadata[skillMetatronTorment_89] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 1000), consumesAll = true),
            skillChainAttributes = listOf(SkillChainAttribute.Fusion, SkillChainAttribute.Transfixion),
            description = "Single-hit attack.\nBase power: 8.0\nPower Scaling: +6.0 per 1000TP"
        )
        SkillAppliers[skillMetatronTorment_89] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 8f + 6f * it.excessTp }
        )

        abilityMetadata[skillSteelCyclone_88] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 1000), consumesAll = true),
            skillChainAttributes = listOf(SkillChainAttribute.Distortion, SkillChainAttribute.Detonation),
            description = "Single-hit attack\nPower: 6.0\nPower Scaling: +12.0 per 1000TP"
        )
        SkillAppliers[skillSteelCyclone_88] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 6f + 12f * it.excessTp }
        )

        abilityMetadata[skillFellCleave_91] = V0Skill(
            rangeInfo = standardSourceRange,
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 1000), consumesAll = false),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation, SkillChainAttribute.Impaction),
            description = "Single-hit attack.\nPower: 12.0"
        )
        SkillAppliers[skillFellCleave_91] = SkillApplier(targetEvaluator = basicPhysicalDamage { 12f })

        abilityMetadata[skillBerserk_543] = V0Skill(
            recast = V0Recast(baseTime = 30.seconds),
            description = "Berserk (5m): +50% STR, -50% VIT"
        )
        SkillAppliers[skillBerserk_543] = SkillApplier(targetEvaluator = applyBasicBuff(
            statusEffect = StatusEffect.Berserk, duration = 5.minutes
        ))

        abilityMetadata[skillBloodRage_779] = V0Skill(
            recast = V0Recast(baseTime = 30.seconds),
            description = "\"Blood Rage\" (3m)\nConsumes \"Retaliation\" stacks.\n1 stack ${ShiftJis.rightArrow} +0.5% Max HP\n1 stack ${ShiftJis.rightArrow} heals 0.5% HP"
        )
        SkillAppliers[skillBloodRage_779] = SkillApplier(targetEvaluator = convertRetaliation(newEffect = StatusEffect.BloodRage, newEffectDuration = 3.minutes) { context, counter ->
            CombatBonusAggregator.clear(context.sourceState)
            val amount = (0.5f * GameV0.getActorCombatStats(context.sourceState.id).maxHp * (counter/100f)).roundToInt()
            listOf(ActorHealedEvent(sourceId = context.sourceState.id, targetId = context.sourceState.id, amount = amount, actionContext = context.context))
        })

        abilityMetadata[skillWarriorsCharge_661] = V0Skill(
            recast = V0Recast(baseTime = 60.seconds),
            description = "\"Warrior's Charge\" (1m)\nConsumes \"Retaliation\" stacks.\n1 stack ${ShiftJis.rightArrow} +5 TP Bonus, +0.5 Critical Hit Rate"
        )
        SkillAppliers[skillWarriorsCharge_661] = SkillApplier(targetEvaluator = convertRetaliation(newEffect = StatusEffect.WarriorsCharge, newEffectDuration = 1.minutes) { _, _ -> emptyList() })

        // H2H skills
        abilityMetadata[skillHowlingFist_7] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 600)),
            skillChainAttributes = listOf(SkillChainAttribute.Distortion, SkillChainAttribute.Transfixion),
            movementLockOverride = 90,
            description = "Two-hit attack.\nPower: 3.0"
        )
        SkillAppliers[skillHowlingFist_7] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 2, ftpSpread = true) { 3.0f }
        )

        abilityMetadata[skillFinalHeaven_10] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 1000), consumesAll = true),
            skillChainAttributes = listOf(SkillChainAttribute.Fusion, SkillChainAttribute.Fragmentation),
            description = "Two-hit attack.\nBase power: 4.0\nPower Scaling: +3.0 per 1000TP"
        )
        SkillAppliers[skillFinalHeaven_10] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 2, ftpSpread = true) { 4f + 3f * it.excessTp }
        )

        abilityMetadata[skillTornadoKick_13] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 1000), consumesAll = true),
            skillChainAttributes = listOf(SkillChainAttribute.Fragmentation, SkillChainAttribute.Detonation),
            movementLockOverride = 100,
            description = "Three-hit attack\nPower: 2.5\nPower Scaling: +3.5 per 1000TP"
        )
        SkillAppliers[skillTornadoKick_13] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 3, ftpSpread = true) { 2.5f + 3.5f * it.excessTp }
        )

        abilityMetadata[skillShijinSpiral_15] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 600), consumesAll = false),
            skillChainAttributes = listOf(SkillChainAttribute.Light, SkillChainAttribute.Fusion),
            movementLockOverride = 40,
            description = "Five-hit attack.\nPower: 1.5"
        )
        SkillAppliers[skillShijinSpiral_15] = SkillApplier(targetEvaluator = basicPhysicalDamage(numHits = 5, ftpSpread = true) { 2.5f })

        abilityMetadata[skillChakra_550] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Tp, value = 1000)),
            recast = V0Recast(baseTime = 5.seconds),
            movementLockOverride = 60,
            description = "Healing power: 1.5\nRemoves 1 debuff."
        )
        SkillAppliers[skillChakra_550] = SkillApplier(targetEvaluator = compose(
            mndHealingMagic(potency = 1.5f),
            expireTargetDebuffs(maxToExpire = 1),
        ))

        abilityMetadata[skillDodge_549] = V0Skill(
            cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Tp, value = 1000)),
            recast = V0Recast(baseTime = 15.seconds),
            movementLockOverride = 60,
            description = "Blink: Gain 3 shadows.\nEach shadow blocks one single-target attack.\nAuto-Attack block-rate: 50%\nSkill block-rate: 100%"
        )
        SkillAppliers[skillDodge_549] = SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Blink) {
            it.status.counter = 3
        })

        // Polearm Skills
        abilityMetadata[skillWheelingThrust_119] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 600)),
            skillChainAttributes = listOf(SkillChainAttribute.Fusion),
            description = "Single-hit attack.\nPower: 6.0"
        )
        SkillAppliers[skillWheelingThrust_119] = SkillApplier(targetEvaluator = basicPhysicalDamage { 6f })

        abilityMetadata[skillImpulseDrive_120] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 600)),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation, SkillChainAttribute.Induration),
            description = "Two-hit attack.\nPower: 2.5"
        )
        SkillAppliers[skillImpulseDrive_120] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 2, ftpSpread = true) { 2.5f }
        )

        abilityMetadata[skillSonicThrust_123] = V0Skill(
            rangeInfo = standardSingleTargetRange,
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 1000), consumesAll = true),
            skillChainAttributes = listOf(SkillChainAttribute.Fusion, SkillChainAttribute.Scission),
            description = "Single-hit attack\nPower: 6.0\nPower Scaling: +12.0 per 1000TP"
        )
        SkillAppliers[skillSonicThrust_123] = SkillApplier(
            targetEvaluator = basicPhysicalDamage { 6f + 12f * it.excessTp }
        )

        abilityMetadata[skillStardiver_125] = V0Skill(
            cost = V0AbilityCost(AbilityCost(AbilityCostType.Tp, 1000), consumesAll = true),
            skillChainAttributes = listOf(SkillChainAttribute.Gravitation, SkillChainAttribute.Transfixion),
            description = "Four-hit attack.\nBase power: 2.0\nPower Scaling: +2.0 per 1000TP",
            actionLockTime = 2.5.seconds,
        )
        SkillAppliers[skillStardiver_125] = SkillApplier(
            targetEvaluator = basicPhysicalDamage(numHits = 4, ftpSpread = true) { 2f + 2f * it.excessTp }
        )

        abilityMetadata[skillSmitingBreath_830] = V0Skill(
            recast = V0Recast(baseTime = 15.seconds),
            movementLockOverride = 0,
            rangeInfo = SkillRangeInfo(maxTargetDistance = 4f, type = AoeType.None, effectRadius = 0f),
            description = "Orders the wyvern to attack with its breath.\nPower: scales with level"
        )
        SkillAppliers[skillSmitingBreath_830] = SkillApplier(targetEvaluator = { wyvernBreath(it, it.targetState.id) })

        abilityMetadata[skillRestoringBreath_831] = V0Skill(
            recast = V0Recast(baseTime = 30.seconds),
            movementLockOverride = 0,
            description = "Orders the wyvern to heal with its breath.\nHealing power: 1.5\nRemoves 2 debuffs."
        )
        SkillAppliers[skillRestoringBreath_831] = SkillApplier(targetEvaluator = { wyvernBreath(it, it.sourceState.pet) })

        // Wyvern breaths
        abilityMetadata[skillHealingBreath_1152] = V0Skill(rangeInfo = extendedSourceRange)
        SkillAppliers[skillHealingBreath_1152] = SkillApplier(targetEvaluator = compose(
            mndHealingMagic(potency = 1.5f),
            expireTargetDebuffs(maxToExpire = 2),
        ))

        for (breathId in PetWyvernBehavior.elementalBreaths) {
            abilityMetadata[breathId] = V0Skill(cost = V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Tp, value = 0)))
            SkillAppliers[breathId] = SkillApplier(targetEvaluator = noop())
        }
    }

    fun getCost(skill: SkillId): V0AbilityCost {
        return abilityMetadata[skill]?.cost ?: V0AbilityCost(AbilityCost(AbilityCostType.Tp, 0))
    }

    fun getRecast(skill: SkillId): V0Recast {
        return abilityMetadata[skill]?.recast ?: V0Recast(5.seconds)
    }

    fun getRange(abilitySkillId: AbilitySkillId): SkillRangeInfo? {
        return abilityMetadata[abilitySkillId]?.rangeInfo
    }

    fun getSkillChainAttributes(skill: SkillId): List<SkillChainAttribute> {
        return abilityMetadata[skill]?.skillChainAttributes ?: emptyList()
    }

    fun getMovementLock(skill: SkillId): Int? {
        return abilityMetadata[skill]?.movementLockOverride
    }

    fun getActionLock(skill: SkillId): Duration {
        return abilityMetadata[skill]?.actionLockTime ?: 1.seconds
    }

    fun weaponSkillToEvents(
        context: TargetEvaluatorContext,
        wsResult: WeaponSkillDamageResult,
        damageType: AttackDamageType,
        attackEffects: AttackEffects = AttackEffects(),
    ): List<Event> {
        return listOf(ActorAttackedEvent(
            sourceId = context.sourceState.id,
            targetId = context.targetState.id,
            damageAmount = wsResult.damage,
            damageType = damageType,
            sourceTpGain = wsResult.sourceTpGained,
            targetTpGain = wsResult.targetTpGained,
            attackEffects = attackEffects,
            skill = context.skill,
            actionContext = context.context,
        ))
    }

    fun magicSwordWs(
        numHits: Int,
        attackStat: CombatStat,
        defendStat: CombatStat = attackStat,
        ftpSpread: Boolean = false,
        attackEffects: AttackEffects = AttackEffects(),
        ftp: TpScalingFn = TpScalingFn { 1f },
    ): SkillApplierHelper.TargetEvaluator {
        return magicSwordWs(numHits = { numHits }, attackStat = attackStat, defendStat = defendStat, ftpSpread = ftpSpread, attackEffects = attackEffects, ftp = ftp)
    }

    fun magicSwordWs(
        numHits: (TargetEvaluatorContext) -> Int = { 1 },
        attackStat: CombatStat,
        defendStat: CombatStat = attackStat,
        ftpSpread: Boolean = false,
        attackEffects: AttackEffects = AttackEffects(),
        ftp: TpScalingFn = TpScalingFn { 1f },
    ): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            val wsResult = WeaponSkillDamageCalculator.magicalSwordSkill(
                skill = it.skill,
                attacker = it.sourceState,
                defender = it.targetState,
                numHits = numHits.invoke(it),
                ftpSpread = ftpSpread,
                attackStat = attackStat,
                defendStat = defendStat,
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

    private fun convertRetaliation(newEffect: StatusEffect, newEffectDuration: Duration, also: (TargetEvaluatorContext, Int) -> List<Event>): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            val retaliationCounter = it.sourceState.getStatusEffect(StatusEffect.Retaliation)?.counter ?: 0
            if (retaliationCounter == 0) { return@TargetEvaluator emptyList()  }
            it.sourceState.expireStatusEffect(StatusEffect.Retaliation)

            val effect = it.sourceState.gainStatusEffect(newEffect, duration = newEffectDuration, sourceId = it.sourceState.id)
            effect.linkedSkillId = it.skill
            effect.potency = retaliationCounter
            ChatLog.statusEffectGained(it.sourceState.name, newEffect, it.context)

            also(it, retaliationCounter)
        }
    }

    private fun wyvernBreath(it: TargetEvaluatorContext, targetId: ActorId?): List<Event> {
        targetId ?: return emptyList()

        val pet = ActorStateManager[it.sourceState.pet] ?: return emptyList()
        if (pet.behaviorController !is PetWyvernBehavior) { return emptyList() }

        pet.behaviorController.enqueueBreath(WyvernBreathCommand(it.skill, targetId))
        return emptyList()
    }

    private fun applyAffinityBonus(it: StatusEffectContext) {
        val levelBonus = (it.context.sourceState.getMainJobLevel().level - 15).coerceAtLeast(0) * 5
        val affinityBonus = 25 + levelBonus
        it.status.potency = affinityBonus
    }

    fun getMagicBurstElement(skill: SkillId): SpellElement? {
        return when (skill) {
            skillFlameBreath_1158 -> SpellElement.Fire
            skillFrostBreath_1159 -> SpellElement.Ice
            skillGustBreath_1160 -> SpellElement.Wind
            skillSandBreath_1161 -> SpellElement.Earth
            skillLightningBreath_1162 -> SpellElement.Lightning
            skillHydroBreath_1163 -> SpellElement.Water
            else -> null
        }
    }

    fun toDescription(skill: SkillId): String {
        val ability = abilityMetadata[skill] ?: return ""

        val player = ActorStateManager.player()
        val baseCost = GameV0.getSkillBaseCost(player, skill)
        val consumesAllIndicator = if (ability.cost?.consumesAll == true) { "+" } else { "" }
        val costDisplay = "${baseCost.type.name.uppercase()}: ${baseCost.value}${consumesAllIndicator}"

        val description = ability.description?.prependIndent("  ") ?: "TBA"
        val skillChainAttributes = ability.skillChainAttributes.joinToString(", ")

        val skillName = skill.displayName()

        return listOf(skillName, description, skillChainAttributes, costDisplay)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

}