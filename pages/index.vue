<template>
  <main class="viewer-shell">
    <section class="viewer-panel">
      <header class="viewer-header">
        <p class="eyebrow">Silo Runtime</p>
        <h1>PC Model Scene</h1>
        <p>
          Loads base race resources plus selected equipment model DATs,
          then renders a composite player model with runtime animation.
        </p>
      </header>

      <!-- Character Creation -->
      <details class="panel-section" open>
        <summary class="section-heading">Character</summary>
        <div class="section-grid">
          <div>
            <label class="label" for="race-select">Race</label>
            <select
              id="race-select"
              v-model="selectedRace"
              :disabled="isLoading"
            >
              <option
                v-for="option in raceOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </div>

          <div v-if="hasGenderChoice">
            <label class="label" for="gender-select">Gender</label>
            <select
              id="gender-select"
              v-model="selectedGender"
              :disabled="isLoading"
            >
              <option
                v-for="option in genderOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </div>

          <div class="two-col">
            <div>
              <label class="label" for="face-select">Face</label>
              <select
                id="face-select"
                v-model.number="selectedFace"
                :disabled="isLoading"
              >
                <option
                  v-for="option in faceOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
            </div>
            <div>
              <label class="label" for="hair-select">Hair Color</label>
              <select
                id="hair-select"
                v-model.number="selectedHairColor"
                :disabled="isLoading"
              >
                <option
                  v-for="option in hairColorOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
            </div>
          </div>

          <div>
            <label class="label" for="size-select">Size</label>
            <select
              id="size-select"
              v-model.number="selectedSize"
              :disabled="isLoading"
            >
              <option
                v-for="option in sizeOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </div>

          <div>
            <label class="label" for="job-select">Starting Job</label>
            <select
              id="job-select"
              v-model.number="selectedJob"
              :disabled="isLoading"
            >
              <option
                v-for="option in startingJobOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </div>
          <div>
            <label class="label" for="nation-select">Starting Nation</label>
            <select
              id="nation-select"
              v-model.number="selectedNation"
              :disabled="isLoading"
            >
              <option
                v-for="option in nationOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </div>
        </div>
      </details>

      <!-- Environment -->
      <details class="panel-section" open>
        <summary class="section-heading">Environment</summary>
        <div class="section-grid">
          <div>
            <label class="label" for="time-slider">Time of Day</label>
            <div class="time-row">
              <input
                id="time-slider"
                v-model.number="timeOfDayMinutes"
                type="range"
                min="0"
                max="1440"
                step="1"
                :disabled="isLoading"
                class="time-slider"
                @input="onTimeOfDayChange"
              />
              <input
                :value="timeOfDayLabel"
                type="text"
                class="time-input"
                :disabled="isLoading"
                placeholder="HH:MM"
                @change="onTimeOfDayInput"
              />
            </div>
          </div>
        </div>
      </details>

      <!-- Equipment -->
      <details class="panel-section">
        <summary class="section-heading">Equipment</summary>
        <div class="equipment-grid">
          <div v-for="option in equipmentGridOptions" :key="option.key" class="equipment-slot">
            <label class="label" :for="`equipment-${option.key}`">{{ option.label }}</label>
            <select
              :id="`equipment-${option.key}`"
              v-model="selectedEquipmentIds[option.key]"
              :disabled="isLoading"
              @change="loadScene"
            >
              <option
                v-for="modelId in equipmentOptionsBySlot[option.key] ?? [0]"
                :key="modelId"
                :value="String(modelId)"
              >
                {{ modelId }}
              </option>
            </select>
            <code>{{ equipmentPathBySlot[option.key] ?? '-' }}</code>
          </div>
        </div>
      </details>

      <!-- Debug Status -->
      <details class="panel-section">
        <summary class="section-heading">Debug</summary>
        <div class="section-grid">
          <div class="debug-toggles">
            <label class="toggle">
              <input v-model="showWireframe" type="checkbox" @change="applyDebugView" />
              <span>Wireframe</span>
            </label>
            <label class="toggle">
              <input v-model="showBones" type="checkbox" @change="applyDebugView" />
              <span>Bones</span>
            </label>
          </div>
          <div>
            <span class="label">Model DAT</span>
            <code>{{ modelPath ?? '-' }}</code>
          </div>
          <div>
            <span class="label">Animation DAT</span>
            <code>{{ animationPath ?? '-' }}</code>
          </div>
          <div>
            <span class="label">Animation Id</span>
            <code>{{ animationId ?? '-' }}</code>
          </div>
          <div>
            <span class="label">Face Model Id</span>
            <code>{{ computedFaceModelId }}</code>
          </div>
          <div>
            <span class="label">Draw Debug</span>
            <code>{{ drawDebug }}</code>
          </div>
        </div>
      </details>

      <div class="actions">
        <button type="button" :disabled="isLoading" @click="loadScene">
          {{ isLoading ? 'Loading...' : 'Reload Scene' }}
        </button>
        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
      </div>
    </section>

    <section class="canvas-wrap">
      <canvas ref="canvasRef" />
      <div class="canvas-overlay">
        <div class="orbit-readout">
          <span>Orbit {{ orbitState.azimuth }}°</span>
          <span>Tilt {{ orbitState.elevation }}°</span>
          <span>Zoom {{ orbitState.distance }}</span>
          <span v-if="debugCamera">Target {{ orbitState.target.x }}, {{ orbitState.target.y }}, {{ orbitState.target.z }}</span>
          <span v-if="debugCamera && groundHit.hit" class="ground-hit">Ground {{ groundHit.x }}, {{ groundHit.y }}, {{ groundHit.z }}</span>
          <span v-if="debugCamera && !groundHit.hit" class="ground-miss">Ground (no hit)</span>
        </div>
        <div class="orbit-actions">
          <label class="overlay-toggle" title="Free-fly camera: right-drag to pan, scroll to zoom">
            <input v-model="debugCamera" type="checkbox" @change="onDebugCameraToggle" />
            <span>Debug Cam</span>
          </label>
          <label class="overlay-toggle" title="Slowly spin the model">
            <input v-model="autoRotate" type="checkbox" @change="onAutoRotateToggle" />
            <span>Auto-rotate</span>
          </label>
          <button class="overlay-btn" title="Reset camera to initial position" @click="resetCamera">
            Reset
          </button>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {
  Mesh,
  MeshBasicMaterial,
  SphereGeometry,
  type Scene as ThreeScene,
} from 'three'

import { DatLoader } from '~/lib/loader/datLoader'
import { DatParser, SectionHeader } from '~/lib/resource/datParser'
import { ByteReader } from '~/lib/resource/byteReader'
import {
  DatId,
  DirectoryResource,
  type InfoDefinition,
  InfoResource,
  SkeletonMeshResource,
  SkeletonResource,
  TextureResource,
} from '~/lib/resource/datResource'
import type { EquipmentModelTable } from '~/lib/resource/table/equipmentModelTable'
import { createResourceTableRuntime } from '~/lib/resource/table/runtime'
import type { ResourceTableRuntime } from '~/lib/resource/table/runtime'
import { initZoneDecrypt } from '~/lib/resource/table/zoneDecrypt'
import { getZoneDatPath, nationIndexToZoneKey, STARTING_ZONES } from '~/lib/resource/table/zoneTables'
import { ZoneResource } from '~/lib/resource/zoneResource'
import { findGroundY } from '~/lib/resource/zoneCollider'
import type { LightingParams, FogParams } from '~/lib/renderer/lights'
import { noOpFog, noOpLighting } from '~/lib/renderer/lights'
import { ThreeRenderer } from '~/lib/renderer/threeRenderer'
import { ZoneRenderer } from '~/lib/renderer/zoneRenderer'
import { EnvironmentManager } from '~/lib/renderer/environmentManager'
import { SkyboxRenderer } from '~/lib/renderer/skyboxRenderer'
import { Actor, ActorId, type ActorState } from '~/lib/runtime/actor'
import { NoOpActorController } from '~/lib/runtime/actorController'
import { ActorModel, SlotVisibilityOverride, type RuntimeActor } from '~/lib/runtime/actorModel'
import {
  equipmentSlotOptions,
  getDefaultPcEquipmentModelId,
  getPcFaceModelIds,
  getXiCameraFrame,
  getPcEquipmentModelPaths,
  getPcEquipmentSlotCount,
  parameterizeAnimationId,
  type PcEquipmentPath,
  resolvePcSceneResourcePaths,
  resolveRaceGenderConfig,
} from '~/lib/runtime/basicPc'
import { ItemModelSlot as RuntimeItemModelSlot, type Model } from '~/lib/runtime/model'
import { collectByTypeRecursive } from '~/lib/runtime/resourceTree'
import { RuntimeScene } from '~/lib/runtime/scene'
import { LoopParams, TransitionParams } from '~/lib/runtime/skeletonAnimator'

const canvasRef = ref<HTMLCanvasElement | null>(null)

const isLoading = ref(false)
const errorMessage = ref<string | null>(null)
const modelPath = ref<string | null>(null)
const animationPath = ref<string | null>(null)
const animationId = ref<string | null>(null)

// --- Time-of-day slider (0-1440 minutes) ---
const timeOfDayMinutes = ref(720) // Default: noon (12:00)
const timeOfDayLabel = computed(() => {
  const h = Math.floor(timeOfDayMinutes.value / 60)
  const m = Math.floor(timeOfDayMinutes.value % 60)
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
})

// --- Character creation selectors ---
type Race = 'Hume' | 'Elvaan' | 'Tarutaru' | 'Mithra' | 'Galka'
type Gender = 'Male' | 'Female'

const raceOptions: readonly { value: Race, label: string }[] = [
  { value: 'Hume', label: 'Hume' },
  { value: 'Elvaan', label: 'Elvaan' },
  { value: 'Tarutaru', label: 'Tarutaru' },
  { value: 'Mithra', label: 'Mithra' },
  { value: 'Galka', label: 'Galka' },
]

const genderlesRaces: readonly Race[] = ['Mithra', 'Galka']

const faceOptions = Array.from({ length: 8 }, (_, i) => ({
  value: i + 1,
  label: `Face ${i + 1}`,
}))

const hairColorOptions: readonly { value: number, label: string }[] = [
  { value: 0, label: 'Hair A' },
  { value: 1, label: 'Hair B' },
]

const sizeOptions: readonly { value: number, label: string }[] = [
  { value: 0, label: 'Small' },
  { value: 1, label: 'Medium' },
  { value: 2, label: 'Large' },
]

const startingJobOptions: readonly { value: number, label: string }[] = [
  { value: 1, label: 'Warrior (WAR)' },
  { value: 2, label: 'Monk (MNK)' },
  { value: 3, label: 'White Mage (WHM)' },
  { value: 4, label: 'Black Mage (BLM)' },
  { value: 5, label: 'Red Mage (RDM)' },
  { value: 6, label: 'Thief (THF)' },
]

const nationOptions: readonly { value: number, label: string }[] = [
  { value: 0, label: "San d'Oria" },
  { value: 1, label: 'Bastok' },
  { value: 2, label: 'Windurst' },
]

const selectedRace = ref<Race>('Hume')
const selectedGender = ref<Gender>('Male')
const selectedFace = ref(1)
const selectedHairColor = ref(0)
const selectedSize = ref(1)
const selectedJob = ref(1)
const selectedNation = ref(0)

const hasGenderChoice = computed(() => !genderlesRaces.includes(selectedRace.value))

const genderOptions = computed<readonly { value: Gender, label: string }[]>(() => {
  if (!hasGenderChoice.value) return []
  return [
    { value: 'Male', label: 'Male' },
    { value: 'Female', label: 'Female' },
  ]
})

// Derive the RaceGender key used by the existing runtime (e.g. 'HumeMale', 'Mithra')
const selectedRaceGender = computed(() => {
  const race = selectedRace.value
  if (race === 'Mithra' || race === 'Galka') return race
  return `${race}${selectedGender.value}`
})

// Derive face equipment model ID from Face (1-8) + Hair (0-1)
const computedFaceModelId = computed(() => {
  return ((selectedFace.value - 1) * 2) + selectedHairColor.value
})

// Equipment grid excludes Face (it's driven by character selectors now)
const equipmentGridOptions = computed(() =>
  equipmentSlotOptions.filter((option) => option.key !== 'Face'),
)

const selectedEquipmentIds = reactive<Record<string, string>>(
  Object.fromEntries(equipmentSlotOptions.map((option) => [option.key, '0'])),
)
const equipmentOptionsBySlot = ref<Record<string, readonly number[]>>(
  Object.fromEntries(equipmentSlotOptions.map((option) => [option.key, [0]])),
)
const equipmentPathBySlot = ref<Record<string, string | null>>(
  Object.fromEntries(equipmentSlotOptions.map((option) => [option.key, null])),
)
const showWireframe = ref(false)
const showBones = ref(false)
const autoRotate = ref(false)
const debugCamera = ref(false)
const orbitState = reactive({ azimuth: 0, elevation: 0, distance: 0, target: { x: 0, y: 0, z: 0 } })
const groundHit = reactive({ x: 0, y: 0, z: 0, hit: false })
const drawDebug = ref('idle')
const modelDebug = ref('')
const slotDebug = ref('')
const faceDebug = ref('')
const faceSectionDebug = ref('')
const faceParsedDebug = ref('')

function summarizeMeshResources(resources: readonly import('~/lib/resource/datResource').SkeletonMeshResource[]): string {
  let meshCount = 0
  let vertexCount = 0
  let maxAbs = 0
  let maxJointIndex = -1

  for (const resource of resources) {
    for (const mesh of resource.meshes) {
      meshCount += 1
      vertexCount += mesh.vertices.length
      for (const vertex of mesh.vertices) {
        maxAbs = Math.max(
          maxAbs,
          Math.abs(vertex.position0.x),
          Math.abs(vertex.position0.y),
          Math.abs(vertex.position0.z),
          Math.abs(vertex.position1.x),
          Math.abs(vertex.position1.y),
          Math.abs(vertex.position1.z),
        )

        if (vertex.jointIndex0 != null) {
          maxJointIndex = Math.max(maxJointIndex, vertex.jointIndex0)
        }
        if (vertex.jointIndex1 != null) {
          maxJointIndex = Math.max(maxJointIndex, vertex.jointIndex1)
        }
      }
    }
  }

  return `meshRes=${resources.length} meshes=${meshCount} verts=${vertexCount} maxAbs=${maxAbs.toFixed(3)} maxJoint=${maxJointIndex}`
}

function countMeshes(root: DirectoryResource): number {
  return collectByTypeRecursive(root, SkeletonMeshResource).length
}

function countTextures(root: DirectoryResource): number {
  return collectByTypeRecursive(root, TextureResource).length
}

async function dumpDatSections(filePath: string): Promise<string> {
  const normalizedPath = filePath.replace(/^\/+/, '')
  const baseUrl = datBaseUrl.replace(/\/+$/, '')
  const response = await fetch(`${baseUrl}/${normalizedPath}`, { headers: datHeaders })
  if (!response.ok) {
    return `dumpErr=${response.status}`
  }

  const bytes = new Uint8Array(await response.arrayBuffer())
  const reader = new ByteReader(bytes, normalizedPath)
  const counts = new Map<string, number>()

  while (reader.hasMore()) {
    const header = new SectionHeader()
    header.read(reader)

    const typeName = header.sectionType.resourceType.name || `type_${header.sectionType.code}`
    const key = `${header.sectionType.code.toString(16).padStart(2, '0')}:${typeName}`
    counts.set(key, (counts.get(key) ?? 0) + 1)

    if (header.sectionSize <= 0) {
      break
    }
    reader.offsetFrom(header, header.sectionSize)
  }

  const summary = Array.from(counts.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([key, count]) => `${key}x${count}`)
    .join('|')

  console.info('[face-dat-sections]', normalizedPath, summary)
  return summary
}

function summarizeParsedTypes(root: DirectoryResource): string {
  const counts = new Map<string, number>()
  const stack: DirectoryResource[] = [root]

  while (stack.length > 0) {
    const next = stack.pop()
    if (!next) {
      continue
    }

    const childrenByType = (next as unknown as { childrenByType?: Map<Function, Map<string, unknown>> }).childrenByType
    if (childrenByType) {
      for (const [ctor, entries] of childrenByType.entries()) {
        const key = ctor.name || 'Unknown'
        counts.set(key, (counts.get(key) ?? 0) + entries.size)
      }
    }

    stack.push(...next.getSubDirectories())
  }

  return Array.from(counts.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([name, count]) => `${name}x${count}`)
    .join('|')
}

const config = useRuntimeConfig()
const datBaseUrl = config.public.datBaseUrl as string
const datAccessToken = config.public.datAccessToken as string
const datHeaders: HeadersInit | undefined = datAccessToken
  ? { Authorization: `Bearer ${datAccessToken}` }
  : undefined

const datLoader = new DatLoader<DirectoryResource>({
  baseUrl: datBaseUrl,
  headers: datHeaders,
  parseDat: (resourceName, bytes) => DatParser.parse(resourceName, bytes),
})

let renderer: ThreeRenderer | null = null
let runtimeScene: RuntimeScene | null = null
let actor: Actor | null = null
let frameHandle = 0
let previousFrameTime = 0
let resourceTableRuntime: ResourceTableRuntime | null = null
let equipmentModelTable: EquipmentModelTable | null = null
let initializedRaceName: string | null = null
let orbitControls: OrbitControlsHandle | null = null
let zoneRenderer: ZoneRenderer | null = null
let skyboxRenderer: SkyboxRenderer | null = null
const envManager = new EnvironmentManager()
let zoneDecryptInitialized = false
let currentZoneId: number | null = null
let currentCollisionMap: import('~/lib/resource/zoneResource').CollisionMap | null = null
let debugMarker: Mesh | null = null

// Zone DAT loader (parses with zoneResource: true)
const zoneDatLoader = new DatLoader<DirectoryResource>({
  baseUrl: datBaseUrl,
  headers: datHeaders,
  parseDat: (resourceName, bytes) => DatParser.parse(resourceName, bytes, { zoneResource: true }),
})

const CANVAS_WIDTH = 1024
const CANVAS_HEIGHT = 768

const resizeCanvas = (): void => {
  if (!renderer) {
    return
  }

  renderer.setSize(CANVAS_WIDTH, CANVAS_HEIGHT)
}

function onTimeOfDayChange(): void {
  if (renderer) {
    applyEnvironment(renderer.scene)
  }
}

function onTimeOfDayInput(event: Event): void {
  const raw = (event.target as HTMLInputElement).value.trim()
  const match = raw.match(/^(\d{1,2}):(\d{2})$/)
  if (!match) return
  const h = parseInt(match[1]!, 10)
  const m = parseInt(match[2]!, 10)
  if (h < 0 || h > 23 || m < 0 || m > 59) return
  timeOfDayMinutes.value = h * 60 + m
  onTimeOfDayChange()
}

function stopLoop(): void {
  if (frameHandle !== 0) {
    cancelAnimationFrame(frameHandle)
    frameHandle = 0
  }
}

function disposeScene(): void {
  stopLoop()

  if (renderer) {
    removeDebugMarker(renderer.scene)
    if (zoneRenderer) {
      zoneRenderer.dispose(renderer.scene)
    }
    if (skyboxRenderer) {
      skyboxRenderer.dispose(renderer.scene)
    }
  }
  zoneRenderer = null
  skyboxRenderer = null
  currentZoneId = null
  currentCollisionMap = null
  zoneModelLighting = null
  zoneModelFog = null

  orbitControls?.dispose()
  orbitControls = null
  renderer?.dispose()
  renderer = null
  runtimeScene = null
  actor = null
  previousFrameTime = 0
}

let debugRaycastFrame = 0

/**
 * Cast a ray from the camera through the look direction, find where it
 * hits the ground, and draw a line from camera to the ground hit point.
 * Throttled to every 6 frames (~10 Hz) to keep perf reasonable.
 */
function updateDebugRaycast(scene: ThreeScene): void {
  if (!debugCamera.value || !currentCollisionMap || !renderer) {
    removeDebugMarker(scene)
    groundHit.hit = false
    return
  }

  debugRaycastFrame++
  if (debugRaycastFrame % 6 !== 0) return

  const cam = renderer.camera
  const cx = cam.position.x
  const cy = cam.position.y
  const cz = cam.position.z

  // Look direction: from camera toward orbit target
  const lx = orbitState.target.x - cx
  const ly = orbitState.target.y - cy
  const lz = orbitState.target.z - cz
  const len = Math.hypot(lx, ly, lz)
  if (len < 1e-6) { groundHit.hit = false; return }
  const dx = lx / len
  const dy = ly / len
  const dz = lz / len

  // March along the ray to find the closest ground intersection
  let bestHitY: number | null = null
  let bestHitX = 0
  let bestHitZ = 0
  let bestDist = Infinity

  for (let t = 5; t <= 200; t += 5) {
    const sx = cx + dx * t
    const sz = cz + dz * t
    const gy = findGroundY(currentCollisionMap, sx, sz)
    if (gy === null) continue

    const rayY = cy + dy * t
    const yDiff = Math.abs(rayY - gy)
    if (yDiff < bestDist) {
      bestDist = yDiff
      bestHitY = gy
      bestHitX = sx
      bestHitZ = sz
    }
    if (yDiff < 3) break
  }

  if (bestHitY !== null) {
    groundHit.x = +bestHitX.toFixed(1)
    groundHit.y = +bestHitY.toFixed(2)
    groundHit.z = +bestHitZ.toFixed(1)
    groundHit.hit = true
    drawDebugMarker(scene, bestHitX, bestHitY, bestHitZ)
  } else {
    groundHit.hit = false
    removeDebugMarker(scene)
  }
}

function drawDebugMarker(scene: ThreeScene, x: number, y: number, z: number): void {
  if (debugMarker) {
    debugMarker.position.set(x, y, z)
  } else {
    const geo = new SphereGeometry(0.4, 12, 8)
    const mat = new MeshBasicMaterial({ color: 0x00ff44, depthTest: false, transparent: true, opacity: 0.8 })
    debugMarker = new Mesh(geo, mat)
    debugMarker.renderOrder = 9999
    debugMarker.frustumCulled = false
    scene.add(debugMarker)
  }
}

function removeDebugMarker(scene: ThreeScene): void {
  if (debugMarker) {
    scene.remove(debugMarker)
    debugMarker.geometry.dispose()
    ;(debugMarker.material as MeshBasicMaterial).dispose()
    debugMarker = null
  }
}

function applyDebugView(): void {
  renderer?.setDebugView({
    showWireframe: showWireframe.value,
    showBones: showBones.value,
  })
}

function onAutoRotateToggle(): void {
  orbitControls?.setAutoRotate(autoRotate.value)
}

function onDebugCameraToggle(): void {
  orbitControls?.setPan(debugCamera.value)
}

function resetCamera(): void {
  orbitControls?.reset()
  autoRotate.value = false
  orbitControls?.setAutoRotate(false)
}

function createStaticModel(
  meshRoot: DirectoryResource,
  battleAnimRoot: DirectoryResource,
  animationRoots: readonly DirectoryResource[],
  equipmentBySlot: ReadonlyMap<RuntimeItemModelSlot, DirectoryResource>,
): Model {
  const equipmentRoots = Array.from(equipmentBySlot.values())

  return {
    isReadyToDraw: () => true,
    getMeshResources: () => [meshRoot, ...equipmentRoots],
    getSkeletonResource: () => {
      const local = collectByTypeRecursive(meshRoot, SkeletonResource)
      if (local.length > 0) {
        return local[0] ?? null
      }

      const fromAnimation = animationRoots.flatMap((root) => collectByTypeRecursive(root, SkeletonResource))
      return fromAnimation[0] ?? null
    },
    getAnimationDirectories: () => [meshRoot, ...animationRoots, ...equipmentRoots],
    getMainBattleAnimationDirectory: () => battleAnimRoot,
    getSubBattleAnimationDirectory: () => null,
    getEquipmentModelResource: (modelSlot) => equipmentBySlot.get(modelSlot) ?? null,
    getMovementInfo: (): InfoDefinition | null => {
      const feetInfo = equipmentBySlot
        .get(RuntimeItemModelSlot.Feet)
        ?.getNullableChildAs(DatId.info, InfoResource)
        ?.infoDefinition

      return feetInfo ?? null
    },
    getMainWeaponInfo: (): InfoDefinition | null => null,
    getSubWeaponInfo: (): InfoDefinition | null => null,
    getRangedWeaponInfo: (): InfoDefinition | null => null,
    getBlurConfig: () => null,
    getScale: () => 1,
  }
}

function parseSelectedModelId(optionKey: string): number {
  const value = Number.parseInt(selectedEquipmentIds[optionKey] ?? '0', 10)
  return Number.isNaN(value) ? 0 : value
}

function toRuntimeSlot(path: PcEquipmentPath): RuntimeItemModelSlot | null {
  switch (path.slot.name) {
    case 'Face':
      return RuntimeItemModelSlot.Face
    case 'Head':
      return RuntimeItemModelSlot.Head
    case 'Body':
      return RuntimeItemModelSlot.Body
    case 'Hands':
      return RuntimeItemModelSlot.Hands
    case 'Legs':
      return RuntimeItemModelSlot.Legs
    case 'Feet':
      return RuntimeItemModelSlot.Feet
    default:
      return null
  }
}

// Store last loaded zone environment for actor lighting
let zoneModelLighting: LightingParams | null = null
let zoneModelFog: FogParams | null = null

/**
 * Apply environment at the current time-of-day to all zone meshes (per-object),
 * the actor lighting, the skybox, and the renderer clear color.
 */
function applyEnvironment(scene: ThreeScene): void {
  const time = timeOfDayMinutes.value
  const env = envManager.resolve(time)

  // Per-object terrain lighting
  if (zoneRenderer) {
    zoneRenderer.applyLightingFromEnvManager(envManager, time)
  }

  // Model lighting for the actor
  const model = envManager.resolveModelLighting(time)
  zoneModelLighting = model.lighting
  zoneModelFog = model.fog

  // Skybox
  if (skyboxRenderer && env.skyBox.radius > 0) {
    skyboxRenderer.build(scene, env.skyBox)
  }

  // Clear color from environment
  if (renderer) {
    const cc = env.clearColor
    renderer.setClearColor(cc.r, cc.g, cc.b)
  }

  // Draw distance culling
  if (zoneRenderer && renderer) {
    zoneRenderer.updateVisibility(renderer.camera, env.drawDistance)
  }
}

// ─── Zone Loading ────────────────────────────────────────────────────────────

/**
 * Load and render a zone by its nation index.
 * Returns the character's ground Y position (or fallback).
 */
async function loadZone(
  nationIndex: number,
  scene: import('three').Scene,
): Promise<{ groundY: number, startX: number, startZ: number }> {
  if (!resourceTableRuntime) {
    throw new Error('Resource table runtime not initialized')
  }

  const zoneKey = nationIndexToZoneKey(nationIndex)
  const zoneConfig = STARTING_ZONES[zoneKey]
  if (!zoneConfig) {
    throw new Error(`No zone config for nation index ${nationIndex}`)
  }

  // Initialize zone decryption tables once
  if (!zoneDecryptInitialized) {
    initZoneDecrypt(resourceTableRuntime.mainDll)
    zoneDecryptInitialized = true
  }

  // Resolve zone DAT path
  const zoneDatPath = getZoneDatPath(zoneConfig.zoneId, resourceTableRuntime.fileTableManager)
  if (!zoneDatPath) {
    throw new Error(`Could not resolve DAT path for zone ${zoneConfig.zoneId}`)
  }

  console.info(`[zone] Loading zone ${zoneConfig.name} (${zoneConfig.zoneId}) from ${zoneDatPath}`)

  // Load and parse the zone DAT
  const zoneDirectory = await zoneDatLoader.load(zoneDatPath)

  // Extract zone resources from the parsed directory tree
  const zoneResources = collectByTypeRecursive(zoneDirectory, ZoneResource)

  const zoneResource = zoneResources[0] ?? null

  if (!zoneResource) {
    console.warn('[zone] No ZoneResource found in DAT')
    return { groundY: zoneConfig.fallbackY, startX: zoneConfig.startPosition.x, startZ: zoneConfig.startPosition.z }
  }

  console.info(`[zone] Found ${zoneResource.zoneObjects.length} zone objects, collision map: ${zoneResource.collisionMap ? 'yes' : 'no'}`)

  // Dispose previous zone if any
  if (!zoneRenderer) {
    zoneRenderer = new ZoneRenderer()
  }

  // Build zone meshes
  zoneRenderer.buildFromZoneData(scene, {
    zoneResource,
    directory: zoneDirectory,
  })

  console.info(`[zone] Built ${zoneRenderer.objectCount} objects, ${zoneRenderer.totalMeshCount} meshes`)

  // Initialize environment manager from zone directory tree
  envManager.init(zoneDirectory)
  console.info(`[zone] Environment manager initialized (envIds: ${envManager.getAvailableEnvIds().join(', ')})`)

  // Initialize skybox renderer
  if (!skyboxRenderer) {
    skyboxRenderer = new SkyboxRenderer()
  }

  // Apply environment at current time-of-day
  applyEnvironment(scene)
  const env = envManager.resolve(timeOfDayMinutes.value)
  console.info(`[zone] Applied environment (indoor=${env.indoors}, drawDistance=${env.drawDistance})`)

  // Find ground Y via collision raycast
  let groundY = zoneConfig.fallbackY
  if (zoneResource.collisionMap) {
    const raycastY = findGroundY(
      zoneResource.collisionMap,
      zoneConfig.startPosition.x,
      zoneConfig.startPosition.z,
    )
    if (raycastY !== null) {
      groundY = raycastY
      console.info(`[zone] Ground Y at (${zoneConfig.startPosition.x}, ${zoneConfig.startPosition.z}): ${groundY}`)
    } else {
      console.warn(`[zone] Raycast missed, using fallback Y=${zoneConfig.fallbackY}`)
    }
  }

  currentZoneId = zoneConfig.zoneId
  currentCollisionMap = zoneResource.collisionMap
  return { groundY, startX: zoneConfig.startPosition.x, startZ: zoneConfig.startPosition.z }
}

async function loadScene(): Promise<void> {
  const canvas = canvasRef.value
  if (!canvas) {
    return
  }

  disposeScene()
  timeOfDayMinutes.value = 720 // Reset to noon
  isLoading.value = true
  errorMessage.value = null

  try {
    if (!resourceTableRuntime) {
      resourceTableRuntime = createResourceTableRuntime({
        baseUrl: datBaseUrl,
        headers: datHeaders,
        fileTableCount: 1,
      })
      await resourceTableRuntime.preloadAll()
      equipmentModelTable = await resourceTableRuntime.createEquipmentModelTable()
    }

    if (!equipmentModelTable) {
      throw new Error('Equipment model table runtime was not initialized')
    }

    const table = equipmentModelTable
    const selectedConfig = resolveRaceGenderConfig(selectedRaceGender.value)
    const isNewRaceInitialization = initializedRaceName !== selectedConfig.name
    const paths = resolvePcSceneResourcePaths(resourceTableRuntime, selectedConfig)
    modelPath.value = paths.modelPath
    animationPath.value = paths.animationPath

    const nextOptionsBySlot: Record<string, readonly number[]> = {}
    for (const option of equipmentSlotOptions) {
      const modelIds = option.key === 'Face'
        ? getPcFaceModelIds()
        : Array.from({ length: Math.max(1, getPcEquipmentSlotCount(table, selectedConfig, option.slot)) }, (_, index) => index)
      const count = modelIds.length
      nextOptionsBySlot[option.key] = modelIds

      const defaultModelId = getDefaultPcEquipmentModelId(option.slot, count)
      if (isNewRaceInitialization) {
        selectedEquipmentIds[option.key] = String(defaultModelId)
        continue
      }

      const selectedModelId = parseSelectedModelId(option.key)
      if (selectedModelId < 0 || selectedModelId >= count) {
        selectedEquipmentIds[option.key] = String(defaultModelId)
      }
    }
    equipmentOptionsBySlot.value = nextOptionsBySlot

    const selectedBySlot = new Map(
      equipmentSlotOptions.map((option) => [option.slot, parseSelectedModelId(option.key)] as const),
    )
    const equipmentPaths = getPcEquipmentModelPaths(table, selectedConfig, selectedBySlot)

    const nextPathBySlot = Object.fromEntries(
      equipmentSlotOptions.map((option) => [option.key, null]),
    ) as Record<string, string | null>
    for (const equipmentPath of equipmentPaths) {
      nextPathBySlot[equipmentPath.slot.name] = equipmentPath.modelPath
    }
    equipmentPathBySlot.value = nextPathBySlot

    const [meshRoot, animRoot, upperAnimRoot, skirtAnimRoot] = await Promise.all([
      datLoader.load(paths.modelPath),
      datLoader.load(paths.animationPath),
      datLoader.load(paths.upperBodyAnimationPath),
      datLoader.load(paths.skirtAnimationPath),
    ])

    const loadedEquipment = await Promise.all(
      equipmentPaths.map(async (equipmentPath) => ({
        equipmentPath,
        root: await datLoader.load(equipmentPath.modelPath),
      })),
    )

    const equipmentBySlot = new Map<RuntimeItemModelSlot, DirectoryResource>()
    for (const entry of loadedEquipment) {
      const runtimeSlot = toRuntimeSlot(entry.equipmentPath)
      if (runtimeSlot === null) {
        continue
      }

      equipmentBySlot.set(runtimeSlot, entry.root)
    }

    const selectedFaceRoot = equipmentBySlot.get(RuntimeItemModelSlot.Face)
    const faceSlot = equipmentSlotOptions.find((option) => option.key === 'Face')?.slot
    const selectedFaceId = parseSelectedModelId('Face')
    const faceOptions = equipmentOptionsBySlot.value.Face ?? []
    const validFacePaths = faceSlot
      ? faceOptions
        .map((id) => ({ id, path: table.getItemModelPath(selectedConfig, faceSlot, id) }))
        .filter((entry) => entry.path)
      : []

    faceDebug.value = `faceSel=${selectedFaceId} facePath=${equipmentPathBySlot.value.Face ?? '-'} faceValid=${validFacePaths.length} faceFirst=${validFacePaths[0]?.id ?? '-'}:${validFacePaths[0]?.path ?? '-'}`
    if (equipmentPathBySlot.value.Face) {
      faceSectionDebug.value = await dumpDatSections(equipmentPathBySlot.value.Face)
    }
    if (selectedFaceRoot) {
      faceParsedDebug.value = summarizeParsedTypes(selectedFaceRoot)
    }

    if (selectedFaceRoot && countMeshes(selectedFaceRoot) === 0 && countTextures(selectedFaceRoot) === 0) {
      if (faceSlot) {
        for (const candidateId of faceOptions) {
          if (candidateId <= 0 || candidateId === selectedFaceId) {
            continue
          }

          const candidatePath = table.getItemModelPath(selectedConfig, faceSlot, candidateId)
          if (!candidatePath) {
            continue
          }

          const candidateRoot = await datLoader.load(candidatePath)
          if (countMeshes(candidateRoot) === 0 && countTextures(candidateRoot) === 0) {
            continue
          }

          equipmentBySlot.set(RuntimeItemModelSlot.Face, candidateRoot)
          selectedEquipmentIds.Face = String(candidateId)
          equipmentPathBySlot.value.Face = candidatePath
          faceDebug.value = `faceSel=${candidateId} facePath=${candidatePath} faceValid=${validFacePaths.length} faceFirst=${validFacePaths[0]?.id ?? '-'}:${validFacePaths[0]?.path ?? '-'}`
          faceSectionDebug.value = await dumpDatSections(candidatePath)
          faceParsedDebug.value = summarizeParsedTypes(candidateRoot)
          break
        }
      }
    }

    slotDebug.value = [
      `face=${countMeshes(equipmentBySlot.get(RuntimeItemModelSlot.Face) ?? meshRoot)}`,
      `faceTex=${countTextures(equipmentBySlot.get(RuntimeItemModelSlot.Face) ?? meshRoot)}`,
      `head=${countMeshes(equipmentBySlot.get(RuntimeItemModelSlot.Head) ?? meshRoot)}`,
      `body=${countMeshes(equipmentBySlot.get(RuntimeItemModelSlot.Body) ?? meshRoot)}`,
      `hands=${countMeshes(equipmentBySlot.get(RuntimeItemModelSlot.Hands) ?? meshRoot)}`,
      `legs=${countMeshes(equipmentBySlot.get(RuntimeItemModelSlot.Legs) ?? meshRoot)}`,
      `feet=${countMeshes(equipmentBySlot.get(RuntimeItemModelSlot.Feet) ?? meshRoot)}`,
      `slots=${Array.from(equipmentBySlot.keys()).join(',')}`,
    ].join(' ')

    // Default position (overridden by zone loading below)
    let charX = 0
    let charY = 0
    let charZ = 0

    const actorState: ActorState = {
      id: new ActorId(1),
      position: { x: charX, y: charY, z: charZ },
      velocity: { x: 0, y: 0, z: 0 },
      rotation: 0,
      visible: true,
      movementSpeed: 0,
    }

    const runtimeActor: RuntimeActor = {
      isDisplayEngagedOrEngaging: () => false,
      getMount: () => null,
    }

    actor = new Actor(actorState, new NoOpActorController(), () => null)
    const model = createStaticModel(meshRoot, animRoot, [upperAnimRoot, skirtAnimRoot], equipmentBySlot)
    const actorModel = new ActorModel(runtimeActor, model)
    actorModel.setDefaultModelVisibility(new SlotVisibilityOverride(RuntimeItemModelSlot.Body, false, false))
    actor.actorModel = actorModel
    modelDebug.value = summarizeMeshResources(actorModel.getMeshResources())

    const resolvedAnimationId = 'idl?'
    animationId.value = resolvedAnimationId
    if (resolvedAnimationId) {
      actorModel.setSkeletonAnimation(
        new DatId(parameterizeAnimationId(resolvedAnimationId)),
        model.getAnimationDirectories(),
        LoopParams.lowPriorityLoop(),
        new TransitionParams(0, 0),
      )
    }

    actor.update(0)

    runtimeScene = new RuntimeScene({
      resolveActorLighting: () => ({
        lightingParams: zoneModelLighting ?? noOpLighting,
        fogParams: zoneModelFog ?? noOpFog,
        pointLights: [],
      }),
    })

    renderer = new ThreeRenderer({ canvas })
    applyDebugView()
    resizeCanvas()

    // Load zone and place character on ground
    try {
      const zoneResult = await loadZone(selectedNation.value, renderer.scene)
      charX = zoneResult.startX
      charY = zoneResult.groundY
      charZ = zoneResult.startZ
      actorState.position.x = charX
      actorState.position.y = charY
      actorState.position.z = charZ

      // Increase far plane for zone geometry
      renderer.camera.far = 10000
      renderer.camera.updateProjectionMatrix()
    } catch (zoneError) {
      console.warn('[zone] Failed to load zone:', zoneError)
      // Continue without zone -- character renders at origin
    }

    const skeletonHeight = actorModel.getSkeleton()?.resource.size.y ?? 1.75
    const frame = getXiCameraFrame(skeletonHeight)

    // Position camera relative to character's world position
    renderer.camera.position.set(
      charX + frame.position.x,
      charY + frame.position.y,
      charZ + frame.position.z,
    )
    const camTarget = {
      x: charX + frame.target.x,
      y: charY + frame.target.y,
      z: charZ + frame.target.z,
    }
    renderer.camera.lookAt(camTarget.x, camTarget.y, camTarget.z)

    orbitControls?.dispose()
    orbitControls = useOrbitControls({
      camera: renderer.camera,
      domElement: canvas,
      target: camTarget,
      maxDistance: 500,
      enablePan: debugCamera.value,
      panSpeed: 2.0,
    })

    previousFrameTime = performance.now()

    const tick = (time: number): void => {
      if (!renderer || !runtimeScene || !actor) {
        return
      }

      const elapsedMs = Math.max(0, time - previousFrameTime)
      previousFrameTime = time
      const elapsedFrames = elapsedMs / (1000 / 60)

      actor.update(elapsedFrames)

      const commands = runtimeScene.buildDrawCommands([actor], {
        cameraPosition: {
          x: renderer.camera.position.x,
          y: renderer.camera.position.y,
          z: renderer.camera.position.z,
        },
        maxDistance: 2000,
        maxVisible: 1,
      })

      // Update zone wind animation (foliage sway)
      if (zoneRenderer) {
        zoneRenderer.updateWind(time / 1000)
      }

      const state = orbitControls?.update()
      if (state) {
        orbitState.azimuth = state.azimuth
        orbitState.elevation = state.elevation
        orbitState.distance = state.distance
        orbitState.target = state.target
      }
      updateDebugRaycast(renderer.scene)
      renderer.render(commands)
      const totalMeshResources = commands.reduce((sum, command) => sum + command.meshes.length, 0)
      const totalMeshes = commands.reduce(
        (sum, command) => sum + command.meshes.reduce((meshSum, resource) => meshSum + resource.meshes.length, 0),
        0,
      )
      const stats = renderer.getFrameStats()
      const skel = commands[0]?.skeleton
      const skeletonInfo = skel ? `skel=1 joints=${skel.joints.length}` : 'skel=0 joints=0'
      const hiddenSlots = Array.from(actor.actorModel?.getHiddenSlotIds() ?? []).sort((a, b) => a - b).join(',')
      drawDebug.value = `cmd=${commands.length} meshRes=${totalMeshResources} meshes=${totalMeshes} calls=${stats.calls} tris=${stats.triangles} prog=${stats.programs} hidden=[${hiddenSlots}] ${skeletonInfo} ${modelDebug.value} ${slotDebug.value} ${faceDebug.value} faceSec=${faceSectionDebug.value} faceParsed=${faceParsedDebug.value}`
      frameHandle = requestAnimationFrame(tick)
    }

    frameHandle = requestAnimationFrame(tick)
    initializedRaceName = selectedConfig.name
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    errorMessage.value = message
  } finally {
    isLoading.value = false
  }
}

// --- Watchers for character selectors ---

let suppressCharacterWatchers = false

function syncFaceModelId(): void {
  selectedEquipmentIds.Face = String(computedFaceModelId.value)
}

function resetFaceAndReload(): void {
  suppressCharacterWatchers = true
  selectedFace.value = 1
  selectedHairColor.value = 0
  suppressCharacterWatchers = false
  syncFaceModelId()
  loadScene()
}

// When race changes, reset dependent selectors and reload
watch(selectedRace, (race) => {
  if (race === 'Mithra' || race === 'Galka') {
    suppressCharacterWatchers = true
    selectedGender.value = 'Male'
    suppressCharacterWatchers = false
  }
  resetFaceAndReload()
})

// When gender changes, reset face/hair and reload
watch(selectedGender, () => {
  if (suppressCharacterWatchers || !hasGenderChoice.value) return
  resetFaceAndReload()
})

// When face or hair changes, sync the equipment model ID and reload
watch([selectedFace, selectedHairColor], () => {
  if (suppressCharacterWatchers) return
  syncFaceModelId()
  loadScene()
})

// When nation changes, reload scene with new zone
watch(selectedNation, () => {
  loadScene()
})

onMounted(async () => {
  syncFaceModelId()
  await loadScene()
})

onUnmounted(() => {
  disposeScene()
})
</script>

<style scoped>
.viewer-shell {
  min-height: 100vh;
  padding: clamp(1rem, 2vw, 2rem);
  display: grid;
  gap: 1rem;
  grid-template-columns: minmax(260px, 360px) 1fr;
  background:
    radial-gradient(circle at 10% 10%, rgba(239, 228, 199, 0.7), transparent 60%),
    radial-gradient(circle at 90% 25%, rgba(183, 208, 214, 0.65), transparent 50%),
    linear-gradient(180deg, #fffdf8, #f0f5f7);
  font-family: 'Spectral', 'Georgia', serif;
}

.viewer-panel {
  border: 1px solid #d7ddd3;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(4px);
  padding: 1rem;
  display: grid;
  gap: 0.75rem;
  align-content: start;
  max-height: 100vh;
  overflow-y: auto;
}

.viewer-header h1 {
  margin: 0;
  font-size: 1.65rem;
  letter-spacing: 0.03em;
  color: #223132;
}

.viewer-header p {
  margin: 0.35rem 0 0;
  color: #334446;
  line-height: 1.35;
}

.eyebrow {
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 0.09em;
  font-size: 0.75rem;
  color: #587171;
}

.panel-section {
  border-top: 1px solid #d7ddd3;
  padding-top: 0.75rem;
}

.section-heading {
  margin: 0 0 0.5rem;
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: #587171;
  font-weight: 600;
  cursor: pointer;
  list-style: none;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  user-select: none;
}

.section-heading::-webkit-details-marker {
  display: none;
}

.section-heading::before {
  content: '';
  display: inline-block;
  width: 0.45em;
  height: 0.45em;
  border-right: 2px solid #7a9595;
  border-bottom: 2px solid #7a9595;
  transform: rotate(-45deg);
  transition: transform 0.15s ease;
  flex-shrink: 0;
}

details[open] > .section-heading::before {
  transform: rotate(45deg);
}

.panel-section:not([open]) .section-heading {
  margin-bottom: 0;
}

.section-grid {
  display: grid;
  gap: 0.6rem;
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
}

.equipment-grid {
  display: grid;
  gap: 0.5rem;
}

.equipment-slot {
  border: 1px solid #d6e1dd;
  border-radius: 10px;
  padding: 0.45rem;
  background: rgba(247, 251, 250, 0.72);
}

.equipment-slot code {
  margin-top: 0.35rem;
  font-size: 0.72rem;
}

.label {
  display: block;
  font-size: 0.75rem;
  color: #5b6d6f;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 0.2rem;
}

.panel-section code {
  display: block;
  font-family: 'IBM Plex Mono', 'Consolas', monospace;
  font-size: 0.8rem;
  color: #293739;
  word-break: break-word;
}

select {
  width: 100%;
  border: 1px solid #8aa4a6;
  border-radius: 10px;
  padding: 0.45rem 0.55rem;
  background: rgba(252, 255, 255, 0.92);
  color: #233537;
  font-family: inherit;
  font-size: 0.95rem;
}

.time-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.time-slider {
  flex: 1;
  accent-color: #5e9aa4;
  cursor: pointer;
}

.time-input {
  width: 4.5rem;
  border: 1px solid #8aa4a6;
  border-radius: 8px;
  padding: 0.3rem 0.4rem;
  background: rgba(252, 255, 255, 0.92);
  color: #233537;
  font-family: 'IBM Plex Mono', 'Consolas', monospace;
  font-size: 0.85rem;
  text-align: center;
}

.actions {
  display: grid;
  gap: 0.5rem;
}

.toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #2c4244;
  font-size: 0.92rem;
}

button {
  border: 1px solid #416465;
  border-radius: 10px;
  background: linear-gradient(180deg, #f5fbfc, #dfeced);
  color: #1f3132;
  font-family: inherit;
  font-size: 0.95rem;
  padding: 0.55rem 0.8rem;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: wait;
}

.error {
  margin: 0;
  color: #992d2d;
}

.canvas-wrap {
  position: relative;
  width: 1024px;
  height: 768px;
  border: 1px solid #c6d4d5;
  border-radius: 14px;
  overflow: hidden;
  background: linear-gradient(180deg, #edf3f6 0%, #d4e0e6 35%, #c2d1db 100%);
}

.canvas-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 0.5rem 0.75rem;
  pointer-events: none;
}

.orbit-readout {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  width: 220px;
  font-family: 'IBM Plex Mono', 'Consolas', monospace;
  font-size: 0.72rem;
  color: rgba(255, 255, 255, 0.85);
  letter-spacing: 0.04em;
  background: rgba(0, 0, 0, 0.16);
  padding: 0.35rem 0.5rem;
  border-radius: 6px;
}

.orbit-actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  pointer-events: auto;
  background: rgba(0, 0, 0, 0.16);
  padding: 0.35rem 0.5rem;
  border-radius: 6px;
}

.overlay-toggle {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.75rem;
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
}

.overlay-toggle input {
  accent-color: #7fb8c4;
}

.overlay-btn {
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.85);
  font-family: inherit;
  font-size: 0.72rem;
  padding: 0.2rem 0.5rem;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.overlay-btn:hover {
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.35);
}

.ground-hit {
  color: rgba(100, 255, 100, 0.95);
  font-weight: 600;
}

.ground-miss {
  color: rgba(255, 130, 100, 0.85);
}

canvas {
  width: 1024px;
  height: 768px;
  display: block;
}

@media (max-width: 960px) {
  .viewer-shell {
    grid-template-columns: 1fr;
  }
}
</style>
