package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.CurrencyType
import xim.poc.game.GameState
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.adjustCurrency
import xim.poc.game.actor.components.getInventory
import xim.poc.ui.ChatLog
import xim.resource.ItemListType

class InventoryItemTransferEvent(
    val sourceId: ActorId,
    val destinationId: ActorId,
    val inventoryItemId: InternalItemId,
    val quantity: Int? = null,
    val actionContext: AttackContext? = null,
) : Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()
        val destination = ActorStateManager[destinationId] ?: return emptyList()

        val inventoryItem = source.getInventory().getByInternalId(inventoryItemId) ?: return emptyList()
        if (!GameState.getGameMode().canObtainItem(source, destination, inventoryItem)) { return emptyList() }

        if (inventoryItem.info().type == ItemListType.Currency) {
            val amount = quantity ?: inventoryItem.quantity
            source.getInventory().discardItem(inventoryItemId, amount = amount)
            destination.adjustCurrency(CurrencyType.Gil, amount = amount)
        } else if (inventoryItem.info().isStackable() && quantity != null) {
            if (quantity > inventoryItem.quantity) { return emptyList() }
            source.getInventory().discardItem(inventoryItemId, amount = quantity)
            destination.getInventory().addItem(inventoryItem.id, quantity = quantity)
        } else {
            source.getInventory().discardItem(inventoryItemId)
            destination.getInventory().addItem(inventoryItem)
        }

        if (destination.isPlayer()) {
            AttackContext.compose(actionContext) {
                ChatLog.gainedItem(destination, inventoryItem, quantity ?: inventoryItem.quantity)
            }
        }

        return emptyList()
    }


}