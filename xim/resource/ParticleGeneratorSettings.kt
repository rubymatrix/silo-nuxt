package xim.resource

import js.typedarrays.Float32Array
import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.EffectAssociation
import xim.poc.WeatherAssociation
import xim.poc.camera.CameraReference
import xim.poc.gl.ByteColor
import xim.poc.gl.Color
import xim.util.PI_f
import xim.util.RandHelper
import kotlin.math.PI
import kotlin.math.pow

abstract class DynamicParticleData

class OscillationParams : DynamicParticleData() {
    val acceleration = Vector3f()
    val previousAmplitude = Vector3f()
}

data class ColorTransform(
    var r: Int = 0,
    var g: Int = 0,
    var b: Int = 0,
    var a: Int = 0,
) : DynamicParticleData()

enum class ActorScaleTarget {
    None, Source, Target
}

data class ActorScaleParams(
    val scaleSize: ActorScaleTarget,
    val scalePosition: ActorScaleTarget,
    val scaleSizeAmount: Float,
    val scalePositionAmount: Float,
)

class ProjectionZBias(
    val param0: Float,
    val param1: Float
)

class OcclusionSettings(
    val size: Float,
)

abstract class ParticleTransform(
    val velocity: Vector3f = Vector3f(),
    val relativeVelocity: Vector3f = Vector3f(),
    val velocityRotation: Vector3f = Vector3f(),
) : DynamicParticleData() {
    var dampeningFactor: Float? = null
}

class PositionTransform: ParticleTransform()
class RotationTransform: ParticleTransform()
class ScaleTransform: ParticleTransform()

data class KeyFrameReference (
    val keyFrameLink: DatLink<KeyFrameResource>,
    val numCycles: Int,
) : DynamicParticleData() {
    var initialValueOverride: Float? = null
}

data class GeneratorReference (
    val id: DatId,
) : DynamicParticleData() {
    var generator: ParticleGenerator? = null
}

data class PointListReference (
    val keyFrameId: DatLink<KeyFrameResource>?,
    val pointListId: DatLink<PointListResource>,
) : DynamicParticleData()

class CameraShakeReference(
    val keyFrameId: DatLink<KeyFrameResource>,
) : DynamicParticleData()

class DaylightBasedColorMultiplier(): DynamicParticleData()

data class RingParams(
    val layerRadius: List<Float>,
    val layerColor: List<ByteColor>,
    val verticesPerLayer: Int,
    val numLayers: Int,
    val textureLink: TextureLink? = null,
)

class PointLightParams(var range: Float, var theta: Float, var rangeMultiplier: Float, var thetaMultiplier: Float) {

    constructor(other: PointLightParams): this(other.range, other.theta, other.rangeMultiplier, other.thetaMultiplier)

    fun copyFrom(other: PointLightParams) {
        this.range = other.range
        this.theta = other.theta
        this.rangeMultiplier = other.rangeMultiplier
        this.thetaMultiplier = other.thetaMultiplier
    }
}

data class PositionVariance (
    var radiusVariance: Float,
    var baseRadius: Float,

    val radiusScaleX: Float = 1f,
    val radiusScaleY: Float = 1f,
    val radiusScaleZ: Float = 1f,

    var rotationZAxis: Float = 0f,
    var rotationYAxis: Float = 0f,

    val tilt: Float = 0f,
    val tiltVariance: Float = PI_f,

    val cameraOriented: Boolean = false,
    val rotationDivisor: Int = 1,
) {

    fun getOffset(particle: Particle): Vector3f {
        val phi = if (rotationDivisor == 1) {
            RandHelper.random.nextDouble(0.0, 2* PI)
        } else {
            PI + (2 * PI) / rotationDivisor * (particle.creator.totalParticlesEmitted % rotationDivisor)
        }

        val random = if (radiusVariance == 0f) { 0f } else { RandHelper.posRand(1.0f).pow(1f/3f) }
        val translate = Vector3f(baseRadius + radiusVariance * random, 0f, 0f)
        val tiltAngle = tilt + tiltVariance * RandHelper.rand()

        val transform = Matrix4f()
            .rotateYInPlace(rotationYAxis)
            .rotateZInPlace(rotationZAxis)
            .scaleInPlace(radiusScaleX, radiusScaleY, radiusScaleZ)
            .rotateYInPlace(phi.toFloat())
            .rotateZInPlace(tiltAngle)
            .translateInPlace(translate)

        if (cameraOriented && !particle.config.localPositionInCameraSpace) {
            val bbDir = CameraReference.getInstance().getViewVector()
            val bbTransform = Matrix4f().axisBillboardInPlace(bbDir)
            bbTransform.multiply(transform, transform)
        }

        return transform.getTranslationVector()
    }

}

enum class AttachType(val flag: Int) {
    None(0x0),
    SourceActor(0x1),
    TargetActor(0x2),
    SourceToTargetBasis(0x3),
    TargetActorSourceFacing(0x04),
    SourceActorTargetFacing(0x05),
    TargetToSourceBasis(0x6),
    SourceActorWeapon(0x9),
    ZoneActor0xA(0xA),
    ZoneActor0xB(0xB),
    ZoneActor0xC(0xC),
    Sun(0xE),
    Moon(0xF),
    ;

    companion object {
        fun from(flag: Int): AttachType? {
            return AttachType.values().firstOrNull { it.flag == flag }
        }
    }

}

enum class BillBoardType {
    None,
    XYZ,
    XZ,
    Camera,
    Movement,
    MovementHorizontal,
}

enum class LinkedDataType(val value: Int) {
    Actor(0x01),
    StaticMesh(0x0B),
    SpriteSheet(0x0E),
    WeightedMesh(0x1D),
    Distortion(0x22),
    RingMesh(0x24),
    LensFlare(0x39),
    Audio(0x3D),
    PointLight(0x47),
    Null(0x57),
    Unknown(-1),
    ;

    companion object {
        fun fromValue(value: Int): LinkedDataType {
            return LinkedDataType.values().firstOrNull { it.value == value } ?: Unknown
        }
    }

}

enum class RotationOrder {
    XYZ,
    ZYX,
}

data class SpecularParams (
    val rotation: Vector3f,
    val textureRefLink: DatLink<TextureResource>?,
    val color: Color,
)

data class ParticleGeneratorDefinition(val datId: DatId) {

    lateinit var particleConfiguration: StandardParticleConfiguration
    lateinit var actorScaleParams: ActorScaleParams

    val generatorUpdaters = ArrayList<ParticleGeneratorUpdater>()
    val initializers = ArrayList<ParticleInitializer>()
    val updaters = ArrayList<ParticleUpdater>()
    val expirationHandlers = ArrayList<ParticleExpirationHandler>()

    var unkId: Int = 0
    var environmentId: DatId? = null

    var attachType: AttachType = AttachType.None

    var attachedJoint0 = 0
    var attachedJoint1 = 0

    var attachSourceOriented = false

    var emissionVariance = 0f
    var framesPerEmission = 0f
    var particlesPerEmission = 0

    var continuousSingleton = false
    var autoRun = false

    var batched = false

    fun getNumParticlesPerEmission(association: EffectAssociation): Int {
        if (continuousSingleton) { return 1 }
        if (association !is WeatherAssociation) { return particlesPerEmission + 1 }

        // For all(?) weather effects, emit fewer particles. Batched particles are less impacted.
        // In-memory, there's a flag (0x01) at 0xBE from the section-header that indicates if this logic is enabled.
        val batchMultiplier = if (batched) { 2 } else { 1 }
        return ((particlesPerEmission * batchMultiplier) / 3) + 1
    }

    fun shouldApplyBatchingOptimization(association: EffectAssociation): Boolean {
        return batched
    }

}
