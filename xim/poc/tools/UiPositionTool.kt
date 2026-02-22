package xim.poc.tools

import web.dom.document
import web.html.*
import xim.math.Axis
import xim.math.Vector2f
import xim.poc.MainTool
import xim.poc.browser.GameKey
import xim.poc.browser.LocalStorage
import xim.poc.browser.UiElementSettings

enum class UiPosition {
    ChatLog,
    Hotbar,
    TargetInfo,
    StatusBar,
    Party,
    Equipment,
    Map,
    Help,
}

object UiPositionTool {

    private var setup = false

    private var dragStartX = 0
    private var dragStartY = 0

    fun update() {
        if (MainTool.platformDependencies.keyboard.isKeyPressed(GameKey.UiConfig)) { toggleContainer() }
    }

    fun setup() {
        if (setup) { return }
        setup = true

        val entryContainer = document.getElementById("UiOffsetSettingsEntries") as HTMLElement
        UiPosition.values().forEach { setupElement(entryContainer, it) }

        val container = document.getElementById("UiOffsetSettings") as HTMLElement
        (document.getElementById("UiOffsetOpen") as HTMLButtonElement).onclick = { displayContainer(container, true) }
        (document.getElementById("UiOffsetClose") as HTMLButtonElement).onclick = { displayContainer(container, false) }

        container.ondragstart = {
            dragStartX = it.clientX
            dragStartY = it.clientY
        }

        container.ondragend = {
            val dX = it.clientX - dragStartX
            val dY = it.clientY - dragStartY

            val x = container.style.left.dropLast(2).toIntOrNull() ?: 0
            val y = container.style.top.dropLast(2).toIntOrNull() ?: 0

            container.style.left = "${x + dX}px"
            container.style.top = "${y + dY}px"
        }
    }

    fun getOffset(uiPosition: UiPosition?): Vector2f {
        uiPosition ?: return Vector2f()

        val currentSetting = LocalStorage.getConfiguration().uiPositionSettings
            .getOrPut(uiPosition) { UiElementSettings() }

        return Vector2f(currentSetting.offsetX.toFloat(), -currentSetting.offsetY.toFloat())
    }

    private fun setupElement(container: HTMLElement, uiPosition: UiPosition) {
        val label = document.createElement("label") as HTMLLabelElement
        label.innerText = uiPosition.name + ":"
        container.appendChild(label)

        val offsetContainer = document.createElement("div") as HTMLDivElement
        container.appendChild(offsetContainer)

        val currentSetting = LocalStorage.getConfiguration().uiPositionSettings
            .getOrPut(uiPosition) { UiElementSettings() }

        val dX = makeInput(currentSetting.offsetX, uiPosition.name + "X")
        offsetContainer.appendChild(dX)
        dX.onchange = { onChange(dX, uiPosition, Axis.X) }

        val dY = makeInput(currentSetting.offsetY, uiPosition.name + "Y")
        offsetContainer.appendChild(dY)
        dY.onchange = { onChange(dY, uiPosition, Axis.Y) }
    }

    private fun makeInput(value: Int, id: String): HTMLInputElement {
        val input = document.createElement("input") as HTMLInputElement
        input.classList.add("shortInput")
        input.type = InputType.number
        input.value = "$value"
        input.id = id
        return input
    }

    private fun displayContainer(element: HTMLElement, show: Boolean) {
        element.style.display = if (show) { "block" } else { "none" }
    }

    private fun toggleContainer() {
        val container = document.getElementById("UiOffsetSettings") as HTMLElement
        container.style.display = if (container.style.display == "block") { "none" } else { "block" }
    }

    private fun onChange(element: HTMLInputElement, uiPosition: UiPosition, axis: Axis) {
        val value = element.value.toIntOrNull() ?: 0

        LocalStorage.changeConfiguration {
            val s = it.uiPositionSettings.getOrPut(uiPosition) { UiElementSettings() }

            when (axis) {
                Axis.X -> s.offsetX = value
                Axis.Y -> s.offsetY = value
                else -> throw IllegalStateException()
            }
        }
    }

}