package xim.poc.game.configuration.v0.interactions

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.audio.AudioManager
import xim.poc.audio.SystemSound
import xim.poc.game.*
import xim.poc.game.actor.components.AugmentHelper
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.events.ActorReinforceItemEvent
import xim.poc.game.configuration.v0.events.ActorReinforceItemEvent.Companion.calculateMaximumUsableUpgradeMaterial
import xim.poc.ui.*
import xim.resource.DatId

object ItemReinforcementInteraction: NpcInteraction {

    private var describedSelf = false

    override fun onInteraction(npcId: ActorId) {
        if (!describedSelf) {
            describedSelf = true
            ChatLog("Upgrade materials can be applied to weapons in order to increase its ${ShiftJis.colorAug}rank${ShiftJis.colorClear}.")
        }

        ActorManager[npcId]?.playRoutine(DatId("hap0"))
        ItemReinforcementUi.push()
    }

}

private object EquipmentFilter: InventoryFilter {
    override fun apply(inventoryItem: InventoryItem): Boolean {
        val augments = inventoryItem.augments ?: return false
        return !AugmentHelper.isMaxRank(augments)
    }
}

private class UpgradeItemFilter(val validUpgradeMaterials: Set<Int>): InventoryFilter {
    override fun apply(inventoryItem: InventoryItem): Boolean {
        return validUpgradeMaterials.contains(inventoryItem.id)
    }
}

private object ItemReinforcementUi {

    private val rankInputAmountContext = UiState(
        focusMenu = "menu    itemctrl",
        drawParent = true,
        additionalDraw = { drawItemControl(it) }
    ) {
        quantityInput.refresh()

        if (UiStateHelper.isEnterPressed()) {
            submitUpgrade()
            UiStateHelper.popState(SystemSound.MenuSelect)
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

    private val rankUpgradeItemSelectContext = UiState(
        additionalDraw = { InventoryUi.drawInventoryItems(it, filter = getUpgradeItemFilter()) },
        drawParent = true,
        parentRelative = ParentRelative.RightOfParent,
        focusMenu = "menu    inventor",
        resetCursorIndexOnPush = false,
        scrollSettings = ScrollSettings(numElementsInPage = 10) { InventoryUi.getItems(filter = getUpgradeItemFilter()).size },
        hideGauge = true,
    ) {
        if (isSelectedItemMaxRank()) {
            UiStateHelper.popState(SystemSound.MenuClose)
            true
        } else if (UiStateHelper.isEnterPressed() && getSelectedUpgradeMaterial() != null) {
            UiStateHelper.pushState(rankInputAmountContext, SystemSound.MenuSelect)
            true
        } else if (UiStateHelper.isEscPressed()) {
            UiStateHelper.popState(SystemSound.MenuClose)
            true
        } else {
            false
        }
    }

    private val rankItemSelectContext = UiState(
        additionalDraw = { InventoryUi.drawInventoryItems(it, filter = EquipmentFilter) },
        focusMenu = "menu    inventor",
        childStates = { listOf(rankUpgradeItemSelectContext) },
        resetCursorIndexOnPush = false,
        scrollSettings = ScrollSettings(numElementsInPage = 10) { InventoryUi.getItems(filter = EquipmentFilter).size },
        hideGauge = true,
    ) {
        if (UiStateHelper.isEnterPressed()) {
            if (isSelectedItemMaxRank()) {
                ChatLog("This item is already at its maximum rank.", ChatLogColor.Error)
                AudioManager.playSystemSoundEffect(SystemSound.Invalid)
                return@UiState true
            }

            UiStateHelper.pushState(rankUpgradeItemSelectContext, SystemSound.MenuSelect)
            true
        } else if (UiStateHelper.isEscPressed()) {
            UiStateHelper.popState(SystemSound.MenuClose)
            true
        } else {
            false
        }
    }

    private val quantityInput = QuantityInputController(this::getMaxUpgradeAmount)

    fun push() {
        UiStateHelper.pushState(rankItemSelectContext, SystemSound.MenuSelect)
    }

    private fun drawItemControl(uiState: UiState) {
        quantityInput.draw(uiState)
    }

    private fun getSelectedUpgradeItem(): InventoryItem? {
        return InventoryUi.getSelectedItem(rankItemSelectContext, filter = EquipmentFilter)
    }

    private fun getSelectedUpgradeMaterial(): InventoryItem? {
        return InventoryUi.getSelectedItem(rankUpgradeItemSelectContext, filter = getUpgradeItemFilter())
    }

    private fun getUpgradeItemFilter(): InventoryFilter {
        val item = getSelectedUpgradeItem() ?: return InventoryFilter { false }
        val validMaterials = GameV0.getItemReinforcementValues(item).filter { it.value > 0 }
        return UpgradeItemFilter(validMaterials.keys)
    }

    private fun isSelectedItemMaxRank(): Boolean {
        val selectedItem = getSelectedUpgradeItem() ?: return true
        val augment = selectedItem.augments ?: return true
        return AugmentHelper.isMaxRank(augment)
    }

    private fun submitUpgrade() {
        val itemToUpgrade = getSelectedUpgradeItem() ?: return
        val upgradeMaterial = getSelectedUpgradeMaterial() ?: return
        val quantity = quantityInput.value

        GameEngine.submitEvent(ActorReinforceItemEvent(ActorStateManager.playerId, itemToUpgrade.internalId, upgradeMaterial.internalId, quantity))

        val player = ActorManager.player()
        MiscEffects.playEffect(player, player, 0x1346, DatId.synthesisNq)
    }

    private fun getMaxUpgradeAmount(): Int {
        val upgradeMaterial = getSelectedUpgradeMaterial() ?: return 0
        val upgradeItem = getSelectedUpgradeItem() ?: return 0
        return calculateMaximumUsableUpgradeMaterial(upgradeMaterial, upgradeItem)
    }

}