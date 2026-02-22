import { Actor, ActorId, type ActorState } from '~/lib/runtime/actor'
import type { ActorController } from '~/lib/runtime/actorController'

export class ActorManager {
  private readonly actors = new Map<number, Actor>()
  private readonly visibleActors = new Map<number, Actor>()

  get(actorId: ActorId | null | undefined): Actor | null {
    if (!actorId) {
      return null
    }
    return this.actors.get(actorId.id) ?? null
  }

  getVisibleActors(): readonly Actor[] {
    return Array.from(this.visibleActors.values())
  }

  remove(actorId: ActorId): void {
    this.actors.delete(actorId.id)
    this.visibleActors.delete(actorId.id)
  }

  clear(): void {
    this.actors.clear()
    this.visibleActors.clear()
  }

  getOrCreate(
    actorState: ActorState,
    actorController: ActorController,
    playRoutine: ConstructorParameters<typeof Actor>[2],
  ): Actor {
    const existing = this.actors.get(actorState.id.id)
    if (existing) {
      return existing
    }

    const actor = new Actor(actorState, actorController, playRoutine)
    this.actors.set(actor.id.id, actor)
    return actor
  }

  updateAll(elapsedFrames: number, cameraPosition: { x: number, y: number, z: number }, maxVisible = 20): void {
    this.refreshVisibleActors(cameraPosition, maxVisible)
    for (const actor of this.actors.values()) {
      actor.update(elapsedFrames)
    }
  }

  private refreshVisibleActors(cameraPosition: { x: number, y: number, z: number }, maxVisible: number): void {
    const sorted = Array.from(this.actors.values())
      .filter((actor) => actor.state.visible)
      .sort((a, b) => {
        const da = distance(cameraPosition, a.displayPosition)
        const db = distance(cameraPosition, b.displayPosition)
        return da - db
      })

    this.visibleActors.clear()
    for (const actor of sorted.slice(0, maxVisible)) {
      this.visibleActors.set(actor.id.id, actor)
    }
  }
}

function distance(a: { x: number, y: number, z: number }, b: { x: number, y: number, z: number }): number {
  return Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z)
}
