package xim.poc.game.configuration.assetviewer

import xim.poc.ActorId
import xim.poc.SceneManager
import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.actor.components.getDoorState
import xim.poc.game.actor.components.getElevatorState
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.resource.DatId
import xim.util.FrameTimer
import xim.util.TriggerTimer
import kotlin.time.Duration.Companion.seconds

class ElevatorSwitch(val elevatorId: DatId): NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        val elevatorPromise = SceneManager.getCurrentScene().getNpc(elevatorId)
        val elevator = elevatorPromise?.resolveIfReady() ?: return

        val task = AssetViewer.getTask(elevator.id, SwitchElevatorTask::class)
        if (task != null && !task.isComplete()) { return }

        AssetViewer.addTask(elevator.id, SwitchElevatorTask(npcId, elevator))
    }

}

private class SwitchElevatorTask(val switchId: ActorId, val elevator: ActorState): NpcTask {

    private val switchTimer = TriggerTimer(0.seconds)
    private val elevatorTimer = TriggerTimer(1.5.seconds)
    private val totalTimer = FrameTimer(9.5.seconds)

    override fun update(elapsedFrames: Float) {
        switchTimer.update(elapsedFrames).triggerIfReady(this::triggerSwitch)
        elevatorTimer.update(elapsedFrames).triggerIfReady(this::toggleElevator)
        totalTimer.update(elapsedFrames)
    }

    override fun isComplete(): Boolean {
        return totalTimer.isReady()
    }

    private fun triggerSwitch() {
        ActorStateManager[switchId]?.getDoorState()?.open(openInterval = 1.seconds)
    }

    private fun toggleElevator() {
        elevator.getElevatorState()?.run()
    }

}