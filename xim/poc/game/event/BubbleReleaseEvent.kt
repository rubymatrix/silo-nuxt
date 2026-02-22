package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.poc.game.StatusEffect
import xim.resource.DatId

class BubbleReleaseEvent(
    val ownerId: ActorId
) : Event {

    override fun apply(): List<Event> {
        val ownerState = ActorStateManager[ownerId] ?: return emptyList()
        val bubbleId = ownerState.bubble
        ownerState.bubble = null

        if (ownerState.hasStatusEffect(StatusEffect.Indicolure)) {
            ownerState.expireStatusEffect(StatusEffect.Indicolure)
        }

        val bubbleState = ActorStateManager[bubbleId] ?: return emptyList()
        bubbleState.setHp(0)

        val bubbleModel = ActorManager[bubbleId]
        bubbleModel?.enqueueModelRoutineIfReady(DatId("sdep"))

        return emptyList()
    }

}