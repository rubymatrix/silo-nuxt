/**
 * Zone definition section parser (section type 0x1C).
 *
 * Ported from xim/resource/ZoneDefParser.kt.
 * Parses zone objects, collision meshes, collision map, space partitioning tree.
 */

import type { ByteReader, Vector3 } from './byteReader'
import type { SectionHeader } from './datParser'
import { ParserResult, type ResourceParser } from './datParser'
import { decryptZoneObjects } from './table/zoneDecrypt'
import {
  ZoneResource,
  terrainTypeFromFlags,
  type CollisionMap,
  type CollisionMesh,
  type CollisionObject,
  type CollisionObjectGroup,
  type CollisionTransformInfo,
  type CollisionTriangle,
  type Matrix4x4,
  type SpacePartitioningNode,
  type ZoneObject,
} from './zoneResource'

export class ZoneDefSection implements ResourceParser {
  private readonly sectionHeader: SectionHeader

  // Grid dimensions
  private zoneBlocksX = 0
  private zoneBlocksZ = 0
  private blockWidth = 0
  private blockLength = 0
  private subBlocksX = 0
  private subBlocksZ = 0

  // Lookup caches
  private readonly collisionGroups: CollisionObjectGroup[] = []
  private readonly collisionGroupsByIndex = new Map<number, number>()
  private readonly collisionMeshesByOffset = new Map<number, CollisionMesh>()
  private readonly transformsByOffset = new Map<number, CollisionTransformInfo>()
  private readonly cullingTables: Set<number>[] = []
  private readonly cullingTableIndicesByOffset = new Map<number, number>()
  private collisionMap: CollisionMap | null = null

  constructor(sectionHeader: SectionHeader) {
    this.sectionHeader = sectionHeader
  }

  getResource(byteReader: ByteReader): ParserResult {
    decryptZoneObjects(this.sectionHeader, byteReader)
    const zoneResource = this.read(byteReader)
    return ParserResult.from(zoneResource)
  }

  private read(byteReader: ByteReader): ZoneResource {
    byteReader.offsetFromDataStart(this.sectionHeader, 0x0)

    // Header
    const _decryptInfo = byteReader.next32()
    const keyNodeData = byteReader.next32()
    const nodeCount = keyNodeData & 0x00ffffff

    const collisionMeshOffset = byteReader.next32()

    this.zoneBlocksX = byteReader.next8()
    this.zoneBlocksZ = byteReader.next8()
    this.blockWidth = byteReader.next8()
    this.blockLength = byteReader.next8()
    this.subBlocksX = Math.max(1, Math.floor(this.blockWidth / 4))
    this.subBlocksZ = Math.max(1, Math.floor(this.blockLength / 4))

    const spacePartitioningTreeOffset = byteReader.next32()
    const cullingTablesOffset = byteReader.next32()
    const pointLightOffset = byteReader.next32()
    const _unk = byteReader.next32()
    const zoneObjectsOffset = byteReader.position

    // Parse culling tables first (offsets needed by zone objects)
    byteReader.position = cullingTablesOffset + this.sectionHeader.dataStartPosition
    this.parseCullingTables(byteReader)

    // Parse zone objects
    byteReader.position = zoneObjectsOffset
    const zoneObjects = this.parseZoneObjects(byteReader, nodeCount)

    // Parse space partitioning tree
    byteReader.position = spacePartitioningTreeOffset + this.sectionHeader.dataStartPosition
    const rootNode = this.parseNode(byteReader)

    // Parse point lights
    byteReader.position = pointLightOffset + this.sectionHeader.dataStartPosition
    const pointLightLinks = this.readPointLights(byteReader)

    // Parse collision meshes (ship zones don't have them)
    if (collisionMeshOffset !== 0) {
      byteReader.position = collisionMeshOffset + this.sectionHeader.dataStartPosition
      this.parseCollisionMeshSection(byteReader)
    }

    return new ZoneResource(
      this.sectionHeader.sectionId,
      zoneObjects,
      this.collisionGroups,
      this.collisionMap,
      this.cullingTables,
      rootNode,
      pointLightLinks,
    )
  }

  private parseZoneObjects(byteReader: ByteReader, nodeCount: number): ZoneObject[] {
    const objects: ZoneObject[] = []

    for (let i = 0; i < nodeCount; i++) {
      const fileOffset = byteReader.position
      const objectId = byteReader.nextString(0x10).replace(/\0+$/, '').trimEnd()
      const position = byteReader.nextVector3f()
      const rotation = byteReader.nextVector3f()
      const scale = byteReader.nextVector3f()

      const effectLinkId = byteReader.nextDatId()
      const highDefThreshold = byteReader.nextFloat()
      const medDefThreshold = byteReader.nextFloat()
      const drawDistance = byteReader.nextFloat()

      const flags0 = byteReader.next8()
      const flags1 = byteReader.next8()
      const _flags2 = byteReader.next8()
      const _flags3 = byteReader.next8()

      const cullingTableLink = byteReader.next32()
      const environmentLinkId = byteReader.nextDatId()
      const fileIdLink = byteReader.next32()

      const pointLightIndex0 = byteReader.next32()
      const pointLightIndex1 = byteReader.next32()
      const pointLightIndex2 = byteReader.next32()
      const pointLightIndex3 = byteReader.next32()

      const pointLightIndices = [pointLightIndex0, pointLightIndex1, pointLightIndex2, pointLightIndex3]
        .filter(idx => idx > 0)
        .map(idx => idx - 1)

      const effectLink = isZeroDatId(effectLinkId) ? null : effectLinkId
      const environmentLink = isZeroDatId(environmentLinkId) ? null : environmentLinkId
      const cullingTableIndex = this.cullingTableIndicesByOffset.get(cullingTableLink) ?? null

      objects.push({
        index: i,
        objectId,
        fileOffset,
        position,
        rotation,
        scale,
        highDefThreshold,
        midDefThreshold: medDefThreshold,
        lowDefThreshold: drawDistance,
        cullingTableIndex,
        effectLink,
        environmentLink,
        pointLightIndices,
        skipDuringDecalRendering: (flags1 & 0x2) !== 0,
        fileIdLink: fileIdLink === 0 ? null : fileIdLink,
      })
    }

    return objects
  }

  private parseCullingTables(byteReader: ByteReader): void {
    const indexTableCount = byteReader.next32()
    if (indexTableCount === 0) {
      byteReader.next32() // skip zero
      return
    }

    for (let i = 0; i < indexTableCount; i++) {
      const offset = byteReader.position
      this.cullingTableIndicesByOffset.set(offset, i)

      const currTableCount = byteReader.next32()
      const table = new Set<number>()
      this.cullingTables.push(table)

      for (let j = 0; j < currTableCount; j++) {
        table.add(byteReader.next32())
      }
    }
  }

  private parseNode(byteReader: ByteReader): SpacePartitioningNode {
    // Read 8 bounding box vertices (ignored for now, we just need the tree structure)
    for (let i = 0; i < 8; i++) {
      byteReader.nextVector3f()
    }

    const idxRef = byteReader.next32()
    const indexCount = byteReader.next32()
    const isLeafNode = indexCount > 0
    const idxFileRef = isLeafNode ? idxRef + this.sectionHeader.dataStartPosition : 0

    const childrenOffsets: number[] = []
    for (let i = 0; i < 4; i++) {
      childrenOffsets.push(byteReader.next32())
    }

    byteReader.next32() // expect zero
    byteReader.next32() // expect zero

    const children: (SpacePartitioningNode | null)[] = []
    for (const childOffset of childrenOffsets) {
      if (childOffset === 0) {
        children.push(null)
        continue
      }

      byteReader.position = childOffset + this.sectionHeader.dataStartPosition
      children.push(this.parseNode(byteReader))
    }

    const containedObjects = new Set<number>()
    if (isLeafNode) {
      byteReader.position = idxFileRef
      for (let i = 0; i < indexCount; i++) {
        containedObjects.add(byteReader.next32())
      }
    }

    return { leafNode: isLeafNode, containedObjects, children }
  }

  private readPointLights(byteReader: ByteReader): string[] {
    const pointLightIdLinks: string[] = []

    for (let i = 0; i < 256; i++) {
      const idLink = byteReader.nextDatId()
      if (isZeroDatId(idLink)) break

      pointLightIdLinks.push(idLink)

      // Pre-allocated space for point-light particle pointers
      for (let j = 0; j < 0x12; j++) {
        byteReader.next32()
      }
    }

    return pointLightIdLinks
  }

  private parseCollisionMeshSection(byteReader: ByteReader): void {
    const _numMeshes = byteReader.next32()
    const _firstMeshOffset = byteReader.next32() + this.sectionHeader.dataStartPosition

    const matrixMeshPairCount = byteReader.next32()
    const matrixMeshPairsOffset = byteReader.next32() + this.sectionHeader.dataStartPosition

    const collisionMapOffset = byteReader.next32() + this.sectionHeader.dataStartPosition
    const _collisionMeshTransformsOffset = byteReader.next32() + this.sectionHeader.dataStartPosition

    byteReader.next32() // space-tree index count
    byteReader.next32() // expect zero

    // Parse transform/mesh pairs
    byteReader.position = matrixMeshPairsOffset
    this.parseMeshTransformPairs(byteReader, matrixMeshPairCount)

    // Parse collision map
    byteReader.position = collisionMapOffset
    this.parseCollisionMap(byteReader, matrixMeshPairCount)
  }

  private parseMeshTransformPairs(byteReader: ByteReader, totalPairCount: number): void {
    for (let i = 0; i < totalPairCount; i++) {
      const position = byteReader.position
      this.collisionGroupsByIndex.set(position, i)

      const countFlags = byteReader.next32()
      const groupSize = countFlags & 0x7ff

      const collisionObjects: CollisionObject[] = []

      for (let j = 0; j < groupSize; j++) {
        const fileOffset = byteReader.position

        const matrixOffset = byteReader.next32() + this.sectionHeader.dataStartPosition
        const transform = this.getOrParseTransform(byteReader, matrixOffset)

        const meshOffset = byteReader.next32() + this.sectionHeader.dataStartPosition
        const mesh = this.getOrParseCollisionMesh(byteReader, meshOffset)

        collisionObjects.push({ fileOffset, collisionMesh: mesh, transformInfo: transform })
      }

      byteReader.next32() // expect zero

      this.collisionGroups.push({ fileOffset: position, collisionObjects })
    }
  }

  private getOrParseTransform(byteReader: ByteReader, offset: number): CollisionTransformInfo {
    const cached = this.transformsByOffset.get(offset)
    if (cached) return cached

    const result = byteReader.wrapped(() => this.parseTransform(byteReader, offset))
    this.transformsByOffset.set(offset, result)
    return result
  }

  private getOrParseCollisionMesh(byteReader: ByteReader, offset: number): CollisionMesh {
    const cached = this.collisionMeshesByOffset.get(offset)
    if (cached) return cached

    const result = byteReader.wrapped(() => this.parseCollisionMesh(byteReader, offset))
    this.collisionMeshesByOffset.set(offset, result)
    return result
  }

  private parseCollisionMesh(byteReader: ByteReader, offset: number): CollisionMesh {
    byteReader.position = offset

    const positionOffset = byteReader.next32() + this.sectionHeader.dataStartPosition
    const directionOffset = byteReader.next32() + this.sectionHeader.dataStartPosition
    const indexOffset = byteReader.next32() + this.sectionHeader.dataStartPosition

    const numTris = byteReader.next16()
    const _unk = byteReader.next16()

    const triangles: CollisionTriangle[] = []

    byteReader.position = indexOffset
    for (let i = 0; i < numTris; i++) {
      const rawP0 = byteReader.next16()
      const pOff0 = positionOffset + (rawP0 & 0x7fff) * 4 * 3

      const rawP1 = byteReader.next16()
      const pOff1 = positionOffset + (rawP1 & 0x3fff) * 4 * 3

      const rawP2 = byteReader.next16()
      const pOff2 = positionOffset + (rawP2 & 0x3fff) * 4 * 3

      const rawD = byteReader.next16()
      const dOff = directionOffset + (rawD & 0x7fff) * 4 * 3

      const nextPos = byteReader.position
      const p0 = readVec3At(byteReader, pOff0)
      const p1 = readVec3At(byteReader, pOff1)
      const p2 = readVec3At(byteReader, pOff2)
      const normal = readVec3At(byteReader, dOff)

      const flags = [rawP0, rawP1, rawP2, rawD].map(v => v >>> 12)
      const hitWall = ((flags[0]! << 12) | (flags[1]! << 8) | (flags[2]! << 4) | flags[3]!) & 0x40
      const terrainType = terrainTypeFromFlags(flags[0]!, flags[1]!, flags[2]!, flags[3]!)

      triangles.push({ p0, p1, p2, normal, terrainType, hitWall: hitWall !== 0 })

      byteReader.position = nextPos
    }

    // Sort by abs(normal.y) descending for stair resolution
    triangles.sort((a, b) => Math.abs(b.normal.y) - Math.abs(a.normal.y))

    return { fileOffset: offset, triangles }
  }

  private parseCollisionMap(byteReader: ByteReader, expected: number): void {
    const rows = this.zoneBlocksZ * this.subBlocksZ
    const cols = this.zoneBlocksX * this.subBlocksX

    const map: (CollisionObjectGroup | null)[][] = []
    for (let z = 0; z < rows; z++) {
      map.push(new Array(cols).fill(null))
    }

    let found = 0

    for (let z = 0; z < rows; z++) {
      for (let x = 0; x < cols; x++) {
        const offset = byteReader.next32()
        if (offset === 0) continue

        found += 1
        const fileOffset = offset + this.sectionHeader.dataStartPosition
        const index = this.collisionGroupsByIndex.get(fileOffset)
        if (index !== undefined) {
          map[z]![x] = this.collisionGroups[index]!
        }

        if (found === expected) break
      }
      if (found === expected) break
    }

    this.collisionMap = {
      numBlocksWide: this.zoneBlocksX,
      numBlocksLong: this.zoneBlocksZ,
      blockWidth: this.blockWidth,
      blockLength: this.blockLength,
      subBlocksX: this.subBlocksX,
      subBlocksZ: this.subBlocksZ,
      entries: map,
    }
  }

  private parseTransform(byteReader: ByteReader, offset: number): CollisionTransformInfo {
    byteReader.position = offset

    const toWorldSpace: number[] = []
    for (let i = 0; i < 16; i++) toWorldSpace.push(byteReader.nextFloat())

    const toCollisionSpace: number[] = []
    for (let i = 0; i < 16; i++) toCollisionSpace.push(byteReader.nextFloat())

    // Additional data
    byteReader.nextVector3f()
    byteReader.nextVector3f()
    byteReader.nextVector3f()

    const miscFlags = byteReader.next32()
    const cullingGroupOffset = byteReader.next32()
    const rawLightIndices = [byteReader.next8(), byteReader.next8(), byteReader.next8(), byteReader.next8()]
    const rawEnvironmentId = byteReader.nextDatId()

    byteReader.nextFloat() // unkFloat0
    byteReader.nextFloat() // unkFloat1

    const rawSubAreaLinkId = byteReader.next32()
    const subAreaLinkId = rawSubAreaLinkId === 0 ? null : rawSubAreaLinkId

    const lightIndices = rawLightIndices.filter(v => v !== 0).map(v => v - 1)
    const environmentId = isZeroDatId(rawEnvironmentId) ? null : rawEnvironmentId

    const mapId = 0x8 * ((miscFlags >>> 26) & 0x3) + ((miscFlags >>> 3) & 0x7)

    const cullingTableIndex = cullingGroupOffset === 0 ? null : (() => {
      const fileOff = cullingGroupOffset + this.sectionHeader.dataStartPosition
      return this.cullingTableIndicesByOffset.get(fileOff) ?? null
    })()

    return {
      fileOffset: offset,
      toWorldSpace: { m: toWorldSpace },
      toCollisionSpace: { m: toCollisionSpace },
      environmentId,
      lightIndices,
      cullingTableIndex,
      miscFlags: miscFlags,
      subAreaLinkId,
      mapId,
    } as CollisionTransformInfo & { miscFlags: number }
  }
}

function readVec3At(byteReader: ByteReader, offset: number): Vector3 {
  byteReader.position = offset
  return byteReader.nextVector3f()
}

function isZeroDatId(id: string): boolean {
  return id === '\0\0\0\0' || id === ''
}
