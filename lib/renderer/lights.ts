export interface Color4 {
  readonly r: number
  readonly g: number
  readonly b: number
  readonly a: number
}

export interface Vector3Like {
  readonly x: number
  readonly y: number
  readonly z: number
}

export interface PointLightParams {
  readonly position: Vector3Like
  readonly color: Color4
  readonly range: number
  readonly attenuationQuad: number
}

export interface DiffuseLightParams {
  readonly direction: Vector3Like
  readonly color: Color4
}

export interface LightingParams {
  readonly ambientColor: Color4
  readonly lights: readonly DiffuseLightParams[]
}

export interface FogParams {
  readonly start: number
  readonly end: number
  readonly color: Color4
}

export const noOpLighting: LightingParams = {
  ambientColor: { r: 1, g: 1, b: 1, a: 1 },
  lights: [],
}

export const noOpFog: FogParams = {
  start: Number.POSITIVE_INFINITY,
  end: Number.POSITIVE_INFINITY,
  color: { r: 0, g: 0, b: 0, a: 0 },
}
