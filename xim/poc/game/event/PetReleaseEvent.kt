package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.resource.DatId

class PetReleaseEvent(
    val ownerId: ActorId
): Event {

    override fun apply(): List<Event> {
        val ownerState = ActorStateManager[ownerId] ?: return emptyList()
        val petId = ownerState.pet
        ownerState.pet = null

        val petState = ActorStateManager[petId] ?: return emptyList()
        petState.setHp(0)

        val currentModel = ActorManager[petState.id]
        currentModel?.enqueueModelRoutineIfReady(DatId("sdep"))

        return emptyList()
    }

}