package xim.poc.game.configuration.v0.synthesis

import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.SynthesisResultType.*
import xim.poc.game.actor.components.countNotEquippedItems
import xim.poc.game.actor.components.getInventory
import xim.poc.game.actor.components.isEquipped
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.interactions.ItemId
import xim.poc.game.configuration.v0.mining.MiningExpEvent
import xim.poc.game.event.Event
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.resource.InventoryItemInfo
import xim.resource.InventoryItems
import kotlin.math.min

class SynthesisCompleteEvent(val sourceId: ActorId): Event {

    companion object {

        private const val materialLossChance = 50

        fun getMaximumCraftable(actor: ActorId, recipe: V0SynthesisRecipe): Int {
            val actorState = ActorStateManager[actor] ?: return 0

            var max = 99

            val crystalCount = actorState.countNotEquippedItems(recipe.getCrystalType())
            max = min(max, crystalCount)

            for (input in recipe.input.distinct()) {
                val recipeCount = recipe.input.count { it == input }
                val inventoryCount = actorState.countNotEquippedItems(input)
                max = min(max, inventoryCount / recipeCount)
            }

            return max
        }

    }

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()
        val synthesisState = actorState.getComponentAs(ActorSynthesisComponent::class) ?: return emptyList()
        val recipe = V0SynthesisRecipes[synthesisState.recipeId] ?: return emptyList()

        val result = if (synthesisState.quantity > getMaximumCraftable(sourceId, recipe)) {
            SynthesisResult(Break)
        } else {
            GameV0.getSynthesisResult(actorState, synthesisState, recipe)
        }

        val output = ArrayList<Event>()

        val outputItem = InventoryItems[recipe.output.itemId]
        val expMultiplier = resultExpMultiplier(result.type) * itemTypeExpMultiplier(outputItem, synthesisState)
        output += MiningExpEvent(itemId = recipe.output.itemId, context = synthesisState.attempt.context, expMultiplier = expMultiplier)

        consumeItem(actorState = actorState, quantity = synthesisState.quantity, itemId = recipe.getCrystalType())

        if (result.type == Break) {
            AttackContext.compose(synthesisState.attempt.context) { ChatLog("Synthesis failed!", ChatLogColor.Info) }
            consumeItemsOnBreak(actorState, synthesisState, recipe)
        } else if (result.item != null) {
            consumeItemsOnSuccess(actorState, synthesisState, recipe)
            actorState.getInventory().addItem(result.item)
            AttackContext.compose(synthesisState.attempt.context) { ChatLog.gainedItem(actorState, result.item, result.item.quantity) }
        }

        synthesisState.setResult(result.type)
        return output
    }

    private fun consumeItemsOnBreak(actorState: ActorState, synthesisState: ActorSynthesisComponent, recipe: V0SynthesisRecipe) {
        for (inputId in recipe.input) {
            val quantityLoss = (0 until synthesisState.quantity)
                .count { GameEngine.rollAgainst(materialLossChance) }

            if (quantityLoss == 0) { continue }
            consumeItem(actorState, quantityLoss, inputId)

            AttackContext.compose(synthesisState.attempt.context) {
                ChatLog.synthesisItemLoss(actorState, InventoryItems[inputId], quantityLoss)
            }
        }
    }

    private fun consumeItemsOnSuccess(actorState: ActorState, synthesisState: ActorSynthesisComponent, recipe: V0SynthesisRecipe) {
        for (inputId in recipe.input) { consumeItem(actorState, synthesisState.quantity, inputId) }
    }

    private fun consumeItem(actorState: ActorState, quantity: Int, itemId: ItemId) {
        val inventory = actorState.getInventory()

        var remainingToRemove = quantity
        val matching = inventory.inventoryItems.filter { it.id == itemId }

        for (match in matching) {
            if (actorState.isEquipped(match)) { continue }

            val toRemove = min(match.quantity, remainingToRemove)
            match.quantity -= toRemove
            if (match.quantity == 0) { inventory.discardItem(match.internalId) }

            remainingToRemove -= toRemove
            if (remainingToRemove == 0) { break }
        }
    }

    private fun resultExpMultiplier(result: SynthesisResultType): Float {
        return when (result) {
            Break -> 1f
            NormalQuality -> 2f
            HighQuality -> 4f
        }
    }

    private fun itemTypeExpMultiplier(outputItem: InventoryItemInfo, synthesisState: ActorSynthesisComponent): Float {
        return if (outputItem.isStackable()) { synthesisState.quantity.toFloat() } else { 8f }
    }

}