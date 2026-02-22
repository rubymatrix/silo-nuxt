package xim.poc

import xim.math.Vector3f
import xim.poc.game.AttackContext
import xim.poc.game.AttackContexts
import xim.poc.game.configuration.constants.SkillId
import xim.poc.gl.Color
import xim.poc.gl.PointLight
import xim.poc.tools.ParticleGenTool
import xim.resource.*
import xim.util.Fps
import xim.util.Fps.secondsToFrames
import xim.util.interpolate
import kotlin.time.Duration

sealed interface EffectAssociation {
    fun copy(): EffectAssociation { return this }
}

class ZoneAssociation(val area: Area): EffectAssociation {
    override fun equals(other: Any?): Boolean {
        if (other !is ZoneAssociation) { return false }
        return area.resourceId == other.area.resourceId
    }

    override fun hashCode(): Int {
        return area.resourceId.hashCode()
    }
}

data class WeatherAssociation(val weatherId: DatId) : EffectAssociation

class ActorAssociation(val actor: Actor, val context: ActorContext = ActorContext(actor.id)): EffectAssociation {

    fun getAttackContextForTarget(): AttackContext {
        return context.attackContexts[context.primaryTargetId]
    }

    override fun copy(): EffectAssociation {
        return ActorAssociation(actor, context.copy())
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ActorAssociation) { return false }
        return actor.id == other.actor.id
    }

    override fun hashCode(): Int {
        return actor.id.hashCode()
    }
}

data class ActorContextEffectOverride(
    var scaleMultiplier: Vector3f? = null,
    var colorOverride: ((Particle, Color) -> Color)? = null,
)

data class MobSkillToBlueMagicOverride(
    val movementLockDuration: Int?,
    val standardJointRemap: Map<Int, Int> = emptyMap(),
)

data class ActorContext(
    val originalActor: ActorId,
    val primaryTargetId: ActorId = originalActor,
    val allTargetIds: List<ActorId> = listOf(primaryTargetId),
    val joint: JointInstance? = null,
    val terrainEffect: Boolean = false,
    val modelSlot: ItemModelSlot? = null,
    val attackContexts: AttackContexts = AttackContexts.noop(),
    var skillId: SkillId? = null,
    var targetAoeCenter: Vector3f? = null,
    var movementLockOverride: Int? = null,
    val jointOverride: MutableMap<Int, Int> = HashMap(),
) {

    private data class ActorSnapshot(val position: Vector3f, val facing: Float)
    private data class ExtendedJointKey(val from: ActorId, val to: ActorId, val ref: Int)

    private var snapshotPositions = false
    private val positionSnapshots = HashMap<ActorId, ActorSnapshot>()

    private var snapshotJoints = false
    private val jointSnapshots = HashMap<Actor, HashMap<Int, Vector3f>>()
    private var resolvedExtendedJoints = HashMap<ExtendedJointKey, Int>()

    fun cloneWithOverrideTarget(targetActor: ActorId): ActorContext {
        return this.copy(primaryTargetId = targetActor, targetAoeCenter = null)
    }

    fun applyJointSnapshot(state: Boolean) {
        snapshotJoints = state
    }

    fun getJointPosition(actor: Actor, standardJointIndex: Int): Vector3f {
        val actorJointLock = jointSnapshots.getOrPut(actor) { HashMap() }

        return if (snapshotJoints) {
            actorJointLock.getOrPut(standardJointIndex) { actor.getJointPosition(standardJointIndex) }
        } else {
            actorJointLock.getOrElse(standardJointIndex) { actor.getJointPosition(standardJointIndex) }
        }
    }

    fun getStandardJointExtended(referenceIndex: Int, fromActor: Actor, toActor: Actor): Int {
        val key = ExtendedJointKey(fromActor.id, toActor.id, referenceIndex)
        val toSkeleton = toActor.actorModel?.getSkeleton() ?: return 0

        return if (snapshotJoints) {
            resolvedExtendedJoints.getOrPut(key) { toSkeleton.getStandardJointExtended(referenceIndex, fromActor, toActor) }
        } else {
            resolvedExtendedJoints.getOrElse(key) { toSkeleton.getStandardJointExtended(referenceIndex, fromActor, toActor) }
        }
    }

    fun applyPositionSnapshot() {
        snapshotPositions = true
    }

    fun getActorPosition(actorId: ActorId, isTarget: Boolean = false): Vector3f? {
        val targetPositionOverride = targetAoeCenter
        if (isTarget && targetPositionOverride != null) { return targetPositionOverride }

        return getActorSnapshot(actorId)?.position
    }

    fun getActorFacingDir(actorId: ActorId): Float? {
        return getActorSnapshot(actorId)?.facing
    }

    private fun getActorSnapshot(actorId: ActorId): ActorSnapshot? {
        val actor = ActorManager[actorId] ?: return null
        return if (snapshotPositions) {
            positionSnapshots.getOrPut(actorId) { ActorSnapshot(actor.displayPosition.copy(), actor.displayFacingDir) }
        } else {
            ActorSnapshot(actor.displayPosition, actor.displayFacingDir)
        }
    }

    fun invokeAllCallbacks() {
        attackContexts.getAll().forEach { it.invokeCallback() }
    }


}

class ParticleLight(val particle: Particle, val pointLight: PointLight)

class EffectLighting(private val particles: Map<DatId, List<ParticleLight>>) {
    fun getPointLights(datId: DatId, characterMode: Boolean) : List<PointLight> {
        val pointLightParticles = particles[datId] ?: return emptyList()

        return pointLightParticles
            .filter { !it.particle.isCharacterLight() || (it.particle.isCharacterLight() && characterMode) }
            .map { it.pointLight }
    }
}

class ParticleContext(val particle: Particle, val opacity: Float)

class FadeParameters(private val opacityStart: Float, private val opacityEnd: Float, private val duration: Float, val removeOnComplete: Boolean) {

    companion object {
        val noFade = FadeParameters(opacityStart = 1f, opacityEnd = 1f, duration = 0f, removeOnComplete = false)

        fun defaultFadeIn(): FadeParameters {
            return FadeParameters(opacityStart = 0.0f, opacityEnd = 1.0f, duration = secondsToFrames(3.33f), removeOnComplete = false)
        }

        fun defaultFadeOut(): FadeParameters {
            return FadeParameters(opacityStart = 1.0f, opacityEnd = 0.0f, duration = secondsToFrames(3.33f), removeOnComplete = true)
        }

        fun fadeIn(duration: Duration): FadeParameters {
            return fadeIn(Fps.toFrames(duration))
        }

        fun fadeOut(duration: Duration, removeOnComplete: Boolean = true): FadeParameters {
            return fadeOut(Fps.toFrames(duration), removeOnComplete)
        }

        fun fadeIn(duration: Float): FadeParameters {
            return FadeParameters(opacityStart = 0.0f, opacityEnd = 1.0f, duration = duration, removeOnComplete = false)
        }

        fun fadeOut(duration: Float, removeOnComplete: Boolean = true): FadeParameters {
            return FadeParameters(opacityStart = 1.0f, opacityEnd = 0.0f, duration = duration, removeOnComplete = removeOnComplete)
        }
    }

    private var counter = 0f

    fun update(elapsedFrames: Float): Boolean {
        val complete = isComplete()
        counter += elapsedFrames
        return !complete && isComplete()
    }

    fun shouldRemove(): Boolean {
        return removeOnComplete && isComplete()
    }

    fun isComplete(): Boolean {
        return counter >= duration
    }

    fun getOpacity(): Float {
        if (duration == 0f) { return opacityEnd }
        return opacityStart.interpolate(opacityEnd, counter / duration).coerceIn(0f, 1f)
    }

}

private class EffectRoutines {

    val effectRoutines: MutableList<EffectRoutineInstance> = ArrayList()
    var fadeParameters: FadeParameters = FadeParameters.noFade

    fun update(elapsedFrames: Float) {
        val itr = effectRoutines.iterator()

        while (itr.hasNext()) {
            val routine = itr.next()

            routine.update(elapsedFrames)
            if (!routine.isComplete()) { continue }

            routine.onAllEffectsCompleted.forEach { it.onComplete() }
            itr.remove()
        }

        fadeParameters.update(elapsedFrames)
        if (fadeParameters.shouldRemove()) {
            effectRoutines.clear()
        }
    }

    fun shouldRemove(): Boolean {
        return effectRoutines.isEmpty() && fadeParameters.isComplete()
    }

}

object EffectManager {

    private val routines: HashMap<EffectAssociation, EffectRoutines> = HashMap()

    fun update(elapsedFrames: Float) {
        routines.values.forEach { it.update(elapsedFrames) }
        routines.entries.removeAll { it.value.shouldRemove() }
    }

    fun getLightingParticles(): EffectLighting {
        val lightingParticles = getAllParticles()
            .filter { it.particle.isPointLight() }
            .map { ParticleLight(it.particle, it.particle.getPointLightParams()) }
            .filter { ParticleGenTool.isDrawingEnabled(it.particle) }
            .groupBy { it.particle.datId }

        return EffectLighting(lightingParticles)
    }

    fun getAllParticles() : List<ParticleContext> {
        return routines.entries.map { entry ->
            val particles = entry.value.effectRoutines.map { it.getParticles() }.flatten()
            val allParticles = particles + particles.flatMap { it.getChildrenRecursively() }
            allParticles.map { ParticleContext(it, opacity = entry.value.fadeParameters.getOpacity()) }
        }.flatten()
    }

    fun registerActorRoutine(actor: Actor, actorContext: ActorContext, effectRoutineResource: EffectRoutineResource) : EffectRoutineInstance {
        return registerRoutine(ActorAssociation(actor, actorContext), effectRoutineResource)
    }

    fun applyFadeParameter(effectAssociation: EffectAssociation, fadeParameters: FadeParameters) {
        val effectRoutines = routines.getOrPut(effectAssociation) { EffectRoutines() }
        effectRoutines.fadeParameters = fadeParameters
    }

    fun clearEffects(effectAssociation: EffectAssociation) {
        val effects = routines[effectAssociation]?.effectRoutines ?: return
        effects.forEach { it.stop() }
        routines.remove(effectAssociation)
    }

    fun clearAllEffects() {
        routines.values.forEach { e -> e.effectRoutines.forEach { it.stop() } }
        routines.clear()
    }

    fun getFirstEffectForAssociation(effectAssociation: EffectAssociation, id: DatId): DatResource? {
        val associationRoutines = routines[effectAssociation]?.effectRoutines ?: return null
        return associationRoutines.firstOrNull { it.routineId == id }?.initialRoutine
    }

    fun forEachEffectForAssociation(effectAssociation: EffectAssociation, predicate: (EffectRoutineInstance) -> Unit) {
        routines[effectAssociation]?.effectRoutines?.forEach { predicate.invoke(it) }
    }

    fun removeEffectsForAssociation(effectAssociation: EffectAssociation, datId: DatId) {
        removeEffectsForAssociation(effectAssociation) { it.routineId == datId }
    }

    fun removeEffectsForAssociation(effectAssociation: EffectAssociation, predicate: (EffectRoutineInstance) -> Boolean = { true }) {
        routines[effectAssociation]?.effectRoutines?.removeAll { predicate.invoke(it) }
    }

    fun registerEffect(effectAssociation: EffectAssociation, effectResource: EffectResource) : EffectRoutineInstance {
        val effectRoutineInstance = EffectRoutineInstance.fromSingleton(effectResource, effectAssociation)
        return registerRoutine(effectAssociation, effectRoutineInstance)
    }

    fun registerRoutine(effectAssociation: EffectAssociation, effectRoutineResource: EffectRoutineResource) : EffectRoutineInstance {
        val effectRoutineInstance = EffectRoutineInstance.fromResource(effectRoutineResource, effectAssociation)
        return registerRoutine(effectAssociation, effectRoutineInstance)
    }

    private fun registerRoutine(effectAssociation: EffectAssociation, effectRoutineInstance: EffectRoutineInstance): EffectRoutineInstance {
        val associated = routines.getOrPut(effectAssociation) { EffectRoutines() }
        associated.effectRoutines.add(effectRoutineInstance)
        return effectRoutineInstance
    }

}