package xim.poc.game.configuration.v0.interactions

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.configuration.constants.itemFernStone_9211
import xim.poc.game.configuration.v0.ItemDefinitions
import xim.poc.game.configuration.v0.abyssea.AugmentRerollUiState
import xim.resource.DatId
import xim.resource.EquipSlot
import xim.resource.InventoryItems

object BaseCampRerollInteraction: NpcInteraction {

    private val validSlots = setOf(
        EquipSlot.Head,
        EquipSlot.Body,
        EquipSlot.Hands,
        EquipSlot.Legs,
        EquipSlot.Feet,
        EquipSlot.Back,
        EquipSlot.Waist
    )

    override fun onInteraction(npcId: ActorId) {
        AugmentRerollUiState(itemFernStone_9211 to 1, this::validItemFilter).push()
        ActorManager[npcId]?.playRoutine(DatId("cait"))
        ActorStateManager[npcId]?.faceToward(ActorStateManager.player())
    }

    private fun validItemFilter(inventoryItem: InventoryItem?): Boolean {
        inventoryItem ?: return false

        val definition = ItemDefinitions[inventoryItem]
        if (definition.augmentSlots.isEmpty()) { return false }

        val augments = inventoryItem.augments ?: return false
        if (augments.rankLevel < 5) { return false }

        val slots = InventoryItems[inventoryItem.id].equipmentItemInfo?.equipSlots ?: return false
        if (!validSlots.containsAll(slots)) { return false }

        return true
    }

}