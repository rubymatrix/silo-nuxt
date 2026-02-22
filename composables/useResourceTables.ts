import { ref, shallowRef } from 'vue'

import { createResourceTableRuntime, type ResourceTableRuntime } from '~/lib/resource/table/runtime'
import type { EquipmentModelTable } from '~/lib/resource/table/equipmentModelTable'

interface UseResourceTablesOptions {
  readonly baseUrl?: string
  readonly concurrency?: number
  readonly fileTableCount?: number
}

const runtimeRef = shallowRef<ResourceTableRuntime | null>(null)
const preloadPromiseRef = shallowRef<Promise<ResourceTableRuntime> | null>(null)

const isLoading = ref(false)
const isLoaded = ref(false)
const error = shallowRef<Error | null>(null)

function ensureRuntime(options?: UseResourceTablesOptions): ResourceTableRuntime {
  if (runtimeRef.value === null) {
    runtimeRef.value = createResourceTableRuntime({
      baseUrl: options?.baseUrl,
      concurrency: options?.concurrency,
      fileTableCount: options?.fileTableCount,
    })
  }

  return runtimeRef.value
}

async function preload(options?: UseResourceTablesOptions): Promise<ResourceTableRuntime> {
  const runtime = ensureRuntime(options)

  if (isLoaded.value) {
    return runtime
  }

  if (preloadPromiseRef.value !== null) {
    return preloadPromiseRef.value
  }

  isLoading.value = true
  error.value = null

  preloadPromiseRef.value = (async () => {
    try {
      await runtime.preloadAll()
      isLoaded.value = true
      return runtime
    } catch (err) {
      error.value = err instanceof Error ? err : new Error(String(err))
      throw err
    } finally {
      isLoading.value = false
      preloadPromiseRef.value = null
    }
  })()

  return preloadPromiseRef.value
}

async function createEquipmentModelTable(options?: UseResourceTablesOptions): Promise<EquipmentModelTable> {
  const runtime = await preload(options)
  return runtime.createEquipmentModelTable()
}

export function useResourceTables(options?: UseResourceTablesOptions) {
  ensureRuntime(options)

  return {
    runtime: runtimeRef,
    isLoading,
    isLoaded,
    error,
    preload: () => preload(options),
    createEquipmentModelTable: () => createEquipmentModelTable(options),
  }
}
