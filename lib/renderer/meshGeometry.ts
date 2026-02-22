import { BufferAttribute, BufferGeometry } from 'three'

import type { BgraColor } from '~/lib/resource/byteReader'
import type { SkeletonVertex, SkinnedMesh } from '~/lib/resource/datResource'

export type MeshType = SkinnedMesh['meshType']

export function buildTriangleIndicesForMesh(meshType: MeshType, vertexCount: number): number[] {
  if (meshType === 'TriMesh') {
    const indices: number[] = []
    for (let i = 0; i + 2 < vertexCount; i += 3) {
      indices.push(i, i + 2, i + 1)
    }
    return indices
  }

  const indices: number[] = []
  for (let i = 0; i < vertexCount - 2; i += 1) {
    if ((i & 1) === 0) {
      indices.push(i, i + 2, i + 1)
    } else {
      indices.push(i + 1, i + 2, i)
    }
  }
  return indices
}

export function buildGeometryFromSkinnedMesh(mesh: SkinnedMesh): BufferGeometry {
  const geometry = new BufferGeometry()
  const vertexCount = mesh.vertices.length

  const position = new Float32Array(vertexCount * 3)
  const normal = new Float32Array(vertexCount * 3)
  const uv = new Float32Array(vertexCount * 2)
  const color = new Float32Array(vertexCount * 4)
  const skinWeight = new Float32Array(vertexCount * 4)
  const skinIndex = new Uint16Array(vertexCount * 4)

  for (let i = 0; i < vertexCount; i += 1) {
    const vertex = mesh.vertices[i]
    writeVector3(position, i * 3, vertex, 'position0')
    writeVector3(normal, i * 3, vertex, 'normal0')
    uv[i * 2] = vertex.u
    uv[i * 2 + 1] = vertex.v

    const rgba = bgraToRgbaFloat(vertex.color)
    color.set(rgba, i * 4)

    skinWeight[i * 4] = vertex.joint0Weight
    skinWeight[i * 4 + 1] = vertex.joint1Weight
    skinWeight[i * 4 + 2] = 0
    skinWeight[i * 4 + 3] = 0

    skinIndex[i * 4] = vertex.jointIndex0 ?? 0
    skinIndex[i * 4 + 1] = vertex.jointIndex1 ?? 0
    skinIndex[i * 4 + 2] = 0
    skinIndex[i * 4 + 3] = 0
  }

  geometry.setAttribute('position', new BufferAttribute(position, 3))
  geometry.setAttribute('normal', new BufferAttribute(normal, 3))
  geometry.setAttribute('uv', new BufferAttribute(uv, 2))
  geometry.setAttribute('color', new BufferAttribute(color, 4))
  geometry.setAttribute('skinWeight', new BufferAttribute(skinWeight, 4))
  geometry.setAttribute('skinIndex', new BufferAttribute(skinIndex, 4))
  geometry.setIndex(buildTriangleIndicesForMesh(mesh.meshType, vertexCount))
  geometry.computeBoundingSphere()

  return geometry
}

export function buildGeometryForXimSkinnedMesh(mesh: SkinnedMesh): BufferGeometry {
  const geometry = new BufferGeometry()
  const vertexCount = mesh.vertices.length

  const position = new Float32Array(vertexCount * 3)
  const position0 = new Float32Array(vertexCount * 3)
  const position1 = new Float32Array(vertexCount * 3)
  const normal0 = new Float32Array(vertexCount * 3)
  const normal1 = new Float32Array(vertexCount * 3)
  const uv = new Float32Array(vertexCount * 2)
  const color = new Float32Array(vertexCount * 4)
  const jointWeight = new Float32Array(vertexCount)
  const joint0 = new Float32Array(vertexCount)
  const joint1 = new Float32Array(vertexCount)

  for (let i = 0; i < vertexCount; i += 1) {
    const vertex = mesh.vertices[i]
    writeVector3(position, i * 3, vertex, 'position0')
    writeVector3(position0, i * 3, vertex, 'position0')
    writeVector3(position1, i * 3, vertex, 'position1')
    writeVector3(normal0, i * 3, vertex, 'normal0')
    writeVector3(normal1, i * 3, vertex, 'normal1')
    uv[i * 2] = vertex.u
    uv[i * 2 + 1] = vertex.v

    const rgba = bgraToRgbaFloat(vertex.color)
    color.set(rgba, i * 4)

    jointWeight[i] = vertex.joint0Weight
    joint0[i] = vertex.jointIndex0 ?? 0
    joint1[i] = vertex.jointIndex1 ?? 0
  }

  geometry.setAttribute('position', new BufferAttribute(position, 3))
  geometry.setAttribute('position0', new BufferAttribute(position0, 3))
  geometry.setAttribute('position1', new BufferAttribute(position1, 3))
  geometry.setAttribute('normal0', new BufferAttribute(normal0, 3))
  geometry.setAttribute('normal1', new BufferAttribute(normal1, 3))
  geometry.setAttribute('uv', new BufferAttribute(uv, 2))
  geometry.setAttribute('color', new BufferAttribute(color, 4))
  geometry.setAttribute('jointWeight', new BufferAttribute(jointWeight, 1))
  geometry.setAttribute('joint0', new BufferAttribute(joint0, 1))
  geometry.setAttribute('joint1', new BufferAttribute(joint1, 1))
  geometry.setIndex(buildTriangleIndicesForMesh(mesh.meshType, vertexCount))
  geometry.computeBoundingSphere()

  return geometry
}

function writeVector3(
  target: Float32Array,
  offset: number,
  vertex: SkeletonVertex,
  key: 'position0' | 'position1' | 'normal0' | 'normal1',
): void {
  target[offset] = vertex[key].x
  target[offset + 1] = vertex[key].y
  target[offset + 2] = vertex[key].z
}

function bgraToRgbaFloat(color: BgraColor | undefined): readonly [number, number, number, number] {
  if (!color) {
    return [0.625, 0.625, 0.625, 0.625]
  }

  return [color.r / 255, color.g / 255, color.b / 255, color.a / 255]
}
