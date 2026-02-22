import { ParserResult, type ResourceParser, type SectionHeader, oops } from './datParser'
import { SkeletonMeshResource, SKELETON_MESH_RENDER_STATE, type SkeletonVertex, type SkinnedMesh } from './datResource'

type MeshType = 'TriStrip' | 'TriMesh'

interface VertexJointRef {
  readonly index: number
  readonly flippedIndex: number
  readonly flipAxis: number
}

interface ParsedVertex {
  readonly position0: import('./byteReader').Vector3
  readonly position1: import('./byteReader').Vector3
  readonly normal0: import('./byteReader').Vector3
  readonly normal1: import('./byteReader').Vector3
  readonly joint0Weight: number
  readonly joint1Weight: number
  readonly jointIndex0: number | null
  readonly jointIndex1: number | null
  readonly flippedJointIndex0: number | null
  readonly flippedJointIndex1: number | null
  readonly flipAxis0: number
  readonly flipAxis1: number
}

export class SkeletonMeshSection implements ResourceParser {
  private readonly sectionHeader: SectionHeader

  constructor(sectionHeader: SectionHeader) {
    this.sectionHeader = sectionHeader
  }

  getResource(byteReader: import('./byteReader').ByteReader): ParserResult {
    byteReader.offsetFromDataStart(this.sectionHeader)

    byteReader.next8()
    byteReader.next8()
    const flags3 = byteReader.next8()
    const clothEffect = (flags3 & 0x01) !== 0
    const useJointArray = (flags3 & 0x80) !== 0
    const hasNormals = !clothEffect

    const occlusionType = byteReader.next8()
    const symmetric = byteReader.next8() === 0x01
    byteReader.next8()

    const instructionOffset = 2 * byteReader.next32()
    byteReader.next8()
    byteReader.next8()

    const jointArrayOffset = 2 * byteReader.next32()
    const numJoints = byteReader.next16()

    const vertexCountsOffset = 2 * byteReader.next32()
    const numVertexCounts = byteReader.next16()
    if (numVertexCounts !== 2) {
      throw new Error('Expected exactly two vertex-count entries')
    }

    const vertexJointMappingOffset = 2 * byteReader.next32()
    byteReader.next16()

    const vertexDataOffset = 2 * byteReader.next32()
    byteReader.next16()

    byteReader.next32()
    byteReader.next16()

    byteReader.offsetFromDataStart(this.sectionHeader, jointArrayOffset)
    const jointIndices = Array.from({ length: numJoints }, () => byteReader.next16())

    byteReader.offsetFromDataStart(this.sectionHeader, vertexCountsOffset)
    const singleJointedCount = byteReader.next16()
    const doubleJointedCount = byteReader.next16()
    const totalVertices = singleJointedCount + doubleJointedCount

    byteReader.offsetFromDataStart(this.sectionHeader, vertexJointMappingOffset)
    const jointRefs0: VertexJointRef[] = []
    const jointRefs1: VertexJointRef[] = []
    for (let i = 0; i < totalVertices; i += 1) {
      jointRefs0.push(this.unpackJointRef(byteReader.next16()))
      jointRefs1.push(this.unpackJointRef(byteReader.next16()))
    }

    byteReader.offsetFromDataStart(this.sectionHeader, vertexDataOffset)
    const vertices: ParsedVertex[] = []

    for (let i = 0; i < singleJointedCount; i += 1) {
      const position0 = byteReader.nextVector3f()
      const normal0 = hasNormals ? byteReader.nextVector3f() : { x: 0, y: 0, z: 0 }
      const ref0 = jointRefs0[i]

      vertices.push({
        position0,
        position1: { x: 0, y: 0, z: 0 },
        normal0,
        normal1: { x: 0, y: 0, z: 0 },
        joint0Weight: 1,
        joint1Weight: 0,
        jointIndex0: useJointArray ? (jointIndices[ref0.index] ?? null) : ref0.index,
        jointIndex1: null,
        flippedJointIndex0: useJointArray ? (jointIndices[ref0.flippedIndex] ?? null) : ref0.flippedIndex,
        flippedJointIndex1: null,
        flipAxis0: ref0.flipAxis,
        flipAxis1: 0,
      })
    }

    for (let i = 0; i < doubleJointedCount; i += 1) {
      const refIndex = singleJointedCount + i
      const position0 = {
        x: byteReader.nextFloat(),
        y: 0,
        z: 0,
      }
      const position1 = {
        x: byteReader.nextFloat(),
        y: 0,
        z: 0,
      }
      position0.y = byteReader.nextFloat()
      position1.y = byteReader.nextFloat()
      position0.z = byteReader.nextFloat()
      position1.z = byteReader.nextFloat()
      const joint0Weight = byteReader.nextFloat()
      const joint1Weight = byteReader.nextFloat()
      const normal0 = { x: 0, y: 0, z: 0 }
      const normal1 = { x: 0, y: 0, z: 0 }
      if (hasNormals) {
        normal0.x = byteReader.nextFloat()
        normal1.x = byteReader.nextFloat()
        normal0.y = byteReader.nextFloat()
        normal1.y = byteReader.nextFloat()
        normal0.z = byteReader.nextFloat()
        normal1.z = byteReader.nextFloat()
      }
      const ref0 = jointRefs0[refIndex]
      const ref1 = jointRefs1[refIndex]

      vertices.push({
        position0,
        position1,
        normal0,
        normal1,
        joint0Weight,
        joint1Weight,
        jointIndex0: useJointArray ? (jointIndices[ref0.index] ?? null) : ref0.index,
        jointIndex1: useJointArray ? (jointIndices[ref1.index] ?? null) : ref1.index,
        flippedJointIndex0: useJointArray ? (jointIndices[ref0.flippedIndex] ?? null) : ref0.flippedIndex,
        flippedJointIndex1: useJointArray ? (jointIndices[ref1.flippedIndex] ?? null) : ref1.flippedIndex,
        flipAxis0: ref0.flipAxis,
        flipAxis1: ref1.flipAxis,
      })
    }

    byteReader.offsetFromDataStart(this.sectionHeader, instructionOffset)
    const meshes: SkinnedMesh[] = []
    let currentTextureName = ''

    while (true) {
      const opCode = byteReader.next16()
      if (opCode === 0xffff) {
        break
      }

      if (opCode === 0x8010) {
        this.readRenderProperties(byteReader)
        continue
      }

      if (opCode === 0x8000) {
        currentTextureName = byteReader.nextString(0x10).replace(/\u0000+$/g, '')
        continue
      }

      if (opCode === 0x0054) {
        const mesh = this.parseTriMesh(byteReader, vertices, currentTextureName)
        meshes.push(mesh)
        if (symmetric) {
          meshes.push(this.mirrorMesh(mesh))
        }
        continue
      }

      if (opCode === 0x5453) {
        const mesh = this.parseTriStrip(byteReader, vertices, currentTextureName)
        meshes.push(mesh)
        if (symmetric) {
          meshes.push(this.mirrorMesh(mesh))
        }
        continue
      }

      if (opCode === 0x0043) {
        const mesh = this.parseUntexturedTriMesh(byteReader, vertices, currentTextureName)
        meshes.push(mesh)
        if (symmetric) {
          meshes.push(this.mirrorMesh(mesh))
        }
        continue
      }

      if (opCode === 0x4353) {
        const mesh = this.parseSingleColorUntexturedTriStrip(byteReader, vertices, currentTextureName)
        meshes.push(mesh)
        if (symmetric) {
          meshes.push(this.mirrorMesh(mesh))
        }
        continue
      }

      throw oops(byteReader, `Unknown skeleton-mesh op-code 0x${opCode.toString(16)}`)
    }

    return ParserResult.from(new SkeletonMeshResource(this.sectionHeader.sectionId, meshes, occlusionType))
  }

  private parseTriMesh(
    byteReader: import('./byteReader').ByteReader,
    vertices: readonly ParsedVertex[],
    textureName: string,
  ): SkinnedMesh {
    const numTriangles = byteReader.next16()
    const meshVertices: SkeletonVertex[] = []

    for (let i = 0; i < numTriangles; i += 1) {
      const indices = [byteReader.next16(), byteReader.next16(), byteReader.next16()]
      const uvs = [
        { u: byteReader.nextFloat(), v: byteReader.nextFloat() },
        { u: byteReader.nextFloat(), v: byteReader.nextFloat() },
        { u: byteReader.nextFloat(), v: byteReader.nextFloat() },
      ]

      for (let j = 0; j < 3; j += 1) {
        meshVertices.push(this.toSkeletonVertex(vertices[indices[j] ?? 0], uvs[j]?.u ?? 0, uvs[j]?.v ?? 0))
      }
    }

    return {
      meshType: 'TriMesh',
      textureName,
      vertexCount: meshVertices.length,
      vertices: meshVertices,
      renderState: SKELETON_MESH_RENDER_STATE,
    }
  }

  private parseTriStrip(
    byteReader: import('./byteReader').ByteReader,
    vertices: readonly ParsedVertex[],
    textureName: string,
  ): SkinnedMesh {
    const numTriangles = byteReader.next16()
    const numVertices = numTriangles + 2
    const meshVertices: SkeletonVertex[] = []

    const indices = [byteReader.next16(), byteReader.next16(), byteReader.next16()]
    const uvs = [
      { u: byteReader.nextFloat(), v: byteReader.nextFloat() },
      { u: byteReader.nextFloat(), v: byteReader.nextFloat() },
      { u: byteReader.nextFloat(), v: byteReader.nextFloat() },
    ]

    meshVertices.push(this.toSkeletonVertex(vertices[indices[0] ?? 0], uvs[0]?.u ?? 0, uvs[0]?.v ?? 0))
    meshVertices.push(this.toSkeletonVertex(vertices[indices[1] ?? 0], uvs[1]?.u ?? 0, uvs[1]?.v ?? 0))
    meshVertices.push(this.toSkeletonVertex(vertices[indices[2] ?? 0], uvs[2]?.u ?? 0, uvs[2]?.v ?? 0))

    for (let i = 1; i < numTriangles; i += 1) {
      const index = byteReader.next16()
      const u = byteReader.nextFloat()
      const v = byteReader.nextFloat()
      meshVertices.push(this.toSkeletonVertex(vertices[index] ?? vertices[0], u, v))
    }

    return {
      meshType: 'TriStrip',
      textureName,
      vertexCount: meshVertices.length,
      vertices: meshVertices,
      renderState: SKELETON_MESH_RENDER_STATE,
    }
  }

  private parseUntexturedTriMesh(
    byteReader: import('./byteReader').ByteReader,
    vertices: readonly ParsedVertex[],
    textureName: string,
  ): SkinnedMesh {
    const numTriangles = byteReader.next16()
    const meshVertices: SkeletonVertex[] = []

    for (let i = 0; i < numTriangles; i += 1) {
      const idx0 = byteReader.next16()
      const idx1 = byteReader.next16()
      const idx2 = byteReader.next16()
      const color = byteReader.nextBGRA()

      meshVertices.push(this.toSkeletonVertex(vertices[idx0] ?? vertices[0], 0, 0, color))
      meshVertices.push(this.toSkeletonVertex(vertices[idx1] ?? vertices[0], 0, 0, color))
      meshVertices.push(this.toSkeletonVertex(vertices[idx2] ?? vertices[0], 0, 0, color))
    }

    return {
      meshType: 'TriMesh',
      textureName,
      vertexCount: meshVertices.length,
      vertices: meshVertices,
      renderState: SKELETON_MESH_RENDER_STATE,
    }
  }

  private parseSingleColorUntexturedTriStrip(
    byteReader: import('./byteReader').ByteReader,
    vertices: readonly ParsedVertex[],
    textureName: string,
  ): SkinnedMesh {
    const numTriangles = byteReader.next16()
    const numVertices = numTriangles + 2
    const meshVertices: SkeletonVertex[] = []

    const idx0 = byteReader.next16()
    const idx1 = byteReader.next16()
    const idx2 = byteReader.next16()
    const color = byteReader.nextBGRA()

    meshVertices.push(this.toSkeletonVertex(vertices[idx0] ?? vertices[0], 0, 0, color))
    meshVertices.push(this.toSkeletonVertex(vertices[idx1] ?? vertices[0], 0, 0, color))
    meshVertices.push(this.toSkeletonVertex(vertices[idx2] ?? vertices[0], 0, 0, color))

    for (let i = 1; i < numTriangles; i += 1) {
      const idx = byteReader.next16()
      meshVertices.push(this.toSkeletonVertex(vertices[idx] ?? vertices[0], 0, 0, color))
    }

    return {
      meshType: 'TriStrip',
      textureName,
      vertexCount: numVertices,
      vertices: meshVertices,
      renderState: SKELETON_MESH_RENDER_STATE,
    }
  }

  private toSkeletonVertex(
    vertex: ParsedVertex | undefined,
    u: number,
    v: number,
    color?: import('./byteReader').BgraColor,
  ): SkeletonVertex {
    const fallback = {
      position0: { x: 0, y: 0, z: 0 },
      position1: { x: 0, y: 0, z: 0 },
      normal0: { x: 0, y: 1, z: 0 },
      normal1: { x: 0, y: 1, z: 0 },
      joint0Weight: 1,
      joint1Weight: 0,
      jointIndex0: 0,
      jointIndex1: null,
      flippedJointIndex0: 0,
      flippedJointIndex1: null,
      flipAxis0: 0,
      flipAxis1: 0,
    } as const

    const source = vertex ?? fallback
    return {
      position0: source.position0,
      position1: source.position1,
      normal0: source.normal0,
      normal1: source.normal1,
      joint0Weight: source.joint0Weight,
      joint1Weight: source.joint1Weight,
      jointIndex0: source.jointIndex0,
      jointIndex1: source.jointIndex1,
      u,
      v,
      color,
      flipAxis0: source.flipAxis0,
      flipAxis1: source.flipAxis1,
      flippedJointIndex0: source.flippedJointIndex0,
      flippedJointIndex1: source.flippedJointIndex1,
    }
  }

  private mirrorMesh(mesh: SkinnedMesh): SkinnedMesh {
    return {
      ...mesh,
      vertices: mesh.vertices.map((vertex) => ({
        ...vertex,
        position0: this.flipVectorByAxis(vertex.position0, vertex.flipAxis0 ?? 0),
        position1: this.flipVectorByAxis(vertex.position1, vertex.flipAxis1 ?? 0),
        normal0: this.flipVectorByAxis(vertex.normal0, vertex.flipAxis0 ?? 0),
        normal1: this.flipVectorByAxis(vertex.normal1, vertex.flipAxis1 ?? 0),
        jointIndex0: vertex.flippedJointIndex0 ?? vertex.jointIndex0,
        jointIndex1: vertex.flippedJointIndex1 ?? vertex.jointIndex1,
      })),
    }
  }

  private flipVectorByAxis(vector: import('./byteReader').Vector3, axis: number): import('./byteReader').Vector3 {
    if (axis === 1) {
      return { x: -vector.x, y: vector.y, z: vector.z }
    }
    if (axis === 2) {
      return { x: vector.x, y: -vector.y, z: vector.z }
    }
    if (axis === 3) {
      return { x: vector.x, y: vector.y, z: -vector.z }
    }
    return { ...vector }
  }

  private unpackJointRef(data: number): VertexJointRef {
    return {
      index: data & 0x7f,
      flippedIndex: (data >>> 0x7) & 0x7f,
      flipAxis: (data >>> 0xe) & 0x3,
    }
  }

  private readRenderProperties(byteReader: import('./byteReader').ByteReader): void {
    byteReader.nextBGRA()
    byteReader.nextFloat()
    byteReader.nextFloat()
    byteReader.next8()
    byteReader.next8()
    byteReader.next8()
    byteReader.next8()
    byteReader.nextFloat()
    byteReader.next32()
    byteReader.next32()
    byteReader.next16()
    byteReader.nextFloat()
    byteReader.next16()
    byteReader.nextFloat()
    byteReader.nextFloat()
  }
}

export function isMeshType(value: string): value is MeshType {
  return value === 'TriMesh' || value === 'TriStrip'
}
