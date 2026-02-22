package xim.poc.game.actor.components

import xim.poc.game.ActorState
import xim.poc.game.ActorStateComponent
import xim.poc.game.ComponentUpdateResult
import xim.poc.game.event.DoorCloseEvent
import xim.util.Fps
import kotlin.time.Duration

class DoorState: ActorStateComponent {

    var open = false
        private set

    private var framesUntilAutoClose: Float? = null

    override fun update(actorState: ActorState, elapsedFrames: Float): ComponentUpdateResult {
        if (open) {
            val remaining = framesUntilAutoClose
            if (remaining != null) { framesUntilAutoClose = remaining - elapsedFrames }
        }

        val doorId = actorState.getNpcInfo()?.datId
        val events = if (doorId != null && isReadyToAutoClose()) { listOf(DoorCloseEvent(actorState.id)) } else { emptyList() }

        return ComponentUpdateResult(events = events, removeComponent = false)
    }

    fun open(openInterval: Duration?) {
        open = true
        if (openInterval != null) { framesUntilAutoClose = Fps.millisToFrames(openInterval.inWholeMilliseconds) }
    }

    fun close() {
        open = false
        framesUntilAutoClose = null
    }

    private fun isReadyToAutoClose(): Boolean {
        if (!open) { return false }
        val remaining = framesUntilAutoClose ?: return false
        return remaining <= 0f
    }

}

fun ActorState.getDoorState(): DoorState {
    return getComponentAs(DoorState::class) ?: DoorState()
}