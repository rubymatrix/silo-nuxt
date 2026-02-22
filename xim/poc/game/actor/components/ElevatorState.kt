package xim.poc.game.actor.components

import xim.poc.SceneManager
import xim.poc.game.ActorState
import xim.poc.game.ActorStateComponent
import xim.poc.game.ComponentUpdateResult
import xim.resource.DatId
import xim.resource.ElevatorSettings
import xim.util.Fps.toFrames
import xim.util.interpolate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class ElevatorStatus {
    IdleTop,
    IdleBottom,
    Descending,
    Ascending,
}

data class ElevatorConfiguration(
    val elevatorId: DatId,
    val activeDuration: Duration,
    val baseStatus: ElevatorStatus = ElevatorStatus.IdleBottom,
    val topDoorId: DatId,
    val bottomDoorIds: List<DatId>,
    val runningDoorIds: List<DatId> = emptyList(),
    val autoRunInterval: Duration? = 5.seconds,
)

class ElevatorState(val configuration: ElevatorConfiguration, val elevatorSettings: ElevatorSettings): ActorStateComponent {

    var currentStatus = configuration.baseStatus
        private set

    private var stateElapsedFrames = 0f
    private val duration = toFrames(configuration.activeDuration)

    override fun update(actorState: ActorState, elapsedFrames: Float): ComponentUpdateResult {
        stateElapsedFrames += elapsedFrames

        actorState.position.y = when (currentStatus) {
            ElevatorStatus.IdleTop -> handleIdleTop()
            ElevatorStatus.IdleBottom -> handleIdleBottom()
            ElevatorStatus.Descending -> handleDescending()
            ElevatorStatus.Ascending -> handleAscending()
        }

        return ComponentUpdateResult()
    }

    fun run() {
        when(currentStatus) {
            ElevatorStatus.IdleTop -> changeStatus(ElevatorStatus.Descending)
            ElevatorStatus.IdleBottom -> changeStatus(ElevatorStatus.Ascending)
            else -> {}
        }
    }

    private fun handleIdleTop(): Float {
        manageDoor(configuration.topDoorId, open = true)
        configuration.bottomDoorIds.forEach { manageDoor(it, open = false) }

        if (configuration.autoRunInterval != null && stateElapsedFrames > toFrames(configuration.autoRunInterval)) { changeStatus(ElevatorStatus.Descending) }
        return elevatorSettings.topPosition
    }

    private fun handleIdleBottom(): Float {
        manageDoor(configuration.topDoorId, open = false)
        configuration.bottomDoorIds.forEach { manageDoor(it, open = true) }

        if (configuration.autoRunInterval != null && stateElapsedFrames > toFrames(configuration.autoRunInterval)) { changeStatus(ElevatorStatus.Ascending) }
        return elevatorSettings.bottomPosition
    }

    private fun handleDescending(): Float {
        manageDoor(configuration.topDoorId, open = false)
        configuration.bottomDoorIds.forEach { manageDoor(it, open = false) }
        configuration.runningDoorIds.forEach { manageDoor(it, open = false) }

        val t = (stateElapsedFrames / duration).coerceIn(0f, 1f)
        if (t == 1f) { changeStatus(ElevatorStatus.IdleBottom) }
        return elevatorSettings.topPosition.interpolate(elevatorSettings.bottomPosition, t)
    }

    private fun handleAscending(): Float {
        manageDoor(configuration.topDoorId, open = false)
        configuration.bottomDoorIds.forEach { manageDoor(it, open = false) }
        configuration.runningDoorIds.forEach { manageDoor(it, open = true) }

        val t = (stateElapsedFrames / duration).coerceIn(0f, 1f)
        if (t == 1f) { changeStatus(ElevatorStatus.IdleTop) }
        return elevatorSettings.bottomPosition.interpolate(elevatorSettings.topPosition, t)
    }

    private fun changeStatus(status: ElevatorStatus) {
        currentStatus = status
        stateElapsedFrames = 0f
    }

    private fun manageDoor(doorId: DatId?, open: Boolean) {
        doorId ?: return

        val doorPromise = SceneManager.getCurrentScene().getNpc(doorId)
        if (doorPromise == null) {
            println("Couldn't find door :( $doorId")
            return
        }

        doorPromise.onReady {
            val doorState = it.getDoorState()
            if (open) { doorState.open(openInterval = null) } else { doorState.close() }
        }
    }

}

fun ActorState.getElevatorState(): ElevatorState? {
    return getComponentAs(ElevatorState::class)
}