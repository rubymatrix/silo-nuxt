package xim.poc.game.configuration.assetviewer

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.ui.ChatLog
import xim.resource.DatId
import xim.resource.EffectRoutineInstance
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.seconds

private enum class ShipState {
    Idle,
    Arriving,
    Arrived,
    Leaving,
}

class BasicShipController(val shipId: ActorId, val animationIds: Pair<DatId, DatId> = DatId("seq0") to DatId("seq1")): NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        var task = AssetViewer.getTask(shipId, NpcShipTask::class)
        if (task != null && !task.isComplete()) { return }

        task = task ?: AssetViewer.addTask(shipId, NpcShipTask(shipId, animationIds))

        when (task.shipState) {
            ShipState.Idle -> ChatLog("The ship will arrive momentarily.")
            ShipState.Arriving -> ChatLog("The ship is arriving.")
            ShipState.Arrived -> ChatLog("The ship has arrived.")
            ShipState.Leaving -> ChatLog("The ship is leaving.")
        }
    }

}

private class NpcShipTask(val shipId: ActorId, val animationIds: Pair<DatId, DatId>): NpcTask {

    var shipState = ShipState.Idle
        private set

    private var currentRoutine: EffectRoutineInstance? = null
    private val arrivedWait = FrameTimer(10.seconds)
    private var complete = false

    override fun update(elapsedFrames: Float) {
        when (shipState) {
            ShipState.Idle -> handleIdle()
            ShipState.Arriving -> handleArriving()
            ShipState.Arrived -> handleArrived(elapsedFrames)
            ShipState.Leaving -> handleLeaving()
        }
    }

    private fun handleIdle() {
        val ship = ActorStateManager[shipId] ?: return
        ship.disabled = false

        val shipDisplay = ActorManager[shipId] ?: return
        currentRoutine = shipDisplay.playRoutine(animationIds.first) ?: return

        shipState = ShipState.Arriving
    }

    private fun handleArriving() {
        val ship = ActorStateManager[shipId] ?: return
        ship.visible = true

        if (currentRoutine?.hasCompletedAllSequences() == true) { shipState = ShipState.Arrived }
    }

    private fun handleArrived(elapsedFrames: Float) {
        arrivedWait.update(elapsedFrames)
        if (arrivedWait.isNotReady()) { return }

        val shipDisplay = ActorManager[shipId] ?: return
        currentRoutine = shipDisplay.playRoutine(animationIds.second)

        shipState = ShipState.Leaving
    }

    private fun handleLeaving() {
        if (currentRoutine?.hasCompletedAllSequences() != true) { return }

        val ship = ActorStateManager[shipId] ?: return
        ship.disabled = true
        ship.visible = false

        complete = true
    }

    override fun isComplete(): Boolean {
        return complete
    }

}