package xim.poc.game.configuration.assetviewer

import xim.math.Vector3f
import xim.poc.game.configuration.*
import xim.util.Fps

object EastSaru {

    const val zoneId = 116

    private val varietyTable = WeightedTable.uniform(
        MonsterDefinitions[MonsterId(1)],
        MonsterDefinitions[MonsterId(2)],
        MonsterDefinitions[MonsterId(3)],
        MonsterDefinitions[MonsterId(4)],
        MonsterDefinitions[MonsterId(5)],
        MonsterDefinitions[MonsterId(6)],
    )

    private val spawner = MonsterSpawnerDefinition(
        spawnArea = SpawnArea(position = Vector3f(-76f, -5f, -520f), size = Vector3f(20f, 0f, 20f)),
        spawnDelay = Fps.secondsToFrames(10f),
        maxMonsters = 8,
        providerFactory = MonsterProviderFactory.from(varietyTable),
    )

    private val boxSpawner = MonsterSpawnerDefinition(
        spawnArea = SpawnArea(position = Vector3f(x = -99.91f, y = -5.00f, z = -528.85f), size = Vector3f(1f, 0f, 1f)),
        spawnDelay = Fps.secondsToFrames(10f),
        maxMonsters = 3,
        providerFactory = MonsterProviderFactory.from(WeightedTable.single(MonsterDefinitions[MonsterId(10)])),
    )

    private val elemSpawner = MonsterSpawnerDefinition(
        spawnArea = SpawnArea(position = Vector3f(-76f, -5f, -520f), size = Vector3f(0f, 0f, 0f)),
        spawnDelay = Fps.secondsToFrames(10f),
        maxMonsters = 1,
        providerFactory = MonsterProviderFactory.from(
            WeightedTable.uniform(MonsterDefinitions[MonsterId(7)], MonsterDefinitions[MonsterId(8)], MonsterDefinitions[MonsterId(9)]
        )),
    )

    val zoneLogic = ZoneLogic(
        monsterSpawnerDefinitions = listOf(spawner, elemSpawner, boxSpawner),
        gatheringPoints = emptyList(),
    )

}