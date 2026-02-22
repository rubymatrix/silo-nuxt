package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.SceneManager
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.getDoorState
import xim.resource.DatId

class DoorCloseEvent(val doorActorId: ActorId) : Event {

    override fun apply(): List<Event> {
        val doorState = ActorStateManager[doorActorId]?.getDoorState() ?: return emptyList()
        if (!doorState.open) { return emptyList() }

        doorState.close()
        return emptyList()
    }

}