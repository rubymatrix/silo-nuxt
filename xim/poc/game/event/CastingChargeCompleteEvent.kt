package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.GameEngine.displayName
import xim.poc.game.actor.components.getInventory
import xim.poc.game.actor.components.setRecastState
import xim.poc.game.configuration.ApplierFailureResult
import xim.poc.game.configuration.ApplierSuccessResult
import xim.poc.game.configuration.SkillAppliers
import xim.poc.game.configuration.SkillAppliers.writeFailureReason
import xim.poc.game.configuration.SkillFailureReason
import xim.poc.game.configuration.constants.*
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.ShiftJis
import xim.resource.AbilityCostType
import xim.resource.ItemListType

class CastingChargeCompleteEvent(
    val sourceId: ActorId
) : Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()

        val current = actorState.getCastingState() ?: return emptyList()
        current.onExecute()

        val targetState = ActorStateManager[current.targetId]

        if (current.result is CastingInterrupted || targetState == null || !current.isTargetValid()) {
            return listOf(CastInterruptedEvent(sourceId))
        }

        if (GameEngine.checkParalyzeProc(actorState)) {
            return listOf(CastInterruptedEvent(sourceId, castInterruptReason = CastInterruptReason.Paralyze))
        }

        if (!GameEngine.canExecuteSkill(actorState, current.skill)) {
            return listOf(CastInterruptedEvent(sourceId))
        }

        return when (current.skill) {
            is SpellSkillId -> invokeSpell(actorState, targetState, current)
            is MobSkillId -> invokeMobSkill(actorState, targetState, current)
            is AbilitySkillId -> invokeAbility(actorState, targetState, current)
            is ItemSkillId -> invokeItem(actorState, targetState, current)
            is RangedAttackSkillId -> invokeRangedAttack(actorState, targetState, current)
        }
    }

    private fun invokeSpell(actorState: ActorState, targetState: ActorState, current: CastingState): List<Event> {
        val applierResult = SkillAppliers.invoke(actorState, targetState, current.skill)

        if (applierResult is ApplierFailureResult) {
            writeFailureReason(actorState, targetState, current.skill.displayName(), applierResult.failureReason)
            return listOf(CastInterruptedEvent(sourceId))
        }

        applierResult as ApplierSuccessResult

        val recastDelay = GameState.getGameMode().getSkillRecastTime(actorState, applierResult.executedSkill)
        actorState.setRecastState(applierResult.executedSkill, recastDelay)

        consumeActorResourceCost(actorState, applierResult)
        actorState.consumeStatusEffectCharge(StatusEffect.Spontaneity)

        completeAction(current, applierResult)
        return applierResult.events
    }

    private fun invokeRangedAttack(actorState: ActorState, targetState: ActorState, current: CastingState): List<Event> {
        val applierResult = SkillAppliers.invoke(actorState, targetState, current.skill)

        if (applierResult is ApplierFailureResult) {
            writeFailureReason(actorState, targetState, current.skill.displayName(), applierResult.failureReason)
            return listOf(CastInterruptedEvent(sourceId))
        }

        applierResult as ApplierSuccessResult

        val recastDelay = GameState.getGameMode().getSkillRecastTime(actorState, applierResult.executedSkill)
        actorState.setRecastState(applierResult.executedSkill, recastDelay)

        consumeActorResourceCost(actorState, applierResult)

        completeAction(current, applierResult)
        return applierResult.events
    }

    private fun invokeItem(actorState: ActorState, targetState: ActorState, current: CastingState): List<Event> {
        val specificItemId = current.context.itemId ?: return emptyList()
        val item = actorState.getInventory().getByInternalId(specificItemId)
        val itemName = current.skill.displayName()

        if (item == null) {
            writeFailureReason(actorState, targetState, itemName, SkillFailureReason.GenericFailure)
            return listOf(CastInterruptedEvent(sourceId))
        }

        val applierResult = SkillAppliers.invoke(actorState, targetState, current.skill)

        if (applierResult is ApplierFailureResult) {
            writeFailureReason(actorState, targetState, itemName, applierResult.failureReason)
            return listOf(CastInterruptedEvent(sourceId))
        }

        applierResult as ApplierSuccessResult

        if (item.info().type == ItemListType.UsableItem) {
            actorState.getInventory().discardItem(current.context.itemId, amount = 1)
        }

        completeAction(current, applierResult)
        return applierResult.events
    }

    private fun invokeAbility(actorState: ActorState, targetState: ActorState, current: CastingState): List<Event> {
        val applierResult = SkillAppliers.invoke(actorState, targetState, current.skill)

        if (applierResult is ApplierFailureResult) {
            writeFailureReason(actorState, targetState, current.skill.displayName(), applierResult.failureReason)
            return listOf(CastInterruptedEvent(sourceId))
        }

        applierResult as ApplierSuccessResult
        consumeActorResourceCost(actorState, applierResult)

        val recastDelay = GameState.getGameMode().getSkillRecastTime(actorState, applierResult.executedSkill)
        actorState.setRecastState(applierResult.executedSkill, recastDelay)

        completeAction(current, applierResult)
        return applierResult.events
    }

    private fun invokeMobSkill(actorState: ActorState, targetState: ActorState, current: CastingState): List<Event> {
        val applierResult = SkillAppliers.invoke(actorState, targetState, current.skill)

        if (applierResult is ApplierFailureResult) {
            val mobSkillName = current.skill.displayName()
            writeFailureReason(actorState, targetState, mobSkillName, applierResult.failureReason)
            return listOf(CastInterruptedEvent(sourceId))
        }

        applierResult as ApplierSuccessResult

        val mobSkillName = applierResult.executedSkill.displayName()
        consumeActorResourceCost(actorState, applierResult)

        completeAction(current, applierResult)
        if (mobSkillName.isNotBlank()) { ChatLog.addLine("${actorState.name} uses $mobSkillName ${ShiftJis.rightArrow} ${targetState.name}!", ChatLogColor.Info) }

        return applierResult.events
    }

    private fun consumeActorResourceCost(actorState: ActorState, applierResult: ApplierSuccessResult) {
        val cost = GameEngine.getSkillUsedCost(actorState, applierResult.executedSkill)

        when (cost.type) {
            AbilityCostType.Tp -> actorState.consumeTp(cost.value)
            AbilityCostType.Mp -> actorState.consumeMp(cost.value)
        }
    }

    private fun completeAction(castingState: CastingState, applierResult: ApplierSuccessResult) {
        val castingContext = if (castingState.skill == applierResult.executedSkill) {
            castingState.context
        } else {
            CastingStateContext()
        }

        castingState.result = CastingComplete(
            applierResult = applierResult,
            castingContext = castingContext,
        )
    }

}