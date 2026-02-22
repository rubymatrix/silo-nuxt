package xim.poc.game.configuration.v0.events

import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.actor.components.*
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.ItemDefinitions
import xim.poc.game.event.ActorEquipEvent
import xim.poc.game.event.Event
import xim.resource.InventoryItemInfo
import xim.resource.InventoryItems

class WeaponUpgradeEvent(
    val sourceId: ActorId,
    val weaponId: InternalItemId,
    val destinationWeaponId: Int,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()

        val sourceWeapon = source.getInventory().getByInternalId(weaponId) ?: return emptyList()

        val destinationWeaponInfo = InventoryItems[destinationWeaponId]
        if (!consumeNeededItemsForWeaponUpgrade(sourceWeapon, destinationWeaponInfo)) { return emptyList() }

        val equippedSlot = source.getEquippedSlot(sourceWeapon)
        source.getInventory().discardItem(weaponId)

        val upgradedWeapon = GameV0Helpers.generateUpgradedWeapon(sourceWeapon, destinationWeaponInfo)
        source.getInventory().addItem(upgradedWeapon)

        val events = ArrayList<Event>()

        if (equippedSlot != null) {
            events += ActorEquipEvent(sourceId = sourceId, equipment = mapOf(equippedSlot to upgradedWeapon.internalId))
        }

        return events
    }

    private fun consumeNeededItemsForWeaponUpgrade(source: InventoryItem, destinationItemInfo: InventoryItemInfo): Boolean {
        val upgradeOption = ItemDefinitions[source].upgradeOptions
            .firstOrNull { it.destinationId == destinationItemInfo.itemId } ?: return false

        val itemRequirement = upgradeOption.itemRequirement ?: return true

        val player = ActorStateManager.player()
        return player.discardNotEquippedItems(itemId = itemRequirement.itemId, quantity = itemRequirement.quantity)
    }

}