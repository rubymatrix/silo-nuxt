package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.mskillDeadeningHaze_2720
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

class FamilyAcuexBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val auraTimer = FrameTimer(15.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.appearanceState == 1) {
            auraTimer.update(elapsedFrames)
            spreadPoison()
        }

        if (auraTimer.isReady()) {
            actorState.appearanceState = 0
        }

        return super.update(elapsedFrames)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill != mskillDeadeningHaze_2720) { return emptyList() }

        actorState.appearanceState = 1
        auraTimer.reset()

        return emptyList()
    }

    private fun spreadPoison() {
        val player = ActorStateManager.player()
        if (player.isDead()) { return }

        val distance = actorState.getTargetingDistance(player)
        if (distance > 5f) { return }

        val statusEffectState = player.getOrGainStatusEffect(StatusEffect.Poison, 1.seconds)
        statusEffectState.potency = (player.getMaxHp() * 0.2f).roundToInt()
    }

}