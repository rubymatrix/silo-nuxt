package xim.poc.game.configuration.v0.behaviors

import xim.poc.ActorManager
import xim.poc.game.*
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.Event
import xim.poc.gl.ByteColor
import xim.util.Fps
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class MobSnollTzarBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private var framesSinceEngaged = 0f
    private var timeSinceEngaged = ZERO
    private var sizeAdjustmentLock = ZERO

    override fun onInitialized(): List<Event> {
        actorState.setTp(1000)
        return super.onInitialized()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isEngaged()) {
            framesSinceEngaged += elapsedFrames
            timeSinceEngaged = Fps.framesToSeconds(framesSinceEngaged)
            adjustSize()
        }

        if (sizeAdjustmentLock > ZERO) {
            sizeAdjustmentLock -= Fps.framesToSeconds(elapsedFrames)
            return emptyList()
        }

        return super.update(elapsedFrames)
    }

    override fun wantsToUseSkill(): Boolean {
        return actorState.isEngaged() && if (sizeAdjustmentLock > ZERO) {
            false
        } else if (actorState.appearanceState == 3) {
            true
        } else {
            actorState.getTpp() > 0.3
        }
    }

    override fun getSkills(): List<SkillId> {
        if (actorState.appearanceState == 3) {
            return listOf(mskillHypothermalCombustion_346)
        }

        val skills = if (!actorState.hasStatusEffect(StatusEffect.Berserk)) {
            listOf(mskillBerserk_342)
        } else if (actorState.appearanceState <= 1) {
            listOf(mskillArcticImpact_343, mskillColdWave_344, mskillHiemalStorm_345)
        } else {
            listOf(mskillColdWave_344, mskillHiemalStorm_345)
        }

        return skills
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill != mskillHypothermalCombustion_346) { return emptyList() }

        primaryTargetContext.sourceState.setHp(primaryTargetContext.sourceState.getMaxHp())
        primaryTargetContext.targetState.setTargetState(null, false)

        AttackContext.compose(primaryTargetContext.context) {
            ActorManager[primaryTargetContext.sourceState.id]?.renderState?.effectColor = ByteColor.zero
        }

        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep)

        if (actorState.appearanceState == 3) {
            aggregate.knockBackResistance += 100
            aggregate.fullResist(StatusEffect.Stun, StatusEffect.Paralysis)
        }
    }

    private fun adjustSize() {
        if (!actorState.isIdleOrEngaged()) { return }

        val desiredSize = getDesiredSize()
        if (actorState.appearanceState == desiredSize) { return }

        sizeAdjustmentLock = 3.seconds

        val actor = ActorManager.getIfPresent(actorState.id)
        if (actor != null && actor.isMovementOrAnimationLocked()) { return }

        actorState.appearanceState = getDesiredSize()
        MiscEffects.playExclamationProc(actorState.id, ExclamationProc.Red)
    }

    private fun getDesiredSize(): Int {
        return if (timeSinceEngaged > 60.seconds) {
            3
        } else if (timeSinceEngaged > 40.seconds) {
            2
        } else if (timeSinceEngaged > 20.seconds) {
            1
        } else {
            0
        }
    }

}