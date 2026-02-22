package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.RestingState

class RestingEndEvent(
    val sourceId: ActorId
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()
        val component = actorState.getComponentAs(RestingState::class) ?: return emptyList()
        component.stopResting()
        return emptyList()
    }

}