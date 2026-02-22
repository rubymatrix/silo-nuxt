package xim.poc.game.event

import xim.math.Vector3f
import xim.poc.*
import xim.poc.camera.CameraReference
import xim.poc.game.*
import xim.poc.game.configuration.ActorMonsterController
import xim.poc.tools.ZoneChanger
import xim.util.Fps.framesToSeconds
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class TickActorEvent(
    val elapsedFrames: Float,
    val sourceId: ActorId,
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()
        actorState.age += elapsedFrames

        val outputEvents = ArrayList<Event>()

        outputEvents += updateDeathStatus(actorState)

        updateStaggerEffects(actorState)

        outputEvents += updateEffectTickTimer(actorState)

        updateSkillChainState(actorState)

        if (actorState.isDummyActor()) { return outputEvents }

        outputEvents += updateVelocity(actorState)

        outputEvents += updateCastingState(actorState)

        outputEvents += updateEngageState(actorState)

        outputEvents += updateStatusStatus(actorState)

        updateCombatStats(actorState)

        moveActor(actorState)

        updateRotation(actorState)

        actorState.effectVelocity.copyFrom(Vector3f.ZERO)

        checkAggro(actorState)

        syncDummyDependents(actorState)

        actorState.components.entries.removeAll {
            val result = it.value.update(actorState, elapsedFrames)
            outputEvents += result.events
            result.removeComponent
        }

        return outputEvents
    }

    private fun updateCastingState(actorState: ActorState): List<Event> {
        val castingState = actorState.getCastingState() ?: return emptyList()

        val shouldInterrupt = actorState.velocity.magnitudeSquare() > 0f || actorState.isDead() || actorState.hasStatusActionLock()
        if (shouldInterrupt) { castingState.result = CastingInterrupted }

        castingState.update(elapsedFrames)

        if (castingState.isReadyToExecute()) {
            return listOf(CastingChargeCompleteEvent(sourceId))
        }

        if (castingState.isComplete()) {
            actorState.clearActionState(castingState)
        }

        return emptyList()
    }

    private fun updateVelocity(actorState: ActorState): List<Event> {
        val newVelocity = Vector3f(actorState.getControllerVelocity(elapsedFrames))
        val outputEvents = ArrayList<Event>()

        actorState.behaviorController.adjustVelocity(newVelocity)

        if (actorState.hasStatusMovementLock()) {
            newVelocity.copyFrom(Vector3f.ZERO)
        } else if (actorState.actionState.preventsMovement()) {
            if (actorState.actionState.cancelledByMovement() && newVelocity.magnitude() > 0f) {
                outputEvents += RestingEndEvent(actorState.id)
                outputEvents += SitChairEndEvent(actorState.id)
            }
            newVelocity.copyFrom(Vector3f.ZERO)
        } else if (actorState.isDead()) {
            newVelocity.copyFrom(Vector3f.ZERO)
        } else if (ZoneChanger.isChangingZones()) {
            newVelocity.copyFrom(Vector3f.ZERO)
        }

        newVelocity += actorState.effectVelocity
        actorState.velocity.copyFrom(newVelocity)

        return outputEvents
    }

    private fun updateDeathStatus(actorState: ActorState): List<Event> {
        actorState.updateDeathTimer(elapsedFrames)
        if (!actorState.isDead()) { return emptyList() }

        val outputEvents = ArrayList<Event>()

        if (actorState.isPlayer() && actorState.hasBeenDeadFor(60.seconds)) {
            GameState.getGameMode().onReturnToHomePoint(actorState)
        } else if (!actorState.isPlayer() && actorState.hasBeenDeadFor(15.seconds)) {
            outputEvents += ActorDeleteEvent(sourceId)
        } else if ((actorState.isEnemy() || actorState.isDependent()) && actorState.isDead() && actorState.hasBeenDeadFor(10.seconds)) {
            ActorManager[actorState.id]?.fadeAway()
        }

        return outputEvents
    }

    private fun moveActor(actor: ActorState) {
        val currentScene = SceneManager.getCurrentScene()

        val collisionResults = if (actor.isStaticNpc()) {
            FrameCoherence.getNpcCollision(actor.id) { currentScene.moveActor(actor, elapsedFrames) }
        } else {
            currentScene.moveActor(actor, elapsedFrames)
        }

        actor.lastCollisionResult = when (collisionResults) {
            is TerrainCollisionResult -> ActorCollision(collisionResults.collisionProperties)
            is ElevatorCollisionResult -> handleElevatorCollision(actor, collisionResults)
            NoCollision -> handleNoCollisionResult(actor)
        }
    }

    private fun handleElevatorCollision(actor: ActorState, elevatorCollision: ElevatorCollisionResult): ActorCollision {
        if (elevatorCollision.collisionProperty == null) { return actor.lastCollisionResult }
        val mainArea = SceneManager.getCurrentScene().getMainArea()

        return ActorCollision(
            collisionsByArea = mapOf(mainArea to listOf(elevatorCollision.collisionProperty)),
            freeFallDuration = 0f
        )
    }

    private fun handleNoCollisionResult(actor: ActorState): ActorCollision {
        val newFreeFallTime = actor.lastCollisionResult.freeFallDuration + elapsedFrames

        return if (framesToSeconds(newFreeFallTime) <= 1.seconds) {
            // Retain the collision properties to avoid "flashing" during minor drops at high frame-rates
            ActorCollision(collisionsByArea = actor.lastCollisionResult.collisionsByArea, freeFallDuration = newFreeFallTime)
        } else {
            ActorCollision(collisionsByArea = emptyMap(), freeFallDuration = newFreeFallTime)
        }
    }

    private fun syncDummyDependents(actorState: ActorState) {
        val bubble = actorState.bubble
        val mount = actorState.mountedState?.id
        val rod = actorState.fishingRod
        if (bubble == null && mount == null && rod == null) { return }

        val dependents = listOfNotNull(bubble, mount, rod)
        for (dependentId in dependents) {
            val dependent = ActorStateManager[dependentId] ?: continue
            dependent.position.copyFrom(actorState.position)
            dependent.rotation = actorState.rotation
            dependent.lastCollisionResult = actorState.lastCollisionResult
            dependent.velocity.copyFrom(actorState.velocity)
        }
    }

    private fun updateRotation(actorState: ActorState) {
        if (actorState.behaviorController.isRotationLocked()) { return }

        val strafing = ActorManager[actorState.id]?.isStrafing() ?: false

        val targetState = actorState.targetState
        val target = ActorStateManager[targetState.targetId]

        if (actorState.facesTarget && targetState.locked && target != null) {
            actorState.faceToward(target)
        } else if (strafing) {
            actorState.setRotation(CameraReference.getInstance().getViewVector())
        } else if (actorState.velocity.magnitudeSquare() > 1e-5f) {
            actorState.setRotation(actorState.velocity)
        } else if (actorState.type != ActorType.Pc && actorState.facesTarget && actorState.isEngaged() && target != null) {
            actorState.faceToward(target)
        }
    }

    private fun updateStatusStatus(actorState: ActorState): List<Event> {
        return actorState.updateStatusEffects(elapsedFrames)
            .map { StatusEffectLostEvent(sourceId = sourceId, statusEffectState = it) }
    }

    private fun updateEngageState(actorState: ActorState): List<Event> {
        val target = ActorManager[actorState.targetState.targetId]

        return if (actorState.isEngaged() && (target == null || target.isDisplayedDead())) {
            if (actorState.type == ActorType.Pc) {
                listOf(ActorTargetEvent(sourceId = sourceId, targetId = null))
            } else {
                listOf(BattleDisengageEvent(sourceId))
            }
        } else {
            return emptyList()
        }
    }

    private fun updateSkillChainState(actorState: ActorState) {
        actorState.skillChainTargetState.update(elapsedFrames)
    }

    private fun updateCombatStats(actorState: ActorState) {
        if (actorState.isStaticNpc()) { return }
        actorState.updateCombatStats(GameEngine.computeCombatStats(actorState.id))
    }

    private fun updateStaggerEffects(actorState: ActorState) {
        if (actorState.isDead()) { actorState.frozenTimer.reset(ZERO) } else { actorState.frozenTimer.update(elapsedFrames) }
    }

    private fun updateEffectTickTimer(actorState: ActorState): List<Event> {
        if (actorState.isStaticNpc()) { return emptyList() }

        return if (actorState.effectTickTimer.update(elapsedFrames)) {
            listOf(ActorEffectTick(sourceId))
        } else {
            emptyList()
        }
    }

    private fun checkAggro(actorState: ActorState) {
        if (actorState.behaviorController is ActorMonsterController) {
            actorState.behaviorController.performAggroCheck()
        }
    }

}