package xim.poc.game.configuration.v0.interactions

import xim.math.Vector2f
import xim.poc.ActorId
import xim.poc.MenuStacks
import xim.poc.UiElementHelper
import xim.poc.audio.AudioManager
import xim.poc.audio.SystemSound
import xim.poc.game.*
import xim.poc.game.actor.components.getInventory
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.ItemDropDefinition
import xim.poc.game.configuration.v0.events.BarterPurchaseEvent
import xim.poc.ui.ChatLog
import xim.poc.ui.InventoryUi
import xim.poc.ui.QuantityInputController
import xim.poc.ui.ShiftJis
import xim.poc.ui.ShiftJis.colorClear
import xim.poc.ui.ShiftJis.colorInvalid
import xim.poc.ui.ShiftJis.colorItem
import xim.resource.InventoryItems

typealias Quantity = Int
typealias ItemId = Int

class BarterItem(val purchaseItem: ItemDropDefinition, val requiredItems: List<Pair<ItemId, Quantity>>)

class BarterConfiguration(val items: List<BarterItem>)

class BarterInteractionUiState(
    val vendorId: ActorId,
    val configuration: BarterConfiguration,
) {

    private val transactionQuantityContext: UiState
    private val transactionConfirmContext: UiState
    private val transactionContext: UiState

    private val quantityInput = QuantityInputController { getMaxTransactionQuantity() }

    init {
        transactionConfirmContext = UiState(
            focusMenu = "menu    shopbuy ",
            drawParent = true,
            locksMovement = true,
            menuStacks = MenuStacks.LogStack,
        ) {
            if (UiStateHelper.isEnterPressed() && it.cursorPos == 0) {
                submitTransaction()
                UiStateHelper.popState(SystemSound.MenuSelect)
                true
            } else if (UiStateHelper.isEnterPressed() && it.cursorPos == 1) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        transactionQuantityContext = UiState(
            focusMenu = "menu    itemctrl",
            drawParent = true,
            locksMovement = true,
            additionalDraw = { drawItemControl(it) }
        ) {
            quantityInput.refresh()
            if (UiStateHelper.isEnterPressed()) {
                UiStateHelper.popState()
                UiStateHelper.pushState(transactionConfirmContext, SystemSound.MenuSelect)
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

        transactionContext = UiState(
            additionalDraw = { drawShopInventory(it); drawPreviewItem(it) },
            focusMenu = "menu    shop    ",
            locksMovement = true,
            resetCursorIndexOnPush = true,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { configuration.items.size },
        ) {
            if (UiStateHelper.isEnterPressed()) {
                val canPurchase = getMaxTransactionQuantity() > 0
                if (!canPurchase) {
                    AudioManager.playSystemSoundEffect(SystemSound.Invalid)
                    outputRequiredItems()
                    return@UiState true
                }

                val item = getSelectedItem() ?: return@UiState true
                val itemInfo = InventoryItems[item.purchaseItem.itemId]

                val nextState = if (itemInfo.isStackable()) { transactionQuantityContext } else { transactionConfirmContext }
                UiStateHelper.pushState(nextState, SystemSound.MenuSelect)
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
        UiStateHelper.pushState(transactionContext)
    }

    private fun drawItemControl(uiState: UiState) {
        quantityInput.draw(uiState)
    }

    private fun drawShopInventory(uiState: UiState) {
        val stackPos = uiState.latestPosition ?: return

        val offset = Vector2f(0f, 0f)

        val scrollSettings = uiState.scrollSettings!!
        for (i in scrollSettings.lowestViewableItemIndex until scrollSettings.lowestViewableItemIndex + scrollSettings.numElementsInPage) {
            val item = configuration.items.getOrNull(i) ?: break

            val fakeItem = GameV0.generateItem(item.purchaseItem)
            UiElementHelper.drawInventoryItemIcon(item = fakeItem, position = offset + stackPos + Vector2f(2f, 4f), scale = Vector2f(0.5f, 0.5f))

            val itemInfo = InventoryItems[item.purchaseItem.itemId]
            UiElementHelper.drawString(text = itemInfo.name, offset = offset + stackPos + Vector2f(22f, 8f))

            var requiredItemOffset = Vector2f(214f, 4f)
            for ((requiredItem, requiredQuantity) in item.requiredItems) {
                val requiredItemInfo = InventoryItems[requiredItem]
                val color = if (getMaxPurchaseAmount(requiredItem, requiredQuantity) > 0) { ShiftJis.colorWhite } else { ShiftJis.colorGrey }

                UiElementHelper.drawString(text = "$color$requiredQuantity", offset = offset + stackPos + requiredItemOffset + Vector2f(0f, 4f))
                UiElementHelper.drawInventoryItemIcon(itemInfo = requiredItemInfo, position = offset + stackPos + requiredItemOffset + Vector2f(16f, 0f), scale = Vector2f(0.5f, 0.5f))

                ToolTipHelper.addItemToolTip(position = offset + stackPos + requiredItemOffset, size = Vector2f(32f, 16f), requiredItem)

                requiredItemOffset -= Vector2f(32f, 0f)
            }

            offset.y += 18f
        }
    }

    private fun drawPreviewItem(uiState: UiState) {
        if (UiStateHelper.isFocus(transactionQuantityContext)) { return }
        val itemDefinition = getSelectedItem() ?: return

        val fakeItem = GameV0.generateItem(itemDefinition.purchaseItem)

        fakeItem.augments?.let {
            fakeItem.augments = it.copy(augmentIds = it.augmentIds.map { 1023 }.toMutableList())
        }

        InventoryUi.drawSelectedInventoryItem(fakeItem, context = uiState)
    }

    private fun getSelectedItem(): BarterItem? {
        return configuration.items.getOrNull(getSelectedItemIndex(transactionContext))
    }

    private fun getSelectedItemIndex(uiState: UiState): Int {
        return uiState.cursorIndex + uiState.scrollSettings!!.lowestViewableItemIndex
    }

    private fun getMaxTransactionQuantity(): Int {
        val item = getSelectedItem() ?: return 0
        return item.requiredItems.minOfOrNull { getMaxPurchaseAmount(it.first, it.second) } ?: 0
    }

    private fun outputRequiredItems() {
        val item = getSelectedItem() ?: return
        val maxPurchasePerRequiredItem = item.requiredItems.associateWith { getMaxPurchaseAmount(it.first, it.second) }

        for ((requiredItem, maxPurchaseAmount) in maxPurchasePerRequiredItem) {
            if (maxPurchaseAmount > 0) { continue }
            ChatLog("${colorInvalid}Required item:$colorClear ${requiredItem.second} $colorItem${InventoryItems[requiredItem.first].logName(requiredItem.second)}$colorClear.")
        }
    }

    private fun getMaxPurchaseAmount(requiredItem: Int, requiredQuantity: Int): Int {
        val playerInventory = ActorStateManager.player().getInventory()
        val ownedAmount = playerInventory.getByItemId(requiredItem).sumOf { it.quantity }
        return ownedAmount / requiredQuantity
    }

    private fun submitTransaction() {
        val item = getSelectedItem() ?: return
        val itemInfo = InventoryItems[item.purchaseItem.itemId]

        val quantity = if (itemInfo.isStackable()) { quantityInput.value } else { 1 }

        GameEngine.submitEvent(BarterPurchaseEvent(
            sourceId = ActorStateManager.playerId,
            vendorId = vendorId,
            barterItem = item,
            purchaseQuantity = quantity,
        ))

    }

}