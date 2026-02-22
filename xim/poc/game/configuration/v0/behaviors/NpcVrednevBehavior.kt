package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.configuration.ActorBehaviorController
import xim.poc.game.event.Event

class NpcVrednevBehavior(val actorState: ActorState): ActorBehaviorController {

    override fun update(elapsedFrames: Float): List<Event> {
        val playerDistance = actorState.getTargetingDistance(ActorStateManager.player())

        if (playerDistance > 5f) {
            actorState.appearanceState = 1
        } else if (playerDistance < 4f) {
            actorState.appearanceState = 0
        }

        return emptyList()
    }

}