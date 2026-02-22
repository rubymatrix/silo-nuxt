package xim.poc.game.configuration.v0.abyssea

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.configuration.NoActionBehaviorId
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.*
import xim.poc.game.configuration.v0.interactions.*
import xim.poc.game.event.InitialActorState
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.ShiftJis
import xim.resource.KeyItemTable

private val r10 = ItemRankSettings(rankDistribution = ItemRankFixed(10))
private val r15 = ItemRankSettings(rankDistribution = ItemRankFixed(15))
private val r20 = ItemRankSettings(rankDistribution = ItemRankFixed(20))
private val r25 = ItemRankSettings(rankDistribution = ItemRankFixed(25))
private val r30 = ItemRankSettings(rankDistribution = ItemRankFixed(30))

private val armorItems = listOf(
    BarterItem(ItemDropDefinition(itemId = itemAuroreBeret_11504, rankSettings = r10), requiredItems = listOf(itemVoyageStone_3226 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreBeret_11504, rankSettings = r15), requiredItems = listOf(itemVoyageStone_3226 to 2)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreBeret_11504, rankSettings = r20), requiredItems = listOf(itemVoyageStone_3226 to 3)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreBeret_11504, rankSettings = r25), requiredItems = listOf(itemHelmofBriareus_2929 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreBeret_11504, rankSettings = r30), requiredItems = listOf(itemHelmofBriareus_2929 to 2)),

    BarterItem(ItemDropDefinition(itemId = itemAuroreDoublet_13760, rankSettings = r10), requiredItems = listOf(itemVoyageStone_3226 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreDoublet_13760, rankSettings = r15), requiredItems = listOf(itemVoyageStone_3226 to 2)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreDoublet_13760, rankSettings = r20), requiredItems = listOf(itemVoyageStone_3226 to 3)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreDoublet_13760, rankSettings = r25), requiredItems = listOf(itemCarabossesGem_2930 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreDoublet_13760, rankSettings = r30), requiredItems = listOf(itemCarabossesGem_2930 to 2)),

    BarterItem(ItemDropDefinition(itemId = itemAuroreGloves_12746, rankSettings = r10), requiredItems = listOf(itemVoyageStone_3226 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreGloves_12746, rankSettings = r15), requiredItems = listOf(itemVoyageStone_3226 to 2)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreGloves_12746, rankSettings = r20), requiredItems = listOf(itemVoyageStone_3226 to 3)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreGloves_12746, rankSettings = r25), requiredItems = listOf(itemHelmofBriareus_2929 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreGloves_12746, rankSettings = r30), requiredItems = listOf(itemHelmofBriareus_2929 to 2)),

    BarterItem(ItemDropDefinition(itemId = itemAuroreBrais_14257, rankSettings = r10), requiredItems = listOf(itemVoyageCard_3229 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreBrais_14257, rankSettings = r15), requiredItems = listOf(itemVoyageCard_3229 to 2)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreBrais_14257, rankSettings = r20), requiredItems = listOf(itemVoyageCard_3229 to 3)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreBrais_14257, rankSettings = r25), requiredItems = listOf(itemHelmofBriareus_2929 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreBrais_14257, rankSettings = r30), requiredItems = listOf(itemHelmofBriareus_2929 to 2)),

    BarterItem(ItemDropDefinition(itemId = itemAuroreGaiters_11414, rankSettings = r10), requiredItems = listOf(itemVoyageCard_3229 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreGaiters_11414, rankSettings = r15), requiredItems = listOf(itemVoyageCard_3229 to 2)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreGaiters_11414, rankSettings = r20), requiredItems = listOf(itemVoyageCard_3229 to 3)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreGaiters_11414, rankSettings = r25), requiredItems = listOf(itemHelmofBriareus_2929 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemAuroreGaiters_11414, rankSettings = r30), requiredItems = listOf(itemHelmofBriareus_2929 to 2)),

    BarterItem(ItemDropDefinition(itemId = itemSharpeyeMantle_11562, rankSettings = r10), requiredItems = listOf(itemVoyageJewel_3228 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemSharpeyeMantle_11562, rankSettings = r15), requiredItems = listOf(itemVoyageJewel_3228 to 2)),
    BarterItem(ItemDropDefinition(itemId = itemSharpeyeMantle_11562, rankSettings = r20), requiredItems = listOf(itemVoyageJewel_3228 to 3)),
    BarterItem(ItemDropDefinition(itemId = itemSharpeyeMantle_11562, rankSettings = r25), requiredItems = listOf(itemCarabossesGem_2930 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemSharpeyeMantle_11562, rankSettings = r30), requiredItems = listOf(itemCarabossesGem_2930 to 2)),

    BarterItem(ItemDropDefinition(itemId = itemAristoBelt_11745, rankSettings = r10), requiredItems = listOf(itemVoyageJewel_3228 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemAristoBelt_11745, rankSettings = r15), requiredItems = listOf(itemVoyageJewel_3228 to 2)),
    BarterItem(ItemDropDefinition(itemId = itemAristoBelt_11745, rankSettings = r20), requiredItems = listOf(itemVoyageJewel_3228 to 3)),
    BarterItem(ItemDropDefinition(itemId = itemAristoBelt_11745, rankSettings = r25), requiredItems = listOf(itemCarabossesGem_2930 to 1)),
    BarterItem(ItemDropDefinition(itemId = itemAristoBelt_11745, rankSettings = r30), requiredItems = listOf(itemCarabossesGem_2930 to 2)),
)

private val barterConfiguration = BarterConfiguration(armorItems)

class AbysseaBarterNpc: FloorEntity {

    private val promise = spawn()

    override fun update(elapsedFrames: Float) { }

    override fun cleanup() {
        promise.cleanup()
    }

    private fun spawn(): ActorPromise {
        return GameEngine.submitCreateActorState(
            InitialActorState(
                name = "Barter Moogle",
                type = ActorType.StaticNpc,
                position = Vector3f(x=322.00f,y=24.8f,z=-140.00f),
                modelLook = ModelLook.npc(0x973),
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
            )
        ).onReady {
            GameV0.interactionManager.registerInteraction(it.id, AbysseaBarterInteraction)
        }
    }

}

private object AbysseaBarterInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        val queryOptions = listOf(
            QueryMenuOption("Shop.", 0),
            QueryMenuOption("Reroll Augments.", 1),
            QueryMenuOption("List Key Items.", 2),
            QueryMenuOption("Manage Blue Magic.", 3),
        )

        UiStateHelper.openQueryMode(prompt = "What will you do?", options = queryOptions, callback = { handleResponse(npcId, it) })
    }

    private fun handleResponse(npcId: ActorId, queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value < 0) { return QueryMenuResponse.pop }

        val validRerollItems = armorItems.map { it.purchaseItem.itemId }.toSet()

        when (queryMenuOption.value) {
            0 -> BarterInteractionUiState(npcId, barterConfiguration).push()
            1 -> AugmentRerollUiState(itemVoyageCoin_3227 to 1) { validRerollItems.contains(it?.id) }.push()
            2 -> listKeyItems()
            3 -> BlueMagicInteraction.open()
        }

        return QueryMenuResponse.noop(sound = null)
    }

    private fun listKeyItems() {
        val state = GameV0SaveStateHelper.getState().keyItems
        val options = ArrayList<QueryMenuOption>()

        for (keyItemIndex in AbysseaKeyItems.laTheineKeyItems) {
            val quantity = state[keyItemIndex] ?: continue
            val text = KeyItemTable.getName(index = keyItemIndex, quantity = quantity)
            options += QueryMenuOption(text = "${quantity}x ${ShiftJis.colorKey}$text${ShiftJis.colorClear}", value = 0)
        }

        if (options.isEmpty()) { return ChatLog("You don't have any key items!", ChatLogColor.Info) }

        UiStateHelper.openQueryMode(prompt = "Key items:", options = options) { QueryMenuResponse.pop }
    }

}
