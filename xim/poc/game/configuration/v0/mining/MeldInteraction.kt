package xim.poc.game.configuration.v0.mining

import xim.poc.ActorManager
import xim.poc.audio.AudioManager
import xim.poc.audio.SystemSound
import xim.poc.game.*
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.GemHelper
import xim.poc.game.configuration.v0.ItemDefinitions
import xim.poc.game.configuration.v0.events.InventoryItemMeldEvent
import xim.poc.gl.ByteColor
import xim.poc.ui.*
import xim.resource.DatId
import kotlin.math.ceil
import kotlin.math.roundToInt

private object AccessoryEquipmentFilter: InventoryFilter {
    override fun apply(inventoryItem: InventoryItem): Boolean {
        return inventoryItem.info().isRingOrEarring()
    }
}

private class GemUpgradeMaterialFilter: InventoryFilter {
    override fun apply(inventoryItem: InventoryItem): Boolean {
        return GemHelper.isGem(inventoryItem.id)
    }
}

object AccessoryGemMeldInteractionUi {

    private val quantityInput = QuantityInputController(maxValueProvider = this::getMaxUpgradeMaterialQuantity)

    private val meldItemSelectContext: UiState
    private val meldUpgradeItemSelectContext: UiState
    private val meldInputAmountContext: UiState

    init {
        meldInputAmountContext = UiState(
            focusMenu = "menu    itemctrl",
            drawParent = true,
            additionalDraw = { drawItemControl(it); drawPreviewItemWithBonuses() }
        ) {
            quantityInput.refresh()

            if (UiStateHelper.isEnterPressed()) {
                UiStateHelper.popState(SystemSound.MenuSelect)
                openBasicMeldConfirmation()
                true
            } else if (quantityInput.processInput()) {
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        meldUpgradeItemSelectContext = UiState(
            additionalDraw = { InventoryUi.drawInventoryItems(it, filter = GemUpgradeMaterialFilter(), colorProvider = AccessoryGemMeldInteractionUi::getUpgradeMaterialColorDisplay) },
            drawParent = true,
            parentRelative = ParentRelative.RightOfParent,
            focusMenu = "menu    inventor",
            resetCursorIndexOnPush = false,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { InventoryUi.getItems(filter = GemUpgradeMaterialFilter()).size },
            hideGauge = true,
        ) {
            if (UiStateHelper.isEnterPressed() && getMaxUpgradeMaterialQuantity() == 0) {
                AudioManager.playSystemSoundEffect(SystemSound.Invalid)
                true
            } else if (UiStateHelper.isEnterPressed()) {
                UiStateHelper.pushState(meldInputAmountContext, SystemSound.MenuSelect)
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        meldItemSelectContext = UiState(
            additionalDraw = { InventoryUi.drawInventoryItems(it, filter = AccessoryEquipmentFilter, descriptionProvider = AccessoryGemMeldInteractionUi::getDescriptionWithMaxes) },
            focusMenu = "menu    inventor",
            childStates = { listOf(meldUpgradeItemSelectContext) },
            resetCursorIndexOnPush = false,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { InventoryUi.getItems(filter = AccessoryEquipmentFilter).size },
            hideGauge = true,
        ) {
            if (UiStateHelper.isEnterPressed()) {
                UiStateHelper.pushState(meldUpgradeItemSelectContext, SystemSound.MenuSelect)
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
        UiStateHelper.pushState(meldItemSelectContext)
    }

    private fun drawItemControl(uiState: UiState) {
        quantityInput.draw(uiState)
    }

    private fun getSelectedUpgradeItem(): InventoryItem? {
        return InventoryUi.getSelectedItem(meldItemSelectContext, filter = AccessoryEquipmentFilter)
    }

    private fun getSelectedUpgradeMaterial(): InventoryItem? {
        return InventoryUi.getSelectedItem(meldUpgradeItemSelectContext, filter = GemUpgradeMaterialFilter())
    }

    private fun getMaxUpgradeMaterialQuantity(): Int {
        val upgradeMaterial = getSelectedUpgradeMaterial() ?: return 0
        val (max, error) = getMaxUpgradeMaterialQuantity(upgradeMaterial)

        if (error != null) { ChatLog(error, ChatLogColor.Error) }
        return max
    }

    private fun getMaxUpgradeMaterialQuantity(upgradeMaterial: InventoryItem): Pair<Int, String?> {
        val targetItem = getSelectedUpgradeItem() ?: return (0 to null)

        val capacityRemaining = targetItem.fixedAugments?.capacityRemaining ?: return (0 to null)
        val upgradeInfo = ItemDefinitions[upgradeMaterial].capacityAugment ?: return (0 to null)

        val remainingPotential = GameV0Helpers.getRemainingMeldPotential(targetItem)
            .getOrElse(upgradeInfo.augmentId) { 0 }

        val potentialLimit = ceil(remainingPotential.toFloat() / upgradeInfo.potency).roundToInt()
        if (potentialLimit == 0) {
            return 0 to toMaximumPotentialMessage(targetItem)
        }

        val capacityLimit = capacityRemaining / upgradeInfo.capacity
        if (capacityLimit == 0) {
            return 0 to toInsufficientCapacityMessage(targetItem, upgradeInfo.capacity)
        }

        return minOf(upgradeMaterial.quantity, capacityLimit, potentialLimit) to null
    }

    private fun openBasicMeldConfirmation() {
        val targetItem = getSelectedUpgradeItem() ?: return
        val material = getSelectedUpgradeMaterial() ?: return
        val quantity = quantityInput.value

        val upgradeBonus = ItemDefinitions[material].capacityAugment ?: return
        val upgradeBonuses = mapOf(upgradeBonus.augmentId to upgradeBonus.potency * quantity)
        val capacityConsumption = upgradeBonus.capacity * quantity

        val prompt = "Consume ${ShiftJis.colorAug}$capacityConsumption capacity${ShiftJis.colorClear}?"

        val options = listOf(
            QueryMenuOption(text = "No", value = 0),
            QueryMenuOption(text = "Yes", value = 1),
        )

        UiStateHelper.openQueryMode(
            prompt = prompt,
            options = options,
            closeable = true,
            callback = this::handleBasicMeldConfirmation,
            drawFn = { drawPreviewItemWithBonuses(targetItem, upgradeBonuses, capacityConsumption) }
        )
    }

    private fun handleBasicMeldConfirmation(queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value == 0) { return QueryMenuResponse.pop }

        submitUpgrade()
        return QueryMenuResponse.pop
    }

    private fun submitUpgrade() {
        val itemToUpgrade = getSelectedUpgradeItem() ?: return
        val upgradeMaterial = getSelectedUpgradeMaterial() ?: return
        val quantity = quantityInput.value

        GameEngine.submitEvent(InventoryItemMeldEvent(ActorStateManager.playerId, itemToUpgrade.internalId, upgradeMaterial.internalId, quantity))

        val player = ActorManager.player()
        MiscEffects.playEffect(player, player, 0x1346, DatId.synthesisNq)
    }

    private fun toInsufficientCapacityMessage(item: InventoryItem, needed: Int): String {
        return "${ShiftJis.colorItem}${item.info().name}${ShiftJis.colorClear} has insufficient capacity (Needed: ${ShiftJis.colorAug}${needed}${ShiftJis.colorClear})."
    }

    private fun toMaximumPotentialMessage(item: InventoryItem): String {
        return "${ShiftJis.colorItem}${item.info().name}${ShiftJis.colorClear} would not gain any bonuses from this meld."
    }

    private fun drawPreviewItemWithBonuses() {
        val targetItem = getSelectedUpgradeItem() ?: return
        val material = getSelectedUpgradeMaterial() ?: return
        val quantity = quantityInput.value

        val upgradeBonus = ItemDefinitions[material].capacityAugment ?: return
        val upgradeBonuses = mapOf(upgradeBonus.augmentId to upgradeBonus.potency * quantity)

        val capacityConsumption = upgradeBonus.capacity * quantity

        drawPreviewItemWithBonuses(targetItem, upgradeBonuses, capacityConsumption)
    }

    private fun drawPreviewItemWithBonuses(baseItem: InventoryItem, bonuses: Map<AugmentId, Int>, capacityConsumption: Int) {
        val cappedBonuses = applyMeldBonusLimits(baseItem, bonuses)
        val description = GameV0Helpers.getItemDescriptionInternal(inventoryItem = baseItem, meldBonuses = cappedBonuses, capacityConsumption = capacityConsumption)
        InventoryUi.drawSelectedInventoryItem(baseItem, itemDescription = description)
    }

    private fun getDescriptionWithMaxes(inventoryItem: InventoryItem): InventoryItemDescription {
        return GameV0Helpers.getItemDescriptionInternal(inventoryItem = inventoryItem, includeAllMeldCaps = true)
    }

    private fun getUpgradeMaterialColorDisplay(upgradeMaterial: InventoryItem): ByteColor {
        val (max, _) = getMaxUpgradeMaterialQuantity(upgradeMaterial)
        return if (max == 0) { ByteColor.grey } else { ByteColor.half }
    }

    private fun applyMeldBonusLimits(item: InventoryItem, upgradeBonuses: Map<AugmentId, Int>): Map<AugmentId, Int> {
        val caps = GameV0Helpers.getRemainingMeldPotential(item)
        return upgradeBonuses.mapValues { it.value.coerceAtMost(caps[it.key] ?: 0) }
    }

}