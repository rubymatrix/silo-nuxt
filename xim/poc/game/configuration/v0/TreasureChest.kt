package xim.poc.game.configuration.v0

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.actor.components.AugmentHelper
import xim.poc.game.actor.components.Inventory
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.getInventory
import xim.poc.game.configuration.NoActionBehaviorId
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.game.event.InitialActorState
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.InventoryUi
import xim.poc.ui.ShiftJis
import xim.resource.DatId
import xim.resource.InventoryItems
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

data class TreasureChestDefinition(val position: Vector3f, val rotation: Float = 0f, val itemDefinitions: List<ItemDropDefinition>, val treasureChestLook: TreasureChestLook)

class TreasureChest(val chestDefinition: TreasureChestDefinition): FloorEntity {

    private val promise: ActorPromise = spawnChest()
    private var timeSinceOpened: Float? = null

    override fun update(elapsedFrames: Float) {
        timeSinceOpened = timeSinceOpened?.let { it + elapsedFrames }

        promise.onReady {
            if (it.getInventory().inventoryItems.isEmpty()) { onEmpty(it) }
        }
    }

    override fun cleanup() {
        promise.onReady { GameEngine.submitDeleteActor(it.id) }
    }

    private fun spawnChest(): ActorPromise {
        val inventory = Inventory()

        for (dropDef in chestDefinition.itemDefinitions) {
            val item = GameV0.generateItem(dropDef)
            inventory.addItem(item, stack = false)
        }

        return GameEngine.submitCreateActorState(
            InitialActorState(
                name = "Starter Chest",
                type = ActorType.StaticNpc,
                position = chestDefinition.position,
                rotation = chestDefinition.rotation,
                modelLook = chestDefinition.treasureChestLook.look,
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
                components = listOf(inventory),
            )
        ).onReady {
            GameV0.interactionManager.registerInteraction(it.id, TreasureChestInteraction(this))
            it.faceToward(ActorStateManager.player())
        }
    }

    fun onOpened() {
        if (timeSinceOpened != null) { return }
        timeSinceOpened = 0f
        ActorManager[promise.getIfReady()]?.enqueueModelRoutine(DatId.open)
    }

    private fun onEmpty(actorState: ActorState) {
        val openedDuration = timeSinceOpened
        if (openedDuration != null && Fps.framesToSeconds(openedDuration) < 5.seconds) { return }

        if (actorState.isDead()) { return }
        actorState.setHp(0)

        val actor = ActorManager[actorState.id] ?: return
        actor.enqueueModelRoutine(DatId("clos"))
        actor.enqueueModelRoutine(DatId("ntoe"))
    }

}

class TreasureChestInteraction(val chest: TreasureChest) : NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        val inventory = inventory(npcId)
        if (inventory == null || inventory.inventoryItems.isEmpty()) { return }

        onOpened(npcId)

        ChatLog("Press 'C' to open the Equipment Screen and equip the gear.", ChatLogColor.SystemMessage)

        val prompt = "What will you take?"

        UiStateHelper.openQueryMode(
            prompt = prompt,
            options = getOptions(npcId),
            drawFn = { drawHoveredItem(npcId, it) },
        ) { handleSelection(npcId, it) }
    }

    private fun getOptions(npcId: ActorId): List<QueryMenuOption> {
        val inventory = inventory(npcId) ?: return emptyList()
        if (inventory.inventoryItems.isEmpty()) { return emptyList() }

        val options = ArrayList<QueryMenuOption>()

        options += QueryMenuOption("Take all.", -1)

        inventory.inventoryItems.forEachIndexed { index, item ->
            val info = InventoryItems[item.id]
            val qualityColor = AugmentHelper.getQualityColorDisplay(item)

            val quantityText = if (item.quantity > 1) {
                "x${item.quantity}"
            } else {
                ""
            }

            options += QueryMenuOption("${qualityColor}${info.name} ${quantityText}${ShiftJis.colorClear}", index)
        }

        return options
    }

    private fun handleSelection(npcId: ActorId, queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null) { return QueryMenuResponse.pop }
        val inventory = inventory(npcId) ?: return QueryMenuResponse.pop

        if (queryMenuOption.value == -1) {
            inventory.inventoryItems.forEach { claimItem(npcId, it) }
        } else {
            val item = inventory.inventoryItems.getOrNull(queryMenuOption.value) ?: return QueryMenuResponse.pop
            claimItem(npcId, item)
        }

        return QueryMenuResponse.pop
    }

    private fun inventory(npcId: ActorId): Inventory? {
        return ActorStateManager[npcId]?.getInventory()
    }

    private fun claimItem(npcId: ActorId, inventoryItem: InventoryItem) {
        GameClient.submitItemTransferEvent(npcId, ActorStateManager.playerId, inventoryItem)
    }

    private fun onOpened(npcId: ActorId) {
        chest.onOpened()
    }

    private fun drawHoveredItem(npcId: ActorId, queryMenuOption: QueryMenuOption) {
        val inventory = inventory(npcId) ?: return
        val item = inventory.inventoryItems.getOrNull(queryMenuOption.value) ?: return
        InventoryUi.drawSelectedInventoryItem(item)
    }

}