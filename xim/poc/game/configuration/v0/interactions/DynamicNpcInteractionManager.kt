package xim.poc.game.configuration.v0.interactions

import xim.poc.ActorId
import xim.poc.game.ActorStateManager

class DynamicNpcInteractionManager {

    private val npcInteractions = HashMap<ActorId, NpcInteraction>()

    fun update(elapsedFrames: Float) {
        npcInteractions.entries.removeAll { ActorStateManager[it.key] == null }
    }

    fun registerInteraction(actorId: ActorId, npcInteraction: NpcInteraction) {
        npcInteractions[actorId] = npcInteraction
    }

    fun getInteraction(actorId: ActorId): NpcInteraction? {
        return npcInteractions[actorId]
    }

}