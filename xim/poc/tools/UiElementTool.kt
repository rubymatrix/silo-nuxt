package xim.poc.tools

import kotlinx.browser.document
import org.w3c.dom.*
import xim.math.Vector2f
import xim.poc.UiElementHelper
import xim.poc.UiResourceManager
import xim.poc.gl.Color
import xim.poc.ui.ChatLog
import xim.resource.UiElementResource
import xim.resource.UiMenu
import xim.resource.UiMenuCursorKey

object UiElementTool {

    private var chosenUiType: String? = null

    private var chosenUiMenu: String? = null
    private var uiCursorIndex = 0

    private var setup = false

    private val addedMenus = HashSet<String>()

    fun setup() {
        if (setup) { return }
        setup = true

        (document.getElementById("UiMenuUp") as HTMLButtonElement).onclick = { moveCursor(UiMenuCursorKey.Up) }
        (document.getElementById("UiMenuDown") as HTMLButtonElement).onclick = { moveCursor(UiMenuCursorKey.Down) }
        (document.getElementById("UiMenuRight") as HTMLButtonElement).onclick = { moveCursor(UiMenuCursorKey.Right) }
        (document.getElementById("UiMenuLeft") as HTMLButtonElement).onclick = { moveCursor(UiMenuCursorKey.Left) }

        (document.getElementById("ChatSubmit") as HTMLButtonElement).onclick = { ChatLog.addLine((document.getElementById("ChatEntry") as HTMLInputElement).value) }
    }

    fun addUiOption(uiElementResource: UiElementResource) {
        val groupSelect = document.getElementById("UiGroupSelect") as HTMLSelectElement
        val name = uiElementResource.uiElementGroup.name

        val option = document.createElement("option") as HTMLOptionElement
        option.text = name
        option.value = name
        groupSelect.add(option)

        if (chosenUiType == null) {
            chosenUiType = name
        }

        groupSelect.onchange = {
            chosenUiType = groupSelect.value
            Unit
        }
    }

    fun addUiOption(uiMenu: UiMenu) {
        val menuSelect = document.getElementById("UiMenuSelect") as HTMLSelectElement
        val name = uiMenu.name

        if (addedMenus.contains(name)) { return }
        addedMenus += name

        val option = document.createElement("option") as HTMLOptionElement
        option.text = name
        option.value = name
        menuSelect.add(option)

        if (chosenUiMenu == null) {
            chosenUiMenu = name
        }

        menuSelect.onchange = {
            chosenUiMenu = menuSelect.value
            uiCursorIndex = 0
            Unit
        }
    }

    fun drawPreviewElement() {
        if (isCheckBox("UiPreview")) {
            val type = chosenUiType ?: return
            val index = (document.getElementById("UiGroupIndex") as HTMLInputElement).value.toIntOrNull() ?: return

            val element = UiResourceManager.getElement(type) ?: return
            val clampedIndex = index.coerceIn(0, element.uiElementGroup.uiElements.size - 1)

            val x = (document.getElementById("UiCoordX") as HTMLInputElement).value.toFloatOrNull() ?: return
            val y = (document.getElementById("UiCoordY") as HTMLInputElement).value.toFloatOrNull() ?: return

            val r = (document.getElementById("UiR") as HTMLInputElement).value.toIntOrNull() ?: return
            val g = (document.getElementById("UiG") as HTMLInputElement).value.toIntOrNull() ?: return
            val b = (document.getElementById("UiB") as HTMLInputElement).value.toIntOrNull() ?: return
            val a = (document.getElementById("UiA") as HTMLInputElement).value.toIntOrNull() ?: return

            UiElementHelper.drawUiElement(type, clampedIndex, Vector2f(x, y), Color(r, g, b, a))
            (document.getElementById("UiPreviewOffset") as HTMLSpanElement).textContent = "(@ ${element.uiElementGroup.uiElements[clampedIndex].fileOffset.toString(0x10)})"
        }

        if (isCheckBox("UiMenuPreview")) {
            val type = chosenUiMenu ?: return
            UiElementHelper.drawMenu(type, uiCursorIndex)
        }
    }

    private fun moveCursor(key: UiMenuCursorKey) {
        val type = chosenUiMenu ?: return
        val menu = UiResourceManager.getMenu(type)?.uiMenu ?: return

        val currentElement = menu.elements[uiCursorIndex]
        uiCursorIndex = currentElement.next[key]!! - 1
    }

}