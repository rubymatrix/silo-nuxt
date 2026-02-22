package xim.poc.game.configuration.v0.mining

import xim.poc.game.*
import xim.util.FrameTimer
import kotlin.time.Duration

class ActorGatheringComponent(
    val attempt: ActorGatheringAttempt,
    val attemptDuration: Duration,
    val actionContext: AttackContext,
): ActorStateComponent {

    private val frameTimer = FrameTimer(attemptDuration)

    override fun update(actorState: ActorState, elapsedFrames: Float): ComponentUpdateResult {
        frameTimer.update(elapsedFrames)

        return if (frameTimer.isReady()) {
            actionContext.invokeCallback()
            actorState.clearActionState(attempt)
            ComponentUpdateResult(removeComponent = true)
        } else {
            ComponentUpdateResult(removeComponent = false)
        }
    }

}
