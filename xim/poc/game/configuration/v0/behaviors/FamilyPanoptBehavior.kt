package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.ActorController
import xim.poc.ActorManager
import xim.poc.DefaultEnemyController
import xim.poc.game.ActorState
import xim.poc.game.configuration.ActorCollisionType
import xim.poc.game.event.Event
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

enum class PanoptState {
    RedEye,
    GreenEye,
}

open class FamilyPanoptBehavior(actorState: ActorState): V0MonsterController(actorState) {

    var state = PanoptState.RedEye

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actorState.isDead()) {
            actorState.appearanceState = getDesiredAppearanceState()
        }

        return super.update(elapsedFrames)
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return if (actorState.appearanceState <= 1) { ActorCollisionType.Actor } else { ActorCollisionType.Object }
    }

    private fun getDesiredAppearanceState(): Int {
        return if (actorState.isEngaged()) {
            when (state) {
                PanoptState.RedEye -> 0
                PanoptState.GreenEye -> 1
            }
        } else {
            when (state) {
                PanoptState.RedEye -> 2
                PanoptState.GreenEye -> 3
            }
        }
    }

}

class FamilyPanoptMovementBehavior: ActorController {

    private val delegate = DefaultEnemyController()

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        val actor = ActorManager.getIfPresent(actorState.id)
        if (actor != null && actorState.appearanceState > 1) {
            actor.actorModel?.lockFacing(Fps.toFrames(1.5.seconds))
            actor.actorModel?.lockMovement(Fps.toFrames(1.5.seconds))
        }

        return if (actorState.appearanceState <= 1) { delegate.getVelocity(actorState, elapsedFrames) } else { Vector3f.ZERO }
    }

}