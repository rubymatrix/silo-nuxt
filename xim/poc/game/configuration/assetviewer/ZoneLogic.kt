package xim.poc.game.configuration.assetviewer

import xim.poc.game.configuration.v0.mining.GatheringConfiguration
import xim.poc.game.configuration.v0.mining.GatheringNodeSpawner
import xim.poc.game.configuration.MonsterSpawnerDefinition
import xim.poc.game.configuration.MonsterSpawnerInstance

class ZoneLogic(monsterSpawnerDefinitions: List<MonsterSpawnerDefinition>, gatheringPoints: List<GatheringConfiguration>) {

    private val monsterSpawners = monsterSpawnerDefinitions.map { MonsterSpawnerInstance(it) }
    private val gatheringPointSpawners = gatheringPoints.map { GatheringNodeSpawner(it) }

    fun setup() {
    }

    fun update(elapsed: Float) {
        gatheringPointSpawners.forEach { it.update(elapsed) }
        monsterSpawners.forEach { it.update(elapsed) }
    }

    fun cleanUp() {
        monsterSpawners.forEach { it.cleanup() }
        gatheringPointSpawners.forEach { it.clear() }
    }

}
