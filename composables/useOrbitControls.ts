import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { Vector3, type PerspectiveCamera } from 'three'

export interface OrbitControlsOptions {
  readonly camera: PerspectiveCamera
  readonly domElement: HTMLElement
  readonly target?: { x: number; y: number; z: number }
  readonly minDistance?: number
  readonly maxDistance?: number
  readonly enablePan?: boolean
  readonly panSpeed?: number
}

/** Snapshot of the current orbit camera state. */
export interface OrbitState {
  /** Horizontal angle in degrees (0 = right, 90 = front, etc.). */
  azimuth: number
  /** Vertical angle in degrees (0 = top pole, 90 = equator, 180 = bottom). */
  elevation: number
  /** Distance from camera to orbit target. */
  distance: number
  /** Current orbit target position. */
  target: { x: number, y: number, z: number }
}

export interface OrbitControlsHandle {
  /** Call once per frame to apply damping and return current state. */
  update(): OrbitState
  /** Reposition the orbit target. */
  setTarget(target: { x: number; y: number; z: number }): void
  /** Enable or disable auto-rotation. */
  setAutoRotate(enabled: boolean): void
  /** Enable or disable panning (right-drag to move) and WASD keys. */
  setPan(enabled: boolean): void
  /** Reset camera to its initial position and target. */
  reset(): void
  /** Remove all event listeners. */
  dispose(): void
}

const RAD_TO_DEG = 180 / Math.PI

/**
 * Creates Three.js OrbitControls bound to the given camera and canvas.
 * Supports orbit (left-drag), zoom (scroll), auto-rotate, and reset.
 */
export function useOrbitControls(options: OrbitControlsOptions): OrbitControlsHandle {
  const controls = new OrbitControls(options.camera, options.domElement)

  controls.enablePan = options.enablePan ?? false
  controls.enableDamping = true
  controls.dampingFactor = 0.1
  controls.minDistance = options.minDistance ?? 0.1
  controls.maxDistance = options.maxDistance ?? 50
  controls.autoRotateSpeed = 2.0
  controls.panSpeed = options.panSpeed ?? 1.0

  if (options.target) {
    controls.target.set(options.target.x, options.target.y, options.target.z)
  }

  // Save initial state for reset
  const initialPosition = options.camera.position.clone()
  const initialTarget = controls.target.clone()

  // ─── WASD keyboard movement ──────────────────────────────────────────────
  const keysDown = new Set<string>()
  let wasdEnabled = options.enablePan ?? false
  const moveSpeed = 1.5

  function onKeyDown(e: KeyboardEvent): void {
    if (!wasdEnabled) return
    const key = e.key.toLowerCase()
    if ('wasdqe'.includes(key)) {
      keysDown.add(key)
      e.preventDefault()
    }
  }

  function onKeyUp(e: KeyboardEvent): void {
    keysDown.delete(e.key.toLowerCase())
  }

  // Listen on the canvas element so keys only work when it has focus
  const el = options.domElement
  el.tabIndex = el.tabIndex === -1 ? 0 : el.tabIndex // make focusable
  el.addEventListener('keydown', onKeyDown)
  el.addEventListener('keyup', onKeyUp)
  // Also focus on click so user doesn't have to tab to canvas
  const onFocus = (): void => { el.focus() }
  el.addEventListener('mousedown', onFocus)

  /** Apply WASD movement: translate both camera and target together. */
  function applyKeyboardMovement(): void {
    if (keysDown.size === 0) return

    // Camera look direction (full 3D, not flattened) for true camera-relative movement
    const look = new Vector3().subVectors(controls.target, options.camera.position).normalize()

    // Right = look cross camera.up (camera.up is (0,-1,0) in FFXI Y-down)
    const right = new Vector3().crossVectors(look, options.camera.up).normalize()

    // Up = right cross look (perpendicular to both)
    const up = new Vector3().crossVectors(right, look).normalize()

    const delta = new Vector3()
    if (keysDown.has('w')) delta.add(look)
    if (keysDown.has('s')) delta.sub(look)
    if (keysDown.has('d')) delta.add(right)
    if (keysDown.has('a')) delta.sub(right)
    if (keysDown.has('q')) delta.add(up)
    if (keysDown.has('e')) delta.sub(up)

    if (delta.lengthSq() < 1e-6) return
    delta.normalize().multiplyScalar(moveSpeed)

    options.camera.position.add(delta)
    controls.target.add(delta)
  }

  // Sync initial state so the first frame isn't a jump
  controls.update()

  function getOrbitState(): OrbitState {
    const offset = new Vector3().subVectors(options.camera.position, controls.target)
    const distance = offset.length()

    // Spherical coordinates: azimuth from +X axis, polar from +Y axis
    const azimuth = Math.atan2(offset.z, offset.x) * RAD_TO_DEG
    const elevation = Math.acos(Math.max(-1, Math.min(1, offset.y / distance))) * RAD_TO_DEG

    return {
      azimuth: +azimuth.toFixed(1),
      elevation: +elevation.toFixed(1),
      distance: +distance.toFixed(2),
      target: {
        x: +controls.target.x.toFixed(1),
        y: +controls.target.y.toFixed(1),
        z: +controls.target.z.toFixed(1),
      },
    }
  }

  return {
    update: () => {
      applyKeyboardMovement()
      controls.update()
      return getOrbitState()
    },
    setTarget: (target) => {
      controls.target.set(target.x, target.y, target.z)
      controls.update()
    },
    setAutoRotate: (enabled) => {
      controls.autoRotate = enabled
    },
    setPan: (enabled) => {
      controls.enablePan = enabled
      wasdEnabled = enabled
      if (!enabled) keysDown.clear()
    },
    reset: () => {
      options.camera.position.copy(initialPosition)
      controls.target.copy(initialTarget)
      controls.update()
    },
    dispose: () => {
      el.removeEventListener('keydown', onKeyDown)
      el.removeEventListener('keyup', onKeyUp)
      el.removeEventListener('mousedown', onFocus)
      keysDown.clear()
      controls.dispose()
    },
  }
}
