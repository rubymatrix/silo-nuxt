import { describe, expect, it } from 'vitest'

import { SectionHeader } from '~/lib/resource/datParser'
import {
  DatId,
  DirectoryResource,
  type InfoDefinition,
  SectionTypes,
  SkeletonAnimation,
  SkeletonAnimationResource,
  SkeletonMeshResource,
  SkeletonResource,
} from '~/lib/resource/datResource'
import {
  ActorModel,
  ModelSlotVisibilityState,
  SlotVisibilityOverride,
  type RuntimeActor,
} from '~/lib/runtime/actorModel'
import type { Model } from '~/lib/runtime/model'
import { LoopParams, TransitionParams } from '~/lib/runtime/skeletonAnimator'

function makeMeshResource(id: string, occlusionType = 0): SkeletonMeshResource {
  return new SkeletonMeshResource(new DatId(id), [], occlusionType)
}

function addChild(parent: DirectoryResource, child: DirectoryResource | SkeletonMeshResource): void {
  const header = new SectionHeader()
  header.sectionId = child.id
  header.sectionType = child instanceof DirectoryResource ? SectionTypes.S01_Directory : SectionTypes.S2A_SkeletonMesh
  parent.addChild(header, child)

  if ('localDir' in child) {
    child.localDir = parent
  }
}

function addEntry(
  parent: DirectoryResource,
  child: DirectoryResource | SkeletonMeshResource | SkeletonResource | SkeletonAnimationResource,
): void {
  const header = new SectionHeader()
  header.sectionId = child.id

  if (child instanceof DirectoryResource) {
    header.sectionType = SectionTypes.S01_Directory
  } else if (child instanceof SkeletonMeshResource) {
    header.sectionType = SectionTypes.S2A_SkeletonMesh
  } else if (child instanceof SkeletonResource) {
    header.sectionType = SectionTypes.S29_Skeleton
  } else {
    header.sectionType = SectionTypes.S2B_SkeletonAnimation
  }

  parent.addChild(header, child)
  if ('localDir' in child) {
    child.localDir = parent
  }
}

function makeModel(meshRoot: DirectoryResource): Model {
  return {
    isReadyToDraw: () => true,
    getMeshResources: () => [meshRoot],
    getSkeletonResource: () => null,
    getAnimationDirectories: () => [],
    getMainBattleAnimationDirectory: () => null,
    getSubBattleAnimationDirectory: () => null,
    getEquipmentModelResource: () => null,
    getMovementInfo: (): InfoDefinition | null => null,
    getMainWeaponInfo: (): InfoDefinition | null => null,
    getSubWeaponInfo: (): InfoDefinition | null => null,
    getRangedWeaponInfo: (): InfoDefinition | null => null,
    getBlurConfig: () => null,
    getScale: () => 1,
  }
}

describe('ActorModel', () => {
  it('hides ranged slot by default and supports visibility overrides', () => {
    const root = new DirectoryResource(null, new DatId('root'))
    const actor: RuntimeActor = {
      isDisplayEngagedOrEngaging: () => false,
      getMount: () => null,
    }

    const actorModel = new ActorModel(actor, makeModel(root))

    expect(actorModel.getHiddenSlotIds().has(2)).toBe(true)

    actorModel.setDefaultModelVisibility(new SlotVisibilityOverride(2, false, false))
    expect(actorModel.getHiddenSlotIds().has(2)).toBe(false)
  })

  it('filters hidden weapon mesh resources by slot id', () => {
    const root = new DirectoryResource(null, new DatId('root'))
    addChild(root, makeMeshResource('wep0'))
    addChild(root, makeMeshResource('wep1'))

    const actorModel = new ActorModel(
      {
        isDisplayEngagedOrEngaging: () => false,
        getMount: () => null,
      },
      makeModel(root),
    )

    actorModel.toggleModelVisibility(0, true)
    const meshes = actorModel.getMeshResources()

    expect(meshes.map((mesh) => mesh.id.id)).toEqual(['wep1'])
  })

  it('collects mesh resources when constructor identity differs after hot reload', () => {
    const root = new DirectoryResource(null, new DatId('root'))
    const nested = new DirectoryResource(root, new DatId('mesh'))

    const rootChildren = (root as unknown as { childrenByType: Map<Function, Map<string, unknown>> }).childrenByType
    rootChildren.set(DirectoryResource as unknown as Function, new Map([[nested.id.id, nested]]))

    const altMeshCtor = class SkeletonMeshResource {
      readonly id = new DatId('face')
      readonly meshes: unknown[] = []
      readonly occlusionType = 0
      localDir = nested
    }
    const altMesh = new altMeshCtor()
    const nestedChildren = (nested as unknown as { childrenByType: Map<Function, Map<string, unknown>> }).childrenByType
    nestedChildren.set(altMeshCtor as unknown as Function, new Map([[altMesh.id.id, altMesh]]))

    const actorModel = new ActorModel(
      {
        isDisplayEngagedOrEngaging: () => false,
        getMount: () => null,
      },
      makeModel(root),
    )

    expect(actorModel.getMeshResources().map((mesh) => mesh.id.id)).toContain('face')
  })

  it('initializes skeleton joints in t-pose when first updated', () => {
    const root = new DirectoryResource(null, new DatId('root'))
    const skeleton = new SkeletonResource(
      new DatId('skel'),
      [
        { parentIndex: -1, rotation: { x: 0, y: 0, z: 0, w: 1 }, translation: { x: 0, y: 0, z: 0 } },
        { parentIndex: 0, rotation: { x: 0, y: 0, z: 0, w: 1 }, translation: { x: 1, y: 0, z: 0 } },
      ],
      [],
      [],
    )
    addEntry(root, skeleton)

    const actorModel = new ActorModel(
      {
        isDisplayEngagedOrEngaging: () => false,
        getMount: () => null,
      },
      {
        ...makeModel(root),
        getSkeletonResource: () => skeleton,
      },
    )

    actorModel.update(0)
    const jointX = actorModel.getSkeleton()?.joints[1]?.worldPosition.x

    expect(jointX).toBeCloseTo(1, 5)
  })

  it('applies skeleton animation transforms while updating', () => {
    const meshRoot = new DirectoryResource(null, new DatId('mesh'))
    const animationRoot = new DirectoryResource(null, new DatId('anim'))

    const skeleton = new SkeletonResource(
      new DatId('skel'),
      [
        { parentIndex: -1, rotation: { x: 0, y: 0, z: 0, w: 1 }, translation: { x: 0, y: 0, z: 0 } },
        { parentIndex: 0, rotation: { x: 0, y: 0, z: 0, w: 1 }, translation: { x: 1, y: 0, z: 0 } },
      ],
      [],
      [],
    )
    addEntry(meshRoot, skeleton)

    const animation = new SkeletonAnimation(new DatId('idl0'), 2, 2, 1)
    animation.keyFrameSets.set(1, [
      {
        rotation: { x: 0, y: 0, z: 0, w: 1 },
        translation: { x: 2, y: 0, z: 0 },
        scale: { x: 1, y: 1, z: 1 },
      },
      {
        rotation: { x: 0, y: 0, z: 0, w: 1 },
        translation: { x: 2, y: 0, z: 0 },
        scale: { x: 1, y: 1, z: 1 },
      },
    ])
    addEntry(animationRoot, new SkeletonAnimationResource(new DatId('idl0'), animation))

    const actorModel = new ActorModel(
      {
        isDisplayEngagedOrEngaging: () => false,
        getMount: () => null,
      },
      {
        ...makeModel(meshRoot),
        getSkeletonResource: () => skeleton,
        getAnimationDirectories: () => [animationRoot],
      },
    )

    actorModel.setSkeletonAnimation(new DatId('idl0'), [animationRoot], LoopParams.lowPriorityLoop(), new TransitionParams(0, 0))
    actorModel.update(1)

    const jointX = actorModel.getSkeleton()?.joints[1]?.worldPosition.x
    expect(jointX).toBeCloseTo(3, 5)
  })

  it('sanitizes invalid animation transforms to avoid NaN skeleton poses', () => {
    const meshRoot = new DirectoryResource(null, new DatId('mesh'))
    const animationRoot = new DirectoryResource(null, new DatId('anim'))

    const skeleton = new SkeletonResource(
      new DatId('skel'),
      [
        { parentIndex: -1, rotation: { x: 0, y: 0, z: 0, w: 1 }, translation: { x: 0, y: 0, z: 0 } },
        { parentIndex: 0, rotation: { x: 0, y: 0, z: 0, w: 1 }, translation: { x: 1, y: 0, z: 0 } },
      ],
      [],
      [],
    )
    addEntry(meshRoot, skeleton)

    const animation = new SkeletonAnimation(new DatId('idl0'), 2, 2, 1)
    animation.keyFrameSets.set(1, [
      {
        rotation: { x: Number.NaN, y: 0, z: 0, w: 1 },
        translation: { x: Number.NaN, y: 0, z: 0 },
        scale: { x: 1, y: 1, z: 1 },
      },
      {
        rotation: { x: Number.NaN, y: 0, z: 0, w: 1 },
        translation: { x: Number.NaN, y: 0, z: 0 },
        scale: { x: 1, y: 1, z: 1 },
      },
    ])
    addEntry(animationRoot, new SkeletonAnimationResource(new DatId('idl0'), animation))

    const actorModel = new ActorModel(
      {
        isDisplayEngagedOrEngaging: () => false,
        getMount: () => null,
      },
      {
        ...makeModel(meshRoot),
        getSkeletonResource: () => skeleton,
        getAnimationDirectories: () => [animationRoot],
      },
    )

    actorModel.setSkeletonAnimation(new DatId('idl0'), [animationRoot], LoopParams.lowPriorityLoop(), new TransitionParams(0, 0))
    actorModel.update(1)

    const joint = actorModel.getSkeleton()?.joints[1]
    expect(Number.isFinite(joint?.worldPosition.x ?? Number.NaN)).toBe(true)
    expect(Number.isFinite(joint?.worldRotation.w ?? Number.NaN)).toBe(true)
  })
})
