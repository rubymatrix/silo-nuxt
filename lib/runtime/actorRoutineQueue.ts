export interface ActorRoutineContext {
  readonly actorId: number
  readonly targetId?: number
}

export interface RoutineHandle {
  hasCompletedAllSequences(): boolean
}

export class RoutineOptions {
  readonly blocking: boolean
  readonly highPriority: boolean
  readonly expiryDurationFrames: number | null
  readonly callback: (() => void) | null

  constructor(options: {
    blocking?: boolean
    highPriority?: boolean
    expiryDurationFrames?: number | null
    callback?: (() => void) | null
  } = {}) {
    this.blocking = options.blocking ?? true
    this.highPriority = options.highPriority ?? false
    this.expiryDurationFrames = options.expiryDurationFrames ?? null
    this.callback = options.callback ?? null
  }
}

interface RoutineProvider<TRoutine> {
  ready: () => boolean
  provider: () => TRoutine | null
}

interface QueuedRoutine<TRoutine> {
  readonly context: ActorRoutineContext
  readonly routineProvider: RoutineProvider<TRoutine>
  readonly options: RoutineOptions
  age: number
}

export class ActorRoutineQueue<TRoutine> {
  private readonly notReadyExpiryFrames: number
  private readonly isAnimationLocked: () => boolean
  private readonly playRoutine: (context: ActorRoutineContext, routine: TRoutine, options: RoutineOptions) => RoutineHandle | null

  private readonly routineQueue: QueuedRoutine<TRoutine>[] = []
  private currentRoutine: RoutineHandle | null = null

  constructor(options: {
    isAnimationLocked: () => boolean
    playRoutine: (context: ActorRoutineContext, routine: TRoutine, options: RoutineOptions) => RoutineHandle | null
    notReadyExpiryFrames?: number
  }) {
    this.isAnimationLocked = options.isAnimationLocked
    this.playRoutine = options.playRoutine
    this.notReadyExpiryFrames = options.notReadyExpiryFrames ?? 300
  }

  update(elapsedFrames: number): void {
    if (this.currentRoutine?.hasCompletedAllSequences()) {
      this.currentRoutine = null
    }

    this.routineQueue.forEach((queuedRoutine) => {
      queuedRoutine.age += elapsedFrames
    })

    this.removeExpiredEntries()

    const next = this.routineQueue[0]
    if (!next) {
      return
    }

    if (!next.routineProvider.ready()) {
      if (next.age >= this.notReadyExpiryFrames) {
        this.routineQueue.shift()
        this.executeCallback(next)
      }
      return
    }

    if (!next.options.highPriority && this.isAnimationLocked()) {
      return
    }

    this.routineQueue.shift()
    const routine = next.routineProvider.provider()
    if (!routine) {
      this.executeCallback(next)
      return
    }

    const handle = this.playRoutine(next.context, routine, next.options)
    if (next.options.blocking) {
      this.currentRoutine = handle
    } else {
      this.update(0)
    }
  }

  hasEnqueuedRoutines(): boolean {
    return this.currentRoutine !== null || this.routineQueue.length > 0
  }

  enqueueRoutine(context: ActorRoutineContext, routine: TRoutine | null, options = new RoutineOptions()): void {
    const wrapped: RoutineProvider<TRoutine> = {
      ready: () => true,
      provider: () => routine,
    }

    this.enqueueInternal({
      context,
      routineProvider: wrapped,
      options,
      age: 0,
    })
  }

  enqueueProvider(context: ActorRoutineContext, provider: () => TRoutine | null, options = new RoutineOptions()): void {
    const wrapped: RoutineProvider<TRoutine> = {
      ready: () => provider() !== null,
      provider,
    }
    this.enqueueInternal({
      context,
      routineProvider: wrapped,
      options,
      age: 0,
    })
  }

  private enqueueInternal(queued: QueuedRoutine<TRoutine>): void {
    if (!queued.options.highPriority) {
      this.routineQueue.push(queued)
      return
    }

    const index = this.routineQueue.findIndex((entry) => !entry.options.highPriority)
    if (index === -1) {
      this.routineQueue.push(queued)
    } else {
      this.routineQueue.splice(index, 0, queued)
    }
  }

  private removeExpiredEntries(): void {
    while (this.routineQueue.length > 0) {
      const next = this.routineQueue[0]
      if (!next || next.options.expiryDurationFrames === null || next.age < next.options.expiryDurationFrames) {
        return
      }

      this.routineQueue.shift()
      this.executeCallback(next)
    }
  }

  private executeCallback(queued: QueuedRoutine<TRoutine>): void {
    queued.options.callback?.()
  }
}
