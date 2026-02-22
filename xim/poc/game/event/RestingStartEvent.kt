package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorRestingState
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.RestingState

class RestingStartEvent(
    val sourceId: ActorId
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()
        if (!actorState.isIdle()) { return emptyList() }

        val action = ActorRestingState(kneeling = true)
        if (!actorState.initiateAction(action)) { return emptyList() }

        val component = RestingState(action)
        actorState.components[RestingState::class] = component

        return emptyList()
    }

}