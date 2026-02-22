import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import type { PerspectiveCamera } from 'three'

export interface OrbitControlsOptions {
  readonly camera: PerspectiveCamera
  readonly domElement: HTMLElement
  readonly target?: { x: number; y: number; z: number }
  readonly minDistance?: number
  readonly maxDistance?: number
}

export interface OrbitControlsHandle {
  /** Call once per frame to apply damping. */
  update(): void
  /** Reposition the orbit target. */
  setTarget(target: { x: number; y: number; z: number }): void
  /** Remove all event listeners. */
  dispose(): void
}

/**
 * Creates Three.js OrbitControls bound to the given camera and canvas.
 * Supports orbit (left-drag) and zoom (scroll). Panning is disabled.
 */
export function useOrbitControls(options: OrbitControlsOptions): OrbitControlsHandle {
  const controls = new OrbitControls(options.camera, options.domElement)

  controls.enablePan = false
  controls.enableDamping = true
  controls.dampingFactor = 0.1
  controls.minDistance = options.minDistance ?? 0.1
  controls.maxDistance = options.maxDistance ?? 50

  if (options.target) {
    controls.target.set(options.target.x, options.target.y, options.target.z)
  }

  // Sync initial state so the first frame isn't a jump
  controls.update()

  return {
    update: () => controls.update(),
    setTarget: (target) => {
      controls.target.set(target.x, target.y, target.z)
      controls.update()
    },
    dispose: () => controls.dispose(),
  }
}
