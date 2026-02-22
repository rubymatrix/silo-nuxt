package xim.poc.game.configuration.v0.events

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.event.Event
import xim.poc.game.actor.components.getInventory

class InventoryItemSlotAugmentEvent(
    val sourceId: ActorId,
    val itemId: InternalItemId,
    val upgradeMaterialId: InternalItemId,
    val slot: Int,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()

        val item = source.getInventory().getByInternalId(itemId) ?: return emptyList()
        val upgradeMaterial = source.getInventory().getByInternalId(upgradeMaterialId) ?: return emptyList()

        val augment = upgradeMaterial.slottedAugments?.get(0) ?: return emptyList()
        val itemAugments = item.slottedAugments ?: return emptyList()

        itemAugments.set(slot, augment)
        source.getInventory().discardItem(upgradeMaterialId)

        return emptyList()
    }

}