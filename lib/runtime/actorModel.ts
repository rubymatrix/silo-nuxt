import {
  DatId,
  type DirectoryResource,
  type InfoDefinition,
  SkeletonAnimationResource,
  SkeletonMeshResource,
} from '~/lib/resource/datResource'
import { SkeletonInstance } from '~/lib/resource/skeletonInstance'
import { datIdParameterizedMatch } from '~/lib/runtime/datId'
import type { BlurConfig, Model } from '~/lib/runtime/model'
import {
  LoopParams,
  type TransitionParams,
  SkeletonAnimationCoordinator,
} from '~/lib/runtime/skeletonAnimator'
import { collectByTypeRecursive } from '~/lib/runtime/resourceTree'

const identityRotation = { x: 0, y: 0, z: 0, w: 1 } as const

export enum FootState {
  Grounded = 'Grounded',
  Lifting = 'Lifting',
  Ungrounded = 'Ungrounded',
  Landing = 'Landing',
}

function nextFootState(state: FootState, isTouching: boolean): FootState {
  if (state === FootState.Grounded) {
    return isTouching ? FootState.Grounded : FootState.Lifting
  }
  if (state === FootState.Lifting || state === FootState.Landing) {
    return isTouching ? FootState.Grounded : FootState.Ungrounded
  }
  return isTouching ? FootState.Landing : FootState.Ungrounded
}

export class CustomModelSettings {
  blurConfig: BlurConfig | null = null
  scale = 1
  effectScale: number | null = null
  hideMain = false
}

export class SlotVisibilityOverride {
  readonly slot: number
  readonly hidden: boolean
  readonly ifEngaged: boolean

  constructor(slot: number, hidden: boolean, ifEngaged: boolean) {
    this.slot = slot
    this.hidden = hidden
    this.ifEngaged = ifEngaged
  }
}

export class ModelSlotVisibilityState {
  private readonly visibilityState = new Map<number, SlotVisibilityOverride>()

  apply(slotVisibilityOverride: SlotVisibilityOverride): void {
    this.visibilityState.set(slotVisibilityOverride.slot, slotVisibilityOverride)
  }

  getOverrides(): SlotVisibilityOverride[] {
    return Array.from(this.visibilityState.values())
  }
}

export class ModelLock {
  duration: number
  readonly validityCheck: () => boolean

  constructor(duration: number, validityCheck: () => boolean = () => true) {
    this.duration = duration
    this.validityCheck = validityCheck
  }
}

export interface RuntimeActor {
  isDisplayEngagedOrEngaging(): boolean
  getMount(): unknown | null
}

export class ActorModel {
  readonly actor: RuntimeActor
  readonly model: Model

  private skeletonInstance: SkeletonInstance | null = null
  readonly skeletonAnimationCoordinator = new SkeletonAnimationCoordinator()

  private readonly defaultVisibilityStates = new ModelSlotVisibilityState()
  private readonly hiddenModelSlotIds = new Set<number>()

  private leftFootState: FootState = FootState.Grounded
  private rightFootState: FootState = FootState.Grounded

  private readonly animationLocks: ModelLock[] = []
  private readonly movementLocks: ModelLock[] = []
  private readonly facingLocks: ModelLock[] = []

  private displayRangedDuration = 0

  customModelSettings = new CustomModelSettings()
  idleAnimationMode = 0
  battleAnimationMode = 0
  walkingAnimationMode = 0
  runningAnimationMode = 0

  constructor(actor: RuntimeActor, model: Model) {
    this.actor = actor
    this.model = model
  }

  update(elapsedFrames: number): void {
    if (!this.skeletonInstance) {
      const resource = this.model.getSkeletonResource()
      if (!resource) {
        return
      }
      this.skeletonInstance = new SkeletonInstance(resource)
      this.skeletonInstance.tPose()
    }

    this.skeletonAnimationCoordinator.update(elapsedFrames)
    this.updateLocks(elapsedFrames, this.movementLocks)
    this.updateLocks(elapsedFrames, this.animationLocks)
    this.updateLocks(elapsedFrames, this.facingLocks)

    this.displayRangedDuration -= elapsedFrames
    this.updateSkeletonPose()

    this.leftFootState = nextFootState(this.leftFootState, this.skeletonInstanceIsLeftFootTouchingGround())
    this.rightFootState = nextFootState(this.rightFootState, this.skeletonInstanceIsRightFootTouchingGround())
  }

  getSkeleton(): SkeletonInstance | null {
    return this.skeletonInstance
  }

  getMeshResources(): SkeletonMeshResource[] {
    const directories = this.model.getMeshResources()
    const resources = directories.flatMap((directory) => collectByTypeRecursive(directory, SkeletonMeshResource))
    const hiddenIds = new Set(Array.from(this.getHiddenSlotIds()).map((slot) => `wep${slot}`))
    return resources.filter((resource) => !hiddenIds.has(resource.id.id))
  }

  getHiddenSlotIds(): Set<number> {
    if (this.actor.getMount() !== null) {
      return new Set([0, 1, 2])
    }

    const hiddenSlots = new Set<number>([2])
    const engaged = this.actor.isDisplayEngagedOrEngaging()

    const overrideStates = [
      ...this.defaultVisibilityStates.getOverrides(),
      ...this.skeletonAnimationCoordinator
        .getSlotVisibilityOverrides()
        .flatMap((state) => state.getOverrides()),
    ]

    for (const slotOverride of overrideStates) {
      if (slotOverride.ifEngaged && !engaged) {
        continue
      }

      if (slotOverride.hidden) {
        hiddenSlots.add(slotOverride.slot)
      } else {
        hiddenSlots.delete(slotOverride.slot)
      }
    }

    for (const hiddenSlot of this.hiddenModelSlotIds) {
      hiddenSlots.add(hiddenSlot)
    }

    if (!hiddenSlots.has(2) && this.displayRangedDuration > 0) {
      return new Set([0, 1])
    }

    return hiddenSlots
  }

  setSkeletonAnimation(
    datId: DatId,
    animationDirs: readonly DirectoryResource[],
    loopParams: LoopParams = LoopParams.lowPriorityLoop(),
    transitionParams: TransitionParams,
    modelSlotVisibilityState: ModelSlotVisibilityState | null = null,
  ): void {
    const resources = this.fetchAnimations(datId, animationDirs)

    if (transitionParams.inBetween) {
      const inBetweenResources = this.fetchAnimations(transitionParams.inBetween, animationDirs)
      transitionParams.resolvedInBetween = new Map(
        inBetweenResources.map((resource) => {
          const key = Number.parseInt(resource.id.id.at(-1) ?? '0', 36)
          return [Number.isNaN(key) ? 0 : key, resource] as const
        }),
      )
    }

    this.skeletonAnimationCoordinator.registerAnimation(resources, loopParams, transitionParams, modelSlotVisibilityState)
  }

  transitionToMoving(datId: DatId, animationDirs: readonly DirectoryResource[], transitionParams: TransitionParams): void {
    this.setSkeletonAnimation(datId, animationDirs, LoopParams.lowPriorityLoop(), transitionParams)
  }

  transitionToIdleOnStopMoving(datId: DatId, animationDirs: readonly DirectoryResource[]): void {
    const idleResources = this.fetchAnimations(datId, animationDirs)
    this.skeletonAnimationCoordinator.registerIdleAnimation(idleResources, false)
  }

  transitionToIdleOnCompleted(datId: DatId, animationDirs: readonly DirectoryResource[]): void {
    const alreadyIdle = this.skeletonAnimationCoordinator.animations
      .filter((animator): animator is NonNullable<(typeof this.skeletonAnimationCoordinator.animations)[number]> => animator !== null)
      .map((animator) => animator.currentAnimation?.animation.id)
      .every((currentId) => (currentId ? datIdParameterizedMatch(currentId, datId) : false))

    if (alreadyIdle || !this.skeletonAnimationCoordinator.hasCompleteTransitionOutAnimations()) {
      return
    }

    const idleResources = this.fetchAnimations(datId, animationDirs)
    this.skeletonAnimationCoordinator.registerIdleAnimation(idleResources, true)
  }

  forceTransitionToIdle(idleId: DatId, transitionTime: number, animationDirs: readonly DirectoryResource[]): void {
    this.displayRangedDuration = 0
    const idleResources = this.fetchAnimations(idleId, animationDirs)
    this.skeletonAnimationCoordinator.registerAnimation(
      idleResources,
      LoopParams.lowPriorityLoop(),
      { transitionInTime: transitionTime, transitionOutTime: 0, inBetween: null, eagerTransitionOut: false, resolvedInBetween: null },
      null,
    )
  }

  getJointPosition(standardPosition: number): { x: number, y: number, z: number } | null {
    const skeleton = this.getSkeleton()
    if (!skeleton) {
      return null
    }

    try {
      return skeleton.getStandardJointPosition(standardPosition)
    } catch {
      return null
    }
  }

  getFootInfoDefinition(): InfoDefinition | null {
    return this.model.getMovementInfo()
  }

  displayRanged(duration: number): void {
    if (this.hiddenModelSlotIds.has(2)) {
      return
    }
    this.displayRangedDuration = duration
  }

  getBlurConfig(): BlurConfig | null {
    return this.customModelSettings.blurConfig ?? this.model.getBlurConfig()
  }

  setDefaultModelVisibility(slotVisibilityOverride: SlotVisibilityOverride): void {
    this.defaultVisibilityStates.apply(slotVisibilityOverride)
  }

  toggleModelVisibility(slot: number, hidden: boolean): void {
    if (hidden) {
      this.hiddenModelSlotIds.add(slot)
    } else {
      this.hiddenModelSlotIds.delete(slot)
    }

    if (slot === 2) {
      this.displayRangedDuration = 0
    }
  }

  lockAnimation(duration: number | ModelLock): void {
    this.animationLocks.push(duration instanceof ModelLock ? duration : new ModelLock(duration))
  }

  isAnimationLocked(): boolean {
    return this.animationLocks.length > 0
  }

  lockMovement(duration: number | ModelLock): void {
    this.movementLocks.push(duration instanceof ModelLock ? duration : new ModelLock(duration))
  }

  isMovementLocked(): boolean {
    return this.movementLocks.length > 0
  }

  lockFacing(duration: number | ModelLock): void {
    this.facingLocks.push(duration instanceof ModelLock ? duration : new ModelLock(duration))
  }

  isFacingLocked(): boolean {
    return this.facingLocks.length > 0
  }

  clearAnimations(): void {
    this.skeletonAnimationCoordinator.clear()
  }

  private updateLocks(elapsedFrames: number, locks: ModelLock[]): void {
    for (const lock of locks) {
      lock.duration -= elapsedFrames
    }

    for (let i = locks.length - 1; i >= 0; i -= 1) {
      const lock = locks[i]
      if (lock && (lock.duration <= 0 || !lock.validityCheck())) {
        locks.splice(i, 1)
      }
    }
  }

  private fetchAnimations(datId: DatId, animationDirs: readonly DirectoryResource[]): SkeletonAnimationResource[] {
    const allAnimations = animationDirs.flatMap((root) => collectByTypeRecursive(root, SkeletonAnimationResource))
    const matched = allAnimations.filter((resource) => datIdParameterizedMatch(resource.id, datId))
    const byId = new Map<string, SkeletonAnimationResource>()

    for (const resource of matched) {
      byId.set(resource.id.id, resource)
    }

    return Array.from(byId.values())
  }

  private updateSkeletonPose(): void {
    const skeleton = this.skeletonInstance
    if (!skeleton) {
      return
    }

    const resourceJoints = skeleton.resource.joints

    for (let i = 0; i < skeleton.joints.length; i += 1) {
      const joint = skeleton.joints[i]
      const jointDef = resourceJoints[i]
      if (!joint || !jointDef) {
        continue
      }

      const animationTransform = this.skeletonAnimationCoordinator.getJointTransform(i)
      const localRotation = normalizeQuaternion(
        multiplyQuaternion(animationTransform?.rotation ?? identityRotation, jointDef.rotation),
      )
      const localTranslation = {
        x: finiteNumber(jointDef.translation.x) + finiteNumber(animationTransform?.translation.x),
        y: finiteNumber(jointDef.translation.y) + finiteNumber(animationTransform?.translation.y),
        z: finiteNumber(jointDef.translation.z) + finiteNumber(animationTransform?.translation.z),
      }

      if (joint.parentIndex < 0) {
        joint.worldPosition.x = localTranslation.x
        joint.worldPosition.y = localTranslation.y
        joint.worldPosition.z = localTranslation.z
        joint.worldRotation.x = localRotation.x
        joint.worldRotation.y = localRotation.y
        joint.worldRotation.z = localRotation.z
        joint.worldRotation.w = localRotation.w
        continue
      }

      const parent = skeleton.joints[joint.parentIndex]
      if (!parent) {
        continue
      }

      const rotatedTranslation = rotateVector(parent.worldRotation, localTranslation)
      joint.worldPosition.x = parent.worldPosition.x + rotatedTranslation.x
      joint.worldPosition.y = parent.worldPosition.y + rotatedTranslation.y
      joint.worldPosition.z = parent.worldPosition.z + rotatedTranslation.z

      const worldRotation = normalizeQuaternion(multiplyQuaternion(parent.worldRotation, localRotation))
      joint.worldRotation.x = worldRotation.x
      joint.worldRotation.y = worldRotation.y
      joint.worldRotation.z = worldRotation.z
      joint.worldRotation.w = worldRotation.w
    }
  }

  private skeletonInstanceIsLeftFootTouchingGround(): boolean {
    return this.leftFootState === FootState.Grounded || this.leftFootState === FootState.Landing
  }

  private skeletonInstanceIsRightFootTouchingGround(): boolean {
    return this.rightFootState === FootState.Grounded || this.rightFootState === FootState.Landing
  }
}

function multiplyQuaternion(
  a: { x: number, y: number, z: number, w: number },
  b: { x: number, y: number, z: number, w: number },
): { x: number, y: number, z: number, w: number } {
  return {
    w: a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z,
    x: a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
    y: a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
    z: a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
  }
}

function conjugateQuaternion(q: { x: number, y: number, z: number, w: number }): { x: number, y: number, z: number, w: number } {
  return {
    x: -q.x,
    y: -q.y,
    z: -q.z,
    w: q.w,
  }
}

function normalizeQuaternion(q: { x: number, y: number, z: number, w: number }): { x: number, y: number, z: number, w: number } {
  const magnitude = Math.hypot(q.x, q.y, q.z, q.w)
  if (!Number.isFinite(magnitude) || magnitude <= 1e-7) {
    return { ...identityRotation }
  }

  return {
    x: q.x / magnitude,
    y: q.y / magnitude,
    z: q.z / magnitude,
    w: q.w / magnitude,
  }
}

function finiteNumber(value: number | undefined): number {
  if (value === undefined || !Number.isFinite(value)) {
    return 0
  }
  return value
}

function rotateVector(
  rotation: { x: number, y: number, z: number, w: number },
  vector: { x: number, y: number, z: number },
): { x: number, y: number, z: number } {
  const q = normalizeQuaternion(rotation)
  const vecQuat = { x: vector.x, y: vector.y, z: vector.z, w: 0 }
  const rotated = multiplyQuaternion(multiplyQuaternion(q, vecQuat), conjugateQuaternion(q))
  return {
    x: rotated.x,
    y: rotated.y,
    z: rotated.z,
  }
}
