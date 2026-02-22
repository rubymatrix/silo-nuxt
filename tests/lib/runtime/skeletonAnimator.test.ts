import { describe, expect, it } from 'vitest'

import { DatId, SkeletonAnimation, SkeletonAnimationResource } from '~/lib/resource/datResource'
import {
  LoopParams,
  SkeletonAnimationContext,
  SkeletonAnimationCoordinator,
  TransitionParams,
} from '~/lib/runtime/skeletonAnimator'

function makeAnimationResource(id: string, x0: number, x1: number): SkeletonAnimationResource {
  const animation = new SkeletonAnimation(new DatId(id), 1, 2, 1)
  animation.keyFrameSets.set(0, [
    {
      rotation: { x: 0, y: 0, z: 0, w: 1 },
      translation: { x: x0, y: 0, z: 0 },
      scale: { x: 1, y: 1, z: 1 },
    },
    {
      rotation: { x: 0, y: 0, z: 0, w: 1 },
      translation: { x: x1, y: 0, z: 0 },
      scale: { x: 1, y: 1, z: 1 },
    },
  ])

  return new SkeletonAnimationResource(new DatId(id), animation)
}

describe('SkeletonAnimationCoordinator', () => {
  it('registers animation into slot from DAT id suffix', () => {
    const coordinator = new SkeletonAnimationCoordinator()
    coordinator.registerAnimation([makeAnimationResource('run3', 0, 2)], LoopParams.lowPriorityLoop())

    expect(coordinator.animations[3]).toBeTruthy()
  })

  it('supports transition interpolation for joint transforms', () => {
    const coordinator = new SkeletonAnimationCoordinator()
    const idle = makeAnimationResource('idl0', 0, 0)
    const run = makeAnimationResource('run0', 2, 2)

    coordinator.registerAnimation([idle], LoopParams.lowPriorityLoop())
    coordinator.update(1)

    coordinator.registerAnimation(
      [run],
      LoopParams.lowPriorityLoop(),
      new TransitionParams(2, 0),
    )

    coordinator.update(1)
    const transform = coordinator.getJointTransform(0)

    expect(transform).toBeTruthy()
    expect(transform?.translation.x).toBeCloseTo(1, 3)
  })
})

describe('SkeletonAnimationContext', () => {
  it('completes finite loops and freezes at animation end', () => {
    const resource = makeAnimationResource('atk0', 0, 5)
    const context = new SkeletonAnimationContext(
      resource.animation,
      new LoopParams(null, 1),
      new TransitionParams(0, 0),
      null,
    )

    context.advance(10)
    expect(context.isDoneLooping()).toBe(true)

    const transform = context.getJointTransform(0)
    expect(transform?.translation.x).toBeCloseTo(5, 3)
  })
})
