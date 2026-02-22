package xim.poc.game.configuration.v0.escha

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.configuration.NoActionBehaviorId
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.*
import xim.poc.game.configuration.v0.abyssea.AugmentRerollUiState
import xim.poc.game.configuration.v0.constants.*
import xim.poc.game.configuration.v0.escha.EschaVorsealBonusApplier.getClearedDifficulties
import xim.poc.game.configuration.v0.interactions.*
import xim.poc.game.event.InitialActorState
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.ShiftJis
import xim.resource.KeyItemTable
import xim.util.PI_f

private val r10 = ItemRankSettings(rankDistribution = ItemRankFixed(10))
private val r15 = ItemRankSettings(rankDistribution = ItemRankFixed(15))
private val r20 = ItemRankSettings(rankDistribution = ItemRankFixed(20))
private val r25 = ItemRankSettings(rankDistribution = ItemRankFixed(25))
private val r30 = ItemRankSettings(rankDistribution = ItemRankFixed(30))

private val armorUnlocks = mapOf(
    armorRawhideMask_26794 to listOf(mobGestalt_288_115, mobLustfulLydia_288_155),
    armorRawhideVest_26950 to listOf(mobRevetaur_288_120, mobAngrboda_288_140),
    armorRawhideGloves_27100 to listOf(mobWepwawet_288_100, mobVyala_288_135),
    armorRawhideTrousers_27285 to listOf(mobAglaophotis_288_105, mobTangataManu_288_125),
    armorRawhideBoots_27460 to listOf(mobVidala_288_110, mobCunnast_288_145),
    armorDispersersCape_27606 to listOf(mobGulltop_288_130),
    armorLuciditySash_28416 to listOf(mobFerrodon_288_150),

    armorPsyclothTiara_26796 to listOf(mobIonos_288_200),
    armorPsyclothVest_26952 to listOf(mobNosoi_288_205),
    armorPsyclothManillas_27102 to listOf(mobUmdhlebi_288_210),
    armorPsyclothLappas_27287 to listOf(mobSensualSandy_288_215),
    armorPsyclothBoots_27462 to listOf(mobBrittlis_288_220),
    armorThaumCape_27607 to listOf(mobKamohoalii_288_225),
    armorSinewBelt_28417 to listOf(mobKamohoalii_288_225),
)

private val validRerollItems = armorUnlocks.keys + setOf(
    armorKubiraMeikogai_26959,
    armorAnnointKalasiris_26960,
    armorMakoraMeikogai_26961,
)

class ZiTahRegisterNpc: FloorEntity {

    private val promise = spawn()

    override fun update(elapsedFrames: Float) { }

    override fun cleanup() {
        promise.cleanup()
    }

    private fun spawn(): ActorPromise {
        return GameEngine.submitCreateActorState(
            InitialActorState(
                name = "Register",
                type = ActorType.StaticNpc,
                position = Vector3f(x=-350.51f,y=0.42f,z=-170.89f),
                modelLook = ModelLook.npc(0x8F2),
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
                maxTargetDistance = 5f,
            )
        ).onReady {
            GameV0.interactionManager.registerInteraction(it.id, ZiTahRegisterInteraction)
            it.faceToward(ActorStateManager.player())
            it.rotation += PI_f
        }
    }

}

private object ZiTahRegisterInteraction: NpcInteraction {

    private var hasDescribedUnlocks = false

    override fun onInteraction(npcId: ActorId) {
        val queryOptions = listOf(
            QueryMenuOption("Nothing.", -1),
            QueryMenuOption("Shop.", 0),
            QueryMenuOption("Reroll Augments.", 1),
            QueryMenuOption("View Vorseal Bonuses.", 2),
            QueryMenuOption("List Key Items.", 3),
            QueryMenuOption("Manage Blue Magic.", 4),
        )

        UiStateHelper.openQueryMode(prompt = "What will you do?", options = queryOptions, callback = { handleResponse(npcId, it) })

    }

    private fun handleResponse(npcId: ActorId, queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value < 0) { return QueryMenuResponse.pop }

        when (queryMenuOption.value) {
            0 -> BarterInteractionUiState(npcId, generateBarterConfiguration()).push()
            1 -> AugmentRerollUiState(itemGravewoodLog_9076 to 2){ validRerollItems.contains(it?.id) }.push()
            2 -> EschaRecordsUi(EschaConfigurations[EschaZiTah]).push()
            3 -> listKeyItems()
            4 -> BlueMagicInteraction.onInteraction(npcId)
        }

        return QueryMenuResponse.noop(sound = null)
    }

    private fun generateBarterConfiguration(): BarterConfiguration {
        val barterItems = ArrayList<BarterItem>()

        barterItems += BarterItem(ItemDropDefinition(itemId = 9084), requiredItems = listOf(itemGravewoodLog_9076 to 1))
        barterItems += BarterItem(ItemDropDefinition(itemId = 9084, quantity = 3), requiredItems = listOf(itemAshweed_9078 to 1))

        barterItems += BarterItem(ItemDropDefinition(itemId = itemCoalitionPotion_5986.id), requiredItems = listOf(itemGravewoodLog_9076 to 3))
        barterItems += BarterItem(ItemDropDefinition(itemId = itemCoalitionEther_5987.id), requiredItems = listOf(itemGravewoodLog_9076 to 3))
        barterItems += BarterItem(ItemDropDefinition(itemId = itemPanacea_4149.id), requiredItems = listOf(itemGravewoodLog_9076 to 3))

        for ((armorId, monsters) in armorUnlocks) {
            val clearedDifficulties = monsters.flatMap { getClearedDifficulties(it) }.toSet()
            val definition = ItemDefinitions.definitionsById[armorId] ?: continue

            val cost = when (definition.internalLevel) {
                28 -> listOf(itemGravewoodLog_9076 to 10)
                30 -> listOf(itemAshweed_9078 to 10)
                else -> continue
            }

            val rank = if (clearedDifficulties.contains(EschaDifficulty.S5)) {
                r30
            } else if (clearedDifficulties.contains(EschaDifficulty.S4)) {
                r25
            } else if (clearedDifficulties.contains(EschaDifficulty.S3)) {
                r20
            } else if (clearedDifficulties.contains(EschaDifficulty.S2)) {
                r15
            } else if (clearedDifficulties.contains(EschaDifficulty.S1)) {
                r10
            } else {
                continue
            }

            barterItems += BarterItem(ItemDropDefinition(itemId = armorId, rankSettings = rank), requiredItems = cost)
        }

        if (!hasDescribedUnlocks) {
            ChatLog("Defeat Notorious Monsters to unlock higher quality gear.", ChatLogColor.Info)
            hasDescribedUnlocks = true
        }

        return BarterConfiguration(barterItems)
    }

    private fun listKeyItems() {
        val state = GameV0SaveStateHelper.getState().keyItems
        val options = ArrayList<QueryMenuOption>()

        for (keyItemIndex in EschaKeyItems.zitahKeyItems) {
            val quantity = state[keyItemIndex] ?: continue
            val text = KeyItemTable.getName(index = keyItemIndex, quantity = quantity)
            options += QueryMenuOption(text = "${quantity}x ${ShiftJis.colorKey}$text${ShiftJis.colorClear}", value = 0)
        }

        if (options.isEmpty()) { return ChatLog("You don't have any key items!", ChatLogColor.Info) }

        UiStateHelper.openQueryMode(prompt = "Key items:", options = options) { QueryMenuResponse.pop }
    }

}
