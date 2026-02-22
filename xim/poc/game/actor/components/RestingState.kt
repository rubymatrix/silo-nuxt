package xim.poc.game.actor.components

import xim.poc.game.*
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

class RestingState(val action: ActorActionState): ActorStateComponent {

    enum class TransitionState {
        StartResting,
        Resting,
        StopResting,
        Complete,
    }

    private var state = TransitionState.StartResting
    private var transitionTime = 1f

    override fun update(actorState: ActorState, elapsedFrames: Float): ComponentUpdateResult {
        when (state) {
            TransitionState.StartResting -> {
                transitionTime -= elapsedFrames
                if (transitionTime < 0f) { state = TransitionState.Resting }
            }
            TransitionState.Resting -> { }
            TransitionState.StopResting -> {
                toggleOffSubState()
                transitionTime -= elapsedFrames
                if (transitionTime < 0f) { state = TransitionState.Complete }
            }
            TransitionState.Complete -> { }
        }

        return if (state == TransitionState.Complete) {
            actorState.clearActionState(action)
            ComponentUpdateResult(removeComponent = true)
        } else {
            ComponentUpdateResult(removeComponent = false)
        }
    }

    fun stopResting() {
        if (state != TransitionState.Resting) { return}
        state = TransitionState.StopResting
        transitionTime = Fps.toFrames(1.seconds)
    }

    private fun toggleOffSubState() {
        when (action) {
            is ActorRestingState -> action.kneeling = false
            is ActorSitChairState -> action.sitting = false
            else -> return
        }
    }

}