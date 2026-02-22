package xim.poc.game.configuration.v0

import xim.math.Vector3f
import xim.poc.*
import xim.poc.audio.SystemSound
import xim.poc.game.*
import xim.poc.game.actor.components.*
import xim.poc.game.configuration.ItemDropSlot
import xim.poc.game.configuration.MonsterSpawnerInstance
import xim.poc.game.configuration.NoActionBehaviorId
import xim.poc.game.configuration.WeightedTable
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.game.event.InitialActorState
import xim.poc.ui.InventoryUi
import xim.poc.ui.ShiftJis
import xim.resource.DatId
import xim.resource.InventoryItems
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

enum class TreasureChestLook(val look: ModelLook) {
    Blue(ModelLook.npc(0x3c5)),
    BrownSimple(ModelLook.npc(0x3c6)),
    Brown(ModelLook.npc(0x3c7)),
    Red(ModelLook.npc(0x3c8)),
    Gold(ModelLook.npc(0x3c9)),
    Dark(ModelLook.npc(0x979)),
    Rainbow(ModelLook.npc(0x97A)),
}

fun interface DropTableProvider {
    fun getDropTable(): WeightedTable<ItemDropSlot>

    companion object {
        fun genderDropTable(maleTable: WeightedTable<ItemDropSlot>, femaleTable: WeightedTable<ItemDropSlot>): DropTableProvider {
            return DropTableProvider {
                when (ActorStateManager.player().getBaseLook().race?.genderType) {
                    GenderType.Male -> maleTable
                    GenderType.Female -> femaleTable
                    GenderType.None -> maleTable
                    null -> maleTable
                }
            }
        }
    }

}

data class ChestSlot(
    val defeatRequirement: Int,
    val dropSlotCondition: () -> Boolean = { true },
    val dropTableProvider: DropTableProvider,
)

data class SpawnerChestDefinition(
    val position: Vector3f,
    val rotation: Float = 0f,
    val dropSlots: List<ChestSlot>,
    val treasureChestLook: TreasureChestLook,
)

class SpawnerChest(val chestDefinition: SpawnerChestDefinition, val monsterSpawnerInstance: MonsterSpawnerInstance) {

    val itemRequirements = HashMap<InternalItemId, Int>()

    private val promise: ActorPromise = spawnChest()
    private var timeSinceOpened: Float? = null

    fun update(elapsedFrames: Float) {
        timeSinceOpened = timeSinceOpened?.let { it + elapsedFrames }
        promise.ifReady {
            if (it.getInventory().inventoryItems.isEmpty()) { onEmpty(it) }
        }
    }

    fun clear() {
        promise.onReady { GameEngine.submitDeleteActor(it.id) }
    }

    private fun spawnChest(): ActorPromise {
        val inventory = Inventory()

        for (chestSlot in chestDefinition.dropSlots) {
            if (!chestSlot.dropSlotCondition()) { continue }
            val dropTable = chestSlot.dropTableProvider.getDropTable()

            val chestItem = tryRollUnique(inventory, dropTable)
            if (chestItem.itemId == null) { continue }

            val item = GameV0.generateItem(ItemDropDefinition(
                itemId = chestItem.itemId,
                quantity = chestItem.quantity,
                temporary = chestItem.temporary,
                rankSettings = chestItem.rankSettings,
            ))

            itemRequirements[item.internalId] = chestSlot.defeatRequirement
            inventory.addItem(item, stack = false)
        }

        return GameEngine.submitCreateActorState(
            InitialActorState(
                name = "Treasure Chest",
                type = ActorType.StaticNpc,
                position = chestDefinition.position,
                rotation = chestDefinition.rotation,
                modelLook = chestDefinition.treasureChestLook.look,
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
                maxTargetDistance = 10f,
                components = listOf(inventory),
            )
        ).onReady {
            GameV0.interactionManager.registerInteraction(it.id, SpawnChestInteraction(this))
        }
    }

    private fun tryRollUnique(inventory: Inventory, dropTable: WeightedTable<ItemDropSlot>): ItemDropSlot {
        if (dropTable.entries.any { it.first.itemId == null }) { return dropTable.getRandom() }

        var drop = dropTable.getRandom()

        for (i in 0 until 10) {
            if (inventory.inventoryItems.none { it.id == drop.itemId }) { return drop }
            drop = dropTable.getRandom()
        }

        return drop
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

class SpawnChestInteraction(val chest: SpawnerChest) : NpcInteraction {

    private val monsterSpawnerInstance = chest.monsterSpawnerInstance

    override fun onInteraction(npcId: ActorId) {
        val inventory = inventory(npcId)
        if (inventory == null || inventory.inventoryItems.isEmpty()) { return }

        if (inventory.inventoryItems.any { hasMetRequirements(it) }) { onOpened(npcId) }

        val prompt = "Defeated: [${monsterSpawnerInstance.getTotalDefeated()}]"

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

        if (inventory.inventoryItems.count { hasMetRequirements(it) } > 1) {
            options += QueryMenuOption("Take all.", -1)
        }

        inventory.inventoryItems.forEachIndexed { index, item ->
            val info = InventoryItems[item.id]
            val requirement = chest.itemRequirements[item.internalId] ?: 0

            val requirementText = if (hasMetRequirements(item)) {
                "${ShiftJis.colorItem}[$requirement]${ShiftJis.colorClear}"
            } else {
                "[$requirement]"
            }

            val qualityColor = AugmentHelper.getQualityColorDisplay(item)

            val quantityText = if (item.quantity > 1) {
                "x${UiElementHelper.formatNumber(item.quantity)}"
            } else {
                ""
            }

            options += QueryMenuOption("$requirementText ${qualityColor}${info.name} ${quantityText}${ShiftJis.colorClear}", index)
        }

        return options
    }

    private fun handleSelection(npcId: ActorId, queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null) { return QueryMenuResponse.pop }
        val inventory = inventory(npcId) ?: return QueryMenuResponse.pop

        if (queryMenuOption.value == -1) {
            inventory.inventoryItems.filter { hasMetRequirements(it) }
                .forEach { claimItem(npcId, it) }
        } else {
            val item = inventory.inventoryItems.getOrNull(queryMenuOption.value) ?: return QueryMenuResponse.pop

            if (!hasMetRequirements(item)) {
                return QueryMenuResponse.noop(SystemSound.Invalid)
            }

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

    private fun hasMetRequirements(item: InventoryItem): Boolean {
        val requirement = chest.itemRequirements[item.internalId] ?: 0
        return monsterSpawnerInstance.getTotalDefeated() >= requirement
    }

}