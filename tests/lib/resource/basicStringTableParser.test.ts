import { describe, expect, it } from 'vitest'

import { BasicStringTableParser } from '~/lib/resource/basicStringTableParser'
import { ByteReader } from '~/lib/resource/byteReader'

describe('BasicStringTableParser', () => {
  it('reads offset-indexed string tables', () => {
    const bytes = Uint8Array.from([
      0x10,
      0x00,
      0x00,
      0x00,
      0x08,
      0x00,
      0x00,
      0x00,
      0x0c,
      0x00,
      0x00,
      0x00,
      0x61,
      0x62,
      0x63,
      0x00,
      0x64,
      0x65,
      0x00,
    ])

    const entries = BasicStringTableParser.read(new ByteReader(bytes), 0x00)
    expect(entries).toEqual(['abc', 'de'])
  })

  it('supports xor-obfuscated string tables', () => {
    const source = Uint8Array.from([
      0x10,
      0x00,
      0x00,
      0x00,
      0x04,
      0x00,
      0x00,
      0x00,
      0x78,
      0x79,
      0x00,
    ])
    const bytes = source.slice()
    const mask = 0x5a
    for (let i = 4; i < bytes.length; i += 1) {
      bytes[i] ^= mask
    }

    const entries = BasicStringTableParser.read(new ByteReader(bytes), mask)
    expect(entries).toEqual(['xy'])
  })
})
