package xim.poc.game.configuration

import xim.math.Vector3f
import xim.poc.SceneManager
import xim.poc.WanderingController
import xim.poc.game.*
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.SpawnerChestDefinition
import xim.poc.game.event.InitialActorState
import xim.util.FrameTimer
import xim.util.PI_f
import xim.util.RandHelper.rand
import kotlin.time.Duration.Companion.seconds

data class SpawnArea(val position: Vector3f, val size: Vector3f)

fun interface MonsterProviderFactory {
    fun getMonsterProvider(): MonsterProvider

    companion object {
        fun from(table: WeightedTable<MonsterDefinition>): MonsterProviderFactory {
            return MonsterProviderFactory { MonsterProvider { table.getRandom() } }
        }
    }
}

fun interface MonsterProvider {
    fun nextMonster(): MonsterDefinition
}

data class MonsterSpawnerDefinition(
    val spawnArea: SpawnArea,
    val providerFactory: MonsterProviderFactory,
    val maxMonsters: Int,
    val spawnDelay: Float,
    val maxSpawnCount: Int? = null,
    val chestDefinition: SpawnerChestDefinition? = null,
    val activeRange: Float? = null,
)

class MonsterSlot {
    var spawnDelay = 0f
    var monster: ActorPromise? = null
    var wanderDelay = FrameTimer(45.seconds)

    init {
        resetWanderDelay()
    }

    fun resetWanderDelay() {
        wanderDelay.resetRandom(lowerBound = 5.seconds)
    }

    fun getMonsterDefinition(): MonsterDefinition? {
        return MonsterDefinitions[monster?.resolveIfReady()?.monsterId]
    }

}

class MonsterSpawnerInstance(val definition: MonsterSpawnerDefinition): FloorEntity {

    private val slots = Array(definition.maxMonsters) { MonsterSlot() }
    private val provider = definition.providerFactory.getMonsterProvider()

    private var totalSpawned = 0
    private var totalDefeated = 0

    override fun update(elapsedFrames: Float) {
        refreshMonsters(elapsedFrames)
    }

    override fun cleanup() {
        slots.mapNotNull { it.monster }.forEach { it.onReady { actorState -> GameEngine.submitDeleteActor(actorState.id) } }
        slots.forEach { it.monster = null }
    }

    fun getTotalDefeated(): Int {
        return totalDefeated
    }

    fun getProgress(): Pair<Int, Int>? {
        if (definition.maxSpawnCount == null) { return null }
        return Pair(totalDefeated, definition.maxSpawnCount)
    }

    fun hasDefeatedAllMonsters(): Boolean {
        val progress = getProgress() ?: return false
        return progress.first == progress.second
    }

    private fun refreshMonsters(elapsedFrames: Float) {
        val spawnerIsActive = isActive()

        for (slot in slots) {
            val currentMonster = slot.monster

            // Empty slot - waiting for spawn delay
            if (currentMonster == null) {
                slot.spawnDelay -= elapsedFrames
                if (slot.spawnDelay < 0f && slot.monster == null) { spawnMonster(slot) }
                continue
            }

            // Monster was spawned & is now obsolete
            if (currentMonster.isObsolete()) {
                totalDefeated += 1
                slot.monster = null
                slot.spawnDelay = definition.spawnDelay
                continue
            }

            val monsterState = currentMonster.resolveIfReady() ?: continue

            if (monsterState.isIdle()) {
                monsterState.disabled = !spawnerIsActive
            } else {
                maybeWander(monsterState, slot, elapsedFrames)
            }
        }
    }

    private fun spawnMonster(slot: MonsterSlot) {
        if (definition.maxSpawnCount != null && totalSpawned >= definition.maxSpawnCount) { return }

        val monsterDefinition = provider.nextMonster()
        if (monsterDefinition.notoriousMonster && slots.any { monsterDefinition.id == it.getMonsterDefinition()?.id }) { return }

        totalSpawned += 1

       slot.monster = GameState.getGameMode().spawnMonster(monsterDefinition.id, InitialActorState(
            monsterId = monsterDefinition.id,
            name = monsterDefinition.name,
            type = ActorType.Enemy,
            position = getRandomSpawnPosition(monsterDefinition),
            modelLook = monsterDefinition.look,
            rotation = 2 * PI_f * rand(),
            targetSize = monsterDefinition.targetSize,
            autoAttackRange = monsterDefinition.autoAttackRange,
            scale = monsterDefinition.customModelSettings.scale,
            movementController = monsterDefinition.movementControllerFn.invoke(),
            behaviorController = monsterDefinition.behaviorId,
            appearanceState = monsterDefinition.baseAppearanceState,
            staticPosition = monsterDefinition.staticPosition,
            facesTarget = monsterDefinition.facesTarget,
            targetable = monsterDefinition.targetable,
        )).onReady {
            monsterDefinition.onSpawn?.invoke(it)
        }
    }

    private fun maybeWander(monsterState: ActorState, slot: MonsterSlot, elapsedFrames: Float) {
        val movementController = monsterState.movementController
        if (movementController !is WanderingController) { return }

        val definition = slot.getMonsterDefinition() ?: return

        if (!movementController.isWandering()) {
            slot.wanderDelay.update(elapsedFrames)
        }

        if (slot.wanderDelay.isReady()) {
            val wanderPosition = GameState.getGameMode().getWanderPosition(monsterState, getRandomSpawnPosition(definition))
            movementController.setWanderDestination(wanderPosition)
            slot.resetWanderDelay()
        }
    }

    private fun getRandomSpawnPosition(monsterDefinition: MonsterDefinition): Vector3f {
        val size = definition.spawnArea.size

        val position = definition.spawnArea.position + Vector3f(size.x * rand(), size.y * rand(), size.z * rand())
        if (monsterDefinition.staticPosition) { return position }
        position.y -= 5f

        return SceneManager.getCurrentScene().getNearestFloorPosition(position) ?: position
    }

    private fun isActive(): Boolean {
        if (definition.activeRange == null) { return true }
        val player = ActorStateManager.player()
        return Vector3f.distance(player.position, definition.spawnArea.position) <= definition.activeRange
    }

}