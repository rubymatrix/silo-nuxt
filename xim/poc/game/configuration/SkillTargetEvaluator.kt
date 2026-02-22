package xim.poc.game.configuration

import xim.poc.ActionTargetFilter
import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.configuration.SkillFailureReason.OutOfRange
import xim.poc.game.configuration.SkillTargetEvaluation.Companion.failure
import xim.poc.game.configuration.constants.SkillId
import xim.resource.AoeType
import xim.resource.TargetFlag
import xim.util.PI_f
import kotlin.math.abs

data class SkillTargetEvaluation(val primaryTarget: ActorId, val allTargetIds: List<ActorId>, val contexts: AttackContexts, val failureReason: SkillFailureReason? = null) {
    companion object {
        fun failure(primaryTarget: ActorId, reason: SkillFailureReason): SkillTargetEvaluation {
            return SkillTargetEvaluation(primaryTarget, emptyList(), AttackContexts.noop(), reason)
        }
    }

    fun primaryTargetContext(): AttackContext {
        return contexts[primaryTarget]
    }

}

object SkillTargetEvaluator {

    fun evaluateTargets(skill: SkillId, sourceState: ActorState, primaryTargetState: ActorState): SkillTargetEvaluation {
        val rangeInfo = GameEngine.getRangeInfo(sourceState, skill)
        if (sourceState.isPc() && !isInSkillRange(rangeInfo, sourceState, primaryTargetState) && (rangeInfo.type == AoeType.None || rangeInfo.type == AoeType.Target)) {
            return failure(primaryTargetState.id, OutOfRange)
        }

        val allTargets = if (rangeInfo.type == AoeType.None) {
            listOf(primaryTargetState)
        } else {
            getNearbyTargets(skill, rangeInfo, sourceState, primaryTargetState)
        }

        val allTargetIds = allTargets.map { it.id }

        val targetContexts = allTargetIds.associateWith { AttackContext(
            appearanceState = sourceState.appearanceState,
            skill = skill,
        ) }

        return SkillTargetEvaluation(primaryTargetState.id, allTargetIds, AttackContexts(targetContexts))
    }

    private fun getNearbyTargets(skill: SkillId, rangeInfo: SkillRangeInfo, sourceState: ActorState, targetState: ActorState): List<ActorState> {
        val targetFlags = GameEngine.getSkillEffectedTargetFlags(sourceState, skill)

        val targetFilter = if (!sourceState.isEnemy() && targetFlags == TargetFlag.Self.flag) {
            ActionTargetFilter(TargetFlag.Self.flag or TargetFlag.Party.flag, effectCheck = true)
        } else if (sourceState.isEnemy() && targetFlags == TargetFlag.Self.flag){
            ActionTargetFilter(TargetFlag.Self.flag or TargetFlag.Ally.flag, effectCheck = true)
        } else {
            ActionTargetFilter(targetFlags, effectCheck = true)
        }

        return ActorStateManager.filter { isInEffectRange(rangeInfo, source = sourceState, primaryTarget = targetState, additionalTarget = it) }
            .filter { targetFilter.targetFilter(sourceState, it) }
    }

    private fun isInSkillRange(rangeInfo: SkillRangeInfo, sourceState: ActorState, targetState: ActorState): Boolean {
        return sourceState.getTargetingDistance(targetState) <= rangeInfo.maxTargetDistance
    }

    fun isInEffectRange(rangeInfo: SkillRangeInfo, source: ActorState, primaryTarget: ActorState, additionalTarget: ActorState): Boolean {
        val effectPosition = when (rangeInfo.type) {
            AoeType.None -> return false
            AoeType.Target -> source.getCastingState()?.context?.targetAoeCenter ?: primaryTarget.position
            AoeType.Cone, AoeType.Source -> source.position
        }

        return if (rangeInfo.type == AoeType.Source && source.getTargetingDistance(additionalTarget) <= rangeInfo.effectRadius) {
            true
        } else if (rangeInfo.type == AoeType.Target && additionalTarget.getTargetingDistance(effectPosition) <= rangeInfo.effectRadius) {
            true
        } else if (rangeInfo.type == AoeType.Cone && source.getTargetingDistance(additionalTarget) <= rangeInfo.effectRadius) {
            checkConeAngle(source, additionalTarget, rangeInfo)
        } else {
            false
        }
    }

    private fun checkConeAngle(source: ActorState, target: ActorState, rangeInfo: SkillRangeInfo): Boolean {
        val coneAngle = rangeInfo.fixedRotation ?: 0f
        val targetAngle = source.getSignedFacingAngle(target.position)
        val difference = abs(coneAngle - targetAngle)
        return difference < PI_f/4f || difference > 2 * PI_f - PI_f/4f
    }

}
