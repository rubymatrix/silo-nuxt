package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.actor.components.AugmentHelper
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.getInventory
import xim.poc.ui.ChatLog
import xim.poc.ui.ShiftJis
import kotlin.math.min

class InventoryItemRpGainEvent(
    val sourceId: ActorId,
    val itemId: InternalItemId,
    val amount: Int,
    val actionContext: AttackContext? = null
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()
        val inventoryItem = source.getInventory().getByInternalId(itemId) ?: return emptyList()

        val augment = inventoryItem.augments ?: return emptyList()
        val originalRankLevel = augment.rankLevel

        var amountRemaining = amount
        while (amountRemaining > 0) {
            val rpNeeded = AugmentHelper.getRpToNextLevel(augment)
            if (rpNeeded == 0) { break }

            val amountToAdd = min(amountRemaining, rpNeeded)
            augment.rankPoints += amountToAdd
            amountRemaining -= amountToAdd

            if (augment.rankPoints >= rpNeeded) {
                augment.rankPoints = 0
                augment.rankLevel += 1
                GameState.getGameMode().onAugmentRankUp(actorState = source, inventoryItem = inventoryItem)
            }
        }

        val finalRankLevel = augment.rankLevel

        if (source.isPlayer() && originalRankLevel != finalRankLevel) {
            AttackContext.compose(actionContext) {
                ChatLog("${ShiftJis.colorAug}[${inventoryItem.info().name}] Rank ${originalRankLevel}${ShiftJis.rightArrow}${finalRankLevel}!${ShiftJis.colorClear}")
            }
        }

        return emptyList()
    }

}