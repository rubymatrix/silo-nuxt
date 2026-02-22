package xim.poc.game.configuration.v0.events

import xim.poc.ActorId
import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.configuration.v0.GameV0Helpers.getRemainingMeldPotential
import xim.poc.game.configuration.v0.ItemCapacityAugment
import xim.poc.game.configuration.v0.ItemDefinitions
import xim.poc.game.event.Event
import xim.poc.game.actor.components.getInventory

class InventoryItemMeldEvent(
    val sourceId: ActorId,
    val targetItemId: InternalItemId,
    val upgradeItemId: InternalItemId,
    val upgradeItemQuantity: Int,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()

        val inventoryItem = source.getInventory().getByInternalId(targetItemId) ?: return emptyList()
        val fixedAugments = inventoryItem.fixedAugments ?: return emptyList()

        val upgradeAugment = getUpgradeAugment(source) ?: return emptyList()

        // Validate resources
        val upgradeItem = source.getInventory().getByInternalId(upgradeItemId) ?: return emptyList()
        if (upgradeItem.quantity < upgradeItemQuantity) { return emptyList() }

        val capacityConsumed = upgradeAugment.capacity * upgradeItemQuantity
        if (fixedAugments.capacityRemaining < capacityConsumed) { return emptyList() }

        // Consume resources
        source.getInventory().discardItem(upgradeItemId, upgradeItemQuantity)
        fixedAugments.capacityRemaining -= capacityConsumed

        // Apply bonus
        val augmentId = upgradeAugment.augmentId
        val remainingPotential = getRemainingMeldPotential(inventoryItem)[augmentId] ?: 0

        val fixedAugment = fixedAugments.getOrCreate(augmentId)
        fixedAugment.potency += (upgradeAugment.potency * upgradeItemQuantity).coerceAtMost(remainingPotential)

        return emptyList()
    }

    private fun getUpgradeAugment(source: ActorState): ItemCapacityAugment? {
        val item = source.getInventory().getByInternalId(upgradeItemId) ?: return null
        val itemDefinition = ItemDefinitions[item]
        return itemDefinition.capacityAugment
    }

}