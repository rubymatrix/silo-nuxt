package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.StatusEffectState
import xim.poc.ui.ChatLog

class StatusEffectGainedEvent(
    val targetId: ActorId,
    val statusEffectState: StatusEffectState,
    val context: AttackContext? = null,
): Event {

    override fun apply(): List<Event> {
        val targetState = ActorStateManager[targetId] ?: return emptyList()

        targetState.gainStatusEffect(state = statusEffectState)
        ChatLog.statusEffectGained(actorName = targetState.name, statusEffect = statusEffectState.statusEffect, actionContext = context)

        return emptyList()
    }

}