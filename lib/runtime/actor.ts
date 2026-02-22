import { ActorRoutineQueue, type ActorRoutineContext } from '~/lib/runtime/actorRoutineQueue'
import type { ActorController } from '~/lib/runtime/actorController'
import type { ActorModel } from '~/lib/runtime/actorModel'

export class ActorId {
  readonly id: number

  constructor(id: number) {
    this.id = id
  }
}

export enum Direction {
  None = 'None',
  Forward = 'Forward',
  Left = 'Left',
  Right = 'Right',
  Backward = 'Backward',
}

export interface ActorState {
  readonly id: ActorId
  position: { x: number, y: number, z: number }
  velocity: { x: number, y: number, z: number }
  rotation: number
  visible: boolean
  movementSpeed: number
}

export class RenderState {
  effectColor = { r: 255, g: 255, b: 255, a: 255 }
  forceHideShadow = false
}

export class Actor {
  readonly id: ActorId
  readonly state: ActorState

  actorModel: ActorModel | null = null
  readonly renderState = new RenderState()

  readonly displayPosition = { x: 0, y: 0, z: 0 }
  readonly previousPosition = { x: 0, y: 0, z: 0 }
  readonly movement = { x: 0, y: 0, z: 0 }

  displayFacingDir = 0

  private readonly routineQueue: ActorRoutineQueue<unknown>
  private readonly readyToDrawActions: Array<(actor: Actor) => void> = []

  constructor(
    state: ActorState,
    private readonly actorController: ActorController,
    playRoutine: (context: ActorRoutineContext, routine: unknown) => { hasCompletedAllSequences(): boolean } | null,
  ) {
    this.id = state.id
    this.state = state

    this.routineQueue = new ActorRoutineQueue({
      isAnimationLocked: () => this.actorModel?.isAnimationLocked() ?? false,
      playRoutine: (context, routine) => playRoutine(context, routine),
    })

    this.syncFromState()
  }

  syncFromState(): void {
    this.displayFacingDir = this.state.rotation
    this.displayPosition.x = this.state.position.x
    this.displayPosition.y = this.state.position.y
    this.displayPosition.z = this.state.position.z
  }

  update(elapsedFrames: number): void {
    this.previousPosition.x = this.displayPosition.x
    this.previousPosition.y = this.displayPosition.y
    this.previousPosition.z = this.displayPosition.z

    const velocity = this.actorController.getVelocity(
      {
        id: this.id.id,
        position: this.state.position,
        movementSpeed: this.state.movementSpeed,
      },
      elapsedFrames,
    )

    this.state.velocity = velocity
    this.state.position.x += velocity.x
    this.state.position.y += velocity.y
    this.state.position.z += velocity.z

    this.displayPosition.x = this.state.position.x
    this.displayPosition.y = this.state.position.y
    this.displayPosition.z = this.state.position.z

    this.movement.x = this.displayPosition.x - this.previousPosition.x
    this.movement.y = this.displayPosition.y - this.previousPosition.y
    this.movement.z = this.displayPosition.z - this.previousPosition.z

    if (this.readyToDrawActions.length > 0 && this.isReadyToDraw()) {
      this.readyToDrawActions.splice(0).forEach((action) => action(this))
    }

    this.routineQueue.update(elapsedFrames)
    this.actorModel?.update(elapsedFrames)
  }

  isReadyToDraw(): boolean {
    return (this.actorModel?.model.isReadyToDraw() ?? false) && this.state.visible
  }

  onReadyToDraw(readyToDrawAction: (actor: Actor) => void): void {
    this.readyToDrawActions.push(readyToDrawAction)
  }

  enqueueRoutine(context: ActorRoutineContext, routine: unknown): void {
    this.routineQueue.enqueueRoutine(context, routine)
  }
}
