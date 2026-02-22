package xim.poc.game.configuration.v0.events

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.adjustCurrency
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.event.Event
import xim.poc.game.event.InventoryItemTransferEvent
import xim.poc.game.actor.components.getInventory

class ShopPurchaseEvent(
    val vendorId: ActorId,
    val shopperId: ActorId,
    val selectedItemId: InternalItemId,
    val quantity: Int,
): Event {

    override fun apply(): List<Event> {
        val vendor = ActorStateManager[vendorId] ?: return emptyList()
        val shopper = ActorStateManager[shopperId] ?: return emptyList()

        val item = vendor.getInventory().getByInternalId(selectedItemId) ?: return emptyList()
        if (quantity > item.quantity || quantity <= 0) { return emptyList() }

        val itemPrice = GameV0.getItemPrice(vendorId, item) ?: return emptyList()

        if (!shopper.adjustCurrency(itemPrice.first, quantity * -itemPrice.second)) { return emptyList() }

        return listOf(InventoryItemTransferEvent(
            sourceId = vendorId,
            destinationId = shopperId,
            inventoryItemId = selectedItemId,
            quantity = quantity,
            actionContext = null,
        ))
    }

}