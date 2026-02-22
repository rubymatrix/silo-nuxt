package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.event.Event

class FamilyUmbrilBehavior(actorState: ActorState): V0MonsterController(actorState) {

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actorState.isDead()) {
            actorState.appearanceState = if (actorState.isEngaged()) { 0 } else { 1 }
        }

        return super.update(elapsedFrames)
    }

}