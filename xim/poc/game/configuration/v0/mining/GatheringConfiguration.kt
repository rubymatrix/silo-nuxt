package xim.poc.game.configuration.v0.mining

import xim.math.Vector3f
import xim.poc.ModelLook
import xim.poc.game.*
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.event.InitialActorState
import kotlin.time.Duration.Companion.milliseconds

class GatheringNodeItem(
    val itemId: Int,
)

class GatheringConfiguration(
    val type: GatheringType,
    val positions: List<Vector3f>,
    val items: List<GatheringNodeItem>,
)

class GatheringNodeSpawner(val configuration: GatheringConfiguration): FloorEntity {

    private var promise: ActorPromise? = null

    private val positionQueue = ArrayDeque<Vector3f>()

    override fun update(elapsedFrames: Float) {
        val currentPromise = promise

        if (currentPromise == null) {
            submitCreate()
            return
        }

        val currentId = currentPromise.getIfReady() ?: return

        val currentState = ActorStateManager[currentId]
        if (currentState == null || currentState.hasBeenDeadFor(500.milliseconds)) {
            submitCreate()
        }
    }

    override fun cleanup() {
        promise.cleanup()
    }

    private fun submitCreate() {
        if (positionQueue.isEmpty()) { positionQueue += configuration.positions.shuffled() }
        val nextPosition = positionQueue.removeFirst()

        promise = GameEngine.submitCreateActorState(InitialActorState(
            name = getName(),
            position = Vector3f(nextPosition),
            modelLook = getModelLook(),
            type = ActorType.Effect,
            displayHp = true,
            components = listOf(GatheringNodeStateComponent(configuration)),
        )).onReady {
            GameV0.interactionManager.registerInteraction(it.id, MiningInteraction)
        }
    }

    private fun getName(): String {
        return when (configuration.type) {
            GatheringType.Harvesting -> "Harvesting point"
            GatheringType.Logging -> "Logging point"
            GatheringType.Mining -> "Mining point"
        }
    }

    private fun getModelLook(): ModelLook {
        return when (configuration.type) {
            GatheringType.Harvesting -> ModelLook.npc(0x976)
            GatheringType.Logging -> ModelLook.npc(0x977)
            GatheringType.Mining -> ModelLook.npc(0x978)
        }
    }

    fun clear() {
        val current = promise ?: return
        current.onReady { GameEngine.submitDeleteActor(it.id) }
    }

}
