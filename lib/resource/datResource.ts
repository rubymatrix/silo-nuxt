import type { SectionHeader } from './datParser'

export class DatId {
  static readonly info = new DatId('info')
  static readonly mount = new DatId('moun')
  static readonly effect = new DatId('effe')
  static readonly zero = new DatId('\u0000\u0000\u0000\u0000')

  readonly id: string

  constructor(id: string) {
    this.id = id
  }

  static base36ToChar(value: number): string {
    if (value >= 0 && value < 10) {
      return String.fromCharCode('0'.charCodeAt(0) + value)
    }

    if (value >= 10 && value < 36) {
      return String.fromCharCode('a'.charCodeAt(0) + (value - 10))
    }

    throw new Error(`Illegal base36 value: ${value}`)
  }

  isZero(): boolean {
    return this.id === DatId.zero.id
  }

  toString(): string {
    return this.id
  }
}

export interface DatEntry {
  readonly id: DatId
  release?(): void
  resourceName?(): string | null
  combine?(datEntry: DatEntry): boolean | null
}

export interface TextureData {
  readonly textureType: number
  readonly width: number
  readonly height: number
  readonly bitCount: number
  readonly dxtType: 'DXT1' | 'DXT3' | null
  readonly rawData: Uint8Array
}

export interface Quaternion {
  readonly x: number
  readonly y: number
  readonly z: number
  readonly w: number
}

export interface Vector3 {
  x: number
  y: number
  z: number
}

export interface BoundingBox {
  readonly min: Vector3
  readonly max: Vector3
}

export interface Joint {
  readonly rotation: Quaternion
  readonly translation: Vector3
  readonly parentIndex: number
}

export interface JointReference {
  readonly index: number
  readonly unkV0: Vector3
  readonly positionOffset: Vector3
  readonly fileOffset: number
}

export interface SkeletonAnimationKeyFrameTransform {
  readonly rotation: Quaternion
  readonly translation: Vector3
  readonly scale: Vector3
}

export interface SkeletonVertex {
  readonly position0: Vector3
  readonly position1: Vector3
  readonly normal0: Vector3
  readonly normal1: Vector3
  readonly joint0Weight: number
  readonly joint1Weight: number
  readonly jointIndex0: number | null
  readonly jointIndex1: number | null
  readonly u: number
  readonly v: number
  readonly color?: import('./byteReader').BgraColor
  readonly flipAxis0?: number
  readonly flipAxis1?: number
  readonly flippedJointIndex0?: number | null
  readonly flippedJointIndex1?: number | null
}

/**
 * Per-mesh GPU render state, ported from Kotlin's `RenderState` data class
 * in `MeshBuffers.kt`.
 *
 * Controls alpha blending, fragment discard, and depth mask on a per-mesh basis.
 * Skeleton meshes default to `discardThreshold = 69/255`, no blending, depth on.
 * Weighted meshes use `discardThreshold = 0.375` when `enableAlphaDiscard` is set.
 * Zone meshes may enable blending with z-bias and back-face culling.
 */
export interface MeshRenderState {
  /** When true, enable GL alpha blending (src-alpha, one-minus-src-alpha). */
  readonly blendEnabled: boolean
  /** Fragment alpha below this value is discarded. Null means no discard (threshold = 0). */
  readonly discardThreshold: number | null
  /** Controls gl.depthMask(). When blendEnabled is true, Kotlin forces this to false. */
  readonly depthMask: boolean
}

/**
 * Default render state for skeleton meshes.
 * Matches Kotlin: `RenderState(discardThreshold = 69f/255f)` with all other fields defaulted.
 */
export const SKELETON_MESH_RENDER_STATE: MeshRenderState = {
  blendEnabled: false,
  discardThreshold: 69 / 255,
  depthMask: true,
}

/**
 * Default render state for opaque meshes with no alpha discard.
 * Matches Kotlin: `RenderState()` with all defaults.
 */
export const DEFAULT_MESH_RENDER_STATE: MeshRenderState = {
  blendEnabled: false,
  discardThreshold: null,
  depthMask: true,
}

export interface SkinnedMesh {
  readonly meshType: 'TriStrip' | 'TriMesh'
  readonly textureName: string
  readonly vertexCount: number
  readonly vertices: readonly SkeletonVertex[]
  readonly renderState: MeshRenderState
}

export interface WeightedChunk {
  readonly positions: readonly Vector3[]
  readonly normals: readonly Vector3[]
}

export interface WeightedMeshTriangle {
  readonly position: Vector3
  readonly normal: Vector3
  readonly color: import('./byteReader').BgraColor
  readonly texCoord: import('./byteReader').Vector2
}

export interface WeightedMeshSectionParserContext {
  readonly zoneResource: boolean
}

export abstract class DatResource implements DatEntry {
  abstract readonly id: DatId
  localDir!: DirectoryResource

  rootDirectory(): DirectoryResource {
    let dir = this.localDir
    while (dir.parent !== null) {
      dir = dir.parent
    }
    return dir
  }

  path(): string {
    const path = [this.id.id]
    let dir: DirectoryResource | null | undefined = this.localDir

    while (dir != null) {
      path.push(dir.id.id)
      dir = dir.parent
    }

    return path.reverse().join('/')
  }
}

type DatCtor<T extends DatEntry> = new (...args: never[]) => T

export class DirectoryResource implements DatEntry {
  private static readonly globalDir = new DirectoryResource(null, DatId.zero)

  readonly parent: DirectoryResource | null
  readonly id: DatId

  private readonly childrenByType = new Map<Function, Map<string, DatEntry>>()
  private readonly texturesByName = new Map<string, TextureResource>()

  constructor(parent: DirectoryResource | null, id: DatId) {
    this.parent = parent
    this.id = id
  }

  static getGlobalTexture(name: string): TextureResource | null {
    return DirectoryResource.globalDir.getTextureResourceByNameAs(name)
  }

  addChild(sectionHeader: SectionHeader, datEntry: DatEntry): void {
    const typeMap = this.childrenByType.get(sectionHeader.sectionType.resourceType) ?? new Map<string, DatEntry>()
    this.childrenByType.set(sectionHeader.sectionType.resourceType, typeMap)

    const key = sectionHeader.sectionId.id
    const existing = typeMap.get(key)

    if (existing) {
      const combined = existing.combine?.(datEntry)
      if (combined) {
        return
      }
    }

    typeMap.set(key, datEntry)

    if (datEntry instanceof TextureResource) {
      this.trackTextureResourceByName(datEntry)
    }
  }

  hasSubDirectory(childId: DatId): boolean {
    return this.getNullableSubDirectory(childId) !== null
  }

  getSubDirectory(childId: DatId): DirectoryResource {
    const child = this.getNullableSubDirectory(childId)
    if (child === null) {
      throw new Error(`[${this.id}] No such child-dir [${childId}]`)
    }
    return child
  }

  getNullableSubDirectory(childId: DatId): DirectoryResource | null {
    return this.getNullableChildAs(childId, DirectoryResource)
  }

  getSubDirectories(): DirectoryResource[] {
    const map = this.resolveTypeMap(DirectoryResource)
    if (!map) {
      return []
    }

    return Array.from(map.values()).filter(
      (entry): entry is DirectoryResource => entry instanceof DirectoryResource || entry.constructor.name === DirectoryResource.name,
    )
  }

  collectByType<T extends DatEntry>(type: DatCtor<T>): T[] {
    const map = this.resolveTypeMap(type)
    if (!map) {
      return []
    }

    return Array.from(map.values()).filter(
      (entry): entry is T => entry instanceof type || entry.constructor.name === type.name,
    )
  }

  getChildAs<T extends DatEntry>(childId: DatId, type: DatCtor<T>): T {
    const child = this.getNullableChildAs(childId, type)
    if (!child) {
      throw new Error(`No such child '${childId}' in '${this.id}' of type ${type.name}`)
    }
    return child
  }

  getNullableChildAs<T extends DatEntry>(childId: DatId, type: DatCtor<T>): T | null {
    const children = this.resolveTypeMap(type)
    const child = children?.get(childId.id)
    if (!child) {
      return null
    }
    if (!(child instanceof type) && child.constructor.name !== type.name) {
      throw new Error(`Child [${child.id}] is not a ${type.name}`)
    }
    return child as T
  }

  private resolveTypeMap<T extends DatEntry>(type: DatCtor<T>): Map<string, DatEntry> | undefined {
    const direct = this.childrenByType.get(type)
    if (direct) {
      return direct
    }

    for (const [ctor, entries] of this.childrenByType.entries()) {
      if (ctor.name === type.name) {
        return entries
      }
    }

    return undefined
  }

  searchLocalAndParentsByName(name: string): TextureResource | null {
    const local = this.getTextureResourceByNameAs(name)
    if (local) {
      return local
    }

    if (this.parent !== null) {
      return this.parent.searchLocalAndParentsByName(name)
    }

    return null
  }

  private getTextureResourceByNameAs(name: string): TextureResource | null {
    const fullMatch = this.texturesByName.get(name)
    if (fullMatch) {
      return fullMatch
    }

    const localName = this.localTextureName(name)
    for (const [key, texture] of this.texturesByName.entries()) {
      if (this.localTextureName(key) === localName) {
        return texture
      }
    }

    return null
  }

  private localTextureName(name: string): string {
    if (name.length >= 16) {
      return name.slice(8, 16)
    }
    return name
  }

  private trackTextureResourceByName(textureResource: TextureResource): void {
    this.texturesByName.set(textureResource.name, textureResource)

    if (this !== DirectoryResource.globalDir) {
      DirectoryResource.globalDir.texturesByName.set(textureResource.name, textureResource)
    }
  }

  combine(): boolean {
    return true
  }

  release(): void {
    for (const children of this.childrenByType.values()) {
      for (const child of children.values()) {
        child.release?.()
      }
    }
  }
}

export enum RangeType {
  None = 'None',
  Wind = 'Wind',
  String = 'String',
  Marksmanship = 'Marksmanship',
  ThrowingWeapon = 'ThrowingWeapon',
  ThrowingAmmo = 'ThrowingAmmo',
  Archery = 'Archery',
  HandbellIndi = 'HandbellIndi',
  HandbellGeo = 'HandbellGeo',
  Unset = 'Unset',
}

export function rangeTypeFromIndex(index: number): RangeType | null {
  switch (index) {
    case 0x00:
      return RangeType.None
    case 0x01:
      return RangeType.Wind
    case 0x02:
      return RangeType.String
    case 0x03:
      return RangeType.Marksmanship
    case 0x04:
      return RangeType.ThrowingWeapon
    case 0x05:
      return RangeType.ThrowingAmmo
    case 0x06:
      return RangeType.Archery
    case 0x0a:
      return RangeType.HandbellIndi
    case 0x0b:
      return RangeType.HandbellGeo
    case 0xff:
      return RangeType.Unset
    default:
      return null
  }
}

export enum MovementType {
  Walking = 'Walking',
  Sliding = 'Sliding',
  Large = 'Large',
  Flying = 'Flying',
  Unset = 'Unset',
}

export function movementTypeFromIndex(index: number): MovementType {
  switch (index) {
    case 0x00:
      return MovementType.Walking
    case 0x01:
      return MovementType.Sliding
    case 0x02:
      return MovementType.Large
    case 0x03:
      return MovementType.Flying
    case 0xff:
      return MovementType.Unset
    default:
      throw new Error(`Unknown movement type: ${index.toString(16)}`)
  }
}

export interface InfoDefinition {
  readonly movementType: MovementType
  readonly movementChar: string
  readonly shakeFactor: number
  readonly weaponAnimationType: number
  readonly weaponAnimationSubType: number
  readonly standardJointIndex: number | null
  readonly scale: number | null
  readonly staticNpcScale: number | null
  readonly rangeType: RangeType
}

export interface MountDefinition {
  readonly rotation: number
  readonly poseType: number
}

export class InfoResource extends DatResource {
  readonly id: DatId
  readonly infoDefinition: InfoDefinition
  readonly mountDefinition: MountDefinition

  constructor(id: DatId, infoDefinition: InfoDefinition, mountDefinition: MountDefinition) {
    super()
    this.id = id
    this.infoDefinition = infoDefinition
    this.mountDefinition = mountDefinition
  }
}

export class TextureResource extends DatResource {
  readonly id: DatId
  readonly name: string
  readonly textureType: number
  readonly width: number
  readonly height: number
  readonly bitCount: number
  readonly dxtType: 'DXT1' | 'DXT3' | null
  readonly rawData: Uint8Array

  constructor(id: DatId, name: string, textureData: TextureData) {
    super()
    this.id = id
    this.name = name
    this.textureType = textureData.textureType
    this.width = textureData.width
    this.height = textureData.height
    this.bitCount = textureData.bitCount
    this.dxtType = textureData.dxtType
    this.rawData = textureData.rawData
  }

  resourceName(): string {
    return this.name
  }
}

export class SkeletonResource extends DatResource {
  readonly id: DatId
  readonly joints: readonly Joint[]
  readonly jointReferences: readonly JointReference[]
  readonly boundingBoxes: readonly BoundingBox[]
  readonly size: Vector3

  constructor(id: DatId, joints: readonly Joint[], jointReferences: readonly JointReference[], boundingBoxes: readonly BoundingBox[]) {
    super()
    this.id = id
    this.joints = joints
    this.jointReferences = jointReferences
    this.boundingBoxes = boundingBoxes
    this.size = { x: 0, y: 0, z: 0 }
  }
}

export class SkeletonAnimation {
  readonly id: DatId
  readonly numJoints: number
  readonly numFrames: number
  readonly keyFrameDuration: number
  readonly keyFrameSets = new Map<number, readonly SkeletonAnimationKeyFrameTransform[]>()

  constructor(id: DatId, numJoints: number, numFrames: number, keyFrameDuration: number) {
    this.id = id
    this.numJoints = numJoints
    this.numFrames = numFrames
    this.keyFrameDuration = keyFrameDuration
  }

  getJointTransform(jointIndex: number, frame: number): SkeletonAnimationKeyFrameTransform | null {
    const keyFrames = this.keyFrameSets.get(jointIndex)
    if (!keyFrames || keyFrames.length === 0) {
      return null
    }

    const scaledFrame = frame * this.keyFrameDuration
    if (scaledFrame >= this.numFrames - 1) {
      return keyFrames[this.numFrames - 1] ?? null
    }

    const lower = Math.floor(scaledFrame)
    const upper = lower + 1
    const delta = scaledFrame - lower
    const a = keyFrames[lower]
    const b = keyFrames[upper]
    if (!a || !b) {
      return a ?? null
    }

    return {
      rotation: nlerpQuaternion(a.rotation, b.rotation, delta),
      translation: {
        x: a.translation.x + (b.translation.x - a.translation.x) * delta,
        y: a.translation.y + (b.translation.y - a.translation.y) * delta,
        z: a.translation.z + (b.translation.z - a.translation.z) * delta,
      },
      scale: {
        x: a.scale.x + (b.scale.x - a.scale.x) * delta,
        y: a.scale.y + (b.scale.y - a.scale.y) * delta,
        z: a.scale.z + (b.scale.z - a.scale.z) * delta,
      },
    }
  }

  getLengthInFrames(): number {
    return Math.max(1, this.numFrames - 1) / this.keyFrameDuration
  }
}

function dotQuaternion(a: Quaternion, b: Quaternion): number {
  return a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
}

function normalizeQuaternion(q: Quaternion): Quaternion {
  const magnitude = Math.hypot(q.x, q.y, q.z, q.w)
  if (magnitude <= 1e-7) {
    return { x: 0, y: 0, z: 0, w: 1 }
  }
  return {
    x: q.x / magnitude,
    y: q.y / magnitude,
    z: q.z / magnitude,
    w: q.w / magnitude,
  }
}

function nlerpQuaternion(a: Quaternion, b: Quaternion, t: number): Quaternion {
  const dot = dotQuaternion(a, b)
  const rhs = dot < 0
    ? { x: -b.x, y: -b.y, z: -b.z, w: -b.w }
    : b

  return normalizeQuaternion({
    x: a.x * (1 - t) + rhs.x * t,
    y: a.y * (1 - t) + rhs.y * t,
    z: a.z * (1 - t) + rhs.z * t,
    w: a.w * (1 - t) + rhs.w * t,
  })
}

export class SkeletonAnimationResource extends DatResource {
  readonly id: DatId
  readonly animation: SkeletonAnimation

  constructor(id: DatId, animation: SkeletonAnimation) {
    super()
    this.id = id
    this.animation = animation
  }
}

export class SkeletonMeshResource extends DatResource {
  readonly id: DatId
  readonly meshes: readonly SkinnedMesh[]
  readonly occlusionType: number

  constructor(id: DatId, meshes: readonly SkinnedMesh[], occlusionType: number) {
    super()
    this.id = id
    this.meshes = meshes
    this.occlusionType = occlusionType
  }
}

export class WeightedMesh {
  readonly id: DatId
  readonly textureName: string
  readonly localDir: DirectoryResource
  readonly numPrimitives: number
  readonly chunks: readonly WeightedChunk[]
  readonly colors: readonly import('./byteReader').BgraColor[]
  readonly texCoords: readonly import('./byteReader').Vector2[]
  readonly positionIndices: readonly number[]
  readonly normalIndices: readonly number[]
  readonly enableAlphaDiscard: boolean

  constructor(
    id: DatId,
    textureName: string,
    localDir: DirectoryResource,
    numPrimitives: number,
    chunks: readonly WeightedChunk[],
    colors: readonly import('./byteReader').BgraColor[],
    texCoords: readonly import('./byteReader').Vector2[],
    positionIndices: readonly number[],
    normalIndices: readonly number[],
    enableAlphaDiscard: boolean,
  ) {
    this.id = id
    this.textureName = textureName
    this.localDir = localDir
    this.numPrimitives = numPrimitives
    this.chunks = chunks
    this.colors = colors
    this.texCoords = texCoords
    this.positionIndices = positionIndices
    this.normalIndices = normalIndices
    this.enableAlphaDiscard = enableAlphaDiscard
  }

  getWeightedTriangles(weights: readonly number[]): WeightedMeshTriangle[] {
    const sliced = weights.slice(0, this.chunks.length)
    const sum = sliced.reduce((acc, value) => acc + value, 0)
    const normalized = sum <= 1e-7
      ? sliced.map((_, index) => (index === 0 ? 1 : 0))
      : sliced.map((value) => value / sum)

    const triangles: WeightedMeshTriangle[] = []
    for (let i = 0; i < this.numPrimitives * 3; i += 1) {
      let px = 0
      let py = 0
      let pz = 0
      let nx = 0
      let ny = 0
      let nz = 0

      for (let j = 0; j < this.chunks.length; j += 1) {
        const weight = normalized[j] ?? 0
        const position = this.chunks[j]?.positions[this.positionIndices[i] ?? 0]
        const normal = this.chunks[j]?.normals[this.normalIndices[i] ?? 0]
        if (!position || !normal) {
          continue
        }

        px += position.x * weight
        py += position.y * weight
        pz += position.z * weight
        nx += normal.x * weight
        ny += normal.y * weight
        nz += normal.z * weight
      }

      const normalLength = Math.hypot(nx, ny, nz)
      triangles.push({
        position: { x: px, y: py, z: pz },
        normal: normalLength > 1e-7 ? { x: nx / normalLength, y: ny / normalLength, z: nz / normalLength } : { x: 0, y: 1, z: 0 },
        color: this.colors[i] ?? { b: 0xff, g: 0xff, r: 0xff, a: 0xff },
        texCoord: this.texCoords[i] ?? { x: 0, y: 0 },
      })
    }

    return triangles
  }
}

export class WeightedMeshResource extends DatResource {
  readonly id: DatId
  readonly weightedMesh: WeightedMesh

  constructor(id: DatId, weightedMesh: WeightedMesh) {
    super()
    this.id = id
    this.weightedMesh = weightedMesh
  }
}

export class NotImplementedResource implements DatEntry {
  readonly id: DatId

  constructor(id: DatId) {
    this.id = id
  }
}

export interface SectionTypeDef {
  readonly code: number
  readonly resourceType: Function
}

export const SectionTypes = {
  S00_End: { code: 0x00, resourceType: NotImplementedResource },
  S01_Directory: { code: 0x01, resourceType: DirectoryResource },
  S20_Texture: { code: 0x20, resourceType: TextureResource },
  S25_WeightedMesh: { code: 0x25, resourceType: WeightedMeshResource },
  S29_Skeleton: { code: 0x29, resourceType: SkeletonResource },
  S2A_SkeletonMesh: { code: 0x2a, resourceType: SkeletonMeshResource },
  S2B_SkeletonAnimation: { code: 0x2b, resourceType: SkeletonAnimationResource },
  S45_Info: { code: 0x45, resourceType: InfoResource },
} as const satisfies Record<string, SectionTypeDef>

export type SectionType = (typeof SectionTypes)[keyof typeof SectionTypes]

const sectionTypeByCode = new Map<number, SectionType>(Object.values(SectionTypes).map((type) => [type.code, type]))

export function sectionTypeFromCode(code: number): SectionTypeDef {
  const type = sectionTypeByCode.get(code)
  if (!type) {
    return {
      code,
      resourceType: NotImplementedResource,
    }
  }
  return type
}
