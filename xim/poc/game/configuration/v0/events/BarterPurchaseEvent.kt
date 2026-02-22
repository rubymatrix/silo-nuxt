package xim.poc.game.configuration.v0.events

import xim.poc.ActorId
import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.interactions.BarterItem
import xim.poc.game.actor.components.discardNotEquippedItems
import xim.poc.game.event.Event
import xim.poc.game.actor.components.getInventory
import xim.poc.ui.ChatLog

class BarterPurchaseEvent(
    val sourceId: ActorId,
    val vendorId: ActorId,
    val barterItem: BarterItem,
    val purchaseQuantity: Int,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()
        if (!consumeRequiredItems(source)) { return emptyList() }

        val itemQuantity = purchaseQuantity * barterItem.purchaseItem.quantity
        val purchasedItem = GameV0.generateItem(dropDefinition = barterItem.purchaseItem.copy(quantity = itemQuantity))
        source.getInventory().addItem(purchasedItem)

        if (source.isPlayer()) { ChatLog.gainedItem(source, purchasedItem, itemQuantity) }

        return emptyList()
    }

    private fun consumeRequiredItems(source: ActorState): Boolean {
        for ((requiredItem, requiredQuantity) in barterItem.requiredItems) {
            val hasSufficient = source.discardNotEquippedItems(itemId = requiredItem, quantity = purchaseQuantity * requiredQuantity)
            if (!hasSufficient) { return false }
        }

        return true
    }

}