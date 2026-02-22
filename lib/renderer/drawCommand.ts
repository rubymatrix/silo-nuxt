import type { SkeletonMeshResource } from '~/lib/resource/datResource'
import type { SkeletonInstance } from '~/lib/resource/skeletonInstance'
import type { Color4, FogParams, LightingParams, PointLightParams, Vector3Like } from './lights'
import type { BlurOffset } from './postProcess'

export interface DrawXimCommand {
  readonly meshes: readonly SkeletonMeshResource[]
  readonly skeleton: SkeletonInstance | null
  readonly translate: Vector3Like
  readonly rotationY: number
  readonly scale: Vector3Like
  readonly alpha: number
  readonly fogParams: FogParams
  readonly lightingParams: LightingParams
  readonly pointLights: readonly PointLightParams[]
  readonly blurOffsets?: readonly BlurOffset[]
  readonly colorMask?: Color4
}
