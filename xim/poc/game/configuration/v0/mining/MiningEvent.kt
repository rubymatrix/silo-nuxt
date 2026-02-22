package xim.poc.game.configuration.v0.mining

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.*
import xim.poc.game.actor.components.getInventory
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.event.Event
import xim.poc.game.event.InventoryItemTransferEvent
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import kotlin.time.Duration.Companion.seconds

class MiningEvent(
    val actorId: ActorId,
    val nodeId: ActorId,
    val itemId: Int,
    val quantity: Int,
): Event {

    override fun apply(): List<Event> {
        val actor = ActorStateManager[actorId] ?: return emptyList()
        if (!actor.isIdle()) { return emptyList() }

        val node = ActorStateManager[nodeId] ?: return emptyList()
        if (node.isDead()) { return emptyList() }

        val gatheringConfiguration = node.getGatheringConfiguration()
        if (Vector3f.distance(actor.position, node.position) > 3f) { return emptyList() }

        val hasMatchingItem = gatheringConfiguration.items.any { it.itemId == itemId }
        if (!hasMatchingItem) { return emptyList() }

        val miningInstance = GameV0.getCurrentMiningZoneInstance() ?: return emptyList()
        val miningHitRate = miningInstance.getItemHitRate(itemId)
        val success = GameEngine.rollAgainst(miningHitRate)

        val context = AttackContext()

        actor.faceToward(node)

        val proposedState = ActorGatheringAttempt(type = GatheringType.Mining, context = context)
        if (!actor.initiateAction(proposedState)) { return emptyList() }

        actor.components[ActorGatheringComponent::class] = ActorGatheringComponent(
            attempt = proposedState,
            attemptDuration = 3.25.seconds,
            actionContext = context,
        )

        val output = ArrayList<Event>()

        if (success) {
            node.getInventory().addItem(itemId, quantity)
            val inventoryItem = node.getInventory().inventoryItems.first { it.id == itemId }

            output += InventoryItemTransferEvent(
                sourceId = nodeId,
                destinationId = actorId,
                inventoryItemId = inventoryItem.internalId,
                quantity = quantity,
                actionContext = context
            )

            val prowessBonus = miningInstance.getProwessBonus()
            if (actor.isPlayer()) { output += MiningExpEvent(itemId = itemId, expMultiplier = prowessBonus.expBonus.toFloat(), context = context) }
        } else {
            AttackContext.compose(context) { ChatLog("Miss!", ChatLogColor.Info) }
        }

        node.consumeHp(1)
        if (node.isDead()) { ActorManager[nodeId]?.onDisplayDeath() }

        return output
    }

}