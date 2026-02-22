export interface DebugViewState {
  readonly showWireframe: boolean
  readonly showBones: boolean
}

interface WireframeMaterialLike {
  wireframe: boolean
}

interface BoneHelperLike {
  visible: boolean
}

export function applyDebugViewState(
  materials: readonly WireframeMaterialLike[],
  boneHelpers: readonly BoneHelperLike[],
  state: DebugViewState,
): void {
  for (const material of materials) {
    material.wireframe = state.showWireframe
  }

  for (const helper of boneHelpers) {
    helper.visible = state.showBones
  }
}
