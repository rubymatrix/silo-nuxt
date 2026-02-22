package xim.poc.game.configuration.v0

import xim.poc.game.*
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.getEquipment
import xim.poc.game.actor.components.isDualWield
import xim.poc.game.actor.components.isHandToHand
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.DamageCalculator.computeTpGained
import xim.poc.game.configuration.v0.DamageCalculator.getCriticalDamageMultiplier
import xim.poc.game.configuration.v0.DamageCalculator.getWeaponMagicPower
import xim.poc.game.configuration.v0.DamageCalculator.getWeaponPowerAndDelay
import xim.poc.game.configuration.v0.DamageCalculator.processWeaponSwing
import xim.poc.game.configuration.v0.DamageCalculator.rollCriticalHit
import xim.poc.game.configuration.v0.DamageCalculator.rollWeaponSkillRounds
import xim.poc.game.configuration.v0.ItemAugmentDefinitions.rankProportion
import xim.poc.game.configuration.v0.SpellDamageCalculator.computeAttackAddedEffectResult
import xim.poc.game.configuration.v0.SpellDamageCalculator.computeOccConvertsDamageToMpResult
import xim.poc.game.configuration.v0.SpellDamageCalculator.computeSpikeDamage
import xim.poc.game.configuration.v0.SpellDamageCalculator.getResistanceMultiplier
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.event.*
import xim.poc.game.event.ActorAttackedHelper.consumeStoneskin
import xim.resource.AbilityCostType
import xim.resource.AbilityType
import xim.resource.EquipSlot
import xim.resource.MagicType
import xim.resource.table.AbilityInfoTable.toAbilityInfo
import xim.resource.table.SpellInfoTable.toSpellInfo
import xim.util.interpolate
import kotlin.math.roundToInt
import kotlin.random.Random

data class TpScalingContext(
    val excessTp: Float,
    val bonus: CombatBonusAggregate,
    val attacker: ActorState,
    val defender: ActorState,
)

fun interface TpScalingFn {
    fun invoke(context: TpScalingContext): Float
}

data class SpellContext(
    val attacker: ActorState,
    val defender: ActorState,
)

fun interface SpellPotencyFn {
    fun invoke(context: SpellContext): Float
}

data class WeaponSkillDamageResult(
    val damage: List<Int>,
    val sourceTpGained: Int,
    val targetTpGained: Int,
)

data class OffenseMagicResult(
    val damage: List<Int>,
    val sourceTpGained: Int,
    val targetTpGained: Int,
    val damageType: AttackDamageType,
)

object DamageCalculator {

    fun getBaseWeaponPowerAndDelay(actor: ActorState, type: AutoAttackType): Pair<Int,Int>? {
        return if (actor.behaviorController is V0MonsterController) {
            actor.behaviorController.getWeapon()
        } else if (actor.monsterId != null) {
            val monsterDefinition = MonsterDefinitions[actor.monsterId]
            monsterDefinition.baseDamage to monsterDefinition.baseDelay
        } else if (actor.type == ActorType.Pc) {
            val item = getWeapon(actor, type)
            if (item != null) {
                val itemDefinition = ItemDefinitions[item]
                getWeaponDamage(item) to itemDefinition.delay
            } else if (type is MainHandAutoAttack) {
                30 to 240
            } else {
                null
            }
        } else {
            null
        }
    }

    fun getWeaponPowerAndDelay(actor: ActorState, type: AutoAttackType): Pair<Float,Int>? {
        val (damage, delay) = getBaseWeaponPowerAndDelay(actor, type) ?: return null
        return adjustWeaponPower(actor, damage, type) to delay
    }


    fun getWeaponMagicPower(actor: ActorState): Float {
        if (actor.type != ActorType.Pc) {
            return getWeaponPowerAndDelay(actor, MainHandAutoAttack())?.first ?: 3f
        }

        val item = getWeapon(actor, MainHandAutoAttack()) ?: return 3f

        val itemDefinition = ItemDefinitions[item]
        val basePower = itemDefinition.magicDamage.interpolate(itemDefinition.maxMagicDamage, rankProportion(item))

        return adjustWeaponPower(actor, basePower, MainHandAutoAttack())
    }

    private fun adjustWeaponPower(actor: ActorState, basePower: Int, type: AutoAttackType): Float {
        val bonuses = CombatBonusAggregator[actor]

        val damageMultiplier = if (rollAgainst(bonuses.doubleDamage)) {
            2f
        } else if (rollAgainst(bonuses[type].occDoubleDamage)) {
            2f
        } else {
            1f
        }

        return 0.1f * damageMultiplier * basePower
    }

    private fun getWeapon(actor: ActorState, type: AutoAttackType): InventoryItem? {
        return when (type) {
            is MainHandAutoAttack -> actor.getEquipment(EquipSlot.Main)
            is OffHandAutoAttack -> actor.getEquipment(EquipSlot.Sub)
            is RangedAutoAttack -> actor.getEquipment(EquipSlot.Range)
        }
    }

    fun getWeaponDamage(item: InventoryItem): Int {
        val itemDefinition = ItemDefinitions[item]
        return itemDefinition.damage.interpolate(itemDefinition.maxDamage, rankProportion(item))
    }

    private fun getAutoAttackDamage(attacker: ActorState, defender: ActorState, weaponPower: Float): Float {
        val ratio = (attacker.combatStats.str.toFloat() / defender.combatStats.vit.toFloat()).coerceIn(0.1f, 10f)

        val attackerBonus = CombatBonusAggregator[attacker]
        val boostBonus = attackerBonus.boost.toMultiplier()

        val defenderBonus = CombatBonusAggregator[defender]
        val physDmgReduction = defenderBonus.physicalDamageTaken.toMultiplier(min = 0f)

        return ratio * weaponPower * physDmgReduction * boostBonus
    }

    fun getHealAmount(caster: ActorState, target: ActorState, potency: Float, maxAmount: Int): Int {
        val casterBonuses = CombatBonusAggregator[caster]
        val baseAmount = (caster.combatStats.mnd * potency).roundToInt().coerceAtMost(maxAmount)
        return (baseAmount * casterBonuses.curePotency.toMultiplier()).roundToInt()
    }

    fun getCriticalDamageMultiplier(attacker: ActorState, defender: ActorState, attackerBonus: CombatBonusAggregate = CombatBonusAggregator[attacker]): Float {
        return 1.15f * attackerBonus.criticalHitDamage.toMultiplier()
    }

    fun rollWeaponSkillRounds(attacker: ActorState, numWsHits: Int): List<AutoAttackType> {
        val rounds = ArrayList<AutoAttackType>()

        for (i in 0 until numWsHits) {
            rounds += (0 until rollAutoAttackRounds(attacker, MainHandAutoAttack(), autoAttack = false)).map { MainHandAutoAttack() }
        }

        if (attacker.isDualWield()) {
            rounds += (0 until rollAutoAttackRounds(attacker, OffHandAutoAttack, autoAttack = false)).map { OffHandAutoAttack }
        }

        return rounds.take(8)
    }

    fun getAutoAttackTypeResult(attacker: ActorState, defender: ActorState, type: AutoAttackType): List<AutoAttackResult> {
        val numHits = rollAutoAttackRounds(attacker, type, autoAttack = true)
        return (0 until numHits).map { processWeaponSwing(attacker, defender, type, autoAttack = true ) }
    }

    fun getH2HAutoAttackResult(attacker: ActorState, defender: ActorState): List<AutoAttackResult> {
        val numHits = rollAutoAttackRounds(attacker, MainHandAutoAttack(), autoAttack = true)
        val bonuses = CombatBonusAggregator[attacker]

        return (0 until numHits).flatMap {
            val roundHits = mutableListOf(MainHandAutoAttack(), MainHandAutoAttack(subType = AutoAttackSubType.H2HOffHand))
            if (rollAgainst(bonuses.kickAttackRate)) { roundHits += MainHandAutoAttack(subType = AutoAttackSubType.H2HKick) }
            roundHits
        }.map { processWeaponSwing(attacker, defender, it, autoAttack = true ) }
    }

    private fun rollAutoAttackRounds(attacker: ActorState, type: AutoAttackType, autoAttack: Boolean): Int {
        val bonuses = CombatBonusAggregator[attacker]

        var numHits = if (rollAgainst(bonuses.quadrupleAttack)) {
            4
        } else if (autoAttack && rollAgainst(bonuses[type].occAttack4x)) {
            4
        } else if (rollAgainst(bonuses.tripleAttack)) {
            3
        } else if (autoAttack && rollAgainst(bonuses[type].occAttack3x)) {
            3
        } else if (rollAgainst(bonuses.doubleAttack)) {
            2
        } else if (autoAttack && rollAgainst(bonuses[type].occAttack2x)) {
            2
        } else {
            1
        }

        if (rollAgainst(bonuses.followUpAttack)) { numHits += 1 }

        return numHits
    }

    fun processWeaponSwing(attacker: ActorState, defender: ActorState, type: AutoAttackType, autoAttack: Boolean): AutoAttackResult {
        val attackerBonuses = CombatBonusAggregator[attacker]
        val defenderBonuses = CombatBonusAggregator[defender]

        val (power, _) = getWeaponPowerAndDelay(attacker, type) ?: Pair(0f, 240)

        val rawDamage = getAutoAttackDamage(attacker, defender, power)

        val criticalHit = autoAttack && rollCriticalHit(attacker, defender)
        val criticalBonus = if (criticalHit) { getCriticalDamageMultiplier(attacker, defender) } else { 1f }

        val autoAttackScale = if (autoAttack) { attackerBonuses.autoAttackScale } else { 1f }

        val damage = (rawDamage * criticalBonus * autoAttackScale).roundToInt()
        val (tpGained, targetTpGained) = computeTpGained(attacker, defender, type, criticalHit = criticalHit)

        val convertsDamageToMp = if (autoAttack && rollAgainst(attackerBonuses[type].occDamageToMp)) {
            computeOccConvertsDamageToMpResult()
        } else {
            null
        }

        val addedEffects = attackerBonuses.autoAttackEffects
            .filter { rollAgainst(it.procChance) }
            .map { computeAttackAddedEffectResult(it) } + listOfNotNull(convertsDamageToMp)

        val retaliationEffects = defenderBonuses.autoAttackRetaliationEffects
            .map { AutoAttackRetaliationEffect(computeSpikeDamage(defender, it), it.type, it.statusEffect) }

        return AutoAttackResult(targetId = defender.id,
            sourceTpGained = tpGained,
            targetTpGained = targetTpGained,
            damageDone = damage,
            criticalHit = criticalHit,
            type = type,
            addedEffects = addedEffects,
            retaliationEffects = retaliationEffects,
        )
    }

    fun computeTpGained(attacker: ActorState, defender: ActorState, type: AutoAttackType, criticalHit: Boolean): Pair<Int, Int> {
        val (_, delay) = getWeaponPowerAndDelay(attacker, type) ?: Pair(0, 240)
        val baseTpGained = delay / 3.0f
        return computeTpGained(attacker, defender, baseTpGained, criticalHit)
    }

    fun computeTpGained(attacker: ActorState, defender: ActorState, baseTpGained: Float, criticalHit: Boolean): Pair<Int, Int> {
        val attackerBonuses = CombatBonusAggregator[attacker]
        val defenderBonuses = CombatBonusAggregator[defender]

        val storeTpPotency = attackerBonuses.storeTp.toMultiplier()
        val criticalHitGain = if (criticalHit) { attackerBonuses.criticalTpGain.toMultiplier() } else { 1f }
        val h2hMultiplier = if (attacker.isHandToHand()) { 0.5f } else { 1f }
        val tpGained = (baseTpGained * criticalHitGain * storeTpPotency * h2hMultiplier).roundToInt()

        val agiMitigation = computeAgiRatio(defender, attacker).coerceIn(0f, 1f)
        val defenderStoreTpPotency = defenderBonuses.storeTp.toMultiplier()
        val attackerSubtleBlow = attackerBonuses.subtleBlow.toPenaltyMultiplier(0.25f, 1f)
        val targetTpGained = (0.5f * baseTpGained * agiMitigation * defenderStoreTpPotency * attackerSubtleBlow).roundToInt()

        return tpGained to targetTpGained
    }

    fun rollCriticalHit(attacker: ActorState, defender: ActorState): Boolean {
        val attackerBonus = CombatBonusAggregator[attacker]
        val criticalHitRateBonus = attackerBonus.criticalHitRate

        val defenderBonus = CombatBonusAggregator[defender]
        val criticalDodgeBonus = defenderBonus.enemyCriticalHitRate

        val ratio = attacker.combatStats.dex.toFloat() / defender.combatStats.agi.toFloat()
        val ratioBonus = (10f * ratio.coerceAtMost(2f)).roundToInt()

        val chance = ratioBonus + criticalHitRateBonus + criticalDodgeBonus
        return rollAgainst(chance)
    }

    fun computeAgiRatio(attacker: ActorState, defender: ActorState): Float {
        return attacker.combatStats.agi.toFloat() / defender.combatStats.agi.toFloat()
    }

    fun computeExcessTp(actorState: ActorState, skill: SkillId): Float {
        val customCost = when (skill) {
            is SpellSkillId -> return 0f
            is MobSkillId -> MobSkills[skill].cost
            is AbilitySkillId -> V0AbilityDefinitions.getCost(skill)
            is ItemSkillId -> return 0f
            is RangedAttackSkillId -> return 0f
        }

        if (!customCost.consumesAll || customCost.baseCost.value == 0) { return 0f }
        if (customCost.baseCost.type != AbilityCostType.Tp) { return 0f }

        val tpBonus = CombatBonusAggregator[actorState].tpBonus

        val calculationTp = (actorState.getTp() + tpBonus).coerceAtMost(GameV0.getMaxTp(actorState))
        val excess = (calculationTp - customCost.baseCost.value).coerceAtLeast(0)
        return (excess.toFloat() / customCost.baseCost.value)
    }

    fun rollSpellInterrupted(attacker: ActorState, defender: ActorState): Boolean {
        val defenderBonus = CombatBonusAggregator[defender]
        val interruptMitigation = defenderBonus.spellInterruptDown.toPenaltyMultiplier()

        val interruptChance = 0.5f * computeAgiRatio(attacker, defender) * interruptMitigation
        return Random.nextFloat() <= interruptChance
    }

    fun rollConserveTp(actorState: ActorState, skill: SkillId): Boolean {
        if (skill !is AbilitySkillId) { return false }

        val abilityInfo = skill.toAbilityInfo()
        if (abilityInfo.type != AbilityType.WeaponSkill) { return false }

        val bonuses = CombatBonusAggregator[actorState]
        return rollAgainst(bonuses.conserveTp)
    }

    fun rollAgainst(odds: Int): Boolean {
        return Random.nextDouble(0.0, 100.0) < odds
    }

}

object WeaponSkillDamageCalculator {

    fun physicalWeaponSkill(
        skill: SkillId,
        attacker: ActorState,
        defender: ActorState,
        numHits: Int = 1,
        ftpSpread: Boolean = false,
        context: AttackContext? = null,
        ftp: TpScalingFn = TpScalingFn { 1f },
    ): WeaponSkillDamageResult {
        val excessTp = DamageCalculator.computeExcessTp(attacker, skill)

        val restraintBonus = (attacker.getStatusEffect(StatusEffect.Restraint)?.counter ?: 0).toMultiplier()

        val rounds = rollWeaponSkillRounds(attacker, numHits)
        val hits = ArrayList<AutoAttackResult>()

        CombatBonusAggregator.shallowBonusScope(attacker) {
            val criticalHit = rollCriticalHit(attacker, defender).also { c -> context?.setCriticalHitFlag(c) }
            val criticalBonus = if (criticalHit) { getCriticalDamageMultiplier(attacker, defender, attackerBonus = it) } else { 1f }

            for (round in rounds.indices) {
                val roundType = rounds[round]

                val ftpResult = if (round == 0 || ftpSpread) {
                    ftp.invoke(TpScalingContext(excessTp = excessTp, bonus = it, attacker = attacker, defender = defender))
                } else {
                    1f
                }

                val baseResult = processWeaponSwing(attacker, defender, roundType, autoAttack = false)

                val wsBonus = it.weaponSkillDamage.toMultiplier()
                val attackDamage = (baseResult.damageDone * criticalBonus * ftpResult * wsBonus * restraintBonus).roundToInt()

                hits += baseResult.copy(damageDone = attackDamage)
            }
        }

        val firstHit = hits.first()
        val hitDamage = hits.map { it.damageDone }

        val baseTpGained = firstHit.sourceTpGained + (numHits - 1) * 10
        val sourceTpGained = applySaveTp(attacker, baseTpGained)

        return WeaponSkillDamageResult(hitDamage, sourceTpGained, firstHit.targetTpGained)
    }

    fun magicalSwordSkill(
        skill: SkillId,
        attacker: ActorState,
        defender: ActorState,
        numHits: Int = 1,
        attackStat: CombatStat,
        defendStat: CombatStat,
        ftpSpread: Boolean = false,
        ftp: TpScalingFn = TpScalingFn { 1f },
    ): WeaponSkillDamageResult {
        val excessTp = DamageCalculator.computeExcessTp(attacker, skill)
        val statRatio = attacker.combatStats[attackStat].toFloat() / defender.combatStats[defendStat].toFloat()

        val rounds = rollWeaponSkillRounds(attacker, numHits)
        val hits = ArrayList<WeaponSkillDamageResult>()

        val restraintBonus = (attacker.getStatusEffect(StatusEffect.Restraint)?.counter ?: 0).toMultiplier()

        for (round in rounds.indices) {
            val roundType = rounds[round]

            hits += CombatBonusAggregator.shallowBonusScope(attacker) {
                val ftpResult = if (round == 0 || ftpSpread) {
                    ftp.invoke(TpScalingContext(excessTp = excessTp, bonus = it, attacker = attacker, defender = defender))
                } else {
                    1f
                }

                val (power, _) = getWeaponPowerAndDelay(attacker, roundType) ?: (1f to 0)

                val criticalHit = rollCriticalHit(attacker, defender)
                val criticalBonus = if (criticalHit) { getCriticalDamageMultiplier(attacker, defender) } else { 1f }

                val wsBonus = (it.weaponSkillDamage + it.elementalWeaponSkillDamage).toMultiplier()
                val magBonus = it.magicAttackBonus.toMultiplier()
                val boostBonus = it.boost.toMultiplier()

                val defenderBonuses = CombatBonusAggregator[defender]
                val defenderMdt = defenderBonuses.magicalDamageTaken.toMultiplier(min = 0f)

                val damage = (power * statRatio * ftpResult * wsBonus * magBonus * boostBonus * defenderMdt * criticalBonus * restraintBonus).roundToInt()
                val (tpGained, targetTpGained) = computeTpGained(attacker, defender, MainHandAutoAttack(), criticalHit = criticalHit)

                WeaponSkillDamageResult(listOf(damage), tpGained, targetTpGained)
            }
        }

        val firstHit = hits.first()
        val hitDamage = hits.flatMap { it.damage }

        val baseTpGained = firstHit.sourceTpGained + (numHits - 1) * 10
        val sourceTpGained = applySaveTp(attacker, baseTpGained)

        return WeaponSkillDamageResult(hitDamage, sourceTpGained, firstHit.targetTpGained)
    }

    fun magicalWeaponSkill(
        skill: SkillId,
        attacker: ActorState,
        defender: ActorState,
        attackStat: CombatStat,
        defendStat: CombatStat,
        damageCap: Int? = null,
        ftp: TpScalingFn = TpScalingFn { 1f },
    ): WeaponSkillDamageResult {
        val (power, _) = getWeaponPowerAndDelay(attacker, MainHandAutoAttack()) ?: (1f to 0)
        val statRatio = attacker.combatStats[attackStat].toFloat() / defender.combatStats[defendStat].toFloat()

        val excessTp = DamageCalculator.computeExcessTp(attacker, skill)

        return CombatBonusAggregator.shallowBonusScope(attacker) { attackerBonus ->
            val tpMultiplier = ftp.invoke(TpScalingContext(excessTp = excessTp, bonus = attackerBonus, attacker = attacker, defender = defender))

            val wsBonus = attackerBonus.weaponSkillDamage.toMultiplier()
            val magBonus = attackerBonus.magicAttackBonus.toMultiplier()
            val elemWsBonus = attackerBonus.elementalWeaponSkillDamage.toMultiplier()
            val boostBonus = attackerBonus.boost.toMultiplier()

            val defenderBonuses = CombatBonusAggregator[defender]
            val defenderMdt = defenderBonuses.magicalDamageTaken.toMultiplier(min = 0f)
            val defenderResistance = getResistanceMultiplier(skill, defenderBonuses)

            val damage = (power * statRatio * tpMultiplier * wsBonus * magBonus * elemWsBonus * boostBonus * defenderMdt * defenderResistance).roundToInt()
                .coerceAtMost(damageCap ?: Int.MAX_VALUE)

            val (tpGained, targetTpGained) = computeTpGained(attacker, defender, MainHandAutoAttack(), criticalHit = false)

            WeaponSkillDamageResult(listOf(damage), tpGained, targetTpGained)
        }
    }

    private fun applySaveTp(attacker: ActorState, baseTpGained: Int): Int {
        val bonus = CombatBonusAggregator[attacker]
        return baseTpGained.coerceAtLeast(bonus.saveTp)
    }

}

object SpellDamageCalculator {

    fun computeDamage(skill: SkillId?,
                      attacker: ActorState,
                      defender: ActorState,
                      damageType: AttackDamageType = AttackDamageType.Magical,
                      originalTarget: ActorState = defender,
                      attackStat: CombatStat,
                      defendStat: CombatStat,
                      numHits: Int = 1,
                      damageCap: Int? = null,
                      context: AttackContext? = null,
                      potencyFn: SpellPotencyFn = SpellPotencyFn { 1f },
    ): OffenseMagicResult {
        val statRatio = attacker.combatStats[attackStat].toFloat() / defender.combatStats[defendStat].toFloat()

        val attackBonuses = CombatBonusAggregator[attacker]
        val boostBonus = attackBonuses.boost.toMultiplier()

        val magBonus = attackBonuses.magicAttackBonus.toMultiplier()

        val defenderBonuses = CombatBonusAggregator[defender]
        val defenderDt = when (damageType) {
            AttackDamageType.Physical -> defenderBonuses.physicalDamageTaken.toMultiplier(min = 0f)
            AttackDamageType.Magical -> defenderBonuses.magicalDamageTaken.toMultiplier(min = 0f)
            else -> 1f
        }

        val restraintBonus = if (damageType == AttackDamageType.Physical) {
            val restraintEffect = attacker.getStatusEffect(StatusEffect.Restraint)?.counter ?: 0
            1f + 0.25f * restraintEffect/100f
        } else {
            1f
        }

        val criticalHit = rollCriticalHit(attacker, defender).also { context?.setCriticalHitFlag(it) }
        val criticalBonus = if (criticalHit) { getCriticalDamageMultiplier(attacker, defender) } else { 1f }

        val spellContext = SpellContext(attacker = attacker, defender = defender)
        val potency = potencyFn.invoke(spellContext)

        val magicBurstBonus = skill?.let { getMagicBurstBonus(attacker, attackBonuses, originalTarget, it) } ?: 1f
        if (magicBurstBonus > 1f) { context?.magicBurst = true }

        val affinityBonus = getAffinityBonus(skill, attacker, damageType)
        val elementalResist = getResistanceMultiplier(skill, defenderBonuses)

        val baseDamage = affinityBonus * restraintBonus * potency * boostBonus * magBonus * statRatio * defenderDt * criticalBonus * magicBurstBonus * elementalResist
        val (_, targetTpGained) = computeTpGained(attacker, defender, baseTpGained = 50f, criticalHit = false)

        val hitDamage = (0 until numHits).map {
            val power = getWeaponMagicPower(attacker)
            (power * baseDamage).roundToInt().coerceAtMost(damageCap ?: Int.MAX_VALUE)
        }

        val sourceTpGained = if (skill is SpellSkillId && skill.toSpellInfo().magicType == MagicType.BlueMagic) {
            CombatBonusAggregator[attacker].blueMagicTpGain
        } else {
            0
        }

        return OffenseMagicResult(
            damage = hitDamage,
            sourceTpGained = sourceTpGained,
            targetTpGained = targetTpGained,
            damageType = damageType,
        )
    }

    fun computeAttackAddedEffectResult(effectBonus: AutoAttackEffect): AutoAttackAddedEffect {
        return AutoAttackAddedEffect(displayType = effectBonus.effectType) {
            computeAttackAddedEffectResultInternal(attackEffect = effectBonus, sourceState = it.attacker, targetState = it.defender, actionContext = it.context)
        }
    }

    fun computeOccConvertsDamageToMpResult(): AutoAttackAddedEffect {
        return AutoAttackAddedEffect(displayType = AttackAddedEffectType.Aspir) {
            listOf(ActorHealedEvent(
                sourceId = it.attacker.id,
                targetId = it.attacker.id,
                amount = 1,
                actionContext = it.context,
                healType = ActorResourceType.MP,
                displayMessage = false,
            ))
        }
    }

    private fun computeAttackAddedEffectResultInternal(attackEffect: AutoAttackEffect, sourceState: ActorState, targetState: ActorState, actionContext: AttackContext): List<Event> {
        val effectSourceBonuses = CombatBonusAggregator[sourceState]

        val magBonus = effectSourceBonuses.magicAttackBonus.toMultiplier()
        val damage = (attackEffect.effectPower * magBonus).roundToInt()

        val finalAddedEffectDamage = consumeStoneskin(targetState, damage, attackEffect.effectType.damageResource)

        val isDrainingEffect = attackEffect.effectType == AttackAddedEffectType.Drain || attackEffect.effectType == AttackAddedEffectType.Aspir

        val statusEffect = attackEffect.statusEffect
        if (statusEffect != null) {
            ActorAttackedHelper.applyStatusEffect(attackStatusEffect = statusEffect, sourceState = sourceState, targetState = targetState, skill = null, actionContext = actionContext)
        }

        val addedEffectEvents = ArrayList<Event>()

        if (damage > 0) {
            addedEffectEvents += ActorDamagedEvent(
                sourceId = sourceState.id,
                targetId = targetState.id,
                type = attackEffect.effectType.damageResource,
                amount = finalAddedEffectDamage,
                actionContext = actionContext,
                damageType = AttackDamageType.Magical,
                additionalEffect = true,
                emitDamageText = !isDrainingEffect,
            )
        }

        if (isDrainingEffect) {
            addedEffectEvents += ActorHealedEvent(
                sourceId = targetState.id,
                targetId = sourceState.id,
                amount = finalAddedEffectDamage,
                actionContext = actionContext,
                healType = attackEffect.effectType.damageResource,
            )
        }

        return addedEffectEvents
    }

    fun computeSpikeDamage(spikeSource: ActorState, retaliationBonus: RetaliationBonus): Int {
        val spikeSourceBonuses = CombatBonusAggregator[spikeSource]
        val magBonus = spikeSourceBonuses.magicAttackBonus.toMultiplier()
        return (retaliationBonus.power * magBonus).roundToInt()
    }

    private fun getMagicBurstBonus(attacker: ActorState, attackBonuses: CombatBonusAggregate, originalTarget: ActorState, skill: SkillId): Float? {
        val baseBonus = getBaseMagicBurstBonus(attacker, originalTarget, skill) ?: return null
        return baseBonus * attackBonuses.magicBurstDamage.toMultiplier() * attackBonuses.magicBurstDamageII.toMultiplier()
    }

    fun getBaseMagicBurstBonus(attacker: ActorState, originalTarget: ActorState, skill: SkillId): Float? {
        val element = when (skill) {
            is MobSkillId -> if (MobSkills[skill].canMagicBurst) { MobSkills[skill].element } else { return null }
            is AbilitySkillId -> V0AbilityDefinitions.getMagicBurstElement(skill) ?: return null
            is SpellSkillId -> skill.toSpellInfo().element
            else -> return null
        }

        val current = originalTarget.skillChainTargetState.skillChainState as? SkillChainStep ?: return null
        if (current.attribute.elements.none { it == element }) { return null }

        return (1.5f + 0.2f * (current.step - 1)).coerceAtMost(2.5f)
    }

    private fun getAffinityBonus(skill: SkillId?, attacker: ActorState, damageType: AttackDamageType): Float {
        val spellInfo = (skill as? SpellSkillId)?.toSpellInfo() ?: return 1f
        if (spellInfo.magicType != MagicType.BlueMagic) { return 1f }

        return when (damageType) {
            AttackDamageType.Physical -> attacker.getStatusEffect(StatusEffect.ChainAffinity)?.potency?.toMultiplier() ?: 1f
            AttackDamageType.Magical -> attacker.getStatusEffect(StatusEffect.BurstAffinity)?.potency?.toMultiplier() ?: 1f
            else -> 1f
        }
    }

    fun getResistanceMultiplier(skill: SkillId?, defenderBonuses: CombatBonusAggregate): Float {
        val element = when (skill) {
            is SpellSkillId -> skill.toSpellInfo().element
            is MobSkillId -> MobSkills[skill].element
            else -> null
        }

        val potency = defenderBonuses.elementalResistance[element] ?: 0
        return potency.toPenaltyMultiplier()
    }

}