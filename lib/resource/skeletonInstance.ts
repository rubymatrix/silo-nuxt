import type { JointReference, Quaternion, SkeletonResource, Vector3 } from './datResource'

export const StandardPosition = {
  AboveHead: 2,
  RightFoot: 8,
  LeftFoot: 9,
  LeftHand: 126,
  RightHand: 127,
} as const

export type StandardPositionKey = keyof typeof StandardPosition

interface MutableQuaternion {
  x: number
  y: number
  z: number
  w: number
}

interface JointInstance {
  readonly index: number
  readonly parentIndex: number
  readonly worldPosition: Vector3
  readonly worldRotation: MutableQuaternion
}

const IDENTITY_ROTATION: Quaternion = { x: 0, y: 0, z: 0, w: 1 }

export class SkeletonInstance {
  readonly resource: SkeletonResource
  readonly joints: JointInstance[]

  constructor(resource: SkeletonResource) {
    this.resource = resource
    this.joints = resource.joints.map((joint, index) => ({
      index,
      parentIndex: joint.parentIndex,
      worldPosition: { x: 0, y: 0, z: 0 },
      worldRotation: { ...IDENTITY_ROTATION },
    }))
  }

  tPose(): void {
    for (let i = 0; i < this.joints.length; i += 1) {
      const jointDef = this.resource.joints[i]
      const joint = this.joints[i]
      if (!jointDef || !joint) {
        continue
      }

      if (joint.parentIndex < 0) {
        this.joints[i] = {
          ...joint,
          worldPosition: { ...jointDef.translation },
          worldRotation: normalizeQuaternion(jointDef.rotation),
        }
        continue
      }

      const parent = this.joints[joint.parentIndex]
      const parentRotation = parent?.worldRotation ?? IDENTITY_ROTATION
      const rotatedTranslation = rotateVector(parentRotation, jointDef.translation)
      const worldPosition = {
        x: (parent?.worldPosition.x ?? 0) + rotatedTranslation.x,
        y: (parent?.worldPosition.y ?? 0) + rotatedTranslation.y,
        z: (parent?.worldPosition.z ?? 0) + rotatedTranslation.z,
      }

      this.joints[i] = {
        ...joint,
        worldPosition,
        worldRotation: normalizeQuaternion(multiplyQuaternion(parentRotation, jointDef.rotation)),
      }
    }
  }

  getStandardJoint(referenceIndex: number | StandardPositionKey): JointReference {
    const index = typeof referenceIndex === 'number' ? referenceIndex : StandardPosition[referenceIndex]
    const reference = this.resource.jointReferences[index]
    if (!reference) {
      throw new Error(`Missing standard joint reference at index ${index}`)
    }
    return reference
  }

  getStandardJointPosition(referenceIndex: number | StandardPositionKey): Vector3 {
    return this.getJointPosition(this.getStandardJoint(referenceIndex))
  }

  getJoint(reference: JointReference): JointInstance {
    const joint = this.joints[reference.index]
    if (!joint) {
      throw new Error(`Unknown joint index: ${reference.index}`)
    }
    return joint
  }

  getJointPosition(reference: JointReference): Vector3 {
    const joint = this.getJoint(reference)
    const rotatedOffset = rotateVector(joint.worldRotation, reference.positionOffset)
    return {
      x: joint.worldPosition.x + rotatedOffset.x,
      y: joint.worldPosition.y + rotatedOffset.y,
      z: joint.worldPosition.z + rotatedOffset.z,
    }
  }
}

function multiplyQuaternion(a: Quaternion, b: Quaternion): Quaternion {
  return {
    w: a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z,
    x: a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
    y: a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
    z: a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
  }
}

function conjugateQuaternion(q: Quaternion): Quaternion {
  return {
    x: -q.x,
    y: -q.y,
    z: -q.z,
    w: q.w,
  }
}

function rotateVector(rotation: Quaternion, vector: Vector3): Vector3 {
  const q = normalizeQuaternion(rotation)
  const vecQuat: Quaternion = { x: vector.x, y: vector.y, z: vector.z, w: 0 }
  const rotated = multiplyQuaternion(multiplyQuaternion(q, vecQuat), conjugateQuaternion(q))
  return {
    x: rotated.x,
    y: rotated.y,
    z: rotated.z,
  }
}

function normalizeQuaternion(q: Quaternion): Quaternion {
  const mag = Math.hypot(q.x, q.y, q.z, q.w)
  if (mag <= 1e-7) {
    return { ...IDENTITY_ROTATION }
  }

  return {
    x: q.x / mag,
    y: q.y / mag,
    z: q.z / mag,
    w: q.w / mag,
  }
}
