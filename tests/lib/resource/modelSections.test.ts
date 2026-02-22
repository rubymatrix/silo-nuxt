import { describe, expect, it } from 'vitest'

import { ByteReader } from '~/lib/resource/byteReader'
import { SectionHeader } from '~/lib/resource/datParser'
import { DatId, DirectoryResource, SkeletonAnimation } from '~/lib/resource/datResource'
import { SkeletonAnimationSection } from '~/lib/resource/skeletonAnimationSection'
import { SkeletonMeshSection } from '~/lib/resource/skeletonMeshSection'
import { SkeletonSection } from '~/lib/resource/skeletonSection'
import { TextureSection } from '~/lib/resource/textureSection'
import { WeightedMeshSection } from '~/lib/resource/weightedMeshSection'

class ByteBuilder {
  private readonly data: number[] = []

  get length(): number {
    return this.data.length
  }

  push8(value: number): void {
    this.data.push(value & 0xff)
  }

  push16(value: number): void {
    this.push8(value)
    this.push8(value >>> 8)
  }

  push32(value: number): void {
    this.push16(value)
    this.push16(value >>> 16)
  }

  pushFloat(value: number): void {
    const bytes = new Uint8Array(new Float32Array([value]).buffer)
    this.data.push(...bytes)
  }

  pushString(value: string, length: number): void {
    for (let i = 0; i < length; i += 1) {
      this.push8(value.charCodeAt(i) ?? 0)
    }
  }

  set16(offset: number, value: number): void {
    this.data[offset] = value & 0xff
    this.data[offset + 1] = (value >>> 8) & 0xff
  }

  toUint8Array(): Uint8Array {
    return Uint8Array.from(this.data)
  }
}

function header(id = 'test'): SectionHeader {
  const sectionHeader = new SectionHeader()
  sectionHeader.sectionId = new DatId(id)
  sectionHeader.sectionStartPosition = 0
  sectionHeader.dataStartPosition = 0
  sectionHeader.sectionSize = 0x1000
  sectionHeader.localDir = new DirectoryResource(null, new DatId('root'))
  return sectionHeader
}

describe('TextureSection', () => {
  it('parses DXT1 texture metadata and payload', () => {
    const b = new ByteBuilder()
    b.push8(0xa1)
    b.pushString('tx_test0', 0x10)
    b.push32(0x28)
    b.push32(4)
    b.push32(4)
    b.push16(1)
    b.push16(16)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.push32(0x10)
    b.pushString('1TXD', 4)
    b.push32(0x100)
    b.push32(0x40)
    for (let i = 0; i < 8; i += 1) {
      b.push8(i)
    }

    const parser = new TextureSection(header('tex0'))
    const result = parser.getResource(new ByteReader(b.toUint8Array(), 'texture.dat'))
    const entry = result.entry?.datEntry

    expect(entry?.id).toEqual(new DatId('tex0'))
    expect(entry?.resourceName?.()).toBe('tx_test0\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000')
    expect(entry).toMatchObject({
      name: 'tx_test0\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000',
      width: 4,
      height: 4,
      bitCount: 16,
      textureType: 0xa1,
      dxtType: 'DXT1',
    })
  })

  it('keeps fixed 0x10 texture name bytes including trailing NUL', () => {
    const b = new ByteBuilder()
    b.push8(0x91)
    b.pushString('abc', 0x10)
    b.push32(0x28)
    b.push32(1)
    b.push32(1)
    b.push16(1)
    b.push16(32)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.push32(0x10)
    b.push32(0xff00ff00)

    const parser = new TextureSection(header('tex0'))
    const result = parser.getResource(new ByteReader(b.toUint8Array(), 'texture.dat'))
    const entry = result.entry?.datEntry as { name: string }

    expect(entry.name.length).toBe(0x10)
    expect(entry.name.startsWith('abc')).toBe(true)
    expect(entry.name.includes('\u0000')).toBe(true)
  })

  it('throws if reserved zero field is non-zero', () => {
    const b = new ByteBuilder()
    b.push8(0x91)
    b.pushString('tx_test0', 0x10)
    b.push32(0x28)
    b.push32(1)
    b.push32(1)
    b.push16(1)
    b.push16(32)
    b.push32(0)
    b.push32(0)
    b.push32(1)
    b.push32(0)
    b.push32(0)
    b.push32(0x10)
    b.push32(0)

    expect(() => TextureSection.read(new ByteReader(b.toUint8Array(), 'texture.dat'), new DatId('tex0'))).toThrow()
  })

  it('throws when A1 dxt tag is unknown', () => {
    const b = new ByteBuilder()
    b.push8(0xa1)
    b.pushString('tx_test0', 0x10)
    b.push32(0x28)
    b.push32(4)
    b.push32(4)
    b.push16(1)
    b.push16(16)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.push32(0x10)
    b.pushString('????', 4)
    b.push32(0x100)
    b.push32(0x40)
    for (let i = 0; i < 8; i += 1) {
      b.push8(i)
    }

    expect(() => TextureSection.read(new ByteReader(b.toUint8Array(), 'texture.dat'), new DatId('tex0'))).toThrow()
  })

  it('reads palette+indices payload size for paletted textures', () => {
    const b = new ByteBuilder()
    b.push8(0x91)
    b.pushString('tx_test0', 0x10)
    b.push32(0x28)
    b.push32(2)
    b.push32(2)
    b.push16(1)
    b.push16(8)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.push32(0x10)
    for (let i = 0; i < 256; i += 1) {
      b.push32(i)
    }
    b.push8(0)
    b.push8(1)
    b.push8(2)
    b.push8(3)

    const entry = TextureSection.read(new ByteReader(b.toUint8Array(), 'texture.dat'), new DatId('tex0'))
    expect(entry?.rawData.length).toBe(256 * 4 + 4)
  })
})

describe('SkeletonSection', () => {
  it('parses joints, references, and bounding boxes', () => {
    const b = new ByteBuilder()
    b.push8(0)
    b.push8(0)
    b.push8(1)
    b.push8(0)

    b.push8(0)
    b.push8(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(1)
    b.pushFloat(1)
    b.pushFloat(2)
    b.pushFloat(3)

    b.push16(1)
    b.push16(0xffff)
    b.push16(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(1)
    b.pushFloat(2)
    b.pushFloat(3)

    b.pushFloat(2)
    b.pushFloat(-2)
    b.pushFloat(3)
    b.pushFloat(-3)
    b.pushFloat(4)
    b.pushFloat(-4)

    const sectionHeader = header('skel')
    sectionHeader.sectionSize = b.length

    const parser = new SkeletonSection(sectionHeader)
    const result = parser.getResource(new ByteReader(b.toUint8Array(), 'skeleton.dat'))
    const entry = result.entry?.datEntry

    expect(entry?.id).toEqual(new DatId('skel'))
    expect(entry).toMatchObject({
      joints: [{ parentIndex: -1 }],
      jointReferences: [{ index: 0 }],
      boundingBoxes: [
        {
          min: { x: -3, y: -2, z: -4 },
          max: { x: 3, y: 2, z: 4 },
        },
      ],
    })
  })

  it('computes skeleton size.y from AboveHead standard joint reference', () => {
    const b = new ByteBuilder()
    b.push8(0)
    b.push8(0)
    b.push8(1)
    b.push8(0)

    b.push8(0)
    b.push8(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(1)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(0)

    b.push16(3)
    b.push16(0)
    for (let i = 0; i < 3; i += 1) {
      b.push16(0)
      b.pushFloat(0)
      b.pushFloat(0)
      b.pushFloat(0)
      b.pushFloat(0)
      b.pushFloat(i === 2 ? 2 : 0)
      b.pushFloat(0)
    }

    const sectionHeader = header('skel')
    sectionHeader.sectionSize = b.length

    const parser = new SkeletonSection(sectionHeader)
    const result = parser.getResource(new ByteReader(b.toUint8Array(), 'skeleton.dat'))
    const entry = result.entry?.datEntry as { size: { y: number } }

    expect(entry.size.y).toBe(2)
  })
})

describe('SkeletonAnimationSection', () => {
  it('parses keyframe transforms and interpolates between frames', () => {
    const b = new ByteBuilder()

    b.push16(0)
    b.push16(1)
    b.push16(2)
    b.pushFloat(1)

    b.push32(0)
    for (let i = 0; i < 4; i += 1) {
      b.push32(0)
    }
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(1)

    for (let i = 0; i < 3; i += 1) {
      b.push32(0)
    }
    b.pushFloat(10)
    b.pushFloat(20)
    b.pushFloat(30)

    for (let i = 0; i < 3; i += 1) {
      b.push32(0)
    }
    b.pushFloat(1)
    b.pushFloat(1)
    b.pushFloat(1)

    const parser = new SkeletonAnimationSection(header('anim'))
    const result = parser.getResource(new ByteReader(b.toUint8Array(), 'anim.dat'))
    const entry = result.entry?.datEntry

    expect(entry?.id).toEqual(new DatId('anim'))
    expect(entry).toMatchObject({
      animation: {
        numJoints: 1,
        numFrames: 2,
      },
    })

    const transform = (entry as { animation: { getJointTransform: (joint: number, frame: number) => { translation: { x: number } } | null } }).animation.getJointTransform(0, 0.5)
    expect(transform?.translation.x).toBe(10)
  })

  it('matches Kotlin nlerp shortest-path behavior for opposite quaternions', () => {
    const animation = new SkeletonAnimation(new DatId('anim'), 1, 2, 1)
    animation.keyFrameSets.set(0, [
      {
        rotation: { x: 0, y: 0, z: 0, w: 1 },
        translation: { x: 0, y: 0, z: 0 },
        scale: { x: 1, y: 1, z: 1 },
      },
      {
        rotation: { x: 0, y: 0, z: 0, w: -1 },
        translation: { x: 0, y: 0, z: 0 },
        scale: { x: 1, y: 1, z: 1 },
      },
    ])

    const transform = animation.getJointTransform(0, 0.5)
    expect(transform?.rotation.w).toBeCloseTo(1, 6)
  })

  it('treats negative sequence offsets as invalid and skips the joint set', () => {
    const b = new ByteBuilder()

    b.push16(0)
    b.push16(1)
    b.push16(2)
    b.pushFloat(1)

    b.push32(0)
    b.push32(0xffffffff)
    b.push32(0)
    b.push32(0)
    b.push32(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(1)

    for (let i = 0; i < 3; i += 1) {
      b.push32(0)
    }
    b.pushFloat(10)
    b.pushFloat(20)
    b.pushFloat(30)

    for (let i = 0; i < 3; i += 1) {
      b.push32(0)
    }
    b.pushFloat(1)
    b.pushFloat(1)
    b.pushFloat(1)

    const parser = new SkeletonAnimationSection(header('anim'))
    const result = parser.getResource(new ByteReader(b.toUint8Array(), 'anim.dat'))
    const entry = result.entry?.datEntry as { animation: SkeletonAnimation }

    expect(entry.animation.getJointTransform(0, 0)).toBeNull()
  })
})

describe('SkeletonMeshSection', () => {
  it('parses a simple textured triangle mesh', () => {
    const b = new ByteBuilder()

    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)

    const instructionOffsetPos = b.length
    b.push32(0)
    b.push8(1)
    b.push8(2)

    const jointOffsetPos = b.length
    b.push32(0)
    b.push16(1)

    const vertexCountsOffsetPos = b.length
    b.push32(0)
    b.push16(2)

    const mappingOffsetPos = b.length
    b.push32(0)
    b.push16(3)

    const vertexDataOffsetPos = b.length
    b.push32(0)
    b.push16(0)

    b.push32(0)
    b.push16(0)

    const jointOffset = b.length
    b.push16(0)

    const vertexCountsOffset = b.length
    b.push16(3)
    b.push16(0)

    const mappingOffset = b.length
    for (let i = 0; i < 3; i += 1) {
      b.push16(0)
      b.push16(0)
    }

    const vertexDataOffset = b.length
    for (let i = 0; i < 3; i += 1) {
      b.pushFloat(i)
      b.pushFloat(0)
      b.pushFloat(0)
      b.pushFloat(0)
      b.pushFloat(1)
      b.pushFloat(0)
    }

    const instructionOffset = b.length
    b.push16(0x8000)
    b.pushString('tx_mesh0', 0x10)
    b.push16(0x0054)
    b.push16(1)
    b.push16(0)
    b.push16(1)
    b.push16(2)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(1)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(1)
    b.push16(0xffff)

    b.set16(instructionOffsetPos, instructionOffset / 2)
    b.set16(jointOffsetPos, jointOffset / 2)
    b.set16(vertexCountsOffsetPos, vertexCountsOffset / 2)
    b.set16(mappingOffsetPos, mappingOffset / 2)
    b.set16(vertexDataOffsetPos, vertexDataOffset / 2)

    const parser = new SkeletonMeshSection(header('smes'))
    const result = parser.getResource(new ByteReader(b.toUint8Array(), 'mesh.dat'))
    const entry = result.entry?.datEntry

    expect(entry).toMatchObject({
      meshes: [{ textureName: 'tx_mesh0', meshType: 'TriMesh', vertexCount: 3 }],
    })
  })

  it('parses interleaved double-joint position data like Kotlin', () => {
    const b = new ByteBuilder()

    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)

    const instructionOffsetPos = b.length
    b.push32(0)
    b.push8(1)
    b.push8(1)

    const jointOffsetPos = b.length
    b.push32(0)
    b.push16(1)

    const vertexCountsOffsetPos = b.length
    b.push32(0)
    b.push16(2)

    const mappingOffsetPos = b.length
    b.push32(0)
    b.push16(1)

    const vertexDataOffsetPos = b.length
    b.push32(0)
    b.push16(0)

    b.push32(0)
    b.push16(0)

    const jointOffset = b.length
    b.push16(0)

    const vertexCountsOffset = b.length
    b.push16(0)
    b.push16(1)

    const mappingOffset = b.length
    b.push16(0)
    b.push16(0)

    const vertexDataOffset = b.length
    b.pushFloat(1)
    b.pushFloat(10)
    b.pushFloat(2)
    b.pushFloat(20)
    b.pushFloat(3)
    b.pushFloat(30)
    b.pushFloat(0.25)
    b.pushFloat(0.75)
    b.pushFloat(4)
    b.pushFloat(40)
    b.pushFloat(5)
    b.pushFloat(50)
    b.pushFloat(6)
    b.pushFloat(60)

    const instructionOffset = b.length
    b.push16(0x0054)
    b.push16(1)
    b.push16(0)
    b.push16(0)
    b.push16(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.push16(0xffff)

    b.set16(instructionOffsetPos, instructionOffset / 2)
    b.set16(jointOffsetPos, jointOffset / 2)
    b.set16(vertexCountsOffsetPos, vertexCountsOffset / 2)
    b.set16(mappingOffsetPos, mappingOffset / 2)
    b.set16(vertexDataOffsetPos, vertexDataOffset / 2)

    const parser = new SkeletonMeshSection(header('smes'))
    const result = parser.getResource(new ByteReader(b.toUint8Array(), 'mesh.dat'))
    const entry = result.entry?.datEntry as { meshes: Array<{ vertices: Array<{ position0: { x: number, y: number, z: number }, position1: { x: number, y: number, z: number } }> }> }

    const v = entry.meshes[0]?.vertices[0]
    expect(v?.position0).toEqual({ x: 1, y: 2, z: 3 })
    expect(v?.position1).toEqual({ x: 10, y: 20, z: 30 })
  })

  it('parses tri-strip indices then UVs ordering', () => {
    const b = new ByteBuilder()

    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)

    const instructionOffsetPos = b.length
    b.push32(0)
    b.push8(1)
    b.push8(2)

    const jointOffsetPos = b.length
    b.push32(0)
    b.push16(1)

    const vertexCountsOffsetPos = b.length
    b.push32(0)
    b.push16(2)

    const mappingOffsetPos = b.length
    b.push32(0)
    b.push16(3)

    const vertexDataOffsetPos = b.length
    b.push32(0)
    b.push16(0)

    b.push32(0)
    b.push16(0)

    const jointOffset = b.length
    b.push16(0)

    const vertexCountsOffset = b.length
    b.push16(3)
    b.push16(0)

    const mappingOffset = b.length
    for (let i = 0; i < 3; i += 1) {
      b.push16(0)
      b.push16(0)
    }

    const vertexDataOffset = b.length
    for (let i = 0; i < 3; i += 1) {
      b.pushFloat(i)
      b.pushFloat(0)
      b.pushFloat(0)
      b.pushFloat(0)
      b.pushFloat(1)
      b.pushFloat(0)
    }

    const instructionOffset = b.length
    b.push16(0x5453)
    b.push16(1)
    b.push16(0)
    b.push16(1)
    b.push16(2)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(1)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(1)
    b.push16(0xffff)

    b.set16(instructionOffsetPos, instructionOffset / 2)
    b.set16(jointOffsetPos, jointOffset / 2)
    b.set16(vertexCountsOffsetPos, vertexCountsOffset / 2)
    b.set16(mappingOffsetPos, mappingOffset / 2)
    b.set16(vertexDataOffsetPos, vertexDataOffset / 2)

    const parser = new SkeletonMeshSection(header('smes'))
    const result = parser.getResource(new ByteReader(b.toUint8Array(), 'mesh.dat'))
    const entry = result.entry?.datEntry as { meshes: Array<{ vertices: Array<{ u: number, v: number }> }> }

    expect(entry.meshes[0]?.vertices[0]).toMatchObject({ u: 0, v: 0 })
    expect(entry.meshes[0]?.vertices[1]).toMatchObject({ u: 1, v: 0 })
    expect(entry.meshes[0]?.vertices[2]).toMatchObject({ u: 0, v: 1 })
  })

  it('supports untextured mesh opcodes 0x0043 and 0x4353', () => {
    const b = new ByteBuilder()

    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)

    const instructionOffsetPos = b.length
    b.push32(0)
    b.push8(2)
    b.push8(2)

    const jointOffsetPos = b.length
    b.push32(0)
    b.push16(1)

    const vertexCountsOffsetPos = b.length
    b.push32(0)
    b.push16(2)

    const mappingOffsetPos = b.length
    b.push32(0)
    b.push16(3)

    const vertexDataOffsetPos = b.length
    b.push32(0)
    b.push16(0)

    b.push32(0)
    b.push16(0)

    const jointOffset = b.length
    b.push16(0)

    const vertexCountsOffset = b.length
    b.push16(3)
    b.push16(0)

    const mappingOffset = b.length
    for (let i = 0; i < 3; i += 1) {
      b.push16(0)
      b.push16(0)
    }

    const vertexDataOffset = b.length
    for (let i = 0; i < 3; i += 1) {
      b.pushFloat(i)
      b.pushFloat(0)
      b.pushFloat(0)
      b.pushFloat(0)
      b.pushFloat(1)
      b.pushFloat(0)
    }

    const instructionOffset = b.length
    b.push16(0x0043)
    b.push16(1)
    b.push16(0)
    b.push16(1)
    b.push16(2)
    b.push8(0x01)
    b.push8(0x02)
    b.push8(0x03)
    b.push8(0x04)
    b.push16(0x4353)
    b.push16(1)
    b.push16(0)
    b.push16(1)
    b.push16(2)
    b.push8(0x05)
    b.push8(0x06)
    b.push8(0x07)
    b.push8(0x08)
    b.push16(0xffff)

    b.set16(instructionOffsetPos, instructionOffset / 2)
    b.set16(jointOffsetPos, jointOffset / 2)
    b.set16(vertexCountsOffsetPos, vertexCountsOffset / 2)
    b.set16(mappingOffsetPos, mappingOffset / 2)
    b.set16(vertexDataOffsetPos, vertexDataOffset / 2)

    const parser = new SkeletonMeshSection(header('smes'))
    const result = parser.getResource(new ByteReader(b.toUint8Array(), 'mesh.dat'))
    const entry = result.entry?.datEntry as { meshes: Array<{ meshType: string, vertices: Array<{ color?: { b: number, g: number, r: number, a: number } }> }> }

    expect(entry.meshes).toHaveLength(2)
    expect(entry.meshes[0]?.meshType).toBe('TriMesh')
    expect(entry.meshes[0]?.vertices[0]?.color).toMatchObject({ b: 1, g: 2, r: 3, a: 4 })
    expect(entry.meshes[1]?.meshType).toBe('TriStrip')
    expect(entry.meshes[1]?.vertices[0]?.color).toMatchObject({ b: 5, g: 6, r: 7, a: 8 })
  })

  it('mirrors symmetric meshes instead of duplicating vertices', () => {
    const b = new ByteBuilder()

    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(0)
    b.push8(1)
    b.push8(0)

    const instructionOffsetPos = b.length
    b.push32(0)
    b.push8(1)
    b.push8(2)

    const jointOffsetPos = b.length
    b.push32(0)
    b.push16(1)

    const vertexCountsOffsetPos = b.length
    b.push32(0)
    b.push16(2)

    const mappingOffsetPos = b.length
    b.push32(0)
    b.push16(3)

    const vertexDataOffsetPos = b.length
    b.push32(0)
    b.push16(0)

    b.push32(0)
    b.push16(0)

    const jointOffset = b.length
    b.push16(0)

    const vertexCountsOffset = b.length
    b.push16(3)
    b.push16(0)

    const mappingOffset = b.length
    for (let i = 0; i < 3; i += 1) {
      b.push16(0x4000)
      b.push16(0)
    }

    const vertexDataOffset = b.length
    for (let i = 0; i < 3; i += 1) {
      b.pushFloat(1)
      b.pushFloat(0)
      b.pushFloat(0)
      b.pushFloat(0)
      b.pushFloat(1)
      b.pushFloat(0)
    }

    const instructionOffset = b.length
    b.push16(0x0054)
    b.push16(1)
    b.push16(0)
    b.push16(1)
    b.push16(2)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(1)
    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(1)
    b.push16(0xffff)

    b.set16(instructionOffsetPos, instructionOffset / 2)
    b.set16(jointOffsetPos, jointOffset / 2)
    b.set16(vertexCountsOffsetPos, vertexCountsOffset / 2)
    b.set16(mappingOffsetPos, mappingOffset / 2)
    b.set16(vertexDataOffsetPos, vertexDataOffset / 2)

    const parser = new SkeletonMeshSection(header('smes'))
    const result = parser.getResource(new ByteReader(b.toUint8Array(), 'mesh.dat'))
    const entry = result.entry?.datEntry as { meshes: Array<{ vertices: Array<{ position0: { x: number } }> }> }

    expect(entry.meshes).toHaveLength(2)
    expect(entry.meshes[0]?.vertices[0]?.position0.x).toBe(1)
    expect(entry.meshes[1]?.vertices[0]?.position0.x).toBe(-1)
  })
})

describe('WeightedMeshSection', () => {
  it('parses a weighted mesh and blends positions by provided weights', () => {
    const b = new ByteBuilder()

    b.push16(1)
    b.push8(0x02)
    b.push8(0)
    b.push16(1)
    b.push16(1)

    const indexOffsetPos = b.length
    b.push16(0)

    b.push16(1)

    const colorOffsetPos = b.length
    b.push16(0)

    const uvOffsetPos = b.length
    b.push16(0)

    b.pushString('tx_wgt00', 0x10)

    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.push32(0)

    b.pushFloat(10)
    b.pushFloat(0)
    b.pushFloat(0)
    b.push32(0)

    const colorOffset = b.length
    b.push8(0x80)
    b.push8(0x80)
    b.push8(0x80)
    b.push8(0xff)
    b.push8(0x80)
    b.push8(0x80)
    b.push8(0x80)
    b.push8(0xff)
    b.push8(0x80)
    b.push8(0x80)
    b.push8(0x80)
    b.push8(0xff)

    const uvOffset = b.length
    for (let i = 0; i < 3; i += 1) {
      b.pushFloat(0)
      b.pushFloat(0)
    }

    const indexOffset = b.length
    b.push16(0)
    b.push16(0)
    b.push16(0)
    b.push16(0)
    b.push16(0)
    b.push16(0)

    b.set16(indexOffsetPos, indexOffset)
    b.set16(colorOffsetPos, colorOffset)
    b.set16(uvOffsetPos, uvOffset)

    const parser = new WeightedMeshSection(header('wmes'), { zoneResource: false })
    const result = parser.getResource(new ByteReader(b.toUint8Array(), 'weighted.dat'))
    const entry = result.entry?.datEntry

    const mesh = (entry as { weightedMesh: { getWeightedTriangles: (weights: number[]) => Array<{ position: { x: number } }> } }).weightedMesh
    const blended = mesh.getWeightedTriangles([0.25, 0.75])

    expect(blended[0]?.position.x).toBe(7.5)
  })

  it('throws when position index exceeds numPositions', () => {
    const b = new ByteBuilder()

    b.push16(1)
    b.push8(0x01)
    b.push8(0)
    b.push16(1)
    b.push16(1)

    const indexOffsetPos = b.length
    b.push16(0)

    b.push16(1)

    const colorOffsetPos = b.length
    b.push16(0)

    const uvOffsetPos = b.length
    b.push16(0)

    b.pushString('tx_wgt00', 0x10)

    b.pushFloat(0)
    b.pushFloat(0)
    b.pushFloat(0)
    b.push32(0)

    const colorOffset = b.length
    for (let i = 0; i < 3; i += 1) {
      b.push8(0x80)
      b.push8(0x80)
      b.push8(0x80)
      b.push8(0xff)
    }

    const uvOffset = b.length
    for (let i = 0; i < 3; i += 1) {
      b.pushFloat(0)
      b.pushFloat(0)
    }

    const indexOffset = b.length
    b.push16(2)
    b.push16(0)
    b.push16(0)
    b.push16(0)
    b.push16(0)
    b.push16(0)

    b.set16(indexOffsetPos, indexOffset)
    b.set16(colorOffsetPos, colorOffset)
    b.set16(uvOffsetPos, uvOffset)

    const parser = new WeightedMeshSection(header('wmes'), { zoneResource: false })
    expect(() => parser.getResource(new ByteReader(b.toUint8Array(), 'weighted.dat'))).toThrow()
  })
})
