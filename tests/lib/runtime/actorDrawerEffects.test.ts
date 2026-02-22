import { describe, expect, it } from 'vitest'

import { getBlurOffset } from '~/lib/runtime/actorDrawer'

describe('actorDrawer blur', () => {
  it('returns radial offsets scaled by zoom factor', () => {
    const offset = getBlurOffset(90, 0, 4, 16, 32)
    expect(offset.x).toBeCloseTo(0, 6)
    expect(offset.y).toBeCloseTo(0.5, 6)
  })
})
