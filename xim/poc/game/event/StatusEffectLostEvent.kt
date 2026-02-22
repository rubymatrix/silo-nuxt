package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.StatusEffect
import xim.poc.game.StatusEffectState
import xim.poc.ui.ChatLog

class StatusEffectLostEvent(
    val sourceId: ActorId,
    val statusEffectState: StatusEffectState,
): Event {

    override fun apply(): List<Event> {
        val sourceState = ActorStateManager[sourceId] ?: return emptyList()

        val events = ArrayList<Event>()

        if (statusEffectState.statusEffect == StatusEffect.Indicolure && sourceState.bubble != null) {
            events += BubbleReleaseEvent(sourceId)
        }

        if (statusEffectState.statusEffect == StatusEffect.Doom) {
            val statusSource = ActorStateManager[statusEffectState.sourceId]
            if (statusSource != null && !statusSource.isDead() && !sourceState.isDead()) {
                events += ActorDefeatedEvent(defeated = sourceId, defeatedBy = null, actionContext = null)
                return events
            }
        }

        if (!sourceState.isDead()) {
            ChatLog.statusEffectLost(sourceState.name, statusEffectState.statusEffect)
        }

        return events
    }

}