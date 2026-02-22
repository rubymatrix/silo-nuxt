package xim.poc.game.configuration

import xim.poc.ActorId
import xim.poc.game.ActorState
import xim.poc.game.AttackContext
import xim.poc.game.AttackContexts
import xim.poc.game.configuration.SkillApplierHelper.SkillUsableEvaluator
import xim.poc.game.configuration.SkillApplierHelper.SwitchSkillEvaluator
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluator
import xim.poc.game.configuration.SkillApplierHelper.makeApplier
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.event.Event
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.resource.AoeType


enum class SkillFailureReason {
    OutOfRange,
    NotEngaged,
    NotEnoughTp,
    GenericFailure,
    Medicated,
}

sealed interface ApplierResult

class ApplierFailureResult(val failureReason: SkillFailureReason?): ApplierResult

class ApplierSuccessResult(val executedSkill: SkillId, val events: List<Event>, val primaryTargetId: ActorId, val allTargetIds: List<ActorId>, val contexts: AttackContexts, val success: Boolean = true): ApplierResult

data class SkillRangeInfo(val maxTargetDistance: Float, val effectRadius: Float, val type: AoeType, val tracksTarget: Boolean = true, val fixedRotation: Float? = null)

class SkillApplier(
    val targetEvaluator: TargetEvaluator,
    val validEvaluator: SkillUsableEvaluator = SkillUsableEvaluator.noop,
    val primaryTargetEvaluator: TargetEvaluator? = null,
    val additionalSelfEvaluator: TargetEvaluator? = null,
    val postEvaluation: TargetEvaluator? = null,
    val switchSkillEvaluator: SwitchSkillEvaluator? = null,
)

object SkillAppliers {

    private val skillAppliers = HashMap<SkillId, SkillApplier>()

    fun register(skill: SkillId, skillApplier: SkillApplier) {
        skillAppliers[skill] = skillApplier
    }

    operator fun set(skill: SkillId, skillApplier: SkillApplier) {
        register(skill, skillApplier)
    }

    fun checkValid(sourceState: ActorState, skill: SkillId): Boolean {
        val applier = sourceState.getSkillApplier(skill) ?: skillAppliers.getOrPut(skill) { makeApplier(TargetEvaluator.noop()) }
        val failure = applier.validEvaluator.isUsable(SkillApplierHelper.SkillUsableEvaluatorContext(skill, sourceState))
        return failure == null
    }

    fun invoke(sourceState: ActorState, targetState: ActorState, skill: SkillId): ApplierResult {
        val applier = sourceState.getSkillApplier(skill) ?: skillAppliers.getOrPut(skill) { makeApplier(TargetEvaluator.noop()) }

        if (applier.switchSkillEvaluator != null) {
            val switchSkillContext = SkillApplierHelper.SwitchSkillContext(sourceState, targetState, skill)
            val switchedSkill = applier.switchSkillEvaluator.evaluate(switchSkillContext)
            if (switchedSkill != null) { return invoke(sourceState, switchedSkill.newTarget, switchedSkill.newSkill) }
        }

        val targets = SkillTargetEvaluator.evaluateTargets(skill, sourceState, targetState)
        if (targets.failureReason != null) { return ApplierFailureResult(targets.failureReason) }

        return SkillApplierRunner.applySkill(skill, applier, sourceState, targets)
    }

    fun writeFailureReason(source: ActorState, target: ActorState, skillName: String, skillFailureReason: SkillFailureReason?) {
        skillFailureReason ?: return

        val string = when (skillFailureReason) {
            SkillFailureReason.OutOfRange -> "${target.name} is out of range."
            SkillFailureReason.NotEngaged -> "${source.name} must be engaged to use $skillName."
            SkillFailureReason.NotEnoughTp -> "${source.name} does not have enough TP to use $skillName."
            SkillFailureReason.GenericFailure -> "${source.name} failed to use $skillName."
            SkillFailureReason.Medicated -> "${source.name} cannot use the $skillName while medicated."
        }

        ChatLog(string, ChatLogColor.Info)
    }

}

object SkillApplierHelper {

    class TargetEvaluatorContext(
        val skill: SkillId,
        val sourceState: ActorState,
        val targetState: ActorState,
        val primaryTargetState: ActorState,
        val context: AttackContext,
        val allTargetStates: List<ActorState>,
    )

    fun interface TargetEvaluator {
        fun getEvents(context: TargetEvaluatorContext): List<Event>

        companion object {
            fun compose(vararg evaluators: TargetEvaluator): TargetEvaluator {
                return TargetEvaluator { c -> evaluators.flatMap { it.getEvents(c) } }
            }

            fun noop(): TargetEvaluator {
                return TargetEvaluator { emptyList() }
            }

        }
    }

    class SkillUsableEvaluatorContext(
        val skill: SkillId,
        val sourceState: ActorState,
    )

    fun interface SkillUsableEvaluator {
        fun isUsable(context: SkillUsableEvaluatorContext): SkillFailureReason?

        companion object {
            val noop = SkillUsableEvaluator { null }
        }
    }

    fun makeApplier(targetEvaluator: TargetEvaluator): SkillApplier {
        return SkillApplier(targetEvaluator = targetEvaluator)
    }

    class SwitchSkillContext(
        val sourceState: ActorState,
        val targetState: ActorState,
        val skill: SkillId,
    )

    class SwitchSkillResult(
        val newTarget: ActorState,
        val newSkill: SkillId,
    )

    fun interface SwitchSkillEvaluator {
        fun evaluate(context: SwitchSkillContext): SwitchSkillResult?
    }

}
