package xim.resource

import xim.math.Axis
import xim.math.Vector3f
import xim.poc.*
import xim.poc.game.ActorParticle
import xim.poc.game.ActorType
import xim.poc.game.GameEngine
import xim.poc.game.event.DependentSettings
import xim.poc.game.event.InitialActorState
import xim.poc.gl.BlendFunc
import xim.poc.gl.ByteColor
import xim.poc.gl.Color
import xim.util.OnceLogger.warn
import xim.util.RandHelper.posRand
import xim.util.RandHelper.rand
import kotlin.math.pow
import kotlin.math.roundToInt

class StandardParticleConfiguration {
    var billBoardType: BillBoardType = BillBoardType.None
    var rotationOrder: RotationOrder = RotationOrder.XYZ
    var followCamera: Boolean = false
    var scaleBeforeRotate: Boolean = false
    var localPositionInCameraSpace = false

    var depthMask = false
    var lightingEnabled = false
    var cameraSpaceBillboard = false
    var ignoreTextureAlpha = false
    var fogEnabled = false
    var specular = false
    var hazeEffect = false
    var decalEffect = false
    var followGenerator = true
    var drawPriorityOffset = false
    var lowPriorityDraw = false
    var cameraAttachedBasePosition = false

    val basePosition: Vector3f = Vector3f()

    lateinit var linkedDataId: DatLink<DatResource>
    lateinit var linkedDataType: LinkedDataType

    var maxLifeSpan = 0f
    var lifeSpanVariance = 0f
}

class AudioConfiguration {
    var looping = false
    var pathLink: DatLink<PathResource>? = null

    var farDistance = 0f
    var nearDistance = 0f

    var volumeMultiplier = 1f
}

class ReadContext(val datId: DatId, val generatorDefinition: ParticleGeneratorDefinition)

sealed interface ParticleInitializer {
    fun read(byteReader: ByteReader, readContext: ReadContext)
    fun apply(particle: Particle)
}

interface SubParticleInitializer {
    fun apply(particle: Particle, subParticle: SubParticle)
}

interface NoDataParticleInitializer: ParticleInitializer {
    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        // no data
    }
}

class StandardParticleSetup : ParticleInitializer {

    var config = StandardParticleConfiguration()

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        val billboardFlags = byteReader.next16()

        config.scaleBeforeRotate = billboardFlags and 0x0002 != 0
        config.followCamera = billboardFlags and 0x0004 != 0
        config.localPositionInCameraSpace = billboardFlags and 0x000C == 0xC //Seems to do nothing when followCamera is disabled
        config.rotationOrder = if (billboardFlags and 0x0200 != 0) { RotationOrder.ZYX } else { RotationOrder.XYZ }
        config.depthMask = billboardFlags and 0x1000 != 0

        modifyCameraRelativeIfNeeded(readContext.generatorDefinition)

        if (billboardFlags and 0x00C0 == 0xC0) {
            config.billBoardType = BillBoardType.Camera
        } else if (billboardFlags and 0x0081 == 0x81) {
            config.billBoardType = BillBoardType.Movement
        } else if (billboardFlags and 0x0080 != 0) {
            config.billBoardType = BillBoardType.MovementHorizontal
        } else if (billboardFlags and 0x0040 != 0) {
            config.billBoardType = BillBoardType.Movement
        } else if (billboardFlags and 0x4000 != 0) {
            config.billBoardType = BillBoardType.XZ
        } else  if (billboardFlags and 0x0001 != 0) {
            config.billBoardType = BillBoardType.XYZ
        }

        val renderStateFlags = byteReader.next16()
        config.lightingEnabled = (renderStateFlags and 0x0001) != 0
        config.cameraSpaceBillboard = (renderStateFlags and 0x0002) != 0
        config.hazeEffect = (renderStateFlags and 0x0010) != 0 // where the screen is rendered to the particle, and some blur/offset is applied; 0x32 is related
        config.decalEffect = (renderStateFlags and 0x0020) != 0
        config.drawPriorityOffset = (renderStateFlags and 0x0040) != 0
        config.followGenerator = (renderStateFlags and 0x0080) == 0
        config.specular = (renderStateFlags and 0x0100) != 0
        config.fogEnabled = (renderStateFlags and 0x0200) == 0            // bit set -> disables fog
        config.cameraAttachedBasePosition = (renderStateFlags and 0x0400) != 0
        config.lowPriorityDraw = (renderStateFlags and 0x0800) != 0
        config.ignoreTextureAlpha = (renderStateFlags and 0x1000) != 0

        expect32(byteReader, 0x0, 0x0)
        config.linkedDataId = DatLink(byteReader.nextDatId())

        expectFloat(byteReader) { it >= -0f && it <= 0f }
        config.basePosition.copyFrom(byteReader.nextVector3f())

        byteReader.next8() // The size of the dynamically allocated data - modeled differently here
        config.linkedDataType = LinkedDataType.fromValue(byteReader.next8())

        config.maxLifeSpan = byteReader.next16().toFloat()
        config.lifeSpanVariance = byteReader.next16().toFloat()

        if (config.maxLifeSpan == 0f || isDelkfuttHack()) {
            config.maxLifeSpan = Float.POSITIVE_INFINITY // used for "singleton" particles, like the sea and such
            readContext.generatorDefinition.framesPerEmission = Float.POSITIVE_INFINITY // singleton particles should only be emitted once
        }

        byteReader.next16()

        expect32(byteReader, 0x0, 0x1)
        expect32(byteReader, 0x0, 0x0)

        readContext.generatorDefinition.particleConfiguration = config
    }

    override fun apply(particle: Particle) {
        particle.config = config
        particle.meshProvider = ParticleMeshResolver.getParticleMesh(config.linkedDataType, config.linkedDataId, particle.creator.localDir, particle.creator.datId)
        particle.maxAge = particle.config.maxLifeSpan + posRand(particle.config.lifeSpanVariance)

        if (config.linkedDataType == LinkedDataType.Actor) {
            createDummyActor(particle)
        } else if (config.linkedDataType == LinkedDataType.Audio) {
            particle.audioEmitter = AudioEmitter(config.linkedDataId, particle.creator.localDir)
        }
    }

    private fun isDelkfuttHack(): Boolean {
        // In Lower, Middle, & Upper Delkfutt, there are point-light particles with maxLifeSpan == 1f
        // These are 'flashy' due to precision issues (may have 0~2 particles in a given frame) TODO fix this
        // In game, these are disabled (contribute 0 lighting). Here, set them to be singletons instead.
        return config.linkedDataType == LinkedDataType.PointLight && config.maxLifeSpan == 1f
    }

    private fun modifyCameraRelativeIfNeeded(generatorDefinition: ParticleGeneratorDefinition) {
        // A few effects use Source/Target attach + camera-relative, which doesn't really make sense.
        // [Red Lotus Blade] is the only example for target - disabling results in the correct appearance.
        if (generatorDefinition.attachType == AttachType.TargetActor) {
            config.followCamera = false
            config.localPositionInCameraSpace = false
        }

        // Some screen-effects use the SourceActor, which is also strange.
        // For each of these, the attach-type should be ignored instead.
        // Ex: [ROM/161/13.DAT], [ROM3/10/12.DAT], [ROM3/10/9.DAT]
        if (generatorDefinition.attachType == AttachType.SourceActor && config.localPositionInCameraSpace) {
            generatorDefinition.attachType = AttachType.None
        }
    }

    private fun createDummyActor(particle: Particle) {
        // This is a rare effect - only used in a few of Garuda's & Titan's abilities
        val particleAssociation = particle.association as? ActorAssociation ?: return
        val targetId = particleAssociation.actor.state.getTargetId()

        val directory = particle.creator.localDir.getSubDirectoriesRecursively().firstOrNull { it.id == config.linkedDataId.id }
            ?: throw IllegalStateException("[${particle.datId}] Couldn't find sub-directory: ${config.linkedDataId.id}")

        val routine = particle.creator.localDir.getNullableChildAs(particle.datId, EffectRoutineResource::class)
            ?: throw IllegalStateException("[${particle.datId}] Couldn't find model routine!")

        val promise = GameEngine.submitCreateActorState(initialActorState = InitialActorState(
            name = particle.datId.id,
            modelLook = ModelLook.particle(),
            position = Vector3f(particleAssociation.actor.displayPosition),
            type = ActorType.Effect,
            dependentSettings = DependentSettings(particleAssociation.actor.id, ActorParticle(particle)),
        ))

        particle.dummyActorLink = ParticleActorLink(routine.id, promise, directory, targetId, particleAssociation)
    }

}

abstract class SphericalPositionVariance : ParticleInitializer, SubParticleInitializer {

    abstract var positionVariance: PositionVariance

    override fun apply(particle: Particle) {
        if (particle.subParticles != null) { return }
        particle.initialPosition += positionVariance.getOffset(particle)
        particle.initialPositionCameraOriented = positionVariance.cameraOriented
    }

    override fun apply(particle: Particle, subParticle: SubParticle) {
        subParticle.position += positionVariance.getOffset(particle)
    }

}

class SphericalPositionVarianceSimple: SphericalPositionVariance() {

    override lateinit var positionVariance: PositionVariance

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        val radiusVariance = byteReader.nextFloat()
        val baseRadius = byteReader.nextFloat()
        expectZero32(byteReader)

        positionVariance = PositionVariance(
            radiusVariance = radiusVariance,
            baseRadius = baseRadius,
        )
    }

}

class SphericalPositionVarianceMedium: SphericalPositionVariance() {

    override lateinit var positionVariance: PositionVariance

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        val batchMultiplier = if (readContext.generatorDefinition.batched) { 2f } else { 1f }

        val radiusVariance = byteReader.nextFloat() * batchMultiplier
        val baseRadius = byteReader.nextFloat()

        val xScale = byteReader.nextFloat()
        val yScale = byteReader.nextFloat()
        val zScale = byteReader.nextFloat()

        val unk = byteReader.nextFloat() // very tiny float?
        val yRot = byteReader.nextFloat()

        positionVariance = PositionVariance(
            radiusVariance = radiusVariance,
            baseRadius = baseRadius,
            radiusScaleX = xScale,
            radiusScaleY = yScale,
            radiusScaleZ = zScale,
            rotationYAxis = yRot
        )
    }

}

class SphericalPositionVarianceFull: SphericalPositionVariance() {

    override lateinit var positionVariance: PositionVariance

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        val radiusVariance = byteReader.nextFloat()
        val baseRadius = byteReader.nextFloat()

        val radiusScaleX = byteReader.nextFloat()
        val radiusScaleY = byteReader.nextFloat()
        val radiusScaleZ = byteReader.nextFloat()

        val rotationZAxis = byteReader.nextFloat()
        val rotationYAxis = byteReader.nextFloat()

        val tilt = byteReader.nextFloat()
        val tiltVariance = byteReader.nextFloat()

        val cameraOriented = byteReader.next32() == 0x1
        val rotationDivisor = 1 + byteReader.next32()

        positionVariance = PositionVariance(
            radiusVariance = radiusVariance,
            baseRadius = baseRadius,
            radiusScaleX = radiusScaleX,
            radiusScaleY = radiusScaleY,
            radiusScaleZ = radiusScaleZ,
            rotationZAxis = rotationZAxis,
            rotationYAxis = rotationYAxis,
            tilt = tilt,
            tiltVariance = tiltVariance,
            cameraOriented = cameraOriented,
            rotationDivisor = rotationDivisor,
        )
    }

}

class FixedPointPositionVarianceSetup: ParticleInitializer {

    private lateinit var pointReference: DatLink<PointListResource>

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        expectZero(byteReader.next32())
        pointReference = DatLink(byteReader.nextDatId())
        expect32(byteReader, 0x0, 0x1)
    }

    override fun apply(particle: Particle) {
        val pointListResource = pointReference.getOrPut {
            particle.creator.localDir.getNullableChildAs(it, PointListResource::class)
                ?: particle.creator.localDir.getOnlyNullableChildByType(PointListResource::class)
                ?: GlobalDirectory.directoryResource.findFirstInEntireTreeById(it, PointListResource::class)
        }

        if (pointListResource == null) {
            warn("[${particle.creator.resource.path()}] Couldn't find point-set: $pointReference]")
            return
        }

        val points = pointListResource.pointList
        val positionOffset = points.points[particle.creator.totalParticlesEmitted % points.points.size]
        particle.initialPosition.addInPlace(positionOffset)
    }

}

open class TranslationVelocitySetup(val allocationOffset: Int): ParticleInitializer {

    val velocity = Vector3f()

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        velocity.copyFrom(byteReader.nextVector3f())
    }

    override fun apply(particle: Particle) {
        val transform = particle.allocate(allocationOffset, PositionTransform())
        transform.velocity.copyFrom(velocity)
    }

}

open class RotationVelocitySetup(val allocationOffset: Int): ParticleInitializer {

    private val velocity = Vector3f()

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        velocity.copyFrom(byteReader.nextVector3f())
    }

    override fun apply(particle: Particle) {
        val transform = particle.allocate(allocationOffset, RotationTransform())
        transform.velocity.copyFrom(velocity)
    }

}

open class ScaleVelocitySetup(val allocationOffset: Int): ParticleInitializer {

    private val velocity = Vector3f()

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        velocity.copyFrom(byteReader.nextVector3f())
    }

    override fun apply(particle: Particle) {
        val transform = particle.allocate(allocationOffset, ScaleTransform())
        transform.velocity.copyFrom(velocity)
    }

}

class RelativeVelocitySetup(val allocationOffset: Int) : ParticleInitializer, SubParticleInitializer {

    var velocity = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        velocity = byteReader.nextFloat()
    }

    override fun apply(particle: Particle) {
        val transform = particle.getDynamic(allocationOffset) as ParticleTransform
        transform.relativeVelocity.copyFrom(computeRelativeVelocity(particle.initialPosition))
    }

    override fun apply(particle: Particle, subParticle: SubParticle) {
        subParticle.relativeVelocity.copyFrom(computeRelativeVelocity(subParticle.position))
    }

    private fun computeRelativeVelocity(initialPosition: Vector3f): Vector3f {
        val direction = if (initialPosition.magnitudeSquare() == 0f) {
            Vector3f()
        } else {
            initialPosition.normalize()
        }

        return direction * velocity
    }

}

class VelocityVarianceSetup(val allocationOffset: Int): ParticleInitializer {

    private val variance = Vector3f()

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        variance.copyFrom(byteReader.nextVector3f())
    }

    override fun apply(particle: Particle) {
        val transform = particle.getDynamic(allocationOffset) as ParticleTransform
        transform.velocity += Vector3f(variance.x * rand(), variance.y * rand(), variance.z * rand())
    }

}

class RelativeVelocityVarianceSetup(val allocationOffset: Int) : ParticleInitializer {

    private var variance = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        variance = byteReader.nextFloat()
    }

    override fun apply(particle: Particle) {
        val transform = particle.getDynamic(allocationOffset) as ParticleTransform
        if (particle.initialPosition.magnitudeSquare() == 0f) { return }

        val direction = particle.initialPosition.normalize()
        val variance = direction * (variance * rand())
        transform.relativeVelocity += variance
    }

}

class RandomVelocitySetup(val allocationOffset: Int) : ParticleInitializer {

    private var value = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        value = byteReader.nextFloat()
    }

    override fun apply(particle: Particle) {
        val particleTransform = particle.getDynamic(allocationOffset) as ParticleTransform
        val randomValue = value * rand()
        particleTransform.velocity.x = randomValue
        particleTransform.velocity.y = randomValue
        particleTransform.velocity.z = randomValue
    }

}

class ReverseDisplacementSetup(val allocationOffset: Int) : ParticleInitializer {

    private var unk = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        unk = byteReader.nextFloat()
    }

    override fun apply(particle: Particle) {
        val transform = particle.getNullableDynamic(allocationOffset)
        if (transform !is PositionTransform) { return }

        particle.position += particle.getTotalVelocity(transform) * particle.maxAge

        transform.velocity *= -1f
        transform.relativeVelocity *= -1f
    }

}

class KeyFrameValueSetup(val allocationOffset: Int) : ParticleInitializer {

    private lateinit var id: DatLink<KeyFrameResource>
    private var numCycles = 0

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        expect32(byteReader, 0x0, 0x0)
        id = DatLink(byteReader.nextDatId())

        // Bits 0~3 seem to change the interpolation function slightly
        // Bit 4 seems to lock the progress to 0.5?
        // Bits 5~16 are the cycle count
        val config = byteReader.next32()
        numCycles = (config and 0xFFFF shr 5).coerceAtLeast(1)
    }

    override fun apply(particle: Particle) {
        particle.allocate(allocationOffset, KeyFrameReference(id, numCycles))
    }

}

class PointListPositionSetup(val allocationOffset: Int): ParticleInitializer {

    private var keyFrameId: DatLink<KeyFrameResource>? = null
    private lateinit var pointListId: DatLink<PointListResource>

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        expectZero(byteReader.next32()) // in-mem ptr
        keyFrameId = DatLink.of(byteReader.nextDatId().toNullIfZero())
        expectZero(byteReader.next32())
        expectZero(byteReader.next32()) // in-mem ptr
        pointListId = DatLink(byteReader.nextDatId())
    }

    override fun apply(particle: Particle) {
        particle.allocate(allocationOffset, PointListReference(keyFrameId = keyFrameId, pointListId = pointListId))
    }

}

class ColorSetup : ParticleInitializer {

    private val color = Color(0f, 0f, 0f, 0f)

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        color.copyFrom(byteReader.nextRGBA())
    }

    override fun apply(particle: Particle) {
        particle.color.copyFrom(color)
    }

}

class ColorVarianceSetup : ParticleInitializer {

    private val variance = Color(0f, 0f, 0f, 0f)

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        variance.copyFrom(byteReader.nextRGBA())
    }

    override fun apply(particle: Particle) {
        for (i in 0 until 4) {
            particle.color.rgba[i] += variance.rgba[i] * posRand(1f)
        }
    }

}

class UniformColorVarianceSetup : ParticleInitializer {

    private var variance = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        variance = (byteReader.next32() and 0xFF) / 255f
    }

    override fun apply(particle: Particle) {
        val factor = variance * posRand(1f)
        for (i in 0 until 4) { particle.color.rgba[i] += factor }
    }

}

class ColorTransformSetup(val allocationOffset: Int) : ParticleInitializer {

    private lateinit var colorTransform: ColorTransform

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        colorTransform = ColorTransform(
            r = byteReader.next16Signed(),
            g = byteReader.next16Signed(),
            b = byteReader.next16Signed(),
            a = byteReader.next16Signed(),
        )
    }

    override fun apply(particle: Particle) {
        particle.allocate(allocationOffset, colorTransform.copy())
    }

}

class ColorTransformVariance(val allocationOffset: Int) : ParticleInitializer {

    private lateinit var variance: ColorTransform

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        variance = ColorTransform(
            r = byteReader.next16Signed(),
            g = byteReader.next16Signed(),
            b = byteReader.next16Signed(),
            a = byteReader.next16Signed(),
        )
    }

    override fun apply(particle: Particle) {
        val colorTransform = particle.getDynamic(allocationOffset) as ColorTransform
        colorTransform.r += (posRand(1f) * variance.r).roundToInt()
        colorTransform.g += (posRand(1f) * variance.g).roundToInt()
        colorTransform.b += (posRand(1f) * variance.b).roundToInt()
        colorTransform.a += (posRand(1f) * variance.a).roundToInt()
    }

}


class OscillationSetup(val allocationOffset: Int) : NoDataParticleInitializer {
    override fun apply(particle: Particle) {
        particle.allocate(allocationOffset, OscillationParams())
    }
}

class OscillationAccelerationSetup(val allocationOffset: Int, val axis: Axis) : ParticleInitializer {

    private var acceleration = 0f
    private var accelerationVariance = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        acceleration = byteReader.nextFloat()
        accelerationVariance = byteReader.nextFloat()
    }

    override fun apply(particle: Particle) {
        val transform = particle.getDynamic(allocationOffset) as OscillationParams
        transform.acceleration[axis] = acceleration + accelerationVariance * rand()
    }

}

class ChildGeneratorSetup(val allocationOffset: Int) : ParticleInitializer {

    private lateinit var generatorId: DatLink<EffectResource>

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        expectZero32(byteReader)
        generatorId = DatLink(byteReader.nextDatId())
    }

    override fun apply(particle: Particle) {
        val effectResource = generatorId.getOrPut {
            val localDir = particle.creator.localDir
            localDir.getNullableChildRecursivelyAs(it, EffectResource::class)
                ?: localDir.root().getNullableChildRecursivelyAs(it, EffectResource::class)
        }

        if (effectResource == null) {
            warn("[${particle.creator.datId}] Couldn't find child gen: $generatorId")
            return
        }

        val lifeSpan = if (particle.creator.def.continuousSingleton) { Float.POSITIVE_INFINITY } else { particle.maxAge }

        val generatorRef = particle.allocate(allocationOffset, GeneratorReference(generatorId.id))
        generatorRef.generator = ParticleGenerator(effectResource, particle.creator.association, maxEmitTime = lifeSpan, parent = particle)
    }

}

class OnceChildGeneratorSetup : ParticleInitializer {

    private lateinit var childId: DatId

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        expect32(byteReader, 0x0, 0x0)
        childId = byteReader.nextDatId()
    }

    override fun apply(particle: Particle) {
        val localDir = particle.creator.localDir

        val particleGeneratorResource = localDir.getNullableChildAs(childId, EffectResource::class)
            ?: GlobalDirectory.directoryResource.getNullableChildRecursivelyAs(childId, EffectResource::class)

        if (particleGeneratorResource == null) {
            warn("[${particle.creator.datId}] Couldn't find particle-gen: $childId")
            return
        }

        // Since the children might copy from the parent's state, need to ensure that the associations are updated
        // This is normally only done after the particle is fully initialized
        particle.onInitialized()

        val particleGenerator = ParticleGenerator(particleGeneratorResource, particle.association, parent = particle)
        particle.children += particleGenerator.emit(0f)  {
            it.attachmentSource = particle.creator
        }
    }

}

class ParentPositionCopyConfig : NoDataParticleInitializer {

    override fun apply(particle: Particle) {
        val parent = particle.parent ?: return

        // This applier is completely ignored for children of complex parents
        if (particle.parentOffsetTransform != null) { return }

        // This is an approximation - haven't experimented much with the (Base->Complex->Simple) parent-chain.
        // Complex-children currently use parentOffsetTransform instead of the associations.
        if (parent.parentOffsetTransform != null) {
            particle.associatedPosition.copyFrom(parent.getWorldSpacePosition())
            return
        }

        particle.associatedPosition.copyFrom(parent.associatedPosition)
        particle.parentPositionSnapshot.copyFrom(parent.position + parent.initialPosition + parent.config.basePosition + parent.parentPositionSnapshot + parent.computeProgressPositionOffset())

        // Particles created from EffectRoutine 0x3F need to retain their attach-type
        if (particle.creator.transitionLink != null) { return }

        particle.attachType = parent.attachType
        particle.creator.syncFromParent()
    }

}

class ParentPositionSnapshotConfig : NoDataParticleInitializer {

    override fun apply(particle: Particle) {
        val parent = particle.parent ?: return
        particle.associatedPosition.copyFrom(parent.getWorldSpacePosition())
    }

}


class ParentVelocityConfig(val allocationOffset: Int) : ParticleInitializer {

    private var multiplier = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        multiplier = byteReader.nextFloat()
    }

    override fun apply(particle: Particle) {
        particle.parent ?: return
        val transform = particle.getDynamic(allocationOffset) as ParticleTransform
        transform.velocity.copyFrom(particle.parent.getTotalVelocity() * multiplier)
    }

}

class ParentRotateConfig : NoDataParticleInitializer {

    override fun apply(particle: Particle) {
        if (particle.parent == null) { return }
        particle.rotation.copyFrom(particle.parent.rotation)
    }

}

class ParentScaleConfig : NoDataParticleInitializer {

    override fun apply(particle: Particle) {
        if (particle.parent == null) { return }
        particle.scale.copyFrom(particle.parent.scale)
    }

}

class ParentColorConfig: NoDataParticleInitializer {
    override fun apply(particle: Particle) {
        if (particle.parent == null) { return }
        particle.color.copyFrom(particle.parent.getColor())
    }
}

class ParentTexCoordConfig: NoDataParticleInitializer {
    override fun apply(particle: Particle) {
        if (particle.parent == null) { return }
        particle.texCoordTranslate.copyFrom(particle.parent.texCoordTranslate)
    }
}

class ParentThetaConfig: NoDataParticleInitializer {
    override fun apply(particle: Particle) {
        if (particle.parent == null) { return }
        particle.pointLightParams.theta = particle.parent.pointLightParams.theta
    }
}

class ParentRangeConfig: NoDataParticleInitializer {
    override fun apply(particle: Particle) {
        if (particle.parent == null) { return }
        particle.pointLightParams.range = particle.parent.pointLightParams.range
    }
}


class RotationInitializer : ParticleInitializer {

    val rotation = Vector3f()

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        rotation.copyFrom(byteReader.nextVector3f())
    }

    override fun apply(particle: Particle) {
        particle.rotation.copyFrom(rotation)
    }

}

class RotationVarianceInitializer: ParticleInitializer {

    private val variance = Vector3f()

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        variance.copyFrom(byteReader.nextVector3f())
    }

    override fun apply(particle: Particle) {
        particle.rotation.x += variance.x * rand()
        particle.rotation.y += variance.y * rand()
        particle.rotation.z += variance.z * rand()
    }

}

class IncrementalRotationApplier: ParticleInitializer {

    private val incrementalRotation = Vector3f()

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        incrementalRotation.copyFrom(byteReader.nextVector3f())
    }

    override fun apply(particle: Particle) {
        particle.rotation += incrementalRotation * (1 + particle.creator.totalParticlesEmitted.toFloat())

        // This is weird, but verified that it happens even with (0f,0f,0f)
        // In particular, this is needed for [Upheaval], or else the slashes aren't oriented correctly
        particle.negateRotationY = true
    }

}

class ScaleInitializer : ParticleInitializer {

    val scale = Vector3f()

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        scale.copyFrom(byteReader.nextVector3f())
    }

    override fun apply(particle: Particle) {
        particle.scale.copyFrom(scale)
    }

}

class ScaleVarianceInitializer: ParticleInitializer {

    private val variance = Vector3f()

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        variance.copyFrom(byteReader.nextVector3f())
    }

    override fun apply(particle: Particle) {
        particle.scale += Vector3f(variance.x * posRand(1f), variance.y * posRand(1f), variance.z * posRand(1f))
    }

}

class SingleScaleVarianceInitializer: ParticleInitializer {

    private var variance = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        variance = byteReader.nextFloat()
    }

    override fun apply(particle: Particle) {
        particle.scale += posRand(variance)
    }

}

class RingMeshSetup: ParticleInitializer {

    private lateinit var ringParams: RingParams

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        val layer1Radius = byteReader.nextFloat()
        val layer2Radius = byteReader.nextFloat()
        val layer3Radius = byteReader.nextFloat()
        val layer4Radius = byteReader.nextFloat()

        val layer1Color = byteReader.nextRGBA()
        val layer2Color = byteReader.nextRGBA()
        val layer3Color = byteReader.nextRGBA()
        val layer4Color = byteReader.nextRGBA()

        val verticesPerLayer = byteReader.next8()
        val numLayers = 2 + byteReader.next8()

        expectZero(byteReader.next8())
        expectZero(byteReader.next8())

        val radii = listOf(layer1Radius, layer2Radius, layer3Radius, layer4Radius).subList(0, numLayers)
        val colors = listOf(layer1Color, layer2Color, layer3Color, layer4Color).subList(0, numLayers)
        ringParams = RingParams(radii, colors, verticesPerLayer, numLayers)
    }

    override fun apply(particle: Particle) {
        particle.ringMeshParams = ringParams
    }

}

class SpecularParamsInitializer: ParticleInitializer {

    private lateinit var rotation: Vector3f
    private var textureLink: DatLink<TextureResource>? = null
    private lateinit var color: ByteColor
    private var specFlags = 0

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        rotation = byteReader.nextVector3f()
        textureLink = DatLink.of(byteReader.nextDatId().toNullIfZero())

        expectZero32(byteReader) // in-mem ptr

        // Couldn't notice any difference - thought it might be distance control, but doesn't look like it
        byteReader.nextFloat() // Commonly 10.0
        byteReader.nextFloat() // Commonly 30.0

        color = byteReader.nextRGBA()

        // it does &0x01, &0x02, &0x04, and stores them somewhere
        // 1 -> turning off seems to make it much darker
        // 2 -> turning on seems to give a weird rotation effect?
        // 4 -> didn't notice a difference in Freeze [g000]; these generally don't have a texture
        specFlags = byteReader.next32()
        if (specFlags != 0x01) { warn("[${readContext.datId}] Has unhandled specular flags: $specFlags") }
    }

    override fun apply(particle: Particle) {
        particle.specularParams = SpecularParams(Vector3f(rotation), textureLink, Color(color))
    }

}

class PointLightParamsInitializer: ParticleInitializer {

    private lateinit var pointLightParams: PointLightParams

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        val range = byteReader.nextFloat()
        val theta = byteReader.nextFloat()
        val rangeMultiplier = mapMultiplier(byteReader.nextFloat())
        val thetaMultiplier = mapMultiplier(byteReader.nextFloat())
        pointLightParams = PointLightParams(range = range, theta = theta, rangeMultiplier = rangeMultiplier, thetaMultiplier = thetaMultiplier)
    }

    override fun apply(particle: Particle) {
        particle.pointLightParams = PointLightParams(pointLightParams)
    }

    private fun mapMultiplier(base: Float): Float {
        return if (base >= 0f) {
            2f.pow(base)
        } else if (base >= -1) {
            1f + base
        } else {
            0f
        }
    }

}

class BlendFuncInitializer: ParticleInitializer {

    private lateinit var blendFunc: BlendFunc
    private var alphaOverride: Int? = null

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        val p0 = byteReader.next8() // 41, 42, 43, 44, 46, 48, 49, 64, 68, 84
        val p1 = byteReader.next8()
        expectZero(byteReader.next8())
        expectZero(byteReader.next8())

        if (p0 and 0x20 != 0) {
            alphaOverride = (p1 * 2).coerceAtMost(0xFF)
        }

        val highNibble = (p0 ushr 4) and 0b1101 // alpha-override flag isn't relevant
        val lowNibble = (p0 and 0x0F)

        if (highNibble and 0x01 != 0) {
            blendFunc = BlendFunc.One_Zero
            return
        }

        if (highNibble != 0x04) {
            warn("[${readContext.datId}] Unknown source-blend flag ${highNibble.toString(0x10)}")
        }

        blendFunc = when (lowNibble) {
            0x1 -> BlendFunc.Src_One_RevSub
            0x2 -> BlendFunc.Src_One_RevSub
            0x4 -> BlendFunc.Src_InvSrc_Add
            0x6 -> BlendFunc.Zero_InvSrc_Add
            0x8 -> BlendFunc.Src_One_Add
            else -> {
                warn("[${readContext.datId}] Unknown blend flag ${lowNibble.toString(0x10)}")
                BlendFunc.Src_One_Add
            }
        }
    }

    override fun apply(particle: Particle) {
        particle.blendFunc = blendFunc
        particle.alphaOverride = alphaOverride

        // For most particles, the alpha override bit also disables alpha from the texture
        // However, it doesn't seem to do this only for weighted-meshes...
        // There are very few relevant samples - this is based on the jumping flowers in [La Theine]
        if (alphaOverride != null && particle.association is ZoneAssociation && particle.config.linkedDataType != LinkedDataType.WeightedMesh) {
            particle.config.ignoreTextureAlpha = true
        }
    }

}

// TODO unify with above
class DeferredBlendFuncInitializer: ParticleInitializer {

    private lateinit var blendFunc: BlendFunc

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        val value = byteReader.next32()  // 42, 44, 48

        blendFunc = when (value) {
            0x42 -> BlendFunc.Src_One_RevSub
            0x44 -> BlendFunc.Src_InvSrc_Add
            0x48 -> BlendFunc.Src_One_Add
            else -> {
                warn("[${readContext.datId}] Unknown blend flag ${value.toString(0x10)}")
                BlendFunc.Src_One_Add
            }
        }
    }

    override fun apply(particle: Particle) {
        particle.deferredBlendFunc = blendFunc
    }

}

class DepthBiasInitializer: ParticleInitializer {

    private lateinit var projectionZBias: ProjectionZBias

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        projectionZBias = ProjectionZBias(byteReader.nextFloat(), 0f)
    }

    override fun apply(particle: Particle) {
        particle.projectionBias = projectionZBias
    }

}

class ProjectionBiasInitializer: ParticleInitializer {

    private lateinit var projectionZBias: ProjectionZBias

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        projectionZBias = ProjectionZBias(byteReader.nextFloat(), byteReader.nextFloat())
    }

    override fun apply(particle: Particle) {
        particle.projectionBias = projectionZBias
    }

}

class SpriteSheetInitializer : ParticleInitializer {
    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        byteReader.next32() // Seems unused
    }

    override fun apply(particle: Particle) { }
}

class FootMarkEffectSetup : NoDataParticleInitializer {
    override fun apply(particle: Particle) {
        particle.footMarkEffect = true
    }
}

class GroundProjectionSetup: NoDataParticleInitializer {

    override fun apply(particle: Particle) {
        // This computes the world-space position, which is used to compute the delta to the nearest floor.
        particle.onInitialized()

        val worldSpacePosition = particle.getWorldSpacePosition()
        val nearestFloor = SceneManager.getCurrentScene().getNearestFloorPosition(worldSpacePosition) ?: return

        particle.position.y -= worldSpacePosition.y - nearestFloor.y
    }

}

class AudioRangeSetup: ParticleInitializer {

    private var far = 0f
    private var near = 0f
    private var unk = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        far = byteReader.nextFloat()
        near = byteReader.nextFloat()
        unk = byteReader.nextFloat()
    }

    override fun apply(particle: Particle) {
        // This initializer seems to prevent multiple particles from being simultaneously active
        particle.creator.def.continuousSingleton = true

        if (particle.maxAge.isInfinite()) {
            particle.audioConfiguration.looping = true
        }

        particle.audioConfiguration.farDistance = far
        particle.audioConfiguration.nearDistance = near
    }

}

class PathReferenceSetup : ParticleInitializer {

    lateinit var reference: DatLink<PathResource>

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        reference = DatLink(byteReader.nextDatId())
        val unk0 = byteReader.next32()
        val unk1 = byteReader.next32()
    }

    override fun apply(particle: Particle) {
        reference.getOrPut { particle.creator.localDir.searchLocalAndParentsById(it, PathResource::class) }
        particle.audioConfiguration.pathLink = reference
    }

}

class CameraShakeSetup(val allocationOffset: Int): ParticleInitializer {

    lateinit var reference: DatLink<KeyFrameResource>

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        expectZero32(byteReader)
        reference = DatLink(byteReader.nextDatId())
        val unk0 = byteReader.next32()    // 0, 1
        val unk1 = byteReader.nextFloat() // 0.0 ~ 1.0
        val unk2 = byteReader.next32()    // 0, 1, 2, 3, 4, 5, 7
    }

    override fun apply(particle: Particle) {
        particle.allocate(allocationOffset, CameraShakeReference(reference))
    }

}

class HazeOffsetInitializer: ParticleInitializer {

    var unk = 0f
    var horizontalOffset = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        unk = byteReader.nextFloat()
        horizontalOffset = byteReader.nextFloat()
    }

    override fun apply(particle: Particle) {
        particle.hazeOffset.x = horizontalOffset
    }

}

class PointLightAttachmentSetup: ParticleInitializer {

    private lateinit var pointLightId: DatId

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        pointLightId = byteReader.nextDatId()
        expectZero32(byteReader)
    }

    override fun apply(particle: Particle) {
        particle.attachedPointLights += pointLightId
    }

}

class DaylightBasedColorAdjuster: NoDataParticleInitializer {

    override fun apply(particle: Particle) {
        val lighting = EnvironmentManager.getMainAreaModelLighting(environmentId = particle.creator.def.environmentId)
        val strongestLight = lighting.lights.map { it.color }.maxByOrNull { it.r() + it.g() + it.b() } ?: return
        particle.color.modulateRgbInPlace(strongestLight, 1f)
    }

}

class DaylightBasedColorSetup(val allocationOffset: Int): ParticleInitializer {

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        expectZero32(byteReader)
    }

    override fun apply(particle: Particle) {
        particle.allocate(allocationOffset, DaylightBasedColorMultiplier())
    }

}

class BatchingSetup: ParticleInitializer {

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        warn("[${readContext.datId}] Batching is enabled")
        expectZero32(byteReader)
    }

    override fun apply(particle: Particle) {
        particle.batched = true
    }

}

class ProgressPositionOffsetConfig: ParticleInitializer {

    lateinit var offset: Pair<Vector3f, Vector3f>

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        offset = (byteReader.nextVector3f() to byteReader.nextVector3f())
    }

    override fun apply(particle: Particle) {
        particle.progressOffsetParams = offset
    }

}