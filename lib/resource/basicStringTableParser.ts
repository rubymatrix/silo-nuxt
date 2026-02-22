import { ByteReader } from './byteReader'

export const BasicStringTableParser = {
  read(byteReader: ByteReader, bitMask: number): string[] {
    byteReader.next32()
    const baseOffset = byteReader.position

    if (bitMask !== 0) {
      while (byteReader.hasMore()) {
        byteReader.xorNext8(bitMask)
      }
    }

    byteReader.position = baseOffset
    const firstEntryOffset = baseOffset + byteReader.next32()

    byteReader.position = baseOffset
    const entries: string[] = []

    while (byteReader.position < firstEntryOffset) {
      const position = byteReader.position
      byteReader.position = baseOffset + byteReader.next32()
      entries.push(byteReader.nextZeroTerminatedString())
      byteReader.position = position + 0x4
    }

    return entries
  },
}
