package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.StatusEffect

class ActorTargetEvent(
    val sourceId: ActorId,
    val targetId: ActorId?,
    val locked: Boolean = false,
): Event {

    override fun apply(): List<Event> {
        val sourceState = ActorStateManager[sourceId] ?: return emptyList()
        if (sourceState.isDead()) { return emptyList() }

        val target = ActorStateManager[targetId]
        if (target != null && !target.targetable) { return emptyList() }

        val provoke = sourceState.getStatusEffect(StatusEffect.Provoke)
        if (provoke != null && target != null && sourceId != targetId && provoke.sourceId != targetId) { return emptyList() }

        sourceState.setTargetState(target?.id, if (sourceId == targetId) { false } else { locked })

        return emptyList()
    }

}