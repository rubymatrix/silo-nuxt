package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.GameEngine

class ActorPostCreateEvent(
    val sourceId: ActorId
) : Event {

    override fun apply(): List<Event> {
        val actor = ActorStateManager[sourceId] ?: return emptyList()
        val combatStats = GameEngine.computeCombatStats(sourceId)

        actor.updateCombatStats(combatStats)

        actor.setHp(actor.getMaxHp())
        actor.setMp(actor.getMaxMp())

        return actor.behaviorController.onInitialized()
    }

}