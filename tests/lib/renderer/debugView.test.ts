import { describe, expect, it } from 'vitest'

import { applyDebugViewState } from '~/lib/renderer/debugView'

describe('applyDebugViewState', () => {
  it('enables wireframe and bones visibility when requested', () => {
    const materials = [{ wireframe: false }, { wireframe: false }]
    const boneHelpers = [{ visible: false }]

    applyDebugViewState(materials, boneHelpers, {
      showWireframe: true,
      showBones: true,
    })

    expect(materials[0]?.wireframe).toBe(true)
    expect(materials[1]?.wireframe).toBe(true)
    expect(boneHelpers[0]?.visible).toBe(true)
  })

  it('disables wireframe and bones visibility when toggled off', () => {
    const materials = [{ wireframe: true }]
    const boneHelpers = [{ visible: true }, { visible: true }]

    applyDebugViewState(materials, boneHelpers, {
      showWireframe: false,
      showBones: false,
    })

    expect(materials[0]?.wireframe).toBe(false)
    expect(boneHelpers[0]?.visible).toBe(false)
    expect(boneHelpers[1]?.visible).toBe(false)
  })
})
