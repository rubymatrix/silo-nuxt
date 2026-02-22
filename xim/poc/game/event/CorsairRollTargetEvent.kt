package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.StatusEffect

class CorsairRollTargetEvent(
    val statusEffect: StatusEffect,
    val rollerId: ActorId,
    val targetId: ActorId,
    val context: AttackContext,
): Event {

    override fun apply(): List<Event> {
        val rollerState = ActorStateManager[rollerId] ?: return emptyList()
        val targetState = ActorStateManager[targetId] ?: return emptyList()

        val rollerStatusState = rollerState.getStatusEffect(StatusEffect.DoubleUpChance)

        if (rollerStatusState == null) {
            targetState.expireStatusEffect(statusEffect)
            context.rollSumFlag = 12
        } else {
            val targetStatusState = targetState.gainStatusEffect(statusEffect)
            targetStatusState.counter = targetStatusState.counter
            context.rollSumFlag = targetStatusState.counter
        }

        return emptyList()
    }


}