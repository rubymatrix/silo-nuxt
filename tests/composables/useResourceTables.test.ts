import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('vue', () => ({
  ref: <T>(value: T) => ({ value }),
  shallowRef: <T>(value: T) => ({ value }),
}))

const preloadAll = vi.fn(async () => undefined)
const createEquipmentModelTable = vi.fn(async () => ({ marker: 'equipment' }))

vi.mock('~/lib/resource/table/runtime', () => ({
  createResourceTableRuntime: vi.fn(() => ({
    mainDll: { isFullyLoaded: () => true },
    fileTableManager: { isFullyLoaded: () => true },
    itemModelTable: { isFullyLoaded: () => true },
    preloadAll,
    createEquipmentModelTable,
  })),
}))

describe('useResourceTables', () => {
  beforeEach(() => {
    preloadAll.mockClear()
    createEquipmentModelTable.mockClear()
  })

  afterEach(() => {
    vi.resetModules()
  })

  it('preloads tables once and reuses runtime state', async () => {
    const { useResourceTables } = await import('~/composables/useResourceTables')

    const first = useResourceTables()
    const second = useResourceTables()

    await first.preload()
    await second.preload()

    expect(preloadAll).toHaveBeenCalledTimes(1)
    expect(first.isLoaded.value).toBe(true)
    expect(second.isLoaded.value).toBe(true)
  })

  it('exposes equipment table loader', async () => {
    const { useResourceTables } = await import('~/composables/useResourceTables')
    const tables = useResourceTables()

    const equipment = await tables.createEquipmentModelTable()

    expect(createEquipmentModelTable).toHaveBeenCalledTimes(1)
    expect(equipment).toEqual({ marker: 'equipment' })
  })
})
