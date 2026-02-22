package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.isEquipped
import xim.poc.game.actor.components.getInventory

class InventoryDiscardEvent(
    val sourceId: ActorId,
    val internalItemId: InternalItemId,
): Event {

    override fun apply(): List<Event> {
        val state = ActorStateManager[sourceId] ?: return emptyList()

        val item = state.getInventory().getByInternalId(internalItemId) ?: return emptyList()
        if (state.isEquipped(item)) { return emptyList() }

        state.getInventory().discardItem(internalItemId)
        return emptyList()
    }

}