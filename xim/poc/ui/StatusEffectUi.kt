package xim.poc.ui

import xim.math.Vector2f
import xim.poc.UiElementHelper
import xim.poc.game.*
import xim.resource.UiMenuCursorKey
import xim.resource.table.StatusEffectHelper
import xim.resource.table.StatusEffectNameTable

object StatusEffectUi {

    private const val elementsPerRow = 9

    fun isValid(): Boolean {
        val player = ActorStateManager.player()
        val statuses = player.getStatusEffects()
        return statuses.isNotEmpty()
    }

    fun draw(uiState: UiState) {
        val menu = uiState.latestMenu ?: return
        val position = uiState.latestPosition ?: return

        val player = ActorStateManager.player()
        val statuses = player.getStatusEffects()

        for (i in statuses.indices) {
            val element = menu.elements[i]
            val status = statuses[i]

            val info = StatusEffectHelper[status.statusEffect]
            info.icon.texture ?: continue

            val elementPosition = position + element.offset + Vector2f(6f, 6f)
            UiElementHelper.drawStatusEffect(status, elementPosition)

            ClickHandler.registerUiClickHandler(elementPosition, size = Vector2f(18f, 18f)) {
                if (it.rightClick) {
                    expireBuff(status.statusEffect)
                    true
                } else {
                    false
                }
            }

            ToolTipHelper.addToolTip(elementPosition, size = Vector2f(18f, 18f)) {
                val builder = StringBuilder()
                builder.appendLine(StatusEffectNameTable.first(status.statusEffect.id))

                val description = GameState.getGameMode().getStatusDescription(player, status)
                if (!description.isNullOrBlank()) { builder.appendLine(description) }

                builder.toString()
            }
        }
    }

    fun getSelectedStatusEffectId(uiState: UiState): StatusEffect? {
        val player = ActorStateManager.player()
        val statuses = player.getStatusEffects()
        return statuses.getOrNull(uiState.cursorIndex)?.statusEffect
    }

    fun navigateCursor(uiState: UiState): Boolean {
        val key = UiStateHelper.getDirectionalInput() ?: return false

        val player = ActorStateManager.player()
        val statuses = player.getStatusEffects()

        if (key == UiMenuCursorKey.Left) {
            uiState.cursorIndex -= 1
        } else if (key == UiMenuCursorKey.Right) {
            uiState.cursorIndex += 1
        } else if (key == UiMenuCursorKey.Up && statuses.size > elementsPerRow) {
            uiState.cursorIndex -= elementsPerRow
        } else if (key == UiMenuCursorKey.Down && statuses.size > elementsPerRow) {
            uiState.cursorIndex += elementsPerRow
        }

        if (uiState.cursorIndex < 0) { uiState.cursorIndex = statuses.size - 1 }
        if (uiState.cursorIndex >= statuses.size) { uiState.cursorIndex = 0 }

        return true
    }

    fun expireCurrentBuff(uiState: UiState) {
        val statusEffect = getSelectedStatusEffectId(uiState) ?: return
        expireBuff(statusEffect)
    }

    private fun expireBuff(statusEffect: StatusEffect) {
        GameClient.submitExpireBuff(ActorStateManager.playerId, statusEffect)
    }

}