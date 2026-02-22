package xim.resource

import xim.math.Axis
import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.EnvironmentManager
import xim.poc.ScreenFlashCommand
import xim.poc.ScreenFlasher
import xim.poc.camera.CameraReference
import xim.poc.gl.Color
import xim.util.OnceLogger.warn
import xim.util.PI_f
import xim.util.RandHelper
import xim.util.fallOff
import xim.util.toDegrees
import kotlin.math.*

interface ParticleUpdater {
    fun read(byteReader: ByteReader, readContext: ReadContext)
    fun apply(elapsedFrames: Float, particle: Particle)
}

interface SubParticleUpdater {
    fun apply(elapsedFrames: Float, particle: Particle, subParticle: SubParticle)
}

interface NoDataParticleUpdater : ParticleUpdater {
    override fun read(byteReader: ByteReader, readContext: ReadContext) { }
}

class NoOpParticleUpdater: ParticleUpdater {
    override fun read(byteReader: ByteReader, readContext: ReadContext) { }
    override fun apply(elapsedFrames: Float, particle: Particle) { }
}

class PositionUpdater(val allocationOffset: Int) : NoDataParticleUpdater, SubParticleUpdater {
    override fun apply(elapsedFrames: Float, particle: Particle) {
        val transform = particle.getDynamic(allocationOffset) as ParticleTransform
        particle.position += particle.getTotalVelocity(transform) * elapsedFrames
    }

    override fun apply(elapsedFrames: Float, particle: Particle, subParticle: SubParticle) {
        subParticle.position += subParticle.relativeVelocity * elapsedFrames
    }
}

class ScaleUpdater(val allocationOffset: Int) : NoDataParticleUpdater {
    override fun apply(elapsedFrames: Float, particle: Particle) {
        val transform = particle.getDynamic(allocationOffset) as ParticleTransform
        particle.scale += transform.velocity * elapsedFrames
    }
}

class RotationUpdater(val allocationOffset: Int) : NoDataParticleUpdater {
    override fun apply(elapsedFrames: Float, particle: Particle) {
        val transform = particle.getDynamic(allocationOffset) as ParticleTransform
        particle.rotation += transform.velocity * elapsedFrames
    }
}

class VelocityAccelerator(val allocationOffset: Int) : ParticleUpdater {
    private val acceleration = Vector3f()

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        acceleration.copyFrom(byteReader.nextVector3f())
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val transform = particle.getDynamic(allocationOffset) as ParticleTransform
        transform.velocity += acceleration * elapsedFrames
    }

}

class VelocityRotationUpdater(val allocationOffset: Int) : NoDataParticleUpdater {

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val transform = particle.getDynamic(allocationOffset) as ParticleTransform

        // Has the strange side effect of converting all velocity into the +x-axis (forward)
        val magnitude = transform.velocity.magnitude() + transform.relativeVelocity.magnitude()
        transform.velocity.copyFrom(Vector3f.ZERO)
        transform.relativeVelocity.copyFrom(Vector3f.ZERO)
        transform.velocity.x = magnitude

        transform.velocityRotation.copyFrom(particle.rotation)
    }

}

class VelocityRotator(val allocationOffset: Int) : ParticleUpdater {

    private val rotateAmount = Vector3f()

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        rotateAmount.copyFrom(byteReader.nextVector3f())
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val transform = particle.getDynamic(allocationOffset) as ParticleTransform

        // TODO - [ge31] for casting black-magic:
        // It seems like this effect needs to be applied in 'actor' space, where the z-axis is forward
        // Otherwise, z-axis is horizontal, and rotating the relative velocity over the horizontal axis produces the wrong effect
        val hackedRotation = if (particle.isAssociatedToActor()) {
            Vector3f(-rotateAmount.z, rotateAmount.y, rotateAmount.x)
        } else {
            rotateAmount
        }

        // TODO - 0.5 is needed for Ice Spikes to work...
        transform.velocityRotation += hackedRotation * (0.5f * elapsedFrames)
    }

}

class VelocityDampener(val allocationOffset: Int) : ParticleUpdater, SubParticleUpdater {

    private var dampen = 0f
    private var unk = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        dampen = byteReader.nextFloat()
        unk = byteReader.nextFloat()
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val transform = particle.getDynamic(allocationOffset) as ParticleTransform
        val dampeningFactor = getDampeningFactor(elapsedFrames, transform)

        transform.velocity *= dampeningFactor
        transform.relativeVelocity *= dampeningFactor
    }

    override fun apply(elapsedFrames: Float, particle: Particle, subParticle: SubParticle) {
        val transform = particle.getDynamic(allocationOffset) as ParticleTransform
        val dampeningFactor = getDampeningFactor(elapsedFrames, transform)
        subParticle.relativeVelocity *= dampeningFactor.pow(elapsedFrames)
    }

    private fun getDampeningFactor(elapsedFrames: Float, transform: ParticleTransform): Float {
        val dampeningFactor = transform.dampeningFactor ?: dampen
        return dampeningFactor.pow(elapsedFrames)
    }

}

class ProgressValueUpdater(
    val allocationOffset: Int,
    val integrate: Boolean = false,
    val initialValueFn: ((Particle) -> Float)? = null,
    val updateFn: (Particle, Float) -> Unit,
): NoDataParticleUpdater {

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val keyFrameReference = particle.getDynamic(allocationOffset) as KeyFrameReference
        val keyFrameResource = keyFrameReference.keyFrameLink.getOrPut { getKeyFrameReference(particle, it) } ?: return

        if (initialValueFn != null && keyFrameReference.initialValueOverride == null) {
            keyFrameReference.initialValueOverride = initialValueFn.invoke(particle)
        }

        val progress = (keyFrameReference.numCycles * particle.getProgress()).mod(1f)
        val progressValue = keyFrameResource.particleKeyFrameData.getCurrentValue(progress, keyFrameReference.initialValueOverride)
        val value = if (integrate) { progressValue * elapsedFrames } else { progressValue }

        updateFn.invoke(particle, value)
    }

}

open class ClockValueUpdater(val allocationOffset: Int, val updateFn: (Particle, Float) -> Unit) : NoDataParticleUpdater {

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val data = particle.getDynamic(allocationOffset) as KeyFrameReference
        val keyFrameLink = data.keyFrameLink

        val keyFrameResource = keyFrameLink.getOrPut { getKeyFrameReference(particle, it) } ?: return
        val particleKeyFrameData = keyFrameResource.particleKeyFrameData

        val value = particleKeyFrameData.getCurrentValue(EnvironmentManager.getClock().getFullDayInterpolation())
        updateFn.invoke(particle, value)
    }

}

class ClockValueRotationUpdater(allocationOffset: Int, updateFn: (Particle, Float) -> Unit): ClockValueUpdater(allocationOffset, updateFn) {

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        byteReader.nextFloat(4) // Always 0?
    }

}


class SpriteSheetFrameUpdater : NoDataParticleUpdater {

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val spriteSheet = (particle.meshProvider as SpriteSheetMeshProvider).spriteSheet

        val numSprites = spriteSheet.meshes.size + 1
        val progress = particle.getProgress()

        val sprite = floor(numSprites * progress).roundToInt()
        if (sprite >= spriteSheet.meshes.size) {
            particle.spriteSheetIndex = spriteSheet.meshes.size -1
        } else {
            particle.spriteSheetIndex = sprite
        }
    }
}

class TextureCoordinateUpdater(val axis: Axis): ParticleUpdater {

    enum class Axis { X, Y }

    private var translateAmount = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        translateAmount = byteReader.nextFloat()
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        when (axis) {
            Axis.X -> particle.texCoordTranslate.x += translateAmount * elapsedFrames
            Axis.Y -> particle.texCoordTranslate.y += translateAmount * elapsedFrames
        }
    }

}

class PointListPositionUpdater(val allocationOffset: Int): NoDataParticleUpdater {

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val data = particle.getDynamic(allocationOffset) as PointListReference

        val pointListResource = data.pointListId.getOrPut { particle.creator.localDir.getChildAs(it, PointListResource::class) }
        val pointList = pointListResource?.pointList ?: throw IllegalStateException("[${particle.datId}] Failed to find point-list: ${data.pointListId.id}")

        val particleProgress = particle.getProgress()
        val keyFrameResource = data.keyFrameId?.getOrPut { particle.creator.localDir.getChildAs(it, KeyFrameResource::class) }
        val positionProgress = keyFrameResource?.particleKeyFrameData?.getCurrentValue(particleProgress) ?: particleProgress

        val pointListPosition = pointList.getSplinePosition(positionProgress)
        particle.position.copyFrom(pointListPosition)
    }

}

class ColorTransformApplier(val allocationOffset: Int) : NoDataParticleUpdater {
    override fun apply(elapsedFrames: Float, particle: Particle) {
        val colorTransform = particle.getDynamic(allocationOffset) as ColorTransform

        val r = colorTransform.r shr 7
        val g = colorTransform.g shr 7
        val b = colorTransform.b shr 7
        val a = colorTransform.a shr 7

        val transform = Color(r, g, b, a)
        particle.color.addInPlace(transform.withMultiplied(0.5f * elapsedFrames))
    }
}

class ColorTransformModifier(val allocationOffset: Int) : ParticleUpdater {

    private lateinit var modifier: ColorTransform

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        modifier = ColorTransform(
            r = byteReader.next16Signed(),
            g = byteReader.next16Signed(),
            b = byteReader.next16Signed(),
            a = byteReader.next16Signed(),
        )
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val colorTransform = particle.getDynamic(allocationOffset) as ColorTransform
        val rate = elapsedFrames / 30f

        colorTransform.r += floor(modifier.r * rate).toInt()
        colorTransform.g += floor(modifier.g * rate).toInt()
        colorTransform.b += floor(modifier.b * rate).toInt()
        colorTransform.a += floor(modifier.a * rate).toInt()
    }

}

class DayOfWeekColorUpdater : ParticleUpdater {

    private val colors = Array(8) { Color() }

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        expectZero32(byteReader)
        for (i in 0 until 8) { colors[i].copyFrom(byteReader.nextRGBA()) }
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val weekDay = EnvironmentManager.getDayOfWeek()
        particle.colorDayOfWeek = colors[weekDay.index]
    }
}

class MoonPhaseColorUpdater : ParticleUpdater {

    private val colors = Array(12) { Color() }

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        expectZero32(byteReader)
        for (i in 0 until 12) { colors[i].copyFrom(byteReader.nextRGBA()) }
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val moonPhase = EnvironmentManager.getMoonPhase()
        particle.colorMoonPhase = colors[moonPhase.index]
    }
}

class MoonPhaseSpriteSheetUpdater : NoDataParticleUpdater {
    override fun apply(elapsedFrames: Float, particle: Particle) {
        val moonPhase = EnvironmentManager.getMoonPhase()
        particle.spriteSheetIndex = moonPhase.index
    }
}

class DrawDistanceUpdater : ParticleUpdater {

    private var near = 0f
    private var far = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        near = byteReader.nextFloat()
        far = byteReader.nextFloat()
        byteReader.next32() // 0 or 1?
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val position = CameraReference.getInstance().getPosition()
        val particlePos = particle.getWorldSpacePosition()
        val distance = Vector3f.distance(position, particlePos)

        val multiplier = distance.fallOff(near, far)

        particle.drawDistanceCulled = multiplier == 0f
        particle.colorMultiplier.multiplyAlphaInPlace(multiplier)
    }

}

class DoubleRangeDrawDistanceUpdater : ParticleUpdater {

    private lateinit var nearRange: Pair<Float, Float>
    private lateinit var farRange: Pair<Float, Float>

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        nearRange = Pair(byteReader.nextFloat(), byteReader.nextFloat())
        farRange = Pair(byteReader.nextFloat(), byteReader.nextFloat())
        expectZero(byteReader.nextFloat())
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val position = CameraReference.getInstance().getPosition()
        val particlePos = particle.getWorldSpacePosition()

        // See Notes - not sure if this should also be applied to the single-range draw-distance calc
        val distance = Vector3f.distance(position, particlePos) + 1.15f * abs(particle.scale.x)

        val multiplier = doubleRangeWeight(distance, nearRange, farRange)
        particle.drawDistanceCulled = multiplier == 0f
        particle.colorMultiplier.multiplyAlphaInPlace(multiplier)
    }

}

class OscillationApplier(val allocationOffset: Int, val axis: Axis): ParticleUpdater {

    private var oscillationRate = 0f
    private var baseOffset = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        oscillationRate = 180f / byteReader.nextFloat()   // 180f determined empirically ([gyf1] in east saru)
        baseOffset = byteReader.nextFloat()

        byteReader.nextFloat() // no effect?
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        // TODO: In [Stone IV], the first parameter is 0f - what does it mean?
        if (oscillationRate.isInfinite()) { return }

        val transform = particle.getDynamic(allocationOffset) as OscillationParams

        val frequency = PI_f * (particle.age() / oscillationRate)
        val baseAmplitude = 0.5f * (sin(baseOffset + frequency - PI_f/2f) + cos(baseOffset))

        // TODO - this is a rough approximation of the amplitude
        val amplitude = 0.5f * transform.acceleration[axis] * baseAmplitude * oscillationRate
        val delta = amplitude - transform.previousAmplitude[axis]

        val direction = getOscillationDirection(particle)
        particle.position += direction * delta

        transform.previousAmplitude[axis] = amplitude
    }

    private fun getOscillationDirection(particle: Particle): Vector3f {
        val particleTransform = particle.getDynamic(ParticleTransform::class)

        if (particleTransform == null || particleTransform.relativeVelocity.magnitude() < 1e-7f) {
            val direction = Vector3f()
            direction[axis] = 1f
            return direction
        }

        val forward = particleTransform.relativeVelocity.normalize()

        return when (axis) {
            Axis.X -> forward
            Axis.Y -> forward.cross(Vector3f.Z).normalizeInPlace()
            Axis.Z -> forward.cross(Vector3f.Y).normalizeInPlace()
        }
    }


}

class ChildGeneratorUpdater(val allocationOffset: Int, val billBoardType: BillBoardType) : NoDataParticleUpdater {

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val generatorRef = particle.getDynamic(allocationOffset) as GeneratorReference
        val generator = generatorRef.generator

        if (generator == null) {
            warn("[${particle.creator.datId}] Couldn't update child with id: ${generatorRef.id}")
            return
        }

        val worldTransform = Matrix4f()
        particle.computeWorldSpaceTransform(worldTransform)
        if (billBoardType != BillBoardType.None) { worldTransform.identityUpperLeft() }

        // Changing the rotation order seems to have no effect on the child? This is necessary for [Fowl Aubade] & friends
        val particleTransform = Matrix4f()
        particle.computeParticleSpaceOrientationTransform(particleTransform = particleTransform, specialBillBoardType = billBoardType, rotationOrderOverride = RotationOrder.XYZ)

        val transform = Matrix4f()
        worldTransform.multiply(particleTransform, transform)

        val newChildren = generator.emit(elapsedFrames) { updateChild(particle, it, transform) }

        particle.children.forEach {
            if (it.config.followGenerator) { updateChild(particle, it, transform) }
        }

        particle.children += newChildren
    }

    private fun updateChild(parent: Particle, child: Particle, parentTransform: Matrix4f) {
        child.associatedPosition.copyFrom(Vector3f.ZERO)
        child.useParentAssociatedPositionOnly = true

        child.associatedRotation.identity()
        child.useParentOrientation = true

        child.parentOffsetTransform = parentTransform
        if (billBoardType == BillBoardType.None) { child.parentOrientation.copyFrom(parent.associatedRotation) }
    }

}

class ChildGeneratorBasicUpdater(val allocationOffset: Int) : NoDataParticleUpdater {

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val generatorRef = particle.getDynamic(allocationOffset) as GeneratorReference
        val generator = generatorRef.generator

        if (generator == null) {
            warn("[${particle.creator.datId}] Couldn't update child with id: ${generatorRef.id}")
            return
        }

        particle.children += generator.emit(elapsedFrames) {
            it.useParentAssociatedPositionOnly = true
        }
    }
}

class CameraShakeUpdater(val allocationOffset: Int, val opCodeSize: Int): ParticleUpdater {

    private var near = 0f
    private var far = 0f
    private var shakeFactor = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        near = byteReader.nextFloat()
        far = byteReader.nextFloat()
        shakeFactor = if (opCodeSize == 4) { byteReader.nextFloat() } else { 0f }
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val data = particle.getDynamic(allocationOffset) as CameraShakeReference
        val keyFrameMultiplier = data.keyFrameId.getOrPut { getKeyFrameReference(particle, it) } ?: return

        val progressMultiplier = keyFrameMultiplier.particleKeyFrameData.getCurrentValue(particle.getProgress())

        val camera = CameraReference.getInstance()
        val particlePosition = particle.getWorldSpacePosition()

        val distance = Vector3f.distance(camera.getPosition(), particlePosition)
        val distanceMultiplier = distance.fallOff(near, far)

        // The values given are crazy small - not sure how they're actually used
        val finalShakeFactor = (1000f * progressMultiplier * distanceMultiplier * shakeFactor)
            .coerceAtMost(0.33f)

        // Maybe use a smooth noise function instead of pure random?
        camera.applyShake(RandHelper.rand() * finalShakeFactor)
    }

}

class DaylightBasedColorApplier(val allocationOffset: Int): ParticleUpdater {
    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        expectZero32(byteReader)
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        particle.getDynamic(allocationOffset) as DaylightBasedColorMultiplier // Not sure why initialization is needed...

        val lighting = EnvironmentManager.getMainAreaModelLighting(environmentId = particle.creator.def.environmentId)
        val strongestLight = lighting.lights.map { it.color }.maxByOrNull { it.r() + it.g() + it.b() } ?: return
        particle.colorMultiplier.modulateRgbInPlace(strongestLight, 1f)
    }

}

class ScreenFlashApplier: ParticleUpdater {

    private var near = 0f
    private var far = 0f

    private var nearAngleDistance = 0f
    private var farAngleDistance = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        near = byteReader.nextFloat()
        far = byteReader.nextFloat()

        nearAngleDistance = byteReader.nextFloat()
        farAngleDistance = byteReader.nextFloat()

        byteReader.next32() // 0
        byteReader.next32() // [0,1]
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val camera = CameraReference.getInstance()

        val cameraPos = camera.getPosition()
        val particlePos = particle.getWorldSpacePosition()

        val distance = Vector3f.distance(cameraPos, particlePos)

        var alphaMultiplier = distance.fallOff(near, far)
        if (alphaMultiplier <= 0f) { return }

        val cameraSpacePos = camera.getViewMatrix().transform(particlePos)
        if (cameraSpacePos.z > 0) { return }

        val fov = camera.getFoV() ?: return
        val zoomFactor = (fov.toDegrees() * 10f) / cameraSpacePos.z
        val angularDistance = (zoomFactor * cameraSpacePos.x).pow(2) + (zoomFactor * cameraSpacePos.y).pow(2)

        alphaMultiplier *= angularDistance.fallOff(nearAngleDistance, farAngleDistance)

        ScreenFlasher.addScreenFlash(ScreenFlashCommand(
            color = particle.getColor().withMultipliedAlpha(alphaMultiplier)
        ))
    }

}

class DoubleRangeWeightedMeshUpdater : ParticleUpdater {

    private lateinit var nearRange: Pair<Float, Float>
    private lateinit var farRange: Pair<Float, Float>

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        nearRange = Pair(byteReader.nextFloat(), byteReader.nextFloat())
        farRange = Pair(byteReader.nextFloat(), byteReader.nextFloat())
        expectZero(byteReader.nextFloat())
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        if (particle.weightedMeshWeights.size < 2) { throw IllegalStateException("[${particle.datId}] Need more weights for double-range!") }

        val position = CameraReference.getInstance().getPosition()
        val particlePos = particle.getWorldSpacePosition()
        val distance = Vector3f.distance(position, particlePos)

        val weight = doubleRangeWeight(distance, nearRange, farRange)
        particle.weightedMeshWeights[0] = weight
        particle.weightedMeshWeights[1] = 1f - weight
    }

}

class OcclusionUpdater: ParticleUpdater {

    private var size = 0f
    private var baseOpacity = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        size = byteReader.nextFloat()
        baseOpacity = byteReader.nextFloat()
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        particle.occlusionSettings = if (particle.colorMultiplier.a() > 0f) {
            OcclusionSettings(size = size)
        } else {
            null
        }
    }

}

class AngularDistanceRotationUpdater: ParticleUpdater {

    private var angularDistanceFactor = 0f
    private var constantFactor = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        angularDistanceFactor = byteReader.nextFloat()
        constantFactor = byteReader.nextFloat()
        expectZero32(byteReader)
    }

    override fun apply(elapsedFrames: Float, particle: Particle) {
        val camera = CameraReference.getInstance()

        val cameraPos = camera.getPosition()
        val particlePos = particle.getWorldSpacePosition()
        val distance = Vector3f.distance(cameraPos, particlePos)

        val particleDir = (particlePos - cameraPos).normalizeInPlace()
        val angle = 16f * acos(camera.getViewVector().dot(particleDir)) // 16 is approximate

        particle.rotation.z = -(constantFactor + angularDistanceFactor * (angle + distance))
    }

}

fun getKeyFrameReference(particle: Particle, id: DatId) : KeyFrameResource? {
    return getKeyFrameReference(particle.creator, id)
}

fun getKeyFrameReference(particleGenerator: ParticleGenerator, id: DatId) : KeyFrameResource? {
    val localDir = particleGenerator.localDir

    val localRef = localDir.getNullableChildAs(id, KeyFrameResource::class)
    if (localRef != null) {
        return localRef
    }

    val desperateSearch = localDir.findFirstInEntireTreeById(id, KeyFrameResource::class)
    if (desperateSearch != null) {
        return desperateSearch
    }

    warn("[${particleGenerator.datId}] Couldn't find key-frame-data with ID: $id")
    return null
}

private fun doubleRangeWeight(distance: Float, nearRange: Pair<Float, Float>, farRange: Pair<Float, Float>): Float {
    return if (distance < nearRange.first) { // anything closer than near is invisible
        0f
    } else if (distance < nearRange.second) { // "near" interpolation range
        1f - (nearRange.second - distance) / (nearRange.second - nearRange.first)
    } else if (distance < farRange.first) { // anything between interpolation ranges is fully visible
        1f
    } else if (distance < farRange.second) { // "far" interpolation range
        1f - (distance - farRange.first) / (farRange.second - farRange.first)
    } else { // anything farther than far is 0
        0f
    }
}