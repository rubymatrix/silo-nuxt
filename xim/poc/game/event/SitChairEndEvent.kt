package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorSitChairState
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.RestingState

class SitChairEndEvent(
    val actorId: ActorId,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[actorId] ?: return emptyList()

        val actionState = source.actionState
        if (actionState !is ActorSitChairState) { return emptyList() }

        val component = source.getComponentAs(RestingState::class) ?: return emptyList()
        component.stopResting()

        ActorStateManager[actionState.chairId]?.setHp(0)
        return emptyList()
    }

}