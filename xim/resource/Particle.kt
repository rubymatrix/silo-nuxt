package xim.resource

import js.typedarrays.Float32Array
import xim.math.Matrix3f
import xim.math.Matrix4f
import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.*
import xim.poc.audio.SoundEffectInstance
import xim.poc.camera.CameraReference
import xim.poc.game.ActorType
import xim.poc.gl.*
import xim.poc.gl.SpecularParams
import xim.util.OnceLogger.error
import xim.util.OnceLogger.warn
import kotlin.math.*
import kotlin.reflect.KClass
import kotlin.reflect.cast

private class DecalProjectionMemoize {
    private var refPosition: Vector3f? = null
    private var memoizedFloor: Vector3f? = null

    fun getOrCompute(position: Vector3f, calc: () -> Vector3f?): Vector3f? {
        val current = refPosition
        if (current != null && Vector3f.distanceSquared(current, position) < 0.1f) { return memoizedFloor }

        refPosition = Vector3f().copyFrom(position)
        memoizedFloor = calc.invoke()

        return memoizedFloor
    }

}

class ParticleBatch(
    val batchCount: Int,
    val batchOffsets: Float32Array,
)

class SubParticle(
    val position: Vector3f = Vector3f(),
    val relativeVelocity: Vector3f = Vector3f(),
)

class Particle(val creator: ParticleGenerator, val association: EffectAssociation, val parent: Particle?, val subParticles: Array<SubParticle>?) {

    companion object {
        private var counter = 1L
    }

    val datId = creator.datId
    val internalId = counter++
    val definition = creator.def

    var attachmentSource = creator

    // Duration
    private var age = 0f
    var maxAge = 0f
    private var forceExpired = false

    // Initial state
    val position: Vector3f = Vector3f()
    val rotation: Vector3f = Vector3f()
    val scale: Vector3f = Vector3f()

    var negateRotationY = false

    val texCoordTranslate: Vector2f = Vector2f()

    var spriteSheetIndex: Int = 0

    var attachType = creator.def.attachType
    val associatedPosition = Vector3f()
    val associatedRotation = Matrix4f()

    lateinit var config: StandardParticleConfiguration

    lateinit var meshProvider: MeshProvider
    var ringMeshParams: RingParams? = null

    private val dynamicallyAllocated = HashMap<Int, DynamicParticleData>()

    private val previousPosition = Vector3f()
    private val lastMovementAmount = Vector3f()

    val initialPosition: Vector3f = Vector3f()
    var initialPositionCameraOriented = false

    val weightedMeshWeights = Array(5) { 0f }

    val color = Color(1f, 1f, 1f, 1f)
    val colorMultiplier = Color(1f, 1f, 1f, 1f)
    var alphaOverride: Int? = null

    var colorDayOfWeek: Color? = null
    var colorMoonPhase: Color? = null

    var blendFunc = BlendFunc.Src_One_Add
    var deferredBlendFunc = BlendFunc.Src_One_Add

    var projectionBias = ProjectionZBias(-0.005f, 0f)
    var occlusionSettings: OcclusionSettings? = null

    val children = ArrayList<Particle>()
    val parentPositionSnapshot = Vector3f()
    var parentOffsetTransform: Matrix4f? = null
    val parentOrientation = Matrix4f()

    var useParentAssociatedPositionOnly = false
    var useParentOrientation = false

    var progressOffsetParams: Pair<Vector3f, Vector3f>? = null

    var footMarkEffect: Boolean = false

    private val decalProjectionMemoize = DecalProjectionMemoize()

    var drawDistanceCulled: Boolean = false

    val audioConfiguration = AudioConfiguration()
    val emittedAudio = ArrayList<SoundEffectInstance>()
    var audioEmitter: AudioEmitter? = null

    var dummyActorLink: ParticleActorLink? = null

    var specularParams: xim.resource.SpecularParams? = null

    lateinit var pointLightParams: PointLightParams
    val attachedPointLights = ArrayList<DatId>()

    val hazeOffset = Vector2f()
    var previousFrameTransform: Matrix4f? = null

    var batched = false

    private val worldTransform = Matrix4f()
    private val particleTransform = Matrix4f()
    private val worldSpacePosition = Vector3f()

    fun onInitialized() { updateTransforms() }

    fun update(numFrames: Float) {
        children.forEach { it.update(numFrames) }
        children.removeAll { it.isComplete() }

        if (isExpired()) { return }

        age += numFrames
        audioEmitter?.update(this) // TODO - can this go after the expiry check?

        if (isExpired()) {
            dummyActorLink?.cleanUp()
            definition.expirationHandlers.forEach { it.onExpire(this) }
            return
        }

        dummyActorLink?.update()

        if (numFrames != 0f) { previousPosition.copyFrom(position) }
        colorMultiplier.copyFrom(Color.NO_MASK)

        try {
            for (updater in definition.updaters) {
                updater.apply(numFrames, this)
                if (updater !is SubParticleUpdater) { continue }
                subParticles?.forEach { updater.apply(numFrames, this, it) }
            }
        } catch (e: Throwable) {
            error("[${datId}] Failed to apply update to particles: ${e.message}")
            forceExpire()
            return
        }

        if (numFrames != 0f) { lastMovementAmount.copyFrom(position - previousPosition) }
        updateTransforms()
    }

    private fun updateTransforms() {
        updateAssociatedPosition()
        updateAssociatedFacing()

        computeWorldSpaceTransform(worldTransform)
        computeParticleSpaceOrientationTransform(particleTransform)
        computeWorldSpacePosition()
    }

    fun age(): Float {
        return age
    }

    fun resetAge() {
        if (forceExpired) { return }
        age = 1e-7f
    }

    fun isDistortion(): Boolean {
        return config.linkedDataType == LinkedDataType.Distortion
    }

    fun isPointLight(): Boolean {
        return config.linkedDataType == LinkedDataType.PointLight
    }

    fun isLensFlare(): Boolean {
        return config.linkedDataType == LinkedDataType.LensFlare
    }

    fun isCharacterLight(): Boolean {
        return datId.id.startsWith('c')
    }

    fun getColor(): Color {
        val outputColor = color * colorMultiplier

        colorDayOfWeek?.let { outputColor.modulateInPlace(it, 2f) }
        colorMoonPhase?.let { outputColor.modulateInPlace(it, 2f) }
        alphaOverride?.let { outputColor.a(it) }

        if (shouldSnapAlpha(outputColor)) {
            outputColor.a(1f)
        }

        return if (association is ActorAssociation) {
            association.actor.effectOverride.colorOverride?.invoke(this, outputColor) ?: outputColor
        } else {
            outputColor
        }
    }

    private fun updateAssociatedPosition() {
        if (useParentAssociatedPositionOnly) { return }

        // TODO - sun/moon often disable following the generator, but they do need to follow the sun/moon position...
        if (!isSunOrMoon() && age > 0f && !config.followGenerator) { return }

        if (config.cameraAttachedBasePosition) {
            if (age > 0f) { return }

            val viewMatrix = CameraReference.getInstance().getViewMatrix()
            val offsetFromCamera = viewMatrix.lookAtLeft() * -config.basePosition.x + viewMatrix.lookAtUp() * config.basePosition.y + viewMatrix.lookAtForward() * -config.basePosition.z
            associatedPosition.copyFrom(CameraReference.getInstance().getPosition() + offsetFromCamera)
        } else if (footMarkEffect) {
            if (age > 0f) { return }

            association as ActorAssociation
            val position = association.actor.displayPosition
            associatedPosition.copyFrom(position)

            val joint = association.context.joint!!
            associatedPosition += joint.currentTransform.getTranslationVector()
        } else if (config.followCamera) {
            associatedPosition.copyFrom(CameraReference.getInstance().getPosition())
        } else {
            associatedPosition.copyFrom(attachmentSource.getAssociatedPosition())
        }
    }

    private fun updateAssociatedFacing() {
        if (useParentOrientation) { return }

        if (age > 0f && !config.followGenerator) { return }

        if (footMarkEffect) {
            if (age > 0f) { return }
            association as ActorAssociation
            associatedRotation.identity()
            associatedRotation.rotateYInPlace(association.actor.displayFacingDir)
        } else if (association is WeatherAssociation && attachmentSource.def.attachType == AttachType.None) {
            val areaTransform = SceneManager.getCurrentScene().getAreaTransform()
            if (areaTransform != null) {
                associatedRotation.identity()
                associatedRotation.copyUpperLeft(areaTransform.inverseTransform)
            }
        } else {
            associatedRotation.copyFrom(attachmentSource.getAssociatedFacing())
        }
    }

    fun getProgress(): Float {
        return (age / maxAge).coerceIn(0f, 1f)
    }

    fun hasMeshes(): Boolean {
        return meshProvider.hasMeshes(this)
    }

    fun getMeshes(): List<MeshBuffer> {
        return try {
            meshProvider.getMeshes(this)
        } catch (e: Exception) {
            creator.stopEmitting()
            forceExpire()

            error("[${datId}] Failed to evaluate meshes: ${e.message}")
            emptyList()
        }
    }

    fun isExpired() = forceExpired || age >= maxAge

    fun forceExpire() {
        forceExpired = true
    }

    fun isComplete() : Boolean {
        return isExpired() && children.all { it.isComplete() } && emittedAudio.all { it.isComplete() }
    }

    fun getChildrenRecursively(): List<Particle> {
        return children + children.flatMap { it.getChildrenRecursively() }
    }

    fun getParticleSpaceOrientationTransform(): Matrix4f {
        return Matrix4f().copyFrom(particleTransform)
    }

    fun computeParticleSpaceOrientationTransform(particleTransform: Matrix4f, specialBillBoardType: BillBoardType? = null, rotationOrderOverride: RotationOrder? = null) {
        particleTransform.identity()

        val directionOrientation = when (config.billBoardType) {
            BillBoardType.Movement -> lastMovementAmount
            BillBoardType.MovementHorizontal -> lastMovementAmount.withY(0f)
            BillBoardType.Camera -> CameraReference.getInstance().getPosition() - getWorldSpacePosition()
            else -> null
        }

        if (specialBillBoardType == BillBoardType.XYZ) {
            val bbDir = CameraReference.getInstance().getViewVector()
            particleTransform.axisBillboardInPlace(bbDir)
        } else if (directionOrientation != null) {
            applyMovementOrientation(directionOrientation, particleTransform)
        } else if (parent != null && config.billBoardType == BillBoardType.None) {
            particleTransform.multiplyInPlace(parentOrientation)
        }

        val targetAdjustedScale = scale * if (specialBillBoardType == null) { evaluateTargetSizeScale() } else { 1f }
        applyContextScale(targetAdjustedScale)

        if (config.scaleBeforeRotate) { particleTransform.scaleInPlace(targetAdjustedScale) }

        val yRotationMultiplier = if (negateRotationY) { -1f } else { 1f }

        when (rotationOrderOverride ?: config.rotationOrder) {
            RotationOrder.XYZ -> particleTransform.rotateXYZInPlace(rotation.x, yRotationMultiplier * rotation.y, rotation.z)
            RotationOrder.ZYX -> particleTransform.rotateZYXInPlace(rotation.x, yRotationMultiplier * rotation.y, rotation.z)
        }

        if (!config.scaleBeforeRotate) { particleTransform.scaleInPlace(targetAdjustedScale) }

    }

    fun computeWorldSpaceTransform(worldTransform: Matrix4f) {
        worldTransform.identity()
        val positionTransform = Matrix4f()

        if (parentOffsetTransform != null) {
            positionTransform.copyFrom(parentOffsetTransform!!)
        } else {
            worldTransform.translateDirect(associatedPosition)
            worldTransform.multiplyInPlace(associatedRotation)
        }

        if (isActorToActorAttachType()) {
            positionTransform.multiplyInPlace(attachmentSource.attachment.getActorToActorScaleTransform())
        }

        if (config.cameraSpaceBillboard) {
            worldTransform.identityUpperLeft()
            positionTransform.multiplyInPlace(getHorizontalCameraBillboardTransform())
        }

        if (!isActorToActorAttachType()) {
            val targetBasedScaling = evaluateTargetPositionScale()
            positionTransform.scaleInPlace(targetBasedScaling, targetBasedScaling, targetBasedScaling)
        }

        if (!config.cameraAttachedBasePosition) {
            worldTransform.translateInPlace(positionTransform.transform(config.basePosition))
        }

        if (initialPositionCameraOriented) {
            worldTransform.identityUpperLeft()
        }

        val progressOffset = computeProgressPositionOffset()
        worldTransform.translateInPlace(positionTransform.transform(initialPosition + position + parentPositionSnapshot + progressOffset, w = 0f))

        if (config.billBoardType == BillBoardType.Camera) {
            worldTransform.identityUpperLeft() // Should Movement & MovementHorizontal also do this?
        }

        if (config.decalEffect) {
            val approxWorldPos = worldTransform.getTranslationVector()
            val nearestFloor = decalProjectionMemoize.getOrCompute(approxWorldPos) { SceneManager.getCurrentScene().getNearestFloorPosition(approxWorldPos)  }
            if (nearestFloor != null) { worldTransform.m[13] = nearestFloor.y }
        }
    }

    fun getWorldSpaceTransform() : Matrix4f {
        return Matrix4f().copyFrom(worldTransform)
    }

    private fun computeWorldSpacePosition() {
        val totalTransform = Matrix4f()
        worldTransform.multiply(particleTransform, totalTransform)
        worldSpacePosition.copyFrom(totalTransform.getTranslationVector())
    }

    fun getWorldSpacePosition(): Vector3f {
        return Vector3f(worldSpacePosition)
    }

    fun getPointLightParams(): PointLight {
        if (!isPointLight()) { throw IllegalStateException("[$datId] Not a lighting particle") }

        val range = pointLightParams.range * pointLightParams.rangeMultiplier
        val atten = 1f / (pointLightParams.theta * pointLightParams.thetaMultiplier)
        val color = color.withMultiplied(2f)
        val position = getWorldSpacePosition()
        return PointLight(position, color, range, atten)
    }

    fun getSpecularParams(): SpecularParams? {
        val params = specularParams ?: return null

        val specularTransform = Matrix3f.truncate(
            Matrix4f()
                // TODO verify rotate order
            .rotateYInPlace(1.5f * PI.toFloat()) // This is by default - maybe it's controlled by one of the flags?
            .rotateZYXInPlace(params.rotation)
        )

        val resource = params.textureRefLink?.getOrPut {
            creator.localDir.searchLocalAndParentsById(it, TextureResource::class)
                ?: GlobalDirectory.directoryResource.getNullableChildRecursivelyAs(it, TextureResource::class)
        }

        if (resource == null) {
            warn("[${datId}] Couldn't find specular texture: ${params.textureRefLink?.id}")
        }

        return SpecularParams(enabled = true, textureResource = resource, color = params.color, specularTransform = specularTransform)
    }

    fun getPositionTransform(): PositionTransform {
        return getDynamic(PositionTransform::class) ?: throw IllegalStateException("[${datId}] But there was no position transform")
    }

    fun getTotalVelocity(): Vector3f {
        return getTotalVelocity(getPositionTransform())
    }

    fun getTotalVelocity(particleTransform: ParticleTransform): Vector3f {
        val yRotationMultiplier = if (negateRotationY) { -1f } else { 1f }
        val rotation = particleTransform.velocityRotation

        val velocityRotation = Matrix4f().rotateZYXInPlace(rotation.x, rotation.y * yRotationMultiplier, rotation.z)
        return velocityRotation.transform(particleTransform.velocity + particleTransform.relativeVelocity)
    }

    fun <T : DynamicParticleData> allocate(offset: Int, data: T) : T {
        val current = dynamicallyAllocated[offset]
        if (current != null) { throw IllegalStateException("[${datId}] But there was already allocated data: $data") }
        dynamicallyAllocated[offset] = data
        return data
    }

    fun getNullableDynamic(offset: Int): DynamicParticleData? {
        return dynamicallyAllocated[offset]
    }

    fun getDynamic(offset: Int): DynamicParticleData {
        return getNullableDynamic(offset) ?: throw IllegalStateException("[${datId}] But there was no data at offset: $offset")
    }

    fun <T: DynamicParticleData> getDynamic(type: KClass<T>): T? {
        val match = dynamicallyAllocated.values.firstOrNull { type.isInstance(it) } ?: return null
        return type.cast(match)
    }

    fun isSunOrMoon(): Boolean {
        return attachType == AttachType.Sun || attachType == AttachType.Moon
    }

    fun isFogEnabled(): Boolean {
        if (attachType == AttachType.Sun || attachType == AttachType.Moon) { return false }
        return config.fogEnabled
    }

    private fun getHorizontalCameraBillboardTransform(): Matrix4f {
        val bbDir = CameraReference.getInstance().getViewVector()
        val bbTransform = Matrix4f().axisBillboardInPlace(bbDir)
        bbTransform.m[4] = 0f; bbTransform.m[5] = 1f; bbTransform.m[6] = 0f
        return bbTransform
    }

    fun isAssociatedToActor(): Boolean {
        return association is ActorAssociation
    }

    private fun isActorToActorAttachType(): Boolean {
        return attachType == AttachType.SourceToTargetBasis || attachType == AttachType.TargetToSourceBasis
    }

    fun computeProgressPositionOffset(): Vector3f {
        val params = progressOffsetParams ?: return Vector3f.ZERO
        if (!isActorToActorAttachType()) { return Vector3f.ZERO }

        val def = attachmentSource.def
        val source = if (def.attachedJoint0 == 48) { Vector3f.ZERO } else { params.first }
        val destination = if (def.attachedJoint1 == 48) { Vector3f.ZERO } else { params.second }

        return Vector3f.lerp(source, destination, getProgress())
    }

    private fun evaluateTargetSizeScale(): Float {
        return evaluateTargetScale(definition.actorScaleParams.scaleSize, definition.actorScaleParams.scaleSizeAmount)
    }

    private fun evaluateTargetPositionScale(): Float {
        // TODO - verify that position scaling actually works the same way as size scaling
        return evaluateTargetScale(definition.actorScaleParams.scalePosition, definition.actorScaleParams.scalePositionAmount)
    }

    private fun evaluateTargetScale(actorType: ActorScaleTarget, multiplier: Float): Float {
        if (association !is ActorAssociation) { return 1f }

        val actor = when(actorType) {
            ActorScaleTarget.Source -> ActorManager[association.context.originalActor] ?: return 1f
            ActorScaleTarget.Target -> ActorManager[association.context.primaryTargetId] ?: return 1f
            ActorScaleTarget.None -> return 1f
        }

        val skeleton = actor.actorModel?.getSkeleton() ?: return 1f
        val boundingBox = skeleton.resource.boundingBoxes.getOrNull(0) ?: return 1f

        val step = max(boundingBox.width()/1.7f - 1f, boundingBox.height()/1.9f - 1)
        val scale = actor.getScale() * (1f + ((multiplier + 13f/16f) * (0.5f * step)))

        return if (actor.state.type == ActorType.StaticNpc) { scale } else { scale.coerceAtMost(3f) }
    }

    private fun applyMovementOrientation(directionOrientation: Vector3f, particleTransform: Matrix4f) {
        // Movement-orientation is completely ignored for batched particles
        if (batched) { return }

        val movement = Vector3f(directionOrientation)

        if (movement.magnitudeSquare() != 0f) {
            movement.normalizeInPlace()

            if (abs(movement.y) < 0.999f) {
                val left = Vector3f.Y.cross(movement).normalizeInPlace()
                val up = movement.cross(left).normalizeInPlace()

                val angle = -acos(up.dot(Vector3f.Y)) * sign(movement.y)
                particleTransform.axisAngleRotationInPlace(left, angle)

                val theta = -atan2(movement.z, movement.x)
                particleTransform.rotateYInPlace(theta)
            } else {
                particleTransform.rotateZInPlace(sign(movement.y) * PI.toFloat()/2f)
            }
        }
    }

    private fun shouldSnapAlpha(color: Color): Boolean {
        return blendFunc == BlendFunc.Src_InvSrc_Add &&
                color.a() >= 127f/255f &&
                // Verified by switching [Bibiki Bay]'s [umi1] to use zone-mesh, which no longer snaps.
                // Is there a specific setting in the particle-mesh that causes this?
                meshProvider.let { it is StaticMeshProvider && it.resource is ParticleMeshResource }
    }

    private fun applyContextScale(scale: Vector3f) {
        if (association !is ActorAssociation) { return }
        val contextScale = association.actor.effectOverride.scaleMultiplier ?: return
        scale *= contextScale
    }

    fun getParticleBatch(): ParticleBatch? {
        subParticles ?: return null

        val batchCount = subParticles.size
        val offsets = Float32Array(4 * batchCount)

        for (i in subParticles.indices) {
            val offset = subParticles[i].position
            offsets[4 * i + 0] = offset.x
            offsets[4 * i + 1] = offset.y
            offsets[4 * i + 2] = offset.z
            offsets[4 * i + 3] = 0f
        }

        return ParticleBatch(batchCount, offsets)
    }

}