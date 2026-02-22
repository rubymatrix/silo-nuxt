package xim.poc

import xim.math.Vector3f
import xim.poc.browser.GameKey
import xim.poc.camera.CameraReference
import xim.poc.game.*
import xim.poc.game.configuration.EventScriptRunner
import xim.poc.game.configuration.assetviewer.TrustBehaviorController
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

interface ActorController {
    fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f
}

class NoOpActorController: ActorController {
    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        return Vector3f()
    }
}

class KeyboardActorController: ActorController {

    private var autorun = false
    private val keyboard = MainTool.platformDependencies.keyboard

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        if (actorState.isPlayer() && (EventScriptRunner.isRunningScript() || UiStateHelper.movementLocked())) { return Vector3f.ZERO }

        val actorModel = ActorManager[actorState.id]?.actorModel
        if (actorModel != null && actorModel.isMovementLocked()) { return Vector3f.ZERO }

        val forwardVector = if (actorState.targetState.locked) {
            actorState.getTargetDirectionVector() ?: return Vector3f.ZERO
        } else {
            CameraReference.getInstance().getViewVector()
        }

        val leftVector = Vector3f.UP.cross(forwardVector)

        val speedUp = if (GameState.isDebugMode() && keyboard.isKeyPressedOrRepeated(GameKey.DebugSpeed)) { 10.0f } else { 1.0f }

        forwardVector.y = 0f
        val forward = forwardVector.normalize()

        leftVector.y = 0f
        val right = leftVector.normalize()

        val velocity = getDirectionFromTouchEvents(forward, right) ?: getDirectionFromButtonPresses(forward, right)
        if (velocity.magnitudeSquare() <= 1e-7) {
            return velocity
        }

        return velocity.normalizeInPlace() * speedUp * actorState.getMovementSpeed() * elapsedFrames
    }

    private fun getDirectionFromTouchEvents(forward: Vector3f, right: Vector3f): Vector3f? {
        val events = keyboard.getTouchData().filter { it.isScreenTouch() }
        if (events.isEmpty()) { return null }

        for (event in events) {
            if (event.normalizedStartingX > 0.5) { continue }

            val (x, y) = event.getDeltaFromStart()
            val direction = Vector3f(x.toFloat(), 0f, y.toFloat())
            if (direction.magnitudeSquare() <= 1e-7) { return Vector3f() }

            direction.normalizeInPlace()
            return right * -direction.x + forward * -direction.z
        }

        return null
    }

    private fun getDirectionFromButtonPresses(forward: Vector3f, right: Vector3f): Vector3f {
        val velocity = Vector3f()

        if (keyboard.isKeyPressed(GameKey.Autorun)) {
            autorun = !autorun
        }

        if (autorun || keyboard.isKeyPressedOrRepeated(GameKey.MoveForward)) {
            velocity.x += forward.x
            velocity.z += forward.z
        }
        if (keyboard.isKeyPressedOrRepeated(GameKey.MoveLeft)) {
            velocity.x += right.x
            velocity.z += right.z
        }
        if (keyboard.isKeyPressedOrRepeated(GameKey.MoveBackward)) {
            velocity.x -= forward.x
            velocity.z -= forward.z
            autorun = false
        }
        if (keyboard.isKeyPressedOrRepeated(GameKey.MoveRight)) {
            velocity.x -= right.x
            velocity.z -= right.z
        }
        if (GameState.isDebugMode() && keyboard.isKeyPressedOrRepeated(GameKey.DebugGravity)) {
            velocity.y -= 1f
        }

        return velocity
    }

}

interface WanderingController {

    fun isWandering(): Boolean

    fun setWanderDestination(destination: Vector3f)

}

class DefaultEnemyController: ActorController, WanderingController {

    companion object {
        private const val defaultFollowDistanceNear: Float = 2f
        private const val defaultFollowDistanceFar: Float = 4f
    }

    private var approaching = false

    private var wanderDestination: Vector3f? = null
    private var wanderTime: Float = 0f

    var followDistanceNear = defaultFollowDistanceNear
    var followDistanceFar = defaultFollowDistanceFar

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        if (!actorState.isEngaged()) { return approachWanderDestination(actorState, elapsedFrames) }

        val actor = ActorManager[actorState.id]
        if (actor != null && actor.isMovementOrAnimationLocked()) { return Vector3f.ZERO }

        val target = ActorStateManager[actorState.targetState.targetId] ?: return Vector3f.ZERO
        if (target.isDead()) { return Vector3f.ZERO }

        val distance = actorState.getTargetingDistance(target)
        val desiredPosition = GameState.getGameMode().pathNextPosition(actorState, elapsedFrames, target.position) ?: target.position

        return if (distance <= followDistanceNear) {
            approaching = false
            Vector3f.ZERO
        } else if (approaching || distance >= followDistanceFar) {
            approaching = true
            velocityVectorTo(actorState.position, desiredPosition, actorState.getMovementSpeed(), elapsedFrames)
        } else {
            Vector3f.ZERO
        }
    }

    override fun isWandering(): Boolean {
        return wanderDestination != null
    }

    override fun setWanderDestination(destination: Vector3f) {
        wanderTime = 0f
        wanderDestination = destination
    }

    private fun approachWanderDestination(actorState: ActorState, elapsedFrames: Float): Vector3f {
        val destination = wanderDestination ?: return Vector3f.ZERO
        val desiredPosition = GameState.getGameMode().pathNextPosition(actorState, elapsedFrames, destination) ?: destination

        wanderTime += elapsedFrames
        if (Vector3f.distance(actorState.position, destination) < 0.5f || Fps.framesToSeconds(wanderTime) > 5.seconds) {
            wanderDestination = null
            return Vector3f.ZERO
        }

        return velocityVectorTo(actorState.position, desiredPosition, actorState.getMovementSpeed(), elapsedFrames)
    }

}

class PetController: ActorController {

    private val followDistanceNear = 2f
    private val followDistanceFar = 4f

    private var approaching = false

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        val followTarget = if (actorState.isEngaged()) { ActorStateManager[actorState.targetState.targetId] } else { ActorStateManager[actorState.owner] } ?: return Vector3f.ZERO
        val distance = actorState.getTargetingDistance(followTarget)

        return if (distance <= followDistanceNear) {
            approaching = false
            Vector3f.ZERO
        } else if (approaching || distance >= followDistanceFar) {
            approaching = true
            velocityVectorTo(actorState, followTarget, actorState.getMovementSpeed(), elapsedFrames)
        } else {
            Vector3f.ZERO
        }
    }

}

class TrustController: ActorController {

    private val defaultFollowDistance = Pair(2f, 4f)

    private var approaching = false

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        val party = PartyManager[actorState.id]
        val partyIndex = party.getIndex(actorState.id)

        val partyFollowTarget = if (partyIndex != null) {
            party.getStateByIndex(partyIndex - 1)
        } else {
            null
        }

        val followTarget = if (actorState.isEngaged()) { ActorStateManager[actorState.targetState.targetId] } else { partyFollowTarget } ?: return Vector3f.ZERO
        val distance = actorState.getTargetingDistance(followTarget)

        var followDistance = defaultFollowDistance
        if (actorState.isEngaged() && actorState.behaviorController is TrustBehaviorController) {
            val trustFollowDistance = actorState.behaviorController.getEngagedDistance()
            if (trustFollowDistance != null) { followDistance = trustFollowDistance }
        }

        return if (distance <= followDistance.first) {
            approaching = false
            Vector3f.ZERO
        } else if (approaching || distance >= followDistance.second) {
            approaching = true
            velocityVectorTo(actorState, followTarget, actorState.getMovementSpeed(), elapsedFrames)
        } else {
            Vector3f.ZERO
        }
    }

}

class FollowPartyController: ActorController {

    private val followDistance = Pair(2.7f, 3.3f)

    private var approaching = false

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        val party = PartyManager[actorState.id]
        val partyIndex = party.getIndex(actorState.id)

        val partyFollowTarget = if (partyIndex != null) {
            party.getStateByIndex(partyIndex - 1)
        } else {
            null
        }

        val followTarget = partyFollowTarget ?: return Vector3f.ZERO

        val distance = actorState.getTargetingDistance(followTarget)
        val pathPosition = GameState.getGameMode().pathNextPosition(actorState, elapsedFrames, followTarget.position)

        val desiredPosition = if (pathPosition != null && Vector3f.distance(pathPosition, actorState.position) < 2f) {
            pathPosition
        } else {
            followTarget.position
        }

        return if (distance <= followDistance.first) {
            approaching = false
            Vector3f.ZERO
        } else if (approaching || distance >= followDistance.second) {
            approaching = true
            velocityVectorTo(actorState.position, desiredPosition, actorState.getMovementSpeed(), elapsedFrames)
        } else {
            Vector3f.ZERO
        }
    }

}

fun velocityVectorTo(actor: ActorState, other: ActorState, speed: Float, elapsedFrames: Float): Vector3f {
    return velocityVectorTo(actor.position, other.position, speed, elapsedFrames)
}

fun velocityVectorTo(source: Vector3f, destination: Vector3f, speed: Float, elapsedFrames: Float): Vector3f {
    val direction = destination - source
    direction.y = 0f

    if (direction.magnitudeSquare() < 1e-5) { return Vector3f.ZERO }
    return direction.normalizeInPlace() * speed * elapsedFrames
}
