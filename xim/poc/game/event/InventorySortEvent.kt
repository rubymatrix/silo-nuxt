package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.getInventory

class InventorySortEvent(
    val sourceId: ActorId
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()
        source.getInventory().sort()
        return emptyList()
    }

}