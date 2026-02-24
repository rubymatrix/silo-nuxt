/**
 * Zone ID to resource path mapping.
 *
 * Ported from xim/resource/table/ZoneTables.kt.
 * Resolves a zone ID to a DAT file path via the file table.
 */

import type { FileTableManager } from './fTable'

/**
 * Convert a zone ID to its file table index.
 * Main areas (< 0x100): index = 0x64 + zoneId
 * Expansion areas (>= 0x100): index = 0x147B3 + (zoneId - 0x100)
 */
export function zoneIdToFileTableIndex(zoneId: number): number {
  if (zoneId < 0x100) {
    return 0x64 + zoneId
  }
  return 0x147b3 + (zoneId - 0x100)
}

/**
 * Resolve a zone ID to a DAT file path (e.g. "ROM/25/81.DAT").
 */
export function getZoneDatPath(zoneId: number, fileTableManager: FileTableManager): string | null {
  const fileTableIndex = zoneIdToFileTableIndex(zoneId)
  return fileTableManager.getFilePath(fileTableIndex)
}

/**
 * Starting nation zone configs.
 * Each entry includes the zone ID and a starting position for character placement.
 * Positions are approximate zone entrance coordinates.
 */
export interface StartingZoneConfig {
  readonly name: string
  readonly zoneId: number
  /** Starting XZ position. Y (height) is resolved via collision raycast. */
  readonly startPosition: { readonly x: number, readonly z: number }
  /** Fallback Y position if raycast fails (FFXI Y-down convention, so negative is up). */
  readonly fallbackY: number
}

export const STARTING_ZONES: Record<string, StartingZoneConfig> = {
  sandoria: {
    name: "South San d'Oria",
    zoneId: 230,
    startPosition: { x: 133, z: 88 },
    fallbackY: -2,
  },
  bastok: {
    name: 'Bastok Markets',
    zoneId: 235,
    startPosition: { x: -328, z: -165 },
    fallbackY: -6,
  },
  windurst: {
    name: 'Windurst Woods',
    zoneId: 241,
    startPosition: { x: -1, z: -13 },
    fallbackY: -2,
  },
}

/** Map the nation selector index (0, 1, 2) to a zone config key. */
export function nationIndexToZoneKey(index: number): string {
  switch (index) {
    case 0: return 'sandoria'
    case 1: return 'bastok'
    case 2: return 'windurst'
    default: return 'sandoria'
  }
}
