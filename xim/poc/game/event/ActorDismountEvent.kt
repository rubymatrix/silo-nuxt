package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.poc.game.StatusEffect
import xim.resource.DatId

class ActorDismountEvent(
    val actorId: ActorId
): Event {

    override fun apply(): List<Event> {
        val riderState = ActorStateManager[actorId] ?: return emptyList()
        riderState.expireStatusEffect(StatusEffect.Mounted)

        val mountSettings = riderState.mountedState ?: return emptyList()
        riderState.mountedState = null

        val riderActor = ActorManager[actorId]
        riderActor?.transitionToIdle(0f)

        val mountState = ActorStateManager[mountSettings.id] ?: return emptyList()
        mountState.setHp(0)

        val mountActor = ActorManager[mountSettings.id]
        mountActor?.playRoutine(DatId("coff"))

        return emptyList()
    }

}