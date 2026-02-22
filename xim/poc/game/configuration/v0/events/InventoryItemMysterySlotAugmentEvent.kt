package xim.poc.game.configuration.v0.events

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.SlottedAugment
import xim.poc.game.configuration.v0.ItemDefinitions
import xim.poc.game.configuration.v0.interactions.ItemId
import xim.poc.game.event.Event
import xim.poc.game.actor.components.getInventory

class InventoryItemMysterySlotAugmentEvent(
    val sourceId: ActorId,
    val itemId: InternalItemId,
    val upgradeMaterialId: ItemId,
    val upgradeValue: Int,
    val slot: Int,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()

        val item = source.getInventory().getByInternalId(itemId) ?: return emptyList()
        val itemAugments = item.slottedAugments ?: return emptyList()

        val upgradeDefinition = ItemDefinitions.definitionsById[upgradeMaterialId]?.mysterySlot ?: return emptyList()

        val augment = SlottedAugment(upgradeDefinition.augmentId, upgradeValue)
        itemAugments.set(slot, augment)

        return emptyList()
    }

}