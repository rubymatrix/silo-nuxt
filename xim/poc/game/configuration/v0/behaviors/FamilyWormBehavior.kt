package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.ActorController
import xim.poc.WanderingController
import xim.poc.game.ActorState
import xim.poc.game.configuration.ActorCollisionType
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.seconds

private enum class WormState {
    None,
    Descending,
    Ascending,
}

class FamilyWormBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private var wormState = WormState.None
    private val stateTimer = FrameTimer(5.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.movementController is FamilyWormMovementController) {
            when (wormState) {
                WormState.None -> handleWander(actorState.movementController)
                WormState.Descending -> handleDescend(elapsedFrames, actorState.movementController)
                WormState.Ascending -> handleAscend(elapsedFrames, actorState.movementController)
            }
        }

        if (wormState != WormState.None) {
            return emptyList()
        }

        return super.update(elapsedFrames)
    }

    override fun wantsToCastSpell(): Boolean {
        return super.wantsToCastSpell() && actorState.isEngaged() && !autoAttackDelegate.isInAttackRange()
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.Object
    }

    override fun shouldPerformAggroCheck(): Boolean {
        return if (wormState != WormState.None) { false } else { super.shouldPerformAggroCheck() }
    }

    private fun handleWander(movementController: FamilyWormMovementController) {
        if (!movementController.isWandering()) { return }

        if (!actorState.isIdle()) {
            movementController.wanderDestination = null
            return
        }

        stateTimer.reset()
        wormState = WormState.Descending

        actorState.targetable = false
        actorState.appearanceState = 1
    }

    private fun handleDescend(elapsedFrames: Float, movementController: FamilyWormMovementController) {
        stateTimer.update(elapsedFrames)
        if (!stateTimer.isReady()) { return }

        val destination = movementController.wanderDestination
        if (destination != null) { actorState.position.copyFrom(destination) }

        stateTimer.reset(3.seconds)
        wormState = WormState.Ascending
        actorState.appearanceState = 0
    }

    private fun handleAscend(elapsedFrames: Float, movementController: FamilyWormMovementController) {
        stateTimer.update(elapsedFrames)
        if (!stateTimer.isReady()) { return }

        movementController.wanderDestination = null

        wormState = WormState.None
        actorState.targetable = true
    }

}

class FamilyWormMovementController: ActorController, WanderingController {

    var wanderDestination: Vector3f? = null

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        return Vector3f.ZERO
    }

    override fun setWanderDestination(destination: Vector3f) {
        wanderDestination = destination
    }

    override fun isWandering(): Boolean {
        return wanderDestination != null
    }


}