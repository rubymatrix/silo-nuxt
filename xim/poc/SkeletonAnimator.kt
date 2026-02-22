package xim.poc

import xim.math.Quaternion
import xim.math.Vector3f
import xim.resource.DatId
import xim.resource.SkeletonAnimation
import xim.resource.SkeletonAnimationKeyFrameTransform
import xim.resource.SkeletonAnimationResource
import kotlin.math.max

class AnimationSnapshot {

    private val jointSnapshots = HashMap<Int, SkeletonAnimationKeyFrameTransform>()

    constructor(previous: SkeletonAnimationContext) {
        val joints = previous.animation.keyFrameSets.keys
        for (joint in joints) {
            val snapshot = previous.animation.getJointTransform(joint, previous.currentFrame) ?: continue
            jointSnapshots[joint] = snapshot
        }
    }

    constructor(transition: AnimationTransition) {
        val allJoints = transition.previous.jointSnapshots.keys + transition.next.animation.keyFrameSets.keys
        for (joint in allJoints) {
            val snapshot = transition.getJointTransform(joint) ?: continue
            jointSnapshots[joint] = snapshot
        }
    }

    fun getJointTransform(jointIndex: Int): SkeletonAnimationKeyFrameTransform? {
        return jointSnapshots[jointIndex]
    }

}

class AnimationTransition(val previous: AnimationSnapshot, val next: SkeletonAnimationContext, val transitionDuration: Float, val inBetween: SkeletonAnimationResource?) {

    companion object {
        private val unitTransform = SkeletonAnimationKeyFrameTransform()
    }

    private var progress = 0f

    fun update(elapsedFrames: Float): Boolean {
        progress += elapsedFrames
        return isComplete()
    }

    fun isComplete(): Boolean {
        return progress >= transitionDuration
    }

    fun getJointTransform(jointIndex: Int): SkeletonAnimationKeyFrameTransform? {
        val t = progress/transitionDuration

        return if (inBetween == null) {
            val keyFramePrev = previous.getJointTransform(jointIndex)
            val keyFrameNext = next.getJointTransform(jointIndex)
            interpolate(keyFramePrev, keyFrameNext, t)
        } else if (t < 0.5f) {
            val keyFramePrev = previous.getJointTransform(jointIndex)
            val keyFrameNext = inBetween.skeletonAnimation.getJointTransform(jointIndex, 0f)
            interpolate(keyFramePrev, keyFrameNext, t * 2f)
        } else {
            val keyFramePrev = inBetween.skeletonAnimation.getJointTransform(jointIndex, 0f)
            val keyFrameNext = next.getJointTransform(jointIndex)
            interpolate(keyFramePrev, keyFrameNext, (t - 0.5f) * 2f)
        }
    }

    private fun interpolate(a: SkeletonAnimationKeyFrameTransform?, b: SkeletonAnimationKeyFrameTransform?, delta: Float) : SkeletonAnimationKeyFrameTransform? {
        if (a == null && b == null) { return null }
        if (a != null && b != null) { return SkeletonAnimationKeyFrameTransform.interpolate(a, b, delta) }

        // Interpolating from the "unit" transform tends to be smoother when the previous animation doesn't act on the joint
        // However, doing this for `scale` breaks some effects (ranged-attack while engaged), so just default to whichever isn't null
        val rotation = Quaternion.nlerp(a?.rotation ?: unitTransform.rotation, b?.rotation ?: unitTransform.rotation, delta)
        val translation = Vector3f.lerp(a?.translation ?: unitTransform.translation, b?.translation ?: unitTransform.translation, delta)
        val scale = a?.scale ?: b?.scale ?: unitTransform.scale

        return SkeletonAnimationKeyFrameTransform(rotation = rotation, translation = translation, scale = scale)
    }

}

data class LoopParams (
    val loopDuration: Float?,
    val numLoops: Int?,
    val lowPriority: Boolean = false,
) {
    companion object {
        fun lowPriorityLoop(): LoopParams = LoopParams(loopDuration = null, numLoops = null, lowPriority = true)
    }
}

class TransitionParams (
    val transitionInTime: Float = 7.5f,
    val transitionOutTime: Float = 7.5f,
    val inBetween: DatId? = null,
    var eagerTransitionOut: Boolean = false,
) {
    var resolvedInBetween: Map<Int, SkeletonAnimationResource>? = null
}

class SkeletonAnimationContext(
    val animation: SkeletonAnimation,
    val loopParams: LoopParams,
    val transitionParams: TransitionParams?,
    val modelSlotVisibilityState: ModelSlotVisibilityState?,
) {

    var currentFrame = 0f
    var framesSinceComplete = 0f
    var totalLifeTime = 0f

    private var completed = false
    private var loopCounter = 0

    fun advance(elapsedFrames: Float) {
        totalLifeTime += elapsedFrames
        if (completed || transitionParams?.eagerTransitionOut == true) {
            framesSinceComplete += elapsedFrames
        }

        if (loopParams.loopDuration == 0f) {
            currentFrame = 0f
            completed = true
            return
        }

        val loopDuration = loopParams.loopDuration ?: animation.getLengthInFrames()
        val scalingFactor = animation.getLengthInFrames() / loopDuration

        currentFrame += (elapsedFrames * scalingFactor)
        currentFrame = applyLoopBounds()
    }

    fun getJointTransform(jointIndex: Int): SkeletonAnimationKeyFrameTransform? {
        return animation.getJointTransform(jointIndex, currentFrame)
    }

    fun isDoneLooping(): Boolean {
        return loopParams.numLoops == null || completed
    }

    private fun applyLoopBounds(): Float {
        val maxLoops = loopParams.numLoops ?: 0

        while (currentFrame > animation.getLengthInFrames()) {
            loopCounter += 1
            currentFrame -= animation.getLengthInFrames()
        }

        if (maxLoops != 0 && loopCounter >= maxLoops) {
            completed = true
            return animation.getLengthInFrames()
        }

        return currentFrame
    }

}

class SkeletonAnimator(private val animationSlot: Int) {

    var currentAnimation: SkeletonAnimationContext? = null
    var transition: AnimationTransition? = null

    fun update(elapsedFrames: Float) {
        if (transition?.update(elapsedFrames) == true) {
            transition = null
        } else {
            currentAnimation?.advance(elapsedFrames)
        }
    }

    fun setNextAnimation(skeletonAnimationContext: SkeletonAnimationContext, transitionParams: TransitionParams?) {
        val current = currentAnimation

        if (current == null || transitionParams?.transitionInTime == 0f) {
            currentAnimation = skeletonAnimationContext
            return
        }

        if ((current.animation === skeletonAnimationContext.animation) && current.loopParams.lowPriority) {
            return
        }

        // TODO - disabling same-slot-interpolation for Slot[5] helps with various "triplet" mobs (Cluster, Bats, etc)
        // Generally, animations in Slot[5] want cross-slot-interpolation instead
        if (animationSlot != 5) {
            val transitionDuration = if (transitionParams != null) {
                transitionParams.transitionInTime
            } else if (current.transitionParams != null && current.transitionParams.transitionOutTime > 0f) {
                current.transitionParams.transitionOutTime
            } else {
                7.5f
            }

            val snapshot = transition?.let { AnimationSnapshot(it) } ?: AnimationSnapshot(current)
            val maybeInBetweenFrame = transitionParams?.resolvedInBetween?.get(animationSlot)

            transition = AnimationTransition(snapshot, skeletonAnimationContext, transitionDuration, maybeInBetweenFrame)
        }

        currentAnimation = skeletonAnimationContext
    }

    fun getJointTransform(jointIndex: Int): SkeletonAnimationKeyFrameTransform? {
        return if (transition != null) {
            transition?.getJointTransform(jointIndex)
        } else {
            currentAnimation?.getJointTransform(jointIndex)
        }
    }

}

class SkeletonAnimationCoordinator {

    val animations = Array<SkeletonAnimator?>(8) { null }

    fun update(elapsedFrames: Float) {
        animations.forEach { it?.update(elapsedFrames) }
    }

    fun registerAnimation(skeletonAnimationResources: List<SkeletonAnimationResource>, loopParams: LoopParams, transitionParams: TransitionParams? = null, modelSlotVisibilityState: ModelSlotVisibilityState? = null, overrideCondition: (SkeletonAnimator) -> Boolean = { true }) {
        for (skeletonAnimationResource in skeletonAnimationResources) {
            val animationType = skeletonAnimationResource.id.finalDigit() ?: 0
            val animator = getOrPut(animationType) { SkeletonAnimator(animationType) }
            val context = SkeletonAnimationContext(skeletonAnimationResource.skeletonAnimation, loopParams, transitionParams, modelSlotVisibilityState)

            if (overrideCondition(animator)) {
                animator.setNextAnimation(context, transitionParams)
            }
        }
    }

    fun hasCompleteTransitionOutAnimations(): Boolean {
        return animations.any { it != null && readyForTransitionOut(it, requireTransitionOut = true) }
    }

    fun registerIdleAnimation(skeletonAnimationResources: List<SkeletonAnimationResource>, requireTransitionOut: Boolean) {
        registerAnimation(skeletonAnimationResources, loopParams = LoopParams.lowPriorityLoop()) {
            readyForTransitionOut(it, requireTransitionOut)
        }
    }

    fun clearCompleteAnimations() {
        for (i in animations.indices) {
            val current = animations[i] ?: continue
            val animation = current.currentAnimation ?: continue

            if (animation.loopParams.lowPriority) { continue }
            if (!readyForTransitionOut(current, requireTransitionOut = true)) { continue }

            val transitionOutTime = animation.transitionParams?.transitionOutTime ?: continue
            if (animation.framesSinceComplete >= transitionOutTime) { animations[i] = null }
        }
    }

    fun getSlotVisibilityOverrides(): List<ModelSlotVisibilityState> {
        return animations.mapNotNull { it?.currentAnimation }.mapNotNull { it.modelSlotVisibilityState }
    }

    private fun readyForTransitionOut(animator: SkeletonAnimator, requireTransitionOut: Boolean): Boolean {
        val current = animator.currentAnimation

        val transitionOutReqs = if (!requireTransitionOut) { true } else {
            val outTime = current?.transitionParams?.transitionOutTime
            val noOutTimeCase = (outTime == null || outTime == 0f)
            val outTimeCase = outTime != null && outTime > 0f
            noOutTimeCase || outTimeCase
        }

        var doneLooping = current?.loopParams == null || current.isDoneLooping()

        val eagerOut = current?.transitionParams?.eagerTransitionOut
        if (eagerOut == true) { doneLooping = true }

        return transitionOutReqs && doneLooping
    }

    fun getJointTransform(jointIndex: Int): SkeletonAnimationKeyFrameTransform? {
        var highTransform: SkeletonAnimationKeyFrameTransform? = null
        var highAnimator: SkeletonAnimator? = null
        var lowTransform: SkeletonAnimationKeyFrameTransform? = null

        for (i in 8 downTo 0) {
            val animator = animations[i] ?: continue
            val animatorTransform = animator.getJointTransform(jointIndex) ?: continue

            if (highTransform == null) {
                highTransform = animatorTransform
                highAnimator = animator
            } else if (lowTransform == null) {
                lowTransform = animatorTransform
                break
            }
        }

        return if (highTransform != null && highAnimator != null && lowTransform != null) {
            crossSlotInterpolation(highTransform, highAnimator, lowTransform)
        } else {
            highTransform ?: lowTransform
        }
    }

    private fun crossSlotInterpolation(highSlot: SkeletonAnimationKeyFrameTransform, highAnimator: SkeletonAnimator, lowSlot: SkeletonAnimationKeyFrameTransform): SkeletonAnimationKeyFrameTransform {
        val highAnimation = highAnimator.currentAnimation ?: return highSlot
        val transitionParams = highAnimation.transitionParams ?: return highSlot

        var deltaIn = 0f
        var deltaOut = 0f

        if (highAnimation.totalLifeTime < transitionParams.transitionInTime) {
            deltaIn = (1f - highAnimation.totalLifeTime/transitionParams.transitionInTime)
        }

        if (readyForTransitionOut(highAnimator, requireTransitionOut = true)) {
            deltaOut = if (transitionParams.transitionOutTime == 0f) { 0f } else { highAnimation.framesSinceComplete/transitionParams.transitionOutTime }
        }

        val delta = max(deltaIn, deltaOut)

        return if (delta <= 0f) {
            highSlot
        } else if (delta >= 1f) {
            lowSlot
        } else {
            SkeletonAnimationKeyFrameTransform.interpolate(highSlot, lowSlot, delta)
        }
    }

    fun isTransitioning(): Boolean {
        return animations.any { it?.transition != null }
    }

    fun clear() {
        for (i in animations.indices) { animations[i] = null }
    }

    private fun getOrPut(slot: Int, fn: () -> SkeletonAnimator): SkeletonAnimator {
        val current = animations[slot]
        if (current != null) { return current }

        val new = fn.invoke()
        animations[slot] = new
        return new
    }

}