import { describe, expect, it } from 'vitest'

import { ByteReader } from '~/lib/resource/byteReader'

describe('ByteReader', () => {
  it('reads little-endian integers and floats', () => {
    const bytes = Uint8Array.from([
      0x34,
      0x12,
      0x78,
      0x56,
      0x00,
      0x00,
      0x80,
      0x3f,
    ])
    const reader = new ByteReader(bytes, 'test')

    expect(reader.next16()).toBe(0x1234)
    expect(reader.next16()).toBe(0x5678)
    expect(reader.nextFloat()).toBe(1)
  })

  it('supports wrapped reads and alignment', () => {
    const bytes = Uint8Array.from([1, 2, 3, 4, 5, 6, 7, 8])
    const reader = new ByteReader(bytes)
    reader.position = 1

    const value = reader.wrapped(() => {
      reader.align0x04()
      return reader.next16()
    })

    expect(value).toBe(0x0605)
    expect(reader.position).toBe(1)
  })

  it('reads zero-terminated strings', () => {
    const bytes = Uint8Array.from([0x61, 0x62, 0x63, 0x00, 0x64, 0x00])
    const reader = new ByteReader(bytes)

    expect(reader.nextZeroTerminatedString()).toBe('abc')
    expect(reader.nextZeroTerminatedString()).toBe('d')
  })
})
