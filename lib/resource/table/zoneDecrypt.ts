/**
 * Zone mesh and zone object decryption.
 *
 * Ported from xim/resource/table/ZoneDecrypt.kt.
 * Uses two 256-byte key tables extracted from FFXiMain.dll.
 */

import type { ByteReader, SectionOffsetSource } from '../byteReader'
import type { MainDll } from './mainDll'

let keyTable1: Uint8Array | null = null
let keyTable2: Uint8Array | null = null

export function initZoneDecrypt(mainDll: MainDll): void {
  keyTable1 = mainDll.getZoneDecryptTable1()
  keyTable2 = mainDll.getZoneDecryptTable2()
}

function requireKeyTable1(): Uint8Array {
  if (!keyTable1) throw new Error('ZoneDecrypt not initialized. Call initZoneDecrypt() first.')
  return keyTable1
}

function requireKeyTable2(): Uint8Array {
  if (!keyTable2) throw new Error('ZoneDecrypt not initialized. Call initZoneDecrypt() first.')
  return keyTable2
}

/**
 * Decrypt zone object data (section 0x1C).
 * XOR-based decryption with variable-length chunks, then unmask object names.
 */
export function decryptZoneObjects(sectionHeader: SectionOffsetSource, byteReader: ByteReader): void {
  const kt1 = requireKeyTable1()

  byteReader.offsetFrom(sectionHeader, 0x10)

  const metadata = byteReader.next32()
  let decodeLength = metadata & 0x00ffffff
  const mode = (metadata >>> 24) & 0xff

  if (mode <= 0x1a) {
    return
  }

  const keyNodeData = byteReader.next32()
  const nodeCount = keyNodeData & 0x00ffffff
  const keyIndex = (keyNodeData >>> 24) & 0xff

  let key = kt1[keyIndex ^ 0xff]!
  let keyCounter = 0
  let consumed = 0

  const sectionEnd = sectionHeader.sectionStartPosition + sectionHeader.sectionSize

  if (byteReader.position + decodeLength > sectionEnd) {
    const extra = (byteReader.position + decodeLength) - sectionEnd
    console.warn(`[${sectionHeader}] Decode length too long by: 0x${extra.toString(16)}`)
    decodeLength -= extra
  }

  while (consumed < decodeLength) {
    const xorLength = ((key >>> 4) & 7) + 16

    const shouldApplyMask = (key & 1) !== 0
    const hasRemainingLength = consumed + xorLength < decodeLength

    if (shouldApplyMask && hasRemainingLength) {
      for (let i = 0; i < xorLength; i++) {
        byteReader.xorNext(0xff)
      }
    } else {
      byteReader.position += xorLength
    }

    keyCounter += 1
    // Kotlin uses unbounded 32-bit int addition -- no masking
    key += keyCounter
    consumed += xorLength
  }

  // Names are masked too
  byteReader.offsetFrom(sectionHeader, 0x30)
  const pos = byteReader.position

  for (let i = 0; i < nodeCount; i++) {
    byteReader.position = pos + i * 0x64
    for (let j = 0; j < 0x10; j++) {
      byteReader.xorNext(0x55)
    }
  }
}

/**
 * Decrypt zone mesh data (section 0x2E).
 * Two-pass: XOR cipher + conditional byte-swap.
 */
export function decryptZoneMesh(sectionHeader: SectionOffsetSource, byteReader: ByteReader): void {
  decryptZoneMeshFirstPass(sectionHeader, byteReader)
  decryptZoneMeshSecondPass(sectionHeader, byteReader)
  byteReader.offsetFrom(sectionHeader, 0x10)
}

function decryptZoneMeshFirstPass(sectionHeader: SectionOffsetSource, byteReader: ByteReader): void {
  const kt1 = requireKeyTable1()

  byteReader.offsetFrom(sectionHeader, 0x10)

  const metadata = byteReader.next32()
  const totalSize = metadata & 0x00ffffff
  const decodeLength = totalSize - 0x8

  const mode = (metadata >>> 24) & 0xff
  if (mode < 0x5) {
    return
  }

  byteReader.next8() // not decrypt related
  const keyIndex = byteReader.next8()
  byteReader.next16() // unused in first pass

  // Kotlin uses unbounded 32-bit int -- no masking on key progression.
  // JavaScript numbers are 64-bit floats but safe for integers up to 2^53.
  let key = kt1[keyIndex ^ 0xf0]!
  let keyCounter = 0

  for (let i = 0; i < decodeLength; i++) {
    const keyMod = ((key & 0xff) << 8) | (key & 0xff)
    key += ++keyCounter

    const mask = (keyMod >>> (key & 0x7)) & 0xff
    byteReader.xorNext(mask)
    key += ++keyCounter
  }
}

function decryptZoneMeshSecondPass(sectionHeader: SectionOffsetSource, byteReader: ByteReader): void {
  const kt2 = requireKeyTable2()

  byteReader.offsetFrom(sectionHeader, 0x10)

  const metadata = byteReader.next32()
  const totalSize = metadata & 0x00ffffff
  const decodeLength = totalSize - 0x8

  byteReader.next8() // not decrypt related
  let key1 = byteReader.next8() ^ 0xf0
  let key2 = kt2[key1 & 0xff]!

  const decodeCount = (decodeLength & ~0xf) / 2

  const needsSecondPass = byteReader.next16() === 0xffff
  if (!needsSecondPass) {
    return
  }

  // Swap
  for (let i = 0; i < decodeCount; i += 8) {
    if ((key2 & 1) === 1) {
      byteReader.swapNext8(decodeCount, 8)
    } else {
      byteReader.position += 0x8
    }

    // Kotlin uses unbounded 32-bit int -- no masking
    key1 += 9
    key2 += key1
  }
}
