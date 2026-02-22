import { DatLoader, type DatLoaderOptions } from '~/lib/loader/datLoader'
import { EquipmentModelTable } from './equipmentModelTable'
import { FileTableManager } from './fTable'
import { ItemModelTable } from './itemModelTable'
import { MainDll } from './mainDll'

interface ResourceTableRuntimeOptions {
  readonly baseUrl: string
  readonly fetchImpl?: DatLoaderOptions<Uint8Array>['fetchImpl']
  readonly concurrency?: number
  readonly fileTableCount?: number
}

export interface ResourceTableRuntime {
  readonly bytesLoader: DatLoader<Uint8Array>
  readonly mainDll: MainDll
  readonly fileTableManager: FileTableManager
  readonly itemModelTable: ItemModelTable
  preloadAll(): Promise<void>
  createEquipmentModelTable(): Promise<EquipmentModelTable>
}

export function createResourceTableRuntime(options: ResourceTableRuntimeOptions): ResourceTableRuntime {
  const bytesLoader = new DatLoader<Uint8Array>({
    baseUrl: options.baseUrl,
    concurrency: options.concurrency,
    fetchImpl: options.fetchImpl,
    parseDat: (_resourceName, bytes) => bytes,
  })

  const loadWithFallback = (path: string) => loadWithTableCaseFallback(bytesLoader, path)

  const mainDll = new MainDll(() => loadWithFallback('FFXiMain.dll'))
  const fileTableManager = new FileTableManager(loadWithFallback, options.fileTableCount ?? 9)
  const itemModelTable = new ItemModelTable(() => loadWithFallback('landsandboat/ItemModelTable.DAT'))

  return {
    bytesLoader,
    mainDll,
    fileTableManager,
    itemModelTable,
    async preloadAll(): Promise<void> {
      await Promise.all([mainDll.preload(), fileTableManager.preload(), itemModelTable.preload()])
    },
    async createEquipmentModelTable(): Promise<EquipmentModelTable> {
      await Promise.all([mainDll.preload(), fileTableManager.preload()])
      return new EquipmentModelTable(mainDll, fileTableManager)
    },
  }
}

async function loadWithTableCaseFallback(loader: DatLoader<Uint8Array>, path: string): Promise<Uint8Array> {
  try {
    return await loader.load(path)
  } catch (error) {
    const fallback = toLegacyTableCasing(path)
    if (fallback === path) {
      throw error
    }

    return loader.load(fallback)
  }
}

function toLegacyTableCasing(path: string): string {
  if (path.includes('VTABLE')) {
    return path.replace('VTABLE', 'VTable')
  }

  if (path.includes('FTABLE')) {
    return path.replace('FTABLE', 'FTable')
  }

  return path
}
