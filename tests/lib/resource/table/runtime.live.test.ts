import { describe, expect, it } from 'vitest'

import { createResourceTableRuntime } from '~/lib/resource/table/runtime'
import { ItemModelSlot, RaceGenderConfig } from '~/lib/resource/table/tableTypes'

const liveEnabled =
  (globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env?.LIVE_DAT_TESTS === '1'

describe('resource table runtime (live server)', () => {
  it.skipIf(!liveEnabled)(
    'loads table resources from localhost:3005',
    async () => {
      const runtime = createResourceTableRuntime({
        baseUrl: 'http://localhost:3005',
        fileTableCount: 1,
      })

      await runtime.preloadAll()
      expect(runtime.mainDll.isFullyLoaded()).toBe(true)
      expect(runtime.fileTableManager.isFullyLoaded()).toBe(true)
      expect(runtime.itemModelTable.isFullyLoaded()).toBe(true)

      const equipmentModelTable = await runtime.createEquipmentModelTable()
      const count = equipmentModelTable.getNumEntries(RaceGenderConfig.HumeMale, ItemModelSlot.Body)

      expect(count).toBeGreaterThanOrEqual(0)
    },
    30_000,
  )
})
