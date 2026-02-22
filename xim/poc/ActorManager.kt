package xim.poc

import xim.math.Vector3f
import xim.poc.camera.Camera
import xim.poc.camera.CameraReference
import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.util.Timer

object ActorManager {

    private val actors: HashMap<ActorId, Actor> = LinkedHashMap()

    private var visibleActors = HashMap<ActorId, Actor>()

    fun clear() {
        val ids = actors.keys.toList()
        ids.forEach { remove(it) }
    }

    fun isVisible(actorId: ActorId): Boolean {
        return visibleActors.containsKey(actorId)
    }

    fun getVisibleActors(): Collection<Actor> {
        return visibleActors.values
    }

    operator fun get(actorId: ActorId?): Actor? {
        return getIfPresent(actorId)
    }

    fun remove(actorId: ActorId) {
        val actor = actors[actorId] ?: return
        EffectManager.clearEffects(ActorAssociation(actor, ActorContext(actorId)))
        actors.remove(actorId)
    }

    fun player(): Actor {
        return getOrCreate(ActorStateManager.player())
    }

    fun getIfPresent(actorId: ActorId?): Actor? {
        actorId ?: return null
        return actors[actorId]
    }

    private fun getOrCreate(actorState: ActorState): Actor {
        return actors.getOrPut(actorState.id) { Actor.createFrom(actorState) }
    }

    fun updateAll(elapsedFrames: Float) {
        val actorStates = ActorStateManager.getAll().filter { !it.value.disabled }
        Timer.time("filterActors") { refreshVisibleActors(actorStates) }

        val actorsToUpdate = actors.values.filter { !it.state.disabled }
        Timer.time("updateActors") { actorsToUpdate.forEach { it.update(elapsedFrames) } }

        // In game, skeletal animations are only updated every other frame.
        // Halving the frame has the same effect on animation speed, but is smoother.
        val scaledElapsedFrames = elapsedFrames / 2f
        Timer.time("updateActorAnimations") { actorsToUpdate.forEach { updateAnimation(scaledElapsedFrames, it) } }
    }

    private fun refreshVisibleActors(validActors: Map<ActorId, ActorState>) {
        visibleActors.clear()

        val player = player()

        val pet = get(player.getPetId())
        if (pet != null) { visibleActors[pet.id] = pet }

        val mount = get(player.getMount()?.id)
        if (mount != null) { visibleActors[mount.id] = mount }

        visibleActors[player.id] = player()

        val camera = CameraReference.getInstance()

        validActors.filter { !it.value.disabled && it.value.isShip() }
            .forEach { visibleActors[it.key] = getOrCreate(it.value) }

        visibleActors += validActors.values
            .filter { !visibleActors.containsKey(it.id) && !isActorCulled(camera, it) }
            .sortedBy { Vector3f.distance(camera.getPosition(), it.position) }
            .take(20)
            .associate { it.id to getOrCreate(it) }
    }

    private fun updateAnimation(elapsedFrames: Float, actor: Actor) {
        val actorModel = actor.actorModel ?: return
        if (actor.state.isStaticNpc() && !isVisible(actor.id)) { return }

        val actorMount = actor.getMount()

        if (!actorModel.isAnimationLocked()) {
            val idleId = actor.getIdleAnimationId()

            if (actorMount != null) {
                actorModel.transitionToIdleOnStopMoving(idleId, actor.getAllAnimationDirectories())
            } else if (actor.currentVelocity.magnitude() > 0.0001f) {
                val transition = if (needsInBetweenFrame(actor)) { TransitionParams(inBetween = idleId) } else { TransitionParams() }
                actorModel.transitionToMoving(actor.getMovementAnimation(), actor.getAllAnimationDirectories(), transitionParams = transition)
            } else if (!actor.hasEnqueuedRoutines() && actor.stoppedMoving) {
                actorModel.transitionToIdleOnStopMoving(idleId, actor.getAllAnimationDirectories())
            } else {
                actorModel.transitionToIdleOnCompleted(idleId, actor.getAllAnimationDirectories())
            }

            if (!actor.isDisplayedDead()) {
                actorModel.skeletonAnimationCoordinator.clearCompleteAnimations()
            }
        }

        actorModel.update(elapsedFrames)
        if (!actorModel.skeletonAnimationThrottler.updateAndCheck(elapsedFrames)) { return }

        if (isVisible(actor.id) && !actor.state.isAnimationFrozen()) {
            actorModel.getSkeleton()?.animate(actor, actorModel, actorMount)
        }
    }

    private fun needsInBetweenFrame(actor: Actor): Boolean {
        // Interpolating between [mvr?] and [mvl?] has bad results, because some joints will rotate ~180 deg
        // So, depending on the joint-tree, some joints interpolate the "long way"
        // To mitigate, we can use the first frame of the idle animation as an in-between frame
        val currentMovementDir = actor.getMovementDirection()
        return currentMovementDir == Direction.Left || currentMovementDir == Direction.Right
    }

    private fun isActorCulled(camera: Camera, actor: ActorState): Boolean {
        val cameraPosition = camera.getPosition()
        val distance = Vector3f.distance(cameraPosition, actor.position)

        if (distance > 55f) { removeActorDisplay(actor) }

        if (!actor.visible || distance > 50f) { return true }

        val actorDisplay = get(actor.id) ?: return false
        val visibilityBox = actorDisplay.getSkeletonBoundingBox(0)
        if (visibilityBox != null && !camera.isVisible(visibilityBox)) { return true }

        return false
    }

    private fun removeActorDisplay(actorState: ActorState) {
        getIfPresent(actorState.id) ?: return

        // TODO - support culling any display-actor type
        if ((actorState.monsterId != null || actorState.isStaticNpc()) && !actorState.isDoor()) {
            remove(actorState.id)
        }
    }

}