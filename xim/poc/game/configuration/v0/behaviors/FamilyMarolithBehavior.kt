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

class FamilyMarolithBehavior(actorState: ActorState): V0MonsterController(actorState) {

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actorState.isDead()) {
            actorState.appearanceState = if (actorState.isEngaged()) { 0 } else { 1 }
        }

        return super.update(elapsedFrames)
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return if (actorState.appearanceState == 0) { ActorCollisionType.Actor } else { ActorCollisionType.Object }
    }

}

class FamilyMarolithMovementBehavior: ActorController {

    private val delegate = DefaultEnemyController()

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        val actor = ActorManager.getIfPresent(actorState.id)
        if (actor != null && actorState.appearanceState == 1) {
            actor.actorModel?.lockFacing(Fps.toFrames(1.5.seconds))
            actor.actorModel?.lockMovement(Fps.toFrames(1.5.seconds))
        }

        return if (actorState.appearanceState == 0) { delegate.getVelocity(actorState, elapsedFrames) } else { Vector3f.ZERO }
    }

}