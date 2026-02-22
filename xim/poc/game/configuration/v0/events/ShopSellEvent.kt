package xim.poc.game.configuration.v0.events

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.isEquipped
import xim.poc.game.actor.components.adjustCurrency
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.event.Event
import xim.poc.game.event.InventoryItemTransferEvent
import xim.poc.game.actor.components.getInventory

class ShopSellEvent(
    val vendorId: ActorId,
    val shopperId: ActorId,
    val selectedItemId: InternalItemId,
    val quantity: Int,
) : Event {

    companion object {
        const val sellPriceDivider = 4
    }

    override fun apply(): List<Event> {
        val vendor = ActorStateManager[vendorId] ?: return emptyList()
        val shopper = ActorStateManager[shopperId] ?: return emptyList()

        val item = shopper.getInventory().getByInternalId(selectedItemId) ?: return emptyList()
        if (quantity > item.quantity || quantity <= 0) { return emptyList() }
        if (shopper.isEquipped(item)) { return emptyList() }

        val itemPrice = GameV0.getItemPrice(vendorId, item) ?: return emptyList()
        shopper.adjustCurrency(itemPrice.first, quantity * itemPrice.second / sellPriceDivider)

        return listOf(InventoryItemTransferEvent(
            sourceId = shopperId,
            destinationId = vendorId,
            inventoryItemId = selectedItemId,
            quantity = quantity,
            actionContext = null,
        ))
    }

}