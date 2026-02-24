/**
 * Zone-specific resource types.
 *
 * Ported from xim/resource/DatResource.kt (zone-related classes)
 * and xim/resource/ZoneDefParser.kt (data types).
 */

import type { BgraColor, Vector3 } from './byteReader'
import { DatResource, type DatEntry, type DatId, type MeshRenderState } from './datResource'
import type { TextureLink } from './textureLink'

// ─── Zone Mesh ───────────────────────────────────────────────────────────────

export interface ZoneMeshVertex {
  readonly p0: Vector3
  readonly p1: Vector3
  readonly normal: Vector3
  readonly color: BgraColor
  readonly u: number
  readonly v: number
}

export interface ZoneMeshBoundingBox {
  readonly p0: Vector3
  readonly p1: Vector3
}

export interface ZoneMeshBuffer {
  readonly numVertices: number
  readonly meshType: 'TriMesh' | 'TriStrip'
  readonly textureName: string
  readonly textureLink: TextureLink | null
  readonly vertices: readonly ZoneMeshVertex[]
  readonly indices: readonly number[]
  readonly tangents: readonly Vector3[]
  readonly renderState: MeshRenderState
  readonly vertexBlendEnabled: boolean
}

export class ZoneMeshResource extends DatResource {
  readonly id: DatId
  readonly name: string
  readonly buffers: readonly ZoneMeshBuffer[]
  readonly boundingBox0: ZoneMeshBoundingBox | null
  readonly boundingBox1: ZoneMeshBoundingBox | null

  constructor(
    id: DatId,
    name: string,
    buffers: readonly ZoneMeshBuffer[],
    boundingBox0: ZoneMeshBoundingBox | null,
    boundingBox1: ZoneMeshBoundingBox | null,
  ) {
    super()
    this.id = id
    this.name = name
    this.buffers = buffers
    this.boundingBox0 = boundingBox0
    this.boundingBox1 = boundingBox1
  }

  resourceName(): string {
    return this.name
  }
}

// ─── Zone Object ─────────────────────────────────────────────────────────────

export interface ZoneObject {
  readonly index: number
  readonly objectId: string
  readonly fileOffset: number
  readonly position: Vector3
  readonly rotation: Vector3
  readonly scale: Vector3
  readonly highDefThreshold: number
  readonly midDefThreshold: number
  readonly lowDefThreshold: number
  readonly pointLightIndices: readonly number[]
  readonly skipDuringDecalRendering: boolean
  readonly cullingTableIndex: number | null
  readonly effectLink: string | null
  readonly environmentLink: string | null
  readonly fileIdLink: number | null
}

// ─── Collision Mesh ──────────────────────────────────────────────────────────

export enum TerrainType {
  Object = 0,
  Path = 1,
  Grass = 2,
  Sand = 3,
  Snow = 4,
  Stone = 5,
  Metal = 6,
  Wood = 7,
  ShallowWater = 8,
  DeepWater = 9,
  Unknown = 10,
}

export function terrainTypeFromFlags(f0: number, f1: number, f2: number, f3: number): TerrainType {
  const index = ((f0 & 0x8) >>> 3) + ((f1 & 0x8) >>> 2) + ((f2 & 0x8) >>> 1) + (f3 & 0x8)
  if (index >= 0 && index <= 10) return index as TerrainType
  console.warn(`Unknown terrain type index: ${index}`)
  return TerrainType.Unknown
}

export interface CollisionTriangle {
  readonly p0: Vector3
  readonly p1: Vector3
  readonly p2: Vector3
  readonly normal: Vector3
  readonly terrainType: TerrainType
  readonly hitWall: boolean
}

export interface CollisionMesh {
  readonly fileOffset: number
  readonly triangles: readonly CollisionTriangle[]
}

export interface Matrix4x4 {
  readonly m: readonly number[] // 16 floats in column-major order
}

export interface CollisionTransformInfo {
  readonly fileOffset: number
  readonly toWorldSpace: Matrix4x4
  readonly toCollisionSpace: Matrix4x4
  readonly cullingTableIndex: number | null
  readonly lightIndices: readonly number[]
  readonly environmentId: string | null
  readonly subAreaLinkId: number | null
  readonly mapId: number
}

export interface CollisionObject {
  readonly fileOffset: number
  readonly collisionMesh: CollisionMesh
  readonly transformInfo: CollisionTransformInfo
}

export interface CollisionObjectGroup {
  readonly fileOffset: number
  readonly collisionObjects: readonly CollisionObject[]
}

export interface CollisionMap {
  readonly numBlocksWide: number
  readonly numBlocksLong: number
  readonly blockWidth: number
  readonly blockLength: number
  readonly subBlocksX: number
  readonly subBlocksZ: number
  readonly entries: readonly (readonly (CollisionObjectGroup | null)[])[]
}

// ─── Space Partitioning ──────────────────────────────────────────────────────

export interface SpacePartitioningNode {
  readonly leafNode: boolean
  readonly containedObjects: ReadonlySet<number>
  readonly children: readonly (SpacePartitioningNode | null)[]
}

// ─── Zone Resource ───────────────────────────────────────────────────────────

export class ZoneResource extends DatResource {
  readonly id: DatId
  readonly zoneObjects: readonly ZoneObject[]
  readonly collisionGroups: readonly CollisionObjectGroup[]
  readonly collisionMap: CollisionMap | null
  readonly cullingTables: readonly ReadonlySet<number>[]
  readonly spaceTreeRoot: SpacePartitioningNode | null
  readonly pointLightLinks: readonly string[]

  constructor(
    id: DatId,
    zoneObjects: readonly ZoneObject[],
    collisionGroups: readonly CollisionObjectGroup[],
    collisionMap: CollisionMap | null,
    cullingTables: readonly ReadonlySet<number>[],
    spaceTreeRoot: SpacePartitioningNode | null,
    pointLightLinks: readonly string[],
  ) {
    super()
    this.id = id
    this.zoneObjects = zoneObjects
    this.collisionGroups = collisionGroups
    this.collisionMap = collisionMap
    this.cullingTables = cullingTables
    this.spaceTreeRoot = spaceTreeRoot
    this.pointLightLinks = pointLightLinks
  }
}

// ─── Environment Resource ────────────────────────────────────────────────────

export interface SkyBoxSlice {
  readonly color: BgraColor
  readonly elevation: number
}

export interface SkyBox {
  readonly radius: number
  readonly slices: readonly SkyBoxSlice[]
  readonly spokes: number
}

export interface LightConfig {
  readonly sunLightColor: BgraColor
  readonly moonLightColor: BgraColor
  readonly ambientColor: BgraColor
  readonly fogColor: BgraColor
  readonly fogEnd: number
  readonly fogStart: number
  readonly diffuseMultiplier: number
}

export interface EnvironmentLighting {
  readonly modelLighting: LightConfig
  readonly terrainLighting: LightConfig
  readonly indoors: boolean
}

export interface EnvironmentColor {
  readonly r: number
  readonly g: number
  readonly b: number
  readonly a: number
}

export class EnvironmentResource extends DatResource {
  readonly id: DatId
  readonly skyBox: SkyBox
  readonly environmentLighting: EnvironmentLighting
  readonly drawDistance: number
  readonly clearColor: EnvironmentColor

  constructor(
    id: DatId,
    skyBox: SkyBox,
    environmentLighting: EnvironmentLighting,
    drawDistance: number,
    clearColor: EnvironmentColor,
  ) {
    super()
    this.id = id
    this.skyBox = skyBox
    this.environmentLighting = environmentLighting
    this.drawDistance = drawDistance
    this.clearColor = clearColor
  }
}
