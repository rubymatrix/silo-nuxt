package xim.poc.game.configuration.v0.abyssea

import xim.poc.game.*
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.countNotEquippedItems
import xim.poc.game.actor.components.getEquipment
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.interactions.ItemId
import xim.poc.game.configuration.v0.interactions.Quantity
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.InventoryUi
import xim.poc.ui.ShiftJis
import xim.resource.InventoryItems

class AugmentRerollUiState(val rerollConfiguration: Pair<ItemId, Quantity>, val validItemFilter: (InventoryItem?) -> Boolean) {

    fun push() {
        val options = getOptions()
        if (options.isEmpty()) { return ChatLog("You're not wearing any rerollable items!", ChatLogColor.Error) }
        UiStateHelper.openQueryMode(prompt = "Reroll which item's augments?", options = options, drawFn = this::drawItem, callback = this::onSelection)
    }
    
    private fun getOptions(): List<QueryMenuOption> {
        val player = ActorStateManager.player()
        val rerollItems = player.getEquipment().filter { validItemFilter.invoke(it.value) }.mapNotNull { it.value }
        if (rerollItems.isEmpty()) { return emptyList() }

        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption(text = "None.", value = -1)
        rerollItems.forEach { options += QueryMenuOption(text = it.info().name, value = it.id) }
        return options
    }

    private fun onSelection(queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value <= 0) { return QueryMenuResponse.pop }
        val selectedItem = getSelectedItem(queryMenuOption) ?: return QueryMenuResponse.pop

        val remaining = ActorStateManager.player().countNotEquippedItems(rerollConfiguration.first)
        if (remaining < rerollConfiguration.second) {
            val needed = rerollConfiguration.second - remaining
            ChatLog("Need $needed more ${InventoryItems[rerollConfiguration.first].logName(needed)}!", ChatLogColor.Error)
            return QueryMenuResponse.noop
        }

        val prompt = "Reroll ${ShiftJis.colorItem}${selectedItem.info().name}${ShiftJis.colorClear} for ${rerollConfiguration.second} ${InventoryItems[rerollConfiguration.first].logName(rerollConfiguration.second)}?"

        val options = listOf(
            QueryMenuOption(text = "Yes!", value = 1),
            QueryMenuOption(text = "No.", value = -1),
        )

        UiStateHelper.openQueryMode(prompt = prompt, options = options, drawFn = { drawItem(selectedItem) }, callback = { onConfirmation(it, selectedItem) })
        return QueryMenuResponse.noop
    }

    private fun onConfirmation(queryMenuOption: QueryMenuOption?, inventoryItem: InventoryItem): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value < 0) { return QueryMenuResponse.pop }

        GameEngine.submitEvent(AugmentRerollEvent(actorId = ActorStateManager.playerId, cost = rerollConfiguration, targetItem = inventoryItem.internalId))

        MiscEffects.playExclamationProc(ActorStateManager.playerId, ExclamationProc.values().random())
        return QueryMenuResponse.pop
    }


    private fun drawItem(queryMenuOption: QueryMenuOption) {
        val item = getSelectedItem(queryMenuOption) ?: return
        drawItem(item)
    }

    private fun drawItem(item: InventoryItem) {
        val description = GameV0Helpers.getItemDescriptionInternal(inventoryItem = item)
        InventoryUi.drawSelectedInventoryItem(item, itemDescription = description)
    }

    private fun getSelectedItem(queryMenuOption: QueryMenuOption): InventoryItem? {
        val player = ActorStateManager.player()
        return player.getEquipment().values.firstOrNull { it?.id == queryMenuOption.value }
    }

}