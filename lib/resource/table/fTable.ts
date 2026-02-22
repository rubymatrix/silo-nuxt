import { ByteReader } from '../byteReader'
import type { LoadableResource } from './loadableResource'

export class VTable {
  readonly byteReader: ByteReader

  constructor(byteReader: ByteReader) {
    this.byteReader = byteReader
  }

  getVersion(fileId: number): number | null {
    if (fileId < 0 || fileId >= this.byteReader.bytes.length) {
      return null
    }

    return this.byteReader.bytes[fileId] ?? null
  }
}

export interface FileTuple {
  readonly folderName: number
  readonly fileName: number
}

export class FTable {
  readonly byteReader: ByteReader

  constructor(byteReader: ByteReader) {
    this.byteReader = byteReader
  }

  getFile(fileId: number): FileTuple {
    this.byteReader.position = 2 * fileId
    const fileValue = this.byteReader.next16()

    return {
      fileName: fileValue & 0x7f,
      folderName: fileValue >>> 7,
    }
  }
}

export class FileTableManager implements LoadableResource {
  private readonly numTables: number
  private readonly loadFile: (path: string) => Promise<Uint8Array>

  private vTable: VTable | null = null
  private loadedVTables = 0

  private fTable: FTable | null = null
  private loadedFTables = 0

  private preloaded = false

  constructor(loadFile: (path: string) => Promise<Uint8Array>, numTables = 9) {
    this.loadFile = loadFile
    this.numTables = numTables
  }

  async preload(): Promise<void> {
    if (this.preloaded) {
      return
    }
    this.preloaded = true

    await Promise.all(Array.from({ length: this.numTables }, (_, i) => this.loadTable(i + 1)))
  }

  isFullyLoaded(): boolean {
    return this.loadedVTables === this.numTables && this.loadedFTables === this.numTables
  }

  getFilePath(fileId: number | null): string | null {
    if (fileId === null || this.vTable === null || this.fTable === null) {
      return null
    }

    const versionNumber = this.vTable.getVersion(fileId)
    if (versionNumber === null || versionNumber === 0) {
      return null
    }

    const versionString = versionNumber === 1 ? '' : String(versionNumber)
    const { folderName, fileName } = this.fTable.getFile(fileId)
    return `ROM${versionString}/${folderName}/${fileName}.DAT`
  }

  private async loadTable(tableIndex: number): Promise<void> {
    const prefix = tableIndex === 1 ? '' : `ROM${tableIndex}/`
    const postfix = tableIndex === 1 ? '' : String(tableIndex)

    const [vTableBytes, fTableBytes] = await Promise.all([
      this.loadFile(`${prefix}VTABLE${postfix}.DAT`),
      this.loadFile(`${prefix}FTABLE${postfix}.DAT`),
    ])

    this.onVTableLoad(new ByteReader(vTableBytes))
    this.onFTableLoad(new ByteReader(fTableBytes))
  }

  private onVTableLoad(byteReader: ByteReader): void {
    if (this.vTable === null) {
      this.vTable = new VTable(byteReader)
    } else {
      this.combine(byteReader, this.vTable.byteReader)
    }

    this.loadedVTables += 1
  }

  private onFTableLoad(byteReader: ByteReader): void {
    if (this.fTable === null) {
      this.fTable = new FTable(byteReader)
    } else {
      this.combine(byteReader, this.fTable.byteReader)
    }

    this.loadedFTables += 1
  }

  private combine(source: ByteReader, destination: ByteReader): void {
    const max = Math.min(source.bytes.length, destination.bytes.length)
    for (let i = 0; i < max; i += 1) {
      destination.bytes[i] = (destination.bytes[i] ?? 0) | (source.bytes[i] ?? 0)
    }
  }
}
