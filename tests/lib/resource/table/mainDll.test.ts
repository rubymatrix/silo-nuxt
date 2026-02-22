import { describe, expect, it } from 'vitest'

import { MainDll } from '~/lib/resource/table/mainDll'
import { RaceGenderConfig } from '~/lib/resource/table/tableTypes'

describe('MainDll', () => {
  it('finds hint offsets and reads lookup indices', async () => {
    const bytes = new Uint8Array(0x40000)
    const view = new DataView(bytes.buffer)

    view.setUint32(0x30020, 0xc825c825, false)
    view.setUint32(0x30030, 0x6f9f6f9f, false)
    view.setUint32(0x30040, 0xef9def9d, false)
    view.setUint32(0x30050, 0x4826c826, false)
    view.setUint32(0x30060, 0xef9f6fa0, false)
    view.setUint32(0x30070, 0x48274827, false)
    view.setUint32(0x30080, 0xa81b0000, false)
    view.setUint32(0x30090, 0xa01ba01b, false)
    view.setUint32(0x300a0, 0xcb81cb81, false)
    view.setUint32(0x300b0, 0xb9e2b9e2, false)
    view.setUint32(0x300c0, 0xcb96cb96, false)
    view.setUint32(0x300d0, 0x8b998b99, false)
    view.setUint32(0x300e0, 0xe2e506a9, false)
    view.setUint32(0x300f0, 0xb8c5f784, false)
    view.setBigUint64(0x30100, 0x6400000100010100n, false)

    view.setUint16(0x30020 + RaceGenderConfig.ElvaanMale.index * 2, 77, true)
    bytes[0x300e0 + 0x30] = 0xab

    const dll = new MainDll(async () => bytes)
    await dll.preload()

    expect(dll.isFullyLoaded()).toBe(true)
    expect(dll.getBaseBattleAnimationIndex(RaceGenderConfig.ElvaanMale)).toBe(77)
    expect(dll.getZoneDecryptTable1().at(0x30)).toBe(0xab)
  })
})
