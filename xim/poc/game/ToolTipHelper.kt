package xim.poc.game

import xim.math.Vector2f
import xim.poc.TextDirection
import xim.poc.UiElementHelper
import xim.poc.UiResourceManager
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.getInventory
import xim.poc.game.configuration.v0.interactions.ItemId
import xim.poc.ui.InventoryUi
import xim.resource.DatId
import xim.resource.UiMenu
import xim.resource.UiMenuResource

object ToolTipHelper {

    private const val fakeUiMenuName = "fake    tooltip "
    private var registered = false

    fun addToolTip(position: Vector2f, size: Vector2f, textFn: () -> String) {
        ClickHandler.registerUiHoverListener(position = position, size = size) {
            val message = textFn.invoke()
            UiElementHelper.enqueueDraw { drawToolTip(it, message) }
            true
        }
    }

    fun addItemToolTip(position: Vector2f, size: Vector2f, itemId: ItemId) {
        ClickHandler.registerUiHoverListener(position = position, size = size) {
            val inventory = ActorStateManager.player().getInventory()
            val item = inventory.getByItemId(itemId).firstOrNull() ?: InventoryItem(id = itemId, quantity = 0)

            UiElementHelper.enqueueDraw {
                InventoryUi.drawSelectedInventoryItem(inventoryItem = item, positionOverride = it.pointerPosition.screenPosition)
            }

            true
        }
    }

    private fun drawToolTip(hoverEvent: HoverEvent, message: String) {
        if (message.isBlank()) { return }

        val uiString = UiElementHelper.formatString(
            text = message,
            maxWidth = Int.MAX_VALUE,
            textDirection = TextDirection.TopToBottom
        ) ?: return

        val maxX = uiString.characters.maxOf { it.position.x }
        val maxY = uiString.characters.maxOf { it.position.y }

        registerFakeMenu()

        val menu = UiResourceManager.getMenu(fakeUiMenuName) ?: return
        menu.uiMenu.frame.size.x = maxX + 24
        menu.uiMenu.frame.size.y = maxY + 24

        val scaledScreenPosition = Vector2f(
            x = hoverEvent.pointerPosition.screenPosition.x / UiElementHelper.globalUiScale.x,
            y = hoverEvent.pointerPosition.screenPosition.y / UiElementHelper.globalUiScale.y,
        )

        val offsetX = if (hoverEvent.pointerPosition.normalizedScreenPosition.x < 0.5f) {
            scaledScreenPosition.x + 4f
        } else {
            scaledScreenPosition.x - maxX - 18f
        }

        val offsetY = if (hoverEvent.pointerPosition.normalizedScreenPosition.y < 0.5f) {
            scaledScreenPosition.y + 4f
        } else {
            scaledScreenPosition.y - maxY - 18f
        }

        menu.uiMenu.frame.offset.copyFrom(Vector2f(offsetX, offsetY))

        val menuPosition = UiElementHelper.drawMenu(fakeUiMenuName) ?: return
        val offset = menuPosition + Vector2f(6f, 8f)

        UiElementHelper.drawFormattedString(formattedString = uiString, offset = offset)
    }

    private fun registerFakeMenu() {
        if (registered) { return }
        registered = true

        val originalMenu = UiResourceManager.getMenu("menu    helpwind") ?: throw IllegalStateException("Couldn't find 'menu    helpwind'")
        val fakeMenu = UiMenu(fakeUiMenuName, originalMenu.uiMenu.frame.deepCopy(), emptyList())
        UiResourceManager.register(UiMenuResource(DatId.zero, fakeMenu))
    }

}