import { ByteReader } from './byteReader'
import {
  DatId,
  InfoResource,
  type InfoDefinition,
  type MountDefinition,
  movementTypeFromIndex,
  rangeTypeFromIndex,
  RangeType,
} from './datResource'
import { ParserResult, type ResourceParser, type SectionHeader } from './datParser'

const TWO_PI = Math.PI * 2

export class InfoSection implements ResourceParser {
  private readonly sectionHeader: SectionHeader
  private infoDefinition: InfoDefinition = {
    movementType: movementTypeFromIndex(0xff),
    movementChar: '0',
    shakeFactor: 0,
    weaponAnimationType: 0,
    weaponAnimationSubType: 0,
    standardJointIndex: null,
    scale: null,
    staticNpcScale: null,
    rangeType: RangeType.Unset,
  }

  private mountDefinition: MountDefinition = {
    rotation: 0,
    poseType: 0,
  }

  constructor(sectionHeader: SectionHeader) {
    this.sectionHeader = sectionHeader
  }

  getResource(byteReader: ByteReader): ParserResult {
    this.read(byteReader)
    return ParserResult.from(new InfoResource(this.sectionHeader.sectionId, this.infoDefinition, this.mountDefinition))
  }

  private read(byteReader: ByteReader): void {
    if (this.sectionHeader.sectionId.id === DatId.mount.id) {
      this.readMountDefinition(byteReader)
      return
    }

    this.readInfoDefinition(byteReader)
  }

  private readInfoDefinition(byteReader: ByteReader): void {
    byteReader.offsetFromDataStart(this.sectionHeader, 0)

    const movementType = movementTypeFromIndex(byteReader.next8())
    const movementChar = this.toMovementChar(byteReader.next8())
    const shakeFactor = byteReader.next8()

    const weaponAnimationType = byteReader.next8()
    const weaponAnimationSubType = byteReader.next8()
    byteReader.next8()
    const standardJointIndex = this.nullIf0xff(byteReader.next8())

    byteReader.next8()
    byteReader.next8()
    byteReader.next8()
    const scale = this.nullIf0xff(byteReader.next8())
    const staticNpcScale = this.nullIf0xff(byteReader.next8())
    byteReader.next8()
    byteReader.next8()

    const rawRangeType = byteReader.next8()
    byteReader.next8()

    this.infoDefinition = {
      movementType,
      movementChar,
      shakeFactor,
      weaponAnimationType,
      weaponAnimationSubType,
      standardJointIndex,
      scale,
      staticNpcScale,
      rangeType: rangeTypeFromIndex(rawRangeType) ?? RangeType.Unset,
    }
  }

  private readMountDefinition(byteReader: ByteReader): void {
    byteReader.offsetFromDataStart(this.sectionHeader, 0)
    byteReader.next8()
    byteReader.next8()
    const rotation = byteReader.next8()
    byteReader.next8()
    byteReader.next8()
    byteReader.next8()
    byteReader.next8()
    byteReader.next8()
    byteReader.next8()
    byteReader.next8()
    const poseType = byteReader.next8()
    byteReader.next8()
    byteReader.next8()
    byteReader.next8()
    byteReader.next8()
    byteReader.next8()

    this.mountDefinition = {
      rotation: TWO_PI * (rotation / 255),
      poseType,
    }
  }

  private toMovementChar(value: number): string {
    return value === 0xff ? '0' : DatId.base36ToChar(value)
  }

  private nullIf0xff(value: number): number | null {
    return value === 0xff ? null : value
  }
}
