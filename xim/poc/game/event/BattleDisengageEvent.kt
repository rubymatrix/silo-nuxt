package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.EngagedState

class BattleDisengageEvent(
    val sourceId: ActorId,
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()
        if (!actorState.isEngaged() || actorState.isOccupied()) { return emptyList() }

        actorState.setEngagedState(EngagedState.State.Disengaged)
        actorState.setTargetState(actorState.targetState.targetId, false)

        val outputEvents = ArrayList<Event>()
        outputEvents += ActorTargetEvent(sourceId, targetId = null, locked = false)

        return outputEvents
    }

}