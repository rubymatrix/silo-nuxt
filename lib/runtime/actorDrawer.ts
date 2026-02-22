import { type SkeletonMeshResource } from '~/lib/resource/datResource'
import type { SkeletonInstance } from '~/lib/resource/skeletonInstance'
import type { Actor } from '~/lib/runtime/actor'
import type { ActorModel } from '~/lib/runtime/actorModel'

const TWO_PI = Math.PI * 2

let frameCounter = 0

export interface BlurOffset {
  readonly x: number
  readonly y: number
}

export interface DrawActorCommand {
  readonly meshes: readonly SkeletonMeshResource[]
  readonly translate: { x: number, y: number, z: number }
  readonly rotationY: number
  readonly scale: number
  readonly alpha: number
  readonly skeleton: SkeletonInstance | null
  readonly blurOffsets: readonly BlurOffset[]
}

export function updateActorDrawer(elapsedFrames: number): void {
  frameCounter += elapsedFrames
  if (frameCounter > 360) {
    frameCounter -= 360
  }
}

export function getBlurOffset(
  frameCounterDegrees: number,
  index: number,
  blurCount: number,
  blurOffset: number,
  zoomFactor: number,
): BlurOffset {
  const angle = TWO_PI * ((frameCounterDegrees / 360) + (index / Math.max(1, blurCount)))
  const scale = blurOffset / Math.max(1e-6, zoomFactor)
  return {
    x: Math.cos(angle) * scale,
    y: Math.sin(angle) * scale,
  }
}

export function buildActorDrawCommand(actor: Actor): DrawActorCommand | null {
  const actorModel = actor.actorModel
  if (!actorModel || !actor.isReadyToDraw() || !actor.state.visible) {
    return null
  }

  return {
    meshes: actorModel.getMeshResources(),
    translate: { ...actor.displayPosition },
    rotationY: actor.displayFacingDir,
    scale: actorModel.customModelSettings.scale,
    alpha: actor.renderState.effectColor.a / 255,
    skeleton: actorModel.getSkeleton(),
    blurOffsets: resolveBlurOffsets(actorModel),
  }
}

export function shouldDrawMainModel(actorModel: ActorModel): boolean {
  return !actorModel.customModelSettings.hideMain
}

function resolveBlurOffsets(actorModel: ActorModel): readonly BlurOffset[] {
  const blurConfig = actorModel.getBlurConfig()
  if (!blurConfig || blurConfig.blurs.length === 0) {
    return []
  }

  const zoomFactor = 24
  return blurConfig.blurs.map((blur, index) => getBlurOffset(frameCounter, index, blurConfig.blurs.length, blur.offset, zoomFactor))
}
