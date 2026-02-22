package xim.resource

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.ActorAssociation
import xim.poc.EffectAssociation
import xim.poc.WeatherAssociation
import xim.poc.game.ActorStateManager
import xim.util.OnceLogger
import xim.util.RandHelper.posRand

class ParticleGenerator(
    val resource: EffectResource,
    val association: EffectAssociation,
    val maxEmitTime: Float = Float.POSITIVE_INFINITY,
    val parent: Particle? = null,
) {

    val datId = resource.id
    val def = resource.particleGenerator
    val localDir = resource.localDir

    private var lifeTime = 0f
    private var framesUntilNextParticle = 0f
    private val activeParticles = ArrayList<Particle>()

    var framesPerEmission = def.framesPerEmission
    var totalParticlesEmitted = 0

    private var stopEmitting = false
    private var invalid = false

    private val genAssociatedPosition = Vector3f()
    private val genAssociatedRotation = Matrix4f()

    val rotation = Vector3f()
    var emitCulled = false

    var attachment: ParticleGeneratorAttachment
    var transitionLink: TransitionParticleEffect? = null

    init {
        val attachmentConfig = ParticleGenerationAttachmentConfig.fromDef(this, def)
        attachment = ParticleGeneratorAttachment(datId, attachmentConfig)

        updateAssociatedPosition(0f)
        updateAssociatedFacing(0f)
    }

    fun isExpired(): Boolean {
        return invalid || (activeParticles.isEmpty() && isDoneEmitting())
    }

    private fun isDoneEmitting(): Boolean {
        if (stopEmitting) { return true }
        return !def.autoRun && (lifeTime >= maxEmitTime && totalParticlesEmitted > 0)
    }

    fun stopEmitting() {
        stopEmitting = true
    }

    fun emit(elapsedFrames: Float, preInitialize: ((Particle) -> Unit)? = null): List<Particle> {
        if (invalid) { return emptyList() }

        activeParticles.removeAll { it.isComplete() }

        lifeTime += elapsedFrames
        if (isExpired()) { return emptyList() }

        def.generatorUpdaters.forEach { it.apply(elapsedFrames, this) }
        if (emitCulled || isDoneEmitting()) { return emptyList() }

        if (!checkEnvironmentMatch()) { return emptyList() }

        val newParticles = ArrayList<Particle>()

        framesUntilNextParticle -= elapsedFrames
        while (framesUntilNextParticle <= 0f) {
            if (def.continuousSingleton && activeParticles.size > 0) { break }

            framesUntilNextParticle += framesPerEmission + posRand(def.emissionVariance)

            try {
                val particlesToEmit = if (def.shouldApplyBatchingOptimization(association)) { 1 } else { def.getNumParticlesPerEmission(association) }

                for (i in 0 until particlesToEmit) {
                    val newParticle = generateParticleInternal(parent, preInitialize)
                    newParticles += newParticle
                    activeParticles += newParticle

                    totalParticlesEmitted += 1
                    if (def.continuousSingleton) { break }
                }
            } catch (e: Exception) {
                OnceLogger.error("[$datId] Couldn't generate particle: ${e.message}\n${e.stackTraceToString()}")
                invalid = true
                return emptyList()
            }

            if (isExpired()) { break }
        }

        return newParticles
    }

    private fun generateParticleInternal(parent: Particle?, preInitialize: ((Particle) -> Unit)?) : Particle {
        val subParticles = if (def.shouldApplyBatchingOptimization(association)) {
            Array(def.getNumParticlesPerEmission(association)) { SubParticle() }
        } else {
            null
        }

        val particle = Particle(creator = this, association = association, parent = parent, subParticles = subParticles)
        preInitialize?.invoke(particle)


        for (initializer in def.initializers) {
            initializer.apply(particle)
            if (initializer !is SubParticleInitializer) { continue }
            particle.subParticles?.forEach { initializer.apply(particle, it) }
        }

        particle.onInitialized()
        return particle
    }

    fun updateAssociatedPosition(elapsedFrames: Float) {
        attachment.updateAssociatedPosition(genAssociatedPosition, elapsedFrames)
    }

    fun getAssociatedPosition(): Vector3f {
        return genAssociatedPosition
    }

    fun updateAssociatedFacing(elapsedFrames: Float) {
        attachment.updateAssociatedFacing(genAssociatedRotation, elapsedFrames)
    }

    fun getAssociatedFacing(): Matrix4f {
        return Matrix4f().copyFrom(genAssociatedRotation)
            .rotateZYXInPlace(rotation)
    }

    fun getTotalLifeTime(): Float {
        return lifeTime
    }

    fun getActiveParticles(): List<Particle> {
        return activeParticles
    }

    fun shouldCull(): Boolean {
        // Actor generators are culled as soon as they have no active particles, even if they're configured to run forever.
        // This is needed for a few generators that seem misconfigured, like [gu00] in [Tonko: Ni].
        if (association !is ActorAssociation) { return false }
        return activeParticles.isEmpty()
    }

    fun syncFromParent() {
        if (parent == null || attachment.datId == parent.datId) { return }

        attachment = parent.attachmentSource.attachment
        genAssociatedPosition.copyFrom(parent.attachmentSource.genAssociatedPosition)
        genAssociatedRotation.copyFrom(parent.attachmentSource.genAssociatedRotation)
    }

    private fun checkEnvironmentMatch(): Boolean {
        // Camera-attached weather-effects will only emit particles while the PoV is in the default environment.
        // This prevents rain, snow, etc. from appearing while inside caves, buildings, etc.
        if (association !is WeatherAssociation || totalParticlesEmitted == 0) { return true }

        val config = def.particleConfiguration
        if (!config.followCamera && !config.cameraAttachedBasePosition) { return true }

        val (_, playerEnvironmentId) = ActorStateManager.player().lastCollisionResult.getEnvironment()
        return playerEnvironmentId == null
    }

}
