package xim.poc.game.configuration.v0.tower

import xim.math.Vector3f
import xim.poc.game.ActorStateManager
import xim.poc.game.configuration.*
import xim.poc.game.configuration.WeightedTable.Companion.uniform
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.*
import xim.poc.game.configuration.v0.ItemAugmentDefinitions.maxPossibleRankLevel
import xim.poc.game.configuration.v0.constants.*
import xim.poc.game.configuration.v0.interactions.ItemId
import xim.poc.game.configuration.v0.tower.DropTables.makeItemRankSettings
import xim.poc.game.configuration.v0.tower.DropTables.singleChestDrop
import xim.poc.game.configuration.v0.tower.DropTables.standardChestDrop
import xim.poc.game.configuration.v0.zones.BattleLocation
import xim.poc.game.configuration.v0.zones.BattleLocations
import xim.poc.ui.ShiftJis
import xim.resource.EquipSlot
import xim.resource.InventoryItems
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

class FloorObstacleConfiguration(
    val maxActive: Int,
    val obstacleProviderFactory: MonsterProviderFactory,
)

fun interface FloorEntityProvider {
    fun invoke(floorDefinition: FloorDefinition): FloorEntity
}

class FloorConfiguration(
    val floorNumber: Int,
    val nextFloor: Int? = floorNumber + 1,
    val floorDisplayName: String = towerFloorName(floorNumber),
    val bonusText: String? = null,
    val monsterProviderFactory: MonsterProviderFactory,
    val obstacleConfiguration: FloorObstacleConfiguration? = null,
    val chestTable: List<ChestSlot>,
    val maxActive: Int = 8,
    val maxSpawnCount: Int = 8,
    val defeatRequirement: Int = maxSpawnCount,
    val battleLocation: BattleLocation,
    val blueMagicReward: List<SpellSkillId> = emptyList(),
    val allies: List<FloorAllyDefinition> = emptyList(),
    val entityProvider: List<FloorEntityProvider> = emptyList(),
    val firstClearNotification: (() -> String)? = null,
)

private fun towerFloorName(floorNumber: Int): String {
    return if (floorNumber <= 30) {
        "1 - $floorNumber"
    } else if (floorNumber <= 50) {
        "2 - ${floorNumber - 30}"
    } else {
        "$floorNumber"
    }
}

object TowerConfiguration {

    private val configurations = HashMap<Int, FloorConfiguration>()

    init {
        configurations[1] = FloorConfiguration(
            floorNumber = 1,
            floorDisplayName = "Tavnazia - 1",
            monsterProviderFactory = countProvider(
                mobWildRabbit_2 to 4,
                mobMinerBee_3 to 4,
            ),
            battleLocation = BattleLocations[2],
            blueMagicReward = emptyList(),
            chestTable = standardChestDrop(armorLevel = 1, floor = 1),
            entityProvider = listOf(
                FloorEntities.starterGearChest(position = Vector3f(x=52f,y=-7.47f,z=32f)),
            ),
            firstClearNotification = { "The ${ShiftJis.leftBracket}Shopkeep${ShiftJis.rightBracket} arrived at camp!" },
        )

        configurations[2] = FloorConfiguration(
            floorNumber = 2,
            floorDisplayName = "Tavnazia - 2",
            monsterProviderFactory = countProvider(
                mobMinerBee_3 to 3,
                mobSeaboardVulture_4 to 2,
                mobBigclaw_6 to 3,
            ),
            battleLocation = BattleLocations[10],
            blueMagicReward = listOf(spellMetallicBody_517),
            chestTable = standardChestDrop(armorLevel = 1, floor = 2),
        )

        configurations[3] = FloorConfiguration(
            floorNumber = 3,
            floorDisplayName = "Tavnazia - 3",
            monsterProviderFactory = countProvider(
                mobSeaboardVulture_4 to 2,
                mobBigclaw_6 to 3,
                mobMakara_7 to 3,
            ),
            battleLocation = BattleLocations[1],
            blueMagicReward = listOf(spellScrewdriver_519),
            chestTable = standardChestDrop(armorLevel = 1, floor = 3, extraSlots = listOf(
                ChestSlot(1) { uniform(ItemDropSlot(weaponChakram_17284, rankSettings = makeItemRankSettings(meanRank = 8))) }
            )),
        )

        configurations[4] = FloorConfiguration(
            floorNumber = 4,
            floorDisplayName = "Tavnazia - 4",
            monsterProviderFactory = countProvider(
                mobSeaboardVulture_4 to 3,
                mobBigclaw_6 to 3,
                mobAtomicCluster_5 to 2,
            ),
            battleLocation = BattleLocations[0],
            blueMagicReward = listOf(spellRefueling_530),
            chestTable = standardChestDrop(armorLevel = 1, floor = 4),
        )

        configurations[5] = FloorConfiguration(
            floorNumber = 5,
            floorDisplayName = "Tavnazia - 5",
            monsterProviderFactory = countProvider(
                mobCherryTree_8 to 1
            ),
            battleLocation = BattleLocations[4],
            blueMagicReward = listOf(spellPineconeBomb_596),
            maxActive = 1,
            maxSpawnCount = 1,
            chestTable = listOf(
                ChestSlot(1) { uniform(ItemDropSlot(itemId = 8933, quantity = 3)) },
                ChestSlot(1) { uniform(ItemDropSlot(itemId = 8942, quantity = 3)) },
                ChestSlot(1) { uniform(ItemDropSlot(itemId = 8951, quantity = 3)) },
                ChestSlot(1) { uniform(ItemDropSlot(itemId = 8960, quantity = 3)) },
            ),
            bonusText = "Weapon Melding",
            firstClearNotification = { "The ${ShiftJis.leftBracket}Meld Moogle${ShiftJis.rightBracket} arrived at camp!" },
        )

        configurations[6] = FloorConfiguration(
            floorNumber = 6,
            floorDisplayName = "Ceizak - 1",
            monsterProviderFactory = countProvider(
                mobLoathsomeLeech_16 to 3,
                mobEchoBats to 3,
                mobFerociousFunguar_12 to 2,
            ),
            battleLocation = BattleLocations[6],
            blueMagicReward = listOf(spellMPDrainkiss_521),
            chestTable = standardChestDrop(armorLevel = 3, floor = 6),
        )

        configurations[7] = FloorConfiguration(
            floorNumber = 7,
            floorDisplayName = "Ceizak - 2",
            monsterProviderFactory = countProvider(
                mobBlanchedMandy_17 to 3,
                mobLoathsomeLeech_16 to 3,
                mobLuckybug to 2,
            ),
            battleLocation = BattleLocations[5],
            blueMagicReward = listOf(spellSuddenLunge_692),
            chestTable = standardChestDrop(armorLevel = 3, floor = 7),
        )

        configurations[8] = FloorConfiguration(
            floorNumber = 8,
            floorDisplayName = "Ceizak - 3",
            maxActive = 1,
            maxSpawnCount = 1,
            battleLocation = BattleLocations[8],
            monsterProviderFactory = countProvider(
                mobSupernalChapuli to 1,
            ),
            blueMagicReward = listOf(spellSanguinarySlash_550),
            chestTable = singleChestDrop(required = 1, armorLevel = 3, floor = 8),
        )

        configurations[9] = FloorConfiguration(
            floorNumber = 9,
            floorDisplayName = "Ceizak - 4",
            battleLocation = BattleLocations[9],
            monsterProviderFactory = countProvider(
                mobGadfly_14 to 2,
                mobLuckybug to 2,
                mobBelaboringWasp_18 to 4,
            ),
            blueMagicReward = listOf(spellCursedSphere_544),
            chestTable = standardChestDrop(armorLevel = 3, floor = 9),
            bonusText = "Weapon Upgrade",
            firstClearNotification = { "The ${ShiftJis.leftBracket}Upgrade Moogle${ShiftJis.rightBracket} arrived at camp!" },
        )

        configurations[10] = FloorConfiguration(
            floorNumber = 10,
            floorDisplayName = "Ceizak - 5",
            monsterProviderFactory = countProvider(
                mobColkhab_10 to 1
            ),
            battleLocation = BattleLocations[3],
            blueMagicReward = listOf(spellDroningWhirlwind_744),
            maxActive = 1,
            maxSpawnCount = 1,
            chestTable = emptyList(),
            bonusText = "Dual Wield",
            firstClearNotification = { "${ActorStateManager.player().name} gained ${ShiftJis.leftBracket}Dual Wield${ShiftJis.rightBracket}!" }
        )

        configurations[11] = FloorConfiguration(
            floorNumber = 11,
            floorDisplayName = "Bibiki - 1",
            monsterProviderFactory = countProvider(
                mobEft_20 to 5,
                mobJagil_21 to 3,
            ),
            battleLocation = BattleLocations[11],
            blueMagicReward = listOf(spellGeistWall_605),
            chestTable = standardChestDrop(armorLevel = 5, floor = 11),
        )

        configurations[12] = FloorConfiguration(
            floorNumber = 12,
            floorDisplayName = "Bibiki - 2",
            maxActive = 1,
            maxSpawnCount = 1,
            monsterProviderFactory = countProvider(
                mobKraken_19 to 1
            ),
            battleLocation = BattleLocations[12],
            blueMagicReward = listOf(spellRegeneration_664),
            chestTable = singleChestDrop(armorLevel = 5, floor = 12),
        )

        configurations[13] = FloorConfiguration(
            floorNumber = 13,
            floorDisplayName = "Bibiki - 3",
            monsterProviderFactory = countProvider(
                mobOpoopo_22 to 3,
                mobUragnite_24 to 2,
                mobAlraune_28 to 3,
            ),
            battleLocation = BattleLocations[13],
            blueMagicReward = listOf(spellDreamFlower_678),
            chestTable = standardChestDrop(armorLevel = 5, floor = 13),
        )

        configurations[14] = FloorConfiguration(
            floorNumber = 14,
            floorDisplayName = "Bibiki - 4",
            monsterProviderFactory = countProvider(
                mobOpoopo_22 to 2,
                mobClot_23 to 2,
                mobAlraune_28 to 3
            ),
            battleLocation = BattleLocations[14],
            blueMagicReward = listOf(spellDigest_542),
            chestTable = standardChestDrop(armorLevel = 5, floor = 14),
        )

        configurations[15] = FloorConfiguration(
            floorNumber = 15,
            floorDisplayName = "Bibiki - 5",
            maxActive = 1,
            maxSpawnCount = 1,
            monsterProviderFactory = countProvider(
                mobWarMachine_25 to 1
            ),
            battleLocation = BattleLocations[15],
            blueMagicReward = listOf(spellBlastbomb_618),
            chestTable = emptyList(),
            bonusText = "Mining Access",
            firstClearNotification = { "${ShiftJis.leftBracket}Vrednev${ShiftJis.rightBracket} arrived at camp!" },
        )

        configurations[16] = FloorConfiguration(
            floorNumber = 16,
            floorDisplayName = "Foret - 1",
            monsterProviderFactory = countProvider(
                mobDuskprowlers_33 to 4,
                mobBlightdella_34 to 2,
                mobRipsawJagil_35 to 2,
            ),
            battleLocation = BattleLocations[16],
            blueMagicReward = listOf(spellJetStream_569),
            chestTable = standardChestDrop(armorLevel = 7, floor = 16),
        )

        configurations[17] = FloorConfiguration(
            floorNumber = 17,
            floorDisplayName = "Foret - 2",
            maxActive = 1,
            maxSpawnCount = 1,
            monsterProviderFactory = countProvider(
                mobKnottedRoots_27 to 1,
            ),
            battleLocation = BattleLocations[17],
            blueMagicReward = listOf(spellTemUpheaval_701),
            chestTable = singleChestDrop(armorLevel = 7, floor = 17),
        )

        configurations[18] = FloorConfiguration(
            floorNumber = 18,
            floorDisplayName = "Foret - 3",
            monsterProviderFactory = countProvider(
                mobHoaryCraklaw_30 to 2,
                mobPerfidiousCrab_37 to 3,
                mobTarichuk_36 to 3,
            ),
            obstacleConfiguration = FloorObstacleConfiguration(
                maxActive = 2,
                obstacleProviderFactory = countProvider(mobNumbingBlossom_101 to 1)
            ),
            battleLocation = BattleLocations[18],
            blueMagicReward = listOf(spellRendingDeluge_702),
            chestTable = standardChestDrop(armorLevel = 7, floor = 18),
        )

        configurations[19] = FloorConfiguration(
            floorNumber = 19,
            floorDisplayName = "Foret - 4",
            monsterProviderFactory = countProvider(
                mobCareeningTwitherym_31 to 3,
                mobScummySlug_38 to 3,
                mobBlightdella_34 to 2,
            ),
            obstacleConfiguration = FloorObstacleConfiguration(
                maxActive = 2,
                obstacleProviderFactory = countProvider(mobNumbingBlossom_101 to 1)
            ),
            battleLocation = BattleLocations[19],
            blueMagicReward = listOf(spellCorrosiveOoze_651),
            chestTable = standardChestDrop(armorLevel = 7, floor = 19),
        )

        configurations[20] = FloorConfiguration(
            floorNumber = 20,
            floorDisplayName = "Foret - 5",
            monsterProviderFactory = countProvider(
                mobTchakka_29 to 1
            ),
            battleLocation = BattleLocations[20],
            maxActive = 1,
            maxSpawnCount = 1,
            blueMagicReward = listOf(spellCarcharianVerve_745),
            chestTable = emptyList(),
        )

        configurations[21] = FloorConfiguration(
            floorNumber = 21,
            floorDisplayName = "Uleguerand - 1",
            monsterProviderFactory = countProvider(
                mobPolarHare_39 to 7,
                mobIceElemental_40 to 1,
            ),
            battleLocation = BattleLocations[21],
            blueMagicReward = listOf(spellWildCarrot_578),
            chestTable = standardChestDrop(armorLevel = 10, floor = 21),
        )

        configurations[22] = FloorConfiguration(
            floorNumber = 22,
            floorDisplayName = "Uleguerand - 2",
            monsterProviderFactory = countProvider(
                mobPolarHare_39 to 3,
                mobIceElemental_40 to 1,
                mobNivalRaptor_41 to 2,
                mobTiger_43 to 2,
            ),
            battleLocation = BattleLocations[22],
            blueMagicReward = listOf(spellFrostBreath_608),
            chestTable = standardChestDrop(armorLevel = 10, floor = 22),
        )

        configurations[23] = FloorConfiguration(
            floorNumber = 23,
            floorDisplayName = "Uleguerand - 3",
            maxSpawnCount = 2,
            maxActive = 2,
            monsterProviderFactory = countProvider(
                mobMindertaur to 1,
                mobEldertaur to 1,
            ),
            battleLocation = BattleLocations[23],
            blueMagicReward = listOf(spellMow_552),
            chestTable = singleChestDrop(required = 2, armorLevel = 10, floor = 23),
        )

        configurations[24] = FloorConfiguration(
            floorNumber = 24,
            floorDisplayName = "Uleguerand - 4",
            monsterProviderFactory = countProvider(
                mobIceElemental_40 to 1,
                mobBuffalo_42 to 2,
                mobSnoll_44 to 5,
            ),
            battleLocation = BattleLocations[24],
            blueMagicReward = listOf(spellLowing_588),
            chestTable = standardChestDrop(armorLevel = 10, floor = 24),
        )

        configurations[25] = FloorConfiguration(
            floorNumber = 25,
            floorDisplayName = "Uleguerand - 5",
            monsterProviderFactory = countProvider(
                mobSnollTzar_45 to 1
            ),
            battleLocation = BattleLocations[25],
            maxActive = 1,
            maxSpawnCount = 1,
            blueMagicReward = listOf(spellColdWave_535),
            chestTable = listOf(
                ChestSlot(1) { uniform(ItemDropSlot(itemId = 8934, quantity = 3)) },
                ChestSlot(1) { uniform(ItemDropSlot(itemId = 8943, quantity = 3)) },
                ChestSlot(1) { uniform(ItemDropSlot(itemId = 8952, quantity = 3)) },
                ChestSlot(1) { uniform(ItemDropSlot(itemId = 8961, quantity = 3)) },
            ),
        )

        configurations[26] = FloorConfiguration(
            floorNumber = 26,
            floorDisplayName = "Promyvion - 1",
            monsterProviderFactory = countProvider(
                mobWanderer_46 to 5,
                mobWeeper_47 to 1,
                mobThinker_48 to 1,
                mobSeether_51 to 1,
            ),
            battleLocation = BattleLocations[26],
            blueMagicReward = listOf(spellVanityDive_667),
            chestTable = standardChestDrop(armorLevel = 12, floor = 26),
        )

        configurations[27] = FloorConfiguration(
            floorNumber = 27,
            floorDisplayName = "Promyvion - 2",
            monsterProviderFactory = countProvider(
                mobWanderer_46 to 3,
                mobWeeper_47 to 2,
                mobGorger_49 to 1,
                mobSeether_51 to 2,
            ),
            battleLocation = BattleLocations[27],
            blueMagicReward = listOf(spellOccultation_679),
            chestTable = standardChestDrop(armorLevel = 12, floor = 27),
        )

        configurations[28] = FloorConfiguration(
            floorNumber = 28,
            floorDisplayName = "Promyvion - 3",
            monsterProviderFactory = countProvider(
                mobWanderer_46 to 3,
                mobWeeper_47 to 2,
                mobCraver_50 to 1,
                mobSeether_51 to 2,
            ),
            battleLocation = BattleLocations[28],
            blueMagicReward = listOf(spellMemoryofEarth_553),
            chestTable = standardChestDrop(armorLevel = 12, floor = 28),
        )

        configurations[29] = FloorConfiguration(
            floorNumber = 29,
            floorDisplayName = "Promyvion - 4",
            monsterProviderFactory = countProvider(
                mobWeeper_47 to 3,
                mobThinker_48 to 1,
                mobGorger_49 to 1,
                mobCraver_50 to 1,
                mobSeether_51 to 2,
            ),
            battleLocation = BattleLocations[29],
            blueMagicReward = listOf(spellWindsofPromy_681),
            chestTable = standardChestDrop(armorLevel = 12, floor = 29),
        )

        configurations[30] = FloorConfiguration(
            floorNumber = 30,
            floorDisplayName = "Promyvion - 5",
            nextFloor = null,
            maxActive = 1,
            maxSpawnCount = 1,
            monsterProviderFactory = countProvider(
                mobMemoryReceptacle_53 to 1
            ),
            battleLocation = BattleLocations[30],
            blueMagicReward = listOf(spellRevelation_526),
            allies = listOf(FloorAllyDefinition(allyId = mobSelhteus_52)),
            chestTable = emptyList(),
            bonusText = "Cavernous Maw",
            firstClearNotification = { "The ${ShiftJis.leftBracket}Cavernous Maw${ShiftJis.rightBracket} has appeared at camp!" },
        )

        configurations[31] = FloorConfiguration(
            floorNumber = 31,
            floorDisplayName = "Halvung - 1",
            monsterProviderFactory = countProvider(
                mobWamouracampa_59 to 2,
                mobFriarsLantern_67 to 2,
                mobMagmaEruca_68 to 3,
            ),
            battleLocation = BattleLocations[31],
            blueMagicReward = listOf(spellThermalPulse_675),
            chestTable = standardChestDrop(armorLevel = 18, floor = 31),
        )

        configurations[32] = FloorConfiguration(
            floorNumber = 32,
            floorDisplayName = "Halvung - 2",
            maxActive = 4,
            maxSpawnCount = 4,
            monsterProviderFactory = countProvider(
                mobTrollIronworker_62 to 1,
                mobTrollTargeteer_63 to 1,
                mobTrollSmelter_64 to 1,
                mobGurfurlur to 1,
            ),
            battleLocation = BattleLocations[32],
            blueMagicReward = listOf(spellDiamondhide_632),
            chestTable = singleChestDrop(armorLevel = 18, floor = 32, required = 4, extraSlots = listOf(
                ChestSlot(4) { uniform(ItemDropSlot(weaponTrollbane_18694, rankSettings = makeItemRankSettings(meanRank = 20))) }
            ))
        )

        configurations[33] = FloorConfiguration(
            floorNumber = 33,
            floorDisplayName = "Halvung - 3",
            monsterProviderFactory = countProvider(
                mobApkallu_60 to 3,
                mobEbonyPudding_61 to 1,
                mobAssassinFly_65 to 2,
                mobVolcanicLeech_66 to 2,
            ),
            battleLocation = BattleLocations[33],
            blueMagicReward = listOf(spellAmorphicSpikes_697),
            chestTable = standardChestDrop(armorLevel = 18, floor = 33),
        )

        configurations[34] = FloorConfiguration(
            floorNumber = 34,
            floorDisplayName = "Halvung - 4",
            monsterProviderFactory = countProvider(
                mobWamoura_58 to 2,
                mobWamouracampa_59 to 2,
                mobEbonyPudding_61 to 2,
                mobAssassinFly_65 to 2,
            ),
            battleLocation = BattleLocations[34],
            blueMagicReward = listOf(spellErraticFlutter_710),
            chestTable = standardChestDrop(armorLevel = 18, floor = 34),
        )

        configurations[35] = FloorConfiguration(
            floorNumber = 35,
            floorDisplayName = "Halvung - 5",
            maxActive = 1,
            maxSpawnCount = 1,
            monsterProviderFactory = countProvider(
                mobCerberus_69 to 1
            ),
            battleLocation = BattleLocations[35],
            blueMagicReward = listOf(spellGatesofHades_739),
            chestTable = emptyList(),
        )

        configurations[36] = FloorConfiguration(
            floorNumber = 36,
            floorDisplayName = "Delkfutt - 1",
            monsterProviderFactory = countProvider(
                mobSeedMandragora_71 to 2,
                mobDancingWeapon_72 to 2,
                mobClipper_74 to 2,
                mobGreaterPugil_75 to 2,
            ),
            battleLocation = BattleLocations[36],
            blueMagicReward = listOf(spellSmiteofRage_527),
            chestTable = standardChestDrop(armorLevel = 20, floor = 36),
        )

        configurations[37] = FloorConfiguration(
            floorNumber = 37,
            floorDisplayName = "Delkfutt - 2",
            maxSpawnCount = 5,
            monsterProviderFactory = countProvider(
                mobSeedGoblin_76 to 1,
                mobSeedQuadav_78 to 1,
                mobMagicPot_73 to 2,
                mobGiantGateKeeper_80 to 1,
            ),
            battleLocation = BattleLocations[37],
            blueMagicReward = listOf(spellBatteryCharge_662),
            chestTable = singleChestDrop(required = 5, armorLevel = 20, floor = 37),
        )

        configurations[38] = FloorConfiguration(
            floorNumber = 38,
            floorDisplayName = "Delkfutt - 3",
            maxSpawnCount = 5,
            monsterProviderFactory = countProvider(
                mobSeedGoblin_76 to 1,
                mobSeedYagudo_77 to 1,
                mobGiantLobber_81 to 1,
                mobChaosIdol_82 to 2,
            ),
            battleLocation = BattleLocations[38],
            blueMagicReward = listOf(spellFeatherMaelstrom_600),
            chestTable = singleChestDrop(required = 5, armorLevel = 20, floor = 38),
        )

        configurations[39] = FloorConfiguration(
            floorNumber = 39,
            floorDisplayName = "Delkfutt - 4",
            maxSpawnCount = 5,
            monsterProviderFactory = countProvider(
                mobSeedGoblin_76 to 1,
                mobSeedOrc_79 to 1,
                mobGiantGateKeeper_80 to 1,
                mobGiantLobber_81 to 1,
                mobPorphyrion_83 to 1,
            ),
            battleLocation = BattleLocations[39],
            blueMagicReward = listOf(spellPhantasmalDance_601),
            chestTable = singleChestDrop(required = 5, armorLevel = 20, floor = 39),
        )

        configurations[40] = FloorConfiguration(
            floorNumber = 40,
            floorDisplayName = "Delkfutt - 5",
            maxSpawnCount = 1,
            monsterProviderFactory = countProvider(
                mobSeedCrystal_84 to 1
            ),
            battleLocation = BattleLocations[40],
            blueMagicReward = listOf(spellSeedofJudgement_602),
            chestTable = emptyList(),
            bonusText = "Seed Fragment",
            firstClearNotification = { "A ${ShiftJis.leftBracket}Seed Fragment${ShiftJis.rightBracket} has appeared at camp!" },
        )

        configurations[41] = FloorConfiguration(
            floorNumber = 41,
            floorDisplayName = "Yorcia - 1",
            monsterProviderFactory = countProvider(
                mobFlatusAcuex_93 to 3,
                mobTenebrousObdella_96 to 2,
                mobPallidFunguar_98 to 2,
                mobAsperousMarolith_95 to 1,
            ),
            obstacleConfiguration = FloorObstacleConfiguration(
                maxActive = 2,
                obstacleProviderFactory = countProvider(mobPungentFungus_100 to 1)
            ),
            battleLocation = BattleLocations[41],
            blueMagicReward = listOf(spellSubduction_708),
            chestTable = standardChestDrop(armorLevel = 23, floor = 41),
        )

        configurations[42] = FloorConfiguration(
            floorNumber = 42,
            floorDisplayName = "Yorcia - 2",
            monsterProviderFactory = countProvider(
                mobLividUmbril_94 to 3,
                mobBalasBats_97 to 2,
                mobSubterraneSpider_99 to 2,
                mobAsperousMarolith_95 to 1,
            ),
            obstacleConfiguration = FloorObstacleConfiguration(
                maxActive = 2,
                obstacleProviderFactory = countProvider(mobPungentFungus_100 to 1)
            ),
            battleLocation = BattleLocations[42],
            blueMagicReward = listOf(spellParalyzingTriad_704),
            chestTable = standardChestDrop(armorLevel = 23, floor = 42),
        )

        configurations[43] = FloorConfiguration(
            floorNumber = 43,
            floorDisplayName = "Yorcia - 3",
            monsterProviderFactory = countProvider(
                mobCheekyOpoOpop_88 to 2,
                mobTwitherym_89 to 2,
                mobSnapweed_90 to 2,
                mobCorpseFlower_91 to 2,
            ),
            obstacleConfiguration = FloorObstacleConfiguration(
                maxActive = 2,
                obstacleProviderFactory = countProvider(mobNumbingBlossom_101 to 1)
            ),
            battleLocation = BattleLocations[43],
            blueMagicReward = listOf(spellMagicFruit_593),
            chestTable = standardChestDrop(armorLevel = 23, floor = 43),
        )

        configurations[44] = FloorConfiguration(
            floorNumber = 44,
            floorDisplayName = "Yorcia - 4",
            maxSpawnCount = 1,
            maxActive = 1,
            monsterProviderFactory = countProvider(
                mobHyoscya_86 to 1,
            ),
            battleLocation = BattleLocations[44],
            blueMagicReward = listOf(spellNightStalker_546),
            chestTable = singleChestDrop(armorLevel = 23, floor = 44),
        )

        configurations[45] = FloorConfiguration(
            floorNumber = 45,
            floorDisplayName = "Yorcia - 5",
            maxSpawnCount = 1,
            monsterProviderFactory = countProvider(
                mobYumcax_92 to 1,
            ),
            battleLocation = BattleLocations[45],
            blueMagicReward = listOf(spellUproot_747),
            chestTable = emptyList()
        )

        configurations[46] = FloorConfiguration(
            floorNumber = 46,
            floorDisplayName = "Castle Zvahl - 1",
            monsterProviderFactory = countProvider(
                mobFustyGnole_110 to 1,
                mobCointeach_111 to 3,
                mobDireGargouille_112 to 2,
                mobDemonWarrior_113 to 1,
                mobDemonBefouler_114 to 1,
            ),
            battleLocation = BattleLocations[46],
            blueMagicReward = listOf(spellPleniluneEmbrace_658),
            chestTable = standardChestDrop(armorLevel = 25, floor = 46),
        )

        configurations[47] = FloorConfiguration(
            floorNumber = 47,
            floorDisplayName = "Castle Zvahl - 2",
            maxActive = 6,
            maxSpawnCount = 6,
            monsterProviderFactory = countProvider(
                mobIronQuadav_104 to 1,
                mobVajraQuadav_105 to 1,
                mobYagudoTemplar_106 to 1,
                mobYagudoChanter_107 to 1,
                mobOrcishVeteran_108 to 1,
                mobOrcishProphetess_109 to 1,
            ),
            battleLocation = BattleLocations[47],
            blueMagicReward = listOf(spellDiamondShell_518),
            chestTable = singleChestDrop(required = 6, armorLevel = 25, floor = 47),
        )

        configurations[48] = FloorConfiguration(
            floorNumber = 48,
            floorDisplayName = "Castle Zvahl - 3",
            monsterProviderFactory = countProvider(
                mobVarkolak_115 to 1,
                mobBerserkerDemon_116 to 2,
                mobEclipseDemon_117 to 1,
                mobTitanotaur_118 to 2,
                mobDoomLens_119 to 2,
            ),
            battleLocation = BattleLocations[48],
            blueMagicReward = listOf(spellHellbornYawp_562),
            chestTable = standardChestDrop(armorLevel = 25, floor = 48),
        )

        configurations[49] = FloorConfiguration(
            floorNumber = 49,
            floorDisplayName = "Castle Zvahl - 4",
            maxSpawnCount = 1,
            maxActive = 1,
            monsterProviderFactory = countProvider(
                mobZirnitra_120 to 1,
            ),
            battleLocation = BattleLocations[49],
            blueMagicReward = listOf(spellFeralPeck_516),
            chestTable = singleChestDrop(required = 1, armorLevel = 25, floor = 49),
        )

        configurations[50] = FloorConfiguration(
            floorNumber = 50,
            floorDisplayName = "Castle Zvahl - 5",
            nextFloor = null,
            maxSpawnCount = 1,
            monsterProviderFactory = countProvider(
                mobShadowLord_103 to 1,
            ),
            battleLocation = BattleLocations[50],
            blueMagicReward = listOf(spellViciousKick_691),
            chestTable = emptyList(),
            bonusText = "Confluence",
            firstClearNotification = { "The ${ShiftJis.leftBracket}Confluence${ShiftJis.rightBracket} has appeared at camp!" },
        )

    }

    operator fun get(floor: Int): FloorConfiguration {
        return configurations[floor] ?: configurations[0]!!
    }

    fun getAll(): Map<Int, FloorConfiguration> {
        return configurations
    }

    fun countProvider(vararg monsterCounts: Pair<MonsterId, Int>): MonsterProviderFactory {
        return countProvider(monsterCounts.toList())
    }

    fun countProvider(monsterCounts: List<Pair<MonsterId, Int>>): MonsterProviderFactory {
        return CountMonsterProvider.factory(monsterCounts)
    }

}

object DropTables {

    fun standardChestDrop(armorLevel: Int, floor: Int, floorPart: Int = floor % 5, extraSlots: List<ChestSlot> = emptyList()): List<ChestSlot> {
        val rpItem = ItemDefinitions.reinforcePointItems.last { it.internalLevel <= armorLevel }
        val rpItemLevelDelta = armorLevel - rpItem.internalLevel

        return extraSlots + listOf(
            ChestSlot(1) { uniform(lvlEquipment(itemLevel = armorLevel, meanRank = 0 + floorPart * 2)) },
            ChestSlot(2) { uniform(lvlMedicine(armorLevel)) },
            ChestSlot(3) { uniform(gilDropSlot(floor = floor)) },
            ChestSlot(4) { uniform(ItemDropSlot(itemId = rpItem.id, quantity = 3 * rpItemLevelDelta + floorPart)) },
            ChestSlot(5) { uniform(lvlEquipment(itemLevel = armorLevel, meanRank = 5 + floorPart * 2)) },
            ChestSlot(6) { uniform(lvlAccessory(itemLevel = armorLevel, meanRank = 12 + floorPart * 2)) },
            ChestSlot(7) { uniform(ItemDropSlot(itemId = itemFernStone_9211, quantity = 1)) },
            ChestSlot(8) { uniform(lvlEquipment(itemLevel = armorLevel, meanRank = 12 + floorPart * 2)) },
        )
    }

    fun singleChestDrop(required: Int = 1, armorLevel: Int, floor: Int, floorPart: Int = floor % 5, extraSlots: List<ChestSlot> = emptyList()): List<ChestSlot> {
        val rpItem = ItemDefinitions.reinforcePointItems.last { it.internalLevel <= armorLevel }
        val rpItemLevelDelta = armorLevel - rpItem.internalLevel

        return extraSlots + listOf(
            ChestSlot(required) { uniform(gilDropSlot(floor = floor)) },
            ChestSlot(required) { uniform(ItemDropSlot(rpItem.id, quantity = 3 * rpItemLevelDelta + floorPart)) },
            ChestSlot(required) { uniform(lvlAccessory(itemLevel = armorLevel, meanRank = 12 + floorPart * 2)) },
            ChestSlot(required) { uniform(lvlEquipment(itemLevel = armorLevel, meanRank = 12 + floorPart * 2)) },
            ChestSlot(required) { uniform(lvlEquipment(itemLevel = armorLevel, meanRank = 12 + floorPart * 2)) },
        )
    }

    fun gilDropSlot(floor: Int): ItemDropSlot {
        val reward = if (floor < 30) { floor } else { floor + 5 }
        val amount = 100 * (1.225f).pow(reward / 2f) * Random.nextDouble(1.0, 2.0)
        return ItemDropSlot(itemId = 65535, quantity = amount.roundToInt())
    }

    fun lvlEquipment(itemLevel: Int, meanRank: Int): List<ItemDropSlot> {
        return ItemDefinitions.definitionsById.filterValues { it.internalLevel == itemLevel }
            .filterValues { it.shopBuyable }
            .filterValues { isArmor(it.id) }
            .map { ItemDropSlot(itemId = it.key, rankSettings = makeItemRankSettings(meanRank)) }
    }

    fun lvlAccessory(itemLevel: Int, meanRank: Int): List<ItemDropSlot> {
        return ItemDefinitions.definitionsById.filterValues { it.internalLevel == itemLevel }
            .filterValues { it.shopBuyable }
            .filterValues { isWaistOrBack(it.id) }
            .map { ItemDropSlot(itemId = it.key, rankSettings = makeItemRankSettings(meanRank)) }
    }

    fun lvlMedicine(itemLevel: Int): List<ItemDropSlot> {
        val potion = ItemDefinitions.potions
            .mapNotNull { ItemDefinitions.definitionsById[it] }
            .filter { it.internalLevel <= itemLevel }
            .maxBy { it.internalLevel }

        val ether = ItemDefinitions.ethers
            .mapNotNull { ItemDefinitions.definitionsById[it] }
            .filter { it.internalLevel <= itemLevel }
            .maxBy { it.internalLevel }

        val remedy = ItemDefinitions.remedies
            .mapNotNull { ItemDefinitions.definitionsById[it] }
            .filter { it.internalLevel <= itemLevel }
            .maxByOrNull { it.internalLevel }

        return listOfNotNull(potion.id, ether.id, remedy?.id).map { ItemDropSlot(itemId = it) }
    }

    fun makeItemRankSettings(meanRank: Int): ItemRankSettings {
        return ItemRankSettings(rankDistribution = ItemRankNormalDistribution(meanRank = meanRank, maxRank = maxPossibleRankLevel))
    }

    private fun isArmor(itemId: ItemId): Boolean {
        val equipSlots = InventoryItems[itemId].equipmentItemInfo?.equipSlots ?: return false
        return equipSlots.contains(EquipSlot.Head) ||
                equipSlots.contains(EquipSlot.Body) ||
                equipSlots.contains(EquipSlot.Hands) ||
                equipSlots.contains(EquipSlot.Legs) ||
                equipSlots.contains(EquipSlot.Feet)
    }

    private fun isWaistOrBack(itemId: ItemId): Boolean {
        val equipSlots = InventoryItems[itemId].equipmentItemInfo?.equipSlots ?: return false
        return equipSlots.contains(EquipSlot.Back) || equipSlots.contains(EquipSlot.Waist)
    }

}

private class CountMonsterProvider(val monsterCounts: List<Pair<MonsterId, Int>>) : MonsterProvider {

    companion object {
        fun factory(monsterCounts: List<Pair<MonsterId, Int>>): MonsterProviderFactory {
            return MonsterProviderFactory { CountMonsterProvider(monsterCounts) }
        }
    }

    private val next = ArrayList<MonsterDefinition>()

    init { populate() }

    override fun nextMonster(): MonsterDefinition {
        if (next.isEmpty()) { populate() }
        return next.removeLast()
    }

    private fun populate() {
        for ((monsterId, count) in monsterCounts) { repeat(count) { next += MonsterDefinitions[monsterId] } }
        next.shuffle()
    }

}

private object FloorEntities {

    fun starterGearChest(position: Vector3f): FloorEntityProvider {
        return FloorEntityProvider {
            TreasureChest(TreasureChestDefinition(
                position = position,
                treasureChestLook = TreasureChestLook.Gold,
                itemDefinitions = listOf(
                    ItemDropDefinition(itemId = weaponOnionSword_16534, rankSettings = ItemRankSettings.leveling()),
                    ItemDropDefinition(itemId = 12448, rankSettings = ItemRankSettings.fixed(1)),
                    ItemDropDefinition(itemId = 12576, rankSettings = ItemRankSettings.fixed(1)),
                    ItemDropDefinition(itemId = 12704, rankSettings = ItemRankSettings.fixed(1)),
                    ItemDropDefinition(itemId = 12832, rankSettings = ItemRankSettings.fixed(1)),
                    ItemDropDefinition(itemId = 12960, rankSettings = ItemRankSettings.fixed(1)),
                )
            ))
        }
    }

}