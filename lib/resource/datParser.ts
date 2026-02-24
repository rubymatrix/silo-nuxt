import { ByteReader } from './byteReader'
import {
  DatId,
  DatResource,
  DirectoryResource,
  type DatEntry,
  sectionTypeFromCode,
  SectionTypes,
  type SectionTypeDef,
} from './datResource'
import { EnvironmentSection } from './environmentSection'
import { InfoSection } from './infoSection'
import { SkeletonAnimationSection } from './skeletonAnimationSection'
import { SkeletonMeshSection } from './skeletonMeshSection'
import { SkeletonSection } from './skeletonSection'
import { TextureSection } from './textureSection'
import { WeightedMeshSection } from './weightedMeshSection'
import { ZoneDefSection } from './zoneDefSection'
import { ZoneMeshSection } from './zoneMeshSection'

export class ParserEntry {
  readonly datEntry: DatEntry
  readonly resourceName: string | null

  constructor(datEntry: DatEntry, resourceName: string | null = null) {
    this.datEntry = datEntry
    this.resourceName = resourceName
  }
}

export class ParserResult {
  readonly entry: ParserEntry | null
  readonly popDirectory: boolean
  readonly pushDirectory: boolean

  constructor(entry: ParserEntry | null, popDirectory = false, pushDirectory = false) {
    this.entry = entry
    this.popDirectory = popDirectory
    this.pushDirectory = pushDirectory
  }

  static none(): ParserResult {
    return new ParserResult(null)
  }

  static from(datEntry: DatEntry): ParserResult {
    return new ParserResult(new ParserEntry(datEntry))
  }
}

export interface ResourceParser {
  getResource(byteReader: ByteReader): ParserResult
}

export class SectionHeader {
  sectionId: DatId = DatId.zero
  sectionType: SectionTypeDef = SectionTypes.S00_End
  localDir!: DirectoryResource

  sectionStartPosition = 0
  dataStartPosition = 0
  sectionSize = 0

  get sectionEndPosition(): number {
    return this.sectionStartPosition + this.sectionSize
  }

  read(byteReader: ByteReader): void {
    this.sectionStartPosition = byteReader.position
    this.dataStartPosition = this.sectionStartPosition + 0x10
    this.sectionId = new DatId(byteReader.nextString(0x4))

    const sectionMeta = byteReader.next32()
    this.sectionType = sectionTypeFromCode(sectionMeta & 0x7f)
    this.sectionSize = ((sectionMeta >>> 7) & 0xfffff) * 0x10
    byteReader.align0x10()
  }

  nextSectionOffset(): number {
    return this.sectionStartPosition + this.sectionSize
  }

  toString(): string {
    return this.sectionId.toString()
  }
}

class UnhandledSection implements ResourceParser {
  getResource(): ParserResult {
    return ParserResult.none()
  }
}

class DirectorySection implements ResourceParser {
  private readonly sectionHeader: SectionHeader
  private readonly parent: DirectoryResource | null

  constructor(sectionHeader: SectionHeader, parent: DirectoryResource | null) {
    this.sectionHeader = sectionHeader
    this.parent = parent
  }

  getResource(): ParserResult {
    if (this.parent !== null && this.parent.hasSubDirectory(this.sectionHeader.sectionId)) {
      return new ParserResult(new ParserEntry(this.parent.getSubDirectory(this.sectionHeader.sectionId)), false, true)
    }

    return new ParserResult(new ParserEntry(new DirectoryResource(this.parent, this.sectionHeader.sectionId)), false, true)
  }
}

class EndSection implements ResourceParser {
  getResource(): ParserResult {
    return new ParserResult(null, true, false)
  }
}

export interface DatParserOptions {
  readonly zoneResource?: boolean
}

export const DatParser = {
  parse(resourceName: string, rawDat: Uint8Array, options?: DatParserOptions): DirectoryResource {
    let rootDirectory: DirectoryResource | null = null
    let currentDirectory: DirectoryResource | null = null
    const byteReader = new ByteReader(rawDat, resourceName)

    const parserContext = {
      zoneResource: options?.zoneResource ?? false,
    }

    while (byteReader.hasMore()) {
      const header = new SectionHeader()
      header.read(byteReader)
      if (currentDirectory !== null) {
        header.localDir = currentDirectory
      }

      const parser: ResourceParser = (() => {
        if (header.sectionType.code === SectionTypes.S00_End.code) {
          return new EndSection()
        }
        if (header.sectionType.code === SectionTypes.S01_Directory.code) {
          return new DirectorySection(header, currentDirectory)
        }
        if (header.sectionType.code === SectionTypes.S45_Info.code) {
          return new InfoSection(header)
        }
        if (header.sectionType.code === SectionTypes.S20_Texture.code) {
          return new TextureSection(header)
        }
        if (header.sectionType.code === SectionTypes.S29_Skeleton.code) {
          return new SkeletonSection(header)
        }
        if (header.sectionType.code === SectionTypes.S2A_SkeletonMesh.code) {
          return new SkeletonMeshSection(header)
        }
        if (header.sectionType.code === SectionTypes.S2B_SkeletonAnimation.code) {
          return new SkeletonAnimationSection(header)
        }
        if (header.sectionType.code === SectionTypes.S25_WeightedMesh.code) {
          return new WeightedMeshSection(header, parserContext)
        }
        if (header.sectionType.code === SectionTypes.S1C_ZoneDef.code) {
          return new ZoneDefSection(header)
        }
        if (header.sectionType.code === SectionTypes.S2E_ZoneMesh.code) {
          return new ZoneMeshSection(header)
        }
        if (header.sectionType.code === SectionTypes.S2F_Environment.code) {
          return new EnvironmentSection(header)
        }

        return new UnhandledSection()
      })()

      const result = parser.getResource(byteReader)
      byteReader.offsetFrom(header, header.sectionSize)

      if (result.popDirectory) {
        if (currentDirectory === null) {
          throw new Error(`[${resourceName}] Unexpected directory pop at ${byteReader}`)
        }
        currentDirectory = currentDirectory.parent
        continue
      }

      const entry = result.entry
      if (entry === null) {
        continue
      }

      if (result.pushDirectory) {
        if (entry.datEntry instanceof DirectoryResource) {
          currentDirectory?.addChild(header, entry.datEntry)
          currentDirectory = entry.datEntry
          if (rootDirectory === null) {
            rootDirectory = currentDirectory
          }
          continue
        }

        throw new Error(`[${resourceName}] pushDirectory expected a DirectoryResource`)
      }

      if (currentDirectory === null) {
        throw new Error(`[${resourceName}] Resource emitted before root directory: ${header.sectionId}`)
      }

      currentDirectory.addChild(header, entry.datEntry)
      if (entry.datEntry instanceof DatResource) {
        entry.datEntry.localDir = currentDirectory
      }
    }

    if (rootDirectory === null) {
      throw new Error(`[${resourceName}] No root directory found`) 
    }

    return rootDirectory
  },
}

export function oops(byteReader: ByteReader, reason = ''): never {
  throw new Error(`${byteReader} | ${reason}`)
}

export function expect32(byteReader: ByteReader, predicate: (value: number) => boolean): void {
  const value = byteReader.next32()
  if (!predicate(value)) {
    oops(byteReader, `Predicate failed, was ${value.toString(16)}`)
  }
}

export function expectZero32(byteReader: ByteReader): void {
  if (byteReader.next32() !== 0) {
    oops(byteReader, 'Wanted 0')
  }
}
