import { ByteReader } from '../byteReader'
import type { ItemModelSlot, RaceGenderConfig } from './tableTypes'
import { ItemModelSlot as ItemModelSlots, RaceGenderConfig as RaceGenderConfigs } from './tableTypes'

interface TableEntry {
  readonly tableOffset: number
  readonly entryCount: number
}

interface RaceGenderTable {
  readonly entries: ReadonlyMap<ItemModelSlot, readonly TableEntry[]>
}

interface MainDllLike {
  getEquipmentLookupTable(): ByteReader
}

interface FileTableLike {
  getFilePath(fileId: number | null): string | null
}

export class EquipmentModelTable {
  private readonly tables: ReadonlyMap<RaceGenderConfig, RaceGenderTable>

  constructor(mainDll: MainDllLike, private readonly fileTableManager: FileTableLike) {
    this.tables = this.parse(mainDll.getEquipmentLookupTable())
  }

  getItemModelPath(raceGenderConfig: RaceGenderConfig, itemModelSlot: ItemModelSlot, itemModelId: number): string | null {
    const table = this.tables.get(raceGenderConfig)
    if (!table) {
      return null
    }

    const subTable = table.entries.get(itemModelSlot)
    if (!subTable) {
      return null
    }

    let cumulativeEntryCount = 0

    for (const subTableEntry of subTable) {
      if (itemModelId >= cumulativeEntryCount + subTableEntry.entryCount) {
        cumulativeEntryCount += subTableEntry.entryCount
        continue
      }

      const offset = subTableEntry.tableOffset + (itemModelId - cumulativeEntryCount)
      return this.fileTableManager.getFilePath(offset)
    }

    return null
  }

  getNumEntries(raceGenderConfig: RaceGenderConfig, itemModelSlot: ItemModelSlot): number {
    const table = this.tables.get(raceGenderConfig)
    const subTable = table?.entries.get(itemModelSlot)
    return (subTable ?? []).reduce((sum, entry) => sum + entry.entryCount, 0)
  }

  private parse(byteReader: ByteReader): ReadonlyMap<RaceGenderConfig, RaceGenderTable> {
    const tables = new Map<RaceGenderConfig, RaceGenderTable>()

    for (const config of RaceGenderConfigs.values()) {
      byteReader.position = 0x1b0 * (config.equipmentTableIndex - 1)
      tables.set(config, this.parseRaceGenderTable(byteReader))
    }

    return tables
  }

  private parseRaceGenderTable(byteReader: ByteReader): RaceGenderTable {
    const basePosition = byteReader.position
    const table = new Map<ItemModelSlot, TableEntry[]>()

    for (const slot of ItemModelSlots.values()) {
      byteReader.position = basePosition + 0x30 * (slot.prefix >>> 0x0c)
      const entries: TableEntry[] = []

      for (let i = 0; i < 6; i += 1) {
        const entry: TableEntry = {
          tableOffset: byteReader.next32(),
          entryCount: byteReader.next32(),
        }

        if (entry.tableOffset !== 0) {
          entries.push(entry)
        }
      }

      table.set(slot, entries)
    }

    return { entries: table }
  }
}
