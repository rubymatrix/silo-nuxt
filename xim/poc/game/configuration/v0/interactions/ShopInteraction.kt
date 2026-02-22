package xim.poc.game.configuration.v0.interactions

import xim.math.Vector2f
import xim.poc.*
import xim.poc.audio.AudioManager
import xim.poc.audio.SystemSound
import xim.poc.game.*
import xim.poc.game.actor.components.*
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.ItemDefinitions
import xim.poc.game.configuration.v0.events.ShopPurchaseEvent
import xim.poc.game.configuration.v0.events.ShopSellEvent
import xim.poc.game.configuration.v0.interactions.ShopInteractionUi.drawItemControl
import xim.poc.game.configuration.v0.interactions.ShopInteractionUi.drawPreviewItem
import xim.poc.game.configuration.v0.interactions.ShopInteractionUi.getInventory
import xim.poc.game.configuration.v0.interactions.ShopInteractionUi.getMaxTransactionQuantity
import xim.poc.game.configuration.v0.interactions.ShopInteractionUi.getSelectedItem
import xim.poc.game.configuration.v0.interactions.ShopInteractionUi.quantityInput
import xim.poc.game.configuration.v0.interactions.ShopInteractionUiState.currentVendorId
import xim.poc.game.configuration.v0.interactions.ShopInteractionUiState.shopMode
import xim.poc.game.configuration.v0.interactions.ShopInteractionUiState.transactionContext
import xim.poc.game.configuration.v0.interactions.ShopInteractionUiState.transactionQuantityContext
import xim.poc.ui.EquipScreenUi
import xim.poc.ui.InventoryUi
import xim.poc.ui.QuantityInputController
import kotlin.math.min

object ShopInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        currentVendorId = npcId
        UiStateHelper.pushState(ShopInteractionUiState.shopMain, SystemSound.TargetConfirm)
    }

}

private enum class ShopMode {
    Buy,
    Sell
}

private object ShopInteractionUiState {

    var currentVendorId: ActorId? = null
    var shopMode = ShopMode.Buy

    val transactionQuantityContext: UiState
    val transactionConfirmContext: UiState
    val transactionContext: UiState
    val shopMain: UiState

    init {
        transactionConfirmContext = UiState(
            dynamicFocusMenu = { when(shopMode) {
                ShopMode.Buy -> "menu    shopbuy "
                ShopMode.Sell -> "menu    shopsell"
            } },
            drawParent = true,
            locksMovement = true,
            menuStacks = MenuStacks.LogStack,
        ) {
            if (UiStateHelper.isEnterPressed() && it.cursorPos == 0) {
                ShopInteractionUi.submitTransaction()
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
            additionalDraw = { ShopInteractionUi.drawShopInventory(it); drawPreviewItem(it) },
            focusMenu = "menu    shop    ",
            locksMovement = true,
            resetCursorIndexOnPush = true,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { getInventory().size },
        ) {
            if (UiStateHelper.isEnterPressed()) {
                val canPurchase = getMaxTransactionQuantity() > 0
                if (!canPurchase) {
                    AudioManager.playSystemSoundEffect(SystemSound.Invalid)
                    return@UiState true
                }

                val item = getSelectedItem() ?: return@UiState true
                val nextState = if (item.info().isStackable()) { transactionQuantityContext } else { transactionConfirmContext }
                UiStateHelper.pushState(nextState, SystemSound.MenuSelect)
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        shopMain = UiState(
            focusMenu = "menu    shopmain",
            menuStacks = MenuStacks.LogStack,
            locksMovement = true,
            onPopped = { currentVendorId = null }
        ) {
            if (UiStateHelper.isEnterPressed() && it.cursorPos == 0) {
                shopMode = ShopMode.Buy
                UiStateHelper.pushState(transactionContext, SystemSound.MenuSelect)
                true
            } else if (UiStateHelper.isEnterPressed() && it.cursorPos == 1) {
                shopMode = ShopMode.Sell
                UiStateHelper.pushState(transactionContext, SystemSound.MenuSelect)
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

private object ShopInteractionUi {

    val quantityInput = QuantityInputController { getMaxTransactionQuantity() }

    fun getInventory(): List<InventoryItem> {
        return when (shopMode) {
            ShopMode.Buy -> ActorStateManager[currentVendorId]?.getInventory()?.inventoryItems ?: return emptyList()
            ShopMode.Sell -> {
                ActorStateManager.player().getNotEquippedItems()
                    .filter { !it.info().isMainHandWeapon() }
                    .filter { ItemDefinitions.getNullable(it)?.shopSellable ?: true }
            }
        }
    }

    fun drawItemControl(uiState: UiState) {
        quantityInput.draw(uiState)
    }

    fun drawShopInventory(uiState: UiState) {
        val vendorId = currentVendorId ?: return
        val stackPos = uiState.latestPosition ?: return
        val menuSize = uiState.latestMenu?.frame?.size ?: return

        val offset = Vector2f(0f, 0f)

        val inventory = getInventory()

        val scrollSettings = uiState.scrollSettings!!
        for (i in scrollSettings.lowestViewableItemIndex until scrollSettings.lowestViewableItemIndex + scrollSettings.numElementsInPage) {
            if (i >= inventory.size) { break }

            val item = inventory[i]
            val price = GameV0.getItemPrice(vendorId, item) ?: (CurrencyType.Gil to 0)

            val itemInfo = item.info()
            val color = AugmentHelper.getQualityColorDisplay(item)
            val text = "${color}${itemInfo.name}"

            UiElementHelper.drawInventoryItemIcon(item = item, position = offset + stackPos + Vector2f(2f, 4f), scale = Vector2f(0.5f, 0.5f))
            UiElementHelper.drawString(text = text, offset = offset + stackPos + Vector2f(22f, 8f))

            val priceDivider = when (shopMode) {
                ShopMode.Buy -> 1
                ShopMode.Sell -> ShopSellEvent.sellPriceDivider
            }

            val priceText = UiElementHelper.formatPrice(price.first, price.second / priceDivider)
            UiElementHelper.drawString(text = priceText, offset = offset + stackPos + Vector2f(menuSize.x - 14f, 8f), font = Font.FontShp, alignment = TextAlignment.Right)

            offset.y += 18f
        }
    }

    fun drawPreviewItem(uiState: UiState) {
        if (!UiStateHelper.isFocus(transactionQuantityContext)) {
            val moneyMenuPosition = UiElementHelper.drawMenu("menu    money   ")
            EquipScreenUi.populateMoneyMenu(moneyMenuPosition)
        }

        val inventoryItem = getSelectedItem() ?: return
        InventoryUi.drawSelectedInventoryItem(inventoryItem, context = uiState, contextOffset = Vector2f(114f, 0f))
    }

    fun getSelectedItem(): InventoryItem? {
        return getInventory().getOrNull(getSelectedItemIndex(transactionContext))
    }

    fun getSelectedItemIndex(uiState: UiState): Int {
        return uiState.cursorIndex + uiState.scrollSettings!!.lowestViewableItemIndex
    }

    fun getMaxTransactionQuantity(): Int {
        val item = getSelectedItem() ?: return 0
        val vendorId = currentVendorId ?: return 0

        if (shopMode == ShopMode.Sell) { return item.quantity }

        val price = GameV0.getItemPrice(vendorId, item) ?: return 0
        val currency = ActorStateManager.player().getCurrency(price.first)

        val maxAfford = currency / price.second
        return min(item.quantity, maxAfford)
    }

    fun submitTransaction() {
        val inventoryItem = getSelectedItem() ?: return
        val vendor = currentVendorId ?: return

        val quantity = if (inventoryItem.info().isStackable()) { quantityInput.value } else { 1 }

        val event = when (shopMode) {
            ShopMode.Buy -> ShopPurchaseEvent(
                vendorId = vendor,
                shopperId = ActorStateManager.playerId,
                selectedItemId = inventoryItem.internalId,
                quantity = quantity,
            )
            ShopMode.Sell -> ShopSellEvent(
                vendorId = vendor,
                shopperId = ActorStateManager.playerId,
                selectedItemId = inventoryItem.internalId,
                quantity = quantity,
            )
        }

        GameEngine.submitEvent(event)
    }

}