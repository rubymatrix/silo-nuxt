import { describe, expect, it } from 'vitest'

import type { DrawXimCommand } from '~/lib/renderer/drawCommand'
import { noOpFog, noOpLighting } from '~/lib/renderer/lights'
import { Actor, ActorId, type ActorState } from '~/lib/runtime/actor'
import { NoOpActorController } from '~/lib/runtime/actorController'
import type { ActorModel } from '~/lib/runtime/actorModel'
import {
  NoCollision,
  RuntimeScene,
  type SceneActorRenderContext,
  getVisibleActors,
} from '~/lib/runtime/scene'

function createActor(id: number, position: { x: number, y: number, z: number }, visible = true): Actor {
  const state: ActorState = {
    id: new ActorId(id),
    position: { ...position },
    velocity: { x: 0, y: 0, z: 0 },
    rotation: 0,
    visible,
    movementSpeed: 0,
  }

  return new Actor(state, new NoOpActorController(), () => null)
}

function attachActorModel(actor: Actor, ready = true, hideMain = false): void {
  const model = {
    model: {
      isReadyToDraw: () => ready,
    },
    customModelSettings: {
      scale: 1,
      hideMain,
    },
    getMeshResources: () => [],
    getSkeleton: () => null,
    getBlurConfig: () => null,
  } as unknown as ActorModel

  actor.actorModel = model
}

describe('scene visibility', () => {
  it('filters by visibility and distance and sorts nearest first', () => {
    const near = createActor(1, { x: 3, y: 0, z: 0 }, true)
    const medium = createActor(2, { x: 7, y: 0, z: 0 }, true)
    const far = createActor(3, { x: 30, y: 0, z: 0 }, true)
    const hidden = createActor(4, { x: 2, y: 0, z: 0 }, false)

    const visibleActors = getVisibleActors([far, medium, hidden, near], {
      cameraPosition: { x: 0, y: 0, z: 0 },
      maxDistance: 10,
      maxVisible: 2,
    })

    expect(visibleActors.map((actor) => actor.id.id)).toEqual([1, 2])
  })
})

describe('scene draw command generation', () => {
  it('builds DrawXimCommand values with scene lighting and culling', () => {
    const actorIncluded = createActor(1, { x: 0, y: 0, z: 0 }, true)
    attachActorModel(actorIncluded, true, false)

    const actorHiddenMain = createActor(2, { x: 1, y: 0, z: 0 }, true)
    attachActorModel(actorHiddenMain, true, true)

    const actorNotReady = createActor(3, { x: 2, y: 0, z: 0 }, true)
    attachActorModel(actorNotReady, false, false)

    const actorOutsideRange = createActor(4, { x: 50, y: 0, z: 0 }, true)
    attachActorModel(actorOutsideRange, true, false)

    const lightingResult = {
      lightingParams: {
        ambientColor: { r: 0.3, g: 0.4, b: 0.5, a: 1 },
        lights: [{ direction: { x: 0, y: -1, z: 0 }, color: { r: 0.7, g: 0.8, b: 0.9, a: 1 } }],
      },
      fogParams: { start: 4, end: 60, color: { r: 0.1, g: 0.2, b: 0.3, a: 1 } },
      pointLights: [{ position: { x: 1, y: 2, z: 3 }, color: { r: 1, g: 0, b: 0, a: 1 }, range: 8, attenuationQuad: 0.2 }],
    }

    const contexts = new Map<number, SceneActorRenderContext>([
      [1, { area: { id: 'main' }, collisionResult: NoCollision }],
    ])

    const resolvedContexts: SceneActorRenderContext[] = []
    const scene = new RuntimeScene({
      resolveActorLighting: (_actor, context) => {
        resolvedContexts.push(context)
        return lightingResult
      },
      defaultLighting: {
        lightingParams: noOpLighting,
        fogParams: noOpFog,
        pointLights: [],
      },
      actorContexts: contexts,
    })

    const commands = scene.buildDrawCommands([actorIncluded, actorHiddenMain, actorNotReady, actorOutsideRange], {
      cameraPosition: { x: 0, y: 0, z: 0 },
      maxDistance: 10,
      maxVisible: 10,
    })

    expect(commands).toHaveLength(1)
    expect(commands[0]).toMatchObject<Partial<DrawXimCommand>>({
      translate: { x: 0, y: 0, z: 0 },
      rotationY: 0,
      alpha: 1,
      scale: { x: 1, y: 1, z: 1 },
      lightingParams: lightingResult.lightingParams,
      fogParams: lightingResult.fogParams,
      pointLights: lightingResult.pointLights,
    })
    expect(resolvedContexts).toEqual([{ area: { id: 'main' }, collisionResult: NoCollision }])
  })
})
