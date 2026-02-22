import {
  DatId,
  type Quaternion,
  type SkeletonAnimation,
  type SkeletonAnimationKeyFrameTransform,
  type SkeletonAnimationResource,
  type Vector3,
} from '~/lib/resource/datResource'
import { datIdFinalDigit } from '~/lib/runtime/datId'
import type { ModelSlotVisibilityState } from '~/lib/runtime/actorModel'

const unitTransform: SkeletonAnimationKeyFrameTransform = {
  rotation: { x: 0, y: 0, z: 0, w: 1 },
  translation: { x: 0, y: 0, z: 0 },
  scale: { x: 1, y: 1, z: 1 },
}

function normalizeQuaternion(q: Quaternion): Quaternion {
  const mag = Math.hypot(q.x, q.y, q.z, q.w)
  if (mag <= 1e-7) {
    return { ...unitTransform.rotation }
  }
  return {
    x: q.x / mag,
    y: q.y / mag,
    z: q.z / mag,
    w: q.w / mag,
  }
}

function nlerpQuaternion(a: Quaternion, b: Quaternion, t: number): Quaternion {
  const dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
  const rhs = dot < 0
    ? { x: -b.x, y: -b.y, z: -b.z, w: -b.w }
    : b

  return normalizeQuaternion({
    x: a.x * (1 - t) + rhs.x * t,
    y: a.y * (1 - t) + rhs.y * t,
    z: a.z * (1 - t) + rhs.z * t,
    w: a.w * (1 - t) + rhs.w * t,
  })
}

function lerpVector3(a: Vector3, b: Vector3, t: number): Vector3 {
  return {
    x: a.x + (b.x - a.x) * t,
    y: a.y + (b.y - a.y) * t,
    z: a.z + (b.z - a.z) * t,
  }
}

function interpolateTransform(
  a: SkeletonAnimationKeyFrameTransform | null,
  b: SkeletonAnimationKeyFrameTransform | null,
  delta: number,
): SkeletonAnimationKeyFrameTransform | null {
  if (!a && !b) {
    return null
  }

  if (a && b) {
    return {
      rotation: nlerpQuaternion(a.rotation, b.rotation, delta),
      translation: lerpVector3(a.translation, b.translation, delta),
      scale: lerpVector3(a.scale, b.scale, delta),
    }
  }

  const only = a ?? b
  if (!only) {
    return null
  }

  return {
    rotation: nlerpQuaternion(a?.rotation ?? unitTransform.rotation, b?.rotation ?? unitTransform.rotation, delta),
    translation: lerpVector3(a?.translation ?? unitTransform.translation, b?.translation ?? unitTransform.translation, delta),
    scale: only.scale,
  }
}

export class AnimationSnapshot {
  private readonly jointSnapshots = new Map<number, SkeletonAnimationKeyFrameTransform>()

  constructor(previous: SkeletonAnimationContext)
  constructor(previous: AnimationTransition)
  constructor(previous: SkeletonAnimationContext | AnimationTransition) {
    if (previous instanceof AnimationTransition) {
      const allJoints = new Set<number>()
      for (const joint of previous.previous.jointSnapshots.keys()) {
        allJoints.add(joint)
      }
      for (const joint of previous.next.animation.keyFrameSets.keys()) {
        allJoints.add(joint)
      }

      for (const joint of allJoints) {
        const snapshot = previous.getJointTransform(joint)
        if (snapshot) {
          this.jointSnapshots.set(joint, snapshot)
        }
      }

      return
    }

    for (const joint of previous.animation.keyFrameSets.keys()) {
      const snapshot = previous.animation.getJointTransform(joint, previous.currentFrame)
      if (snapshot) {
        this.jointSnapshots.set(joint, snapshot)
      }
    }
  }

  getJointTransform(jointIndex: number): SkeletonAnimationKeyFrameTransform | null {
    return this.jointSnapshots.get(jointIndex) ?? null
  }
}

export class AnimationTransition {
  readonly previous: AnimationSnapshot
  readonly next: SkeletonAnimationContext
  readonly transitionDuration: number
  readonly inBetween: SkeletonAnimationResource | null

  private progress = 0

  constructor(previous: AnimationSnapshot, next: SkeletonAnimationContext, transitionDuration: number, inBetween: SkeletonAnimationResource | null) {
    this.previous = previous
    this.next = next
    this.transitionDuration = Math.max(0.0001, transitionDuration)
    this.inBetween = inBetween
  }

  update(elapsedFrames: number): boolean {
    this.progress += elapsedFrames
    return this.isComplete()
  }

  isComplete(): boolean {
    return this.progress >= this.transitionDuration
  }

  getJointTransform(jointIndex: number): SkeletonAnimationKeyFrameTransform | null {
    const t = this.progress / this.transitionDuration

    if (!this.inBetween) {
      return interpolateTransform(
        this.previous.getJointTransform(jointIndex),
        this.next.getJointTransform(jointIndex),
        t,
      )
    }

    if (t < 0.5) {
      return interpolateTransform(
        this.previous.getJointTransform(jointIndex),
        this.inBetween.animation.getJointTransform(jointIndex, 0),
        t * 2,
      )
    }

    return interpolateTransform(
      this.inBetween.animation.getJointTransform(jointIndex, 0),
      this.next.getJointTransform(jointIndex),
      (t - 0.5) * 2,
    )
  }
}

export class LoopParams {
  readonly loopDuration: number | null
  readonly numLoops: number | null
  readonly lowPriority: boolean

  constructor(loopDuration: number | null, numLoops: number | null, lowPriority = false) {
    this.loopDuration = loopDuration
    this.numLoops = numLoops
    this.lowPriority = lowPriority
  }

  static lowPriorityLoop(): LoopParams {
    return new LoopParams(null, null, true)
  }
}

export class TransitionParams {
  readonly transitionInTime: number
  readonly transitionOutTime: number
  readonly inBetween: DatId | null
  eagerTransitionOut: boolean
  resolvedInBetween: Map<number, SkeletonAnimationResource> | null = null

  constructor(
    transitionInTime = 7.5,
    transitionOutTime = 7.5,
    inBetween: DatId | null = null,
    eagerTransitionOut = false,
  ) {
    this.transitionInTime = transitionInTime
    this.transitionOutTime = transitionOutTime
    this.inBetween = inBetween
    this.eagerTransitionOut = eagerTransitionOut
  }
}

export class SkeletonAnimationContext {
  readonly animation: SkeletonAnimation
  readonly loopParams: LoopParams
  readonly transitionParams: TransitionParams | null
  readonly modelSlotVisibilityState: ModelSlotVisibilityState | null

  currentFrame = 0
  framesSinceComplete = 0
  totalLifeTime = 0

  private completed = false
  private loopCounter = 0

  constructor(
    animation: SkeletonAnimation,
    loopParams: LoopParams,
    transitionParams: TransitionParams | null,
    modelSlotVisibilityState: ModelSlotVisibilityState | null,
  ) {
    this.animation = animation
    this.loopParams = loopParams
    this.transitionParams = transitionParams
    this.modelSlotVisibilityState = modelSlotVisibilityState
  }

  advance(elapsedFrames: number): void {
    this.totalLifeTime += elapsedFrames
    if (this.completed || this.transitionParams?.eagerTransitionOut === true) {
      this.framesSinceComplete += elapsedFrames
    }

    if (this.loopParams.loopDuration === 0) {
      this.currentFrame = 0
      this.completed = true
      return
    }

    const loopDuration = this.loopParams.loopDuration ?? this.animation.getLengthInFrames()
    const scalingFactor = this.animation.getLengthInFrames() / loopDuration
    this.currentFrame += elapsedFrames * scalingFactor
    this.currentFrame = this.applyLoopBounds()
  }

  getJointTransform(jointIndex: number): SkeletonAnimationKeyFrameTransform | null {
    return this.animation.getJointTransform(jointIndex, this.currentFrame)
  }

  isDoneLooping(): boolean {
    return this.loopParams.numLoops === null || this.completed
  }

  private applyLoopBounds(): number {
    const maxLoops = this.loopParams.numLoops ?? 0
    while (this.currentFrame > this.animation.getLengthInFrames()) {
      this.loopCounter += 1
      this.currentFrame -= this.animation.getLengthInFrames()
    }

    if (maxLoops !== 0 && this.loopCounter >= maxLoops) {
      this.completed = true
      return this.animation.getLengthInFrames()
    }

    return this.currentFrame
  }
}

export class SkeletonAnimator {
  readonly animationSlot: number
  currentAnimation: SkeletonAnimationContext | null = null
  transition: AnimationTransition | null = null

  constructor(animationSlot: number) {
    this.animationSlot = animationSlot
  }

  update(elapsedFrames: number): void {
    if (this.transition?.update(elapsedFrames)) {
      this.transition = null
      return
    }

    this.currentAnimation?.advance(elapsedFrames)
  }

  setNextAnimation(skeletonAnimationContext: SkeletonAnimationContext, transitionParams: TransitionParams | null): void {
    const current = this.currentAnimation
    if (!current || transitionParams?.transitionInTime === 0) {
      this.currentAnimation = skeletonAnimationContext
      return
    }

    if (current.animation === skeletonAnimationContext.animation && current.loopParams.lowPriority) {
      return
    }

    if (this.animationSlot !== 5) {
      const transitionDuration = transitionParams?.transitionInTime
        ?? (current.transitionParams && current.transitionParams.transitionOutTime > 0 ? current.transitionParams.transitionOutTime : 7.5)

      const snapshot = this.transition ? new AnimationSnapshot(this.transition) : new AnimationSnapshot(current)
      const maybeInBetweenFrame = transitionParams?.resolvedInBetween?.get(this.animationSlot) ?? null
      this.transition = new AnimationTransition(snapshot, skeletonAnimationContext, transitionDuration, maybeInBetweenFrame)
    }

    this.currentAnimation = skeletonAnimationContext
  }

  getJointTransform(jointIndex: number): SkeletonAnimationKeyFrameTransform | null {
    if (this.transition) {
      return this.transition.getJointTransform(jointIndex)
    }
    return this.currentAnimation?.getJointTransform(jointIndex) ?? null
  }
}

export class SkeletonAnimationCoordinator {
  readonly animations: Array<SkeletonAnimator | null> = Array.from({ length: 8 }, () => null)

  update(elapsedFrames: number): void {
    for (const animation of this.animations) {
      animation?.update(elapsedFrames)
    }
  }

  registerAnimation(
    skeletonAnimationResources: readonly SkeletonAnimationResource[],
    loopParams: LoopParams,
    transitionParams: TransitionParams | null = null,
    modelSlotVisibilityState: ModelSlotVisibilityState | null = null,
    overrideCondition: (animator: SkeletonAnimator) => boolean = () => true,
  ): void {
    for (const skeletonAnimationResource of skeletonAnimationResources) {
      const animationType = datIdFinalDigit(skeletonAnimationResource.id) ?? 0
      const animator = this.getOrPut(animationType)
      const context = new SkeletonAnimationContext(
        skeletonAnimationResource.animation,
        loopParams,
        transitionParams,
        modelSlotVisibilityState,
      )

      if (overrideCondition(animator)) {
        animator.setNextAnimation(context, transitionParams)
      }
    }
  }

  hasCompleteTransitionOutAnimations(): boolean {
    return this.animations.some((animation) => animation !== null && this.readyForTransitionOut(animation, true))
  }

  registerIdleAnimation(
    skeletonAnimationResources: readonly SkeletonAnimationResource[],
    requireTransitionOut: boolean,
  ): void {
    this.registerAnimation(
      skeletonAnimationResources,
      LoopParams.lowPriorityLoop(),
      null,
      null,
      (animator) => this.readyForTransitionOut(animator, requireTransitionOut),
    )
  }

  clearCompleteAnimations(): void {
    for (let i = 0; i < this.animations.length; i += 1) {
      const current = this.animations[i]
      const animation = current?.currentAnimation
      if (!current || !animation || animation.loopParams.lowPriority) {
        continue
      }
      if (!this.readyForTransitionOut(current, true)) {
        continue
      }

      const transitionOutTime = animation.transitionParams?.transitionOutTime
      if (transitionOutTime !== undefined && animation.framesSinceComplete >= transitionOutTime) {
        this.animations[i] = null
      }
    }
  }

  getSlotVisibilityOverrides(): ModelSlotVisibilityState[] {
    return this.animations
      .map((animation) => animation?.currentAnimation)
      .filter((value): value is SkeletonAnimationContext => value !== null && value !== undefined)
      .map((value) => value.modelSlotVisibilityState)
      .filter((value): value is ModelSlotVisibilityState => value !== null)
  }

  getJointTransform(jointIndex: number): SkeletonAnimationKeyFrameTransform | null {
    let highTransform: SkeletonAnimationKeyFrameTransform | null = null
    let highAnimator: SkeletonAnimator | null = null
    let lowTransform: SkeletonAnimationKeyFrameTransform | null = null

    for (let i = this.animations.length - 1; i >= 0; i -= 1) {
      const animator = this.animations[i]
      if (!animator) {
        continue
      }

      const transform = animator.getJointTransform(jointIndex)
      if (!transform) {
        continue
      }

      if (!highTransform) {
        highTransform = transform
        highAnimator = animator
      } else if (!lowTransform) {
        lowTransform = transform
        break
      }
    }

    if (highTransform && highAnimator && lowTransform) {
      return this.crossSlotInterpolation(highTransform, highAnimator, lowTransform)
    }

    return highTransform ?? lowTransform
  }

  isTransitioning(): boolean {
    return this.animations.some((animation) => animation?.transition !== null)
  }

  clear(): void {
    for (let i = 0; i < this.animations.length; i += 1) {
      this.animations[i] = null
    }
  }

  private crossSlotInterpolation(
    highSlot: SkeletonAnimationKeyFrameTransform,
    highAnimator: SkeletonAnimator,
    lowSlot: SkeletonAnimationKeyFrameTransform,
  ): SkeletonAnimationKeyFrameTransform {
    const highAnimation = highAnimator.currentAnimation
    const transitionParams = highAnimation?.transitionParams
    if (!highAnimation || !transitionParams) {
      return highSlot
    }

    let deltaIn = 0
    let deltaOut = 0

    if (highAnimation.totalLifeTime < transitionParams.transitionInTime) {
      deltaIn = 1 - (highAnimation.totalLifeTime / transitionParams.transitionInTime)
    }

    if (this.readyForTransitionOut(highAnimator, true)) {
      deltaOut = transitionParams.transitionOutTime === 0
        ? 0
        : highAnimation.framesSinceComplete / transitionParams.transitionOutTime
    }

    const delta = Math.max(deltaIn, deltaOut)
    if (delta <= 0) {
      return highSlot
    }
    if (delta >= 1) {
      return lowSlot
    }

    return {
      rotation: nlerpQuaternion(highSlot.rotation, lowSlot.rotation, delta),
      translation: lerpVector3(highSlot.translation, lowSlot.translation, delta),
      scale: lerpVector3(highSlot.scale, lowSlot.scale, delta),
    }
  }

  private readyForTransitionOut(animator: SkeletonAnimator, requireTransitionOut: boolean): boolean {
    const current = animator.currentAnimation
    const transitionOutReqs = !requireTransitionOut || (() => {
      const outTime = current?.transitionParams?.transitionOutTime
      return outTime === undefined || outTime >= 0
    })()

    let doneLooping = current?.loopParams === undefined || current.isDoneLooping()
    if (current?.transitionParams?.eagerTransitionOut) {
      doneLooping = true
    }

    return transitionOutReqs && doneLooping
  }

  private getOrPut(slot: number): SkeletonAnimator {
    const normalizedSlot = Math.max(0, Math.min(this.animations.length - 1, slot))
    const current = this.animations[normalizedSlot]
    if (current) {
      return current
    }

    const created = new SkeletonAnimator(normalizedSlot)
    this.animations[normalizedSlot] = created
    return created
  }
}
