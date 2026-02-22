package xim.poc.ui

import xim.math.Vector2f
import xim.poc.*
import xim.poc.audio.AudioManager
import xim.poc.audio.SystemSound
import xim.poc.browser.GameKey
import xim.poc.browser.LocalStorage
import xim.poc.game.*
import xim.poc.game.UiStateHelper.isEnterPressed
import xim.poc.game.UiStateHelper.isEscPressed
import xim.poc.game.UiStateHelper.keyPressed
import xim.poc.game.UiStateHelper.popState
import xim.poc.game.UiStateHelper.pushState
import xim.poc.game.actor.components.*
import xim.poc.tools.UiPosition
import xim.poc.ui.EquipScreenUiState.equipContext
import xim.resource.EquipSlot

private class EquipUiSlot(val slot: EquipSlot, val x: Int, val y: Int)

class BonusDescription(val bonusDescription: String, val tooltip: (() -> String)? = null)

object EquipScreenUiState {

    val equipContext: UiState = UiState(
        additionalDraw = { EquipScreenUi.draw(it) },
        dynamicFocusMenu = { if (EquipScreenUi.currentEquipSet == null) { "menu    equip   " } else { "menu    mcresed " } },
        hideGauge = true,
        childStates = { listOf(equipSelectContext, equipSetSelectContext) },
        resetCursorIndexOnPush = false,
        uiPositionKey = UiPosition.Equipment,
    ) {
        if (isEnterPressed()) {
            val numItems = EquipScreenUi.getInventoryItemsByEquipSlot(it.local.cursorIndex).size
            if (numItems == 0) {
                AudioManager.playSystemSoundEffect(SystemSound.Invalid)
            } else {
                pushState(equipSelectContext, SystemSound.MenuSelect)
            }
            true
        } else if ((keyPressed(GameKey.UiRight) && it.cursorPos >= 12) || keyPressed(GameKey.UiContext)) {
            pushState(equipSetSelectContext, SystemSound.TargetCycle)
            true
        } else if (isEscPressed() && EquipScreenUi.currentEquipSet != null) {
            pushState(equipSetSelectContext, SystemSound.MenuClose)
            true
        } else if (isEscPressed() || keyPressed(GameKey.OpenEquipMenu)) {
            popState(SystemSound.MenuClose)
            true
        } else {
            false
        }
    }

    private val equipSelectContext: UiState = UiState(
        focusMenu = "menu    inventor",
        drawParent = true,
        parentRelative = ParentRelative.RightOfParent,
        additionalDraw = { InventoryUi.drawInventoryItems(it, equipSlotFilter = equipContext.cursorIndex) },
        scrollSettings = ScrollSettings(numElementsInPage = 10) { EquipScreenUi.getInventoryItemsByEquipSlot(equipContext.cursorIndex).size },
    ) {
        if (isEnterPressed()) {
            EquipScreenUi.equipHoveredItem(it.local)
            popState(SystemSound.MenuSelect)
            true
        } else if (isEscPressed()) {
            popState(SystemSound.MenuClose)
            true
        } else {
            false
        }
    }

    private val equipSetSelectContext: UiState = UiState(
        focusMenu = "menu    mcres20 ",
        drawParent = true,
        menuStacks = MenuStacks.PartyStack,
        appendType = AppendType.HorizontalOnly,
        resetCursorIndexOnPush = false,
        grabFocusOnHover = { EquipScreenUi.currentEquipSet == null },
        additionalDraw = { EquipScreenUi.drawEquipSets(it) }
    ) {
        if (isEnterPressed()) {
            EquipScreenUi.currentEquipSet = it.cursorPos
            popState(SystemSound.MenuSelect)
            true
        } else if (isEscPressed() || keyPressed(GameKey.UiLeft)) {
            EquipScreenUi.currentEquipSet = null
            popState(SystemSound.MenuClose)
            true
        } else {
            false
        }
    }

}

object EquipScreenUi {

    var currentEquipSet: Int? = null

    private val equipOrder = listOf(
        EquipUiSlot(EquipSlot.Main, 0, 0),
        EquipUiSlot(EquipSlot.Head, 0, 1),
        EquipUiSlot(EquipSlot.Body, 0, 2),
        EquipUiSlot(EquipSlot.Back, 0, 3),
        EquipUiSlot(EquipSlot.Sub, 1, 0),
        EquipUiSlot(EquipSlot.Neck, 1, 1),
        EquipUiSlot(EquipSlot.Hands, 1, 2),
        EquipUiSlot(EquipSlot.Waist, 1, 3),
        EquipUiSlot(EquipSlot.Range, 2, 0),
        EquipUiSlot(EquipSlot.LEar, 2, 1),
        EquipUiSlot(EquipSlot.LRing, 2, 2),
        EquipUiSlot(EquipSlot.Legs, 2, 3),
        EquipUiSlot(EquipSlot.Ammo, 3, 0),
        EquipUiSlot(EquipSlot.REar, 3, 1),
        EquipUiSlot(EquipSlot.RRing, 3, 2),
        EquipUiSlot(EquipSlot.Feet, 3, 3),
    )

    fun draw(uiState: UiState) {
        drawEquippedItems(uiState)

        if (UiStateHelper.isFocus(uiState)) {
            val hoveredItem = getHoveredEquipItem()

            if (hoveredItem != null) {
                InventoryUi.drawSelectedInventoryItem(hoveredItem, equipContext)
            } else if (currentEquipSet == null) {
                drawEffectsDescription(uiState)
            }
        }

        if (currentEquipSet == null) {
            val statusPosition = UiElementHelper.drawMenu(menuName = "menu    persona ", uiPosition = UiPosition.Equipment)
            if (statusPosition != null) { StatusWindowUi.populateStatusWindow(statusPosition) }

            val framePosition = UiElementHelper.drawMenu(menuName = "menu    money   ", uiPosition = UiPosition.Equipment)
            populateMoneyMenu(framePosition)
        }
    }

    fun getInventoryItemsByEquipSlot(cursorIndex: Int) : List<InventoryItem> {
        return Inventory.player().getInventoryItemsByEquipSlot(equipOrder[cursorIndex].slot)
    }

    fun equipHoveredItem(uiState: UiState) {
        val equipIndex = equipContext.cursorIndex
        val slot = equipOrder[equipIndex].slot

        val filteredView = getInventoryItemsByEquipSlot(equipContext.cursorIndex)
        val index = uiState.cursorIndex + uiState.scrollSettings!!.lowestViewableItemIndex

        val item = filteredView[index]

        val current = getCurrentEquipment().getItem(ActorStateManager.player().getInventory(), slot)
        val itemId = if (current?.internalId == item.internalId) { null } else { item.internalId }

        if (currentEquipSet == null) {
            GameClient.submitEquipItem(ActorStateManager.playerId, slot, itemId)
        } else {
            updateEquipmentSet(slot, itemId)
        }
    }

    fun drawEquipSets(it: UiState) {
        val menu = it.latestMenu ?: return
        val offset = it.latestPosition ?: return

        if (UiStateHelper.isFocus(it)) {
            currentEquipSet = it.cursorIndex
        }

        for (i in 0 until 11) {
            val totalOffset = offset + menu.elements[i].offset + Vector2f(6f, 1f)

            val highlight = if (i == currentEquipSet) { "${ShiftJis.colorItem}" } else { "" }

            if (i == 0) {
                UiElementHelper.drawString("${highlight}Style Set", offset = totalOffset, font = Font.FontShp)
            }
        }
    }

    fun populateMoneyMenu(framePosition: Vector2f?) {
        framePosition ?: return

        val gil = ActorStateManager.player().getCurrency(CurrencyType.Gil)
        val priceText = UiElementHelper.formatPrice(CurrencyType.Gil, gil)

        UiElementHelper.drawString(text = priceText, offset = framePosition + Vector2f(104f, 30f), font = Font.FontShp, alignment = TextAlignment.Right)
    }

    private fun getCurrentEquipment(): Equipment {
        val current = currentEquipSet ?: return ActorStateManager.player().getEquipmentComponentOrThrow()
        return LocalStorage.getPlayerEquipmentSet(current) ?: ActorStateManager.player().getEquipmentComponentOrThrow()
    }

    private fun updateEquipmentSet(slot: EquipSlot, itemId: InternalItemId?) {
        val currentIndex = currentEquipSet ?: return
        val equipmentSet = LocalStorage.getPlayerEquipmentSet(currentIndex) ?: return

        val inventoryItem = ActorStateManager.player().getInventory().getByInternalId(itemId)
        equipmentSet.setItem(slot, inventoryItem)
    }

    private fun getHoveredEquipItem(): InventoryItem? {
        val equipment = getCurrentEquipment()
        val inventory = ActorStateManager.player().getInventory()
        val hoveredIndex = equipContext.cursorIndex
        return equipment.getItem(inventory, equipOrder[hoveredIndex].slot)
    }

    private fun drawEquippedItems(uiState: UiState) {
        val stackPos = uiState.latestPosition ?: return
        val baseOffset = Vector2f(24f, 10f)

        val inventory = ActorStateManager.player().getInventory()
        val equipment = getCurrentEquipment()

        for (slot in equipOrder) {
            val offset = baseOffset + Vector2f(slot.x * 34f, slot.y * 34f)
            val item = equipment.getItem(inventory, slot.slot) ?: continue
            UiElementHelper.drawInventoryItemIcon(item = item, position = offset + stackPos)
        }
    }

    private fun drawEffectsDescription(context: UiState) {
        val descriptions = GameState.getGameMode().getActorEffectsDescription(ActorStateManager.player()) ?: return

        val parentMenu = context.latestMenu ?: return
        val parentPosition = context.latestPosition ?: return

        val effectMenu = UiResourceManager.getMenu("menu    fxfilter") ?: return
        effectMenu.uiMenu.frame.size.x = 480f

        val offset = Vector2f(parentPosition.x, parentPosition.y + parentMenu.frame.size.y + 2f)
        val effectsPosition = UiElementHelper.drawMenu(menuName = "menu    fxfilter", offsetOverride = offset) ?: return

        val baseDescriptionOffset = effectsPosition + Vector2f(6f, 24f)

        var counter = 0
        for (description in descriptions) {
            val descriptionOffset = baseDescriptionOffset + Vector2f(240f * (counter % 2), 16f * (counter/2))
            counter += 1

            UiElementHelper.drawString(text = description.bonusDescription, offset = descriptionOffset)

            if (description.tooltip != null) {
                ToolTipHelper.addToolTip(position = descriptionOffset, size = Vector2f(230f, 10f), textFn = description.tooltip)
            }
        }
    }

}