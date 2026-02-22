package xim.resource

import xim.math.Axis
import xim.math.Vector3f
import xim.poc.ActorAssociation
import xim.poc.ActorManager
import xim.poc.SceneManager
import xim.poc.ZoneAssociation
import xim.util.PI_f

interface ParticleGeneratorUpdater {

    fun read(byteReader: ByteReader, readContext: ReadContext)

    fun apply(elapsedFrames: Float, particleGenerator: ParticleGenerator)

}

abstract class ParticleGeneratorKeyFrameUpdater: ParticleGeneratorUpdater {

    lateinit var keyFrameLink: DatLink<KeyFrameResource>
    var spare = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        expectZero(byteReader.next32())
        keyFrameLink = DatLink(byteReader.nextDatId())
        byteReader.next32() // 0, 1
        spare = byteReader.nextFloat()
    }

    fun getGeneratorProgressValue(particleGenerator: ParticleGenerator, initialValue: Float? = null): Float? {
        val maxLifeTime = particleGenerator.maxEmitTime
        return getProgress(particleGenerator, maxLifeTime, initialValue)
    }

    fun getGeneratorPlusParticleProgressValue(particleGenerator: ParticleGenerator, initialValue: Float? = null): Float? {
        val maxLifeTime = particleGenerator.maxEmitTime + particleGenerator.def.particleConfiguration.maxLifeSpan
        return getProgress(particleGenerator, maxLifeTime, initialValue)
    }

    private fun getProgress(particleGenerator: ParticleGenerator, maxLifeTime: Float, initialValue: Float?): Float? {
        val keyFrameResource = keyFrameLink.getOrPut { getKeyFrameReference(particleGenerator, it) } ?: return null
        val progress = (particleGenerator.getTotalLifeTime() / maxLifeTime).coerceAtMost(1f)
        return keyFrameResource.particleKeyFrameData.getCurrentValue(progress, initialValue)
    }

}

class AssociationUpdater : ParticleGeneratorUpdater {

    var followAttachedPosition = false
    var followAttachedFacing = false
    var followAttachedFactor = 0

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        val config = byteReader.next32()
        followAttachedPosition = (config and 0x1) != 0
        followAttachedFacing = (config and 0x2) != 0

        // the rest of the data seems to indicate how quickly the particle should follow
        // It acts like a rubber-banding parameter; lower values let it get further out
        // As it gets closer, it slows down a bit
        followAttachedFactor = config ushr 2
    }

    override fun apply(elapsedFrames: Float, particleGenerator: ParticleGenerator) {
        if (followAttachedPosition) {
            particleGenerator.updateAssociatedPosition(elapsedFrames)
        }

        if (followAttachedFacing) {
            particleGenerator.updateAssociatedFacing(elapsedFrames)
        }
    }

}

class GeneratorCullUpdater: ParticleGeneratorUpdater {

    private var maxEmitDistance = 0f

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        maxEmitDistance = byteReader.nextFloat()
        byteReader.nextFloat() // unk, -1 ~ 600
        byteReader.next32() // 0, 1
    }

    override fun apply(elapsedFrames: Float, particleGenerator: ParticleGenerator) {
        if (particleGenerator.def.framesPerEmission.isInfinite() || maxEmitDistance == 0f) { return }

        val emitterPosition = getEmitterPosition(particleGenerator)

        val playerPosition = ActorManager.player().displayPosition
        val playerDistance = Vector3f.distance(playerPosition, emitterPosition)

        particleGenerator.emitCulled = playerDistance > maxEmitDistance
    }

    private fun getEmitterPosition(particleGenerator: ParticleGenerator): Vector3f {
        val association = particleGenerator.association
        val def = particleGenerator.def

        val position = if (association is ActorAssociation) {
            association.actor.displayPosition
        } else {
            def.particleConfiguration.basePosition
        }.copy()

        // For ship zones, transform the emitter-position into ship-space
        if (association is ZoneAssociation && def.attachType == AttachType.TargetActorSourceFacing) {
            val areaTransform = SceneManager.getCurrentScene().getAreaTransform() ?: return position
            areaTransform.inverseTransform.transformInPlace(position)
        }

        return position
    }

}

class GeneratorRotationUpdater(val axis: Axis) : ParticleGeneratorKeyFrameUpdater() {

    override fun apply(elapsedFrames: Float, particleGenerator: ParticleGenerator) {
        val rotation = getGeneratorPlusParticleProgressValue(particleGenerator) ?: return
        particleGenerator.rotation[axis] = PI_f * rotation
    }

}

class SphericalPositionUpdater(val updater: (PositionVariance, Float) -> Unit): ParticleGeneratorKeyFrameUpdater() {
    override fun apply(elapsedFrames: Float, particleGenerator: ParticleGenerator) {
        val value = getGeneratorProgressValue(particleGenerator) ?: return

        // Verified this only modifies 0x1F (does not modify 0x06 or 0x07)
        val sphericalPosition = particleGenerator.def.initializers.firstOrNull { it is SphericalPositionVarianceFull } ?: return
        sphericalPosition as SphericalPositionVarianceFull
        updater.invoke(sphericalPosition.positionVariance, value)
    }
}

class EmissionFrequencyUpdater: ParticleGeneratorKeyFrameUpdater() {

    override fun apply(elapsedFrames: Float, particleGenerator: ParticleGenerator) {
        val initialValue = 60f / particleGenerator.def.framesPerEmission
        val frequency = getGeneratorProgressValue(particleGenerator, initialValue) ?: return

        if (frequency <= 0f) { return }
        particleGenerator.framesPerEmission = (60f / frequency)
    }

}

class GeneratorBasePositionUpdater(val axis: Axis): ParticleGeneratorKeyFrameUpdater() {

    override fun apply(elapsedFrames: Float, particleGenerator: ParticleGenerator) {
        val value = getGeneratorPlusParticleProgressValue(particleGenerator) ?: return

        val setup = particleGenerator.def.initializers.firstOrNull { it is StandardParticleSetup } ?: return
        setup as StandardParticleSetup
        setup.config.basePosition[axis] = value
    }

}

class GeneratorVelocityUpdater(val axis: Axis) : ParticleGeneratorKeyFrameUpdater() {

    override fun apply(elapsedFrames: Float, particleGenerator: ParticleGenerator) {
        val velocity = getGeneratorProgressValue(particleGenerator) ?: return

        val setup = particleGenerator.def.initializers.firstOrNull { it is TranslationVelocitySetup } ?: return
        (setup as TranslationVelocitySetup).velocity[axis] = velocity
    }

}

class RelativeVelocityUpdater: ParticleGeneratorKeyFrameUpdater() {
    override fun apply(elapsedFrames: Float, particleGenerator: ParticleGenerator) {
        val velocity = getGeneratorProgressValue(particleGenerator) ?: return

        val setup = particleGenerator.def.initializers.firstOrNull { it is RelativeVelocitySetup } ?: return
        (setup as RelativeVelocitySetup).velocity = velocity
    }

}
