package xim.poc.ui

import xim.math.Vector2f
import xim.poc.Font
import xim.poc.TextAlignment
import xim.poc.UiElementHelper
import xim.poc.game.*
import xim.poc.game.configuration.v0.GameV0
import xim.poc.gl.ByteColor
import xim.poc.gl.Color
import xim.poc.tools.UiPosition
import xim.poc.tools.UiPositionTool
import xim.resource.table.StatusEffectHelper
import xim.resource.table.StatusEffectNameTable
import kotlin.math.ceil
import kotlin.math.roundToInt

object TargetInfoUi {

    fun draw() {
        if (GameState.getGameMode() != GameV0) { return }

        val target = ActorStateManager.playerTarget() ?: return
        if (target.monsterId == null) { return }

        val basePosition = Vector2f(320f, 120f) + UiPositionTool.getOffset(UiPosition.TargetInfo)

        val hpp = target.getHpp()
        UiElementHelper.drawString(target.name, offset = basePosition + Vector2f(4f, 0f), color = ByteColor(0x80, 0x50, 0x50, 0x80), font = Font.FontShp)

        val hppString = ceil(hpp * 100f).roundToInt()
        UiElementHelper.drawString("$hppString%", offset = basePosition + Vector2f(194f, 0f), color = ByteColor(0x80, 0x50, 0x50, 0x80), alignment = TextAlignment.Right, font = Font.FontShp)

        UiElementHelper.drawUiElement(lookup = "menu    framesus", index = 103, position = basePosition + Vector2f(5f, 12f), scale = Vector2f(1.5f * hpp, 1f), color = Color(1f, 0f, 0f, 1f))
        UiElementHelper.drawUiElement(lookup = "menu    framesus", index = 98, position = basePosition + Vector2f(0f, 12f), scale = Vector2f(1.5f, 1f))

        drawCastingState(target, basePosition)

        val statusEffects = target.getStatusEffects().take(30)
        for (i in statusEffects.indices) {
            val status = statusEffects[i]
            val info = StatusEffectHelper[status.statusEffect]
            info.icon.texture ?: continue

            val row = i / 10
            val col = i % 10
            val iconPosition = basePosition + Vector2f(24f * col, 26f + 24f * row)

            UiElementHelper.drawStatusEffect(status, position = iconPosition, color = ByteColor.half.copy(a = 0x60))

            ToolTipHelper.addToolTip(position = iconPosition, size = Vector2f(16f, 16f)) {
                val builder = StringBuilder()
                builder.appendLine(StatusEffectNameTable.first(status.statusEffect.id))

                val description = GameState.getGameMode().getStatusDescription(target, status)
                if (!description.isNullOrBlank()) { builder.appendLine(description) }

                if (status.canDispel) { builder.appendLine("Dispellable")}

                builder.toString()
            }
        }

    }

    private fun drawCastingState(target: ActorState, basePosition: Vector2f) {
        val castingState = target.getCastingState() ?: return
        if (!castingState.isCharging()) { return }

        val position = basePosition + Vector2f(200f, 0f)

        val skillName = GameEngine.getSkillLogName(castingState.skill) ?: return
        UiElementHelper.drawString(text = skillName, offset = position + Vector2f(22f, 0f), font = Font.FontShp)

        val progress = castingState.percentProgress()

        UiElementHelper.drawUiElement(lookup = "menu    framesus", index = 103, position = position + Vector2f(23f, 12f), scale = Vector2f(0.005f * progress, 1f))
        UiElementHelper.drawUiElement(lookup = "menu    framesus", index = 98, position = position + Vector2f(20f, 12f), scale = Vector2f(0.5f, 1f))

        UiElementHelper.drawString(text = "$progress%", offset = position + Vector2f(114f, 10f), font = Font.FontShp, alignment = TextAlignment.Right)
    }

}