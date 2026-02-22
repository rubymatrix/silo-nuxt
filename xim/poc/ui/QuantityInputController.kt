package xim.poc.ui

import xim.math.Vector2f
import xim.poc.Font
import xim.poc.TextAlignment
import xim.poc.UiElementHelper
import xim.poc.game.UiState
import xim.poc.game.UiStateHelper
import xim.resource.UiMenuCursorKey

class QuantityInputController(val maxValueProvider: () -> Int) {

    var value = 0
        private set

    fun refresh() {
        adjustItemControl(0)
    }

    fun processInput(): Boolean {
        val key = UiStateHelper.getDirectionalInput() ?: return false

        when (key) {
            UiMenuCursorKey.Up -> adjustItemControl(1)
            UiMenuCursorKey.Down -> adjustItemControl(-1)
            UiMenuCursorKey.Right -> adjustItemControl(-10)
            UiMenuCursorKey.Left -> adjustItemControl(10)
        }

        return true
    }

    fun draw(uiState: UiState) {
        val menu = uiState.latestMenu ?: return
        val menuOffset = uiState.latestPosition ?: return

        adjustItemControl(0)

        val elemPos = menu.elements[0].offset
        val quantityOffset = menuOffset + elemPos + Vector2f(18f, 1f)

        UiElementHelper.drawString("$value", offset = quantityOffset, font = Font.FontShp, alignment = TextAlignment.Right)

        val maximum = maxValueProvider.invoke()
        val maximumOffset = menuOffset + elemPos + Vector2f(24f, 1f)
        UiElementHelper.drawString("/ $maximum", offset = maximumOffset, font = Font.FontShp)
    }

    private fun adjustItemControl(delta: Int) {
        value += delta
        value = value.coerceIn(1, maxValueProvider.invoke())
    }

}