package xim.poc.game.configuration

import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.event.Event

object SkillApplierRunner {

    fun applySkill(skill: SkillId, applier: SkillApplier, source: ActorState, targetEvaluation: SkillTargetEvaluation): ApplierResult {
        val failure = applier.validEvaluator.isUsable(SkillApplierHelper.SkillUsableEvaluatorContext(skill, source))
        if (failure != null) { return ApplierFailureResult(failure) }

        val primaryTargetState = ActorStateManager[targetEvaluation.primaryTarget] ?: return primaryTargetNotFound()
        val primaryContext = targetEvaluation.primaryTargetContext()

        val allTargetStates = targetEvaluation.allTargetIds.mapNotNull { ActorStateManager[it] }

        val events = ArrayList<Event>()

        if (applier.additionalSelfEvaluator != null) {
            events += applier.additionalSelfEvaluator.getEvents(SkillApplierHelper.TargetEvaluatorContext(skill, source, source, primaryTargetState, primaryContext, allTargetStates))
        }

        val primaryTargetContext = SkillApplierHelper.TargetEvaluatorContext(skill, source, primaryTargetState, primaryTargetState, primaryContext, allTargetStates)

        if (applier.primaryTargetEvaluator != null) {
            events += applier.primaryTargetEvaluator.getEvents(primaryTargetContext)
        }

        for (target in allTargetStates) {
            if (applier.primaryTargetEvaluator != null && target.id == targetEvaluation.primaryTarget) { continue }
            val context = targetEvaluation.contexts[target.id]
            events += applier.targetEvaluator.getEvents(SkillApplierHelper.TargetEvaluatorContext(skill, source, target, primaryTargetState, context, allTargetStates))
        }

        if (applier.postEvaluation != null) {
            events += applier.postEvaluation.getEvents(primaryTargetContext)
        }

        events += source.behaviorController.onSkillExecuted(primaryTargetContext)

        return ApplierSuccessResult(executedSkill = skill, events = events, primaryTargetId = primaryTargetState.id, allTargetIds = targetEvaluation.allTargetIds, contexts = targetEvaluation.contexts)
    }

    private fun primaryTargetNotFound(): ApplierResult {
        return ApplierFailureResult(failureReason = null)
    }

}