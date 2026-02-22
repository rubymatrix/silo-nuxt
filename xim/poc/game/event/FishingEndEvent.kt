package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorFishingAttempt
import xim.poc.game.ActorStateManager
import xim.poc.game.GameState

class FishingEndEvent(
    val actorId: ActorId,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[actorId] ?: return emptyList()

        val actionState = source.actionState
        if (actionState !is ActorFishingAttempt) { return emptyList() }
        source.clearActionState(actionState)

        val fishingRodId = source.fishingRod ?: return emptyList()
        source.fishingRod = null

        return listOf(ActorDeleteEvent(fishingRodId))
    }

}