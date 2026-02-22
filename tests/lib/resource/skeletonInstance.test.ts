import { describe, expect, it } from 'vitest'

import { DatId, SkeletonResource } from '~/lib/resource/datResource'
import { SkeletonInstance } from '~/lib/resource/skeletonInstance'

describe('SkeletonInstance', () => {
  it('applies parent rotation in t-pose transforms', () => {
    const rootRotationY90 = {
      x: 0,
      y: Math.SQRT1_2,
      z: 0,
      w: Math.SQRT1_2,
    }

    const resource = new SkeletonResource(
      new DatId('skel'),
      [
        { parentIndex: -1, rotation: rootRotationY90, translation: { x: 0, y: 0, z: 0 } },
        { parentIndex: 0, rotation: { x: 0, y: 0, z: 0, w: 1 }, translation: { x: 1, y: 0, z: 0 } },
      ],
      [
        { index: 1, unkV0: { x: 0, y: 0, z: 0 }, positionOffset: { x: 0, y: 0, z: 0 }, fileOffset: 0 },
      ],
      [],
    )

    const instance = new SkeletonInstance(resource)
    instance.tPose()

    const pos = instance.getJointPosition(resource.jointReferences[0]!)
    expect(Math.round(pos.x)).toBe(0)
    expect(Math.round(pos.z)).toBe(-1)
  })

  it('rotates joint reference offset by joint rotation', () => {
    const rootRotationY90 = {
      x: 0,
      y: Math.SQRT1_2,
      z: 0,
      w: Math.SQRT1_2,
    }

    const resource = new SkeletonResource(
      new DatId('skel'),
      [
        { parentIndex: -1, rotation: rootRotationY90, translation: { x: 0, y: 0, z: 0 } },
      ],
      [
        { index: 0, unkV0: { x: 0, y: 0, z: 0 }, positionOffset: { x: 1, y: 0, z: 0 }, fileOffset: 0 },
      ],
      [],
    )

    const instance = new SkeletonInstance(resource)
    instance.tPose()

    const pos = instance.getJointPosition(resource.jointReferences[0]!)
    expect(Math.round(pos.x)).toBe(0)
    expect(Math.round(pos.z)).toBe(-1)
  })
})
