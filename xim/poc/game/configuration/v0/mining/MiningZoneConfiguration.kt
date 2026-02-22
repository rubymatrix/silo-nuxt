package xim.poc.game.configuration.v0.mining

import xim.math.Vector3f
import xim.poc.ModelLook
import xim.poc.game.GatheringType
import xim.poc.game.configuration.MonsterProviderFactory
import xim.poc.game.configuration.SpawnArea
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.constants.*
import xim.poc.game.configuration.v0.tower.Boundary
import xim.poc.game.configuration.v0.tower.EncompassingSphere
import xim.poc.game.configuration.v0.tower.PushWall
import xim.poc.game.configuration.v0.tower.TowerConfiguration.countProvider
import xim.poc.game.configuration.v0.zones.PathingSettings
import xim.poc.tools.ZoneConfig

class MiningZoneConfiguration(
    val requiredMiningLevel: Int,
    val gatheringConfiguration: GatheringConfiguration,
    val monsterProviderFactory: MonsterProviderFactory,
    val maxActive: Int = 8,
    val maxSpawnCount: Int? = 8,
    val startingPosition: ZoneConfig,
    val entrancePosition: Vector3f,
    val entranceLook: ModelLook,
    val helpBookPosition: Vector3f = entrancePosition,
    val spawnerArea: SpawnArea,
    val pathingSettings: PathingSettings,
    val boundaries: List<Boundary> = emptyList(),
)

object MiningZoneConfigurations {

    val zones: List<MiningZoneConfiguration>

    init {
        val colorRocks = (itemRedRock_769 .. itemWhiteRock_776).map { GatheringNodeItem(itemId = it) }
        val elementalOre = (itemFireOre_1255 .. itemDarkOre_1262).map { GatheringNodeItem(itemId = it) }

        zones = listOf(
            MiningZoneConfiguration(
                requiredMiningLevel = 1,
                gatheringConfiguration = GatheringConfiguration(
                    type = GatheringType.Mining,
                    positions = listOf(
                        Vector3f(x=142.16f,y=7.89f,z=-211.77f),
                        Vector3f(x=132.10f,y=6.84f,z=-193.35f),
                        Vector3f(x=147.75f,y=6.92f,z=-205.07f),
                        Vector3f(x=130.42f,y=6.37f,z=-187.71f),
                    ),
                    items = listOf(
                        GatheringNodeItem(itemId = itemFireCrystal_4096.id),
                        GatheringNodeItem(itemId = itemCopperOre_640),
                        GatheringNodeItem(itemId = itemZincOre_642),
                        GatheringNodeItem(itemId = itemSilverOre_736),
                    ) + colorRocks
                ),
                monsterProviderFactory = countProvider(
                    mobBurrowerWorm_M1 to 3,
                    mobDingBats_M2 to 2,
                    mobDiggerLeech_M3 to 3,
                ),
                startingPosition = ZoneConfig(zoneId = 172, startPosition = Vector3f(x=131.46f,y=8.42f,z=-179.52f)),
                entrancePosition = Vector3f(x=129.46f,y=8.20f,z=-177.51f),
                entranceLook = ModelLook.npc(0x975),
                helpBookPosition = Vector3f(x=130.46f,y=8.42f,z=-179.52f),
                spawnerArea = SpawnArea(Vector3f(x=141.55f,y=8.28f,z=-195.35f), Vector3f(x=10.00f,y=0.00f,z=14.00f)),
                pathingSettings = PathingSettings(Vector3f(x=140f,y=7.29f,z=-214f), radius = 50f),
                boundaries = listOf(
                    EncompassingSphere(center = Vector3f(x=141.55f,y=8.28f,z=-195.35f), radius = 30f)
                ),
            ),

            MiningZoneConfiguration(
                requiredMiningLevel = 8,
                gatheringConfiguration = GatheringConfiguration(
                    type = GatheringType.Mining,
                    positions = listOf(
                        Vector3f(x=-256.04f,y=37.80f,z=391.48f),
                        Vector3f(x=-230.15f,y=38.28f,z=416.60f),
                        Vector3f(x=-231.56f,y=37.58f,z=397.35f),
                        Vector3f(x=-240.73f,y=36.96f,z=391.29f),
                    ),
                    items = listOf(
                        GatheringNodeItem(itemId = itemFireCrystal_4096.id),
                        GatheringNodeItem(itemId = itemGranite_1465),
                        GatheringNodeItem(itemId = itemMythrilOre_644),
                    ) + colorRocks
                ),
                monsterProviderFactory = countProvider(
                    mobWalkingSapling_M to 3,
                    mobManeatingHornet_M to 2,
                    mobHugeSpider_M to 2,
                    mobLesserWivre_M to 1,
                ),
                startingPosition = ZoneConfig(zoneId = 88, startPosition = Vector3f(x=-286.66f,y=40.27f,z=398.76f)),
                entrancePosition = Vector3f(x=-288.91f,y=40.23f,z=398.03f),
                entranceLook = ModelLook.npc(0x975),
                helpBookPosition = Vector3f(x=-285.66f,y=40.27f,z=398.76f),
                spawnerArea = SpawnArea(Vector3f(x=-258.96f,y=40.18f,z=407.43f), Vector3f(x=14.00f,y=0.00f,z=10.00f)),
                pathingSettings = PathingSettings(Vector3f(x=-258.96f,y=40.18f,z=407.43f), radius = 40f),
                boundaries = listOf(
                    EncompassingSphere(center = Vector3f(x=-258.96f,y=40.18f,z=407.43f), radius = 40f)
                ),
            ),

            MiningZoneConfiguration(
                requiredMiningLevel = 15,
                gatheringConfiguration = GatheringConfiguration(
                    type = GatheringType.Mining,
                    positions = listOf(
                        Vector3f(x=33.71f,y=-21.45f,z=146.18f),
                        Vector3f(x=33.71f,y=-21.45f,z=146.18f),
                        Vector3f(x=30.18f,y=-21.34f,z=124.49f),
                        Vector3f(x=30.18f,y=-21.34f,z=124.49f),
                        Vector3f(x=12.75f,y=-21.61f,z=149.09f),
                        Vector3f(x=12.75f,y=-21.61f,z=149.09f),
                        Vector3f(x=-15.29f,y=-20.90f,z=124.62f),
                        Vector3f(x=58.50f,y=-20.93f,z=147.51f),
                    ),
                    items = listOf(
                        GatheringNodeItem(itemId = itemFireCluster_4104.id),
                        GatheringNodeItem(itemId = itemGoldOre_737),
                    ) + elementalOre
                ),
                monsterProviderFactory = countProvider(
                    mobOreEater_M to 2,
                    mobMautheDoog_M to 2,
                    mobWightWar_M to 2,
                    mobWightBlm_M to 1,
                    mobBanshee_M to 1,
                ),
                startingPosition = ZoneConfig(zoneId = 196, startPosition = Vector3f(x=15.96f,y=-40.10f,z=115.24f)),
                entrancePosition = Vector3f(x=15.96f,y=-40.10f,z=110.86f),
                entranceLook = ModelLook.npc(0x975),
                helpBookPosition = Vector3f(x=8.21f,y=-20.18f,z=129.34f),
                spawnerArea = SpawnArea(Vector3f(x=20.00f,y=-20.00f,z=145.00f), Vector3f(x=30.00f,y=0.00f,z=12.00f)),
                pathingSettings = PathingSettings(Vector3f(x=18.66f,y=-40.1f,z=115.34f), radius = 45f, radialCenter = Vector3f(x=21.36f,y=-19.84f,z=138.53f)),
                boundaries = listOf(
                    PushWall(center = Vector3f(x=20f,y=-40f,z=100f), direction = Vector3f.Z)
                ),
            ),

            MiningZoneConfiguration(
                requiredMiningLevel = 22,
                gatheringConfiguration = GatheringConfiguration(
                    type = GatheringType.Mining,
                    positions = listOf(
                        Vector3f(x=17.48f, y=6.85f, z=65.65f),
                        Vector3f(x=21.38f, y=6.85f, z=57.761f),
                        Vector3f(x=25.37f, y=6.85f, z=101.83f),
                        Vector3f(x=25.53f, y=6.85f, z=65.08f),
                        Vector3f(x=19.73f, y=6.85f, z=94.01f),
                        Vector3f(x=13.94f, y=6.85f, z=100.69f),
                    ),
                    items = listOf(
                        GatheringNodeItem(itemId = itemFireCluster_4104.id),
                        GatheringNodeItem(itemId = itemPlatinumOre_738),
                        GatheringNodeItem(itemId = itemAluminumOre_678),
                    ) + elementalOre
                ),
                monsterProviderFactory = countProvider(
                    mobPurgatoryBat_M to 2,
                    mobMoblinAidman_M to 1,
                    mobMoblinRoadman_M to 1,
                    mobMoblinEngineman_M to 1,
                    mobMoblinJunkman_M to 1,
                    mobMoblinHangman_M to 1,
                    mobBugbearDeathsman_M to 1,
                ),
                startingPosition = ZoneConfig(zoneId = 11, startPosition = Vector3f(x=25.68f,y=8.12f,z=136.04f)),
                entrancePosition = Vector3f(x=28.08f,y=8.05f,z=138.52f),
                entranceLook = ModelLook.npc(0x975),
                helpBookPosition = Vector3f(x=27.85f,y=8.01f,z=136.20f),
                spawnerArea = SpawnArea(Vector3f(x=0f,y=8f,z=81f), Vector3f(x=30.00f,y=0.00f,z=30.00f)),
                pathingSettings = PathingSettings(Vector3f(x=-6.00f,y=8.00f,z=98.00f), radius = 100f, xMin = -58f, zMin = 20f, xMax = 36f),
                boundaries = listOf(
                    PushWall(center = Vector3f(x=-58f,y=8f,z=100f), direction = Vector3f.X),
                    PushWall(center = Vector3f(x=20f,y=8f,z=22f), direction = Vector3f.Z),
                    PushWall(center = Vector3f(x=36f,y=8f,z=140f), direction = Vector3f.NegX),
                ),
            ),
        )
    }

}