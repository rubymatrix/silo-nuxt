package xim.poc.game.configuration.v0.events

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.event.ActorDefeatedEvent
import xim.poc.game.event.Event

class ActorFallsToGroundEvent(
    val defeatedId: ActorId,
    val defeatedById: ActorId = defeatedId,
    val context: AttackContext? = null
): Event {

    override fun apply(): List<Event> {
        val actor = ActorStateManager[defeatedId] ?: return emptyList()
        if (actor.isDead()) { return emptyList() }
        actor.setHp(0)
        return listOf(ActorDefeatedEvent(defeated = defeatedId, defeatedBy = defeatedById, actionContext = context))
    }

}