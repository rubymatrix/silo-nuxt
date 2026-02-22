import { describe, expect, it } from 'vitest'

import { RaceGenderConfig } from '~/lib/resource/table/tableTypes'
import {
  equipmentSlotOptions,
  getDefaultPcEquipmentModelId,
  getPcFaceModelIds,
  getXiCameraFrame,
  getPcEquipmentModelPaths,
  getPcEquipmentSlotCount,
  parameterizeAnimationId,
  pcEquipmentSlots,
  raceGenderOptions,
  resolvePcSceneResourcePaths,
  resolveRaceGenderConfig,
  selectDefaultAnimationId,
} from '~/lib/runtime/basicPc'

describe('resolvePcSceneResourcePaths', () => {
  it('resolves model and animation DAT paths for a race config', () => {
    const paths = resolvePcSceneResourcePaths(
      {
        mainDll: {
          getBaseRaceConfigIndex: () => 20,
          getBaseBattleAnimationIndex: () => 42,
        },
        fileTableManager: {
          getFilePath: (fileId) => {
            if (fileId === 20) {
              return 'ROM/10/20.DAT'
            }

            if (fileId === 42) {
              return 'ROM/12/42.DAT'
            }

            if (fileId === 21) {
              return 'ROM/10/21.DAT'
            }

            if (fileId === 24) {
              return 'ROM/10/24.DAT'
            }

            return null
          },
        },
      },
      RaceGenderConfig.HumeMale,
    )

    expect(paths).toEqual({
      modelPath: 'ROM/10/20.DAT',
      animationPath: 'ROM/12/42.DAT',
      upperBodyAnimationPath: 'ROM/10/21.DAT',
      skirtAnimationPath: 'ROM/10/24.DAT',
    })
  })

  it('throws when either DAT path cannot be resolved', () => {
    expect(() => resolvePcSceneResourcePaths(
      {
        mainDll: {
          getBaseRaceConfigIndex: () => 20,
          getBaseBattleAnimationIndex: () => 42,
        },
        fileTableManager: {
          getFilePath: () => null,
        },
      },
      RaceGenderConfig.HumeMale,
    )).toThrowError(/Failed to resolve PC scene resource paths/)
  })
})

describe('race/gender selection helpers', () => {
  it('exposes race/gender options in table order', () => {
    expect(raceGenderOptions[0]).toEqual({ value: 'HumeMale', label: 'Hume Male' })
    expect(raceGenderOptions.at(-1)).toEqual({ value: 'Galka', label: 'Galka' })
  })

  it('resolves race/gender by option value and falls back safely', () => {
    expect(resolveRaceGenderConfig('TarutaruFemale')).toBe(RaceGenderConfig.TarutaruFemale)
    expect(resolveRaceGenderConfig('UnknownValue')).toBe(RaceGenderConfig.HumeMale)
  })

  it('selects idle animation first when available', () => {
    expect(selectDefaultAnimationId(['atk0', 'run0', 'idl1', 'idl0'])).toBe('idl?')
    expect(selectDefaultAnimationId(['atk0', 'idl2', 'run0'])).toBe('idl?')
  })

  it('parameterizes animation ids to composite slot variants', () => {
    expect(parameterizeAnimationId('at00')).toBe('at0?')
    expect(parameterizeAnimationId('idl0')).toBe('idl?')
    expect(parameterizeAnimationId('idl?')).toBe('idl?')
  })
})

describe('equipment slot helpers', () => {
  const equipmentModelTable = {
    getNumEntries: (raceGenderConfig: { name: string }, slot: { name: string }) => {
      if (raceGenderConfig.name !== 'HumeMale') {
        return 0
      }

      return slot.name === 'Face' ? 3 : 2
    },
    getItemModelPath: (_raceGenderConfig: { name: string }, slot: { name: string }, modelId: number) => {
      if (slot.name === 'Face') {
        return `ROM/faces/${modelId}.DAT`
      }

      if (modelId === 0) {
        return null
      }

      return `ROM/${slot.name.toLowerCase()}/${modelId}.DAT`
    },
  }

  it('exposes expected selectable PC equipment slots', () => {
    expect(pcEquipmentSlots.map((slot) => slot.name)).toEqual(['Face', 'Head', 'Body', 'Hands', 'Legs', 'Feet'])
    expect(equipmentSlotOptions[0]).toEqual({ key: 'Face', label: 'Face', slot: pcEquipmentSlots[0] })
    expect(getPcFaceModelIds()).toEqual(Array.from({ length: 32 }, (_, i) => i))
  })

  it('builds model id options for a slot based on table count', () => {
    const options = getPcEquipmentSlotCount(equipmentModelTable, RaceGenderConfig.HumeMale, pcEquipmentSlots[0])
    expect(options).toBe(3)
  })

  it('resolves concrete DAT paths for selected model ids', () => {
    const paths = getPcEquipmentModelPaths(
      equipmentModelTable,
      RaceGenderConfig.HumeMale,
      new Map([
        [pcEquipmentSlots[0], 2],
        [pcEquipmentSlots[1], 0],
        [pcEquipmentSlots[2], 1],
      ]),
    )

    expect(paths).toEqual([
      { slot: pcEquipmentSlots[0], modelId: 2, modelPath: 'ROM/faces/2.DAT' },
      { slot: pcEquipmentSlots[2], modelId: 1, modelPath: 'ROM/body/1.DAT' },
    ])
  })

  it('falls back to first available model path when selected id has no DAT', () => {
    const fallbackTable = {
      getNumEntries: (_raceGenderConfig: { name: string }, slot: { name: string }) => {
        if (slot.name === 'Face') {
          return 3
        }
        return 1
      },
      getItemModelPath: (_raceGenderConfig: { name: string }, slot: { name: string }, modelId: number) => {
        if (slot.name === 'Face') {
          if (modelId === 2) {
            return 'ROM/faces/2.DAT'
          }
          return null
        }
        return null
      },
    }

    const paths = getPcEquipmentModelPaths(
      fallbackTable,
      RaceGenderConfig.HumeMale,
      new Map([
        [pcEquipmentSlots[0], 1],
      ]),
    )

    expect(paths).toEqual([
      { slot: pcEquipmentSlots[0], modelId: 2, modelPath: 'ROM/faces/2.DAT' },
    ])
  })

  it('defaults face to model 1 when available', () => {
    expect(getDefaultPcEquipmentModelId(pcEquipmentSlots[0], 4)).toBe(1)
  })

  it('defaults to none model for non-face slots and limited face lists', () => {
    expect(getDefaultPcEquipmentModelId(pcEquipmentSlots[1], 7)).toBe(0)
    expect(getDefaultPcEquipmentModelId(pcEquipmentSlots[2], 3)).toBe(0)
    expect(getDefaultPcEquipmentModelId(pcEquipmentSlots[0], 1)).toBe(0)
  })
})

describe('camera framing helpers', () => {
  it('frames actor centered using XI negative-up convention', () => {
    const frame = getXiCameraFrame(2)

    expect(frame.position).toEqual({ x: 5.6, y: -1.6, z: 0 })
    expect(frame.target).toEqual({ x: 0, y: -1, z: 0 })
  })
})
