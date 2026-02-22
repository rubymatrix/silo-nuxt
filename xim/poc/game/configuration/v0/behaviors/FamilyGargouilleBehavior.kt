package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FamilyGargouilleBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val formChangeTimer = FrameTimer(2.minutes).resetRandom(lowerBound = 30.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        formChangeTimer.update(elapsedFrames)

        if (formChangeTimer.isReady() && actorState.isIdleOrEngaged()) {
            actorState.appearanceState = (actorState.appearanceState + 1) % 2
            formChangeTimer.reset()
        }

        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        if (actorState.appearanceState == 1) { aggregate.evasionRate += 10 }
    }

}