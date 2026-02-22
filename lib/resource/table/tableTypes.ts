class RaceGenderConfigDef {
  readonly name: string
  readonly index: number
  readonly equipmentTableIndex: number

  constructor(name: string, index: number, equipmentTableIndex: number) {
    this.name = name
    this.index = index
    this.equipmentTableIndex = equipmentTableIndex
  }
}

export const RaceGenderConfig = {
  HumeMale: new RaceGenderConfigDef('HumeMale', 0, 1),
  HumeFemale: new RaceGenderConfigDef('HumeFemale', 1, 2),
  ElvaanMale: new RaceGenderConfigDef('ElvaanMale', 2, 3),
  ElvaanFemale: new RaceGenderConfigDef('ElvaanFemale', 3, 4),
  TarutaruMale: new RaceGenderConfigDef('TarutaruMale', 4, 5),
  TarutaruFemale: new RaceGenderConfigDef('TarutaruFemale', 5, 6),
  Mithra: new RaceGenderConfigDef('Mithra', 6, 7),
  Galka: new RaceGenderConfigDef('Galka', 7, 8),
  values(): RaceGenderConfigDef[] {
    return [
      this.HumeMale,
      this.HumeFemale,
      this.ElvaanMale,
      this.ElvaanFemale,
      this.TarutaruMale,
      this.TarutaruFemale,
      this.Mithra,
      this.Galka,
    ]
  },
} as const

export type RaceGenderConfig = RaceGenderConfigDef

class ItemModelSlotDef {
  readonly name: string
  readonly prefix: number

  constructor(name: string, prefix: number) {
    this.name = name
    this.prefix = prefix
  }
}

export const ItemModelSlot = {
  Face: new ItemModelSlotDef('Face', 0x0000),
  Head: new ItemModelSlotDef('Head', 0x1000),
  Body: new ItemModelSlotDef('Body', 0x2000),
  Hands: new ItemModelSlotDef('Hands', 0x3000),
  Legs: new ItemModelSlotDef('Legs', 0x4000),
  Feet: new ItemModelSlotDef('Feet', 0x5000),
  Main: new ItemModelSlotDef('Main', 0x6000),
  Sub: new ItemModelSlotDef('Sub', 0x7000),
  Range: new ItemModelSlotDef('Range', 0x8000),
  values(): ItemModelSlotDef[] {
    return [this.Face, this.Head, this.Body, this.Hands, this.Legs, this.Feet, this.Main, this.Sub, this.Range]
  },
} as const

export type ItemModelSlot = ItemModelSlotDef

export interface InventoryItemInfo {
  readonly itemId: number
}
