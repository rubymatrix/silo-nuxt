/**
 * Zone mesh section parser (section type 0x2E).
 *
 * Ported from xim/resource/ZoneMeshSection.kt.
 * Parses encrypted zone mesh data into ZoneMeshResource.
 */

import type { ByteReader, Vector3 } from './byteReader'
import type { SectionHeader } from './datParser'
import { ParserEntry, ParserResult, type ResourceParser } from './datParser'
import { DatId, type MeshRenderState } from './datResource'
import { TextureLink } from './textureLink'
import {
  ZoneMeshResource,
  type ZoneMeshBoundingBox,
  type ZoneMeshBuffer,
  type ZoneMeshVertex,
} from './zoneResource'
import { decryptZoneMesh } from './table/zoneDecrypt'

export class ZoneMeshSection implements ResourceParser {
  private readonly sectionHeader: SectionHeader

  constructor(sectionHeader: SectionHeader) {
    this.sectionHeader = sectionHeader
  }

  getResource(byteReader: ByteReader): ParserResult {
    decryptZoneMesh(this.sectionHeader, byteReader)
    const { name, buffers, boundingBox0, boundingBox1 } = this.read(byteReader)
    const resource = new ZoneMeshResource(
      this.sectionHeader.sectionId,
      name,
      buffers,
      boundingBox0,
      boundingBox1,
    )
    return new ParserResult(new ParserEntry(resource, name))
  }

  private read(byteReader: ByteReader): {
    name: string
    buffers: ZoneMeshBuffer[]
    boundingBox0: ZoneMeshBoundingBox | null
    boundingBox1: ZoneMeshBoundingBox | null
  } {
    byteReader.offsetFromDataStart(this.sectionHeader)

    const _decryptInfo = byteReader.next32()
    const keyConfigData = byteReader.next32()
    const _unkString = byteReader.nextString(0x8)

    const configData = keyConfigData & 0x000000ff
    const meshTypeFlag = configData & 0x1
    const meshType: 'TriMesh' | 'TriStrip' = meshTypeFlag === 0 ? 'TriMesh' : 'TriStrip'
    const vertexBlendEnabled = (configData & 0x2) !== 0

    const name = byteReader.nextString(0x10).replace(/\0+$/, '').trimEnd()

    const defStart = byteReader.position

    // Section 1
    const meshCount0 = byteReader.next32()
    const boundingBox0 = readBoundingBox(byteReader)
    const section1DataStart = defStart + byteReader.next32()

    // "hit"-type models have only a bounding box with no data
    if (meshCount0 === 0) {
      return { name, buffers: [], boundingBox0, boundingBox1: null }
    }

    // Section 2
    const meshCount1 = byteReader.next32()
    const boundingBox1 = readBoundingBox(byteReader)
    const _unknown = byteReader.next32()

    if (byteReader.position !== section1DataStart) {
      console.warn(`[ZoneMesh] More definition data than expected`)
    }

    const buffers: ZoneMeshBuffer[] = []
    for (let i = 0; i < meshCount1; i++) {
      buffers.push(this.parseMesh(byteReader, name, vertexBlendEnabled, meshType))
      byteReader.align0x04()
    }

    return { name, buffers, boundingBox0, boundingBox1 }
  }

  private parseMesh(
    byteReader: ByteReader,
    name: string,
    vertexBlendEnabled: boolean,
    meshType: 'TriMesh' | 'TriStrip',
  ): ZoneMeshBuffer {
    const textureName = byteReader.nextString(0x10)
    const numVerts = byteReader.next16()
    const flags = byteReader.next16()
    const blendEnabled = (flags & 0x8000) !== 0
    const backFaceCulling = (flags & 0x2000) === 0

    const verts: ZoneMeshVertex[] = []
    for (let i = 0; i < numVerts; i++) {
      if (vertexBlendEnabled) {
        verts.push({
          p0: byteReader.nextVector3f(),
          p1: byteReader.nextVector3f(),
          normal: byteReader.nextVector3f(),
          color: byteReader.nextBGRA(),
          u: byteReader.nextFloat(),
          v: byteReader.nextFloat(),
        })
      } else {
        verts.push({
          p0: byteReader.nextVector3f(),
          p1: { x: 0, y: 0, z: 0 },
          normal: byteReader.nextVector3f(),
          color: byteReader.nextBGRA(),
          u: byteReader.nextFloat(),
          v: byteReader.nextFloat(),
        })
      }
    }

    const numIndices = byteReader.next16()
    const _unk1 = byteReader.next16()

    const indices: number[] = []
    const indexedVertices: ZoneMeshVertex[] = []
    for (let i = 0; i < numIndices; i++) {
      const idx = byteReader.next16()
      indices.push(idx)
      indexedVertices.push(verts[idx]!)
    }

    // Compute tangent vectors per-triangle for bump mapping
    const tangents: Vector3[] = []
    for (let i = 0; i < numIndices; i++) {
      tangents.push(computeTangent(i, indexedVertices, meshType))
    }

    const discardThreshold = name.startsWith('_') ? 0.375 : null

    const zBias = blendEnabled ? 5 : 0

    const renderState: MeshRenderState = {
      blendEnabled,
      discardThreshold,
      depthMask: !blendEnabled,
      useBackFaceCulling: backFaceCulling,
      zBias,
    }

    return {
      numVertices: numIndices,
      meshType,
      textureName,
      textureLink: TextureLink.of(textureName, this.sectionHeader.localDir),
      vertices: indexedVertices,
      indices,
      tangents,
      renderState,
      vertexBlendEnabled,
    }
  }
}

function readBoundingBox(byteReader: ByteReader): ZoneMeshBoundingBox {
  const x0 = byteReader.nextFloat()
  const x1 = byteReader.nextFloat()
  const y0 = byteReader.nextFloat()
  const y1 = byteReader.nextFloat()
  const z0 = byteReader.nextFloat()
  const z1 = byteReader.nextFloat()
  return {
    p0: { x: x0, y: y0, z: z0 },
    p1: { x: x1, y: y1, z: z1 },
  }
}

function computeTangent(i: number, verts: readonly ZoneMeshVertex[], type: 'TriMesh' | 'TriStrip'): Vector3 {
  if (i <= 2) return computeTangentFromTriangle(verts[0]!, verts[1]!, verts[2]!)

  if (type === 'TriMesh') {
    const i0 = i - (i % 3)
    return computeTangentFromTriangle(verts[i0]!, verts[i0 + 1]!, verts[i0 + 2]!)
  }

  // TriStrip
  return computeTangentFromTriangle(verts[i - 2]!, verts[i - 1]!, verts[i]!)
}

function computeTangentFromTriangle(v0: ZoneMeshVertex, v1: ZoneMeshVertex, v2: ZoneMeshVertex): Vector3 {
  const edge1x = v1.p0.x - v0.p0.x
  const edge1y = v1.p0.y - v0.p0.y
  const edge1z = v1.p0.z - v0.p0.z

  const edge2x = v2.p0.x - v0.p0.x
  const edge2y = v2.p0.y - v0.p0.y
  const edge2z = v2.p0.z - v0.p0.z

  const dUV1x = v1.u - v0.u
  const dUV1y = v1.v - v0.v
  const dUV2x = v2.u - v0.u
  const dUV2y = v2.v - v0.v

  const denom = dUV1x * dUV2y - dUV2x * dUV1y
  if (Math.abs(denom) < 1e-5) {
    const len = Math.hypot(1, 1, 1)
    return { x: 1 / len, y: 1 / len, z: 1 / len }
  }

  const f = 1.0 / denom
  return {
    x: f * (dUV2y * edge1x - dUV1y * edge2x),
    y: f * (dUV2y * edge1y - dUV1y * edge2y),
    z: f * (dUV2y * edge1z - dUV1y * edge2z),
  }
}
