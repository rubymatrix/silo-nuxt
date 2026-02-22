package xim.poc.ui

import xim.math.Vector2f
import xim.poc.ActorManager
import xim.poc.AppendType
import xim.poc.MenuStacks
import xim.poc.UiElementHelper
import xim.poc.audio.SystemSound
import xim.poc.game.GameClient
import xim.poc.game.ScrollSettings
import xim.poc.game.UiState
import xim.poc.game.UiStateHelper
import xim.resource.table.MountNameTable

data class MountItem(val index: Int, val name: String)

object MountSelectUi {

    private val items by lazy { MountNameTable.getAllFirst().mapIndexed { index, name -> MountItem(index, name) } }

    private val mountSelectContext = UiState(
        additionalDraw = { draw(it) },
        focusMenu = "menu    magic   ",
        menuStacks = MenuStacks.LogStack,
        appendType = AppendType.StackAndAppend,
        resetCursorIndexOnPush = false,
        scrollSettings = ScrollSettings(numElementsInPage = 12) { getItems().size },
    ) {
        if (UiStateHelper.isEnterPressed()) {
            val itemIndex = UiStateHelper.currentItemIndex()
            useSelectedAbility(itemIndex)
            true
        } else if (UiStateHelper.isEscPressed()) {
            UiStateHelper.popState(SystemSound.MenuClose)
            true
        } else {
            false
        }
    }

    fun push() {
        UiStateHelper.pushState(mountSelectContext, SystemSound.MenuSelect)
    }

    fun getItems(): List<MountItem> {
        return items
    }

    fun draw(uiState: UiState) {
        val stackPos = uiState.latestPosition ?: return
        val offset = Vector2f(0f, 0f)

        val scrollSettings = uiState.scrollSettings!!
        val items = getItems()

        for (i in scrollSettings.lowestViewableItemIndex until scrollSettings.lowestViewableItemIndex + scrollSettings.numElementsInPage) {
            if (i >= items.size) { break }
            val mount = items[i]

            UiElementHelper.drawUiElement(lookup = "menu    magico2w", index = 87, position = offset + stackPos + Vector2f(16f, 4f))
            UiElementHelper.drawString(text = mount.name, offset = offset + stackPos + Vector2f(36f, 8f))
            offset.y += 16f
        }
    }

    fun useSelectedAbility(index: Int) {
        val item = getItems()[index]
        GameClient.submitMountEvent(ActorManager.player(), item.index)
    }

}