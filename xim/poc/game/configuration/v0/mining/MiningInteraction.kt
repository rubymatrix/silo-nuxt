package xim.poc.game.configuration.v0.mining

import xim.math.Vector2f
import xim.poc.ActorId
import xim.poc.Font
import xim.poc.TextAlignment
import xim.poc.UiElementHelper
import xim.poc.audio.SystemSound
import xim.poc.game.*
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.game.configuration.v0.mining.MiningInteractionUi.drawMiningOptions
import xim.poc.game.configuration.v0.mining.MiningInteractionUi.drawPreviewItem
import xim.poc.game.configuration.v0.mining.MiningInteractionUi.getMiningOptions
import xim.poc.game.configuration.v0.mining.MiningInteractionUi.isNodeDead
import xim.poc.game.configuration.v0.mining.MiningInteractionUi.submitInteraction
import xim.poc.game.configuration.v0.mining.MiningInteractionUiState.currentNodeId
import xim.poc.game.configuration.v0.mining.MiningInteractionUiState.miningSelectContext
import xim.poc.ui.InventoryUi
import xim.resource.InventoryItemInfo
import xim.resource.InventoryItems

object MiningInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        val player = ActorStateManager.player()
        if (player.isEngaged()) { GameClient.submitPlayerDisengage() }

        if (ActorStateManager[npcId]?.isGatheringNode() != true) { return }

        currentNodeId = npcId
        UiStateHelper.pushState(miningSelectContext, SystemSound.TargetConfirm)
    }

    override fun maxInteractionDistance(npcId: ActorId): Float {
        return 3f
    }

}

private object MiningInteractionUiState {

    var currentNodeId: ActorId? = null
    val miningSelectContext: UiState

    init {
        miningSelectContext = UiState(
            additionalDraw = { drawMiningOptions(it); drawPreviewItem(it) },
            focusMenu = "menu    shop    ",
            locksMovement = true,
            resetCursorIndexOnPush = false,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { getMiningOptions().size },
        ) {
            if (isNodeDead()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else if (!ActorStateManager.player().isIdleOrEngaged()) {
                false
            } else if (UiStateHelper.isEnterPressed()) {
                submitInteraction()
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }
    }

}

private object MiningInteractionUi {

    fun getMiningOptions(): List<InventoryItemInfo> {
        val config = ActorStateManager[currentNodeId]?.getGatheringConfiguration() ?: return emptyList()
        return config.items.map { it.itemId }.map { InventoryItems[it] }
    }

    fun drawMiningOptions(uiState: UiState) {
        val stackPos = uiState.latestPosition ?: return
        val menuSize = uiState.latestMenu?.frame?.size ?: return

        val miningZoneInstance = GameV0.getCurrentMiningZoneInstance() ?: return
        val miningOptions = getMiningOptions()

        val offset = Vector2f(0f, 0f)

        val scrollSettings = uiState.scrollSettings!!
        for (i in scrollSettings.lowestViewableItemIndex until scrollSettings.lowestViewableItemIndex + scrollSettings.numElementsInPage) {
            if (i >= miningOptions.size) { break }

            val itemInfo = miningOptions[i]

            UiElementHelper.drawInventoryItemIcon(itemInfo = itemInfo, position = offset + stackPos + Vector2f(2f, 4f), scale = Vector2f(0.5f, 0.5f))
            UiElementHelper.drawString(text = itemInfo.name, offset = offset + stackPos + Vector2f(22f, 8f))

            val hitRate = miningZoneInstance.getItemHitRate(itemInfo.itemId)
            UiElementHelper.drawString(text = "${hitRate}%", offset = offset + stackPos + Vector2f(menuSize.x - 14f, 8f), font = Font.FontShp, alignment = TextAlignment.Right)

            offset.y += 18f
        }
    }

    fun drawPreviewItem(uiState: UiState) {
        val inventoryItemInfo = getSelectedItem() ?: return

        val miningZoneInstance = GameV0.getCurrentMiningZoneInstance() ?: return
        val bonus = miningZoneInstance.getProwessBonus()

        val fakeItem = InventoryItem(id = inventoryItemInfo.itemId, quantity = 1 + bonus.yieldBonus)
        InventoryUi.drawSelectedInventoryItem(fakeItem, context = uiState)
    }

    fun getSelectedItem(): InventoryItemInfo? {
        return getMiningOptions().getOrNull(getSelectedItemIndex(miningSelectContext))
    }

    fun getSelectedItemIndex(uiState: UiState): Int {
        return uiState.cursorIndex + uiState.scrollSettings!!.lowestViewableItemIndex
    }

    fun isNodeDead(): Boolean {
        val nodeState = ActorStateManager[currentNodeId] ?: return true
        return nodeState.isDead()
    }

    fun submitInteraction() {
        val nodeId = currentNodeId ?: return
        val item = getSelectedItem() ?: return

        val miningZoneInstance = GameV0.getCurrentMiningZoneInstance() ?: return
        val quantity = miningZoneInstance.getProwessBonus()

        GameEngine.submitEvent(MiningEvent(
            actorId = ActorStateManager.playerId,
            nodeId = nodeId,
            itemId = item.itemId,
            quantity = 1 + quantity.yieldBonus,
        ))
    }

}