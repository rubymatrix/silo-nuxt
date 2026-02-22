package xim.poc.game.event

import xim.poc.ActionTargetFilter
import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.EngagedState

class BattleEngageEvent(
    val sourceId: ActorId,
    val targetId: ActorId?,
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()
        if (actorState.isDead() || actorState.isOccupied()) { return emptyList() }

        val targetState = ActorStateManager[targetId]
        if (targetState != null && !ActionTargetFilter.areEnemies(actorState, targetState)) { return emptyList() }
        if (targetState != null && targetState.isDead()) { return emptyList() }

        if (actorState.isIdle()) { actorState.setEngagedState(EngagedState.State.Engaged) }

        val outputEvents = ArrayList<Event>()
        outputEvents += ActorTargetEvent(sourceId, targetId, locked = false)

        return outputEvents
    }

}