import { describe, expect, it, vi } from 'vitest'

import { ActorRoutineQueue, RoutineOptions } from '~/lib/runtime/actorRoutineQueue'

interface TestRoutine {
  readonly id: string
}

describe('ActorRoutineQueue', () => {
  it('plays high-priority routines before normal entries', () => {
    const played: string[] = []
    const queue = new ActorRoutineQueue<TestRoutine>({
      isAnimationLocked: () => false,
      playRoutine: (_context, routine) => {
        played.push(routine.id)
        return { hasCompletedAllSequences: () => true }
      },
    })

    queue.enqueueRoutine({ actorId: 1 }, { id: 'normal' }, new RoutineOptions())
    queue.enqueueRoutine({ actorId: 1 }, { id: 'high' }, new RoutineOptions({ highPriority: true }))

    queue.update(1)
    queue.update(1)

    expect(played).toEqual(['high', 'normal'])
  })

  it('executes callback when queued provider resolves to null', () => {
    const onComplete = vi.fn()
    const queue = new ActorRoutineQueue<TestRoutine>({
      isAnimationLocked: () => false,
      playRoutine: () => ({ hasCompletedAllSequences: () => true }),
    })

    queue.enqueueRoutine({ actorId: 1 }, null, new RoutineOptions({ callback: onComplete }))

    queue.update(1)
    expect(onComplete).toHaveBeenCalledTimes(1)
  })
})
