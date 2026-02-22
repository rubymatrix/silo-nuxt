import { ByteReader } from '../byteReader'
import type { LoadableResource } from './loadableResource'
import type { InventoryItemInfo, ItemModelSlot } from './tableTypes'
import { ItemModelSlot as ItemModelSlots } from './tableTypes'

const swimwearOverride = new Map<ItemModelSlot, ReadonlySet<ItemModelSlot>>([
  [ItemModelSlots.Body, new Set([ItemModelSlots.Hands])],
  [ItemModelSlots.Legs, new Set([ItemModelSlots.Feet])],
])

const itemModelOverrides = new Map<number, ReadonlyMap<ItemModelSlot, ReadonlySet<ItemModelSlot>>>([
  [495, swimwearOverride],
  [496, swimwearOverride],
])

const additionalMappings = new Map<number, number>([
  [23871, 495],
  [23872, 495],
  [23873, 496],
  [23874, 496],
  [26959, 303],
  [26961, 209],
  [26962, 51],
])

export class ItemModelTable implements LoadableResource {
  private readonly loadTableBytes: () => Promise<Uint8Array>
  private table: ByteReader | null = null
  private preloaded = false

  constructor(loadTableBytes: () => Promise<Uint8Array>) {
    this.loadTableBytes = loadTableBytes
  }

  async preload(): Promise<void> {
    if (this.preloaded) {
      return
    }

    this.preloaded = true
    this.table = new ByteReader(await this.loadTableBytes())
  }

  isFullyLoaded(): boolean {
    return this.table !== null
  }

  getModelId(item: InventoryItemInfo | null): number {
    if (item === null) {
      return 0
    }

    return this.getModelIdFromItemId(item.itemId)
  }

  getModelIdFromItemId(itemId: number): number {
    const mapped = additionalMappings.get(itemId)
    if (mapped !== undefined) {
      return mapped
    }

    if (this.table === null) {
      throw new Error('ItemModelTable must be preloaded before use')
    }

    this.table.position = itemId * 2
    return this.table.next16()
  }

  getForcedMatches(slot: ItemModelSlot, modelId: number): ReadonlySet<ItemModelSlot> {
    return itemModelOverrides.get(modelId)?.get(slot) ?? new Set<ItemModelSlot>()
  }
}
