import { describe, expect, it } from 'vitest'

import { ByteReader } from '~/lib/resource/byteReader'
import { EquipmentModelTable } from '~/lib/resource/table/equipmentModelTable'
import { FTable, FileTableManager, VTable } from '~/lib/resource/table/fTable'
import { ItemModelTable } from '~/lib/resource/table/itemModelTable'
import { ItemModelSlot, RaceGenderConfig } from '~/lib/resource/table/tableTypes'

describe('VTable and FTable', () => {
  it('decodes version and file tuples', () => {
    const vTable = new VTable(new ByteReader(Uint8Array.from([0, 0, 0, 0, 0, 2])))
    const fBytes = new Uint8Array(12)
    fBytes[10] = 0x89
    fBytes[11] = 0x03
    const fTable = new FTable(new ByteReader(fBytes))

    expect(vTable.getVersion(5)).toBe(2)
    expect(fTable.getFile(5)).toEqual({ folderName: 7, fileName: 9 })
  })

  it('builds ROM path from merged tables', async () => {
    const tableBytes = new Uint8Array(16)
    tableBytes[3] = 2
    const fBytes = new Uint8Array(16)
    fBytes[6] = 0x81
    fBytes[7] = 0x02

    const manager = new FileTableManager(async (path) => {
      if (path.includes('VTABLE')) {
        return tableBytes.slice()
      }
      return fBytes.slice()
    }, 1)

    await manager.preload()
    expect(manager.getFilePath(3)).toBe('ROM2/5/1.DAT')
  })
})

describe('ItemModelTable', () => {
  it('returns model IDs from table bytes and additional mappings', async () => {
    const bytes = new Uint8Array(64)
    bytes[20] = 0x2a
    const table = new ItemModelTable(async () => bytes)

    await table.preload()

    expect(table.getModelIdFromItemId(10)).toBe(42)
    expect(table.getModelIdFromItemId(23871)).toBe(495)
    expect(table.getForcedMatches(ItemModelSlot.Body, 495)).toEqual(new Set([ItemModelSlot.Hands]))
  })
})

describe('EquipmentModelTable', () => {
  it('resolves model path via lookup and file table', () => {
    const bytes = new Uint8Array(0x1b0)
    const view = new DataView(bytes.buffer)
    view.setUint32(0x00, 100, true)
    view.setUint32(0x04, 2, true)

    const table = new EquipmentModelTable(
      {
        getEquipmentLookupTable: () => new ByteReader(bytes),
      },
      {
        getFilePath: (fileId) => `ROM/0/${fileId}.DAT`,
      },
    )

    expect(table.getItemModelPath(RaceGenderConfig.HumeMale, ItemModelSlot.Face, 1)).toBe('ROM/0/101.DAT')
    expect(table.getNumEntries(RaceGenderConfig.HumeMale, ItemModelSlot.Face)).toBe(2)
  })
})
