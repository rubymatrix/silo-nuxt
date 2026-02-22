package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.poc.game.PartyManager

class ActorDeleteEvent(
    val id: ActorId,
): Event {

    override fun apply(): List<Event> {
        PartyManager[id].removeMember(id)
        ActorStateManager.delete(id)
        ActorManager.remove(id)
        return emptyList()
    }

}