package xim.poc.game.configuration.v0.events

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.AugmentHelper
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.getInventory
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.event.Event
import xim.poc.game.event.InventoryItemRpGainEvent
import xim.poc.ui.ChatLog
import xim.poc.ui.ShiftJis
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

class ActorReinforceItemEvent(
    val sourceId: ActorId,
    val targetItemId: InternalItemId,
    val upgradeItemId: InternalItemId,
    val upgradeItemQuantity: Int,
): Event {

    companion object {
        fun calculateMaximumUsableUpgradeMaterial(upgradeItem: InventoryItem, targetItem: InventoryItem): Int {
            val reinforcementValues = GameV0.getItemReinforcementValues(targetItem)
            val reinforcementValue = reinforcementValues[upgradeItem.id] ?: return 0

            val augment = targetItem.augments ?: return 0

            var cumulativeSum = 0

            cumulativeSum += AugmentHelper.getRpToNextLevel(augment) - augment.rankPoints

            for (i in augment.rankLevel + 1 until augment.maxRankLevel) {
                val augmentCopy = augment.copy(rankLevel = i)
                cumulativeSum += AugmentHelper.getRpToNextLevel(augmentCopy)
            }

            val maxPossible = ceil(cumulativeSum / reinforcementValue.toFloat()).roundToInt()
            return min(upgradeItem.quantity, maxPossible)
        }
    }

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()

        val inventoryItem = source.getInventory().getByInternalId(targetItemId) ?: return emptyList()

        val upgradeItem = source.getInventory().getByInternalId(upgradeItemId) ?: return emptyList()
        if (upgradeItem.quantity < upgradeItemQuantity) { return emptyList() }

        val reinforcementValues = GameV0.getItemReinforcementValues(inventoryItem)
        val reinforcementValue = reinforcementValues[upgradeItem.id] ?: return emptyList()

        val maxUpgradeItemUsable = calculateMaximumUsableUpgradeMaterial(upgradeItem = upgradeItem, targetItem = inventoryItem)
        if (maxUpgradeItemUsable < upgradeItemQuantity) { return emptyList() }

        source.getInventory().discardItem(upgradeItemId, upgradeItemQuantity)
        val rpGained = upgradeItemQuantity * reinforcementValue

        if (source.isPlayer()) {
            ChatLog("${ShiftJis.colorAug}[${inventoryItem.info().name}] gained $rpGained RP!${ShiftJis.colorClear}")
        }

        return listOf(InventoryItemRpGainEvent(sourceId, targetItemId, rpGained))
    }

}