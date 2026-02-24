/**
 * Environment manager — resolves time-of-day lighting, fog, skybox, and draw distance.
 *
 * Ported from xim/poc/EnvironmentManager.kt and xim/resource/EnvironmentSection.kt.
 *
 * Key responsibilities:
 * - Groups EnvironmentResource entries by weather type and hour-of-day
 * - Interpolates between adjacent hour entries based on current time
 * - Computes sun/moon direction from time-of-day
 * - Applies FFXI color bias to ambient and diffuse colors
 * - Resolves per-object environment overrides via environmentLink
 */

import type { BgraColor } from '~/lib/resource/byteReader'
import { DatId, DirectoryResource } from '~/lib/resource/datResource'
import { EnvironmentResource, type EnvironmentLighting, type LightConfig, type SkyBox, type SkyBoxSlice } from '~/lib/resource/zoneResource'
import type { Color4, DiffuseLightParams, FogParams, LightingParams, Vector3Like } from './lights'
import { collectByTypeRecursive } from '~/lib/runtime/resourceTree'

// ─── Constants ───────────────────────────────────────────────────────────────

const BIAS_THRESHOLD = 0xCC
const BIAS_THRESHOLD_F = BIAS_THRESHOLD / 0xFF
const COLOR_BIAS = [1.4, 1.36, 1.45] as const

const SECONDS_PER_DAY = 24 * 60 * 60

/** PI/2 sweep over 12 game-hours (21600 seconds). */
const SUN_ANGLE_PER_SECOND = (0.5 * Math.PI) / (6 * 60 * 60)

// ─── Public Types ────────────────────────────────────────────────────────────

export interface InterpolatedEnvironment {
  readonly terrainLighting: LightingParams
  readonly terrainFog: FogParams
  readonly modelLighting: LightingParams
  readonly modelFog: FogParams
  readonly skyBox: SkyBox
  readonly drawDistance: number
  readonly clearColor: Color4
  readonly indoors: boolean
}

// ─── Environment Manager ─────────────────────────────────────────────────────

export class EnvironmentManager {
  /** All environment resources from the zone DAT, keyed by environment id → hour. */
  private envByIdAndHour = new Map<string, EnvironmentResource[]>()

  /** The zone directory root for sub-environment lookups. */
  private directory: DirectoryResource | null = null

  /** Cache of resolved interpolated environments per (envId, timeMinutes) key. */
  private cache = new Map<string, InterpolatedEnvironment>()
  private cacheTimeMinutes = -1

  /**
   * Initialize from a parsed zone directory.
   * Collects all EnvironmentResource entries and groups them by parent directory id + hour.
   */
  init(directory: DirectoryResource): void {
    this.directory = directory
    this.envByIdAndHour.clear()
    this.cache.clear()
    this.cacheTimeMinutes = -1

    const allEnvResources = collectByTypeRecursive(directory, EnvironmentResource)

    // Group by the grandparent directory id (the environment id like "weat" or "ev01")
    // Structure: zone_root → [weat] → [suny] → [0600, 1200, 1800, ...]
    // We walk the directory tree to find environment directories properly.
    this.buildEnvLookup(directory)

    if (this.envByIdAndHour.size === 0 && allEnvResources.length > 0) {
      // Fallback: if the directory structure doesn't match expected layout,
      // just group all env resources under the "weat" key
      this.envByIdAndHour.set(DatId.weather.id, allEnvResources)
    }
  }

  /**
   * Resolve the interpolated environment for the default (weather) environment at a given time.
   */
  resolve(timeMinutes: number): InterpolatedEnvironment {
    return this.resolveForEnvId(DatId.weather.id, timeMinutes)
  }

  /**
   * Resolve the interpolated environment for a specific environment id.
   * Used for per-object environment overrides (e.g., "ev01" for indoor areas).
   */
  resolveForEnvId(envId: string, timeMinutes: number): InterpolatedEnvironment {
    const roundedTime = Math.floor(timeMinutes)
    if (roundedTime !== this.cacheTimeMinutes) {
      this.cache.clear()
      this.cacheTimeMinutes = roundedTime
    }

    const cacheKey = envId
    const cached = this.cache.get(cacheKey)
    if (cached) return cached

    const resources = this.envByIdAndHour.get(envId)
    if (!resources || resources.length === 0) {
      // Fallback to default weather environment
      if (envId !== DatId.weather.id) {
        return this.resolveForEnvId(DatId.weather.id, timeMinutes)
      }
      return defaultEnvironment()
    }

    const result = interpolateEnvironment(resources, timeMinutes)
    this.cache.set(cacheKey, result)
    return result
  }

  /**
   * Resolve terrain lighting for a zone object, respecting its environmentLink.
   */
  resolveTerrainForObject(environmentLink: string | null, timeMinutes: number): { lighting: LightingParams, fog: FogParams } {
    const envId = environmentLink ?? DatId.weather.id
    const env = this.resolveForEnvId(envId, timeMinutes)
    return { lighting: env.terrainLighting, fog: env.terrainFog }
  }

  /**
   * Resolve model lighting (for actors/characters).
   */
  resolveModelLighting(timeMinutes: number): { lighting: LightingParams, fog: FogParams } {
    const env = this.resolve(timeMinutes)
    return { lighting: env.modelLighting, fog: env.modelFog }
  }

  /** Get all available environment IDs (for debugging). */
  getAvailableEnvIds(): string[] {
    return Array.from(this.envByIdAndHour.keys())
  }

  private buildEnvLookup(root: DirectoryResource): void {
    // Look for well-known environment directories: [weat], [ev01], etc.
    for (const envDir of root.getSubDirectories()) {
      const envId = envDir.id.id
      // Each environment directory contains weather-type subdirectories
      // (e.g., [suny], [fine]) which contain hour-keyed EnvironmentResources
      const weatherDirs = envDir.getSubDirectories()
      if (weatherDirs.length === 0) continue

      // For now, use the first available weather type (default = "suny" or "fine")
      const preferredWeather = weatherDirs.find(d =>
        d.id.id === DatId.weatherSunny.id || d.id.id === DatId.weatherFine.id,
      ) ?? weatherDirs[0]!

      const envResources = preferredWeather.collectByType(EnvironmentResource)
      if (envResources.length > 0) {
        this.envByIdAndHour.set(envId, envResources)
      }
    }
  }
}

// ─── Interpolation ───────────────────────────────────────────────────────────

function interpolateEnvironment(resources: EnvironmentResource[], timeMinutes: number): InterpolatedEnvironment {
  // Sort resources by their hour-of-day
  const byHour = resources
    .map(r => ({ hour: r.id.isNumeric() ? r.id.toHourOfDay() : 0, resource: r }))
    .sort((a, b) => a.hour - b.hour)

  if (byHour.length === 0) return defaultEnvironment()
  if (byHour.length === 1) return envResourceToInterpolated(byHour[0]!.resource, timeMinutes)

  const currentHour = Math.floor(timeMinutes / 60)
  const currentMinuteInHour = timeMinutes % 60

  // Find floor and ceil entries
  const floorEntry = byHour.filter(e => e.hour <= currentHour).at(-1) ?? byHour.at(-1)!
  const ceilEntry = byHour.filter(e => e.hour > currentHour).at(0) ?? byHour.at(0)!

  if (floorEntry.hour === ceilEntry.hour) {
    return envResourceToInterpolated(floorEntry.resource, timeMinutes)
  }

  // Compute interpolation parameter
  const currentMinuteValue = currentHour * 60 + currentMinuteInHour
  const t0 = floorEntry.hour * 60
  const t1 = (ceilEntry.hour === 0 ? 24 : ceilEntry.hour) * 60
  const t = Math.max(0, Math.min(1, (currentMinuteValue - t0) / (t1 - t0)))

  return lerpEnvironment(floorEntry.resource, ceilEntry.resource, t, timeMinutes)
}

function envResourceToInterpolated(env: EnvironmentResource, timeMinutes: number): InterpolatedEnvironment {
  const timeSeconds = timeMinutes * 60
  const terrain = resolveLightingParams(env.environmentLighting.terrainLighting, env.environmentLighting.indoors, timeSeconds, false)
  const model = resolveLightingParams(env.environmentLighting.modelLighting, env.environmentLighting.indoors, timeSeconds, true)

  return {
    terrainLighting: terrain.lighting,
    terrainFog: terrain.fog,
    modelLighting: model.lighting,
    modelFog: model.fog,
    skyBox: env.skyBox,
    drawDistance: env.drawDistance,
    clearColor: envClearColor(env),
    indoors: env.environmentLighting.indoors,
  }
}

function lerpEnvironment(a: EnvironmentResource, b: EnvironmentResource, t: number, timeMinutes: number): InterpolatedEnvironment {
  const envA = envResourceToInterpolated(a, timeMinutes)
  const envB = envResourceToInterpolated(b, timeMinutes)

  return {
    terrainLighting: lerpLighting(envA.terrainLighting, envB.terrainLighting, t),
    terrainFog: lerpFog(envA.terrainFog, envB.terrainFog, t),
    modelLighting: lerpLighting(envA.modelLighting, envB.modelLighting, t),
    modelFog: lerpFog(envA.modelFog, envB.modelFog, t),
    skyBox: lerpSkyBox(envA.skyBox, envB.skyBox, t),
    drawDistance: lerp(envA.drawDistance, envB.drawDistance, t),
    clearColor: lerpColor4(envA.clearColor, envB.clearColor, t),
    indoors: envA.indoors,
  }
}

// ─── Lighting Resolution ─────────────────────────────────────────────────────

function resolveLightingParams(
  cfg: LightConfig,
  indoors: boolean,
  timeOfDaySeconds: number,
  isModelLighting: boolean,
): { lighting: LightingParams, fog: FogParams } {
  const ambient = ambientToColor4(cfg.ambientColor)
  const fog = fogFromConfig(cfg)

  if (indoors) {
    const direction = indoorDiffuseDirection(cfg.moonLightColor)
    const color = diffuseToColor4(cfg.sunLightColor, cfg.diffuseMultiplier)
    return {
      lighting: { ambientColor: ambient, lights: [{ direction, color }] },
      fog,
    }
  }

  const sunDir = sunDirection(timeOfDaySeconds)
  const moonDir: Vector3Like = { x: -sunDir.x, y: -sunDir.y, z: -sunDir.z }
  const sunColor = diffuseToColor4(cfg.sunLightColor, cfg.diffuseMultiplier)
  const moonColor = diffuseToColor4(cfg.moonLightColor, cfg.diffuseMultiplier)

  let lights: DiffuseLightParams[]
  if (isModelLighting) {
    lights = [modelLightMix(sunDir, sunColor, moonDir, moonColor, timeOfDaySeconds)]
  } else {
    lights = [
      { direction: sunDir, color: sunColor },
      { direction: moonDir, color: moonColor },
    ]
  }

  return { lighting: { ambientColor: ambient, lights }, fog }
}

// ─── Sun / Moon Direction ────────────────────────────────────────────────────

function sunDirection(timeOfDaySeconds: number): Vector3Like {
  const angle = timeOfDaySeconds * SUN_ANGLE_PER_SECOND
  const x = Math.sin(angle)
  const y = Math.cos(angle)
  const len = Math.hypot(x, y) || 1
  return { x: x / len, y: y / len, z: 0 }
}

function indoorDiffuseDirection(moonLightColor: BgraColor): Vector3Like {
  // Reinterpret unsigned byte as signed: bit-shift trick
  const dx = ((moonLightColor.r << 24) >> 24) / 128
  const dy = ((moonLightColor.g << 24) >> 24) / 128
  const dz = ((moonLightColor.b << 24) >> 24) / 128
  const len = Math.hypot(dx, dy, dz) || 1
  return { x: -dx / len, y: -dy / len, z: -dz / len }
}

// ─── Model Light Sun/Moon Blending ───────────────────────────────────────────

/**
 * For model lighting outdoors, blend between sun and moon based on time of day.
 * - Before 05:55 (minute 355): 100% moon
 * - 05:55-06:05 (355-365): linear interpolation moon→sun
 * - 06:05-17:55 (365-1075): 100% sun
 * - 17:55-18:05 (1075-1085): linear interpolation sun→moon
 * - After 18:05 (minute 1085): 100% moon
 */
function modelLightMix(
  sunDir: Vector3Like,
  sunColor: Color4,
  moonDir: Vector3Like,
  moonColor: Color4,
  timeOfDaySeconds: number,
): DiffuseLightParams {
  const currentMinute = Math.floor(timeOfDaySeconds / 60)

  let t: number
  if (currentMinute < 355) {
    t = 0
  } else if (currentMinute < 365) {
    t = (currentMinute - 355) / 10
  } else if (currentMinute < 1075) {
    t = 1
  } else if (currentMinute < 1085) {
    t = (1085 - currentMinute) / 10
  } else {
    t = 0
  }

  return {
    direction: lerpVec3(moonDir, sunDir, t),
    color: lerpColor4(moonColor, sunColor, t),
  }
}

// ─── Color Bias ──────────────────────────────────────────────────────────────

function ambientToColor4(c: BgraColor): Color4 {
  const applyBias = c.r < BIAS_THRESHOLD && c.g < BIAS_THRESHOLD && c.b < BIAS_THRESHOLD
  const bias = applyBias ? COLOR_BIAS : [1, 1, 1] as const
  return {
    r: Math.min(0.5, (bias[0] * c.r) / 510),
    g: Math.min(0.5, (bias[1] * c.g) / 510),
    b: Math.min(0.5, (bias[2] * c.b) / 510),
    a: Math.min(1, c.a / 128),
  }
}

function diffuseToColor4(c: BgraColor, intensity: number): Color4 {
  let r = (c.r / 255) * intensity
  let g = (c.g / 255) * intensity
  let b = (c.b / 255) * intensity
  const applyBias = r < BIAS_THRESHOLD_F && g < BIAS_THRESHOLD_F && b < BIAS_THRESHOLD_F
  if (applyBias) {
    r = Math.min(1, r * COLOR_BIAS[0])
    g = Math.min(1, g * COLOR_BIAS[1])
    b = Math.min(1, b * COLOR_BIAS[2])
  }
  return { r, g, b, a: 1 }
}

// ─── Fog ─────────────────────────────────────────────────────────────────────

function fogFromConfig(cfg: LightConfig): FogParams {
  return {
    start: Number.isFinite(cfg.fogStart) ? cfg.fogStart : 99999,
    end: Number.isFinite(cfg.fogEnd) ? cfg.fogEnd : 99999,
    color: {
      r: cfg.fogColor.r / 255,
      g: cfg.fogColor.g / 255,
      b: cfg.fogColor.b / 255,
      a: cfg.fogColor.a / 255,
    },
  }
}

// ─── Clear Color ─────────────────────────────────────────────────────────────

function envClearColor(env: EnvironmentResource): Color4 {
  if (env.environmentLighting.indoors) {
    return env.clearColor
  }
  // Outdoor: use the first skybox slice color (horizon)
  const c = env.skyBox.slices[0]?.color
  if (c) {
    return { r: c.r / 255, g: c.g / 255, b: c.b / 255, a: c.a / 255 }
  }
  return env.clearColor
}

// ─── Interpolation Helpers ───────────────────────────────────────────────────

function lerp(a: number, b: number, t: number): number {
  return a + (b - a) * t
}

function lerpColor4(a: Color4, b: Color4, t: number): Color4 {
  return {
    r: lerp(a.r, b.r, t),
    g: lerp(a.g, b.g, t),
    b: lerp(a.b, b.b, t),
    a: lerp(a.a, b.a, t),
  }
}

function lerpVec3(a: Vector3Like, b: Vector3Like, t: number): Vector3Like {
  return {
    x: lerp(a.x, b.x, t),
    y: lerp(a.y, b.y, t),
    z: lerp(a.z, b.z, t),
  }
}

function lerpLighting(a: LightingParams, b: LightingParams, t: number): LightingParams {
  const ambient = lerpColor4(a.ambientColor, b.ambientColor, t)
  const maxLights = Math.max(a.lights.length, b.lights.length)
  const lights: DiffuseLightParams[] = []
  for (let i = 0; i < maxLights; i++) {
    const la = a.lights[i]
    const lb = b.lights[i]
    if (la && lb) {
      lights.push({
        direction: lerpVec3(la.direction, lb.direction, t),
        color: lerpColor4(la.color, lb.color, t),
      })
    } else {
      lights.push(la ?? lb!)
    }
  }
  return { ambientColor: ambient, lights }
}

function lerpFog(a: FogParams, b: FogParams, t: number): FogParams {
  return {
    start: lerp(a.start, b.start, t),
    end: lerp(a.end, b.end, t),
    color: lerpColor4(a.color, b.color, t),
  }
}

function lerpSkyBox(a: SkyBox, b: SkyBox, t: number): SkyBox {
  const slices: SkyBoxSlice[] = a.slices.map((sa, i) => {
    const sb = b.slices[i]
    if (!sb) return sa
    return {
      color: {
        r: Math.round(lerp(sa.color.r, sb.color.r, t)),
        g: Math.round(lerp(sa.color.g, sb.color.g, t)),
        b: Math.round(lerp(sa.color.b, sb.color.b, t)),
        a: Math.round(lerp(sa.color.a, sb.color.a, t)),
      },
      elevation: lerp(sa.elevation, sb.elevation, t),
    }
  })
  return {
    radius: lerp(a.radius, b.radius, t),
    slices,
    spokes: a.spokes,
  }
}

// ─── Default Fallback ────────────────────────────────────────────────────────

function defaultEnvironment(): InterpolatedEnvironment {
  return {
    terrainLighting: { ambientColor: { r: 0.6, g: 0.6, b: 0.6, a: 1 }, lights: [] },
    terrainFog: { start: 99999, end: 99999, color: { r: 0, g: 0, b: 0, a: 0 } },
    modelLighting: { ambientColor: { r: 0.6, g: 0.6, b: 0.6, a: 1 }, lights: [] },
    modelFog: { start: 99999, end: 99999, color: { r: 0, g: 0, b: 0, a: 0 } },
    skyBox: { radius: 0, slices: [], spokes: 0 },
    drawDistance: 10000,
    clearColor: { r: 0, g: 0, b: 0, a: 1 },
    indoors: false,
  }
}
