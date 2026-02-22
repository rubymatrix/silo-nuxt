package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.SceneManager
import xim.poc.game.ActorPromise
import xim.poc.game.ActorState
import xim.poc.game.configuration.ActorCollisionType
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.constants.mobDuskprowlers_33
import xim.poc.game.configuration.v0.constants.mobLeafdancerTwitherym_32
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.configuration.v0.tower.TowerConfiguration.countProvider
import xim.poc.game.event.DoorOpenEvent
import xim.poc.game.event.Event
import xim.resource.DatId
import xim.util.RandHelper

class MobKnottedRootsBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val doorIds = listOf(DatId("_7k0"), DatId("_7k1"))

    private val monsterProvider = countProvider(listOf(
        mobLeafdancerTwitherym_32 to 1,
        mobDuskprowlers_33 to 1,
    )).getMonsterProvider()

    private var nextSpawnThreshold = 1.00f
    private val spawned = ArrayList<ActorPromise>()

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actorState.isDead() && actorState.getHpp() < nextSpawnThreshold) {
            nextSpawnThreshold -= 0.20f
            spawnMonsters(1)
        }

        return super.update(elapsedFrames)
    }

    override fun onDefeated(): List<Event> {
        for (promise in spawned) { promise.onReady(GameV0Helpers::defeatActor) }
        return doorIds.map { DoorOpenEvent(sourceId = null, doorId = it) }
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.None
    }

    private fun spawnMonsters(count: Int) {
        (0 until count).map { spawnMonster() }
    }

    private fun spawnMonster() {
        val next = monsterProvider.nextMonster()
        spawned += V0MonsterHelper.spawnMonster(
            monsterDefinition = next,
            position = getRandomSpawnPosition(),
        ).onReady { it.syncEnmity(actorState) }
    }

    private fun getRandomSpawnPosition(): Vector3f {
        val basePosition = actorState.position + Vector3f(0f, -5f, -4f)
        val position = basePosition + Vector3f(2f * RandHelper.rand(), 0f, 2f * RandHelper.rand())
        return SceneManager.getCurrentScene().getNearestFloorPosition(position) ?: position
    }

}