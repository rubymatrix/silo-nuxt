package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.actor.components.getInventory
import xim.poc.ui.ChatLog
import xim.resource.InventoryItems

class InventoryGainEvent(
    val sourceId: ActorId,
    val itemId: Int,
    val quantity: Int = 1,
    val context: AttackContext? = null,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()
        source.getInventory().addItem(itemId, quantity)

        if (source.isPlayer()) {
            val inventoryItemInfo = InventoryItems[itemId]
            AttackContext.compose(context) { ChatLog.gainedItem(source, inventoryItemInfo, quantity) }
        }

        return emptyList()
    }

}