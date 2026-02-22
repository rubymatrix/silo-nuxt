package xim.poc.game.configuration.v0.interactions

import xim.math.Vector2f
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.UiElementHelper
import xim.poc.audio.AudioManager
import xim.poc.audio.SystemSound
import xim.poc.game.*
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.GameV0Helpers.getNeededItemsForWeaponUpgrade
import xim.poc.game.configuration.v0.ItemBuildUpOption
import xim.poc.game.configuration.v0.ItemDefinitions
import xim.poc.game.configuration.v0.events.WeaponUpgradeEvent
import xim.poc.game.configuration.v0.interactions.UpgradeWeaponUiState.upgradeWeaponDestinationSelectContext
import xim.poc.game.configuration.v0.interactions.UpgradeWeaponUiState.upgradeWeaponSelectContext
import xim.poc.ui.*
import xim.resource.DatId
import xim.resource.InventoryItemInfo
import xim.resource.InventoryItems

object UpgradeWeaponInteraction : NpcInteraction {

    private var describedSelf = false

    override fun onInteraction(npcId: ActorId) {
        if (!describedSelf) {
            describedSelf = true
            ChatLog("Weapons can be upgraded once they have reached a high enough rank.")
        }

        ActorManager[npcId]?.playRoutine(DatId("hap0"))
        UiStateHelper.pushState(upgradeWeaponSelectContext, SystemSound.MenuSelect)
    }

}

object UpgradeWeaponEquipmentFilter: InventoryFilter {
    override fun apply(inventoryItem: InventoryItem): Boolean {
        val item = ItemDefinitions.getNullable(inventoryItem) ?: return false
        return item.upgradeOptions.isNotEmpty()
    }
}

private object UpgradeWeaponUiState {

    val upgradeWeaponSelectContext: UiState
    val upgradeWeaponDestinationSelectContext: UiState

    init {
        upgradeWeaponDestinationSelectContext = UiState(
            additionalDraw = { UpgradeWeaponInteractionUi.drawUpgradeOptions(it) },
            drawParent = true,
            parentRelative = ParentRelative.RightOfParent,
            focusMenu = "menu    shop    ",
            resetCursorIndexOnPush = false,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { UpgradeWeaponInteractionUi.getUpgradeOptions().size },
            hideGauge = true,
        ) {
            if (UiStateHelper.isEnterPressed()) {
                UpgradeWeaponInteractionUi.openUpgradeConfirmation()
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        upgradeWeaponSelectContext = UiState(
            additionalDraw = { InventoryUi.drawInventoryItems(it, filter = UpgradeWeaponEquipmentFilter, descriptionProvider = UpgradeWeaponInteractionUi::getDescriptionWithMaxes) },
            focusMenu = "menu    inventor",
            childStates = { listOf(upgradeWeaponDestinationSelectContext) },
            resetCursorIndexOnPush = false,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { InventoryUi.getItems(filter = UpgradeWeaponEquipmentFilter).size },
            hideGauge = true,
        ) {
            if (UiStateHelper.isEnterPressed()) {
                UiStateHelper.pushState(upgradeWeaponDestinationSelectContext, SystemSound.MenuSelect)
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

    }

}

object UpgradeWeaponInteractionUi {

    fun getSelectedUpgradeItem(): InventoryItem? {
        return InventoryUi.getSelectedItem(upgradeWeaponSelectContext, filter = UpgradeWeaponEquipmentFilter)
    }

    fun drawUpgradeOptions(uiState: UiState) {
        val stackPos = uiState.latestPosition ?: return
        val offset = Vector2f(0f, 0f)

        val options = getUpgradeOptions()

        val scrollSettings = uiState.scrollSettings!!
        for (i in scrollSettings.lowestViewableItemIndex until scrollSettings.lowestViewableItemIndex + scrollSettings.numElementsInPage) {
            if (i >= options.size) { break }

            val option = options[i]
            val itemInfo = InventoryItems[option.destinationId]

            val color = if (canUpgrade(itemInfo)) { "" } else { "${ShiftJis.colorGrey}" }

            UiElementHelper.drawInventoryItemIcon(itemInfo = itemInfo, position = offset + stackPos + Vector2f(2f, 4f), scale = Vector2f(0.5f, 0.5f))
            UiElementHelper.drawString(text = "${color}${itemInfo.name}", offset = offset + stackPos + Vector2f(22f, 8f))

            val buildUpItem = option.itemRequirement
            if (buildUpItem != null) {
                val buildUpItemInfo = InventoryItems[buildUpItem.itemId]
                val buildUpItemName = "${color}x${buildUpItem.quantity}"

                val iconPosition = offset + stackPos + Vector2f(202f, 4f)

                UiElementHelper.drawInventoryItemIcon(itemInfo = buildUpItemInfo, position = iconPosition, scale = Vector2f(0.5f, 0.5f))
                UiElementHelper.drawString(text = buildUpItemName, offset = offset + stackPos + Vector2f(222f, 8f))

                ToolTipHelper.addItemToolTip(position = iconPosition, size = Vector2f(48f, 16f), buildUpItem.itemId)
            }

            offset.y += 18f
        }

        if (UiStateHelper.isFocus(uiState)) {
            drawItemPreview()
        }
    }

    fun openUpgradeConfirmation() {
        val selectedItem = getSelectedUpgradeItem() ?: return
        val selectedDestination = getSelectedDestinationItemInfo() ?: return

        val (sufficientRank, requiredRank) = GameV0Helpers.weaponRankIsSufficient(selectedItem, selectedDestination) ?: return
        if (!sufficientRank) {
            ChatLog("${ShiftJis.colorItem}${selectedItem.info().name}${ShiftJis.colorClear} needs to reach ${ShiftJis.colorAug}Rank $requiredRank${ShiftJis.colorClear} to upgrade into ${ShiftJis.colorItem}${selectedDestination.name}${ShiftJis.colorClear}.", ChatLogColor.Error)
            AudioManager.playSystemSoundEffect(SystemSound.Invalid)
            return
        }

        val neededItems = getNeededItemsForWeaponUpgrade(selectedItem, selectedDestination)
        if (neededItems.isNotEmpty()) {
            for ((itemId, quantity) in neededItems) {
                ChatLog("${ShiftJis.colorItem}${selectedDestination.name}${ShiftJis.colorClear} requires additional items: ${ShiftJis.colorItem}${quantity} ${InventoryItems[itemId].logName(quantity)}${ShiftJis.colorClear}.", ChatLogColor.Error)
            }
            AudioManager.playSystemSoundEffect(SystemSound.Invalid)
            return
        }

        val prompt = "Upgrade ${ShiftJis.colorItem}${selectedItem.info().name}${ShiftJis.colorClear} into ${ShiftJis.colorItem}${selectedDestination.name}${ShiftJis.colorClear}?"

        val options = listOf(
            QueryMenuOption(text = "No", value = 0),
            QueryMenuOption(text = "Yes", value = 1),
        )

        UiStateHelper.openQueryMode(
            prompt = prompt,
            options = options,
            closeable = true,
            systemSound = SystemSound.MenuSelect,
            drawFn = { drawItemPreview() },
            callback = this::handleWeaponUpgradeConfirmation,
        )
    }

    private fun handleWeaponUpgradeConfirmation(queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value == 0) { return QueryMenuResponse.pop }

        val sourceItem = getSelectedUpgradeItem() ?: return QueryMenuResponse.pop
        val destinationItemInfo = getSelectedDestinationItemInfo() ?: return QueryMenuResponse.pop

        GameEngine.submitEvent(WeaponUpgradeEvent(
            sourceId = ActorStateManager.playerId,
            weaponId = sourceItem.internalId,
            destinationWeaponId = destinationItemInfo.itemId,
        ))

        val player = ActorManager.player()
        MiscEffects.playEffect(player, player, 0x1346, DatId.synthesisHq)

        return QueryMenuResponse.popAll
    }

    fun getUpgradeOptions(): List<ItemBuildUpOption> {
        val item = getSelectedUpgradeItem() ?: return emptyList()
        return ItemDefinitions[item].upgradeOptions
    }

    private fun getSelectedDestinationItemInfo(): InventoryItemInfo? {
        val itemId = getUpgradeOptions().getOrNull(getSelectedDestinationIndex())?.destinationId ?: return null
        return InventoryItems[itemId]
    }

    private fun getSelectedDestinationIndex(): Int {
        val context = upgradeWeaponDestinationSelectContext
        return context.cursorIndex + context.scrollSettings!!.lowestViewableItemIndex
    }

    private fun drawItemPreview() {
        val sourceItem = getSelectedUpgradeItem() ?: return
        val destinationItemInfo = getSelectedDestinationItemInfo() ?: return

        val fakeItem = GameV0Helpers.generateUpgradedWeapon(sourceItem, destinationItemInfo)
        val description = GameV0Helpers.getItemDescriptionInternal(fakeItem, includeAllMeldCaps = true)
        InventoryUi.drawSelectedInventoryItem(fakeItem, itemDescription = description)
    }

    private fun canUpgrade(destinationItemInfo: InventoryItemInfo): Boolean {
        val item = getSelectedUpgradeItem() ?: return false

        val (sufficientRank, _) = GameV0Helpers.weaponRankIsSufficient(item, destinationItemInfo) ?: return false
        if (!sufficientRank) { return false }

        val neededItems = getNeededItemsForWeaponUpgrade(item, destinationItemInfo)
        if (neededItems.isNotEmpty()) { return false }

        return true
    }

    fun getDescriptionWithMaxes(inventoryItem: InventoryItem): InventoryItemDescription {
        return GameV0Helpers.getItemDescriptionInternal(inventoryItem = inventoryItem, includeAllMeldCaps = true)
    }

}