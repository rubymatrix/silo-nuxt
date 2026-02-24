/**
 * Zone mesh geometry builder for Three.js.
 *
 * Builds BufferGeometry from ZoneMeshBuffer data with attributes matching
 * the ximShader.ts vertex layout:
 *   location 0: position0     (vec3)
 *   location 1: position1     (vec3)
 *   location 2: normal        (vec3)
 *   location 3: tangent       (vec3)
 *   location 4: textureCoords (vec2)
 *   location 8: vertexColor   (vec4)
 */

import { BufferAttribute, BufferGeometry } from 'three'

import type { ZoneMeshBuffer, ZoneMeshVertex } from '~/lib/resource/zoneResource'
import type { Vector3 } from '~/lib/resource/byteReader'
import { buildTriangleIndicesForMesh } from './meshGeometry'

export function buildGeometryForZoneMesh(buffer: ZoneMeshBuffer): BufferGeometry {
  const geometry = new BufferGeometry()
  const vertexCount = buffer.vertices.length

  const position0 = new Float32Array(vertexCount * 3)
  const position1 = new Float32Array(vertexCount * 3)
  const normal = new Float32Array(vertexCount * 3)
  const tangent = new Float32Array(vertexCount * 3)
  const texCoords = new Float32Array(vertexCount * 2)
  const vertexColor = new Float32Array(vertexCount * 4)

  for (let i = 0; i < vertexCount; i++) {
    const vertex = buffer.vertices[i]!
    const tang = buffer.tangents[i]

    writeVec3(position0, i * 3, vertex.p0)
    writeVec3(position1, i * 3, vertex.p1)
    writeVec3(normal, i * 3, vertex.normal)
    writeVec3(tangent, i * 3, tang ?? { x: 1, y: 0, z: 0 })

    texCoords[i * 2] = vertex.u
    texCoords[i * 2 + 1] = vertex.v

    // Zone vertex colors: normalize bytes to [0,1]. The shader's 2.0* multiplier
    // (zoneShader.ts) provides the zone-specific 2x scaling — do NOT pre-multiply here.
    const c = vertex.color
    vertexColor[i * 4] = c.r / 255
    vertexColor[i * 4 + 1] = c.g / 255
    vertexColor[i * 4 + 2] = c.b / 255
    vertexColor[i * 4 + 3] = c.a / 255
  }

  // Use attribute names matching the ximShader layout locations
  geometry.setAttribute('position', new BufferAttribute(position0, 3))
  geometry.setAttribute('position0', new BufferAttribute(position0, 3))
  geometry.setAttribute('position1', new BufferAttribute(position1, 3))
  geometry.setAttribute('normal', new BufferAttribute(normal, 3))
  geometry.setAttribute('tangent', new BufferAttribute(tangent, 3))
  geometry.setAttribute('textureCoords', new BufferAttribute(texCoords, 2))
  geometry.setAttribute('vertexColor', new BufferAttribute(vertexColor, 4))

  geometry.setIndex(buildTriangleIndicesForMesh(buffer.meshType, vertexCount))
  geometry.computeBoundingSphere()

  return geometry
}

function writeVec3(target: Float32Array, offset: number, v: Vector3): void {
  target[offset] = v.x
  target[offset + 1] = v.y
  target[offset + 2] = v.z
}
