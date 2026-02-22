export interface ActorControllerState {
  readonly id: number
  readonly position: { x: number, y: number, z: number }
  readonly targetPosition?: { x: number, y: number, z: number }
  readonly movementSpeed: number
}

export interface ActorController {
  getVelocity(actorState: ActorControllerState, elapsedFrames: number): { x: number, y: number, z: number }
}

export class NoOpActorController implements ActorController {
  getVelocity(): { x: number, y: number, z: number } {
    return { x: 0, y: 0, z: 0 }
  }
}

export class FollowTargetActorController implements ActorController {
  private readonly followDistanceNear: number
  private readonly followDistanceFar: number
  private approaching = false

  constructor(followDistanceNear = 2, followDistanceFar = 4) {
    this.followDistanceNear = followDistanceNear
    this.followDistanceFar = followDistanceFar
  }

  getVelocity(actorState: ActorControllerState, elapsedFrames: number): { x: number, y: number, z: number } {
    const target = actorState.targetPosition
    if (!target) {
      return { x: 0, y: 0, z: 0 }
    }

    const distance = distance3(actorState.position, target)
    if (distance <= this.followDistanceNear) {
      this.approaching = false
      return { x: 0, y: 0, z: 0 }
    }

    if (!this.approaching && distance < this.followDistanceFar) {
      return { x: 0, y: 0, z: 0 }
    }

    this.approaching = true
    return velocityVectorTo(actorState.position, target, actorState.movementSpeed, elapsedFrames)
  }
}

export function velocityVectorTo(
  source: { x: number, y: number, z: number },
  destination: { x: number, y: number, z: number },
  speed: number,
  elapsedFrames: number,
): { x: number, y: number, z: number } {
  const dx = destination.x - source.x
  const dz = destination.z - source.z
  const magnitude = Math.hypot(dx, dz)
  if (magnitude <= 1e-5) {
    return { x: 0, y: 0, z: 0 }
  }

  const normalizedX = dx / magnitude
  const normalizedZ = dz / magnitude
  return {
    x: normalizedX * speed * elapsedFrames,
    y: 0,
    z: normalizedZ * speed * elapsedFrames,
  }
}

function distance3(a: { x: number, y: number, z: number }, b: { x: number, y: number, z: number }): number {
  return Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z)
}
