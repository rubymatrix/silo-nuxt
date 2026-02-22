package xim.poc.ui

import xim.math.Vector2f
import xim.poc.UiElementHelper
import xim.poc.game.QueryMenuOption
import xim.poc.game.UiState
import kotlin.math.min

object QueryUi {

    fun draw(uiState: UiState, prompt: String, options: List<QueryMenuOption>) {
        val position = uiState.latestPosition ?: return
        UiElementHelper.drawString(prompt, position + Vector2f(8f, 10f))

        val menu = uiState.latestMenu ?: return
        val scrollSettings = uiState.scrollSettings ?: return

        val max = min(options.size, scrollSettings.numElementsInPage)
        for (i in 0 until  max) {
            val itemIndex = i + scrollSettings.lowestViewableItemIndex
            val option = options[itemIndex].text

            val element = menu.elements[i]
            val elementOffset = element.offset + position
            UiElementHelper.drawString(option, elementOffset)
        }
    }

}