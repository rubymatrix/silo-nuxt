package xim.poc.ui

import xim.math.Vector2f
import xim.poc.*
import xim.poc.game.*
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.Inventory
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.isEquipped
import xim.poc.gl.ByteColor
import xim.poc.ui.EquipScreenUiState.equipContext
import xim.resource.InventoryItemType
import xim.resource.ItemListType

fun interface InventoryFilter {
    fun apply(inventoryItem: InventoryItem): Boolean
}

object InventoryUi {

    private var lastDescribedItem: InternalItemId = 0
    private var page = 0

    fun advanceItemDescriptionPage() {
        page += 1
    }

    fun getItems(equipSlotFilter: Int? = null, itemTypeFilter: ItemListType? = null, filter: InventoryFilter? = null): List<InventoryItem> {
        return if (equipSlotFilter != null) {
            EquipScreenUi.getInventoryItemsByEquipSlot(equipSlotFilter)
        } else if (itemTypeFilter != null) {
            Inventory.player().inventoryItems.filter { it.info().type == itemTypeFilter }
        } else if (filter != null) {
            Inventory.player().inventoryItems.filter { filter.apply(it) }
        } else {
            Inventory.player().inventoryItems
        }
    }

    fun getSelectedItemIndex(uiState: UiState): Int {
        return uiState.cursorIndex + uiState.scrollSettings!!.lowestViewableItemIndex
    }

    fun getSelectedItem(uiState: UiState, equipSlotFilter: Int? = null, itemTypeFilter: ItemListType? = null, filter: InventoryFilter? = null): InventoryItem? {
        val currentItemIndex = getSelectedItemIndex(uiState)
        return getItems(equipSlotFilter, itemTypeFilter, filter).getOrNull(currentItemIndex)
    }

    fun drawInventoryItems(
        uiState: UiState,
        equipSlotFilter: Int? = null,
        itemTypeFilter: ItemListType? = null,
        filter: InventoryFilter? = null,
        descriptionProvider: ((InventoryItem) -> InventoryItemDescription)? = null,
        colorProvider: ((InventoryItem) -> ByteColor)? = null,
    ) {
        val stackPos = uiState.latestPosition ?: return
        val offset = Vector2f(0f, 0f)

        val filteredItems = getItems(equipSlotFilter, itemTypeFilter, filter)
        val playerState = ActorStateManager.player()

        val scrollSettings = uiState.scrollSettings!!
        for (i in scrollSettings.lowestViewableItemIndex until scrollSettings.lowestViewableItemIndex + scrollSettings.numElementsInPage) {
            if (i >= filteredItems.size) { break }

            val item = filteredItems[i]
            val itemInfo = item.info()

            val color = if (colorProvider != null) {
                colorProvider.invoke(item)
            } else if (playerState.isEquipped(item)) {
                UiElementHelper.getStandardTextColor(4)
            } else if (item.temporary) {
                UiElementHelper.getStandardTextColor(8)
            }else if (itemInfo.itemType == InventoryItemType.UsableItem || itemInfo.itemType == InventoryItemType.Crystal) {
                UiElementHelper.getStandardTextColor(5)
            }  else {
                UiElementHelper.getStandardTextColor(0)
            }

            UiElementHelper.drawInventoryItemIcon(item = item, position = offset + stackPos + Vector2f(2f, 4f), scale = Vector2f(0.5f, 0.5f))
            UiElementHelper.drawString(text = itemInfo.name, color = color, offset = offset + stackPos + Vector2f(22f, 8f))

            offset.y += 18f
        }

        if (UiStateHelper.isFocus(uiState)) { drawSelectedInventoryItem(uiState, equipSlotFilter, itemTypeFilter, filter, descriptionProvider) }
    }

    private fun drawSelectedInventoryItem(
        uiState: UiState,
        equipSlotFilter: Int?,
        itemTypeFilter: ItemListType? = null,
        filter: InventoryFilter? = null,
        descriptionProvider: ((InventoryItem) -> InventoryItemDescription)? = null,
    ) {
        val currentItem = getSelectedItem(uiState, equipSlotFilter, itemTypeFilter, filter) ?: return
        val context = if (equipSlotFilter != null) { equipContext } else { null }

        val description = descriptionProvider?.invoke(currentItem) ?: getDescription(currentItem)
        drawSelectedInventoryItem(currentItem, context, itemDescription = description)
    }

    fun drawSelectedInventoryItem(
        inventoryItem: InventoryItem,
        context: UiState? = null,
        contextOffset: Vector2f? = null,
        positionOverride: Vector2f? = null,
        itemDescription: InventoryItemDescription = getDescription(inventoryItem),
    ) {
        if (lastDescribedItem != inventoryItem.internalId) { page = 0 }
        lastDescribedItem = inventoryItem.internalId

        val descriptionBuilder = StringBuilder()
        descriptionBuilder.appendLine(itemDescription.name)

        if (itemDescription.quantity != null) { descriptionBuilder.appendLine(itemDescription.quantity) }

        val numPages = itemDescription.pages.size
        if (numPages > 0) {
            val currentPage = page % numPages
            descriptionBuilder.appendLine(itemDescription.pages[currentPage])
        }

        val description = descriptionBuilder.toString().trimEnd()
        val formattedDescription = UiElementHelper.formatString(description, maxWidth = 300, textDirection = TextDirection.TopToBottom) ?: return

        var additionalLinesNeeded = 0
        if (itemDescription.augmentPath != null) { additionalLinesNeeded += itemDescription.augmentPath.lines().size }
        if (itemDescription.itemLevel != null) { additionalLinesNeeded += 1 }
        if (numPages > 1) { additionalLinesNeeded += 1 }

        val descriptionLines = (formattedDescription.numLines + additionalLinesNeeded).coerceIn(3, 12)

        val menuName = when {
            descriptionLines == 3 -> "menu    iteminfo"
            descriptionLines <= 9 -> "menu    item${descriptionLines}inf"
            else -> "menu    item${descriptionLines}in"
        }

        val menuPos = if (positionOverride != null) {
            UiElementHelper.drawMenu(menuName = menuName, offsetOverride = positionOverride) ?: return
        } else if (context != null) {
            val offset = Vector2f(context.latestPosition!!.x, context.latestPosition!!.y + context.latestMenu!!.frame.size.y + 2f) + (contextOffset ?: Vector2f())
            UiElementHelper.drawMenu(menuName = menuName, offsetOverride = offset) ?: return
        } else {
            UiElementHelper.drawMenu(menuName = menuName, menuStacks = MenuStacks.LogStack) ?: return
        }

        UiElementHelper.drawFormattedString(formattedDescription, offset = menuPos + Vector2f(x = 46f, y = 8f))
        UiElementHelper.drawInventoryItemIcon(inventoryItem, position = menuPos + Vector2f(8f, 12f))

        val tags = listOfNotNull(
            if (itemDescription.rare) { 294 } else null,
            if (itemDescription.exclusive) { 295 } else null,
        )

        val tagOffset = Vector2f(320f, 4f)
        tags.forEach {
            UiElementHelper.drawUiElement(lookup = "menu    windowps", index = it, position = menuPos + tagOffset)
            tagOffset.x += 16f
        }

        // Additional lines
        val lastChar = formattedDescription.characters.lastOrNull() ?: return
        val rightAlignedOffset = menuPos + Vector2f(x = 46f, y = 8f)
        rightAlignedOffset.x += 312
        rightAlignedOffset.y += lastChar.position.y + 16f

        if (itemDescription.augmentPath != null) {
            for (line in itemDescription.augmentPath.lines()) {
                UiElementHelper.drawString(line, offset = rightAlignedOffset, alignment = TextAlignment.Right)
                rightAlignedOffset.y += 16f
            }
        }

        if (itemDescription.itemLevel != null) {
            UiElementHelper.drawString(itemDescription.itemLevel, offset = rightAlignedOffset, alignment = TextAlignment.Right)
            rightAlignedOffset.y += 16f
        }

        if (numPages > 1) {
            UiElementHelper.drawString("${ShiftJis.solidDownTriangle}", offset = rightAlignedOffset, alignment = TextAlignment.Right)
        }
    }

    private fun getDescription(inventoryItem: InventoryItem): InventoryItemDescription {
        return GameState.getGameMode().getItemDescription(ActorStateManager.playerId, inventoryItem)
    }

    fun useSelectedInventoryItem(currentItem: InventoryItem, target: ActorId) {
        val player = ActorManager.player()
        val currentItemInfo = currentItem.info()

        if (currentItemInfo.type == ItemListType.UsableItem) {
            GameClient.submitStartUsingItem(player.id, target, currentItem)
        }
    }

}