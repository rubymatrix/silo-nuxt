package xim.resource

import xim.math.Matrix4f
import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.*
import xim.poc.camera.CameraReference
import xim.poc.game.AttackContext
import xim.poc.gl.ByteColor
import xim.util.interpolate
import kotlin.math.atan2

abstract class InterpolatedEffect {

    var runTime = 0f
    var progress = 0f

    private var ranOnce = false

    fun update(elapsedFrames: Float) {
        val completed = isComplete()
        if (ranOnce && completed) { return }

        runTime += elapsedFrames
        runTime = runTime.coerceAtMost(duration())
        progress = if (duration() == 0f) { 1.0f } else { runTime / duration() }

        updateEffect(elapsedFrames)
        ranOnce = true

        if (isComplete()) { onComplete() }
    }

    open fun updateEffect(elapsedFrames: Float) {}

    abstract fun duration(): Float

    fun isComplete(): Boolean {
        return runTime >= duration()
    }

    open fun onComplete() {}

}

class SkeletonAnimationInstance(val actor: Actor, val skeletonAnimationRoutine: SkeletonAnimationRoutine, val rate: Float, val localDir: DirectoryResource, val modelSlotVisibilityState: ModelSlotVisibilityState): InterpolatedEffect() {

    init {
        val animationDirs = listOf(localDir) + actor.getAllAnimationDirectories()
        val loopParams = LoopParams(loopDuration = skeletonAnimationRoutine.duration.toFloat() / (2f * rate), numLoops = skeletonAnimationRoutine.maxLoops)
        val transitionParams = TransitionParams(transitionInTime = skeletonAnimationRoutine.transitionInTime.toFloat() / (2f * rate), transitionOutTime = skeletonAnimationRoutine.transitionOutTime.toFloat() / (2f * rate))

        actor.actorModel?.setSkeletonAnimation(skeletonAnimationRoutine.id, animationDirs, loopParams, transitionParams = transitionParams, modelSlotVisibilityState = modelSlotVisibilityState)
    }

    override fun duration(): Float {
        return skeletonAnimationRoutine.duration.toFloat() / rate
    }

}

class FlinchAnimationInstance(val actor: Actor, val flinchRoutine: FlinchRoutine): InterpolatedEffect() {

    private val transitionParams: TransitionParams

    init {
        transitionParams = TransitionParams(transitionInTime = flinchRoutine.animationDuration/2f, transitionOutTime = flinchRoutine.animationDuration/2f)
        setModelRoutine()
    }

    override fun updateEffect(elapsedFrames: Float) {
        if (runTime >= flinchRoutine.animationDuration / 2f) {
            transitionParams.eagerTransitionOut = true
        }
    }

    override fun duration(): Float {
        return flinchRoutine.animationDuration / 2f
    }

    private fun setModelRoutine() {
        val actorModel = actor.actorModel ?: return
        if (actorModel.isAnimationLocked()) { return }

        // Flinching should only ever overwrite idle animations
        val currentlyIdle = actorModel.skeletonAnimationCoordinator.animations.all { it == null || it.currentAnimation?.loopParams?.lowPriority == true }
        if (!currentlyIdle) { return }

        val animationDirs = actor.getAllAnimationDirectories()
        val loopParams = LoopParams(loopDuration = null, numLoops = 1)

        // TODO - using [dfi?] arbitrarily here. There's also [dbi?], which is used for getting hit from the back.
        // TODO - where is [dfi?] defined for pc models? [dfm?] is probably for extended knock-back effects
        val flinchId = if (actorModel.model is PcModel) { DatId("dfm?") } else { DatId("dfi?") }
        actorModel.setSkeletonAnimation(flinchId, animationDirs, loopParams, transitionParams = transitionParams)
    }

}

class ModelTransformInstance(
    val modelId: DatId,
    val effect: ModelTransformEffect,
    val area: Area,
    val initialValueSupplier: (ModelTransform) -> Vector3f,
    val updater: (ModelTransform, Vector3f) -> Unit) : InterpolatedEffect() {

    private val initialValue = Vector3f()

    init {
        val initialTransforms = area.getModelTransform(modelId)
        if (initialTransforms != null) {
            val transform = initialTransforms.transforms[effect.index]
            if (transform != null) { initialValue.copyFrom(initialValueSupplier.invoke(transform)) }
        }
    }

    override fun updateEffect(elapsedFrames: Float) {
        val newValue = Vector3f.lerp(initialValue, effect.finalValue, progress)
        area.updateModelTransform(modelId, effect.index) { updater.invoke(it, newValue) }
    }

    override fun duration(): Float {
        return effect.duration.toFloat()
    }

}

class ActorColorTransform(val effect: ActorFadeRoutine, val actor: Actor) : InterpolatedEffect() {

    private val initialValue = actor.renderState.effectColor.copy()

    override fun updateEffect(elapsedFrames: Float) {
        actor.renderState.effectColor = ByteColor.interpolate(initialValue, effect.endColor, progress)
    }

    override fun duration(): Float {
        return effect.duration.toFloat()
    }

}

class ActorWrapEffect (
    var textureLink: DatLink<TextureResource> = DatLink(DatId.zero),
    var color: ByteColor = ByteColor.zero.copy(),
    var uvTranslation: Vector2f = Vector2f()
) {

    init {
        reset()
    }

    fun reset() {
        textureLink = DatLink(DatId.zero)
        color = ByteColor.zero.copy()
        uvTranslation = Vector2f()
    }
}

class ActorWrapColorTransform(val effect: ActorWrapColor, val actor: Actor) : InterpolatedEffect() {

    private val initialValue = actor.renderState.wrapEffect.color.copy()

    override fun updateEffect(elapsedFrames: Float) {
        actor.renderState.wrapEffect.color = ByteColor.interpolate(initialValue, effect.endValue, progress)
    }

    override fun duration(): Float {
        return effect.duration.toFloat()
    }
}

class ActorWrapUvTransform(val effect: ActorWrapUvTranslation, val actor: Actor) : InterpolatedEffect() {

    private val initialValue = actor.renderState.wrapEffect.uvTranslation.copy()

    override fun updateEffect(elapsedFrames: Float) {
        actor.renderState.wrapEffect.uvTranslation[effect.uv] = initialValue[effect.uv].interpolate(effect.endValue, progress)
    }

    override fun duration(): Float {
        return effect.duration.toFloat()
    }
}

class ActorWrapTextureEffect(val effect: ActorWrapTexture, val actor: Actor): InterpolatedEffect() {
    override fun duration(): Float {
        return effect.duration.toFloat()
    }

    init {
        actor.renderState.wrapEffect.textureLink = effect.textureLink
    }

    override fun onComplete() {
        actor.renderState.wrapEffect.textureLink = DatLink(DatId.zero)
    }

}

class ActorJumpTransform(val effect: ActorJumpRoutine, val actor: Actor, val start: Vector3f, val end: Vector3f) : InterpolatedEffect() {

    init {
        actor.displayPositionOffset.copyFrom(end - start)
        if (actor.isPlayer()) { CameraReference.getInstance().lock(enable = true, position = start) }
    }

    override fun onComplete() {
        actor.displayPositionOffset.copyFrom(Vector3f.ZERO)
        if (actor.isPlayer()) { CameraReference.getInstance().lock(enable = false) }
    }

    override fun duration(): Float {
        return effect.duration.toFloat()
    }

}

class ActorForwardDisplacement(val effect: ForwardDisplacementEffect, val actor: Actor): InterpolatedEffect() {

    val end = Matrix4f().rotateYInPlace(actor.displayFacingDir)
            .translateInPlace(Vector3f(effect.displacement, 0f, 0f))
            .getTranslationVector()

    override fun updateEffect(elapsedFrames: Float) {
        actor.displayPositionOffset.copyFrom(Vector3f.lerp(Vector3f.ZERO, end, progress))
    }

    override fun onComplete() {
        actor.displayPositionOffset.copyFrom(Vector3f.ZERO)
    }

    override fun duration(): Float {
        return effect.duration.toFloat()
    }

}

class PointLightMultiplierModifier(val effect: PointLightInterpolationEffect, val particleGenerator: ParticleGenerator): InterpolatedEffect() {

    private val startingValues = HashMap<Long, Float>()

    override fun duration(): Float {
        return effect.duration.toFloat()
    }

    override fun updateEffect(elapsedFrames: Float) {
        val particles = particleGenerator.getActiveParticles()

        particles.forEach {
            val start = startingValues.getOrPut(it.internalId) { getValue(it) }
            setValue(it, start.interpolate(effect.endValue, progress))
        }
    }

    private fun getValue(particle: Particle): Float {
        return if (effect.theta) { particle.pointLightParams.thetaMultiplier } else { particle.pointLightParams.rangeMultiplier }
    }

    private fun setValue(particle: Particle, value: Float) {
        if (effect.theta) { particle.pointLightParams.thetaMultiplier = value } else { particle.pointLightParams.rangeMultiplier = value }
    }

}

class KnockBackInstance(val source: Actor, val target: Actor, val knockBackRoutine: KnockBackRoutine, val context: AttackContext, val modelSlotVisibilityState: ModelSlotVisibilityState): InterpolatedEffect() {

    private val standUpTime = 8f
    private val magnitude = context.knockBackMagnitude / 2f

    private val knockBackDirection = computeKnockBackDirection()
    private val totalDuration = (knockBackRoutine.animationDuration) + standUpTime
    private var standUp = false

    init {
        if (duration() > standUpTime) {
            target.getState().faceToward(source.getState())
            target.actorModel?.lockAnimation(totalDuration/2f)
            target.actorModel?.lockMovement(totalDuration/2f)

            knockBackAnim()
        }
    }

    override fun updateEffect(elapsedFrames: Float) {
        target.getState().effectVelocity += knockBackDirection * (elapsedFrames * magnitude / 8f)

        if (!standUp && runTime > knockBackRoutine.animationDuration) {
            standUp = true
            standUpAnim()
        }
    }

    override fun duration(): Float {
        return totalDuration
    }

    private fun computeKnockBackDirection(): Vector3f {
        return (target.displayPosition - source.displayPosition).normalizeInPlace()
    }

    private fun knockBackAnim() {
        val animationDirs = target.getAllAnimationDirectories()
        val loopParams = LoopParams(loopDuration = totalDuration, numLoops = 1)
        val transitionParams = TransitionParams(transitionInTime = 7.5f, transitionOutTime = 3.5f)
        target.actorModel?.setSkeletonAnimation(DatId("bf0?"), animationDirs, loopParams, transitionParams = transitionParams, modelSlotVisibilityState = modelSlotVisibilityState)
    }

    private fun standUpAnim() {
        val animationDirs = target.getAllAnimationDirectories()
        val loopParams = LoopParams(loopDuration = standUpTime, numLoops = 1)
        val transitionParams = TransitionParams(transitionInTime = 3.5f, transitionOutTime = 7.5f)
        target.actorModel?.setSkeletonAnimation(DatId("bf1?"), animationDirs, loopParams, transitionParams = transitionParams, modelSlotVisibilityState = modelSlotVisibilityState)
    }

}

class AttackBlockedAnimationInstance(val actor: Actor, val animationId: DatId, val effect: AttackBlockedRoutine): InterpolatedEffect() {

    private val transitionParams: TransitionParams

    init {
        transitionParams = TransitionParams(transitionInTime = 1f, transitionOutTime = 7.5f)
        setModelRoutine()
    }

    override fun duration(): Float {
        return effect.animationDuration / 2f
    }

    private fun setModelRoutine() {
        val actorModel = actor.actorModel ?: return
        if (actorModel.isAnimationLocked()) { return }

        // Blocking should only ever overwrite idle animations
        val currentlyIdle = actorModel.skeletonAnimationCoordinator.animations.all { it == null || it.currentAnimation?.loopParams?.lowPriority == true }
        if (!currentlyIdle) { return }

        val animationDirs = actor.getAllAnimationDirectories()
        val loopParams = LoopParams(loopDuration = effect.animationDuration / 2f, numLoops = 1)

        actorModel.setSkeletonAnimation(animationId, animationDirs, loopParams, transitionParams = transitionParams)
    }

}

class WeaponTraceEffect(val weaponTraceRoutine: WeaponTraceRoutine, val weaponTraceResource: WeaponTraceResource, val actorAssociation: ActorAssociation): InterpolatedEffect() {

    private val rotation = actorAssociation.actor.displayFacingDir
    private val trace = weaponTraceResource.trace

    override fun duration(): Float {
        return weaponTraceRoutine.duration.toFloat()
    }

    override fun updateEffect(elapsedFrames: Float) {
        val current = runTime * (trace.endTime / duration())
        if (current < trace.rowStartTime || current > trace.endTime) { return }

        WeaponTraceDrawer.enqueue(WeaponTraceCommand(
            frames = current,
            rotation = rotation,
            weaponTraceResource = weaponTraceResource,
            weaponTraceRoutine = weaponTraceRoutine,
            actorAssociation = actorAssociation,
        ))
    }

}

class FollowPointsEffect(val followPointsRoutine: FollowPointsRoutine, val points: PointList, val actorAssociation: ActorAssociation): InterpolatedEffect() {

    override fun duration(): Float {
        return followPointsRoutine.duration.toFloat()
    }

    override fun updateEffect(elapsedFrames: Float) {
        actorAssociation.actor.state.position.copyFrom(points.getLerpPosition(progress))

        if (progress == 0f) { return }

        val rotationEnabled = followPointsRoutine.flags0 and 0x02 != 0
        if (!rotationEnabled) { return }

        val horizontalMovement = actorAssociation.actor.movement.withY(0f)
        if (horizontalMovement.magnitude() < 1e-4f) { return }

        val horizontalDirection = horizontalMovement.normalize()

        val theta = -atan2(horizontalDirection.z, horizontalDirection.x)
        actorAssociation.actor.state.rotation = theta + followPointsRoutine.rotation
    }

}