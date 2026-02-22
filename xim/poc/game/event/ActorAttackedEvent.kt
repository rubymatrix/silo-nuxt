package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.*
import xim.poc.game.GameEngine.getStatusEffectDuration
import xim.poc.game.GameEngine.rollAgainst
import xim.poc.game.GameEngine.rollStatusEffect
import xim.poc.game.configuration.ActorAttackedContext
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.event.ActorAttackedHelper.applyStatusEffects
import xim.poc.game.event.ActorAttackedHelper.attemptAbsorbBuffs
import xim.poc.game.event.ActorAttackedHelper.attemptCastingInterrupt
import xim.poc.game.event.ActorAttackedHelper.blockedByBlink
import xim.poc.game.event.ActorAttackedHelper.consumeBlinkOnAoE
import xim.poc.game.event.ActorAttackedHelper.consumeBoost
import xim.poc.game.event.ActorAttackedHelper.consumeStoneskin
import xim.poc.game.event.ActorAttackedHelper.dodged
import xim.poc.game.event.ActorAttackedHelper.engageTargetAndDependents
import xim.poc.game.event.ActorAttackedHelper.expireStatusEffectsOnDamageTaken
import xim.poc.ui.ChatLog
import xim.poc.ui.ShiftJis
import xim.resource.AoeType
import xim.util.PI_f
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration

enum class AttackDamageType {
    Physical,
    Magical,
    SkillChain,
    Static,
    StatusOnly,
}

enum class ActorResourceType {
    HP,
    MP,
    TP,
}

data class AttackStatusEffectContext(
    val sourceState: ActorState,
    val targetState: ActorState,
    val statusState: StatusEffectState,
    val skill: SkillId?,
)

data class AttackStatusEffect(
    val statusEffect: StatusEffect,
    val baseDuration: Duration?,
    val baseChance: Float = 1f,
    val canResist: Boolean = true,
    val decorator: (AttackStatusEffectContext) -> Unit = { }
)

data class AttackAbsorptionEffect(
    val absorption: Float,
    val absorptionResource: ActorResourceType = ActorResourceType.HP,
    val absorptionCap: Int = Int.MAX_VALUE,
    val absorbBuffs: Int = 0,
    val absorbBuffsNegatesAttack: Boolean = false,
    val proportionalAbsorption: Boolean = false,
)

data class AttackEffects(
    val damageResource: ActorResourceType = ActorResourceType.HP,
    val knockBackMagnitude: Int = 0,
    val dispelCount: Int = 0,
    val gazeAttack: Boolean = false,
    val absorptionEffect: AttackAbsorptionEffect? = null,
    val attackStatusEffects: List<AttackStatusEffect> = emptyList(),
    val bypassShadows: Boolean = false,
)

class AttackCounterEffect(
    val damageAmount: Int,
    val counterSourceTpGain: Int = 0,
    val counterTargetTpGain: Int = 0,
)

class ActorAutoAttackedEvent(
    val sourceId: ActorId,
    val result: AutoAttackResult,
    val totalAttacksInRound: Int,
): Event {

    override fun apply(): List<Event> {
        val sourceState = ActorStateManager[sourceId] ?: return emptyList()

        val targetState = ActorStateManager[result.targetId] ?: return emptyList()
        if (targetState.isDead()) { return emptyList() }

        val actionContext = AttackContext.from(sourceState, targetState)
        actionContext.setCriticalHitFlag(result.criticalHit)

        val actor = ActorManager[sourceId]
        actor?.displayAutoAttack(result.type, actionContext, targetState, totalAttacksInRound = totalAttacksInRound)

        val outputEvents = ArrayList<Event>()
        outputEvents += engageTargetAndDependents(sourceState, targetState)

        if (dodged(AttackDamageType.Physical, sourceState, targetState)) {
            actionContext.setMissFlag()
            AttackContext.compose(actionContext) { ChatLog("${sourceState.name}${ShiftJis.rightArrow}${targetState.name} miss!") }
            return outputEvents
        }

        if (blockedByBlink(targetState, blockChance = 50)) {
            actionContext.setMissFlag()
            AttackContext.compose(actionContext) { ChatLog("1 of ${targetState.name}'s shadows absorbs the damage and disappears.") }
            return outputEvents
        }

        var adjustedDamage = result.damageDone

        if (targetState.isEngaged() && targetState.isFacingTowards(sourceState)) {
            if (GameEngine.rollParry(sourceState, targetState)) {
                actionContext.setParryFlag()
                AttackContext.compose(actionContext) { ChatLog("${targetState.name} parries the attack.") }
                return outputEvents
            }

            val counter = GameEngine.rollCounter(sourceState, targetState)
            val counterResult = counter?.let { processCounter(sourceState, targetState, it, actionContext) }
            if (counterResult != null) { return outputEvents + counterResult }

            if (GameEngine.rollGuard(sourceState, targetState)) {
                actionContext.setGuardFlag()
                adjustedDamage /= 4
            }
        }

        sourceState.behaviorController.onAutoAttackExecuted()
        consumeBoost(sourceState)

        val finalDamage = consumeStoneskin(targetState, adjustedDamage, ActorResourceType.HP)
        outputEvents += ActorDamagedEvent(
            sourceId = sourceId,
            targetId = result.targetId,
            type = ActorResourceType.HP,
            amount = finalDamage,
            actionContext = actionContext,
            damageType = AttackDamageType.Physical,
        )

        if (finalDamage > 0) {
            sourceState.gainTp(result.sourceTpGained)
            targetState.gainTp(result.targetTpGained)
            expireStatusEffectsOnDamageTaken(targetState)
            attemptCastingInterrupt(sourceState, targetState, AttackDamageType.Physical)
        }

        for (autoAttackEffect in result.addedEffects) {
            outputEvents += processAutoAttackEffect(autoAttackEffect = autoAttackEffect, sourceState = sourceState, targetState = targetState, actionContext = actionContext)
        }

        for (retaliationEffect in result.retaliationEffects) {
            outputEvents += processRetaliationEffect(autoAttackEffect = retaliationEffect, sourceState = sourceState, targetState = targetState, actionContext = actionContext)
        }

        actionContext.onHitEffect = result.addedEffects.mapNotNull { it.displayType.contextDisplay }.randomOrNull() ?: actionContext.onHitEffect
        actionContext.retaliationFlag = result.retaliationEffects.mapNotNull { it.type.contextDisplay }.randomOrNull() ?: actionContext.retaliationFlag

        return outputEvents
    }

    private fun processAutoAttackEffect(autoAttackEffect: AutoAttackAddedEffect, sourceState: ActorState, targetState: ActorState, actionContext: AttackContext): List<Event> {
        val context = AddedEffectContext(attacker = sourceState, defender = targetState, context = actionContext)
        return autoAttackEffect.onProc.invoke(context)
    }

    private fun processRetaliationEffect(autoAttackEffect: AutoAttackRetaliationEffect, sourceState: ActorState, targetState: ActorState, actionContext: AttackContext): List<Event> {
        val addedEffectEvents = ArrayList<Event>()

        val finalAddedEffectDamage = consumeStoneskin(sourceState, autoAttackEffect.damage, autoAttackEffect.type.damageResource)

        val isDrainingEffect = autoAttackEffect.type == AttackRetaliationEffectType.DreadSpikes

        addedEffectEvents += ActorDamagedEvent(
            sourceId = result.targetId,
            targetId = sourceId,
            type = autoAttackEffect.type.damageResource,
            amount = finalAddedEffectDamage,
            actionContext = actionContext,
            damageType = AttackDamageType.Magical,
            additionalEffect = true,
            emitDamageText = !isDrainingEffect,
        )

        val statusEffect = autoAttackEffect.statusEffect
        if (statusEffect != null && rollStatusEffect(targetState = sourceState, attackStatusEffect = statusEffect)) {
            val statusEffectState = sourceState.gainStatusEffect(status = statusEffect.statusEffect, duration = statusEffect.baseDuration, sourceId = result.targetId)
            statusEffect.decorator.invoke(AttackStatusEffectContext(sourceState, targetState, statusEffectState, skill = null))
        }

        if (isDrainingEffect) {
            addedEffectEvents += ActorHealedEvent(
                sourceId = result.targetId,
                targetId = sourceId,
                amount = finalAddedEffectDamage,
                actionContext = actionContext,
                healType = autoAttackEffect.type.damageResource,
            )
        }

        return addedEffectEvents
    }

    private fun processCounter(attacker: ActorState, defender: ActorState, counterEffect: AttackCounterEffect, actionContext: AttackContext): List<Event>? {
        if (dodged(AttackDamageType.Physical, defender, attacker)) { return null }

        consumeBoost(defender)

        actionContext.setCounteredFlag()
        actionContext.setCriticalHitFlag(false) // Critical isn't visible on counters (?)

        if (blockedByBlink(attacker, blockChance = 50)) {
            actionContext.setMissFlag()
            AttackContext.compose(actionContext) { ChatLog("1 of ${attacker.name}'s shadows absorbs the damage and disappears.", actionContext = actionContext) }
            return emptyList()
        }

        val finalDamage = consumeStoneskin(targetState = attacker, incomingDamage = counterEffect.damageAmount, incomingDamageType = ActorResourceType.HP)

        if (finalDamage > 0) {
            defender.gainTp(counterEffect.counterSourceTpGain)
            attacker.gainTp(counterEffect.counterTargetTpGain)
        }

        return listOf(ActorDamagedEvent(
            sourceId = result.targetId,
            targetId = sourceId,
            amount = finalDamage,
            actionContext = actionContext,
            damageType = AttackDamageType.Physical,
        ))
    }

}

class ActorAttackedEvent(
    val sourceId: ActorId,
    val targetId: ActorId,
    val damageAmount: List<Int>,
    val damageType: AttackDamageType,
    val sourceTpGain: Int = 0,
    val targetTpGain: Int = 0,
    val attackEffects: AttackEffects = AttackEffects(),
    val actionContext: AttackContext,
    val skill: SkillId,
): Event {

    override fun apply(): List<Event> {
        val sourceState = ActorStateManager[sourceId] ?: return emptyList()

        val targetState = ActorStateManager[targetId] ?: return emptyList()
        if (targetState.isDead()) { return emptyList() }

        val outputEvents = ArrayList<Event>()
        outputEvents += engageTargetAndDependents(sourceState, targetState)

        outputEvents += sourceState.behaviorController.onAttackExecuted(actorAttackedEvent = this)

        if (attackEffects.gazeAttack && !targetState.isFacingTowards(sourceState, halfAngle = PI_f /3f)) {
            actionContext.setMissFlag()
            AttackContext.compose(actionContext) { ChatLog("${sourceState.name}${ShiftJis.rightArrow}${targetState.name} miss!", actionContext = actionContext) }
            return outputEvents
        }

        var landedHits = damageAmount.filter { !dodged(damageType, sourceState, targetState) }
        if (damageAmount.isNotEmpty() && landedHits.isEmpty()) {
            actionContext.setMissFlag()
            AttackContext.compose(actionContext) { ChatLog("${sourceState.name}${ShiftJis.rightArrow}${targetState.name} miss!", actionContext = actionContext) }
            return outputEvents
        }

        if (!attackEffects.bypassShadows) {
            consumeBlinkOnAoE(skill, sourceState, targetState)
            landedHits = landedHits.dropWhile { blockedByBlink(targetState, blockChance = 100) }
        }

        if (damageAmount.isNotEmpty() && landedHits.isEmpty()) {
            actionContext.setMissFlag()
            AttackContext.compose(actionContext) { ChatLog("${damageAmount.size} of ${targetState.name}'s shadows absorbs the damage and disappears.", actionContext = actionContext) }
            return outputEvents
        }

        if (landedHits.isNotEmpty() && attackEffects.dispelCount > 0) {
            dispelEffects(targetState, attackEffects.dispelCount)
        }

        if (damageType == AttackDamageType.StatusOnly) {
            applyStatusEffects(attackEffects, sourceState, targetState, skill, actionContext)
            return outputEvents
        }

        if (landedHits.isNotEmpty() && sourceTpGain > 0) {
            sourceState.gainTp(sourceTpGain + 10 * (landedHits.size - 1))
        }

        if (landedHits.isNotEmpty() && targetTpGain > 0) {
            targetState.gainTp(targetTpGain + 10 * (landedHits.size - 1))
        }

        val absorptionEffects = attackEffects.absorptionEffect

        if (landedHits.isNotEmpty() && absorptionEffects != null && absorptionEffects.absorbBuffs > 0) {
            val buffsToSteal = absorptionEffects.absorbBuffs.coerceAtMost(landedHits.size)
            val buffsStolen = attemptAbsorbBuffs(sourceState, targetState, buffsToSteal)

            if (absorptionEffects.absorbBuffsNegatesAttack) {
                landedHits = landedHits.drop(buffsStolen)
                if (landedHits.isEmpty()) { return outputEvents }
            }
        }

        consumeBoost(sourceState)

        val landedDamage = landedHits.sum()
        val finalDamage = consumeStoneskin(targetState, landedDamage, attackEffects.damageResource)

        if (finalDamage > 0) {
            expireStatusEffectsOnDamageTaken(targetState)
            attemptCastingInterrupt(sourceState, targetState, damageType)

            val targetBonuses = GameEngine.getCombatBonusAggregate(targetState)
            actionContext.knockBackMagnitude = (attackEffects.knockBackMagnitude * targetBonuses.knockBackResistance.toPenaltyMultiplier()).roundToInt()
        }

        outputEvents += ActorDamagedEvent(
            sourceId = sourceId,
            targetId = targetId,
            type = attackEffects.damageResource,
            amount = finalDamage,
            actionContext = actionContext,
            skill = skill,
            damageType = damageType,
        )

        applyStatusEffects(attackEffects, sourceState, targetState, skill, actionContext)

        if (absorptionEffects != null) {
            val absorption = (finalDamage * absorptionEffects.absorption).roundToInt()
            if (absorption > 0) { outputEvents += processDrainingEffect(sourceState, targetState, absorption, absorptionEffects) }
        }

        outputEvents += getSkillChainEvents(landedDamage, sourceState, targetState)

        return outputEvents
    }

    private fun getSkillChainEvents(landedDamage: Int, sourceState: ActorState, targetState: ActorState): List<Event> {
        val skillChainResult = targetState.skillChainTargetState.applySkill(sourceState, targetState, skill) ?: return emptyList()
        if (skillChainResult !is SkillChainStep) { return emptyList() }

        return listOf(ActorSkillChainEvent(
                sourceId = sourceId,
                targetId = targetId,
                skillChainStep = skillChainResult,
                closingWeaponSkillDamage = landedDamage,
                context = actionContext,
            ))
    }

    private fun dispelEffects(targetState: ActorState, count: Int) {
        targetState.getStatusEffects()
            .filter { it.canDispel }
            .take(count)
            .forEach { targetState.expireStatusEffect(it.statusEffect) }
    }

    private fun processDrainingEffect(sourceState: ActorState, targetState: ActorState, amount: Int, absorptionEffects: AttackAbsorptionEffect): List<Event> {
        val resource = targetState.getResource(absorptionEffects.absorptionResource)
        val cappedAmount = amount.coerceAtMost(min(resource, absorptionEffects.absorptionCap))

        val drainAmount = if (absorptionEffects.proportionalAbsorption) {
            val proportion = cappedAmount.toFloat() / targetState.getMaxResource(absorptionEffects.absorptionResource).toFloat()
            (proportion * sourceState.getMaxResource(absorptionEffects.absorptionResource)).roundToInt()
        } else {
            cappedAmount
        }

        val events = ArrayList<Event>()

        if (attackEffects.damageResource != absorptionEffects.absorptionResource) {
            events += ActorDamagedEvent(
                sourceId = sourceId,
                targetId = targetId,
                amount = cappedAmount,
                damageType = damageType,
                type = absorptionEffects.absorptionResource,
                actionContext = actionContext,
                emitDamageText = false,
            )
        }

        events += ActorHealedEvent(
            sourceId = targetId,
            targetId = sourceId,
            amount = drainAmount,
            actionContext = actionContext,
            healType = absorptionEffects.absorptionResource,
        )

        return events
    }

}

class ActorSkillChainEvent(
    val sourceId: ActorId,
    val targetId: ActorId,
    val skillChainStep: SkillChainStep,
    val closingWeaponSkillDamage: Int,
    val context: AttackContext,
) : Event {

    override fun apply(): List<Event> {
        val sourceState = ActorStateManager[sourceId] ?: return emptyList()
        val targetState = ActorStateManager[targetId] ?: return emptyList()

        context.skillChainStep = skillChainStep

        val damage = GameState.getGameMode().getSkillChainDamage(
            attacker = sourceState,
            defender = targetState,
            skillChainStep = skillChainStep,
            closingWeaponSkillDamage = closingWeaponSkillDamage,
        )

        val finalDamage = consumeStoneskin(targetState, damage, ActorResourceType.HP)

        val event = ActorDamagedEvent(
            sourceId = sourceId,
            targetId = targetId,
            type = ActorResourceType.HP,
            amount = finalDamage,
            skillChainStep = skillChainStep,
            actionContext = context,
            damageType = AttackDamageType.SkillChain,
        )

        AttackContext.compose(context) {
            MiscEffects.playSkillChain(sourceId, targetId, skillChainStep.attribute)
        }

        return listOf(event)
    }

}

object ActorAttackedHelper {

    fun engageTargetAndDependents(sourceState: ActorState, targetState: ActorState): List<Event> {
        val outputEvents = ArrayList<Event>()
        outputEvents += targetState.behaviorController.onAttacked(ActorAttackedContext(attacker = sourceState))

        sourceState.pet?.let { outputEvents += BattleEngageEvent(it, targetState.id) }
        GameEngine.getTrusts(sourceState.id).forEach { outputEvents += BattleEngageEvent(it.id, targetState.id) }

        if (targetState.isPlayer() && targetState.targetState.targetId == null) {
            outputEvents += ActorTargetEvent(targetState.id, sourceState.id)
        }

        return outputEvents
    }

    fun dodged(damageType: AttackDamageType, attacker: ActorState, defender: ActorState): Boolean {
        if (damageType != AttackDamageType.Physical) { return false }
        return GameState.getGameMode().rollEvasion(attacker, defender)
    }

    fun consumeBlinkOnAoE(skill: SkillId, attacker: ActorState, defender: ActorState) {
        val rangeInfo = GameEngine.getRangeInfo(attacker, skill)
        if (rangeInfo.type == AoeType.None) { return }
        defender.expireStatusEffect(StatusEffect.Blink)
    }

    fun blockedByBlink(targetState: ActorState, blockChance: Int): Boolean {
        if (!rollAgainst(blockChance)) { return false }
        return targetState.consumeStatusEffectCharge(StatusEffect.Blink)
    }

    fun consumeStoneskin(targetState: ActorState, incomingDamage: Int, incomingDamageType: ActorResourceType): Int {
        if (incomingDamageType != ActorResourceType.HP) { return incomingDamage }
        val stoneskin = targetState.getStatusEffect(StatusEffect.Stoneskin) ?: return incomingDamage

        val originalValue = stoneskin.counter

        stoneskin.counter -= incomingDamage
        if (stoneskin.counter <= 0) { targetState.expireStatusEffect(StatusEffect.Stoneskin) }

        return (incomingDamage - originalValue).coerceAtLeast(0)
    }

    fun attemptCastingInterrupt(sourceState: ActorState, targetState: ActorState, damageType: AttackDamageType) {
        val targetCastingState = targetState.getCastingState() ?: return
        if (!targetCastingState.canInterrupt()) { return }

        if (damageType != AttackDamageType.Physical) { return }
        if (!GameEngine.rollCastingInterrupt(sourceState, targetState)) { return }

        if (targetState.consumeStatusEffectCharge(StatusEffect.Aquaveil)) { return }

        targetCastingState.result = CastingInterrupted
    }

    fun attemptAbsorbBuffs(sourceState: ActorState, targetState: ActorState, count: Int): Int {
        val buffs = targetState.getStatusEffects().filter { it.statusEffect.buff }.take(count)

        for (buff in buffs) {
            val copyEffect = sourceState.gainStatusEffect(buff.statusEffect, sourceId = targetState.id)
            copyEffect.copyFrom(buff)
            targetState.expireStatusEffect(buff.statusEffect)
        }

        return buffs.size
    }

    fun consumeBoost(sourceState: ActorState) {
        sourceState.consumeStatusEffectCharge(StatusEffect.Boost, expireOnZero = true)
    }

    fun expireStatusEffectsOnDamageTaken(targetState: ActorState) {
        val sleep = targetState.getStatusEffect(StatusEffect.Sleep)
        if (sleep != null && sleep.counter == 0) {
            targetState.expireStatusEffect(StatusEffect.Sleep)
        }

        val petrify = targetState.getStatusEffect(StatusEffect.Petrify)
        if (petrify != null && Random.nextDouble() < 0.33) {
            targetState.expireStatusEffect(StatusEffect.Petrify)
        }

        val bind = targetState.getStatusEffect(StatusEffect.Bind)
        if (bind != null && (targetState.isEnemy() || Random.nextDouble() < 0.33)) {
            targetState.expireStatusEffect(StatusEffect.Bind)
        }
    }

    fun applyStatusEffects(attackEffects: AttackEffects, sourceState: ActorState, targetState: ActorState, skill: SkillId?, actionContext: AttackContext) {
        for (attackStatusEffect in attackEffects.attackStatusEffects) { applyStatusEffect(attackStatusEffect, sourceState, targetState, skill, actionContext) }
    }

    fun applyStatusEffect(attackStatusEffect: AttackStatusEffect, sourceState: ActorState, targetState: ActorState, skill: SkillId?, actionContext: AttackContext) {
        if (attackStatusEffect.statusEffect == StatusEffect.Sleep && targetState.hasStatusEffect(StatusEffect.Sleep)) {
            return
        }

        if (!rollStatusEffect(targetState, attackStatusEffect)) {
            ChatLog.statusEffectResist(targetState.name, attackStatusEffect.statusEffect, actionContext)
            return
        }

        val effectDuration = getStatusEffectDuration(targetState, attackStatusEffect)
        if (effectDuration != null && effectDuration <= Duration.ZERO) {
            ChatLog.statusEffectResist(targetState.name, attackStatusEffect.statusEffect, actionContext)
            return
        }

        val state = targetState.gainStatusEffect(status = attackStatusEffect.statusEffect, duration = effectDuration, sourceId = sourceState.id)
        AttackContext.compose(actionContext) { ChatLog.statusEffectGained(targetState.name, attackStatusEffect.statusEffect, actionContext) }
        attackStatusEffect.decorator.invoke(AttackStatusEffectContext(sourceState, targetState, state, skill))
    }

}