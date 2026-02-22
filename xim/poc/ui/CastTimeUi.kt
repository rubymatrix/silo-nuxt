package xim.poc.ui

import xim.math.Vector2f
import xim.poc.Font
import xim.poc.TextAlignment
import xim.poc.UiElementHelper
import xim.poc.game.ActorStateManager
import xim.poc.tools.UiPosition
import xim.resource.UiMenuElement

object CastTimeUi {

    fun draw() {
        val castingState = ActorStateManager.player().getCastingState() ?: return
        if (castingState.castTime == 0f) { return }

        val progress = castingState.percentProgress().coerceAtMost(100)
        if (progress == 100) { return }

        val progressScale = progress.toFloat() / 100f

        val position = UiElementHelper.drawMenu("menu    casttime", uiPosition = UiPosition.StatusBar, elementPositionOverride = this::overrideMenuPosition) {
            UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 97, position = Vector2f(17f, 9f) + it, scale = Vector2f(progressScale, 1f))
        } ?: return

        UiElementHelper.drawString("$progress", offset = Vector2f(106f, 6f) + position, font = Font.FontShp, alignment = TextAlignment.Right)
    }

    // TODO This is used to center the element - is there a config on the menu element itself that does this...?
    private fun overrideMenuPosition(element: UiMenuElement): Vector2f {
        return if (element.defaultOption().elementIndex == 0x3B) {
            Vector2f(x = -8f, y = element.offset.y)
        } else {
            element.offset
        }
    }

}