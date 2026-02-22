import { ParserEntry, ParserResult, type ResourceParser, type SectionHeader } from './datParser'
import { DatId, TextureResource } from './datResource'

const SUPPORTED_TEXTURE_TYPES = new Set([0x91, 0xa1, 0xb1])

export class TextureSection implements ResourceParser {
  private readonly sectionHeader: SectionHeader

  constructor(sectionHeader: SectionHeader) {
    this.sectionHeader = sectionHeader
  }

  getResource(byteReader: import('./byteReader').ByteReader): ParserResult {
    byteReader.offsetFromDataStart(this.sectionHeader, 0)
    const result = TextureSection.read(byteReader, this.sectionHeader.sectionId)
    if (!result) {
      return ParserResult.none()
    }

    return new ParserResult(new ParserEntry(result, result.name))
  }

  static read(byteReader: import('./byteReader').ByteReader, datId: DatId = DatId.zero): TextureResource | null {
    const type = byteReader.next8()
    if (!SUPPORTED_TEXTURE_TYPES.has(type)) {
      return null
    }

    const name = byteReader.nextString(0x10)
    byteReader.next32()
    const width = byteReader.next32()
    const height = byteReader.next32()
    byteReader.next16()
    const bitCount = byteReader.next16()

    TextureSection.expectZero(byteReader.next32(), datId)
    TextureSection.expectZero(byteReader.next32(), datId)
    TextureSection.expectZero(byteReader.next32(), datId)
    TextureSection.expectZero(byteReader.next32(), datId)
    TextureSection.expectZero(byteReader.next32(), datId)
    byteReader.next32()

    let dxtType: 'DXT1' | 'DXT3' | null = null
    if (type === 0xa1) {
      const tag = byteReader.nextString(4)
      dxtType = tag === '1TXD' ? 'DXT1' : tag === '3TXD' ? 'DXT3' : null
      if (dxtType === null) {
        throw new Error(`[${datId}] Don't know texture-type ${tag}`)
      }
      byteReader.next32()
      byteReader.next32()
    } else if (type === 0xb1) {
      byteReader.next32()
    }

    const compressedSize = dxtType === 'DXT1'
      ? (width * height) / 2
      : dxtType === 'DXT3'
        ? width * height
        : 0
    const uncompressedSize = bitCount === 32
      ? width * height * 4
      : 256 * 4 + width * height
    const remaining = byteReader.bytes.length - byteReader.position
    const readSize = Math.max(0, Math.min(remaining, dxtType ? compressedSize : uncompressedSize))
    const rawData = byteReader.subBuffer(readSize)
    byteReader.position += readSize

    return new TextureResource(datId, name, {
      textureType: type,
      width,
      height,
      bitCount,
      dxtType,
      rawData,
    })
  }

  private static expectZero(value: number, datId: DatId): void {
    if (value !== 0) {
      throw new Error(`[${datId}] Wanted 0, was ${value.toString(16)}`)
    }
  }
}
