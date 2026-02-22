import type { DrawXimCommand } from '~/lib/renderer/drawCommand'
import { noOpFog, noOpLighting, type FogParams, type LightingParams, type PointLightParams, type Vector3Like } from '~/lib/renderer/lights'
import type { Actor } from '~/lib/runtime/actor'
import { buildActorDrawCommand, shouldDrawMainModel } from '~/lib/runtime/actorDrawer'

export interface NoCollisionResult {
  readonly kind: 'NoCollision'
}

export interface TerrainCollisionResult {
  readonly kind: 'Terrain'
  readonly collisionProperties: ReadonlyMap<string, readonly unknown[]>
}

export interface ElevatorCollisionResult {
  readonly kind: 'Elevator'
  readonly elevatorId: number
  readonly collisionProperty: unknown | null
}

export type SceneCollisionResult = NoCollisionResult | TerrainCollisionResult | ElevatorCollisionResult

export const NoCollision: NoCollisionResult = {
  kind: 'NoCollision',
}

export type SceneAreaType = 'MainArea' | 'SubArea' | 'ShipArea' | 'UnknownArea'

export interface SceneAreaRenderContext {
  readonly id: string
  readonly areaType?: SceneAreaType
}

export interface SceneActorRenderContext {
  readonly area: SceneAreaRenderContext | null
  readonly collisionResult: SceneCollisionResult
}

export interface SceneLightingContext {
  readonly lightingParams: LightingParams
  readonly fogParams: FogParams
  readonly pointLights: readonly PointLightParams[]
}

export interface SceneLightingProvider {
  resolveActorLighting(actor: Actor, context: SceneActorRenderContext): SceneLightingContext
}

export interface SceneCullingOptions {
  readonly cameraPosition: Vector3Like
  readonly maxDistance?: number
  readonly maxVisible?: number
}

export interface RuntimeSceneOptions {
  readonly resolveActorLighting: SceneLightingProvider['resolveActorLighting']
  readonly defaultLighting?: SceneLightingContext
  readonly actorContexts?: ReadonlyMap<number, SceneActorRenderContext>
}

const defaultActorRenderContext: SceneActorRenderContext = {
  area: null,
  collisionResult: NoCollision,
}

const fallbackLighting: SceneLightingContext = {
  lightingParams: noOpLighting,
  fogParams: noOpFog,
  pointLights: [],
}

export function getVisibleActors(actors: readonly Actor[], options: SceneCullingOptions): readonly Actor[] {
  const maxDistance = options.maxDistance ?? Number.POSITIVE_INFINITY
  const maxVisible = options.maxVisible ?? Number.POSITIVE_INFINITY

  return actors
    .filter((actor) => actor.state.visible)
    .filter((actor) => distance(actor.displayPosition, options.cameraPosition) <= maxDistance)
    .sort((a, b) => distance(a.displayPosition, options.cameraPosition) - distance(b.displayPosition, options.cameraPosition))
    .slice(0, maxVisible)
}

export class RuntimeScene {
  private readonly actorContexts: Map<number, SceneActorRenderContext>
  private readonly defaultLighting: SceneLightingContext
  private readonly resolveActorLighting: SceneLightingProvider['resolveActorLighting']

  constructor(options: RuntimeSceneOptions) {
    this.resolveActorLighting = options.resolveActorLighting
    this.defaultLighting = options.defaultLighting ?? fallbackLighting
    this.actorContexts = new Map(options.actorContexts)
  }

  setActorRenderContext(actorId: number, context: SceneActorRenderContext): void {
    this.actorContexts.set(actorId, context)
  }

  clearActorRenderContext(actorId: number): void {
    this.actorContexts.delete(actorId)
  }

  buildDrawCommands(actors: readonly Actor[], culling: SceneCullingOptions): readonly DrawXimCommand[] {
    const visibleActors = getVisibleActors(actors, culling)
    const commands: DrawXimCommand[] = []

    for (const actor of visibleActors) {
      const drawActor = buildActorDrawCommand(actor)
      if (!drawActor) {
        continue
      }

      if (!actor.actorModel || !shouldDrawMainModel(actor.actorModel)) {
        continue
      }

      const context = this.actorContexts.get(actor.id.id) ?? defaultActorRenderContext
      const lighting = this.resolveActorLighting(actor, context) ?? this.defaultLighting

      commands.push({
        meshes: drawActor.meshes,
        skeleton: drawActor.skeleton,
        translate: drawActor.translate,
        rotationY: drawActor.rotationY,
        scale: { x: drawActor.scale, y: drawActor.scale, z: drawActor.scale },
        alpha: drawActor.alpha,
        fogParams: lighting.fogParams,
        lightingParams: lighting.lightingParams,
        pointLights: lighting.pointLights,
        blurOffsets: drawActor.blurOffsets,
      })
    }

    return commands
  }
}

function distance(a: Vector3Like, b: Vector3Like): number {
  return Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z)
}
