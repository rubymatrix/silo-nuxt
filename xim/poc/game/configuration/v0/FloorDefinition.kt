package xim.poc.game.configuration.v0

import xim.math.Vector3f
import xim.poc.EnvironmentManager
import xim.poc.SceneManager
import xim.poc.game.ActorStateManager
import xim.poc.game.GameEngine.displayName
import xim.poc.game.configuration.MonsterSpawnerDefinition
import xim.poc.game.configuration.MonsterSpawnerInstance
import xim.poc.game.configuration.v0.navigation.BattleLocationNavigator
import xim.poc.game.configuration.v0.navigation.BattleLocationPather
import xim.poc.game.configuration.v0.tower.*
import xim.poc.game.configuration.v0.zones.BattleLocation
import xim.poc.tools.ZoneConfig
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.util.Fps
import kotlin.math.atan2

class FloorDefinition(
    val monsterSpawnerDefinitions: List<MonsterSpawnerDefinition>,
    val location: BattleLocation,
    val configuration: FloorConfiguration,
) {

    companion object {

        fun fromFloor(floorNumber: Int): FloorDefinition {
            val floor = TowerConfiguration[floorNumber]
            val location = floor.battleLocation

            val spawnerChest = SpawnerChestDefinition(
                position = location.treasureChestPosition,
                rotation = getRotation(location.startingPosition.startPosition!! - location.treasureChestPosition),
                dropSlots = floor.chestTable,
                treasureChestLook = TreasureChestLook.Red,
            )

            val spawner = mutableListOf(MonsterSpawnerDefinition(
                spawnArea = location.spawnerArea,
                maxMonsters = floor.maxActive,
                maxSpawnCount = floor.maxSpawnCount,
                spawnDelay = Fps.secondsToFrames(15),
                chestDefinition = spawnerChest,
                providerFactory = floor.monsterProviderFactory,
            ))

            if (floor.obstacleConfiguration != null) {
                spawner += MonsterSpawnerDefinition(
                    spawnArea = location.spawnerArea,
                    maxMonsters = floor.obstacleConfiguration.maxActive,
                    maxSpawnCount = floor.obstacleConfiguration.maxActive,
                    spawnDelay = Fps.secondsToFrames(15),
                    providerFactory = floor.obstacleConfiguration.obstacleProviderFactory,
                )
            }

            return FloorDefinition(
                monsterSpawnerDefinitions = spawner,
                location = floor.battleLocation,
                configuration = floor,
            )
        }

        private fun getRotation(direction: Vector3f): Float {
            val horizontal = direction.normalize().withY(0f)
            if (horizontal.magnitudeSquare() < 1e-5f) { return 0f }

            val dir = horizontal.normalize()
            return -atan2(dir.z, dir.x)
        }
    }

    fun newInstance(): FloorInstance {
        return FloorInstance(this)
    }

}

class FloorInstance(val definition: FloorDefinition): ZoneLogic {

    private val navigator = BattleLocationPather.generateNavigator(definition.location.pathingSettings)

    private val monsterSpawners = ArrayList<MonsterSpawnerInstance>()
    private val treasureChests = ArrayList<SpawnerChest>()
    private val floorEntities = ArrayList<FloorEntity>()

    private var cleared = false
    private var fullCleared = false

    init {
        for (monsterSpawnerDef in definition.monsterSpawnerDefinitions) { addMonsterSpawner(monsterSpawnerDef) }

        floorEntities += FloorEntrance(definition.location.entrancePosition, definition.location.entranceLook)
        addFloorExit(definition.configuration, definition.location)

        floorEntities += definition.configuration.allies.map { FloorAlly(it, definition.location.entrancePosition) }
        floorEntities += definition.configuration.entityProvider.map { it.invoke(definition) }

        val location = definition.location

        if (location.shipRoute != null) {
            SceneManager.getCurrentScene().setShipRoute(location.shipRoute)
        }

        if (location.timeOfDay != null) {
            EnvironmentManager.setCurrentHour(location.timeOfDay.hour)
            EnvironmentManager.setCurrentMinute(location.timeOfDay.minute)
        }

        location.onSetup?.invoke()

        val playerSpells = ActorStateManager.player().getLearnedSpells()
        val newSpells = definition.configuration.blueMagicReward.filter { !playerSpells.spellIds.contains(it) }
        if (newSpells.isNotEmpty()) { ChatLog("Defeat all monsters to learn: ${newSpells.joinToString { it.displayName() }}", ChatLogColor.Info) }
    }

    override fun update(elapsedFrames: Float) {
        val playerPosition = ActorStateManager.player().position
        definition.location.boundaries.forEach { it.apply(playerPosition) }

        monsterSpawners.forEach { it.update(elapsedFrames) }
        treasureChests.forEach { it.update(elapsedFrames) }
        floorEntities.forEach { it.update(elapsedFrames) }

        val primarySpawner = monsterSpawners.firstOrNull()
        if (!cleared && primarySpawner != null && primarySpawner.getTotalDefeated() >= definition.configuration.defeatRequirement) {
            cleared = true
            definition.location.onCleared?.invoke()
            GameTower.onClearedFloor(definition.configuration.floorNumber)
        }

        if (!fullCleared && primarySpawner != null && primarySpawner.hasDefeatedAllMonsters()) {
            fullCleared = true
            GameV0Helpers.learnSpells(ActorStateManager.player(), definition.configuration.blueMagicReward)
        }

        navigator.update(elapsedFrames)
    }

    override fun cleanUp() {
        monsterSpawners.forEach { it.cleanup() }
        treasureChests.forEach { it.clear() }
        floorEntities.forEach { it.cleanup() }
    }

    override fun getCurrentNavigator(): BattleLocationNavigator {
        return navigator
    }

    override fun getEntryPosition(): ZoneConfig {
        return definition.location.startingPosition
    }

    override fun toNew(): ZoneLogic {
        return FloorInstance(definition)
    }

    private fun addMonsterSpawner(monsterSpawnerDefinition: MonsterSpawnerDefinition) {
        val instance = MonsterSpawnerInstance(monsterSpawnerDefinition)
        monsterSpawners += instance

        if (monsterSpawnerDefinition.chestDefinition != null && monsterSpawnerDefinition.chestDefinition.dropSlots.isNotEmpty()) {
            treasureChests += SpawnerChest(monsterSpawnerDefinition.chestDefinition, instance)
        }
    }

    private fun addFloorExit(configuration: FloorConfiguration, location: BattleLocation) {
        val spawner = monsterSpawners.firstOrNull() ?: return
        floorEntities += FloorExit(configuration, location, spawner)
    }

}