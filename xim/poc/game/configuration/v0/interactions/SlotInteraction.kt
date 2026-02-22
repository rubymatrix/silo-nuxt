package xim.poc.game.configuration.v0.interactions

import xim.poc.ActorManager
import xim.poc.audio.AudioManager
import xim.poc.audio.SystemSound
import xim.poc.game.*
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.SlottedAugments
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.events.InventoryItemSlotAugmentEvent
import xim.poc.game.configuration.v0.toDescription
import xim.poc.ui.InventoryFilter
import xim.poc.ui.InventoryUi
import xim.poc.ui.ShiftJis
import xim.resource.DatId

private object SlotEquipmentFilter: InventoryFilter {
    override fun apply(inventoryItem: InventoryItem): Boolean {
        val info = inventoryItem.info()
        return inventoryItem.slottedAugments != null && info.equipmentItemInfo != null && !info.isMainHandWeapon()
    }
}

private class SlotUpgradeMaterialFilter(val itemId: Int): InventoryFilter {
    override fun apply(inventoryItem: InventoryItem): Boolean {
        return inventoryItem.id == itemId
    }
}

class SlotInteractionUiState(val slotMaterial: ItemId) {

    private val slotItemSelectContext: UiState
    private val slotUpgradeItemSelectContext: UiState

    init {
        slotUpgradeItemSelectContext = UiState(
            additionalDraw = { InventoryUi.drawInventoryItems(it, filter = SlotUpgradeMaterialFilter(slotMaterial)) },
            drawParent = true,
            parentRelative = ParentRelative.RightOfParent,
            focusMenu = "menu    inventor",
            resetCursorIndexOnPush = false,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { InventoryUi.getItems(filter = SlotUpgradeMaterialFilter(slotMaterial)).size },
            hideGauge = true,
            locksMovement = true,
        ) {
            if (UiStateHelper.isEnterPressed() && getSelectedUpgradeMaterial() == null) {
                AudioManager.playSystemSoundEffect(SystemSound.Invalid)
                true
            } else if (UiStateHelper.isEnterPressed()) {
                pushSlotSelectionContext()
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        slotItemSelectContext = UiState(
            additionalDraw = { InventoryUi.drawInventoryItems(it, filter = SlotEquipmentFilter) },
            focusMenu = "menu    inventor",
            childStates = { listOf(slotUpgradeItemSelectContext) },
            resetCursorIndexOnPush = false,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { InventoryUi.getItems(filter = SlotEquipmentFilter).size },
            hideGauge = true,
            locksMovement = true,
        ) {
            if (UiStateHelper.isEnterPressed() && getSelectedUpgradeItem() == null) {
                AudioManager.playSystemSoundEffect(SystemSound.Invalid)
                true
            } else if (UiStateHelper.isEnterPressed()) {
                UiStateHelper.pushState(slotUpgradeItemSelectContext, SystemSound.MenuSelect)
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }
    }

    fun push() {
        UiStateHelper.pushState(slotItemSelectContext)
    }

    private fun getSelectedUpgradeItem(): InventoryItem? {
        return InventoryUi.getSelectedItem(slotItemSelectContext, filter = SlotEquipmentFilter)
    }

    private fun getSelectedUpgradeMaterial(): InventoryItem? {
        return InventoryUi.getSelectedItem(slotUpgradeItemSelectContext, filter = SlotUpgradeMaterialFilter(slotMaterial))
    }

    private fun pushSlotSelectionContext() {
        val item = getSelectedUpgradeItem() ?: return
        val slots = item.slottedAugments ?: return

        val material = getSelectedUpgradeMaterial() ?: return
        val materialSlot = material.slottedAugments?.get(0) ?: return

        val matchingSlot = slots.augments.values.indexOfFirst { it.augmentId == materialSlot.augmentId }
        if (matchingSlot >= 0) {
            handleSlotChoice(matchingSlot)
            return
        }

        val firstEmptySlot = (0 until slots.slots).indexOfFirst { slots.get(it).augmentId == AugmentId.Blank }
        if (firstEmptySlot >= 0) {
            handleSlotChoice(firstEmptySlot)
            return
        }

        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Go back.", -1)

        for (i in 0 until slots.slots) {
            val augment = slots.get(i)
            val description = augment.augmentId.toDescription(augment.potency)
            options += QueryMenuOption(value = i, text = "${ShiftJis.colorInfo}[${i+1}] $description")
        }

        UiStateHelper.openQueryMode(
            prompt = "Overwrite which slot?",
            options = options,
            callback = { handleSlotChoice(chosenSlot = it?.value) },
            drawFn = { drawPreviewItem(item) },
            systemSound = SystemSound.MenuSelect,
        )
    }

    private fun handleSlotChoice(chosenSlot: Int?): QueryMenuResponse {
        if (chosenSlot == null || chosenSlot < 0) { return QueryMenuResponse.pop }

        val upgradeMaterial = getSelectedUpgradeMaterial() ?: return QueryMenuResponse.pop
        val augment = upgradeMaterial.slottedAugments?.get(0) ?: return QueryMenuResponse.pop

        val item = getSelectedUpgradeItem() ?: return QueryMenuResponse.pop
        val slottedAugments = item.slottedAugments ?: return QueryMenuResponse.pop

        val fakeItem = item.copy()
        fakeItem.slottedAugments = SlottedAugments(slottedAugments.slots, HashMap(slottedAugments.augments))
            .also { it.set(chosenSlot, augment) }

        val options = listOf(
            QueryMenuOption(text = "No", value = 0),
            QueryMenuOption(text = "Yes", value = 1),
        )

        UiStateHelper.openQueryMode(
            prompt = "Proceed?",
            options = options,
            closeable = true,
            callback = { handleConfirmation(it, chosenSlot) },
            drawFn = { drawPreviewItem(fakeItem) },
        )

        return QueryMenuResponse.noop
    }

    private fun handleConfirmation(queryMenuOption: QueryMenuOption?, chosenSlot: Int): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value <= 0) { return QueryMenuResponse.pop }

        val upgradeMaterial = getSelectedUpgradeMaterial() ?: return QueryMenuResponse.pop
        val item = getSelectedUpgradeItem() ?: return QueryMenuResponse.pop

        GameEngine.submitEvent(InventoryItemSlotAugmentEvent(
            sourceId = ActorStateManager.playerId,
            itemId = item.internalId,
            upgradeMaterialId = upgradeMaterial.internalId,
            slot = chosenSlot,
        ))

        val player = ActorManager.player()
        MiscEffects.playEffect(player, player, 0x1347, DatId.synthesisNq)

        UiStateHelper.popState()
        UiStateHelper.popState()

        return QueryMenuResponse.noop
    }

    private fun drawPreviewItem(item: InventoryItem) {
        val description = GameV0Helpers.getItemDescriptionInternal(inventoryItem = item)
        InventoryUi.drawSelectedInventoryItem(item, itemDescription = description)
    }

}