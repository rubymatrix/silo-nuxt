package xim.poc.game.configuration.v0.abyssea

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.discardNotEquippedItems
import xim.poc.game.actor.components.getInventory
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.interactions.ItemId
import xim.poc.game.configuration.v0.interactions.Quantity
import xim.poc.game.event.Event

class AugmentRerollEvent(
    val actorId: ActorId,
    val cost: Pair<ItemId, Quantity>,
    val targetItem: InternalItemId,
): Event {

    override fun apply(): List<Event> {
        val actor = ActorStateManager[actorId] ?: return emptyList()
        if (!actor.discardNotEquippedItems(cost.first, cost.second)) { return emptyList() }

        val item = actor.getInventory().getByInternalId(targetItem) ?: return emptyList()

        val augments = item.augments ?: return emptyList()
        GameV0.populateItemAugments(item, augments.augmentIds.size)

        return emptyList()
    }

}