package xim.poc.game.configuration.v0.interactions

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.audio.AudioManager
import xim.poc.audio.SystemSound
import xim.poc.game.*
import xim.poc.game.actor.components.*
import xim.poc.game.configuration.v0.GameV0Helpers.getItemDescriptionInternal
import xim.poc.game.configuration.v0.ItemDefinitions
import xim.poc.game.configuration.v0.MysteryMeldRanges
import xim.poc.game.configuration.v0.events.InventoryItemMysterySlotAugmentEvent
import xim.poc.game.configuration.v0.toDescription
import xim.poc.ui.*
import xim.resource.DatId

private object MysterySlotEquipmentFilter: InventoryFilter {
    override fun apply(inventoryItem: InventoryItem): Boolean {
        return inventoryItem.slottedAugments != null && inventoryItem.info().isMainHandWeapon()
    }
}

private object MysterySlotUpgradeMaterialFilter: InventoryFilter {
    override fun apply(inventoryItem: InventoryItem): Boolean {
        return ItemDefinitions.getNullable(inventoryItem)?.mysterySlot != null
    }
}

object MysterySlotInteraction: NpcInteraction {

    private val slotItemSelectContext: UiState
    private val slotUpgradeItemSelectContext: UiState

    init {
        slotUpgradeItemSelectContext = UiState(
            additionalDraw = { InventoryUi.drawInventoryItems(it, filter = MysterySlotUpgradeMaterialFilter, descriptionProvider = this::getDescriptionWithMaxes) },
            drawParent = true,
            parentRelative = ParentRelative.RightOfParent,
            focusMenu = "menu    inventor",
            resetCursorIndexOnPush = false,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { InventoryUi.getItems(filter = MysterySlotUpgradeMaterialFilter).size },
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
            additionalDraw = { InventoryUi.drawInventoryItems(it, filter = MysterySlotEquipmentFilter) },
            focusMenu = "menu    inventor",
            childStates = { listOf(slotUpgradeItemSelectContext) },
            resetCursorIndexOnPush = false,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { InventoryUi.getItems(filter = MysterySlotEquipmentFilter).size },
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

    override fun onInteraction(npcId: ActorId) {
        ActorManager[npcId]?.playRoutine(DatId("hap0"))
        UiStateHelper.pushState(slotItemSelectContext)
    }

    private fun getSelectedUpgradeItem(): InventoryItem? {
        return InventoryUi.getSelectedItem(slotItemSelectContext, filter = MysterySlotEquipmentFilter)
    }

    private fun getSelectedUpgradeMaterial(): InventoryItem? {
        return InventoryUi.getSelectedItem(slotUpgradeItemSelectContext, filter = MysterySlotUpgradeMaterialFilter)
    }

    private fun pushSlotSelectionContext() {
        val item = getSelectedUpgradeItem() ?: return
        val slots = item.slottedAugments ?: return

        val material = getSelectedUpgradeMaterial() ?: return
        val mysterySlot = ItemDefinitions[material].mysterySlot ?: return

        val matchingSlot = slots.augments.values.indexOfFirst { it.augmentId == mysterySlot.augmentId }
        if (matchingSlot >= 0) {
            handleSlotChoice(matchingSlot, item, material)
            return
        }

        val firstEmptySlot = (0 until slots.slots).indexOfFirst { slots.get(it).augmentId == AugmentId.Blank }
        if (firstEmptySlot >= 0) {
            handleSlotChoice(firstEmptySlot, item, material)
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
            callback = { UiStateHelper.popState(); handleSlotChoice(chosenSlot = it?.value, item, material ) },
            drawFn = { drawPreviewItem(item) },
            systemSound = SystemSound.MenuSelect,
        )
    }

    private fun handleSlotChoice(chosenSlot: Int?, item: InventoryItem, upgradeMaterial: InventoryItem): QueryMenuResponse {
        if (chosenSlot == null || chosenSlot < 0) { return QueryMenuResponse.noop }

        val mysterySlotDefinition = ItemDefinitions[upgradeMaterial].mysterySlot ?: return QueryMenuResponse.pop
        val slottedAugments = item.slottedAugments ?: return QueryMenuResponse.pop

        val randomAugmentValue = MysteryMeldRanges.getRandomValue(item, upgradeMaterial.id)
        val augment = SlottedAugment(mysterySlotDefinition.augmentId, randomAugmentValue)

        val successfulDiscard = ActorStateManager.player().discardNotEquippedItems(itemId = upgradeMaterial.id, quantity = 1)
        if (!successfulDiscard) { return QueryMenuResponse.pop }

        val remaining = ActorStateManager.player().countNotEquippedItems(upgradeMaterial.id)
        ChatLog("Consumed a ${ShiftJis.colorItem}${upgradeMaterial.info().logName}${ShiftJis.colorClear}. Remaining: $remaining.", ChatLogColor.Info)

        val fakeItem = item.copy()
        fakeItem.slottedAugments = SlottedAugments(slottedAugments.slots, HashMap(slottedAugments.augments))
            .also { it.set(chosenSlot, augment) }

        val tryAgainEnabled = if (remaining > 0) { "" } else { ShiftJis.colorGrey }

        val options = listOf(
            QueryMenuOption("${tryAgainEnabled}No - try again.", value = 0),
            QueryMenuOption(text = "No - exit.", value = 1),
            QueryMenuOption(text = "Yes!", value = 2),
        )

        UiStateHelper.openQueryMode(
            prompt = "Proceed with meld?",
            options = options,
            closeable = true,
            callback = { handleConfirmation(it, item, upgradeMaterial, chosenSlot, randomAugmentValue) },
            drawFn = { drawPreviewItem(fakeItem) },
        )

        return QueryMenuResponse.noop
    }

    private fun handleConfirmation(queryMenuOption: QueryMenuOption?, item: InventoryItem, upgradeMaterial: InventoryItem, chosenSlot: Int, randomAugmentValue: Int): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value < 0) { return QueryMenuResponse.pop }

        val remaining = ActorStateManager.player().countNotEquippedItems(upgradeMaterial.id)
        if (remaining == 0 && queryMenuOption.value == 0) { return QueryMenuResponse.noop(SystemSound.Invalid) }

        if (queryMenuOption.value == 0) {
            UiStateHelper.popState()
            handleSlotChoice(chosenSlot, item, upgradeMaterial)
            return QueryMenuResponse.noop
        }

        if (queryMenuOption.value == 1) {
            return QueryMenuResponse.pop
        }

        GameEngine.submitEvent(InventoryItemMysterySlotAugmentEvent(
            sourceId = ActorStateManager.playerId,
            itemId = item.internalId,
            upgradeMaterialId = upgradeMaterial.id,
            upgradeValue = randomAugmentValue,
            slot = chosenSlot,
        ))

        val player = ActorManager.player()
        MiscEffects.playEffect(player, player, 0x1346, DatId.synthesisNq)

        return QueryMenuResponse.pop
    }

    private fun drawPreviewItem(item: InventoryItem) {
        val description = getItemDescriptionInternal(inventoryItem = item)
        InventoryUi.drawSelectedInventoryItem(item, itemDescription = description)
    }

    private fun getDescriptionWithMaxes(material: InventoryItem): InventoryItemDescription {
        val item = getSelectedUpgradeItem() ?: return getItemDescriptionInternal(inventoryItem = material)
        val (min, max) = MysteryMeldRanges.getRange(item, material.id) ?: return getItemDescriptionInternal(inventoryItem = material)
        return getItemDescriptionInternal(inventoryItem = material, mysterySlotMax = "$min${ShiftJis.longTilde}$max")
    }

}