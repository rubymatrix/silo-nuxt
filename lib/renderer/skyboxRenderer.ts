/**
 * Skybox renderer — procedural colored hemisphere.
 *
 * Ported from xim/poc/EnvironmentManager.kt (SkyBoxMesh class).
 *
 * The skybox is built from 8 horizontal elevation layers connected by 7 triangle
 * strips. Each layer is a ring of points at a given elevation angle, colored by
 * the corresponding SkyBox slice. The hemisphere sits at the scene origin; the
 * camera is always inside it.
 */

import {
  BackSide,
  BufferAttribute,
  BufferGeometry,
  Group,
  Mesh,
  MeshBasicMaterial,
  type Scene,
} from 'three'

import type { SkyBox, SkyBoxSlice } from '~/lib/resource/zoneResource'

export class SkyboxRenderer {
  private readonly root = new Group()
  private meshes: Mesh[] = []

  /**
   * Build (or rebuild) the skybox hemisphere from interpolated skybox config.
   * Call when time-of-day changes or on initial zone load.
   */
  build(scene: Scene, skyBox: SkyBox): void {
    this.dispose(scene)

    if (skyBox.radius <= 0 || skyBox.spokes === 0 || skyBox.slices.length < 2) {
      return
    }

    const spokes = skyBox.spokes
    const slices = skyBox.slices

    // Build rotation matrices around Y axis for each spoke
    const thetaStep = (2 * Math.PI) / spokes

    // Compute layer positions: each layer is a ring of `spokes` points
    const layers: { x: number, y: number, z: number }[][] = []
    for (let i = 0; i < slices.length; i++) {
      const layer: { x: number, y: number, z: number }[] = []
      // Elevation: 0 = horizon, 1 = zenith. Map to angle: 0 → 0, 1 → -PI/2
      const phi = -0.5 * Math.PI * slices[i]!.elevation

      // Start with a point on the X axis at the given radius, rotate by phi around Z,
      // then by each spoke's theta around Y.
      const cosPhi = Math.cos(phi)
      const sinPhi = Math.sin(phi)

      for (let j = 0; j < spokes; j++) {
        const theta = thetaStep * j
        const cosTheta = Math.cos(theta)
        const sinTheta = Math.sin(theta)

        // rotateZ(phi) on (radius, 0, 0) → (radius*cos(phi), radius*sin(phi), 0)
        // Then rotateY(theta) on that:
        const rx = skyBox.radius * cosPhi
        const ry = skyBox.radius * sinPhi
        layer.push({
          x: rx * cosTheta,
          y: ry,
          z: -rx * sinTheta,
        })
      }
      layers.push(layer)
    }

    // Build 7 triangle strips connecting adjacent layers
    for (let i = 0; i < slices.length - 1; i++) {
      const lowerLayer = layers[i]!
      const upperLayer = layers[i + 1]!
      const lowerColor = sliceToColor(slices[i]!)
      const upperColor = sliceToColor(slices[i + 1]!)

      const vertexCount = (spokes + 1) * 2
      const positions = new Float32Array(vertexCount * 3)
      const colors = new Float32Array(vertexCount * 3)

      let vi = 0
      for (let j = 0; j < spokes; j++) {
        // Lower vertex
        const lp = lowerLayer[j]!
        positions[vi * 3] = lp.x
        positions[vi * 3 + 1] = lp.y
        positions[vi * 3 + 2] = lp.z
        colors[vi * 3] = lowerColor.r
        colors[vi * 3 + 1] = lowerColor.g
        colors[vi * 3 + 2] = lowerColor.b
        vi++

        // Upper vertex
        const up = upperLayer[j]!
        positions[vi * 3] = up.x
        positions[vi * 3 + 1] = up.y
        positions[vi * 3 + 2] = up.z
        colors[vi * 3] = upperColor.r
        colors[vi * 3 + 1] = upperColor.g
        colors[vi * 3 + 2] = upperColor.b
        vi++
      }

      // Close the strip by repeating spoke 0
      const lp0 = lowerLayer[0]!
      positions[vi * 3] = lp0.x
      positions[vi * 3 + 1] = lp0.y
      positions[vi * 3 + 2] = lp0.z
      colors[vi * 3] = lowerColor.r
      colors[vi * 3 + 1] = lowerColor.g
      colors[vi * 3 + 2] = lowerColor.b
      vi++

      const up0 = upperLayer[0]!
      positions[vi * 3] = up0.x
      positions[vi * 3 + 1] = up0.y
      positions[vi * 3 + 2] = up0.z
      colors[vi * 3] = upperColor.r
      colors[vi * 3 + 1] = upperColor.g
      colors[vi * 3 + 2] = upperColor.b
      vi++

      // Convert triangle strip to triangle list indices
      const indices = triangleStripToList(vertexCount)

      const geometry = new BufferGeometry()
      geometry.setAttribute('position', new BufferAttribute(positions, 3))
      geometry.setAttribute('color', new BufferAttribute(colors, 3))
      geometry.setIndex(indices)

      const material = new MeshBasicMaterial({
        vertexColors: true,
        depthWrite: false,
        side: BackSide,
      })

      const mesh = new Mesh(geometry, material)
      mesh.frustumCulled = false
      mesh.renderOrder = -1000 // Render before everything else
      this.meshes.push(mesh)
      this.root.add(mesh)
    }

    scene.add(this.root)
  }

  dispose(scene: Scene): void {
    for (const mesh of this.meshes) {
      mesh.geometry.dispose()
      if (mesh.material instanceof MeshBasicMaterial) {
        mesh.material.dispose()
      }
    }
    scene.remove(this.root)
    this.root.clear()
    this.meshes = []
  }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function sliceToColor(slice: SkyBoxSlice): { r: number, g: number, b: number } {
  return {
    r: slice.color.r / 255,
    g: slice.color.g / 255,
    b: slice.color.b / 255,
  }
}

/** Convert a triangle strip with N vertices into a triangle list index array. */
function triangleStripToList(vertexCount: number): number[] {
  const indices: number[] = []
  for (let i = 0; i < vertexCount - 2; i++) {
    if (i % 2 === 0) {
      indices.push(i, i + 1, i + 2)
    } else {
      indices.push(i, i + 2, i + 1)
    }
  }
  return indices
}
