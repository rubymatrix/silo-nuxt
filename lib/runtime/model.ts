import { type DatId, type DirectoryResource, type InfoDefinition, type SkeletonResource } from '~/lib/resource/datResource'

export enum ItemModelSlot {
  Face = 0,
  Head = 1,
  Body = 2,
  Hands = 3,
  Legs = 4,
  Feet = 5,
  Main = 6,
  Sub = 7,
  Range = 8,
}

export interface Blur {
  readonly color: number
  readonly offset: number
}

export interface BlurConfig {
  readonly blurs: readonly Blur[]
}

export interface Model {
  isReadyToDraw(): boolean
  getMeshResources(): readonly DirectoryResource[]
  getSkeletonResource(): SkeletonResource | null
  getAnimationDirectories(): readonly DirectoryResource[]
  getMainBattleAnimationDirectory(): DirectoryResource | null
  getSubBattleAnimationDirectory(): DirectoryResource | null
  getEquipmentModelResource(modelSlot: ItemModelSlot): DirectoryResource | null
  getMovementInfo(): InfoDefinition | null
  getMainWeaponInfo(): InfoDefinition | null
  getSubWeaponInfo(): InfoDefinition | null
  getRangedWeaponInfo(): InfoDefinition | null
  getBlurConfig(): BlurConfig | null
  getScale(): number
}

export class EquipmentLook {
  private readonly look: number[] = Array.from({ length: 9 }, () => 0)

  copy(): EquipmentLook {
    const next = new EquipmentLook()
    this.look.forEach((value, index) => {
      next.look[index] = value
    })
    return next
  }

  set(slot: ItemModelSlot, modelId: number): this {
    this.look[slot] = modelId
    return this
  }

  get(slot: ItemModelSlot): number {
    return this.look[slot] ?? 0
  }
}

export class ModelLook {
  static readonly fileTableIndexType = -11
  static readonly particleType = -12

  readonly type: number
  readonly modelId: number
  readonly equipment: EquipmentLook

  constructor(type: number, modelId: number, equipment = new EquipmentLook()) {
    this.type = type
    this.modelId = modelId
    this.equipment = equipment
  }

  static blank(): ModelLook {
    return new ModelLook(0, 0, new EquipmentLook())
  }

  copy(): ModelLook {
    return new ModelLook(this.type, this.modelId, this.equipment.copy())
  }
}

export interface RuntimeModelDirectory {
  readonly id: DatId
  readonly root: DirectoryResource
}
