package xim.poc.game.configuration.v0.abyssea

import xim.math.Vector3f
import xim.poc.ModelLook
import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.configuration.MonsterId
import xim.poc.game.configuration.MonsterSpawnerDefinition
import xim.poc.game.configuration.MonsterSpawnerInstance
import xim.poc.game.configuration.SpawnArea
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.*
import xim.poc.game.configuration.v0.abyssea.AbysseaKeyItems.keyItemBloodSmearedGigasHelm
import xim.poc.game.configuration.v0.abyssea.AbysseaKeyItems.keyItemBloodiedSaberTooth
import xim.poc.game.configuration.v0.abyssea.AbysseaKeyItems.keyItemDentedGigasShield
import xim.poc.game.configuration.v0.abyssea.AbysseaKeyItems.keyItemGlitteringPixieChoker
import xim.poc.game.configuration.v0.abyssea.AbysseaKeyItems.keyItemMarbledMuttonChop
import xim.poc.game.configuration.v0.abyssea.AbysseaKeyItems.keyItemPellucidFlyEye
import xim.poc.game.configuration.v0.abyssea.AbysseaKeyItems.keyItemSeveredGigasCollar
import xim.poc.game.configuration.v0.abyssea.AbysseaKeyItems.keyItemShimmeringPixiePinion
import xim.poc.game.configuration.v0.abyssea.AbysseaKeyItems.keyItemWarpedGigasArmband
import xim.poc.game.configuration.v0.constants.*
import xim.poc.game.configuration.v0.events.KeyItemGainEvent
import xim.poc.game.configuration.v0.tower.Boundary
import xim.poc.game.configuration.v0.tower.EncompassingSphere
import xim.poc.game.configuration.v0.tower.TowerConfiguration.countProvider
import xim.poc.game.event.Event
import xim.poc.tools.ZoneConfig
import xim.resource.KeyItemId
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

object AbysseaKeyItems {
    const val keyItemMarbledMuttonChop = 1478
    const val keyItemBloodiedSaberTooth = 1479
    const val keyItemBloodSmearedGigasHelm = 1480
    const val keyItemGlitteringPixieChoker = 1481
    const val keyItemDentedGigasShield = 1482
    const val keyItemWarpedGigasArmband = 1483
    const val keyItemSeveredGigasCollar = 1484
    const val keyItemPellucidFlyEye = 1485
    const val keyItemShimmeringPixiePinion = 1486

    val laTheineKeyItems = listOf(
        keyItemMarbledMuttonChop,
        keyItemBloodiedSaberTooth,
        keyItemBloodSmearedGigasHelm,
        keyItemGlitteringPixieChoker,
        keyItemDentedGigasShield,
        keyItemWarpedGigasArmband,
        keyItemSeveredGigasCollar,
        keyItemPellucidFlyEye,
        keyItemShimmeringPixiePinion,
    )
}

class AbysseaConfiguration(
    val requiredFloorClear: Int,
    val startingPosition: ZoneConfig,
    val entrancePosition: Vector3f,
    val entranceLook: ModelLook,
    val bossSpawner: BossSpawnerDefinition,
    val questMonster: MonsterId,
    val monsterSpawners: List<MonsterSpawnerDefinition>,
    val boundaries: List<Boundary> = emptyList(),
    val decorations: List<DecorationActorConfig> = emptyList(),
    val keyItemDropTable: Map<MonsterId, KeyItemId> = emptyMap(),
    val entityFactory: () -> List<FloorEntity> = { emptyList() },
)

class AbysseaZoneInstance(val definition: AbysseaConfiguration): ZoneLogic {

    private val floorEntities = ArrayList<FloorEntity>()

    init {
        val entrance = AbysseaExitNpc(definition)
        entrance.promise.onReady { it.faceToward(definition.startingPosition.startPosition!!) }
        floorEntities += entrance

        floorEntities += definition.monsterSpawners.map { MonsterSpawnerInstance(it) }

        floorEntities += BossSpawner(definition.bossSpawner)

        floorEntities += definition.decorations.map { FloorDecoration(it) }

        floorEntities += definition.entityFactory.invoke()
    }

    override fun update(elapsedFrames: Float) {
        val playerPosition = ActorStateManager.player().position
        definition.boundaries.forEach { it.apply(playerPosition) }
        floorEntities.forEach { it.update(elapsedFrames) }
    }

    override fun cleanUp() {
        floorEntities.forEach { it.cleanup() }
    }

    override fun getEntryPosition(): ZoneConfig {
        return definition.startingPosition
    }

    override fun toNew(): ZoneLogic {
        return AbysseaZoneInstance(definition)
    }

    override fun onActorDefeated(source: ActorState, target: ActorState, context: AttackContext?): List<Event> {
        val keyItem = definition.keyItemDropTable[target.monsterId] ?: return emptyList()
        return listOf(KeyItemGainEvent(keyItemId = keyItem, quantity = 1, context = context))
    }

}

object AbysseaConfigurations {

    private val configurations = HashMap<Int, AbysseaConfiguration>()

    init {
        configurations[1] = AbysseaConfiguration(
            requiredFloorClear = 30,
            startingPosition = ZoneConfig(zoneId = 132, startPosition = Vector3f(x=333.98f,y=24.59f,z=-154.97f)),
            entrancePosition = Vector3f(x=326.51f,y=24.20f,z=-158.76f),
            entranceLook = ModelLook.npc(0x915),
            questMonster = mobHadhayosh_135_021,
            keyItemDropTable = mapOf(
                mobLaTheineLiege_135_002 to keyItemPellucidFlyEye,
                mobBabaYaga_135_004 to keyItemShimmeringPixiePinion,
                mobPantagruel_135_008 to keyItemDentedGigasShield,
                mobGrandgousier_135_010 to keyItemSeveredGigasCollar,
                mobAdamastor_135_012 to keyItemWarpedGigasArmband,
                mobTrudgingThomas_135_015 to keyItemMarbledMuttonChop,
                mobMegantereon_135_017 to keyItemBloodiedSaberTooth,
                mobCarabosse_135_005 to keyItemGlitteringPixieChoker,
                mobBriareus_135_013 to keyItemBloodSmearedGigasHelm,
            ),
            bossSpawner = BossSpawnerDefinition(
                consumptionMode = BossSpawnerConsumptionMode.OnDefeat,
                position = Vector3f(x=341.97f,y=24.12f,z=-143.55f),
                model = ClassicBossSpawnerModel,
                bossDefinitions = listOf(
                    // T1
                    BossDefinition(bossMonsterId = mobLaTheineLiege_135_002, requiredItemIds = mapOf(itemTrInsectWing_2897 to 1)),
                    BossDefinition(bossMonsterId = mobBabaYaga_135_004, requiredItemIds = mapOf(itemPiceousScale_2898 to 1)),
                    BossDefinition(bossMonsterId = mobPantagruel_135_008, requiredItemIds = mapOf(itemOversizedSock_2895 to 1)),
                    BossDefinition(bossMonsterId = mobGrandgousier_135_010, requiredItemIds = mapOf(itemMassiveArmband_2896 to 1)),
                    BossDefinition(bossMonsterId = mobAdamastor_135_012, requiredItemIds = mapOf(itemTrophyShield_2894 to 1)),
                    BossDefinition(bossMonsterId = mobTrudgingThomas_135_015, requiredItemIds = mapOf(itemRMuttonChop_2892 to 1)),
                    BossDefinition(bossMonsterId = mobMegantereon_135_017, requiredItemIds = mapOf(itemGBlkTigerFang_2893 to 1)),
                    // T2
                    BossDefinition(bossMonsterId = mobCarabosse_135_005, requiredKeyItemIds = mapOf(
                        keyItemPellucidFlyEye to 1,
                        keyItemShimmeringPixiePinion to 1,
                    )),
                    BossDefinition(bossMonsterId = mobBriareus_135_013, requiredKeyItemIds = mapOf(
                        keyItemDentedGigasShield to 1,
                        keyItemSeveredGigasCollar to 1,
                        keyItemWarpedGigasArmband to 1,
                    )),
                    // T3
                    BossDefinition(bossMonsterId = mobHadhayosh_135_021, requiredKeyItemIds = mapOf(
                        keyItemMarbledMuttonChop to 1,
                        keyItemBloodiedSaberTooth to 1,
                        keyItemBloodSmearedGigasHelm to 1,
                        keyItemGlitteringPixieChoker to 1,
                    )),
                )
            ),
            monsterSpawners = listOf(
                MonsterSpawnerDefinition(
                    spawnArea = SpawnArea(position = Vector3f(x=373.01f,y=20.47f,z=-159.81f), size = Vector3f(20f, 0f, 20f)),
                    maxMonsters = 5,
                    spawnDelay = Fps.toFrames(15.seconds),
                    providerFactory = countProvider(mobPlateauGlider_135_001 to 1),
                    activeRange = 55f,
                ),
                MonsterSpawnerDefinition(
                    spawnArea = SpawnArea(position = Vector3f(x=400.62f,y=16.00f,z=-162.18f), size = Vector3f(20f, 0f, 20f)),
                    maxMonsters = 5,
                    spawnDelay = Fps.toFrames(15.seconds),
                    providerFactory = countProvider(mobFarfadet_135_003 to 1),
                    activeRange = 55f,
                ),
                MonsterSpawnerDefinition(
                    spawnArea = SpawnArea(position = Vector3f(x=344.40f,y=24.75f,z=-9.68f), size = Vector3f(25f, 0f, 25f)),
                    maxMonsters = 5,
                    spawnDelay = Fps.toFrames(15.seconds),
                    providerFactory = countProvider(mobHadalGigas_135_007 to 1),
                    activeRange = 55f,
                ),
                MonsterSpawnerDefinition(
                    spawnArea = SpawnArea(position = Vector3f(x=344.40f,y=24.75f,z=-44.68f), size = Vector3f(25f, 0f, 25f)),
                    maxMonsters = 5,
                    spawnDelay = Fps.toFrames(15.seconds),
                    providerFactory = countProvider(mobDemersalGigas_135_009 to 1),
                    activeRange = 55f,
                ),
                MonsterSpawnerDefinition(
                    spawnArea = SpawnArea(position = Vector3f(x=344.40f,y=24.75f,z=-79.68f), size = Vector3f(25f, 0f, 25f)),
                    maxMonsters = 5,
                    spawnDelay = Fps.toFrames(15.seconds),
                    providerFactory = countProvider(mobBathyalGigas_135_011 to 1),
                    activeRange = 55f,
                ),
                MonsterSpawnerDefinition(
                    spawnArea = SpawnArea(position = Vector3f(x=430.07f,y=24.43f,z=-124.19f), size = Vector3f(20f, 0f, 20f)),
                    maxMonsters = 5,
                    spawnDelay = Fps.toFrames(15.seconds),
                    providerFactory = countProvider(mobHammeringRam_135_014 to 1),
                    activeRange = 55f,
                ),
                MonsterSpawnerDefinition(
                    spawnArea = SpawnArea(position = Vector3f(x=389.98f,y=24.28f,z=-75.62f), size = Vector3f(20f, 0f, 20f)),
                    maxMonsters = 5,
                    spawnDelay = Fps.toFrames(15.seconds),
                    providerFactory = countProvider(mobAnglerTiger_135_016 to 1),
                    activeRange = 55f,
                ),
                MonsterSpawnerDefinition(
                    spawnArea = SpawnArea(position = Vector3f(x=437.07f,y=16.00f,z=-83.57f), size = Vector3f(20f, 0f, 20f)),
                    maxMonsters = 5,
                    spawnDelay = Fps.toFrames(15.seconds),
                    providerFactory = countProvider(mobIrateSheep_135_018 to 1),
                    activeRange = 55f,
                ),
                MonsterSpawnerDefinition(
                    spawnArea = SpawnArea(position = Vector3f(x=400.26f,y=19.68f,z=-0.38f), size = Vector3f(20f, 0f, 20f)),
                    maxMonsters = 5,
                    spawnDelay = Fps.toFrames(15.seconds),
                    providerFactory = countProvider(mobCankercap_135_019 to 6, mobTopplingTuber_135_020 to 1),
                    activeRange = 55f,
                ),
                MonsterSpawnerDefinition(
                    spawnArea = SpawnArea(position = Vector3f(x=400.00f,y=15.00f,z=-60.00f), size = Vector3f(90f, 0f, 90f)),
                    maxMonsters = 1,
                    spawnDelay = Fps.toFrames(60.seconds),
                    providerFactory = countProvider(mobAkash_135_006 to 1)
                ),
                MonsterSpawnerDefinition(
                    spawnArea = SpawnArea(position = Vector3f(x=437.07f,y=16.00f,z=-83.57f), size = Vector3f(5f, 0f, 5f)),
                    maxMonsters = 1,
                    spawnDelay = Fps.toFrames(60.seconds),
                    providerFactory = countProvider(mobOvni_135_022 to 1)
                ),
            ),
            boundaries = listOf(
                EncompassingSphere(center = Vector3f(x=400.00f,y=24.00f,z=-60.00f), radius = 140f)
            ),
            decorations = listOf(
                DecorationActorConfig(ModelLook.npc(0x906), Vector3f(x=320.00f,y=24.97f,z=-140.00f)),
            ), entityFactory = { listOf(
                AbysseaBarterNpc(),
            ) }
        )
    }

    fun getAll(): Map<Int, AbysseaConfiguration> = configurations

    operator fun get(value: Int): AbysseaConfiguration {
        return configurations[value] ?: throw IllegalStateException("Abyssea ")
    }


}