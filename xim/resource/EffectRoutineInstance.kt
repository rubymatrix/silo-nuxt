package xim.resource

import xim.math.Vector3f
import xim.poc.*
import xim.poc.audio.AudioManager
import xim.poc.audio.SoundEffectInstance
import xim.poc.browser.DatLoader
import xim.poc.game.ActorStateManager
import xim.poc.game.ActorType
import xim.poc.game.AttackContext
import xim.poc.game.GameState
import xim.poc.game.event.AutoAttackSubType
import xim.poc.game.event.MainHandAutoAttack
import xim.resource.table.FileTableManager
import xim.resource.table.SpellAnimationTable
import xim.util.Fps.secondsToFrames
import xim.util.OnceLogger.warn
import xim.util.Stack
import xim.util.fallOff
import kotlin.reflect.KClass

fun interface RoutineCompleteCallback {
    fun onComplete()
}

private class ParticleGeneratorContext(val sequenceId: DatId, val particleGenerator: ParticleGenerator)

private class EffectResult {
    companion object {
        fun noop(): EffectResult {
            return EffectResult()
        }

        fun of(effect: Effect): EffectResult {
            return of(ArrayList<Effect>().also { it += effect })
        }

        fun of(effects: List<Effect>): EffectResult {
            return EffectResult().also { it.createdEffects.addAll(effects) }
        }

        fun stop(): EffectResult {
            return EffectResult().also { it.shouldStop = true }
        }
    }

    var createdEffects = ArrayList<Effect>()
    var shouldStop = false
}

private class ControlFlowState(val skipped: Boolean) {

    var r0: Int = 0
    var r1: Int = 0
    var r2: Int = 0

    var skipNextBranch = false

    fun onTrueBranch(logId: DatId) {
        skipNextBranch = !evaluateControlFlowExpression(logId)
    }

    fun onFalseBranch(logId: DatId) {
        skipNextBranch = evaluateControlFlowExpression(logId)
    }

    private fun evaluateControlFlowExpression(logId: DatId): Boolean {
        return when (r2) {
            0x0C -> r0 == r1
            0x0E -> r0 < r1
            0x0F -> r0 > r1 // TODO confirm
            0x11 -> r0 == r1 // It's only used in [ctrl] - this is made up for now
            else -> {
                warn("[$logId] Unhandled control flow r2: ${r2.toString(0x10)}. Defaulting to [false]")
                false
            }
        }
    }

}

class EffectRoutineInstance private constructor (
    val routineId: DatId,
    val initialRoutine: EffectRoutineResource,
    val effectAssociation: EffectAssociation,
    private val localDir: DirectoryResource,
) {

    companion object {
        fun fromResource(effectRoutineResource: EffectRoutineResource, effectAssociation: EffectAssociation): EffectRoutineInstance {
            return EffectRoutineInstance(
                routineId = effectRoutineResource.id,
                initialRoutine = effectRoutineResource,
                effectAssociation = effectAssociation,
                localDir = effectRoutineResource.localDir)
        }

        fun fromSingleton(effectResource: EffectResource, effectAssociation: EffectAssociation, duration: Int = 0) : EffectRoutineInstance {
            val singletonDefinition = EffectRoutineDefinition()
            singletonDefinition.effects += ParticleGeneratorRoutine(
                delay = 0,
                duration = duration,
                effectResource.id
            )

            val singletonResource = EffectRoutineResource(effectResource.id, singletonDefinition)
                .also { it.localDir = effectResource.localDir }

            return EffectRoutineInstance(
                routineId = effectResource.id,
                initialRoutine = singletonResource,
                effectAssociation = effectAssociation,
                localDir = effectResource.localDir)
        }
    }

    private val effectSequences = ArrayList<EffectSequence>()
    private val particleGenerators = ArrayList<ParticleGeneratorContext>()
    private val interpolatedEffects = ArrayList<InterpolatedEffect>()
    private val particles = ArrayList<Particle>()
    private val emittedAudio = HashSet<SoundEffectInstance>()
    private val modelSlotVisibilityState = ModelSlotVisibilityState()

    val onAllEffectsCompleted = ArrayList<RoutineCompleteCallback>()

    var repeatOnSequencesCompleted = false
    var animationPlaybackRate = 1f
    var skipTypes = HashSet<KClass<out Effect>>()

    init {
        repeat()
    }

    fun update(elapsedFrames: Float) {
        updateSequences(elapsedFrames)
        updateParticleGenerators(elapsedFrames)
        updateParticles(elapsedFrames)

        updateInterpolatedEffects(elapsedFrames)
        emittedAudio.removeAll { it.isComplete() }

        if (!repeatOnSequencesCompleted && hasCompletedAllSequences() && effectAssociation is ActorAssociation) {
            effectAssociation.context.invokeAllCallbacks()
        }

        if (repeatOnSequencesCompleted && hasCompletedAllSequences()) { repeat() }
    }

    fun stop() {
        effectSequences.forEach { it.stop() }
        effectSequences.clear()

        particleGenerators.clear()
        interpolatedEffects.clear()

        emittedAudio.forEach { it.applyFade(FadeParameters.fadeOut(secondsToFrames(2f/3f))) }
    }

    fun registerAllEffectsCompletedCallback(routineCompleteCallback: RoutineCompleteCallback?) {
        routineCompleteCallback ?: return
        onAllEffectsCompleted += routineCompleteCallback
    }

    private fun repeat() {
        val initialSequence = EffectSequence(initialRoutine, effectAssociation)
        effectSequences += initialSequence
        GameState.getGameMode().onEffectRoutineStarted(effectRoutineInstance = this, initialSequence = initialSequence)
    }

    private fun updateSequences(elapsedFrames: Float) {
        val current = ArrayList(effectSequences)
        current.forEach { it.update(elapsedFrames) }
        effectSequences.removeAll { it.isComplete() }
    }

    private fun updateParticleGenerators(elapsedFrames: Float) {
        particleGenerators.forEach { particles.addAll(it.particleGenerator.emit(elapsedFrames)) }
        particleGenerators.removeAll { it.particleGenerator.isExpired() || it.particleGenerator.shouldCull() }
    }

    private fun updateParticles(elapsedFrames: Float) {
        particles.forEach {
            it.update(elapsedFrames)
            emittedAudio += it.emittedAudio
        }
        particles.removeAll { it.isComplete() }
    }

    private fun updateInterpolatedEffects(elapsedFrames: Float) {
        interpolatedEffects.forEach { it.update(elapsedFrames) }
        interpolatedEffects.removeAll { it.isComplete() }
    }

    fun getParticles() : List<Particle> {
        return particles
    }

    fun hasCompletedAllSequences(): Boolean {
        return effectSequences.isEmpty()
    }

    fun isComplete() : Boolean {
        if (!effectSequences.isEmpty()) { return false }
        if (!particles.all { it.isComplete() }) { return false }
        if (!interpolatedEffects.all { it.isComplete() }) { return false }
        return particleGenerators.all { it.particleGenerator.isExpired() }
    }

    fun getSequences(): List<EffectSequence> {
        return effectSequences
    }

    inner class EffectSequence(val resource: EffectRoutineResource, effectAssociation: EffectAssociation, private val looping: Boolean = false) {

        val id = resource.id
        private val definition = resource.effectRoutineDefinition
        private val localEffectAssociation = effectAssociation.copy()

        private val effectSequence = ArrayDeque(definition.effects)
        private val controlFlowStack = Stack<ControlFlowState>()

        private val blockers = ArrayList<EffectSequence>()

        private var storedFrames: Float = 0f
        private var broadcast: Boolean = false

        var scheduledStartTime: Long? = null
        private var previousTimeOfDay: Long? = null

        private var stopLooping = false

        fun update(elapsedFrames: Float) {
            if (!isAfterScheduledTime()) { return }

            blockers.removeAll { it.isComplete() }
            if (!blockers.isEmpty()) { return }

            storedFrames += elapsedFrames * animationPlaybackRate
            runEffects()

            if (looping && effectSequence.isEmpty()) { onLoop() }
            if (isComplete()) { definition.completionEffects.forEach { it.onComplete(this) } }
        }

        fun isComplete(): Boolean {
            if (looping && !stopLooping) { return false }
            return effectSequence.isEmpty() && blockers.isEmpty() && storedFrames >= 0f
        }

        fun onLoop() {
            if (stopLooping) { return }
            effectSequence += definition.effects
            particleGenerators.filter { it.sequenceId == id }.forEach { it.particleGenerator.stopEmitting() }
        }

        fun prependCustomEffect(effect: Effect) {
            effectSequence.addFirst(effect)
        }

        fun appendCustomEffect(effect: Effect) {
            effectSequence.addLast(effect)
        }

        fun addCustomBroadcast(broadCastedEffect: DatId) {
            val index = effectSequence.indexOfFirst { (it as? LinkedEffectRoutine)?.id == broadCastedEffect }
            if (index == -1) { return }

            effectSequence.add(index, ToggleBroadcastEffect(delay = 0, duration = 0, useBroadcast = true))
            effectSequence.add(index + 2, ToggleBroadcastEffect(delay = 0, duration = 0, useBroadcast = false))
        }

        fun stop() {
            stopLooping = true
            effectSequence.clear()
            blockers.clear()
            storedFrames = 0f
        }

        private fun runEffects() {
            while (storedFrames >= 0f && blockers.isEmpty()) {
                val head = effectSequence.removeFirstOrNull() ?: break

                val currentState = controlFlowStack.peek()
                if (currentState != null && currentState.skipped && (head !is ControlFlowBlock)) { continue }

                storedFrames -= head.delay
                if (skipTypes.contains(head::class)) { continue }

                val result = runEffect(head)
                effectSequence.addAll(0, result.createdEffects)

                if (result.shouldStop) { effectSequence.clear() }
            }

            if (blockers.isNotEmpty()) { storedFrames = storedFrames.coerceAtMost(0f) }
        }

        private fun runEffect(effect: Effect) : EffectResult {
            return when (effect) {
                is ParticleGeneratorRoutine -> createParticleGenerator(effect)
                is LinkedEffectRoutine -> createChild(effect)
                is SoundEffectRoutine -> emitSound(effect)
                is SkeletonAnimationRoutine -> createSkeletonAnimationRoutine(effect)
                is MovementLockEffect -> lockMovement(effect)
                is FacingLockEffect -> lockFacing(effect)
                is AnimationLockEffect -> lockAnimation(effect)
                is TimeBasedReplayRoutine -> createScheduledSequence(effect)
                is ToggleBroadcastEffect -> toggleBroadcast(effect)
                is StartLoopRoutine -> startAssociatedLoop(effect)
                is EndLoopRoutine -> endAssociatedLoop(effect)
                is ControlFlowBlock -> handleControlFlow(effect)
                is ControlFlowBranch -> handleControlFlowBranch(effect)
                is ControlFlowCondition -> handleControlFlowCondition(effect)
                is RandomChildRoutine -> handleRandomChildRoutine(effect)
                is ModelTranslationRoutine -> handleModelTranslationRoutine(effect)
                is ModelRotationRoutine -> handleRotationRoutine(effect)
                is ParticleDampenRoutine -> handleParticleEffectDampen(effect)
                is ActorFadeRoutine -> handleActorFadeRoutine(effect)
                is StartRoutineMarker -> EffectResult.noop()
                is EndRoutineMarker -> EffectResult.noop()
                is ActorWrapUvTranslation -> handleActorWrapUvTranslate(effect)
                is ActorWrapColor -> handleActorWrapColor(effect)
                is ActorWrapTexture -> handleActorWrapTexture(effect)
                is StartRangedAnimationRoutine -> handleStartRangedAnimationRoutine(effect)
                is FinishRangedAnimationRoutine -> handleFinishRangedAnimationRoutine(effect)
                is StopParticleGeneratorRoutine -> handleStopParticleGeneratorRoutine(effect)
                is DisplayRangedModelRoutine -> handleDisplayRangedRoutine(effect)
                is TransitionParticleEffect -> handleParticleTransitionEffect(effect)
                is StopRoutineEffect -> handleStopRoutineEffect(effect)
                is JointSnapshotEffect -> handleJointSnapshotEffect(effect)
                is TransitionToIdleEffect -> handleTransitionToIdle(effect)
                is ActorJumpRoutine -> handleActorJumpRoutine(effect)
                is DualWieldEngageRoutine -> handleDualWieldEngageRoutine(effect)
                is DamageCallbackRoutine -> handleDamageCallbackRoutine(effect)
                is SetModelVisibilityRoutine -> handleSetModelVisibilityRoutine(effect)
                is SpellEffect -> handleSpellEffect(effect)
                is ForwardDisplacementEffect -> handleForwardDisplacementEffect(effect)
                is PointLightInterpolationEffect -> handlePointLightInterpolationEffect(effect)
                is ActorPositionSnapshotEffect -> handleActorPositionSnapshot(effect)
                is ToggleModelVisibilityRoutine -> handleToggleModelVisibilityRoutine(effect)
                is FlinchRoutine -> handleFlinchRoutine(effect)
                is AdjustAnimationModeRoutine -> handleAdjustAnimationModeRoutine(effect)
                is DisplayDeadRoutine -> handleDisplayDeadRoutine(effect)
                is KnockBackRoutine -> handleKnockBackRoutine(effect)
                is AttackBlockedRoutine -> handleAttackBlockedRoutine(effect)
                is AttackCounteredRoutine -> handleAttackCounteredRoutine(effect)
                is LoadBaseModelRoutine -> handleLoadBaseModelRoutine(effect)
                is WeaponTraceRoutine -> handleWeaponTraceRoutine(effect)
                is FollowPointsRoutine -> handleFollowPointsRoutine(effect)
                is NotImplementedRoutine -> EffectResult.noop()
                is DelayRoutine -> EffectResult.noop()
                is CustomRoutine -> { effect.callback.invoke(); EffectResult.noop() }
            }
        }

        private fun createParticleGenerator(particleGeneratorRoutine: ParticleGeneratorRoutine, parent: Particle? = null) : EffectResult {
            particleGenerators += makeParticleGenerators(particleGeneratorRoutine.id, particleGeneratorRoutine.duration, parent)
            return EffectResult.noop()
        }

        private fun makeParticleGenerators(effectId: DatId, duration: Int, parent: Particle?): List<ParticleGeneratorContext> {
            val particleGeneratorResource = findResource(effectId, EffectResource::class)

            if (particleGeneratorResource == null) {
                warn("[$id] Couldn't find particle-gen: $effectId")
                return emptyList()
            }

            return getAssociations().map {
                val pg = ParticleGenerator(particleGeneratorResource, it, maxEmitTime = duration.toFloat(), parent = parent)
                ParticleGeneratorContext(sequenceId = id, particleGenerator = pg)
            }
        }

        private fun createChild(linkedEffectRoutine: LinkedEffectRoutine) : EffectResult {
            val effectRoutineResource = findLinkedRoutineResource(linkedEffectRoutine.id, linkedEffectRoutine.useTarget)

            if (effectRoutineResource == null) {
                warn("[$id] Couldn't find child-routine: ${linkedEffectRoutine.id} [${localDir.id}]")
                return EffectResult.noop()
            }

            val newSequences = if (linkedEffectRoutine.useTarget) {
                localEffectAssociation as ActorAssociation
                val target = ActorManager[localEffectAssociation.context.primaryTargetId] ?: return EffectResult.noop()
                val flippedContext = localEffectAssociation.context.cloneWithOverrideTarget(localEffectAssociation.actor.id)
                listOf(createChildFromResource(effectRoutineResource, ActorAssociation(target, flippedContext)))
            } else {
                getAssociations().map { createChildFromResource(effectRoutineResource, it) }
            }

            if (linkedEffectRoutine.blocking) { blockers += newSequences }

            appendChildSequences(newSequences)
            return EffectResult.noop()
        }

        private fun createChildFromResource(effectRoutineResource: EffectRoutineResource, childAssociation: EffectAssociation, looping: Boolean = false): EffectSequence {
            return EffectSequence(effectRoutineResource, childAssociation, looping)
        }

        private fun appendChildSequence(newSequence: EffectSequence) {
            appendChildSequences(listOf(newSequence))
        }

        private fun appendChildSequences(newSequences: List<EffectSequence>) {
            effectSequences.addAll(0, newSequences)
            if (storedFrames > 0f) { newSequences.forEach { it.update(storedFrames) } }
        }

        private fun <T: DatResource> findResource(resourceId: DatId, type: KClass<T>, useTarget: Boolean = false): T? {
            return if (useTarget || effectAssociation != localEffectAssociation) {
                searchAssociatedDir(useTarget, resourceId, type)
                    ?: resource.localDir.getNullableChildAs(resourceId, type)
                    ?: GlobalDirectory.directoryResource.getNullableChildRecursivelyAs(resourceId, type)
            } else {
                resource.localDir.getNullableChildAs(resourceId, type)
                    ?: localDir.getNullableChildRecursivelyAs(resourceId, type)
                    ?: localDir.root().getNullableChildRecursivelyAs(resourceId, type)
                    ?: searchAssociatedDir(useTarget, resourceId, type)
                    ?: GlobalDirectory.directoryResource.getNullableChildRecursivelyAs(resourceId, type)
            }
        }

        private fun findLinkedRoutineResource(routineId: DatId, useTarget: Boolean = false) : EffectRoutineResource? {
            return findResource(routineId, EffectRoutineResource::class, useTarget)
        }

        private fun emitSound(soundEffectRoutine: SoundEffectRoutine) : EffectResult {
            val soundPointerResource = if (effectAssociation == localEffectAssociation) {
                resource.localDir.getNullableChildRecursivelyAs(soundEffectRoutine.id, SoundPointerResource::class)
                    ?: localDir.getNullableChildRecursivelyAs(soundEffectRoutine.id, SoundPointerResource::class)
                    ?: searchAssociatedDir(useTarget = false, soundEffectRoutine.id, SoundPointerResource::class)
                    ?: GlobalDirectory.directoryResource.getNullableChildRecursivelyAs(soundEffectRoutine.id, SoundPointerResource::class)
            } else {
                searchAssociatedDir(useTarget = false, soundEffectRoutine.id, SoundPointerResource::class)
                    ?: GlobalDirectory.directoryResource.getNullableChildRecursivelyAs(soundEffectRoutine.id, SoundPointerResource::class)
            }

            if (soundPointerResource == null) {
                warn("[$id] Couldn't find sound-effect: ${soundEffectRoutine.id}")
                return EffectResult.noop()
            }

            if (shouldSkipAudio(soundEffectRoutine)) { return EffectResult.noop() }

            val positionFn = getSoundEffectPositionFn(soundEffectRoutine)
            val volumeFn = getSoundEffectVolumeFn(soundEffectRoutine)

            val output = AudioManager.playSoundEffect(
                sePointer = soundPointerResource,
                association = localEffectAssociation,
                positionFn = positionFn,
                volumeFn = volumeFn
            )

            if (output != null) {
                emittedAudio += output
            }

            return EffectResult.noop()
        }

        private fun shouldSkipAudio(soundEffectRoutine: SoundEffectRoutine): Boolean {
            // Skip audio for "missed" AoE skills.
            // Some newer effects have this functionality built-in (by detecting the miss-flag).
            val target = soundEffectRoutine.target
            if (target != SoundEffectTarget.Target && target != SoundEffectTarget.NearestTarget) { return false }

            if (localEffectAssociation !is ActorAssociation) { return false }
            return localEffectAssociation.context.allTargetIds.isEmpty() && localEffectAssociation.context.targetAoeCenter == null
        }

        private fun getSoundEffectPositionFn(soundEffectRoutine: SoundEffectRoutine): (() -> Vector3f?) {
            val actor = if (localEffectAssociation !is ActorAssociation) {
                null
            } else if (soundEffectRoutine.target == SoundEffectTarget.Global) {
                null
            } else if (soundEffectRoutine.target == SoundEffectTarget.Source) {
                localEffectAssociation.actor
            } else if (soundEffectRoutine.target == SoundEffectTarget.Target) {
                ActorManager[localEffectAssociation.context.primaryTargetId]
            } else if (soundEffectRoutine.target == SoundEffectTarget.NearestTarget) {
                val listenerPos = AudioManager.getListenerPosition()
                val targetIds = localEffectAssociation.context.allTargetIds
                targetIds.mapNotNull { ActorManager[it] }.minByOrNull { Vector3f.distance(listenerPos, it.displayPosition) }
            } else {
                localEffectAssociation.actor
            }

            return { actor?.displayPosition }
        }

        private fun getSoundEffectVolumeFn(soundEffectRoutine: SoundEffectRoutine): (Vector3f?) -> Float? {
            val baseVolume = AudioManager.volumeSettings.effectVolume

            return {
                if (soundEffectRoutine.target == SoundEffectTarget.Global) {
                    baseVolume
                } else if (soundEffectRoutine.target == SoundEffectTarget.PlayerOnly) {
                    if (localEffectAssociation is ActorAssociation && ActorManager[localEffectAssociation.context.primaryTargetId] == ActorManager.player()) {
                        baseVolume
                    } else {
                        0f
                    }
                } else if (it == null || soundEffectRoutine.nearDistance == null || soundEffectRoutine.farDistance == null) {
                    null
                } else {
                    val listenerPos = AudioManager.getListenerPosition()
                    val distance = Vector3f.distance(it, listenerPos)

                    val farDistance = if (soundEffectRoutine.farDistance == 0f) { 30f } else { soundEffectRoutine.farDistance }
                    baseVolume * distance.fallOff(soundEffectRoutine.nearDistance, farDistance)
                }
            }
        }

        private fun createSkeletonAnimationRoutine(skeletonAnimationRoutine: SkeletonAnimationRoutine) : EffectResult {
            localEffectAssociation as ActorAssociation
            interpolatedEffects.add(SkeletonAnimationInstance(localEffectAssociation.actor, skeletonAnimationRoutine, animationPlaybackRate, resource.localDir, modelSlotVisibilityState))
            return EffectResult.noop()
        }

        private fun toggleBroadcast(effect: ToggleBroadcastEffect): EffectResult {
            broadcast = effect.useBroadcast
            return EffectResult.noop()
        }

        private fun lockMovement(effect: MovementLockEffect): EffectResult {
            localEffectAssociation as ActorAssociation

            val actorModel = localEffectAssociation.actor.actorModel ?: return EffectResult.noop()
            val duration = localEffectAssociation.context.movementLockOverride ?: effect.duration

            val modelLock = ModelLock(duration.toFloat()/(2f*animationPlaybackRate)) { !isComplete() }
            actorModel.lockMovement(modelLock)

            return EffectResult.noop()
        }

        private fun lockAnimation(effect: AnimationLockEffect): EffectResult {
            localEffectAssociation as ActorAssociation
            val actorModel = localEffectAssociation.actor.actorModel ?: return EffectResult.noop()

            val modelLock = ModelLock(effect.duration.toFloat()/(2f*animationPlaybackRate)) { !isComplete() }
            actorModel.lockAnimation(modelLock)

            return EffectResult.noop()
        }

        private fun lockFacing(effect: FacingLockEffect): EffectResult {
            localEffectAssociation as ActorAssociation

            val actorModel = localEffectAssociation.actor.actorModel ?: return EffectResult.noop()
            val duration = localEffectAssociation.context.movementLockOverride ?: effect.duration

            val modelLock = ModelLock(duration.toFloat()/(2f*animationPlaybackRate)) { !isComplete() }
            actorModel.lockFacing(modelLock)

            return EffectResult.noop()
        }

        private fun createScheduledSequence(loopParameters: TimeBasedReplayRoutine): EffectResult {
            val clock = EnvironmentManager.getClock()
            val currentTime = clock.currentTimeOfDayInSeconds() * 60

            return if (currentTime < loopParameters.timeOfDayStart || currentTime >= loopParameters.timeOfDayEnd) {
                val nextStartTime = loopParameters.timeOfDayStart.toLong()
                addScheduledSequence(nextStartTime, this)
                EffectResult.stop()
            } else {
                val nextStartTime = currentTime + loopParameters.loopInterval
                addScheduledSequence(nextStartTime, this)
                EffectResult.noop()
            }
        }

        private fun <T: DatResource> searchAssociatedDir(useTarget: Boolean, datId: DatId, resourceType: KClass<T>) : T? {
            return if (localEffectAssociation is ActorAssociation) {
                val actor = if (useTarget) {
                    ActorManager[localEffectAssociation.context.primaryTargetId] ?: return null
                } else {
                    localEffectAssociation.actor
                }

                for (dir in actor.getAllAnimationDirectories()) {
                    val res = dir.getNullableChildRecursivelyAs(datId, resourceType)
                    if (res != null) {
                        return res
                    }
                }
                return null
            } else {
                null
            }
        }

        private fun getAssociations(): List<EffectAssociation> {
            return if (broadcast) {
                localEffectAssociation as ActorAssociation
                localEffectAssociation.context.allTargetIds.mapNotNull { ActorManager[it] }
                    .map { ActorAssociation(localEffectAssociation.actor, localEffectAssociation.context.cloneWithOverrideTarget(it.id)) }
            }  else {
                listOf(localEffectAssociation)
            }
        }

        private fun endAssociatedLoop(endLoopRoutine: EndLoopRoutine) : EffectResult {
            EffectManager.forEachEffectForAssociation(localEffectAssociation) {
                it.effectSequences.filter { es -> es.id == endLoopRoutine.refId }.forEach { es -> es.stop() }
            }

            return EffectResult.noop()
        }

        private fun startAssociatedLoop(startLoopRoutine: StartLoopRoutine) : EffectResult {
            val effectRoutineResource = findLinkedRoutineResource(startLoopRoutine.refId)
            if (effectRoutineResource == null) {
                warn("[$id] Couldn't find child-routine: ${startLoopRoutine.refId} [${localDir.id}]")
                return EffectResult.noop()
            }

            val child = createChildFromResource(effectRoutineResource, localEffectAssociation, looping = true)
            appendChildSequence(child)

            return EffectResult.noop()
        }

        private fun handleControlFlow(controlFlow: ControlFlowBlock): EffectResult {
            if (controlFlow.openBlock) {
                val current = controlFlowStack.peek()
                val skipped = current != null && (current.skipped || current.skipNextBranch)
                controlFlowStack.push(ControlFlowState(skipped))
            } else {
                controlFlowStack.pop()
            }

            return EffectResult.noop()
        }

        private fun handleControlFlowBranch(controlFlowBranch: ControlFlowBranch) : EffectResult {
            val controlFlow = controlFlowStack.peek()!!
            if (controlFlowBranch.branchType) { controlFlow.onTrueBranch(id) } else { controlFlow.onFalseBranch(id) }
            return EffectResult.noop()
        }

        private fun handleControlFlowCondition(controlFlowCondition: ControlFlowCondition): EffectResult {
            val current = controlFlowStack.peek()!!

            val context = if (localEffectAssociation is ActorAssociation) {
                localEffectAssociation.getAttackContextForTarget()
            } else {
                AttackContext()
            }

            if (controlFlowCondition.arg0 == 0x1C && controlFlowCondition.arg1 == 0x03 && controlFlowCondition.input == 0x38) {
                // Special case for [crtl], which only sets r0? Maybe "registers" are per-sequence, rather than per-block?
                // Copy r1 & r2 from the previous setting, which is fine to hardcode because this only happens in [crtl]
                current.r0 = resolveControlFlowVariable(controlFlowCondition.input, context)
                current.r1 = 0x02
                current.r2 = 0x11
            } else if (controlFlowCondition.arg0 == 0x1C) {
                when (controlFlowCondition.arg1) {
                    0x03 -> current.r0 = resolveControlFlowVariable(controlFlowCondition.input, context)
                    0x01 -> current.r1 = controlFlowCondition.input ?: 0
                    else -> throw IllegalStateException("[$id] Unknown argument type: $controlFlowCondition")
                }
            } else {
                current.r2 = controlFlowCondition.arg0
            }

            return EffectResult.noop()
        }

        private fun resolveControlFlowVariable(variable: Int?, context: AttackContext): Int {
            return when (variable) {
                0x28 -> context.hitTypeFlag // ex: [damh]/[damg], [sway], [gurd], [pary]
                0x2B -> context.effectArg // Seen in cards ("pip"), [crtl], various misc effects
                0x2D -> context.rollSumFlag // Seen in cards ("sum")
                0x2F -> context.onHitEffect // On-hit effects
                0x33 -> context.retaliationFlag // "Retaliation" effects
                0x38 -> context.sourceFlag // Ally/Enemy flag for crits
                0x3A -> context.appearanceState ?: resolveAppearanceState()
                0x3C -> context.appearanceCurrentDisplayState // Used for triggering state transitions
                else -> {
                    warn("[${id}] Unhandled variable: [${variable?.toString(0x10)}]. Defaulting to [0]")
                    0x00
                }
            }
        }

        private fun resolveAppearanceState(): Int {
            if (localEffectAssociation !is ActorAssociation) { return 0 }
            return localEffectAssociation.actor.state.appearanceState
        }

        private fun handleRandomChildRoutine(effect: RandomChildRoutine): EffectResult {
            return runEffect(effect.children.random())
        }

        private fun handleRotationRoutine(effect: ModelRotationRoutine) : EffectResult {
            val area = SceneManager.getCurrentScene().getMainArea()
            interpolatedEffects.add(ModelTransformInstance(localDir.id, effect, area,
                initialValueSupplier = { it.rotation },
                updater = { transform, value -> transform.rotation.copyFrom(value) }
            ))
            return EffectResult.noop()
        }

        private fun handleModelTranslationRoutine(effect: ModelTranslationRoutine) : EffectResult {
            val area = SceneManager.getCurrentScene().getMainArea()
            interpolatedEffects.add(ModelTransformInstance(localDir.id, effect, area,
                initialValueSupplier = { it.translation },
                updater = { transform, value -> transform.translation.copyFrom(value) }
            ))
            return EffectResult.noop()
        }

        private fun handleParticleEffectDampen(effect: ParticleDampenRoutine) : EffectResult {
            val particles = ArrayList<Particle>()

            EffectManager.forEachEffectForAssociation(localEffectAssociation) {
                val effectParticles = it.getParticles()
                particles += effectParticles
                particles += effectParticles.flatMap { p -> p.getChildrenRecursively() }
            }

            val matchingParticles = particles.filter { it.creator.datId == effect.id }

            if (matchingParticles.isEmpty()) {
                warn("[$id] Couldn't find any particles to expire: ${effect.id}")
                return EffectResult.noop()
            }

            for (particle in matchingParticles) {
                particle.creator.stopEmitting()
                particle.forceExpire()

                val audios = particle.emittedAudio.filter { !it.isComplete() }
                for (audio in audios) {
                    audio.player.stopLooping()
                    audio.applyFade(FadeParameters.fadeOut(particle.maxAge))
                }
            }

            return EffectResult.noop()
        }

        private fun handleActorFadeRoutine(actorFadeRoutine: ActorFadeRoutine): EffectResult {
            localEffectAssociation as ActorAssociation

            val actor = if (actorFadeRoutine.useTarget) {
                ActorManager[localEffectAssociation.context.primaryTargetId]
            } else {
                localEffectAssociation.actor
            }

            if (actor != null) {
                interpolatedEffects += ActorColorTransform(actorFadeRoutine, actor)
            }

            return EffectResult.noop()
        }

        private fun handleActorWrapTexture(effect: ActorWrapTexture): EffectResult {
            localEffectAssociation as ActorAssociation
            effect.textureLink.getOrPut {
                localDir.searchLocalAndParentsById(it, TextureResource::class) ?:
                GlobalDirectory.directoryResource.getNullableChildRecursivelyAs(it, TextureResource::class)
            }

            val actor = getLocalEffectActor(effect.useTarget) ?: return EffectResult.noop()
            interpolatedEffects += ActorWrapTextureEffect(effect, actor)
            return EffectResult.noop()
        }

        private fun handleActorWrapColor(effect: ActorWrapColor): EffectResult {
            localEffectAssociation as ActorAssociation
            val actor = getLocalEffectActor(effect.useTarget) ?: return EffectResult.noop()
            interpolatedEffects += ActorWrapColorTransform(effect, actor)
            return EffectResult.noop()
        }

        private fun handleActorWrapUvTranslate(effect: ActorWrapUvTranslation): EffectResult {
            localEffectAssociation as ActorAssociation
            val actor = getLocalEffectActor(effect.useTarget) ?: return EffectResult.noop()
            interpolatedEffects += ActorWrapUvTransform(effect, actor)
            return EffectResult.noop()
        }

        private fun handleStartRangedAnimationRoutine(effect: StartRangedAnimationRoutine): EffectResult {
            val animationId = resolveRangedEffectId(startEffect = true, effect.rangeSubtype)
            return createChild(LinkedEffectRoutine(delay = effect.delay, duration = effect.duration, id = animationId))
        }

        private fun handleFinishRangedAnimationRoutine(effect: FinishRangedAnimationRoutine): EffectResult {
            val animationId = resolveRangedEffectId(startEffect = false, effect.rangeSubtype)
            return createChild(LinkedEffectRoutine(delay = effect.delay, duration = effect.duration, id = animationId))
        }

        private fun resolveRangedEffectId(startEffect: Boolean, subtype: Int): DatId {
            localEffectAssociation as ActorAssociation

            val actorState = localEffectAssociation.actor.getState()
            if (actorState.type != ActorType.Pc) {
                // Can't infer from equipment, because they don't have any... look for the only match instead?
                val prefix = if (startEffect) { "lc" } else { "ls" }
                val allEffectRoutines = localDir.collectByTypeRecursive(EffectRoutineResource::class)
                    .filter { it.id.id.startsWith(prefix) }

                if (allEffectRoutines.size == 1) { return allEffectRoutines.first().id }

                warn("[$id] Too many/few ranged attack routines for non-PC actor: ${allEffectRoutines.map { it.id }}")
            }

            val info = localEffectAssociation.actor.actorModel?.model?.getRangedWeaponInfo()

            val rangeType = if (subtype == 0) { // "Other" (throwing, marksmanship, archery) - default to throwing...?
                if (info?.rangeType?.subtype == 0) { info.rangeType } else { RangeType.ThrowingAmmo }
            } else if (subtype == 1) { // Song subtype - default to singing if ranged isn't an instrument
                if (info?.rangeType?.subtype == 1) { info.rangeType } else { RangeType.None }
            } else if (subtype == 2) { // Geomancy - handbell-10 and handbell-11 seem the same
                RangeType.HandbellGeo
            } else {
                throw IllegalStateException("[${resource.path()}] Unknown subtype: $subtype")
            }

            return if (startEffect) { DatId.startRangeAnimationId(rangeType) } else { DatId.finishRangeAnimationId(rangeType) }
        }

        private fun handleStopParticleGeneratorRoutine(effect: StopParticleGeneratorRoutine): EffectResult {
            EffectManager.forEachEffectForAssociation(localEffectAssociation) { r ->
                r.particleGenerators.forEach { if (it.particleGenerator.datId == effect.id) { it.particleGenerator.stopEmitting() } }
            }
            return EffectResult.noop()
        }

        private fun handleDisplayRangedRoutine(effect: DisplayRangedModelRoutine): EffectResult {
            val actor = getLocalEffectActor(useTarget = false) ?: return EffectResult.noop()
            actor.actorModel?.displayRanged(effect.duration.toFloat()/2f)
            return EffectResult.noop()
        }

        private fun addScheduledSequence(startTimeOfDay: Long, current: EffectSequence) {
            val scheduled = EffectSequence(current.resource, current.localEffectAssociation)
            scheduled.scheduledStartTime = startTimeOfDay
            appendChildSequence(scheduled)
        }

        private fun isAfterScheduledTime(): Boolean {
            val scheduledTime = scheduledStartTime ?: return true

            val clock = EnvironmentManager.getClock()
            val currentTime = clock.currentTimeOfDayInSeconds() * 60
            val previousTime = previousTimeOfDay

            val passedScheduledTime = previousTime != null && previousTime < scheduledTime && scheduledTime <= currentTime
            return if (passedScheduledTime) {
                scheduledStartTime = null
                true
            } else {
                previousTimeOfDay = currentTime
                false
            }
        }

        private fun getLocalEffectActor(useTarget: Boolean): Actor? {
            localEffectAssociation as ActorAssociation
            return if (useTarget) { ActorManager[localEffectAssociation.context.primaryTargetId] } else { localEffectAssociation.actor }
        }

        private fun handleParticleTransitionEffect(effect: TransitionParticleEffect): EffectResult {
            var parent: Particle? = null

            EffectManager.forEachEffectForAssociation(localEffectAssociation) {
                it.particleGenerators.filter { pg -> pg.particleGenerator.datId == effect.stopEffect }
                    .forEach { pg -> pg.particleGenerator.stopEmitting() }

                val allParticles = it.particles + it.particles.flatMap { p -> p.getChildrenRecursively() }
                allParticles.filter { p -> p.datId == effect.stopEffect }
                    .filter { p -> !p.isExpired() }
                    .forEach { p -> parent = parent ?: p; p.forceExpire() }
            }

            if (parent == null) {
                warn("[${effect.startEffect}] Failed to find parent particle [${effect.stopEffect}]")
                return EffectResult.noop()
            }

            particleGenerators += makeParticleGenerators(effect.startEffect, effect.duration, parent)
                .onEach { it.particleGenerator.transitionLink = effect }

            return EffectResult.noop()
        }

        private fun handleStopRoutineEffect(effect: StopRoutineEffect): EffectResult {
            EffectManager.forEachEffectForAssociation(localEffectAssociation) {
                it.effectSequences.filter { es -> es.id == effect.id }.forEach { es -> es.stop() }
            }
            return EffectResult.noop()
        }

        private fun handleJointSnapshotEffect(effect: JointSnapshotEffect): EffectResult {
            localEffectAssociation as ActorAssociation
            localEffectAssociation.context.applyJointSnapshot(effect.snapshot)
            return EffectResult.noop()
        }

        private fun handleTransitionToIdle(effect: TransitionToIdleEffect): EffectResult {
            localEffectAssociation as ActorAssociation
            localEffectAssociation.actor.transitionToIdle(effect.transitionTime / 2f)
            return EffectResult.noop()
        }

        private fun handleActorJumpRoutine(effect: ActorJumpRoutine): EffectResult {
            localEffectAssociation as ActorAssociation

            val target = ActorManager[localEffectAssociation.context.primaryTargetId] ?: return EffectResult.noop()
            val destination = localEffectAssociation.context.targetAoeCenter ?: target.getWorldSpaceJointPosition(effect.targetJoint)

            val originalPosition = Vector3f(localEffectAssociation.actor.displayPosition)

            interpolatedEffects += ActorJumpTransform(effect, localEffectAssociation.actor, originalPosition, destination)

            return EffectResult.noop()
        }

        private fun handleDualWieldEngageRoutine(effect: DualWieldEngageRoutine): EffectResult {
            localEffectAssociation as ActorAssociation

            val actorModel = localEffectAssociation.actor.actorModel?.model
            val mainType = actorModel?.getMainWeaponInfo()?.weaponAnimationSubType ?: 0
            val subType = actorModel?.getSubWeaponInfo()?.weaponAnimationSubType ?: 0

            val prefix = if (effect.inOutFlag == 0) { "in" } else { "ot" }
            val suffix = if (effect.index == 0) { "1$mainType" } else { "2$subType" }
            val routineId = DatId("$prefix$suffix")

            return createChild(LinkedEffectRoutine(delay = effect.delay, duration = 0, id = routineId))
        }

        private fun handleDamageCallbackRoutine(effect: DamageCallbackRoutine): EffectResult {
            localEffectAssociation as ActorAssociation
            localEffectAssociation.getAttackContextForTarget().invokeCallback()
            return EffectResult.noop()
        }

        private fun handleSetModelVisibilityRoutine(effect: SetModelVisibilityRoutine): EffectResult {
            localEffectAssociation as ActorAssociation
            val override = SlotVisibilityOverride(effect.slot, effect.hidden, effect.ifEngaged)

            // When the slot is toggled during initialization, it seems to be persisted for the actor's lifetime.
            val initializeEffect = routineId.parameterizedMatch(DatId("ini?")) || routineId.parameterizedMatch(DatId("pop?"))

            if (initializeEffect) {
                localEffectAssociation.actor.actorModel?.setDefaultModelVisibility(override)
            } else {
                modelSlotVisibilityState.apply(override)
            }

            return EffectResult.noop()
        }

        private fun handleSpellEffect(effect: SpellEffect): EffectResult {
            localEffectAssociation as ActorAssociation

            val fileId = SpellAnimationTable.fileTableOffset + effect.spellIndex
            val filePath = FileTableManager.getFilePath(fileId) ?: return EffectResult.noop()

            val awaitingResource = DatLoader.load(filePath)
            if (!awaitingResource.isReady()) { return EffectResult.of(effect.copy(delay = 1)) }

            val main = awaitingResource.getAsResource().getNullableChildRecursivelyAs(DatId.main, EffectRoutineResource::class)
            if (main != null) {
                val child = createChildFromResource(main, localEffectAssociation)
                child.effectSequence.add(0, DelayRoutine(delay = effect.delay))
                appendChildSequence(child)
            }

            return EffectResult.noop()
        }

        private fun handleForwardDisplacementEffect(effect: ForwardDisplacementEffect): EffectResult {
            localEffectAssociation as ActorAssociation
            interpolatedEffects += ActorForwardDisplacement(effect, localEffectAssociation.actor)
            return EffectResult.noop()
        }

        private fun handlePointLightInterpolationEffect(effect: PointLightInterpolationEffect): EffectResult {
            val particleGens = ArrayList<ParticleGenerator>()

            EffectManager.forEachEffectForAssociation(effectAssociation) {
                particleGens += it.particleGenerators.map { pg -> pg.particleGenerator }.filter { pg -> pg.datId == effect.particleGenId }
            }

            if (particleGens.isEmpty()) {
                warn("[$id] Couldn't find particle-gen: ${effect.particleGenId}")
                return EffectResult.noop()
            }

            interpolatedEffects += particleGens.map { PointLightMultiplierModifier(effect, it) }
            return EffectResult.noop()
        }

        private fun handleActorPositionSnapshot(effect: ActorPositionSnapshotEffect): EffectResult {
            localEffectAssociation as ActorAssociation
            localEffectAssociation.context.applyPositionSnapshot()
            localEffectAssociation.context.applyJointSnapshot(true)
            return EffectResult.noop()
        }

        private fun handleToggleModelVisibilityRoutine(effect: ToggleModelVisibilityRoutine): EffectResult {
            localEffectAssociation as ActorAssociation
            localEffectAssociation.actor.actorModel?.toggleModelVisibility(effect.slot, effect.hidden)
            return EffectResult.noop()
        }

        private fun handleFlinchRoutine(effect: FlinchRoutine): EffectResult {
            localEffectAssociation as ActorAssociation

            val actor = (if (effect.useTarget) {
                ActorManager[localEffectAssociation.context.primaryTargetId]
            } else {
                localEffectAssociation.actor
            }) ?: return EffectResult.noop()

            interpolatedEffects.add(FlinchAnimationInstance(actor, effect))
            return EffectResult.noop()
        }

        private fun handleAdjustAnimationModeRoutine(effect: AdjustAnimationModeRoutine): EffectResult {
            localEffectAssociation as ActorAssociation
            val model = localEffectAssociation.actor.actorModel

            when (effect.mode) {
                0 -> model?.battleAnimationMode = effect.value
                1 -> model?.idleAnimationMode = effect.value
                2 -> model?.walkingAnimationMode = effect.value
                3 -> model?.runningAnimationMode = effect.value
                else -> warn("[$id] Unimplemented mode: ${effect.mode}")
            }

            return EffectResult.noop()
        }

        private fun handleDisplayDeadRoutine(effect: DisplayDeadRoutine): EffectResult {
            localEffectAssociation as ActorAssociation
            localEffectAssociation.actor.displayDead = true
            return EffectResult.noop()
        }

        private fun handleKnockBackRoutine(effect: KnockBackRoutine): EffectResult {
            localEffectAssociation as ActorAssociation

            val target = ActorManager[localEffectAssociation.context.primaryTargetId] ?: return EffectResult.noop()

            val context = localEffectAssociation.getAttackContextForTarget()
            if (context.knockBackMagnitude == 0 || context.missed()) { return EffectResult.noop() }

            interpolatedEffects += KnockBackInstance(localEffectAssociation.actor, target, effect, context, modelSlotVisibilityState)
            return EffectResult.noop()
        }

        private fun handleAttackBlockedRoutine(effect: AttackBlockedRoutine): EffectResult {
            localEffectAssociation as ActorAssociation

            val animationId = when (effect.blockType) {
                0 -> DatId("gdm?")
                1 -> DatId("pym?")
                else -> return EffectResult.noop()
            }

            interpolatedEffects += AttackBlockedAnimationInstance(localEffectAssociation.actor, animationId, effect)
            return EffectResult.noop()
        }

        private fun handleAttackCounteredRoutine(effect: AttackCounteredRoutine): EffectResult {
            localEffectAssociation as ActorAssociation

            val target = ActorStateManager[localEffectAssociation.context.primaryTargetId] ?: return EffectResult.noop()

            val context = localEffectAssociation.getAttackContextForTarget()
            AttackContext.compose(context) {
                localEffectAssociation.actor.displayAutoAttack(
                    autoAttackType = MainHandAutoAttack(subType = AutoAttackSubType.None),
                    attackContext = context,
                    targetState = target,
                    totalAttacksInRound = 1,
                )
            }

            return EffectResult.noop()
        }

        private fun handleLoadBaseModelRoutine(loadBaseModelRoutine: LoadBaseModelRoutine): EffectResult {
            localEffectAssociation as ActorAssociation
            val model = localEffectAssociation.actor.actorModel?.model ?: return EffectResult.noop()

            if (model !is NpcWithBaseModel) { throw IllegalStateException("[$id] Was expecting model type: 0x06") }
            val resource = model.setBase(loadBaseModelRoutine.modelId)

            resource?.onReady { localEffectAssociation.actor.transitionToIdle(0f) }

            return EffectResult.noop()
        }

        private fun handleWeaponTraceRoutine(weaponTraceRoutine: WeaponTraceRoutine): EffectResult {
            localEffectAssociation as ActorAssociation

            val resource = weaponTraceRoutine.resourceId.getOrPut {
                resource.localDir.root()
                    .collectByTypeRecursive(WeaponTraceResource::class)
                    .firstOrNull { r -> r.id.parameterizedMatch(it) }
            }

            if (resource == null) {
                warn("[$id] Couldn't find weapon-trace: ${weaponTraceRoutine.resourceId.id}")
                return EffectResult.noop()
            }

            interpolatedEffects += WeaponTraceEffect(weaponTraceRoutine, resource, localEffectAssociation)

            return EffectResult.noop()
        }

        private fun handleFollowPointsRoutine(followPointsRoutine: FollowPointsRoutine): EffectResult {
            localEffectAssociation as ActorAssociation
            val resource = followPointsRoutine.resourceId.getOrPut { findResource(it, PointListResource::class) }

            if (resource == null) {
                warn("[$id] Couldn't find ${followPointsRoutine.resourceId}")
                return EffectResult.noop()
            }

            val reversed = followPointsRoutine.flags0 and 0x08 == 0
            val pointList = if (reversed) { resource.pointList.asReversed() } else { resource.pointList }

            interpolatedEffects += FollowPointsEffect(followPointsRoutine, pointList, localEffectAssociation)

            return EffectResult.noop()
        }

    }

}