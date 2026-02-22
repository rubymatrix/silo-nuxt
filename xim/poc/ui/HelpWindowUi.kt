package xim.poc.ui

import xim.math.Vector2f
import xim.poc.MainTool
import xim.poc.TextAlignment
import xim.poc.UiElementHelper
import xim.poc.UiResourceManager
import xim.poc.game.GameState
import xim.poc.game.UiState
import xim.poc.game.UiStateHelper
import xim.poc.tools.UiPosition
import kotlin.math.roundToInt

object HelpWindowUi {

    private const val helpWindowLength = 600f

    private val uiState = UiState(
        focusMenu = "menu    helpwind",
        additionalDraw = this::drawText,
        uiPositionKey = UiPosition.Help,
    ) { false }

    private var message: String = ""
    private var pendingText: String = ""

    fun clearText() {
        message = ""
    }

    fun setText(text: String) {
        message = text
    }

    fun draw() {
        adjustFrameSize()

        pendingText = if (message.isNotBlank()) {
            message
        } else if (GameState.gameSpeed != 1f) {
            "Game speed: ${(GameState.gameSpeed * 100f).roundToInt()}%"
        } else {
            return
        }

        UiStateHelper.submitInfoFrame(uiState)
    }

    private fun drawText(uiState: UiState) {
        val frame = uiState.latestMenu?.frame ?: return
        UiElementHelper.drawString(text = pendingText, offset = frame.offset + Vector2f(frame.size.x/2f, 10f), alignment = TextAlignment.Center)
    }

    private fun adjustFrameSize() {
        val helpWind = UiResourceManager.getMenu("menu    helpwind") ?: return
        val screenSettings = MainTool.platformDependencies.screenSettingsSupplier

        val width = (screenSettings.width - 2 * helpWind.uiMenu.frame.offset.x).coerceAtLeast(400f)
        helpWind.uiMenu.frame.size.x = width
    }

}