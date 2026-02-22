import { describe, expect, it, vi } from 'vitest'

import {
  DatId,
  DirectoryResource,
  TextureResource,
  type TextureData,
} from '~/lib/resource/datResource'
import {
  applyMaterialAlpha,
  applyXiCameraConventions,
  applySoftwareSkinningToMesh,
  composeJointMatrix,
  resolveMaxJointUniforms,
  resolveTextureResource,
  toLocalSkeletonPose,
} from '~/lib/renderer/threeRenderer'
import { BufferAttribute, BufferGeometry, Matrix4, Mesh, MeshBasicMaterial, ShaderMaterial } from 'three'

describe('resolveTextureResource', () => {
  it('returns null when mesh resource has no local directory', () => {
    const meshResource = {
      meshes: [],
    }

    expect(resolveTextureResource(meshResource as never, 'tx00')).toBeNull()
  })

  it('falls back to global texture registry for sibling resources', () => {
    const meshDir = new DirectoryResource(null, new DatId('meshRoot'))
    const textureDir = new DirectoryResource(null, new DatId('faceRoot'))

    const textureData: TextureData = {
      textureType: 0,
      width: 1,
      height: 1,
      bitCount: 32,
      dxtType: null,
      rawData: new Uint8Array([255, 255, 255, 255]),
    }
    const texture = new TextureResource(new DatId('tx00'), 'face_tex', textureData)
    texture.localDir = textureDir

    const meshResource = {
      meshes: [],
      localDir: meshDir,
    }

    const globalSpy = vi.spyOn(DirectoryResource, 'getGlobalTexture').mockReturnValue(texture)

    const trackedTexture = resolveTextureResource(meshResource as never, 'face_tex')
    expect(trackedTexture).toBe(texture)

    globalSpy.mockRestore()
  })
})

describe('toLocalSkeletonPose', () => {
  it('converts child world transform into parent-relative local transform', () => {
    const joints = [
      {
        parentIndex: -1,
        worldPosition: { x: 2, y: 0, z: 0 },
        worldRotation: { x: 0, y: 0, z: 0, w: 1 },
      },
      {
        parentIndex: 0,
        worldPosition: { x: 5, y: 0, z: 0 },
        worldRotation: { x: 0, y: 0, z: 0, w: 1 },
      },
    ]

    const local = toLocalSkeletonPose(joints, 1)

    expect(local.position).toEqual({ x: 3, y: 0, z: 0 })
    expect(local.rotation).toEqual({ x: 0, y: 0, z: 0, w: 1 })
  })
})

describe('applyXiCameraConventions', () => {
  it('sets camera up-vector to FFXI world up', () => {
    const set = vi.fn()
    const camera = {
      up: { set },
    }

    applyXiCameraConventions(camera as never)

    expect(set).toHaveBeenCalledWith(0, -1, 0)
  })
})

describe('resolveMaxJointUniforms', () => {
  it('caps joint uniforms for low vertex uniform budgets', () => {
    expect(resolveMaxJointUniforms(256)).toBe(56)
  })

  it('keeps the renderer target cap when budget is large enough', () => {
    expect(resolveMaxJointUniforms(1024)).toBe(128)
  })
})

describe('applyMaterialAlpha', () => {
  it('sets opacity for basic materials', () => {
    const material = new MeshBasicMaterial({ opacity: 1, transparent: true })
    applyMaterialAlpha(material, 0.4)
    expect(material.opacity).toBeCloseTo(0.4, 5)
    expect(material.transparent).toBe(true)
  })

  it('sets uAlpha uniform for shader materials', () => {
    const material = new ShaderMaterial({
      uniforms: {
        uAlpha: { value: 1 },
      },
    })

    applyMaterialAlpha(material, 0.25)
    expect(material.uniforms.uAlpha.value).toBeCloseTo(0.25, 5)
  })
})

describe('applySoftwareSkinningToMesh', () => {
  it('updates positions from joint matrices', () => {
    const geometry = new BufferGeometry()
    geometry.setAttribute('position', new BufferAttribute(new Float32Array([0, 0, 0]), 3))
    geometry.setAttribute('position0', new BufferAttribute(new Float32Array([0, 0, 0]), 3))
    geometry.setAttribute('position1', new BufferAttribute(new Float32Array([0, 0, 0]), 3))
    geometry.setAttribute('jointWeight', new BufferAttribute(new Float32Array([1]), 1))
    geometry.setAttribute('joint0', new BufferAttribute(new Float32Array([0]), 1))
    geometry.setAttribute('joint1', new BufferAttribute(new Float32Array([0]), 1))

    const mesh = new Mesh(geometry, new MeshBasicMaterial())
    const joints = [new Matrix4().makeTranslation(2, 0, 0)]

    applySoftwareSkinningToMesh(mesh, joints)

    const position = mesh.geometry.getAttribute('position') as BufferAttribute
    expect(position.getX(0)).toBeCloseTo(2, 5)
    expect(position.getY(0)).toBeCloseTo(0, 5)
    expect(position.getZ(0)).toBeCloseTo(0, 5)
  })
})

describe('composeJointMatrix', () => {
  it('composes a finite matrix from quaternion rotation and translation', () => {
    const matrix = composeJointMatrix(
      { x: 2, y: 3, z: 4 },
      { x: 0, y: 0, z: 0, w: 1 },
    )

    const elements = matrix.elements
    expect(Number.isFinite(elements[12])).toBe(true)
    expect(Number.isFinite(elements[13])).toBe(true)
    expect(Number.isFinite(elements[14])).toBe(true)
    expect(elements[12]).toBeCloseTo(2, 5)
    expect(elements[13]).toBeCloseTo(3, 5)
    expect(elements[14]).toBeCloseTo(4, 5)
  })
})
