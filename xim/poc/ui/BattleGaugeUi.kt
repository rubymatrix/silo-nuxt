package xim.poc.ui

import xim.math.Vector2f
import xim.poc.Font
import xim.poc.TextAlignment
import xim.poc.UiElementHelper
import xim.poc.game.ActorStateManager
import xim.poc.game.UiState
import xim.poc.gl.Color

object BattleGaugeUi {

    fun draw(state: UiState) {
        val position = state.latestPosition ?: return
        val player = ActorStateManager.player()

        // Gauge bar fillings
        val hpBarScale = player.getHpp()
        UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 97, position = position + Vector2f(30f, 9f), scale = Vector2f(hpBarScale, 1f), color = Color.NO_MASK)

        val mpBarScale = player.getMpp()
        UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 97, position = position + Vector2f(30f, 19f), scale = Vector2f(mpBarScale, 1f), color = Color(1f, 2.5f, 1f, 1f))

        val tpBarScale = player.getTpp()
        UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 97, position = position + Vector2f(30f, 29f), scale = Vector2f(tpBarScale, 1f), color = Color(0.25f, 1f, 4f, 1f))

        // The gauge bar containers + HP/MP/TP
        UiElementHelper.drawUiElement("menu    framesus", index = 8, position = position + Vector2f(8f, 8f))

        // The text
        UiElementHelper.drawString(player.getHp().toString(), position + Vector2f(122f, 7f), alignment = TextAlignment.Right, font = Font.FontShp)
        UiElementHelper.drawString(player.getMp().toString(), position + Vector2f(122f, 17f), alignment = TextAlignment.Right, font = Font.FontShp)
        UiElementHelper.drawString(player.getTp().toString(), position + Vector2f(122f, 27f), alignment = TextAlignment.Right, font = Font.FontShp)
    }

}