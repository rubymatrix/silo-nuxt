package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.actor.components.resetRecastStates
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillSelection
import xim.poc.game.configuration.SkillSelector
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.staticDamage
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.seconds

private data class MercurialResult(
    val damage: Int,
    val followUpSkill: MobSkillId?,
    val executionSkill: MobSkillId = mskillMercurialStrike_2320,
) {
    companion object {
        val results = listOf(
            MercurialResult(11, mskillImpactRoar_408),
            MercurialResult(22, mskillGrandSlam_409),
            MercurialResult(33, mskillPowerAttack_411),
            MercurialResult(44, mskillTrebuchet_1380),
            MercurialResult(55, mskillColossalSlam_2322),
            MercurialResult(66, mskillColossalSlam_2322),
            MercurialResult(77, followUpSkill = null, executionSkill = mskillMercurialStrike_2321),
        )
    }
}


class MobBriareusController(actorState: ActorState): V0MonsterController(actorState) {

    companion object {
        fun getMercurialStrikeTargetEvaluator(): SkillApplierHelper.TargetEvaluator {
            return SkillApplierHelper.TargetEvaluator {
                val behavior = it.sourceState.behaviorController
                if (behavior !is MobBriareusController) { return@TargetEvaluator emptyList() }

                val pendingStrike = behavior.pendingStrike ?: return@TargetEvaluator emptyList()
                staticDamage(pendingStrike.damage, it)
            }
        }
    }

    private var pendingStrike: MercurialResult? = null
    private var pendingSkill: MobSkillId? = null
    private var mobSkillLockTimer: FrameTimer? = null

    override fun update(elapsedFrames: Float): List<Event> {
        mobSkillLockTimer?.update(elapsedFrames)
        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Bind, StatusEffect.Petrify)

        if (pendingSkill != null) {
            aggregate.fullResist(StatusEffect.Stun)
            aggregate.mobSkillFastCast += 50
            aggregate.tpRequirementBypass = true
        }
    }

    override fun wantsToUseSkill(): Boolean {
        return mobSkillLockTimer?.isReady() ?: return super.wantsToUseSkill()
    }

    override fun selectSkill(): SkillSelection? {
        val followUpSkill = pendingSkill
        if (followUpSkill != null) { return SkillSelector.selectSkill(actorState, listOf(followUpSkill)) }

        val strike = MercurialResult.results.random()
        pendingStrike = strike

        return SkillSelector.selectSkill(actorState, listOf(strike.executionSkill))
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        val skill = primaryTargetContext.skill

        if (skill == pendingSkill) {
            mobSkillLockTimer = null
            pendingSkill = null
        }

        if (skill == mskillMercurialStrike_2320) {
            val strike = pendingStrike ?: return emptyList()
            pendingSkill = strike.followUpSkill
            mobSkillLockTimer = FrameTimer(2.seconds)
        }

        if (skill == mskillMercurialStrike_2321) {
            restoreTargets(primaryTargetContext.allTargetStates)
        }

        pendingStrike = null
        return emptyList()
    }

    private fun restoreTargets(targets: List<ActorState>) {
        targets.forEach {
            it.resetRecastStates()
            it.setMp(it.getMaxMp())
        }
    }

}