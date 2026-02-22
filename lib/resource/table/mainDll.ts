import { ByteReader } from '../byteReader'
import type { LoadableResource } from './loadableResource'
import type { RaceGenderConfig } from './tableTypes'

interface DllOffsets {
  readonly battleAnimationFileTableOffset: number
  readonly dualWieldMainHandFileTableOffset: number
  readonly dualWieldOffHandFileTableOffset: number
  readonly battleSkirtAnimationFileTableOffset: number
  readonly battleSkirtDwAnimationFileTableOffset: number
  readonly emoteAnimationOffset: number
  readonly equipmentLookupTableOffset: number
  readonly raceConfigLookupTableOffset: number
  readonly weaponSkillAnimationFileTableOffset: number
  readonly danceSkillAnimationFileTableOffset: number
  readonly actionAnimationFileTableOffset: number
  readonly fishingRodFileTableOffset: number
  readonly zoneDecryptTable1Offset: number
  readonly zoneDecryptTable2Offset: number
  readonly zoneMapTableOffset: number
}

const hints = {
  battleAnimationFileTableOffsetHint: 0xc825c825,
  dualWieldMainHandFileTableOffsetHint: 0x6f9f6f9f,
  dualWieldOffHandFileTableOffsetHint: 0xef9def9d,
  battleSkirtAnimationFileTableOffsetHint: 0x4826c826,
  battleSkirtDwAnimationFileTableOffsetHint: 0xef9f6fa0,
  emoteAnimationOffsetHint: 0x48274827,
  equipmentLookupTableOffsetHint: 0xa81b0000,
  raceConfigLookupTableOffsetHint: 0xa01ba01b,
  weaponSkillAnimationFileTableOffsetHint: 0xcb81cb81,
  danceSkillAnimationFileTableOffsetHint: 0xb9e2b9e2,
  actionAnimationFileTableOffsetHint: 0xcb96cb96,
  fishingRodFileTableOffsetHint: 0x8b998b99,
  zoneDecryptTable1OffsetHint: 0xe2e506a9,
  zoneDecryptTable2OffsetHint: 0xb8c5f784,
  zoneMapTableOffsetHint: 0x6400000100010100n,
} as const

export class MainDll implements LoadableResource {
  private readonly loadDllBytes: () => Promise<Uint8Array>
  private dll: ByteReader | null = null
  private offsets: DllOffsets | null = null

  constructor(loadDllBytes: () => Promise<Uint8Array>) {
    this.loadDllBytes = loadDllBytes
  }

  async preload(): Promise<void> {
    if (this.dll !== null) {
      return
    }

    this.dll = new ByteReader(await this.loadDllBytes(), 'FFXiMain.dll')
    this.offsets = this.loadOffsets()
  }

  isFullyLoaded(): boolean {
    return this.dll !== null
  }

  getZoneDecryptTable1(): Uint8Array {
    return this.getBytes(this.requireOffsets().zoneDecryptTable1Offset, 0x100)
  }

  getZoneDecryptTable2(): Uint8Array {
    return this.getBytes(this.requireOffsets().zoneDecryptTable2Offset, 0x100)
  }

  getZoneMapTableReader(): ByteReader {
    return this.getByteReader(this.requireOffsets().zoneMapTableOffset, 0x2d64)
  }

  getEquipmentLookupTable(): ByteReader {
    return this.getByteReader(this.requireOffsets().equipmentLookupTableOffset, 0x1b0 * 0x10)
  }

  getBaseBattleAnimationIndex(raceGenderConfig: RaceGenderConfig): number {
    const offsets = this.requireOffsets()
    return this.requireDll().read16(offsets.battleAnimationFileTableOffset + raceGenderConfig.index * 2)
  }

  getBaseSkirtAnimationIndex(raceGenderConfig: RaceGenderConfig, dualWield: boolean): number {
    const offsets = this.requireOffsets()
    const offset = dualWield ? offsets.battleSkirtDwAnimationFileTableOffset : offsets.battleSkirtAnimationFileTableOffset
    return this.requireDll().read16(offset + raceGenderConfig.index * 4 + 2)
  }

  getBaseWeaponSkillAnimationIndex(raceGenderConfig: RaceGenderConfig): number {
    const offsets = this.requireOffsets()
    return this.requireDll().read16(offsets.weaponSkillAnimationFileTableOffset + raceGenderConfig.index * 2)
  }

  getBaseRaceConfigIndex(raceGenderConfig: RaceGenderConfig): number {
    const offsets = this.requireOffsets()
    return this.requireDll().read16(offsets.raceConfigLookupTableOffset + raceGenderConfig.index * 2)
  }

  getBaseDanceSkillAnimationIndex(raceGenderConfig: RaceGenderConfig): number {
    const offsets = this.requireOffsets()
    return this.requireDll().read16(offsets.danceSkillAnimationFileTableOffset + raceGenderConfig.index * 2)
  }

  getBaseDualWieldMainHandAnimationIndex(raceGenderConfig: RaceGenderConfig): number {
    const offsets = this.requireOffsets()
    return this.requireDll().read16(offsets.dualWieldMainHandFileTableOffset + raceGenderConfig.index * 2)
  }

  getBaseDualWieldOffHandAnimationIndex(raceGenderConfig: RaceGenderConfig): number {
    const offsets = this.requireOffsets()
    return this.requireDll().read16(offsets.dualWieldOffHandFileTableOffset + raceGenderConfig.index * 2)
  }

  getBaseEmoteAnimationIndex(raceGenderConfig: RaceGenderConfig): number {
    const offsets = this.requireOffsets()
    return this.requireDll().read16(offsets.emoteAnimationOffset + raceGenderConfig.index * 2)
  }

  getActionAnimationIndex(raceGenderConfig: RaceGenderConfig): number {
    const offsets = this.requireOffsets()
    return this.requireDll().read16(offsets.actionAnimationFileTableOffset + raceGenderConfig.index * 2)
  }

  getBaseFishingRodIndex(raceGenderConfig: RaceGenderConfig): number {
    const offsets = this.requireOffsets()
    return this.requireDll().read16(offsets.fishingRodFileTableOffset + raceGenderConfig.index * 2)
  }

  private requireDll(): ByteReader {
    if (this.dll === null) {
      throw new Error('MainDll must be preloaded before use')
    }

    return this.dll
  }

  private requireOffsets(): DllOffsets {
    if (this.offsets === null) {
      throw new Error('MainDll offsets are not loaded')
    }

    return this.offsets
  }

  private getBytes(offset: number, size: number): Uint8Array {
    return this.requireDll().subBuffer(offset, size)
  }

  private getByteReader(offset: number, size: number): ByteReader {
    return new ByteReader(this.getBytes(offset, size), 'FFXiMain.dll')
  }

  private loadOffsets(): DllOffsets {
    return {
      battleAnimationFileTableOffset: this.findOffset32(hints.battleAnimationFileTableOffsetHint),
      dualWieldMainHandFileTableOffset: this.findOffset32(hints.dualWieldMainHandFileTableOffsetHint),
      dualWieldOffHandFileTableOffset: this.findOffset32(hints.dualWieldOffHandFileTableOffsetHint),
      battleSkirtAnimationFileTableOffset: this.findOffset32(hints.battleSkirtAnimationFileTableOffsetHint),
      battleSkirtDwAnimationFileTableOffset: this.findOffset32(hints.battleSkirtDwAnimationFileTableOffsetHint),
      emoteAnimationOffset: this.findOffset32(hints.emoteAnimationOffsetHint),
      equipmentLookupTableOffset: this.findOffset32(hints.equipmentLookupTableOffsetHint),
      raceConfigLookupTableOffset: this.findOffset32(hints.raceConfigLookupTableOffsetHint),
      weaponSkillAnimationFileTableOffset: this.findOffset32(hints.weaponSkillAnimationFileTableOffsetHint),
      danceSkillAnimationFileTableOffset: this.findOffset32(hints.danceSkillAnimationFileTableOffsetHint),
      actionAnimationFileTableOffset: this.findOffset32(hints.actionAnimationFileTableOffsetHint),
      fishingRodFileTableOffset: this.findOffset32(hints.fishingRodFileTableOffsetHint),
      zoneDecryptTable1Offset: this.findOffset32(hints.zoneDecryptTable1OffsetHint),
      zoneDecryptTable2Offset: this.findOffset32(hints.zoneDecryptTable2OffsetHint),
      zoneMapTableOffset: this.findOffset64(hints.zoneMapTableOffsetHint),
    }
  }

  private findOffset32(hint: number): number {
    const dll = this.requireDll()
    dll.position = 0x30000

    for (let i = 0; i < 0xc000; i += 1) {
      if (dll.next32BE() === hint) {
        return dll.position - 0x04
      }
    }

    throw new Error(`Failed to find offset for ${hint.toString(16)}`)
  }

  private findOffset64(hint: bigint): number {
    const dll = this.requireDll()
    dll.position = 0x30000

    for (let i = 0; i < 0x6000; i += 1) {
      if (dll.next64BE() === hint) {
        return dll.position - 0x08
      }
    }

    throw new Error(`Failed to find offset for ${hint.toString(16)}`)
  }
}
