import { describe, expect, it } from 'vitest'

import { buildBlurCompositePlan } from '~/lib/renderer/postProcess'

describe('postProcess', () => {
  it('builds additive blur passes from blur offsets', () => {
    const plan = buildBlurCompositePlan([
      { x: 0.1, y: 0 },
      { x: -0.05, y: 0.03 },
    ])

    expect(plan.requiresBlur).toBe(true)
    expect(plan.passes).toEqual([
      { offset: { x: 0.1, y: 0 }, blendMode: 'additive' },
      { offset: { x: -0.05, y: 0.03 }, blendMode: 'additive' },
    ])
  })

  it('drops non-finite offsets and disables blur when none remain', () => {
    const plan = buildBlurCompositePlan([
      { x: Number.NaN, y: 1 },
      { x: Number.POSITIVE_INFINITY, y: 0 },
    ])

    expect(plan.requiresBlur).toBe(false)
    expect(plan.passes).toEqual([])
  })
})
