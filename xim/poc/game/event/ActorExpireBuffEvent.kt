package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.StatusEffect

class ActorExpireBuffEvent(
    val actorId: ActorId,
    val statusEffect: StatusEffect,
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[actorId] ?: return emptyList()

        if (statusEffect.buff && statusEffect.canDispel) {
            actorState.expireStatusEffect(statusEffect)
        }

        return emptyList()
    }

}