package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.ActorManager
import xim.poc.game.*
import xim.poc.game.configuration.constants.mskillEald2WarpIn_732
import xim.poc.game.configuration.constants.mskillEald2WarpOut_733
import xim.poc.game.configuration.v0.GameV0
import xim.util.FrameTimer
import kotlin.time.Duration

sealed interface WarpDestination {
    fun getDestination(): Vector3f
}

class FixedWarpDestination(val destination: Vector3f): WarpDestination {
    override fun getDestination(): Vector3f {
        return destination
    }
}

class TargetWarpDestination(val actorState: ActorState, val distanceFromTarget: Float): WarpDestination {
    override fun getDestination(): Vector3f {
        val target = ActorStateManager[actorState.getTargetId()] ?: return actorState.position
        val navigator = GameV0.getNavigator() ?: return actorState.position
        val desiredPosition = target.position.withRandomHorizontalOffset(distanceFromTarget)
        return navigator.nearestGridPoint(desiredPosition)
    }
}

private enum class WarpState {
    None,
    WarpingIn,
    WarpInComplete,
    WarpingOut,
    WarpOutComplete,
}

class WarpStateMachine<T>(
    val actorState: ActorState,
    val destination: WarpDestination,
    val invisibleTime: Duration,
    val warpContext: T,
) {

    private var currentState = WarpState.None
    private val invisibleTimer = FrameTimer(invisibleTime)

    fun update(elapsedFrames: Float) {
        when (currentState) {
            WarpState.None -> initiateWarpIn()
            WarpState.WarpingIn -> {}
            WarpState.WarpInComplete -> initiateWarpOut(elapsedFrames)
            WarpState.WarpingOut -> {}
            WarpState.WarpOutComplete -> {}
        }
    }

    fun isComplete(): Boolean {
        return currentState == WarpState.WarpOutComplete
    }

    private fun initiateWarpIn() {
        val context = AttackContext()

        AttackContext.compose(context) {
            currentState = WarpState.WarpInComplete
        }

        EffectDisplayer.displaySkill(
            skillId = mskillEald2WarpIn_732,
            castingContext = null,
            sourceId = actorState.id,
            primaryTargetId = actorState.id,
            allTargetIds = listOf(actorState.id),
            actionContext = AttackContexts.single(actorState.id, context),
        )

        currentState = WarpState.WarpingIn
    }

    private fun initiateWarpOut(elapsedFrames: Float) {
        invisibleTimer.update(elapsedFrames)
        if (!invisibleTimer.isReady()) { return }

        actorState.position.copyFrom(destination.getDestination())
        faceTarget()

        val context = AttackContext()

        AttackContext.compose(context) {
            currentState = WarpState.WarpOutComplete
        }

        EffectDisplayer.displaySkill(
            skillId = mskillEald2WarpOut_733,
            castingContext = null,
            sourceId = actorState.id,
            primaryTargetId = actorState.id,
            allTargetIds = listOf(actorState.id),
            actionContext = AttackContexts.single(actorState.id, context),
        )

        currentState = WarpState.WarpingOut
    }

    private fun faceTarget() {
        val target = ActorStateManager[actorState.getTargetId()] ?: return
        actorState.faceToward(target)
        ActorManager[actorState.id]?.syncFromState()
    }

}
