package xim.poc.ui

import xim.math.Vector2f
import xim.poc.Font
import xim.poc.TextAlignment
import xim.poc.UiElementHelper
import xim.poc.game.ActorStateManager
import xim.poc.game.FishSize
import xim.poc.gl.ByteColor
import xim.poc.gl.Color
import xim.poc.tools.UiPosition
import xim.poc.tools.UiPositionTool
import kotlin.math.ceil
import kotlin.math.roundToInt

object FishHppUi {

    fun draw() {
        val fishingState = ActorStateManager.player().getFishingState() ?: return
        if (!fishingState.currentState.active) { return }

        val basePosition = Vector2f(480f, 120f) + UiPositionTool.getOffset(UiPosition.TargetInfo)

        val hpp = fishingState.fishHpp
        val name = when(fishingState.fishSize) {
            FishSize.Small -> "Small Fish"
            FishSize.Large -> "Large Fish"
        }
        UiElementHelper.drawString(name, offset = basePosition + Vector2f(4f, 0f), color = ByteColor(0x80, 0x50, 0x50, 0x80), font = Font.FontShp)

        val hppString = ceil(hpp * 100f).roundToInt()
        UiElementHelper.drawString("$hppString%", offset = basePosition + Vector2f(194f, 0f), color = ByteColor(0x80, 0x50, 0x50, 0x80), alignment = TextAlignment.Right, font = Font.FontShp)

        UiElementHelper.drawUiElement(lookup = "menu    framesus", index = 103, position = basePosition + Vector2f(5f, 12f), scale = Vector2f(1.5f * hpp, 1f), color = Color(1f, 0f, 0f, 1f))
        UiElementHelper.drawUiElement(lookup = "menu    framesus", index = 98, position = basePosition + Vector2f(0f, 12f), scale = Vector2f(1.5f, 1f))
    }

}