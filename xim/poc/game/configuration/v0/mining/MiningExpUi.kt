package xim.poc.game.configuration.v0.mining

import xim.math.Vector2f
import xim.poc.Font
import xim.poc.TextAlignment
import xim.poc.UiElementHelper
import xim.poc.game.ActorStateManager
import xim.poc.game.UiState
import xim.poc.game.UiStateHelper
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.GameV0SaveStateHelper

object MiningExpUi {

    private const val lineSpacing = 12f

    private val menu = UiState(
        focusMenu = "menu    level   ",
        additionalDraw = { drawDetails() },
    ) { false }

    fun draw() {
        if (UiStateHelper.hasActiveUi()) { return }
        if (!ActorStateManager.player().isIdle()) { return }
        UiStateHelper.submitInfoFrame(uiState = menu)
    }

    private fun drawDetails() {
        val miningState = GameV0SaveStateHelper.getState().mining
        val levelFramePosition = menu.latestPosition ?: return
        val expNext = GameV0.getExperiencePointsNeeded(miningState.level)

        // Level
        val levelOffset = Vector2f(6f, 8f)

        UiElementHelper.drawString("Level ${miningState.level}", offset = levelFramePosition + levelOffset, Font.FontShp)
        levelOffset.y += lineSpacing + 2f

        UiElementHelper.drawString("EXP", offset = levelFramePosition + levelOffset + Vector2f(x = 6f), Font.FontShp)
        levelOffset.y += lineSpacing

        UiElementHelper.drawString("${miningState.currentExp}", offset = levelFramePosition + levelOffset + Vector2f(x = 80f), Font.FontShp, alignment = TextAlignment.Right)
        levelOffset.y += lineSpacing

        UiElementHelper.drawString("NEXT Level", offset = levelFramePosition + levelOffset + Vector2f(x = 6f), Font.FontShp)
        levelOffset.y += lineSpacing

        UiElementHelper.drawString("$expNext", offset = levelFramePosition + levelOffset + Vector2f(x = 80f), Font.FontShp, alignment = TextAlignment.Right)
        levelOffset.y += lineSpacing + 1f

        // Exp bar
        val expScale = miningState.currentExp.toFloat() / expNext.toFloat()
        UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 97, position = levelFramePosition + levelOffset + Vector2f(x = 14f), scale = Vector2f(expScale, 1f))
        UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 38, position = levelFramePosition + levelOffset + Vector2f(x = 14f))
    }

}