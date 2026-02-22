package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.SceneManager
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.getDoorState
import xim.resource.DatId
import kotlin.time.Duration.Companion.seconds

class DoorOpenEvent(
    val sourceId: ActorId?,
    val doorId: DatId,
): Event {

    override fun apply(): List<Event> {
        val doorNpc = SceneManager.getCurrentScene().getNpcs().npcsByDatId[doorId] ?: return emptyList()
        val doorActorId = doorNpc.actorId

        val doorState = ActorStateManager[doorActorId]?.getDoorState() ?: return emptyList()
        if (doorState.open) { return emptyList() }

        val autoCloseDuration = if (sourceId == ActorStateManager.playerId) { 5.seconds } else { null }
        doorState.open(autoCloseDuration)

        return emptyList()
    }

}