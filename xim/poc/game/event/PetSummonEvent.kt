package xim.poc.game.event

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.PetController
import xim.poc.game.ActorMount
import xim.poc.game.ActorPet
import xim.poc.game.ActorStateManager
import xim.poc.game.ActorType
import xim.resource.DatId

class PetSummonEvent(
    val ownerId: ActorId,
    val lookId: Int,
    val name: String,
    val entranceAnimation: DatId = DatId.spop,
    val stationary: Boolean = false,
): Event {

    override fun apply(): List<Event> {
        val ownerState = ActorStateManager[ownerId] ?: return emptyList()

        val position = Vector3f().copyFrom(ownerState.position)
        position += Matrix4f().rotateYInPlace(ownerState.rotation).transformInPlace(Vector3f(2f, 0f, 0f))

        val npcLook = ModelLook.npc(lookId)
        val controller = if (stationary) { NoOpActorController() } else { PetController() }

        val petActorId = ActorStateManager.nextId()
        ownerState.pet = petActorId

        val initialActorState = InitialActorState(
            presetId = petActorId,
            name = name,
            type = ActorType.AllyNpc,
            position = position,
            modelLook = npcLook,
            movementController = controller,
            dependentSettings = DependentSettings(ownerId, ActorPet),
            popRoutines = listOf(entranceAnimation),
        )

        return listOf(ActorCreateEvent(initialActorState))
    }

}