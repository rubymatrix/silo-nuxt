import { describe, expect, it } from 'vitest'

import { decodeDxt1ToRgba, decodeDxt3ToRgba } from '~/lib/renderer/textureDecoder'

function expectAllPixels(
  data: Uint8Array,
  width: number,
  height: number,
  rgba: readonly [number, number, number, number],
): void {
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const offset = (y * width + x) * 4
      expect(data[offset]).toBe(rgba[0])
      expect(data[offset + 1]).toBe(rgba[1])
      expect(data[offset + 2]).toBe(rgba[2])
      expect(data[offset + 3]).toBe(rgba[3])
    }
  }
}

describe('textureDecoder', () => {
  it('decodes a flat DXT1 block', () => {
    const block = new Uint8Array([
      0x00, 0xf8,
      0xe0, 0x07,
      0x00, 0x00, 0x00, 0x00,
    ])

    const rgba = decodeDxt1ToRgba(block, 4, 4)
    expectAllPixels(rgba, 4, 4, [255, 0, 0, 255])
  })

  it('decodes a flat DXT3 block', () => {
    const block = new Uint8Array([
      0xff, 0xff, 0xff, 0xff,
      0xff, 0xff, 0xff, 0xff,
      0x00, 0xf8,
      0xe0, 0x07,
      0x00, 0x00, 0x00, 0x00,
    ])

    const rgba = decodeDxt3ToRgba(block, 4, 4)
    // DXT3 alpha nibble 0xF → 15 * 255 / 16 ≈ 239 (matches Kotlin integer division)
    expectAllPixels(rgba, 4, 4, [255, 0, 0, 239])
  })
})
