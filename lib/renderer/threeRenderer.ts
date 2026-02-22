import {
  BufferAttribute,
  BufferGeometry,
  DoubleSide,
  Group,
  LineBasicMaterial,
  LineSegments,
  MeshBasicMaterial,
  Matrix4,
  Material,
  Mesh,
  PerspectiveCamera,
  Quaternion,
  Scene,
  ShaderMaterial,
  Vector3,
  Vector4,
  WebGLRenderer,
  type Object3D,
} from 'three'

import { buildGeometryForXimSkinnedMesh } from './meshGeometry'
import { FrameBufferManager } from './frameBufferManager'
import { PostProcessBlitter, buildBlurCompositePlan, type BlurCompositePlan } from './postProcess'
import { getOrCreateTexture } from './textureFactory'
import { applyDebugViewState } from './debugView'
import type { DrawXimCommand } from './drawCommand'
import type { DiffuseLightParams, LightingParams, PointLightParams } from './lights'
import { buildXimSkinnedVertexShader, ximSkinnedFragmentShader } from './shaders/ximSkinnedShader'
import { POINT_LIGHT_COUNT, DIFFUSE_LIGHT_COUNT } from './shaders/shaderConstants'
import { DirectoryResource, type SkeletonMeshResource } from '~/lib/resource/datResource'
import type { SkeletonInstance } from '~/lib/resource/skeletonInstance'

const MAX_JOINTS = 128
const MIN_JOINTS = 8
const RESERVED_VERTEX_UNIFORM_VECTORS = 32
const unitScale = new Vector3(1, 1, 1)

interface SkeletonLineHelper {
  readonly line: LineSegments
  readonly edges: readonly [number, number][]
  readonly positions: Float32Array
}

interface ActorRenderObject {
  readonly root: Group
  readonly meshes: readonly Mesh[]
  readonly skeletonHelpers: readonly Object3D[]
  readonly jointMatrices: readonly Matrix4[]
  readonly skeletonLine: SkeletonLineHelper | null
}

interface ActiveRenderCommand {
  readonly command: DrawXimCommand
  readonly object: ActorRenderObject
}

interface BlurActorRender {
  readonly object: ActorRenderObject
  readonly alpha: number
  readonly plan: BlurCompositePlan
}

export interface RendererOptions {
  readonly canvas: HTMLCanvasElement
  readonly width?: number
  readonly height?: number
}

export interface RendererFrameStats {
  readonly calls: number
  readonly triangles: number
  readonly lines: number
  readonly points: number
  readonly programs: number
}

export class ThreeRenderer {
  readonly scene = new Scene()
  readonly camera = new PerspectiveCamera(55, 1, 0.1, 4000)

  private readonly renderer: WebGLRenderer
  private readonly maxJointUniforms: number
  private readonly frameBuffers: FrameBufferManager
  private readonly blitter = new PostProcessBlitter()

  private readonly actorNodes = new Map<string, ActorRenderObject>()
  private showWireframe = false
  private showBones = false

  constructor(options: RendererOptions) {
    const safeWidth = Math.max(1, Math.floor(options.width ?? options.canvas.clientWidth ?? 1))
    const safeHeight = Math.max(1, Math.floor(options.height ?? options.canvas.clientHeight ?? 1))

    this.renderer = new WebGLRenderer({ canvas: options.canvas, antialias: true, alpha: true })
    this.renderer.setPixelRatio(window.devicePixelRatio || 1)
    this.maxJointUniforms = resolveMaxJointUniforms(this.renderer.capabilities.maxVertexUniforms)
    applyXiCameraConventions(this.camera)
    this.frameBuffers = new FrameBufferManager(safeWidth, safeHeight)
    this.setSize(safeWidth, safeHeight)
  }

  setSize(width: number, height: number): void {
    const safeWidth = Math.max(1, Math.floor(width))
    const safeHeight = Math.max(1, Math.floor(height))
    this.renderer.setSize(safeWidth, safeHeight, false)
    this.frameBuffers.resize(safeWidth, safeHeight)
    this.camera.aspect = safeWidth / safeHeight
    this.camera.updateProjectionMatrix()
  }

  render(commands: readonly DrawXimCommand[]): void {
    const used = new Set<string>()
    const activeCommands: ActiveRenderCommand[] = []

    for (const command of commands) {
      const key = buildCommandKey(command)
      used.add(key)

      const object = this.actorNodes.get(key) ?? this.createActorObject(command, key)
      activeCommands.push({ command, object })
      applyObjectTransform(object.root, command)
      applyShaderLighting(object.meshes, command)
      applyMaterialState(object.meshes, command.alpha)
      applyDebugViewState(
        object.meshes.map((mesh) => mesh.material as ShaderMaterial),
        object.skeletonHelpers,
        {
          showWireframe: this.showWireframe,
          showBones: this.showBones,
        },
      )

      if (command.skeleton) {
        applySkeletonPose(command.skeleton, object)
      }
    }

    for (const [key, object] of this.actorNodes) {
      if (!used.has(key)) {
        this.scene.remove(object.root)
        this.actorNodes.delete(key)
      }
    }

    const blurActors = activeCommands
      .map((active): BlurActorRender | null => {
        const plan = buildBlurCompositePlan(active.command.blurOffsets ?? [])
        if (!plan.requiresBlur) {
          return null
        }

        return {
          object: active.object,
          alpha: active.command.alpha,
          plan,
        }
      })
      .filter((entry): entry is BlurActorRender => entry !== null)

    if (blurActors.length === 0) {
      this.renderer.setRenderTarget(null)
      this.renderer.render(this.scene, this.camera)
      return
    }

    this.renderWithBlur(activeCommands, blurActors)
  }

  dispose(): void {
    for (const object of this.actorNodes.values()) {
      for (const mesh of object.meshes) {
        mesh.geometry.dispose()
        mesh.material.dispose()
      }

      if (object.skeletonLine) {
        object.skeletonLine.line.geometry.dispose()
        object.skeletonLine.line.material.dispose()
      }
    }

    this.actorNodes.clear()
    this.frameBuffers.dispose()
    this.blitter.dispose()
    this.renderer.dispose()
  }

  setDebugView(options: { showWireframe: boolean, showBones: boolean }): void {
    this.showWireframe = options.showWireframe
    this.showBones = options.showBones
  }

  getFrameStats(): RendererFrameStats {
    return {
      calls: this.renderer.info.render.calls,
      triangles: this.renderer.info.render.triangles,
      lines: this.renderer.info.render.lines,
      points: this.renderer.info.render.points,
      programs: this.renderer.info.programs?.length ?? 0,
    }
  }

  private renderWithBlur(activeCommands: readonly ActiveRenderCommand[], blurActors: readonly BlurActorRender[]): void {
    this.setAllActorsVisible(activeCommands, true)
    this.frameBuffers.bindAndClearScreenBuffer(this.renderer)
    this.renderer.render(this.scene, this.camera)

    for (const blurActor of blurActors) {
      this.setAllActorsVisible(activeCommands, false)
      blurActor.object.root.visible = true

      this.frameBuffers.bindAndClearBlurBuffer(this.renderer)
      this.renderer.render(this.scene, this.camera)

      this.frameBuffers.bindAndClearHazeBuffer(this.renderer)
      this.blitter.blit(this.renderer, this.frameBuffers.getBlurBuffer(), this.frameBuffers.getHazeBuffer())

      for (const pass of blurActor.plan.passes) {
        this.blitter.blit(this.renderer, this.frameBuffers.getHazeBuffer(), this.frameBuffers.getScreenBuffer(), {
          offset: pass.offset,
          blendMode: pass.blendMode,
          colorMask: { r: 1, g: 1, b: 1, a: blurActor.alpha },
        })
      }
    }

    this.setAllActorsVisible(activeCommands, true)
    this.blitter.blit(this.renderer, this.frameBuffers.getScreenBuffer(), null)
  }

  private setAllActorsVisible(activeCommands: readonly ActiveRenderCommand[], visible: boolean): void {
    for (const active of activeCommands) {
      active.object.root.visible = visible
    }
  }

  private createActorObject(command: DrawXimCommand, key: string): ActorRenderObject {
    const root = new Group()
    const meshes: Mesh[] = []
    const jointMatrices = createJointMatrixStorage()
    fillJointMatrices(jointMatrices, command.skeleton)

    for (const meshResource of command.meshes) {
      this.appendMeshResource(root, meshes, meshResource, jointMatrices)
    }

    const skeletonLine = createSkeletonLine(command.skeleton)
    const skeletonHelpers: Object3D[] = skeletonLine ? [skeletonLine.line] : []
    for (const helper of skeletonHelpers) {
      helper.visible = this.showBones
      root.add(helper)
    }

    this.scene.add(root)
    const object: ActorRenderObject = { root, meshes, skeletonHelpers, jointMatrices, skeletonLine }
    this.actorNodes.set(key, object)
    return object
  }

  private appendMeshResource(
    root: Object3D,
    meshes: Mesh[],
    meshResource: SkeletonMeshResource,
    jointMatrices: readonly Matrix4[],
  ): void {
    for (const mesh of meshResource.meshes) {
      const geometry = buildGeometryForXimSkinnedMesh(mesh)
      const textureResource = resolveTextureResource(meshResource, mesh.textureName)
      const diffuseTexture = textureResource ? getOrCreateTexture(textureResource) : null

      const material = new ShaderMaterial({
        uniforms: {
          // Core
          uAlpha: { value: 1 },
          uDiffuseTexture: { value: diffuseTexture },
          uHasDiffuseTexture: { value: diffuseTexture !== null },
          uDiscardThreshold: { value: mesh.renderState.discardThreshold ?? 0.0 },
          uColorMask: { value: new Vector4(1, 1, 1, 1) },
          joints: { value: jointMatrices.slice(0, this.maxJointUniforms) },

          // Ambient
          ambientLightColor: { value: new Vector4(1, 1, 1, 1) },

          // Diffuse lights
          ...buildDiffuseLightUniforms(),

          // Point lights
          ...buildPointLightUniforms(),

          // Fog (large values = effectively disabled; Infinity is not valid in GLSL)
          'uFog.fogNearDist': { value: 99999.0 },
          'uFog.fogFarDist': { value: 99999.0 },
          'uFog.fogColor': { value: new Vector4(0, 0, 0, 0) },
        },
        vertexShader: buildXimSkinnedVertexShader(this.maxJointUniforms),
        fragmentShader: ximSkinnedFragmentShader,
        transparent: mesh.renderState.blendEnabled,
        depthWrite: mesh.renderState.blendEnabled ? false : mesh.renderState.depthMask,
        vertexColors: true,
        side: DoubleSide,
      })

      const renderMesh = new Mesh(geometry, material)
      meshes.push(renderMesh)
      root.add(renderMesh)
    }
  }
}

export function applyXiCameraConventions(camera: PerspectiveCamera): void {
  camera.up.set(0, -1, 0)
}

export function resolveTextureResource(meshResource: SkeletonMeshResource, textureName: string) {
  const localDir = (meshResource as Partial<SkeletonMeshResource>).localDir
  if (!localDir) {
    return null
  }

  return localDir.searchLocalAndParentsByName(textureName) ?? DirectoryResource.getGlobalTexture(textureName)
}

export function resolveMaxJointUniforms(maxVertexUniformVectors: number, preferredMaxJoints = MAX_JOINTS): number {
  const budgetedVectors = Math.max(0, Math.floor(maxVertexUniformVectors) - RESERVED_VERTEX_UNIFORM_VECTORS)
  const maxByBudget = Math.floor(budgetedVectors / 4)
  return Math.max(MIN_JOINTS, Math.min(preferredMaxJoints, maxByBudget))
}

function createJointMatrixStorage(): Matrix4[] {
  return Array.from({ length: MAX_JOINTS }, () => new Matrix4().identity())
}

function fillJointMatrices(jointMatrices: Matrix4[], skeleton: SkeletonInstance | null): void {
  for (const matrix of jointMatrices) {
    matrix.identity()
  }

  if (!skeleton) {
    return
  }

  const tempPosition = new Vector3()
  const tempQuaternion = new Quaternion()

  const count = Math.min(jointMatrices.length, skeleton.joints.length)
  for (let i = 0; i < count; i += 1) {
    const joint = skeleton.joints[i]
    const matrix = jointMatrices[i]
    if (!joint || !matrix) {
      continue
    }

    tempPosition.set(joint.worldPosition.x, joint.worldPosition.y, joint.worldPosition.z)
    tempQuaternion.set(joint.worldRotation.x, joint.worldRotation.y, joint.worldRotation.z, joint.worldRotation.w)
    matrix.copy(composeJointMatrix(tempPosition, tempQuaternion))
  }
}

export function composeJointMatrix(
  position: { x: number, y: number, z: number },
  rotation: { x: number, y: number, z: number, w: number },
): Matrix4 {
  const matrix = new Matrix4()
  const pos = new Vector3(position.x, position.y, position.z)
  const quat = new Quaternion(rotation.x, rotation.y, rotation.z, rotation.w)
  matrix.compose(pos, quat, unitScale)
  return matrix
}

function createSkeletonLine(skeleton: SkeletonInstance | null): SkeletonLineHelper | null {
  if (!skeleton) {
    return null
  }

  const edges: [number, number][] = []
  for (let i = 0; i < skeleton.joints.length; i += 1) {
    const joint = skeleton.joints[i]
    if (!joint || joint.parentIndex < 0) {
      continue
    }
    edges.push([joint.parentIndex, i])
  }

  if (edges.length === 0) {
    return null
  }

  const positions = new Float32Array(edges.length * 6)
  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new BufferAttribute(positions, 3))
  const material = new LineBasicMaterial({ color: 0xf6f1aa })
  const line = new LineSegments(geometry, material)

  const helper: SkeletonLineHelper = { line, edges, positions }
  updateSkeletonLine(helper, skeleton)
  return helper
}

function updateSkeletonLine(helper: SkeletonLineHelper, skeleton: SkeletonInstance): void {
  let offset = 0
  for (const [parentIndex, childIndex] of helper.edges) {
    const parent = skeleton.joints[parentIndex]
    const child = skeleton.joints[childIndex]
    if (!parent || !child) {
      continue
    }

    helper.positions[offset] = parent.worldPosition.x
    helper.positions[offset + 1] = parent.worldPosition.y
    helper.positions[offset + 2] = parent.worldPosition.z
    helper.positions[offset + 3] = child.worldPosition.x
    helper.positions[offset + 4] = child.worldPosition.y
    helper.positions[offset + 5] = child.worldPosition.z
    offset += 6
  }

  const attribute = helper.line.geometry.getAttribute('position') as BufferAttribute
  attribute.needsUpdate = true
  helper.line.geometry.computeBoundingSphere()
}

function buildCommandKey(command: DrawXimCommand): string {
  return command.meshes.map((mesh) => mesh.path()).join('|')
}

function applyObjectTransform(root: Group, command: DrawXimCommand): void {
  root.position.set(command.translate.x, command.translate.y, command.translate.z)
  root.rotation.set(0, command.rotationY, 0)
  root.scale.set(command.scale.x, command.scale.y, command.scale.z)
}

/**
 * Sets all lighting, fog, and color mask uniforms on every ShaderMaterial in the actor.
 * This replaces the old Three.js AmbientLight/DirectionalLight approach with
 * custom uniforms matching the Kotlin XimShader reference.
 */
function applyShaderLighting(meshes: readonly Mesh[], command: DrawXimCommand): void {
  for (const mesh of meshes) {
    const material = mesh.material
    if (!(material instanceof ShaderMaterial)) {
      continue
    }

    const u = material.uniforms

    // Ambient
    const ac = command.lightingParams.ambientColor
    setUniformVec4(u, 'ambientLightColor', ac.r, ac.g, ac.b, ac.a)

    // Diffuse lights
    applyDiffuseLightUniforms(u, command.lightingParams)

    // Point lights
    applyPointLightUniforms(u, command.pointLights)

    // Fog (clamp Infinity to large finite value; GLSL floats don't support Infinity)
    setUniformFloat(u, 'uFog.fogNearDist', clampFogDistance(command.fogParams.start))
    setUniformFloat(u, 'uFog.fogFarDist', clampFogDistance(command.fogParams.end))
    const fc = command.fogParams.color
    setUniformVec4(u, 'uFog.fogColor', fc.r, fc.g, fc.b, fc.a)

    // Color mask
    const cm = command.colorMask ?? { r: 1, g: 1, b: 1, a: 1 }
    setUniformVec4(u, 'uColorMask', cm.r, cm.g, cm.b, cm.a)
  }
}

function applyMaterialState(meshes: readonly Mesh[], alpha: number): void {
  for (const mesh of meshes) {
    applyMaterialAlpha(mesh.material as Material, alpha)
  }
}

export function applyMaterialAlpha(material: Material, alpha: number): void {
  if (material instanceof ShaderMaterial) {
    const alphaUniform = material.uniforms.uAlpha
    if (alphaUniform) {
      alphaUniform.value = alpha
    }
    material.needsUpdate = false
    return
  }

  if (material instanceof MeshBasicMaterial) {
    material.opacity = alpha
    material.transparent = alpha < 1
  }
}

function applySkeletonPose(skeletonInstance: SkeletonInstance, object: ActorRenderObject): void {
  fillJointMatrices(object.jointMatrices as Matrix4[], skeletonInstance)
  if (object.skeletonLine) {
    updateSkeletonLine(object.skeletonLine, skeletonInstance)
  }
}

export function applySoftwareSkinningToMesh(mesh: Mesh, jointMatrices: readonly Matrix4[]): void {
  const geometry = mesh.geometry
  const position = geometry.getAttribute('position')
  const position0 = geometry.getAttribute('position0')
  const position1 = geometry.getAttribute('position1')
  const jointWeight = geometry.getAttribute('jointWeight')
  const joint0 = geometry.getAttribute('joint0')
  const joint1 = geometry.getAttribute('joint1')

  if (
    !(position instanceof BufferAttribute)
    || !(position0 instanceof BufferAttribute)
    || !(position1 instanceof BufferAttribute)
    || !(jointWeight instanceof BufferAttribute)
    || !(joint0 instanceof BufferAttribute)
    || !(joint1 instanceof BufferAttribute)
  ) {
    return
  }

  const p0 = new Vector3()
  const p1 = new Vector3()
  const t0 = new Vector3()
  const t1 = new Vector3()
  const identityMatrix = new Matrix4().identity()

  const count = position.count
  for (let i = 0; i < count; i += 1) {
    p0.set(position0.getX(i), position0.getY(i), position0.getZ(i))
    p1.set(position1.getX(i), position1.getY(i), position1.getZ(i))

    const w = Math.max(0, Math.min(1, jointWeight.getX(i)))
    const j0 = clampJointIndex(joint0.getX(i), jointMatrices.length)
    const j1 = clampJointIndex(joint1.getX(i), jointMatrices.length)

    t0.copy(p0).applyMatrix4(jointMatrices[j0] ?? identityMatrix)
    t1.copy(p1).applyMatrix4(jointMatrices[j1] ?? identityMatrix)

    position.setXYZ(
      i,
      t0.x * w + t1.x * (1 - w),
      t0.y * w + t1.y * (1 - w),
      t0.z * w + t1.z * (1 - w),
    )
  }

  position.needsUpdate = true
  geometry.computeBoundingSphere()
}

function clampJointIndex(value: number, length: number): number {
  if (length <= 0) {
    return 0
  }

  const index = Math.floor(value)
  return Math.max(0, Math.min(length - 1, index))
}

interface PoseJoint {
  readonly parentIndex: number
  readonly worldPosition: { x: number, y: number, z: number }
  readonly worldRotation: { x: number, y: number, z: number, w: number }
}

interface LocalPose {
  readonly position: { x: number, y: number, z: number }
  readonly rotation: { x: number, y: number, z: number, w: number }
}

export function toLocalSkeletonPose(joints: readonly PoseJoint[], index: number): LocalPose {
  const joint = joints[index]
  if (!joint) {
    return {
      position: { x: 0, y: 0, z: 0 },
      rotation: { x: 0, y: 0, z: 0, w: 1 },
    }
  }

  if (joint.parentIndex < 0) {
    return {
      position: { ...joint.worldPosition },
      rotation: normalizeQuaternion(joint.worldRotation),
    }
  }

  const parent = joints[joint.parentIndex]
  if (!parent) {
    return {
      position: { ...joint.worldPosition },
      rotation: normalizeQuaternion(joint.worldRotation),
    }
  }

  const parentInverse = conjugateQuaternion(normalizeQuaternion(parent.worldRotation))
  const delta = {
    x: joint.worldPosition.x - parent.worldPosition.x,
    y: joint.worldPosition.y - parent.worldPosition.y,
    z: joint.worldPosition.z - parent.worldPosition.z,
  }

  return {
    position: rotateVector(parentInverse, delta),
    rotation: normalizeQuaternion(multiplyQuaternion(parentInverse, joint.worldRotation)),
  }
}

function multiplyQuaternion(
  a: { x: number, y: number, z: number, w: number },
  b: { x: number, y: number, z: number, w: number },
): { x: number, y: number, z: number, w: number } {
  return {
    w: a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z,
    x: a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
    y: a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
    z: a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
  }
}

function conjugateQuaternion(q: { x: number, y: number, z: number, w: number }): { x: number, y: number, z: number, w: number } {
  return {
    x: -q.x,
    y: -q.y,
    z: -q.z,
    w: q.w,
  }
}

function normalizeQuaternion(q: { x: number, y: number, z: number, w: number }): { x: number, y: number, z: number, w: number } {
  const magnitude = Math.hypot(q.x, q.y, q.z, q.w)
  if (magnitude <= 1e-7) {
    return { x: 0, y: 0, z: 0, w: 1 }
  }

  return {
    x: q.x / magnitude,
    y: q.y / magnitude,
    z: q.z / magnitude,
    w: q.w / magnitude,
  }
}

function rotateVector(
  rotation: { x: number, y: number, z: number, w: number },
  vector: { x: number, y: number, z: number },
): { x: number, y: number, z: number } {
  const vecQuat = { x: vector.x, y: vector.y, z: vector.z, w: 0 }
  const rotated = multiplyQuaternion(multiplyQuaternion(rotation, vecQuat), conjugateQuaternion(rotation))
  return {
    x: rotated.x,
    y: rotated.y,
    z: rotated.z,
  }
}

// ─── Uniform helpers ─────────────────────────────────────────────────────────

const MAX_FOG_DISTANCE = 99999.0

function clampFogDistance(value: number): number {
  if (!Number.isFinite(value)) {
    return MAX_FOG_DISTANCE
  }
  return value
}

type UniformMap = Record<string, { value: unknown }>

function setUniformFloat(uniforms: UniformMap, name: string, value: number): void {
  if (uniforms[name]) {
    uniforms[name].value = value
  }
}

function setUniformVec4(uniforms: UniformMap, name: string, x: number, y: number, z: number, w: number): void {
  const u = uniforms[name]
  if (!u) { return }
  const current = u.value as Vector4 | null
  if (current instanceof Vector4) {
    current.set(x, y, z, w)
  } else {
    u.value = new Vector4(x, y, z, w)
  }
}

function setUniformVec3(uniforms: UniformMap, name: string, x: number, y: number, z: number): void {
  const u = uniforms[name]
  if (!u) { return }
  const current = u.value as Vector3 | null
  if (current instanceof Vector3) {
    current.set(x, y, z)
  } else {
    u.value = new Vector3(x, y, z)
  }
}

/**
 * Creates the initial uniform entries for diffuse light structs.
 * Matches GLSL: `uniform DiffuseLight diffuseLights[2]`
 */
function buildDiffuseLightUniforms(): Record<string, { value: unknown }> {
  const uniforms: Record<string, { value: unknown }> = {}
  for (let i = 0; i < DIFFUSE_LIGHT_COUNT; i++) {
    uniforms[`diffuseLights[${i}].diffuseLightDir`] = { value: new Vector3(0, 0, 0) }
    uniforms[`diffuseLights[${i}].diffuseLightColor`] = { value: new Vector4(0, 0, 0, 0) }
  }
  return uniforms
}

/**
 * Creates the initial uniform entries for point light structs.
 * Matches GLSL: `uniform PointLight pointLights[4]`
 */
function buildPointLightUniforms(): Record<string, { value: unknown }> {
  const uniforms: Record<string, { value: unknown }> = {}
  for (let i = 0; i < POINT_LIGHT_COUNT; i++) {
    uniforms[`pointLights[${i}].position`] = { value: new Vector3(0, 0, 0) }
    uniforms[`pointLights[${i}].color`] = { value: new Vector4(0, 0, 0, 0) }
    uniforms[`pointLights[${i}].range`] = { value: 0 }
    uniforms[`pointLights[${i}].attenuation`] = { value: new Vector3(1, 0, 0) }
  }
  return uniforms
}

/**
 * Updates diffuse light uniforms from a LightingParams.
 */
function applyDiffuseLightUniforms(uniforms: UniformMap, lighting: LightingParams): void {
  for (let i = 0; i < DIFFUSE_LIGHT_COUNT; i++) {
    const light: DiffuseLightParams | undefined = lighting.lights[i]
    if (light) {
      setUniformVec3(uniforms, `diffuseLights[${i}].diffuseLightDir`, light.direction.x, light.direction.y, light.direction.z)
      setUniformVec4(uniforms, `diffuseLights[${i}].diffuseLightColor`, light.color.r, light.color.g, light.color.b, light.color.a)
    } else {
      setUniformVec3(uniforms, `diffuseLights[${i}].diffuseLightDir`, 0, 0, 0)
      setUniformVec4(uniforms, `diffuseLights[${i}].diffuseLightColor`, 0, 0, 0, 0)
    }
  }
}

/**
 * Updates point light uniforms from the command's point light array.
 * Unused slots are zeroed out to prevent stale light data.
 */
function applyPointLightUniforms(uniforms: UniformMap, pointLights: readonly PointLightParams[]): void {
  for (let i = 0; i < POINT_LIGHT_COUNT; i++) {
    const pl: PointLightParams | undefined = pointLights[i]
    if (pl) {
      setUniformVec3(uniforms, `pointLights[${i}].position`, pl.position.x, pl.position.y, pl.position.z)
      setUniformVec4(uniforms, `pointLights[${i}].color`, pl.color.r, pl.color.g, pl.color.b, pl.color.a)
      setUniformFloat(uniforms, `pointLights[${i}].range`, pl.range)
      // Attenuation: (constant=1, linear=0, quadratic=attenuationQuad)
      setUniformVec3(uniforms, `pointLights[${i}].attenuation`, 1, 0, pl.attenuationQuad)
    } else {
      setUniformVec3(uniforms, `pointLights[${i}].position`, 0, 0, 0)
      setUniformVec4(uniforms, `pointLights[${i}].color`, 0, 0, 0, 0)
      setUniformFloat(uniforms, `pointLights[${i}].range`, 0)
      setUniformVec3(uniforms, `pointLights[${i}].attenuation`, 1, 0, 0)
    }
  }
}
