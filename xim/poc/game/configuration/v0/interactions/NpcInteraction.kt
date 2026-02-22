package xim.poc.game.configuration.v0.interactions

import xim.poc.ActorId

interface NpcInteraction {

    fun onInteraction(npcId: ActorId)

    fun maxInteractionDistance(npcId: ActorId): Float {
        return 5f
    }

}
