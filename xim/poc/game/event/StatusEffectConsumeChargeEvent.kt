package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.StatusEffect

class StatusEffectConsumeChargeEvent(
    val sourceId: ActorId,
    val statusEffect: StatusEffect,
    val expireOnZero: Boolean = true,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()
        source.consumeStatusEffectCharge(statusEffect = statusEffect, expireOnZero = expireOnZero)
        return emptyList()
    }

}