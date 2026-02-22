package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ModelLook
import xim.poc.game.ActorStateManager

class ActorUpdateBaseLookEvent(
    val actorId: ActorId,
    val look: ModelLook,
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[actorId] ?: return emptyList()
        actorState.setBaseLook(look.copy())

        return listOf(ActorEquipEvent(actorId, emptyMap()))
    }

}