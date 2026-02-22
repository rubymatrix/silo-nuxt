package xim.poc.game.configuration.v0.zones

import xim.math.Vector3f
import xim.poc.*
import xim.poc.game.configuration.SpawnArea
import xim.poc.game.configuration.v0.tower.Boundary
import xim.poc.game.configuration.v0.tower.BoundaryConditions.playerDoesNotHaveEnmity
import xim.poc.game.configuration.v0.tower.BoundaryConditions.playerHasEnmity
import xim.poc.game.configuration.v0.tower.EncompassingSphere
import xim.poc.game.configuration.v0.tower.PushWall
import xim.poc.tools.ZoneConfig
import xim.resource.DatId
import xim.resource.EffectRoutineResource
import xim.resource.table.MusicSettings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BattleTimeOfDay(
    val hour: Int,
    val minute: Int,
)

class PathingSettings(
    val root: Vector3f,
    val radius: Float,
    val radialCenter: Vector3f = root,
    val xMin: Float = Float.NEGATIVE_INFINITY,
    val xMax: Float = Float.POSITIVE_INFINITY,
    val zMin: Float = Float.NEGATIVE_INFINITY,
    val zMax: Float = Float.POSITIVE_INFINITY,
)

class BattleLocation(
    val startingPosition: ZoneConfig,
    val treasureChestPosition: Vector3f,
    val entrancePosition: Vector3f,
    val entranceLook: ModelLook,
    val exitPosition: Vector3f,
    val exitLook: ModelLook,
    val spawnerArea: SpawnArea,
    val pathingSettings: PathingSettings,
    val boundaries: List<Boundary> = emptyList(),
    val shipRoute: DatId? = null,
    val musicSettings: MusicSettings? = null,
    val timeOfDay: BattleTimeOfDay? = null,
    val onCleared: (() -> Unit)? = null,
    val onSetup: (() -> Unit)? = null
)

object BattleLocations {

    private val locations = HashMap<Int, BattleLocation>()

    init {
        // Misareaux Coast: Waterfall
        locations[0] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 25, startPosition = Vector3f(x=-36.71f,y=-24.20f,z=683.13f)),
            treasureChestPosition = Vector3f(x=-65.83f,y=-23.41f,z=665.54f),
            entrancePosition = Vector3f(x=-35.25f,y=-24.27f,z=684.56f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-68.74f,y=-23.53f,z=666.95f),
            exitLook = ModelLook.npc(0x9BC),
            spawnerArea = SpawnArea(Vector3f(x=-64.36f,y=-23.22f,z=668.26f), Vector3f(18f, 0f, 15f)),
            pathingSettings = PathingSettings(Vector3f(x=-56.37f,y=-23.34f,z=658.09f), radius = 38f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-56.37f,y=-23.34f,z=658.09f), radius = 38f),
            ))

        // Misareaux Coast: River
        locations[1] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 25, startPosition = Vector3f(x=266.26f,y=-15.54f,z=360.67f)),
            treasureChestPosition = Vector3f(x=246.34f,y=-15.53f,z=392.43f),
            entrancePosition = Vector3f(x=266.26f,y=-15.54f,z=358.67f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=242.92f,y=-15.60f,z=392.43f),
            exitLook = ModelLook.npc(0x9BC),
            spawnerArea = SpawnArea(Vector3f(x=259.36f,y=-15.55f,z=388.50f), Vector3f(8.5f, 0f, 16f)),
            pathingSettings = PathingSettings(Vector3f(x=260.69f,y=-15.50f,z=384.36f), radius = 38f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=260.69f,y=-15.50f,z=384.36f), radius = 38f),
            ))

        // Lufaise Meadows: Lake
        locations[2] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 24, startPosition = Vector3f(x=42.29f,y=-8.06f,z=46.80f)),
            treasureChestPosition = Vector3f(x=66.27f,y=-7.68f,z=9.35f),
            entrancePosition = Vector3f(x=42.35f,y=-8.06f,z=49.07f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=69.24f,y=-7.61f,z=8.20f),
            exitLook = ModelLook.npc(0x9BC),
            spawnerArea = SpawnArea(Vector3f(x=56.36f,y=-7.34f,z=6.30f), Vector3f(10f, 0f, 13f)),
            pathingSettings = PathingSettings(Vector3f(x=54.40f,y=-7.37f,z=27.60f), radius = 38f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=54.40f,y=-7.37f,z=27.60f), radius = 38f),
            ))

        // Ceizak Battlegrounds: Wildskeeper Reive
        locations[3] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 261, startPosition = Vector3f(x=-100.57f,y=0.44f,z=364.31f)),
            treasureChestPosition = Vector3f(x=44.48f,y=-8.31f,z=44.44f),
            entrancePosition = Vector3f(x=-99.76f,y=0.46f,z=361.69f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-98.86f,y=0.57f,z=373.46f),
            exitLook = ModelLook.npc(0x9BC),
            spawnerArea = SpawnArea(Vector3f(x=-135.12f,y=0.57f,z=423.66f), Vector3f(10f, 0f, 10f)),
            musicSettings = MusicSettings(musicId = null, battleSoloMusicId = 62, battlePartyMusicId = 62),
            pathingSettings = PathingSettings(Vector3f(x=-135.12f,y=0.57f,z=423.66f), radius = 100f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-135.12f,y=0.57f,z=423.66f), radius = 50f, condition = playerHasEnmity),
            ))

        // Lufaise Meadows: Cherry Tree
        locations[4] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 24, startPosition = Vector3f(x=-183.20f,y=-15.45f,z=306.01f)),
            entrancePosition = Vector3f(x=-187.54f,y=-15.18f,z=304.81f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-164.53f,y=-15.77f,z=290.58f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-166.68f,y=-15.66f,z=289.04f),
            spawnerArea = SpawnArea(Vector3f(x=-162.95f,y=-16.08f,z=314.59f), Vector3f(x=4.00f,y=0.00f,z=4.00f)),
            musicSettings = MusicSettings(battleSoloMusicId = 102, battlePartyMusicId = 102),
            timeOfDay = BattleTimeOfDay(hour = 19, minute = 0),
            pathingSettings = PathingSettings(Vector3f(x=-162.95f,y=-16.08f,z=314.59f), radius = 30f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-162.95f,y=-16.08f,z=314.59f), radius = 30f)
            ),
        )

        // Ceizak Battlegrounds: Beach
        locations[5] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 261, startPosition = Vector3f(x=273.21f,y=-0.00f,z=-281.52f)),
            treasureChestPosition = Vector3f(x=282.15f,y=0.42f,z=-250.54f),
            entrancePosition = Vector3f(x=270.86f,y=0.00f,z=-283.10f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=283.35f,y=0.33f,z=-248.77f),
            exitLook = ModelLook.npc(0x9BC),
            spawnerArea = SpawnArea(Vector3f(x=298.02f,y=1.67f,z=-263.82f), Vector3f(15f, 0f, 15f)),
            pathingSettings = PathingSettings(Vector3f(x=298.02f,y=1.67f,z=-263.82f), radius = 40f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=298.02f,y=1.67f,z=-263.82f), radius = 40f)
            ))

        // Sih Gates: Lake
        locations[6] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 268, startPosition = Vector3f(x=172.58f,y=0.12f,z=-265.76f)),
            entrancePosition = Vector3f(x=175.00f,y=0.74f,z=-264.79f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=123.68f,y=0.53f,z=-271.14f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=125.44f,y=0.31f,z=-269.09f),
            spawnerArea = SpawnArea(Vector3f(x=153.02f,y=0.17f,z=-272.56f), Vector3f(x=10.00f,y=0.00f,z=10.00f)),
            pathingSettings = PathingSettings(Vector3f(x=153.02f,y=0.17f,z=-272.56f), radius = 40f, xMin = 118f, zMax = -255f),
            boundaries = listOf(
                PushWall(center = Vector3f(x=159.79f,y=1.00f,z=-255.08f), tracking = true, direction = Vector3f.South),
                PushWall(center = Vector3f(x=117.97f,y=0.17f,z=-272.38f), tracking = true, direction = Vector3f.East),
            ))

        // Ceizak Battlegrounds: Inner Area 1
        locations[8] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 261, startPosition = Vector3f(x=40.04f,y=-0.00f,z=120.71f)),
            treasureChestPosition = Vector3f(x=84.28f,y=0.00f,z=80.09f),
            entrancePosition = Vector3f(x=35.63f,y=0.00f,z=125.42f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=83.43f,y=0.00f,z=77.09f),
            exitLook = ModelLook.npc(0x9BC),
            spawnerArea = SpawnArea(Vector3f(x=60.50f,y=0.51f,z=99.13f), Vector3f(8f, 0f, 8f)),
            pathingSettings = PathingSettings(Vector3f(x=60.50f,y=0.51f,z=99.13f), radius = 40f),
            boundaries = listOf(
                PushWall(center = Vector3f(x=33.19f,y=0.24f,z=128.34f), direction = Vector3f.SouthEast)
            ))

        // Ceizak Battlegrounds: Inner Area 2
        locations[9] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 261, startPosition = Vector3f(x=-54.77f,y=0.33f,z=267.55f)),
            treasureChestPosition = Vector3f(x=-67.27f,y=0.55f,z=298.99f),
            entrancePosition = Vector3f(x=-58.78f,y=0.26f,z=266.74f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-69.34f,y=0.50f,z=298.69f),
            exitLook = ModelLook.npc(0x9BC),
            spawnerArea = SpawnArea(Vector3f(x=-41.35f,y=-0.00f,z=282.42f), Vector3f(9.25f, 0f, 9.25f)),
            pathingSettings = PathingSettings(Vector3f(x=-41.35f,y=-0.00f,z=282.42f), radius = 35f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-41.35f,y=-0.00f,z=282.42f), radius = 35f)
            ))

        // Misareaux Coast: Coast
        locations[10] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 25, startPosition = Vector3f(x=714.91f,y=0.69f,z=-483.28f)),
            treasureChestPosition = Vector3f(x=677.19f,y=0.45f,z=-467.39f),
            entrancePosition = Vector3f(x=721.75f,y=0.84f,z=-483.58f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=673.17f,y=0.47f,z=-468.95f),
            exitLook = ModelLook.npc(0x9BC),
            spawnerArea = SpawnArea(Vector3f(x=689.19f,y=0.48f,z=-468.86f), Vector3f(14f, 0f, 12f)),
            pathingSettings = PathingSettings(Vector3f(x=689.19f,y=0.48f,z=-468.86f), radius = 40f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=689.19f,y=0.48f,z=-468.86f), radius = 40f),
            ))

        // Bibiki Bay: Town
        locations[11] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 4, startPosition = Vector3f(x=519.32f,y=-8.00f,z=758.77f)),
            entrancePosition = Vector3f(x=520.72f,y=-8.00f,z=760.82f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=489.81f,y=-3.00f,z=708.85f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=488.65f,y=-3.00f,z=711.08f),
            spawnerArea = SpawnArea(Vector3f(x=477.44f,y=-3.50f,z=734.09f), Vector3f(22f, 0f, 22f)),
            pathingSettings = PathingSettings(Vector3f(x=511.02f,y=-5.00f,z=747.16f), radius = 70f, zMax = 775f),
            boundaries = listOf(
                PushWall(center = Vector3f(x=518.88f,y=-12.16f,z=775.19f), direction = Vector3f.NegZ, tracking = true)
            ),
        )

        // Manaclipper
        locations[12] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 3, startPosition = Vector3f(x=17.13f,y=-3.00f,z=2.18f)),
            treasureChestPosition = Vector3f(x=-16.41f,y=-3.00f,z=3.74f),
            entrancePosition = Vector3f(x=20.26f,y=-3.00f,z=2.17f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-18.95f,y=-3.00f,z=1.78f),
            exitLook = ModelLook.npc(0x9BC),
            spawnerArea = SpawnArea(Vector3f(x=0.00f,y=-3.00f,z=-6.50f), Vector3f(9f, 0f, 10f)),
            shipRoute = DatId("c002"),
            pathingSettings = PathingSettings(Vector3f(x=0f,y=-3f,z=0f), radius = 30f),
        ) {
            val mainArea = SceneManager.getCurrentScene().getMainArea()
            mainArea.root.getNullableChildRecursivelyAs(DatId("eved"), EffectRoutineResource::class)?.let {
                EffectManager.registerRoutine(ZoneAssociation(mainArea), it)
            }
        }

        // Bibiki Bay: Island Entrance
        locations[13] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 4, startPosition = Vector3f(x=-398.41f,y=-3.03f,z=-419.06f)),
            entrancePosition = Vector3f(x=-398.41f,y=-3.03f,z=-416.06f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-353.89f, y=-2.85f, z=-430.57f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-354.89f,y=-2.68f,z=-429.03f),
            spawnerArea = SpawnArea(Vector3f(x=-364.44f, y=-2.95f, z=-438.79f), Vector3f(x=11.00f, y=0.00f, z=13.00f)),
            musicSettings = MusicSettings(musicId = 229, battleSoloMusicId = 101, battlePartyMusicId = 219),
            pathingSettings = PathingSettings(Vector3f(x=-372.44f, y=-2.95f, z=-438.79f), radius = 40f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-372.44f, y=-2.95f, z=-438.79f), radius = 40f)
            ),
        )

        // Bibiki Bay: Shimmery
        locations[14] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 4, startPosition = Vector3f(x=-644.24f,y=0.30f,z=-679.23f)),
            entrancePosition = Vector3f(x=-644.09f,y=0.30f,z=-676.15f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-650.66f,y=0.30f,z=-718.15f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-647.72f,y=0.30f,z=-717.53f),
            spawnerArea = SpawnArea(Vector3f(x=-637.57f,y=0.30f,z=-708.27f), Vector3f(x=10.00f,y=0.00f,z=15.00f)),
            timeOfDay = BattleTimeOfDay(hour = 17, minute = 15),
            musicSettings = MusicSettings(musicId = 229, battleSoloMusicId = 101, battlePartyMusicId = 219),
            pathingSettings = PathingSettings(Vector3f(x=-648.57f,y=0.30f,z=-704.27f), radius = 40f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-648.57f,y=0.30f,z=-704.27f), radius = 40f)
            ),
        )

        // Bibiki Bay: Orcish Warmachine
        locations[15] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 4, startPosition = Vector3f(x=-359.15f,y=-3.40f,z=-881.89f)),
            entrancePosition = Vector3f(x=-361.99f,y=-3.40f,z=-879.40f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-323.94f,y=-3.35f,z=-884.66f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-340.66f,y=-2.47f,z=-881.85f),
            spawnerArea = SpawnArea(Vector3f(x=-347.26f,y=-0.16f,z=-903.08f), Vector3f(x=1.00f,y=0.00f,z=1.00f)),
            musicSettings = MusicSettings(battleSoloMusicId = 219, battlePartyMusicId = 219),
            timeOfDay = BattleTimeOfDay(hour = 16, minute = 0),
            pathingSettings = PathingSettings(Vector3f(x=-347.26f,y=-0.16f,z=-901.08f), radius = 35f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-347.26f,y=-0.16f,z=-901.08f), radius = 35f)
            ),
        )

        // Dho Gates: River
        locations[16] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 272, startPosition = Vector3f(x=-238.96f,y=-20.30f,z=162.84f)),
            entrancePosition = Vector3f(x=-238.30f,y=-19.00f,z=167.06f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-242.57f,y=-18.96f,z=113.36f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-244.34f,y=-19.32f,z=115.58f),
            spawnerArea = SpawnArea(Vector3f(x=-241.50f,y=-19.19f,z=136.95f), Vector3f(x=9.00f,y=0.00f,z=13.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-241.33f,y=-19.14f,z=136.92f), radius = 35f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-241.33f,y=-19.14f,z=136.92f), radius = 35f)
            ),
        )

        // Dho Gates: Root
        locations[17] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 272, startPosition = Vector3f(x=-61.44f,y=-9.94f,z=60.95f)),
            entrancePosition = Vector3f(x=-61.60f,y=-10.16f,z=58.15f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-65.74f,y=-10.01f,z=89.01f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-63.75f,y=-10.29f,z=88.86f),
            spawnerArea = SpawnArea(Vector3f(x=-59.81f,y=-10.99f,z=79.2f), Vector3f(x=0.00f,y=0.00f,z=0.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-59.81f,y=-9.99f,z=77.76f), radius = 25f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-59.81f,y=-9.99f,z=77.76f), radius = 25f)
            ),
        )

        // Foret de Hennetiel: Tree Bridge
        locations[18] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 262, startPosition = Vector3f(x=-415.59f,y=-5.30f,z=56.89f)),
            entrancePosition = Vector3f(x=-417.36f,y=-5.97f,z=59.44f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-413.79f,y=-1.44f,z=-27.43f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-412.62f,y=-1.16f,z=-25.64f),
            spawnerArea = SpawnArea(Vector3f(x=-400.73f,y=-1.82f,z=34.16f), Vector3f(x=11.00f,y=0.00f,z=11.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-419.00f,y=-1.42f,z=-17.59f), radius = 80f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-419.00f,y=-1.42f,z=-17.59f), radius = 80f)
            ),
            onCleared = {
                openDoor(DatId("_7a0"))
                openDoor(DatId("_7aa"))
            }
        )

        // Foret de Hennetiel: Wildskeeper Entrance
        locations[19] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 262, startPosition = Vector3f(x=-256.42f,y=-5.57f,z=-182.76f)),
            entrancePosition = Vector3f(x=-258.68f,y=-5.97f,z=-180.17f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-225.09f,y=-4.91f,z=-215.08f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-227.43f,y=-4.46f,z=-214.51f),
            spawnerArea = SpawnArea(Vector3f(x=-236.00f,y=-2.00f,z=-197.78f), Vector3f(x=10.00f,y=0.00f,z=10.00f)),
            timeOfDay = BattleTimeOfDay(hour = 20, minute = 0),
            pathingSettings = PathingSettings(Vector3f(x=-236.00f,y=-2.00f,z=-197.78f), radius = 30f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-236.00f,y=-2.00f,z=-197.78f), radius = 30f)
            ),
        )

        // Foret de Hennetiel: Wildskeeper Reive
        locations[20] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 262, startPosition = Vector3f(x=-202.82f,y=-0.50f,z=-438.61f)),
            entrancePosition = Vector3f(x=-207.74f,y=-0.73f,z=-438.03f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-198.78f,y=-0.78f,z=-430.74f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-200.13f,y=-0.76f,z=-431.43f),
            spawnerArea = SpawnArea(Vector3f(x=-161.80f,y=-0.45f,z=-444.94f), Vector3f(x=10.00f,y=0.00f,z=10.00f)),
            musicSettings = MusicSettings(musicId = null, battleSoloMusicId = 62, battlePartyMusicId = 62),
            pathingSettings = PathingSettings(Vector3f(x=-161.80f,y=-0.45f,z=-444.94f), radius = 55f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-161.80f,y=-0.45f,z=-444.94f), radius = 55f)
            ),
        )

        // Uleguerand Range: Cliff Foot
        locations[21] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 5, startPosition = Vector3f(x=-297.64f,y=-40.11f,z=-343.78f)),
            entrancePosition = Vector3f(x=-297.55f,y=-39.83f,z=-346.32f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-285.71f,y=-39.89f,z=-289.81f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-288.35f,y=-40.14f,z=-291.19f),
            spawnerArea = SpawnArea(Vector3f(x=-286.49f,y=-39.79f,z=-313.99f), Vector3f(x=14.00f,y=0.00f,z=14.00f)),
            timeOfDay = BattleTimeOfDay(hour = 17, minute = 15),
            pathingSettings = PathingSettings(Vector3f(x=-304.03f,y=-40.98f,z=-308.19f), radius = 42f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-304.03f,y=-40.98f,z=-308.19f), radius = 42f)
            ),
            onSetup = { setWeather(Weather.snow) }
        )

        // Uleguerand Range: Forest
        locations[22] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 5, startPosition = Vector3f(x=150.45f,y=-0.04f,z=-329.97f)),
            entrancePosition = Vector3f(x=146.68f,y=-0.03f,z=-332.68f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=185.12f,y=0.27f,z=-295.69f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=184.50f,y=0.09f,z=-299.05f),
            spawnerArea = SpawnArea(Vector3f(x=173.46f,y=0.54f,z=-316.65f), Vector3f(x=14.00f,y=0.00f,z=14.00f)),
            timeOfDay = BattleTimeOfDay(hour = 17, minute = 15),
            pathingSettings = PathingSettings(Vector3f(x=173.46f,y=0.54f,z=-316.65f), radius = 35f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=173.46f,y=0.54f,z=-316.65f), radius = 35f)
            ),
            onSetup = { setWeather(Weather.bliz) }
        )

        // Uleguerand Range: Cliff Top
        locations[23] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 5, startPosition = Vector3f(x=-284.47f,y=-176.01f,z=-122.57f)),
            entrancePosition = Vector3f(x=-284.54f,y=-176.01f,z=-126.59f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-282.62f,y=-176.00f,z=-40.38f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-281.09f,y=-176.00f,z=-42.23f),
            spawnerArea = SpawnArea(Vector3f(x=-277.20f,y=-176.00f,z=-81.65f), Vector3f(x=6.00f,y=0.00f,z=6.00f)),
            timeOfDay = BattleTimeOfDay(hour = 17, minute = 15),
            pathingSettings = PathingSettings(Vector3f(x=-277.20f,y=-176.00f,z=-81.65f), radius = 48f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-277.20f,y=-176.00f,z=-81.65f), radius = 48f)
            ),
            onSetup = { setWeather(Weather.suny) }
        )

        // Uleguerand Range: Frozen Lake
        locations[24] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 5, startPosition = Vector3f(x=-37.92f,y=-176.00f,z=78.53f)),
            entrancePosition = Vector3f(x=-41.01f,y=-176.00f,z=80.26f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=14.31f,y=-177.12f,z=73.75f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=13.41f,y=-177.33f,z=72.11f),
            spawnerArea = SpawnArea(Vector3f(x=-22.33f,y=-175.00f,z=51.58f), Vector3f(x=15.00f,y=0.00f,z=15.00f)),
            timeOfDay = BattleTimeOfDay(hour = 17, minute = 15),
            pathingSettings = PathingSettings(Vector3f(x=-22.33f,y=-175.00f,z=51.58f), radius = 44f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-22.33f,y=-175.00f,z=51.58f), radius = 44f)
            ),
            onCleared = { openDoor(DatId("_058")); setWeather(Weather.suny, transitionDuration = 5.seconds) },
            onSetup = { setWeather(Weather.snow) }
        )

        // Bearclaw Pinnacle
        locations[25] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 6, startPosition = Vector3f(x=-199.97f,y=-0.34f,z=433.62f)),
            entrancePosition = Vector3f(x=-197.23f,y=-0.10f,z=436.05f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-219.34f,y=-1.08f,z=533.49f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-221.49f,y=-0.71f,z=531.83f),
            spawnerArea = SpawnArea(Vector3f(x=-222.97f,y=0.45f,z=500.14f), Vector3f(x=14.00f,y=0.00f,z=14.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-220.99f,y=0.75f,z=478.97f), 60f),
        )

        // Promy. Holla: Warp 06
        locations[26] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 16, startPosition = Vector3f(x=-176.33f,y=0.00f,z=-144.65f)),
            entrancePosition = Vector3f(x=-180.02f,y=0.00f,z=-140.29f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-160.01f,y=-0.50f,z=-200.20f),
            exitLook = ModelLook.npc(0x032),
            treasureChestPosition = Vector3f(x=-157.32f,y=-0.05f,z=-192.99f),
            spawnerArea = SpawnArea(Vector3f(x=-163.12f,y=-0.00f,z=-168.61f), Vector3f(x=9.00f,y=0.00f,z=15.00f)),
            musicSettings = MusicSettings(musicId = 222, battleSoloMusicId = 222, battlePartyMusicId = 222),
            pathingSettings = PathingSettings(Vector3f(x=-163.12f,y=-0.00f,z=-168.61f), radius = 38f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-163.12f,y=-0.00f,z=-168.61f), radius = 38f)
            ),
            onSetup = { playZoneRoutine("effe/dor1/kib1") },
            onCleared = { openDoor(DatId("_0g6")) }
        )

        // Promy. Dem: Warp 05
        locations[27] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 18, startPosition = Vector3f(x=-127.93f,y=-0.30f,z=-57.80f)),
            entrancePosition = Vector3f(x=-130.37f,y=-0.31f,z=-54.13f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-80.01f,y=-0.50f,z=-79.97f),
            exitLook = ModelLook.npc(0x032),
            treasureChestPosition = Vector3f(x=-85.00f,y=-0.30f,z=-77.18f),
            spawnerArea = SpawnArea(Vector3f(x=-110.83f,y=0.00f,z=-80.73f), Vector3f(x=14.00f,y=0.00f,z=10.00f)),
            musicSettings = MusicSettings(musicId = 222, battleSoloMusicId = 222, battlePartyMusicId = 222),
            pathingSettings = PathingSettings(Vector3f(x=-110.83f,y=0.00f,z=-80.73f), radius = 38f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-110.83f,y=0.00f,z=-80.73f), radius = 38f)
            ),
            onCleared = { openDoor(DatId("_0i5")) }
        )

        // Promy. Mea: Warp 09
        locations[28] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 20, startPosition = Vector3f(x=-77.21f,y=-0.00f,z=-280.52f)),
            entrancePosition = Vector3f(x=-80.69f,y=0.00f,z=-281.81f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-39.99f,y=-0.49f,z=-319.99f),
            exitLook = ModelLook.npc(0x032),
            treasureChestPosition = Vector3f(x=-37.43f,y=-0.30f,z=-314.89f),
            spawnerArea = SpawnArea(Vector3f(x=-50.50f,y=0.00f,z=-287.42f), Vector3f(x=14.00f,y=0.00f,z=11.00f)),
            musicSettings = MusicSettings(musicId = 222, battleSoloMusicId = 222, battlePartyMusicId = 222),
            pathingSettings = PathingSettings(Vector3f(x=-50.50f,y=0.00f,z=-287.42f), radius = 38f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-50.50f,y=0.00f,z=-287.42f), radius = 38f)
            ),
            onCleared = { openDoor(DatId("_0k9")) }
        )

        // Promy. Vahzl: Warp 09
        locations[29] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 22, startPosition = Vector3f(x=342.01f,y=-0.20f,z=-127.74f)),
            entrancePosition = Vector3f(x=338.11f,y=-0.84f,z=-132.06f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=360.00f,y=-0.50f,z=-79.99f),
            exitLook = ModelLook.npc(0x032),
            treasureChestPosition = Vector3f(x=357.44f,y=-0.30f,z=-84.70f),
            spawnerArea = SpawnArea(Vector3f(x=354.78f,y=-0.00f,z=-103.25f), Vector3f(x=14.00f,y=0.00f,z=11.00f)),
            musicSettings = MusicSettings(musicId = 222, battleSoloMusicId = 222, battlePartyMusicId = 222),
            pathingSettings = PathingSettings(Vector3f(x=354.78f,y=-0.00f,z=-103.25f), radius = 35f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=354.78f,y=-0.00f,z=-103.25f), radius = 35f)
            ),
            onCleared = { openDoor(DatId("_0m9")) }
        )

        // Spire of Vahzl
        locations[30] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 23, startPosition = Vector3f(x=0.09f,y=-1.99f,z=33.81f)),
            entrancePosition = Vector3f(x=0.01f,y=-1.99f,z=39.04f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-0.25f,y=-0.00f,z=-22.98f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=0.58f,y=-0.00f,z=-21.63f),
            spawnerArea = SpawnArea(Vector3f(x=0.00f,y=0.00f,z=0.00f), Vector3f(x=0.00f,y=0.00f,z=0.00f)),
            pathingSettings = PathingSettings(root = Vector3f(x=0.00f,y=0.75f,z=0.00f), radius = 50f)
        )

        // Halvung: Corridor
        locations[31] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 62, startPosition = Vector3f(x=376.97f,y=-0.05f,z=99.95f)),
            entrancePosition = Vector3f(x=378.95f,y=0.00f,z=99.94f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=300.97f,y=10.00f,z=98.78f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=302.97f,y=10.00f,z=100.00f),
            pathingSettings = PathingSettings(Vector3f(x=337.37f,y=5.11f,z=96.04f), radius = 48f),
            spawnerArea = SpawnArea(Vector3f(x=337.37f,y=5.11f,z=96.04f), Vector3f(x=30.00f,y=0.00f,z=5.00f)),
            boundaries = listOf(
                EncompassingSphere(Vector3f(x=337.37f,y=5.11f,z=96.04f), radius = 48f),
            ),
            musicSettings = MusicSettings(battleSoloMusicId = 138),
        )

        // Halvung: Gurfurlur
        locations[32] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 62, startPosition = Vector3f(x=-60f,y=-17f,z=60f)),
            entrancePosition = Vector3f(x=-60f,y=-17.63f,z=66.60f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-60f,y=-23.86f,z=0f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-58.11f,y=-23.69f,z=2.06f),
            spawnerArea = SpawnArea(Vector3f(x=-60f,y=-16.00f,z=36.65f), Vector3f(x=6.00f,y=0.00f,z=6.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-60.00f,y=-23.00f,z=15.00f), radius = 63f, zMax = 80f),
            boundaries = emptyList(),
            musicSettings = MusicSettings(battleSoloMusicId = 138),
        )

        // Mount Zhayolm: Sunrise
        locations[33] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 61, startPosition = Vector3f(x=823.21f,y=-17.60f,z=284.47f)),
            entrancePosition = Vector3f(x=823.30f,y=-17.15f,z=287.53f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=820.22f,y=-14.83f,z=219.71f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=818.48f,y=-14.87f,z=221.75f),
            spawnerArea = SpawnArea(Vector3f(x=820.29f,y=-14.97f,z=255.75f), Vector3f(x=15.00f,y=0.00f,z=15.00f)),
            pathingSettings = PathingSettings(Vector3f(x=820.29f,y=-14.97f,z=255.75f), radius = 50f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=820.29f,y=-14.97f,z=255.75f), radius = 50f)
            ),
            timeOfDay = BattleTimeOfDay(hour = 5, minute = 45),
            musicSettings = MusicSettings(battleSoloMusicId = 138),
        )

        // Mount Zhayolm: Volcano
        locations[34] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 61, startPosition = Vector3f(x=467.86f,y=-26.23f,z=140.26f)),
            entrancePosition = Vector3f(x=472.14f,y=-27.30f,z=140.88f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=455.14f,y=-22.89f,z=109.25f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=451.95f,y=-23.07f,z=108.30f),
            spawnerArea = SpawnArea(Vector3f(x=422.97f,y=-23.48f,z=144.55f), Vector3f(x=20.00f,y=0.00f,z=20.00f)),
            pathingSettings = PathingSettings(Vector3f(x=434.25f,y=-23.69f,z=132.82f), radius = 50f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=434.25f,y=-23.69f,z=132.82f), radius = 50f)
            ),
            timeOfDay = BattleTimeOfDay(hour = 11, minute = 45),
            musicSettings = MusicSettings(battleSoloMusicId = 138),
        )

        // Navukgo Execution Chamber
        locations[35] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 64, startPosition = Vector3f(x=-60.60f,y=-18.86f,z=-18.48f)),
            entrancePosition = Vector3f(x=-63.75f,y=-19.21f,z=-18.40f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-55.92f,y=-18.19f,z=-21.55f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-57.42f,y=-18.51f,z=-20.22f),
            spawnerArea = SpawnArea(Vector3f(x=-22.01f,y=-16.00f,z=-19.84f), Vector3f(x=0.00f,y=0.00f,z=0.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-22.01f,y=-16.00f,z=-19.84f), radius = 45f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-22.01f,y=-16.00f,z=-19.84f), radius = 45f)
            ),
        )

        // Qufim Island
        locations[36] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 126, startPosition = Vector3f(x=-120.02f,y=-19.30f,z=375.02f)),
            entrancePosition = Vector3f(x=-115.21f,y=-19.12f,z=381.39f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-165.06f,y=-19.84f,z=325.05f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-161.98f,y=-19.90f,z=325.99f),
            spawnerArea = SpawnArea(Vector3f(x=-140.31f,y=-20.39f,z=345.58f), Vector3f(x=20.00f,y=0.00f,z=20.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-140.31f,y=-20.39f,z=345.58f), radius = 60f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-140.31f,y=-20.39f,z=345.58f), radius = 60f)
            ),
            timeOfDay = BattleTimeOfDay(hour = 20, minute = 0),
            onSetup = { setWeather(Weather.aura) }
        )

        // Lower Delkfutt - 3rd Floor
        locations[37] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 184, startPosition = Vector3f(x=425.55f,y=-31.42f,z=28.99f)),
            entrancePosition = Vector3f(x=427.87f,y=-31.42f,z=27.79f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=406.43f,y=-33.88f,z=86.38f),
            exitLook = ModelLook.npc(0x032),
            treasureChestPosition = Vector3f(x=403.73f,y=-33.88f,z=85.73f),
            spawnerArea = SpawnArea(Vector3f(x=400.61f,y=-31.42f,z=58.02f), Vector3f(x=20.00f,y=0.00f,z=20.00f)),
            pathingSettings = PathingSettings(Vector3f(x=400.61f,y=-31.42f,z=58.02f), radius = 50f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=400.61f,y=-31.42f,z=58.02f), radius = 50f)
            ),
        )

        // Middle Delkfutt - 7th Floor
        locations[38] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 157, startPosition = Vector3f(x=-355.29f,y=-97.88f,z=-44.87f)),
            entrancePosition = Vector3f(x=-353.66f,y=-97.88f,z=-46.48f),
            entranceLook = ModelLook.npc(0x032),
            exitPosition = Vector3f(x=-353.68f,y=-97.88f,z=86.48f),
            exitLook = ModelLook.npc(0x032),
            treasureChestPosition = Vector3f(x=-356.36f,y=-97.88f,z=85.47f),
            spawnerArea = SpawnArea(Vector3f(x=-366.48f,y=-95.42f,z=11.59f), Vector3f(x=20.00f,y=0.00f,z=40.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-362.48f,y=-95.42f,z=20.00f), radius = 85f, xMin = -408f),
            boundaries = listOf(
                PushWall(center = Vector3f(x=-408f,y=-95.42f,z=-3f), direction = Vector3f.X, tracking = true)
            ),
        )

        // Upper Delkfutt - 10th Floor
        locations[39] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 158, startPosition = Vector3f(x=-329.82f,y=-143.93f,z=-4.41f)),
            entrancePosition = Vector3f(x=-333.46f,y=-144.01f,z=-5.32f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-290.23f,y=-143.99f,z=50.33f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-287.35f,y=-144.00f,z=51.19f),
            spawnerArea = SpawnArea(Vector3f(x=-287.13f,y=-143.42f,z=14.95f), Vector3f(x=25.00f,y=0.00f,z=25.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-287.13f,y=-143.42f,z=14.95f), radius = 55f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-287.13f,y=-143.42f,z=14.95f), radius = 55f)
            ),
        )

        // Stellar Fulcrum
        locations[40] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 179, startPosition = Vector3f(x=-0.00f,y=-200.00f,z=415.60f)),
            entrancePosition = Vector3f(x=-0.00f,y=-200.00f,z=412.89f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=1.29f,y=-199.88f,z=418.35f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=1.28f,y=-200.00f,z=417.25f),
            spawnerArea = SpawnArea(Vector3f(x=-0.00f,y=-197.50f,z=439.00f), Vector3f(x=0.00f,y=0.00f,z=0.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-0.00f,y=-197.50f,z=439.07f), radius = 50f),
        )

        // Cirdas Cavern - 1
        locations[41] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 270, startPosition = Vector3f(x=-26.83f,y=20.03f,z=61.81f)),
            entrancePosition = Vector3f(x=-30.83f,y=19.97f,z=61.42f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=9.86f,y=20.00f,z=89.94f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=7.12f,y=19.87f,z=89.96f),
            spawnerArea = SpawnArea(Vector3f(x=-0.03f,y=20.00f,z=80.07f), Vector3f(x=10.00f,y=0.00f,z=10.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-0.03f,y=20.00f,z=80.07f), radius = 40f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-0.03f,y=20.00f,z=80.07f), radius = 40f)
            ),
        )

        // Cirdas Cavern - 2
        locations[42] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 270, startPosition = Vector3f(x=140.60f,y=10.78f,z=117.02f)),
            entrancePosition = Vector3f(x=137.78f,y=9.98f,z=114.34f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=157.18f,y=10.00f,z=157.96f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=155.62f,y=10.05f,z=156.47f),
            spawnerArea = SpawnArea(Vector3f(x=155.97f,y=10.39f,z=135.45f), Vector3f(x=10.00f,y=0.00f,z=15.00f)),
            pathingSettings = PathingSettings(Vector3f(x=155.97f,y=10.39f,z=135.45f), radius = 30f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=155.97f,y=10.39f,z=135.45f), radius = 30f)
            ),
        )

        // Yorcia Weald - 1
        locations[43] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 263, startPosition = Vector3f(x=-149.10f,y=0.15f,z=446.86f)),
            entrancePosition = Vector3f(x=-148.99f,y=0.16f,z=450.81f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-166.08f,y=0.17f,z=397.87f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-166.07f,y=-0.13f,z=400.15f),
            spawnerArea = SpawnArea(Vector3f(x=-159.40f,y=0.90f,z=421.93f), Vector3f(x=10.00f,y=0.00f,z=15.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-159.40f,y=0.90f,z=421.93f), radius = 35f),
            boundaries = listOf(
                PushWall(center = Vector3f(x=-131.53f,y=0.37f,z=438.80f), direction = Vector3f.West, tracking = true),
            ),
            timeOfDay = BattleTimeOfDay(hour = 16, minute = 0),
            musicSettings = MusicSettings(musicId = 61, battleSoloMusicId = 61, battlePartyMusicId = 61),
        )

        // Yorcia Weald - 2
        locations[44] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 263, startPosition = Vector3f(x=-392.34f,y=0.26f,z=-32.60f)),
            entrancePosition = Vector3f(x=-390.17f,y=0.10f,z=-29.49f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-410.86f,y=0.73f,z=-51.69f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-410.61f,y=0.67f,z=-49.43f),
            spawnerArea = SpawnArea(Vector3f(x=-401.28f,y=0.10f,z=-41.85f), Vector3f(x=3.00f,y=0.00f,z=3.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-401.28f,y=0.10f,z=-41.85f), radius = 30f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-401.28f,y=0.10f,z=-41.85f), radius = 30f)
            ),
            musicSettings = MusicSettings(musicId = 61, battleSoloMusicId = 61, battlePartyMusicId = 61),
        )

        // Yorcia Weald - Wildskeeper Reive
        locations[45] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 263, startPosition = Vector3f(x=-458.78f,y=0.77f,z=-97.08f)),
            entrancePosition = Vector3f(x=-457.17f,y=0.78f,z=-98.54f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-462.80f,y=0.81f,z=-97.29f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-461.59f,y=0.76f,z=-97.02f),
            spawnerArea = SpawnArea(Vector3f(x=-494.52f,y=1.36f,z=-58.10f), Vector3f(x=3.00f,y=0.00f,z=3.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-494.52f,y=1.36f,z=-58.10f), radius = 60f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-494.52f,y=1.36f,z=-58.10f), radius = 60f)
            ),
            musicSettings = MusicSettings(musicId = null, battleSoloMusicId = 62, battlePartyMusicId = 62),
        )

        // Xarcabard (S)
        locations[46] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 137, startPosition = Vector3f(x=-322.85f,y=-28.01f,z=-45.31f)),
            entrancePosition = Vector3f(x=-323.08f,y=-27.75f,z=-49.02f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-400.00f,y=-43.05f,z=21.00f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-397.84f,y=-43.14f,z=23.05f),
            spawnerArea = SpawnArea(Vector3f(x=-360.34f,y=-41.53f,z=7.67f), Vector3f(x=25.00f,y=0.00f,z=25.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-345.21f,y=-33.31f,z=5.99f), radius = 65f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-345.21f,y=-33.31f,z=5.99f), radius = 65f)
            ),
            onSetup = { setWeather(Weather.dark) },
            musicSettings = MusicSettings(musicId = 42, battleSoloMusicId = 42, battlePartyMusicId = 42),
            timeOfDay = BattleTimeOfDay(hour = 20, minute = 0)
        )

        // Castle Zvahl Baileys (S)
        locations[47] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 138, startPosition = Vector3f(x=120.00f,y=-24.00f,z=20.00f)),
            entrancePosition = Vector3f(x=124.00f,y=-24.01f,z=20.00f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=65.00f,y=-24.04f,z=20.00f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=66.00f,y=-24.03f,z=21.00f),
            spawnerArea = SpawnArea(Vector3f(x=90.00f,y=-24.02f,z=20.00f), Vector3f(x=18.00f,y=0.00f,z=20.00f)),
            pathingSettings = PathingSettings(Vector3f(x=90.00f,y=-24.02f,z=20.00f), radius = 40f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=90.00f,y=-24.02f,z=20.00f), radius = 40f)
            ),
            onSetup = { setWeather(Weather.dark) },
            musicSettings = MusicSettings(musicId = 43, battleSoloMusicId = 43, battlePartyMusicId = 43),
        )

        // Castle Zvahl Keep (S) - Exit
        locations[48] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 155, startPosition = Vector3f(x=-499.00f,y=-68.00f,z=60.00f)),
            entrancePosition = Vector3f(x=-494.00f,y=-68.00f,z=60.00f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-550.00f,y=-70.00f,z=60.00f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-548.85f,y=-70.00f,z=61.97f),
            spawnerArea = SpawnArea(Vector3f(x=-525.00f,y=-68.00f,z=60.00f), Vector3f(x=10.00f,y=0.00f,z=6.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-525.00f,y=-68.00f,z=60.00f), radius = 40f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=-525.00f,y=-68.00f,z=60.00f), radius = 40f)
            ),
            onSetup = { setWeather(Weather.dark) },
            musicSettings = MusicSettings(musicId = 43, battleSoloMusicId = 43, battlePartyMusicId = 43),
        )

        // Throne Room (S) - Outside
        locations[49] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 156, startPosition = Vector3f(x=45.00f,y=0.00f,z=0.00f)),
            entrancePosition = Vector3f(x=50.00f,y=0.00f,z=0.00f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-45.00f,y=0.00f,z=0.00f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-42.5f,y=0.00f,z=-2.00f),
            spawnerArea = SpawnArea(Vector3f(x=0.00f,y=0.00f,z=0.00f), Vector3f(x=0.00f,y=0.00f,z=0.00f)),
            pathingSettings = PathingSettings(Vector3f(x=0.00f,y=0.00f,z=0.00f), radius = 55f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=0.00f,y=0.00f,z=0.00f), radius = 55f, condition = playerDoesNotHaveEnmity),
                PushWall(center = Vector3f(x=21.75f,y=0.00f,z=0.00f), direction = Vector3f.West, condition = playerHasEnmity),
                PushWall(center = Vector3f(x=-21.75f,y=0.00f,z=0.00f), direction = Vector3f.East, condition = playerHasEnmity),
            ),
            onSetup = { setWeather(Weather.dark) },
            musicSettings = MusicSettings(musicId = 43, battleSoloMusicId = 43, battlePartyMusicId = 43),
        )

        // Throne Room (S)
        locations[50] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 156, startPosition = Vector3f(x=-442.50f,y=-167.01f,z=-240.00f)),
            entrancePosition = Vector3f(x=-439.44f,y=-167.00f,z=-243.52f),
            entranceLook = ModelLook.npc(0x975),
            exitPosition = Vector3f(x=-439.44f,y=-167.00f,z=-236.48f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=-438.13f,y=-167.09f,z=-237.76f),
            spawnerArea = SpawnArea(Vector3f(x=-482.75f,y=-172.00f,z=-240.00f), Vector3f(x=0.10f,y=0.00f,z=0.10f)),
            pathingSettings = PathingSettings(Vector3f(x=-482.75f,y=-172.00f,z=-240.00f), radius = 47f),
            boundaries = listOf(
                PushWall(center = Vector3f(x=-470.42f,y=-167.10f,z=-240.02f), direction = Vector3f.X, condition = playerHasEnmity),
            ),
            onSetup = { setWeather(Weather.dark) },
            musicSettings = MusicSettings(musicId = null, battleSoloMusicId = 119, battlePartyMusicId = 119),
        )

        // Riverne B01 - Entrance
        locations[141] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 29, startPosition = Vector3f(x=730.65f,y=-19.84f,z=409.10f)),
            entrancePosition = Vector3f(x=732.75f, y=-22.18f, z=411.96f),
            entranceLook = ModelLook.npc(0x032),
            exitPosition = Vector3f(x=663.38f, y=-27.18f, z=340.08f),
            exitLook = ModelLook.npc(0x032),
            treasureChestPosition = Vector3f(x=667.22f,y=-24.50f,z=341.90f),
            spawnerArea = SpawnArea(Vector3f(x=692.45f,y=-23.27f,z=374.46f), Vector3f(x=20.00f,y=0.00f,z=20.00f)),
            pathingSettings = PathingSettings(Vector3f(x=692.45f,y=-23.27f,z=374.46f), radius = 60f),
        )

        // Riverne B01 - Main land
        locations[142] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 29, startPosition = Vector3f(x=696.01f,y=-0.49f,z=-505.11f)),
            entrancePosition = Vector3f(x=698.44f,y=-0.50f,z=-503.71f),
            entranceLook = ModelLook.npc(0x032),
            exitPosition = Vector3f(x=538.98f,y=-0.50f,z=-422.44f),
            exitLook = ModelLook.npc(0x032),
            treasureChestPosition = Vector3f(x=541.44f,y=-0.49f,z=-422.45f),
            spawnerArea = SpawnArea(Vector3f(x=628.79f,y=1.00f,z=-500.18f), Vector3f(x=35.00f,y=0.00f,z=35.00f)),
            pathingSettings = PathingSettings(Vector3f(x=614.80f,y=-0.59f,z=-462.36f), radius = 100f),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=619.50f,y=-0.50f,z=-460.30f), radius = 95f)
            ),
        )

        // Riverne B01 - Island
        locations[143] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 29, startPosition = Vector3f(x=56.36f,y=51.50f,z=745.69f)),
            entrancePosition = Vector3f(x=59.36f,y=51.50f,z=743.39f),
            entranceLook = ModelLook.npc(0x032),
            exitPosition = Vector3f(x=18.71f,y=51.50f,z=703.97f),
            exitLook = ModelLook.npc(0x032),
            treasureChestPosition = Vector3f(x=15.05f,y=51.50f,z=704.88f),
            spawnerArea = SpawnArea(Vector3f(x=7.14f,y=52.17f,z=744.57f), Vector3f(x=30.00f,y=0.00f,z=30.00f)),
            pathingSettings = PathingSettings(Vector3f(x=7.14f,y=52.17f,z=744.57f), radius = 60f),
        )

        // Riverne B01 - Monarch Linn entrance
        locations[144] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 29, startPosition = Vector3f(x=-504.62f,y=-20.50f,z=578.30f)),
            entrancePosition = Vector3f(x=-502.88f,y=-20.50f,z=580.97f),
            entranceLook = ModelLook.npc(0x032),
            exitPosition = Vector3f(x=-536.83f,y=-20.50f,z=500.00f),
            exitLook = ModelLook.npc(0x032),
            treasureChestPosition = Vector3f(x=0.00f,y=0.00f,z=0.00f),
            spawnerArea = SpawnArea(Vector3f(x=-520.75f,y=-19.71f,z=530.32f), Vector3f(x=20.00f,y=0.00f,z=30.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-520.75f,y=-19.71f,z=530.32f), radius = 30f),
        )

        // Monarch Linn
        locations[145] = BattleLocation(
            startingPosition = ZoneConfig(zoneId = 31, startPosition = Vector3f(x=79.30f,y=-0.27f,z=0.01f)),
            entrancePosition = Vector3f(x=81.54f,y=-0.34f,z=-1.67f),
            entranceLook = ModelLook.npc(0x32),
            exitPosition = Vector3f(x=74.42f,y=-0.01f,z=4.54f),
            exitLook = ModelLook.npc(0x9BC),
            treasureChestPosition = Vector3f(x=76.20f,y=-0.07f,z=5.03f),
            spawnerArea = SpawnArea(Vector3f(x=-1.20f,y=2.20f,z=-0.29f), Vector3f(x=1.00f,y=0.00f,z=1.00f)),
            pathingSettings = PathingSettings(Vector3f(x=-0.00f,y=2.20f,z=0.00f), radius = 100f),
            timeOfDay = BattleTimeOfDay(hour = 16, minute = 0),
        )

    }

    operator fun get(index: Int): BattleLocation {
        return locations[index] ?: throw IllegalStateException("No such battle location: $index")
    }

    fun getAll(): Collection<BattleLocation> {
        return locations.values
    }

    private fun openDoor(datId: DatId) {
        val scene = SceneManager.getCurrentScene()
        scene.openDoor(datId)
    }

    private fun setWeather(weather: Weather, transitionDuration: Duration = Duration.ZERO) {
        EnvironmentManager.switchWeather(DatId(weather.id), interpolationTime = transitionDuration)
    }

    private fun playZoneRoutine(datPath: String) {
        val dats = datPath.split("/").map { DatId(it) }
        val scene = SceneManager.getCurrentScene()
        scene.getMainArea().runEffectRoutine(dats) ?: throw IllegalStateException("Failed to run $datPath!")
    }

}