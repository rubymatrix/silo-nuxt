package xim.poc.game.configuration.v0.escha

import xim.math.Vector3f
import xim.poc.Font
import xim.poc.game.CombatStat
import xim.poc.game.configuration.MonsterId
import xim.poc.game.configuration.MonsterProviderFactory
import xim.poc.game.configuration.MonsterSpawnerDefinition
import xim.poc.game.configuration.SpawnArea
import xim.poc.game.configuration.constants.itemAshweed_9078
import xim.poc.game.configuration.v0.*
import xim.poc.game.configuration.v0.constants.*
import xim.poc.game.configuration.v0.escha.EschaKeyItems.keyItemBlazewingsPincer
import xim.poc.game.configuration.v0.escha.EschaKeyItems.keyItemCovensDust
import xim.poc.game.configuration.v0.escha.EschaKeyItems.keyItemFleetstalkersClaw
import xim.poc.game.configuration.v0.escha.EschaKeyItems.keyItemPazuzusBlade
import xim.poc.game.configuration.v0.escha.EschaKeyItems.keyItemShockmawsBlubber
import xim.poc.game.configuration.v0.escha.EschaKeyItems.keyItemUrmahlulluArmor
import xim.poc.game.configuration.v0.escha.EschaKeyItems.keyItemWratharesCarrot
import xim.poc.game.configuration.v0.tower.Boundary
import xim.poc.game.configuration.v0.tower.BoundaryConditions.playerHasEnmity
import xim.poc.game.configuration.v0.tower.EncompassingSphere
import xim.poc.game.configuration.v0.tower.PushWall
import xim.poc.game.configuration.v0.tower.TowerConfiguration.countProvider
import xim.poc.game.configuration.v0.zones.PathingSettings
import xim.poc.tools.ZoneConfig
import xim.poc.ui.MapMarkerCommand
import xim.poc.ui.ShiftJis
import xim.poc.ui.ShiftJis.emptyStar
import xim.poc.ui.ShiftJis.solidStar
import xim.resource.KeyItemId
import xim.util.Fps
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

const val EschaZiTah = 1

enum class EschaDifficulty(val value: Int, val offset: Int, val displayName: String) {
    S1(value = 1, offset = 0, "$solidStar$emptyStar$emptyStar$emptyStar$emptyStar"),
    S2(value = 2, offset = 1, "$solidStar$solidStar$emptyStar$emptyStar$emptyStar"),
    S3(value = 3, offset = 2, "$solidStar$solidStar$solidStar$emptyStar$emptyStar"),
    S4(value = 4, offset = 3, "$solidStar$solidStar$solidStar$solidStar$emptyStar"),
    S5(value = 5, offset = 4, "$solidStar$solidStar$solidStar$solidStar$solidStar"),
    ;
}

operator fun MonsterId.plus(difficulty: EschaDifficulty): MonsterId {
    return MonsterId(this.id + difficulty.offset)
}

object EschaKeyItems {
    const val keyItemBlazewingsPincer = 2907
    const val keyItemCovensDust = 2908
    const val keyItemPazuzusBlade = 2909
    const val keyItemWratharesCarrot = 2910

    const val keyItemFleetstalkersClaw = 2917
    const val keyItemShockmawsBlubber = 2918
    const val keyItemUrmahlulluArmor = 2919

    val zitahKeyItems = listOf(
        keyItemBlazewingsPincer,
        keyItemCovensDust,
        keyItemPazuzusBlade,
        keyItemWratharesCarrot,
        keyItemFleetstalkersClaw,
        keyItemShockmawsBlubber,
        keyItemUrmahlulluArmor,
    )

}

sealed interface EschaBindingArea {
    val center: Vector3f
    val pathingSettings: PathingSettings
    val boundaries: List<Boundary>
}

class EschaMonsterAreaConfiguration(
    override val center: Vector3f,
    override val pathingSettings: PathingSettings,
    override val boundaries: List<Boundary>,
    val monsterSpawnerDefinition: MonsterSpawnerDefinition,
): EschaBindingArea

class EschaBossAreaConfiguration(
    override val center: Vector3f,
    override val pathingSettings: PathingSettings,
    override val boundaries: List<Boundary>,
    val bossSpawnerDefinition: BossSpawnerDefinition,
    val hidden: Boolean = false,
): EschaBindingArea

class EschaPortalConfiguration(
    val index: Int,
    val location: Vector3f,
    val displayName: String,
    val destination: Vector3f,
)

class EschaConfiguration(
    val requiredFloorClear: Int,
    val startingPosition: ZoneConfig,
    val entrancePosition: Vector3f,
    val portals: List<EschaPortalConfiguration>,
    val bindingAreas: List<EschaBindingArea>,
    val vorsealConfiguration: EschaVorsealConfiguration,
    val vorsealRewards: Map<MonsterId, EschaVorsealReward>,
    val staticBoundaries: List<Boundary>,
    val extraMapMarkers: List<MapMarkerCommand> = emptyList(),
    val keyItemDropTable: Map<MonsterId, KeyItemId> = emptyMap(),
    val entityFactory: () -> List<FloorEntity> = { emptyList() },
)

object EschaConfigurations {

    private val configurations = HashMap<Int, EschaConfiguration>()

    init {
        configurations[EschaZiTah] = makeEschaZiTah()
    }

    private fun makeEschaZiTah(): EschaConfiguration {
        val basicMonsterAreas = listOf(
            makeMonsterArea(
                center = Vector3f(x=-411.00f,y=-0.03f,z=-130.00f),
                providerFactory = countProvider(mobEschanWorm_288_001 to 1, mobEschanObdella_288_002 to 1),
            ),
            makeMonsterArea(
                center = Vector3f(x=-375.00f,y=-0.27f,z=11.00f),
                providerFactory = countProvider(mobEschanWorm_288_001 to 1, mobEschanCrawler_288_003 to 1),
            ),
            makeMonsterArea(
                center = Vector3f(x=-290f,y=0.00f,z=210f),
                providerFactory = countProvider(mobEschanCouerl_288_004 to 1),
            ),
            makeMonsterArea(
                center = Vector3f(x=-447.08f,y=0.08f,z=112.15f),
                providerFactory = countProvider(mobEschanDhalmel_288_005 to 1),
            ),
            makeMonsterArea(
                center = Vector3f(x=-409.48f,y=0.27f,z=276.88f),
                providerFactory = countProvider(mobEschanWeapon_288_006 to 1),
            ),
            makeMonsterArea(
                center = Vector3f(x=-33.64f,y=0.68f,z=-135.59f),
                providerFactory = countProvider(mobEschanVulture_288_007 to 2, mobEschanSorcerer_288_008 to 1, mobEschanCorse_288_009 to 1),
            ),
            makeMonsterArea(
                center = Vector3f(x=-328.63f,y=-0.01f,z=-430.20f),
                providerFactory = countProvider(mobEschanGoobbue_288_010 to 1),
            ),
            makeMonsterArea(
                center = Vector3f(x=-23.30f,y=0.56f,z=-416.90f),
                providerFactory = countProvider(mobEschanSnapweed_288_011 to 1, mobEschanPugil_288_014 to 1),
            ),
            makeMonsterArea(
                center = Vector3f(x=-148.81f,y=-0.26f,z=-302.76f),
                providerFactory = countProvider(mobEschanCrab_288_013 to 1, mobEschanWasp_288_012 to 1),
            ),
            makeMonsterArea(
                center = Vector3f(x=367.77f,y=0.18f,z=-163.20f),
                providerFactory = countProvider(mobEschanTarichuk_288_015 to 2, mobEschanBugard_288_016 to 1),
            ),
            makeDummyArea(
                center = Vector3f(x=-323.94f,y=6.99f,z=-230.33f),
                providerFactory = countProvider(mobTrainingDummy_288_500 to 1),
            )
        )

        val aziDahakaArea = makeMonsterArea(
            center = Vector3f(x=-0.91f,y=0.00f,z=39.25f),
            providerFactory = countProvider(mobAziDahaka_288_050 to 1),
            maxActive = 1,
            spawnDelay = 1.minutes,
        )

        val mapMarkers = ArrayList<MapMarkerCommand>()
        mapMarkers += MapMarkerCommand(worldSpacePosition = aziDahakaArea.center, uiElementIndex = 139, uiElementGroup = Font.FontShp.elementName)

        val vorsealRewards = mapOf(
            mobWepwawet_288_100 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.vit],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.int],
                EschaDifficulty.S3 to EschaZiTahVorseals.tripleAttackVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.vit],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.int],
            )),
            mobAglaophotis_288_105 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.str],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.mnd],
                EschaDifficulty.S3 to EschaZiTahVorseals.storeTpVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.str],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.mnd],
            )),
            mobVidala_288_110 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.str],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.agi],
                EschaDifficulty.S3 to EschaZiTahVorseals.movementSpeedVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.str],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.agi],
            )),
            mobGestalt_288_115 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.maxMp],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.int],
                EschaDifficulty.S3 to EschaZiTahVorseals.fastCastVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.maxMp],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.int],
            )),
            mobRevetaur_288_120 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.str],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.dex],
                EschaDifficulty.S3 to EschaZiTahVorseals.magicAttackBonusVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.str],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.dex],
            )),
            mobTangataManu_288_125 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.mnd],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.agi],
                EschaDifficulty.S3 to EschaZiTahVorseals.curePotencyVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.mnd],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.agi],
            )),
            mobGulltop_288_130 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.vit],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.maxMp],
                EschaDifficulty.S3 to EschaZiTahVorseals.evasionVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.vit],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.maxMp],
            )),
            mobVyala_288_135 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.dex],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.int],
                EschaDifficulty.S3 to EschaZiTahVorseals.criticalHitRateVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.dex],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.int],
            )),
            mobAngrboda_288_140 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.maxHp],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.maxMp],
                EschaDifficulty.S3 to EschaZiTahVorseals.magicBurstDamageVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.maxHp],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.maxMp],
            )),
            mobCunnast_288_145 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.agi],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.dex],
                EschaDifficulty.S3 to EschaZiTahVorseals.weaponSkillDamageVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.agi],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.dex],
            )),
            mobFerrodon_288_150 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.maxHp],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.vit],
                EschaDifficulty.S3 to EschaZiTahVorseals.subtleBlowVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.maxHp],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.vit],
            )),
            mobLustfulLydia_288_155 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.mnd],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.maxHp],
                EschaDifficulty.S3 to EschaZiTahVorseals.resistStatusVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.mnd],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.maxHp],
            )),
            mobIonos_288_200 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.str],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.dex],
                EschaDifficulty.S3 to EschaZiTahVorseals.hasteVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.vit],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.maxHp],
            )),
            mobNosoi_288_205 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.dex],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.vit],
                EschaDifficulty.S3 to EschaZiTahVorseals.regenVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.agi],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.maxMp],
            )),
            mobUmdhlebi_288_210 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.vit],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.agi],
                EschaDifficulty.S3 to EschaZiTahVorseals.skillChainDamageVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.int],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.maxHp],
            )),
            mobSensualSandy_288_215 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.agi],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.int],
                EschaDifficulty.S3 to EschaZiTahVorseals.conserveTpVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.mnd],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.maxMp],
            )),
            mobBrittlis_288_220 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.int],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.mnd],
                EschaDifficulty.S3 to EschaZiTahVorseals.conserveMpVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.str],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.maxHp],
            )),
            mobKamohoalii_288_225 to EschaVorsealReward(mapOf(
                EschaDifficulty.S1 to EschaZiTahVorseals[CombatStat.mnd],
                EschaDifficulty.S2 to EschaZiTahVorseals[CombatStat.str],
                EschaDifficulty.S3 to EschaZiTahVorseals.criticalHitDamageVorseal.id,
                EschaDifficulty.S4 to EschaZiTahVorseals[CombatStat.dex],
                EschaDifficulty.S5 to EschaZiTahVorseals[CombatStat.maxMp],
            )),
        )

        val keyItemRewards = mapOf(
            mobFleetstalker_288_300 to keyItemFleetstalkersClaw,
            mobShockmaw_288_305 to keyItemShockmawsBlubber,
            mobUrmahlullu_288_310 to keyItemUrmahlulluArmor,
            mobBlazewing_288_315 to keyItemBlazewingsPincer,
            mobPazuzu_288_320 to keyItemPazuzusBlade,
            mobAlpluachra_288_325 to keyItemCovensDust,
            mobWrathare_288_400 to keyItemWratharesCarrot,
        )

        return EschaConfiguration(
            requiredFloorClear = 50,
            startingPosition = ZoneConfig(zoneId = 288, startPosition = Vector3f(x=-345.00f,y=1.24f,z=-180.00f)),
            entrancePosition = Vector3f(x=-344.37f, y=1.60f, z=-182f),
            portals = listOf(
                EschaPortalConfiguration(index = 0, displayName = "Portal #1", location = Vector3f(x=-342f, y=0f, z=-172f), destination = Vector3f(x=-344.70f,y=0.32f,z=-171.69f)),
                EschaPortalConfiguration(index = 1, displayName = "Portal #2", location = Vector3f(x=-303f, y=0f, z=309f), destination = Vector3f(x=-304.61f,y=0.31f,z=308.32f)),
                EschaPortalConfiguration(index = 2, displayName = "Portal #3", location = Vector3f(x=-238.22f,y=0.56f,z=13.71f), destination = Vector3f(x=-238.22f,y=0.56f,z=15.71f)),
                EschaPortalConfiguration(index = 3, displayName = "Portal #4", location = Vector3f(x=-48f,y=0.15f,z=34f), destination = Vector3f(x=-44f,y=0.05f,z=35f)),
                EschaPortalConfiguration(index = 4, displayName = "Portal #5", location = Vector3f(x=-250.00f,y=0.28f,z=-386.00f), destination = Vector3f(x=-250.00f,y=0.22f,z=-389.00f)),
                EschaPortalConfiguration(index = 5, displayName = "Portal #6", location = Vector3f(x=423.66f,y=0.41f,z=-187.20f), destination = Vector3f(x=424.80f,y=0.70f,z=-190.52f)),
            ),
            bindingAreas = basicMonsterAreas + listOf(
                aziDahakaArea,
                makeT1BossArea(),
                makeT2BossArea(),
                makeT3BossArea(),
                makeT4BossArea(),
            ),
            vorsealConfiguration = EschaZiTahVorseals.getConfiguration(),
            vorsealRewards = vorsealRewards,
            staticBoundaries = listOf(
                PushWall(center = Vector3f(x=-323f,y=0f,z=336f), tracking = true, direction = Vector3f.South),
                PushWall(center = Vector3f(x=93.04f,y=0.32f,z=-432.49f), direction = Vector3f.West, effectRange = 10f),
                PushWall(center = Vector3f(x=-7.65f,y=2.46f,z=-341.23f), direction = Vector3f.West, effectRange = 10f),
                PushWall(center = Vector3f(x=249.12f,y=0.64f,z=-168.28f), tracking = true, direction = Vector3f.NorthWest, effectRange = 15f),
                PushWall(center = Vector3f(x=441.62f,y=0.00f,z=-240.50f), tracking = true, direction = Vector3f.North, effectRange = 15f),
            ),
            entityFactory = { listOf(
                ZiTahRegisterNpc(),
            )},
            extraMapMarkers = mapMarkers,
            keyItemDropTable = keyItemRewards,
        )
    }

    fun getAll(): Map<Int, EschaConfiguration> = configurations

    operator fun get(value: Int): EschaConfiguration {
        return configurations[value] ?: throw IllegalStateException("Undefined eschan configuration: $value")
    }

    private fun difficultyTextProvider(monsterId: MonsterId, difficulty: EschaDifficulty, eschaZone: Int): String {
        val configuration = EschaConfigurations[eschaZone]

        val rewards = configuration.vorsealRewards[monsterId] ?: return ""
        val difficultyReward = rewards.vorseals[difficulty] ?: return ""

        val vorseal = configuration.vorsealConfiguration[difficultyReward]
        return " ${ShiftJis.leftRoundedBracket}${vorseal.displayName}${ShiftJis.rightRoundedBracket}"
    }

    private fun makeMonsterArea(
        center: Vector3f,
        providerFactory: MonsterProviderFactory,
        maxActive: Int = 8,
        spawnDelay: Duration = 15.seconds,
    ): EschaMonsterAreaConfiguration {
        return EschaMonsterAreaConfiguration(
            center = center,
            monsterSpawnerDefinition = MonsterSpawnerDefinition(
                spawnArea = SpawnArea(center, Vector3f(20f, 0f, 20f)),
                maxMonsters = maxActive,
                spawnDelay = Fps.toFrames(spawnDelay),
                providerFactory = providerFactory,
            ),
            pathingSettings = PathingSettings(center, radius = 40f),
            boundaries = listOf(
                EncompassingSphere(center, radius = 40f, playerHasEnmity)
            ),
        )
    }

    private fun makeDummyArea(center: Vector3f, providerFactory: MonsterProviderFactory): EschaMonsterAreaConfiguration {
        return EschaMonsterAreaConfiguration(
            center = center,
            monsterSpawnerDefinition = MonsterSpawnerDefinition(
                spawnArea = SpawnArea(center, Vector3f(1f, 0f, 1f)),
                maxMonsters = 1,
                spawnDelay = Fps.toFrames(1.seconds),
                providerFactory = providerFactory,
            ),
            pathingSettings = PathingSettings(center, radius = 5f),
            boundaries = emptyList(),
        )
    }

    private fun makeT1BossArea(): EschaBindingArea {
        return makeBossArea(model = ClassicBossSpawnerModel, position = Vector3f(x=-236f,y=0f,z=41f), bossDefinitions = listOf(
            BossDefinition(bossMonsterId = mobWepwawet_288_100, requiredItemIds = mapOf(9076 to 3)),
            BossDefinition(bossMonsterId = mobAglaophotis_288_105, requiredItemIds = mapOf(9076 to 3)),
            BossDefinition(bossMonsterId = mobVidala_288_110, requiredItemIds = mapOf(9076 to 3)),
            BossDefinition(bossMonsterId = mobGestalt_288_115, requiredItemIds = mapOf(9076 to 3)),
            BossDefinition(bossMonsterId = mobRevetaur_288_120, requiredItemIds = mapOf(9076 to 3)),
            BossDefinition(bossMonsterId = mobTangataManu_288_125, requiredItemIds = mapOf(9076 to 3)),
            BossDefinition(bossMonsterId = mobGulltop_288_130, requiredItemIds = mapOf(9076 to 3)),
            BossDefinition(bossMonsterId = mobVyala_288_135, requiredItemIds = mapOf(9076 to 3)),
            BossDefinition(bossMonsterId = mobAngrboda_288_140, requiredItemIds = mapOf(9076 to 3)),
            BossDefinition(bossMonsterId = mobCunnast_288_145, requiredItemIds = mapOf(9076 to 3)),
            BossDefinition(bossMonsterId = mobFerrodon_288_150, requiredItemIds = mapOf(9076 to 3)),
            BossDefinition(bossMonsterId = mobLustfulLydia_288_155, requiredItemIds = mapOf(9076 to 3)),
        ))
    }

    private fun makeT2BossArea(): EschaBindingArea {
        return makeBossArea(model = FreshBossSpawnerModel, position = Vector3f(x=-222.00f,y=0.32f,z=-381.00f), bossDefinitions = listOf(
            BossDefinition(mobIonos_288_200, requiredItemIds = mapOf(9076 to 15)),
            BossDefinition(mobNosoi_288_205, requiredItemIds = mapOf(9076 to 15)),
            BossDefinition(mobUmdhlebi_288_210, requiredItemIds = mapOf(9076 to 15)),
            BossDefinition(mobSensualSandy_288_215, requiredItemIds = mapOf(9076 to 15)),
            BossDefinition(mobBrittlis_288_220, requiredItemIds = mapOf(9076 to 15)),
            BossDefinition(mobKamohoalii_288_225, requiredItemIds = mapOf(9076 to 15)),
        ))
    }

    private fun makeT3BossArea(): EschaBindingArea {
        return makeBossArea(model = BlueBossSpawnerModel, position = Vector3f(x=435.59f,y=0.17f,z=-207.12f), difficultyModes = emptyList(), bossDefinitions = listOf(
            BossDefinition(mobFleetstalker_288_300, requiredItemIds = mapOf(itemAshweed_9078 to 5)),
            BossDefinition(mobShockmaw_288_305, requiredItemIds = mapOf(itemAshweed_9078 to 5)),
            BossDefinition(mobUrmahlullu_288_310, requiredItemIds = mapOf(itemAshweed_9078 to 5)),
            BossDefinition(mobBlazewing_288_315, requiredItemIds = mapOf(itemAshweed_9078 to 5)),
            BossDefinition(mobPazuzu_288_320, requiredItemIds = mapOf(itemAshweed_9078 to 5)),
            BossDefinition(mobAlpluachra_288_325, requiredItemIds = mapOf(itemAshweed_9078 to 5)),
        ))
    }

    private fun makeT4BossArea(): EschaBindingArea {
        return makeBossArea(model = StarryBossSpawnerModel, position = Vector3f(x=625.31f,y=0.86f,z=-169.68f), difficultyModes = emptyList(), hidden = true, bossDefinitions = listOf(
            BossDefinition(mobWrathare_288_400, requiredKeyItemIds = mapOf(
//                keyItemFleetstalkersClaw to 1,
//                keyItemShockmawsBlubber to 1,
//                keyItemUrmahlulluArmor to 1,
//                keyItemBlazewingsPincer to 1,
//                keyItemCovensDust to 1,
//                keyItemPazuzusBlade to 1,
            )),
        ))
    }

    private fun makeBossArea(
        model: BossSpawnerModel = FreshBossSpawnerModel,
        position: Vector3f,
        hidden: Boolean = false,
        bossDefinitions: List<BossDefinition>,
        difficultyModes: List<EschaDifficulty> = EschaDifficulty.values().toList(),
    ): EschaBindingArea {
        val bossSpawner = BossSpawnerDefinition(
            consumptionMode = BossSpawnerConsumptionMode.OnDefeat,
            position = position,
            model = model,
            difficultyModes = difficultyModes,
            difficultyTextProvider = { monsterId, eschaDifficulty ->  difficultyTextProvider(monsterId, eschaDifficulty, EschaZiTah) },
            bossDefinitions = bossDefinitions
        )

        return EschaBossAreaConfiguration(
            center = position,
            bossSpawnerDefinition = bossSpawner,
            pathingSettings = PathingSettings(root = position, radius = 40f),
            boundaries = listOf(EncompassingSphere(position, radius = 40f, playerHasEnmity)),
            hidden = hidden,
        )

    }

}
