package xim.poc.ui

import xim.math.Vector2f
import xim.poc.Font
import xim.poc.TextAlignment
import xim.poc.UiElementHelper
import xim.poc.game.ActorStateManager
import xim.poc.game.GameState
import xim.poc.game.Job
import xim.poc.game.UiState

object StatusWindowUi {

    private const val lineSpacing = 12f

    fun draw(uiState: UiState) {
        val playerState = ActorStateManager.player()

        val statusFramePosition = uiState.latestPosition!!
        populateStatusWindow(statusFramePosition)

        // Level
        val statusFrameSize = uiState.latestMenu!!.frame.size
        val levelFrameOffset = statusFramePosition + Vector2f(0f, statusFrameSize.y + 2f)
        val levelFramePosition = UiElementHelper.drawMenu(menuName = "menu    level   ", offsetOverride = levelFrameOffset) ?: return

        val levelOffset = Vector2f(6f, 8f)

        val mainJobLevel = playerState.getMainJobLevel()
        val maximumLevel = GameState.getGameMode().getMaximumLevel(playerState)

        val levelColor = if (mainJobLevel.level == maximumLevel) { ShiftJis.colorKey } else { ShiftJis.colorWhite }

        UiElementHelper.drawString("${levelColor}Level ${mainJobLevel.level}", offset = levelFramePosition + levelOffset, Font.FontShp)
        levelOffset.y += lineSpacing

        UiElementHelper.drawString("EXP", offset = levelFramePosition + levelOffset + Vector2f(x = 6f), Font.FontShp)
        levelOffset.y += lineSpacing

        UiElementHelper.drawString("${mainJobLevel.exp}", offset = levelFramePosition + levelOffset + Vector2f(x = 80f), Font.FontShp, alignment = TextAlignment.Right)
        levelOffset.y += lineSpacing

        UiElementHelper.drawString("NEXT Level", offset = levelFramePosition + levelOffset + Vector2f(x = 6f), Font.FontShp)
        levelOffset.y += lineSpacing

        UiElementHelper.drawString("${mainJobLevel.getExpNeeded()}", offset = levelFramePosition + levelOffset + Vector2f(x = 80f), Font.FontShp, alignment = TextAlignment.Right)
        levelOffset.y += lineSpacing + 1f

        // Exp bar
        val expScale = mainJobLevel.exp.toFloat() / mainJobLevel.getExpNeeded().toFloat()
        UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 97, position = levelFramePosition + levelOffset + Vector2f(x = 14f), scale = Vector2f(expScale, 1f))
        UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 38, position = levelFramePosition + levelOffset + Vector2f(x = 14f))
    }

    fun populateStatusWindow(statusFramePosition: Vector2f) {
        val player = ActorStateManager.player()

        val textOffset = statusFramePosition + Vector2f(4f, 8f)

        UiElementHelper.drawString(player.name, offset = textOffset, Font.FontShp)

        val mainJobLevel = player.getMainJobLevel()
        UiElementHelper.drawString("Lv${mainJobLevel.level}", offset = textOffset + Vector2f(0f, lineSpacing), Font.FontShp)

        if (player.jobState.mainJob != Job.Nop) {
            val mainJobText = 212 + player.jobState.mainJob.index
            UiElementHelper.drawUiElement("menu    windowps", index = mainJobText, position = textOffset + Vector2f(34f, lineSpacing))
        }

        val subJobLevel = player.getSubJobLevel()
        if (subJobLevel != null) {
            UiElementHelper.drawString("Lv${subJobLevel}", offset = textOffset + Vector2f(0f, lineSpacing*2), Font.FontShp)
            val subJobText = 212 + player.jobState.subJob!!.index
            UiElementHelper.drawUiElement("menu    windowps", index = subJobText, position = textOffset + Vector2f(34f, lineSpacing*2))
        }

        val statOffset = textOffset + Vector2f(60f, lineSpacing * 4 + 2f)
        val statSpacing = Vector2f()

        // HP
        UiElementHelper.drawString("${player.getHp()}/${player.getMaxHp()}", offset = statOffset + statSpacing, Font.FontShp, alignment = TextAlignment.Center)
        statSpacing.y += lineSpacing

        // MP
        UiElementHelper.drawString("${player.getMp()}/${player.getMaxMp()}", offset = statOffset + statSpacing, Font.FontShp, alignment = TextAlignment.Center)
        statSpacing.y += lineSpacing

        // TP
        UiElementHelper.drawString("0", offset = statOffset + statSpacing, Font.FontShp, alignment = TextAlignment.Center)
        statSpacing.y += lineSpacing + 1f

        val combatStats = player.combatStats

        val stats = listOf(
            combatStats.str,
            combatStats.dex,
            combatStats.vit,
            combatStats.agi,
            combatStats.int,
            combatStats.mnd,
            combatStats.chr,
        )

        for (stat in stats) {
            UiElementHelper.drawString(stat.toString(), offset = statOffset + statSpacing, Font.FontShp, alignment = TextAlignment.Center)
            statSpacing.y += 13f
        }
    }

}