package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.getEquipment
import xim.resource.EquipSlot

class ActorGainRpEvent(
    val actorId: ActorId,
    val defeatedTargetId: ActorId,
    val actionContext: AttackContext? = null,
): Event {

    override fun apply(): List<Event> {
        val actor = ActorStateManager[actorId] ?: return emptyList()
        val defeated = ActorStateManager[defeatedTargetId] ?: return emptyList()

        return EquipSlot.values()
            .mapNotNull { actor.getEquipment(it) }
            .mapNotNull { gainRp(actor, defeated, it) }
    }

    private fun gainRp(actorState: ActorState, defeatedTarget: ActorState, inventoryItem: InventoryItem): Event? {
        inventoryItem.augments ?: return null
        val rp = GameState.getGameMode().getAugmentRankPointGain(actorState, defeatedTarget, inventoryItem)
        return InventoryItemRpGainEvent(actorId, inventoryItem.internalId, rp, actionContext)
    }

}