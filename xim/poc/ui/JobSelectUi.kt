package xim.poc.ui

import xim.poc.Font
import xim.poc.UiElementHelper
import xim.poc.UiResourceManager
import xim.poc.game.ActorStateManager
import xim.poc.game.Job
import xim.poc.game.UiState

object JobSelectUi {

    private const val menuName = "menu    jobcselu"

    fun draw(uiState: UiState) {
        val menu = UiResourceManager.getMenu(menuName) ?: return
        val position = uiState.latestPosition ?: return

        val player = ActorStateManager.player()

        for (job in Job.values()) {
            if (job.index < Job.War.index || job.index > Job.Run.index) { continue }
            val jobLevel = player.getJobLevel(job) ?: continue

            val button = menu.uiMenu.elements[job.index - 1]
            val offset = position + button.offset

            offset.x += button.size.x + 4
            offset.y += 2

            val color = if (player.jobState.mainJob == job) {
                UiElementHelper.getStandardTextColor(20)
            } else if (player.jobState.subJob == job) {
                UiElementHelper.getStandardTextColor(17)
            } else {
                UiElementHelper.getStandardTextColor(0)
            }

            val level = jobLevel.level.toString().padStart(2, ' ')
            UiElementHelper.drawString(text = "L.$level", offset = offset, color = color, font = Font.FontShp)
        }

    }

}