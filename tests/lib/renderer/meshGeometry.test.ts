import { describe, expect, it } from 'vitest'

import { buildTriangleIndicesForMesh } from '~/lib/renderer/meshGeometry'

describe('meshGeometry', () => {
  it('triangulates tri-strip meshes and converts winding for Three.js front faces', () => {
    const indices = buildTriangleIndicesForMesh('TriStrip', 5)
    expect(indices).toEqual([0, 2, 1, 2, 3, 1, 2, 4, 3])
  })

  it('triangulates tri-mesh meshes and flips each triangle winding', () => {
    const indices = buildTriangleIndicesForMesh('TriMesh', 6)
    expect(indices).toEqual([0, 2, 1, 3, 5, 4])
  })
})
