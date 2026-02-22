import {
  ItemModelSlot,
  type ItemModelSlot as ItemModelSlotType,
  RaceGenderConfig,
  type RaceGenderConfig as RaceGenderConfigType,
} from '~/lib/resource/table/tableTypes'

interface MainDllLike {
  getBaseRaceConfigIndex(raceGenderConfig: RaceGenderConfigType): number
  getBaseBattleAnimationIndex(raceGenderConfig: RaceGenderConfigType): number
}

interface FileTableManagerLike {
  getFilePath(fileId: number | null): string | null
}

interface EquipmentModelTableLike {
  getNumEntries(raceGenderConfig: RaceGenderConfigType, itemModelSlot: ItemModelSlotType): number
  getItemModelPath(
    raceGenderConfig: RaceGenderConfigType,
    itemModelSlot: ItemModelSlotType,
    itemModelId: number,
  ): string | null
}

export interface PcSceneRuntimeTables {
  readonly mainDll: MainDllLike
  readonly fileTableManager: FileTableManagerLike
}

export interface PcSceneResourcePaths {
  readonly modelPath: string
  readonly animationPath: string
  readonly upperBodyAnimationPath: string
  readonly skirtAnimationPath: string
}

export interface RaceGenderOption {
  readonly value: string
  readonly label: string
}

export interface EquipmentSlotOption {
  readonly key: string
  readonly label: string
  readonly slot: ItemModelSlotType
}

export interface PcEquipmentPath {
  readonly slot: ItemModelSlotType
  readonly modelId: number
  readonly modelPath: string
}

export interface XiCameraFrame {
  readonly position: { x: number, y: number, z: number }
  readonly target: { x: number, y: number, z: number }
}

export const pcEquipmentSlots: readonly ItemModelSlotType[] = [
  ItemModelSlot.Face,
  ItemModelSlot.Head,
  ItemModelSlot.Body,
  ItemModelSlot.Hands,
  ItemModelSlot.Legs,
  ItemModelSlot.Feet,
]

export function getPcFaceModelIds(): readonly number[] {
  return Array.from({ length: 0x20 }, (_, index) => index)
}

export const equipmentSlotOptions: readonly EquipmentSlotOption[] = pcEquipmentSlots.map((slot) => ({
  key: slot.name,
  label: slot.name,
  slot,
}))

export const raceGenderOptions: readonly RaceGenderOption[] = RaceGenderConfig
  .values()
  .map((config) => ({
    value: config.name,
    label: formatRaceGenderLabel(config.name),
  }))

export function resolveRaceGenderConfig(value: string | null | undefined): RaceGenderConfigType {
  if (!value) {
    return RaceGenderConfig.HumeMale
  }

  const match = RaceGenderConfig.values().find((config) => config.name === value)
  return match ?? RaceGenderConfig.HumeMale
}

export function resolvePcSceneResourcePaths(
  runtime: PcSceneRuntimeTables,
  raceGenderConfig: RaceGenderConfigType,
): PcSceneResourcePaths {
  const baseRaceConfigIndex = runtime.mainDll.getBaseRaceConfigIndex(raceGenderConfig)
  const modelPath = runtime.fileTableManager.getFilePath(baseRaceConfigIndex)
  const upperBodyAnimationPath = runtime.fileTableManager.getFilePath(baseRaceConfigIndex + 0x01)
  const skirtAnimationPath = runtime.fileTableManager.getFilePath(baseRaceConfigIndex + 0x04)
  const animationPath = runtime.fileTableManager.getFilePath(runtime.mainDll.getBaseBattleAnimationIndex(raceGenderConfig))

  if (!modelPath || !animationPath || !upperBodyAnimationPath || !skirtAnimationPath) {
    throw new Error(`Failed to resolve PC scene resource paths for ${raceGenderConfig.name}`)
  }

  return {
    modelPath,
    animationPath,
    upperBodyAnimationPath,
    skirtAnimationPath,
  }
}

export function parameterizeAnimationId(animationId: string): string {
  if (animationId.length === 0 || animationId.endsWith('?')) {
    return animationId
  }

  return `${animationId.slice(0, -1)}?`
}

export function selectDefaultAnimationId(animationIds: readonly string[]): string | null {
  if (animationIds.length === 0) {
    return null
  }

  const idle = animationIds.find((id) => id.startsWith('idl'))
  if (idle) {
    return 'idl?'
  }

  const zeroVariant = animationIds.find((id) => id.endsWith('0'))
  return zeroVariant ?? animationIds[0] ?? null
}

export function getPcEquipmentSlotCount(
  equipmentModelTable: EquipmentModelTableLike,
  raceGenderConfig: RaceGenderConfigType,
  slot: ItemModelSlotType,
): number {
  return equipmentModelTable.getNumEntries(raceGenderConfig, slot)
}

export function getDefaultPcEquipmentModelId(slot: ItemModelSlotType, modelCount: number): number {
  if (slot === ItemModelSlot.Face && modelCount > 1) {
    return 1
  }

  return 0
}

export function getPcEquipmentModelPaths(
  equipmentModelTable: EquipmentModelTableLike,
  raceGenderConfig: RaceGenderConfigType,
  selectedModelIds: ReadonlyMap<ItemModelSlotType, number>,
): readonly PcEquipmentPath[] {
  const paths: PcEquipmentPath[] = []

  for (const slot of pcEquipmentSlots) {
    const modelId = selectedModelIds.get(slot) ?? 0
    if (modelId < 0) {
      continue
    }

    const modelPath = equipmentModelTable.getItemModelPath(raceGenderConfig, slot, modelId)
      ?? (modelId > 0 ? findFirstAvailableModelPath(equipmentModelTable, raceGenderConfig, slot) : null)
    if (!modelPath) {
      continue
    }

    const resolvedModelId = modelPath === equipmentModelTable.getItemModelPath(raceGenderConfig, slot, modelId)
      ? modelId
      : findModelIdForPath(equipmentModelTable, raceGenderConfig, slot, modelPath)

    paths.push({ slot, modelId: resolvedModelId, modelPath })
  }

  return paths
}

function findFirstAvailableModelPath(
  equipmentModelTable: EquipmentModelTableLike,
  raceGenderConfig: RaceGenderConfigType,
  slot: ItemModelSlotType,
): string | null {
  const count = Math.max(0, equipmentModelTable.getNumEntries(raceGenderConfig, slot))
  for (let id = 0; id < count; id += 1) {
    const path = equipmentModelTable.getItemModelPath(raceGenderConfig, slot, id)
    if (path) {
      return path
    }
  }

  return null
}

function findModelIdForPath(
  equipmentModelTable: EquipmentModelTableLike,
  raceGenderConfig: RaceGenderConfigType,
  slot: ItemModelSlotType,
  modelPath: string,
): number {
  const count = Math.max(0, equipmentModelTable.getNumEntries(raceGenderConfig, slot))
  for (let id = 0; id < count; id += 1) {
    if (equipmentModelTable.getItemModelPath(raceGenderConfig, slot, id) === modelPath) {
      return id
    }
  }

  return 0
}

export function getXiCameraFrame(skeletonHeight: number): XiCameraFrame {
  const height = Math.max(1, skeletonHeight)

  return {
    position: {
      x: Math.max(3.5, height * 2.8),
      y: -Math.max(1.4, height * 0.8),
      z: 0,
    },
    target: {
      x: 0,
      y: -Math.max(0.9, height * 0.5),
      z: 0,
    },
  }
}

function formatRaceGenderLabel(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, '$1 $2')
}
