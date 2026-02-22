import { ByteReader } from '~/lib/resource/byteReader'
import type { TextureResource } from '~/lib/resource/datResource'

export function decodeTextureToRgba(texture: TextureResource): Uint8Array {
  if (texture.dxtType === 'DXT1') {
    return decodeDxt1ToRgba(texture.rawData, texture.width, texture.height)
  }

  if (texture.dxtType === 'DXT3') {
    return decodeDxt3ToRgba(texture.rawData, texture.width, texture.height)
  }

  if (texture.bitCount === 32) {
    return decodeBgra32(texture.rawData, texture.width, texture.height)
  }

  return decodeIndexed8(texture.rawData, texture.width, texture.height)
}

export function decodeDxt1ToRgba(data: Uint8Array, width: number, height: number): Uint8Array {
  const byteReader = new ByteReader(data)
  const buffer = new Uint8Array(width * height * 4)

  const r = [0, 0, 0, 0]
  const g = [0, 0, 0, 0]
  const b = [0, 0, 0, 0]
  const a = [0, 0, 0, 0]

  for (let y1 = 0; y1 < height; y1 += 4) {
    for (let x1 = 0; x1 < width; x1 += 4) {
      const c0 = byteReader.next16()
      const c1 = byteReader.next16()

      r[0] = ((c0 >>> 11) & 0x1f) * 255 / 31
      g[0] = ((c0 >>> 5) & 0x3f) * 255 / 63
      b[0] = (c0 & 0x1f) * 255 / 31
      a[0] = 255

      r[1] = ((c1 >>> 11) & 0x1f) * 255 / 31
      g[1] = ((c1 >>> 5) & 0x3f) * 255 / 63
      b[1] = (c1 & 0x1f) * 255 / 31
      a[1] = 255

      if (c0 > c1) {
        r[2] = (2 * r[0] + r[1]) / 3
        g[2] = (2 * g[0] + g[1]) / 3
        b[2] = (2 * b[0] + b[1]) / 3
        a[2] = 255

        r[3] = (r[0] + 2 * r[1]) / 3
        g[3] = (g[0] + 2 * g[1]) / 3
        b[3] = (b[0] + 2 * b[1]) / 3
        a[3] = 255
      } else {
        r[2] = (r[0] + r[1]) / 2
        g[2] = (g[0] + g[1]) / 2
        b[2] = (b[0] + b[1]) / 2
        a[2] = 255

        r[3] = 0
        g[3] = 0
        b[3] = 0
        a[3] = 0
      }

      const indices = byteReader.next32BE()
      let count = 15

      for (let y = y1; y < y1 + 4 && y < height; y += 1) {
        for (let x = x1 + 3; x >= x1 && x < width; x -= 1) {
          const pixelIndex = (indices >>> (2 * count)) & 0x3
          count -= 1
          writeRgba(buffer, width, x, y, r[pixelIndex] ?? 0, g[pixelIndex] ?? 0, b[pixelIndex] ?? 0, a[pixelIndex] ?? 0)
        }
      }
    }
  }

  return buffer
}

export function decodeDxt3ToRgba(data: Uint8Array, width: number, height: number): Uint8Array {
  const byteReader = new ByteReader(data)
  const buffer = new Uint8Array(width * height * 4)

  const r = [0, 0, 0, 0]
  const g = [0, 0, 0, 0]
  const b = [0, 0, 0, 0]

  for (let y1 = 0; y1 < height; y1 += 4) {
    for (let x1 = 0; x1 < width; x1 += 4) {
      const alphaHigh = BigInt(byteReader.next32()) << 32n
      const alphaLow = BigInt(byteReader.next32())
      const alpha = alphaHigh | alphaLow

      const c0 = byteReader.next16()
      const c1 = byteReader.next16()

      r[0] = ((c0 >>> 11) & 0x1f) * 255 / 31
      g[0] = ((c0 >>> 5) & 0x3f) * 255 / 63
      b[0] = (c0 & 0x1f) * 255 / 31

      r[1] = ((c1 >>> 11) & 0x1f) * 255 / 31
      g[1] = ((c1 >>> 5) & 0x3f) * 255 / 63
      b[1] = (c1 & 0x1f) * 255 / 31

      r[2] = (2 * r[0] + r[1]) / 3
      g[2] = (2 * g[0] + g[1]) / 3
      b[2] = (2 * b[0] + b[1]) / 3

      r[3] = (r[0] + 2 * r[1]) / 3
      g[3] = (g[0] + 2 * g[1]) / 3
      b[3] = (b[0] + 2 * b[1]) / 3

      const indices = byteReader.next32BE()
      let count = 15

      for (let y = y1; y < y1 + 4 && y < height; y += 1) {
        for (let x = x1 + 3; x >= x1 && x < width; x -= 1) {
          const pixelIndex = (indices >>> (2 * count)) & 0x3
          const pixelAlpha = Number((alpha >> BigInt(4 * count)) & 0xfn) * 255 / 16
          count -= 1
          writeRgba(buffer, width, x, y, r[pixelIndex] ?? 0, g[pixelIndex] ?? 0, b[pixelIndex] ?? 0, pixelAlpha)
        }
      }
    }
  }

  return buffer
}

function decodeBgra32(data: Uint8Array, width: number, height: number): Uint8Array {
  const output = new Uint8Array(data.length)
  for (let y = 0; y < height; y += 1) {
    // Flip vertically to match Kotlin reference (TextureSection.kt parseNoPalette)
    const dstY = height - (y + 1)
    for (let x = 0; x < width; x += 1) {
      const srcIdx = (y * width + x) * 4
      const dstIdx = (dstY * width + x) * 4
      output[dstIdx] = data[srcIdx + 2] ?? 0     // B -> R
      output[dstIdx + 1] = data[srcIdx + 1] ?? 0 // G -> G
      output[dstIdx + 2] = data[srcIdx] ?? 0      // R -> B
      output[dstIdx + 3] = data[srcIdx + 3] ?? 255 // A -> A
    }
  }
  return output
}

function decodeIndexed8(data: Uint8Array, width: number, height: number): Uint8Array {
  const paletteSize = 256 * 4
  const palette = data.slice(0, paletteSize)
  const pixels = data.slice(paletteSize)
  const out = new Uint8Array(width * height * 4)

  for (let y = 0; y < height; y += 1) {
    // Flip vertically to match Kotlin reference (TextureSection.kt parsePalette)
    const dstY = height - (y + 1)
    for (let x = 0; x < width; x += 1) {
      const srcIdx = y * width + x
      const dstIdx = (dstY * width + x) * 4
      const colorIndex = (pixels[srcIdx] ?? 0) * 4
      out[dstIdx] = palette[colorIndex + 2] ?? 0     // B -> R
      out[dstIdx + 1] = palette[colorIndex + 1] ?? 0 // G -> G
      out[dstIdx + 2] = palette[colorIndex] ?? 0      // R -> B
      out[dstIdx + 3] = palette[colorIndex + 3] ?? 255 // A -> A
    }
  }

  return out
}

function writeRgba(buffer: Uint8Array, width: number, x: number, y: number, r: number, g: number, b: number, a: number): void {
  const offset = (y * width + x) * 4
  buffer[offset] = r
  buffer[offset + 1] = g
  buffer[offset + 2] = b
  buffer[offset + 3] = a
}
