package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.poc.game.PartyManager
import xim.resource.DatId

class TrustReleaseEvent(
    val sourceId: ActorId,
    val targetId: ActorId,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()
        val target = ActorStateManager[targetId] ?: return emptyList()

        if (target.owner != sourceId) { return emptyList() }

        val party = PartyManager[sourceId]
        party.removeMember(target.id)

        if (!target.isDead()) {
            target.setHp(0)
            val targetModel = ActorManager[targetId]
            targetModel?.enqueueModelRoutineIfReady(DatId("sdep"))
        }

        return emptyList()
    }

}