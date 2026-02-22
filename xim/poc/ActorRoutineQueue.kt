package xim.poc

import xim.poc.game.AttackContext
import xim.poc.game.AttackContexts
import xim.resource.*
import xim.util.Fps
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RoutineOptions(
    val blocking: Boolean = true,
    val highPriority: Boolean = false,
    val expiryDuration: Duration? = null,
    val playbackRate: Float = 1f,
    val callback: RoutineCompleteCallback? = null,
)

private class RoutineProvider(val ready: () -> Boolean, val provider: () -> EffectRoutineResource?) {

    fun isReady(): Boolean {
        return ready.invoke()
    }

    fun getIfPresent(): EffectRoutineResource? {
        return provider.invoke()
    }

}

private class QueuedRoutine(
    val context: ActorContext,
    val routineProvider: RoutineProvider,
    val options: RoutineOptions = RoutineOptions(),
) {
    var age = 0f
}

class ActorRoutineQueue(private val actor: Actor) {

    private val notReadyExpiryDuration = 5.seconds

    private val routineQueue = ArrayDeque<QueuedRoutine>()
    private var currentRoutine: EffectRoutineInstance? = null

    fun update(elapsedFrames: Float) {
        val actorModel = actor.actorModel ?: return

        if (currentRoutine?.hasCompletedAllSequences() == true) { currentRoutine = null }

        routineQueue.forEach { it.age += elapsedFrames }
        removeExpiredEntries()

        val next = routineQueue.firstOrNull() ?: return
        if (!next.routineProvider.isReady()) {
            removeNotReadyRoutineIfNeeded(next)
            return
        }

        if (!next.options.highPriority && actorModel.isAnimationLocked()) { return }

        routineQueue.removeFirst()

        val routine = next.routineProvider.getIfPresent()
        if (routine == null) {
            executeCallbacks(next)
            return
        }

        val routineInstance = EffectManager.registerActorRoutine(actor, next.context, routine)
        routineInstance.registerAllEffectsCompletedCallback(next.options.callback)
        routineInstance.animationPlaybackRate = next.options.playbackRate

        if (next.options.blocking) { currentRoutine = routineInstance } else { update(0f) }
    }

    fun hasEnqueuedRoutines(): Boolean {
        return currentRoutine != null || routineQueue.isNotEmpty()
    }

    fun enqueueRoutine(context: ActorContext, options: RoutineOptions, effectRoutineResource: EffectRoutineResource?) {
        val provider = RoutineProvider(ready = { true }, provider = { effectRoutineResource })
        enqueueRoutine(context, provider, options)
    }

    fun enqueueRoutine(context: ActorContext, options: RoutineOptions, routineProvider: () -> EffectRoutineResource?) {
        val provider = RoutineProvider(ready = { routineProvider.invoke() != null }, provider = { routineProvider.invoke() })
        enqueueRoutine(context, provider, options)
    }

    fun enqueueMainBattleRoutine(datId: DatId, targetId: ActorId, attackContext: AttackContext = AttackContext(), callback: (() -> Unit)? = null) {
        enqueueMainBattleRoutine(datId = datId, attackContext = attackContext, targetId = targetId, options = RoutineOptions(blocking = false, callback = callback))
    }

    fun enqueueMainBattleRoutine(datId: DatId, targetId: ActorId, attackContext: AttackContext, options: RoutineOptions) {
        val context = ActorContext(actor.id, targetId, attackContexts = AttackContexts.single(targetId, attackContext))

        enqueueRoutine(datId, context = context, options = options) {
            actor.actorModel?.model?.getMainBattleAnimationDirectory()
        }
    }

    fun enqueueMainBattleRoutine(targetId: ActorId, attackContext: AttackContext, options: RoutineOptions, animProvider: () -> DatId) {
        val context = ActorContext(actor.id, targetId, attackContexts = AttackContexts.single(targetId, attackContext))

        enqueueRoutine(context = context, options = options) {
            val directory = actor.actorModel?.model?.getMainBattleAnimationDirectory()
            directory?.getNullableChildRecursivelyAs(animProvider.invoke(), EffectRoutineResource::class)
        }
    }

    fun enqueueSubBattleRoutine(targetId: ActorId, attackContext: AttackContext, options: RoutineOptions, animProvider: () -> DatId) {
        val context = ActorContext(actor.id, targetId, attackContexts = AttackContexts.single(targetId, attackContext))

        enqueueRoutine(context = context, options = options) {
            val directory = actor.actorModel?.model?.getSubBattleAnimationDirectory()
            directory?.getNullableChildRecursivelyAs(animProvider.invoke(), EffectRoutineResource::class)
        }
    }

    fun enqueueItemModelRoutine(itemModelSlot: ItemModelSlot, datId: DatId) {
        val context = ActorContext(actor.id, modelSlot = itemModelSlot)
        enqueueRoutine(datId, context = context, options = RoutineOptions(blocking = false)) {
            actor.actorModel?.model?.getEquipmentModelResource(itemModelSlot)
        }
    }

    private fun enqueueRoutine(routineId: DatId, context: ActorContext, options: RoutineOptions = RoutineOptions(), directoryProvider: () -> DirectoryResource?) {
        val provider = RoutineProvider(
            ready = { directoryProvider.invoke() != null },
            provider = { directoryProvider.invoke()?.getNullableChildRecursivelyAs(routineId, EffectRoutineResource::class) }
        )
        enqueueRoutine(context, provider, options)
    }

    private fun enqueueRoutine(context: ActorContext, routineProvider: RoutineProvider, options: RoutineOptions = RoutineOptions()) {
        val queuedRoutine = QueuedRoutine(context, routineProvider, options)

        if (!options.highPriority) {
            routineQueue += queuedRoutine
            return
        }

        val idx = routineQueue.indexOfFirst { !it.options.highPriority }
        if (idx == -1) { routineQueue += queuedRoutine } else { routineQueue.add(idx, queuedRoutine) }
    }

    private fun isTimedOut(queuedRoutine: QueuedRoutine, timeLimit: Duration?): Boolean {
        if (timeLimit == null) { return false }
        return Fps.framesToSeconds(queuedRoutine.age) > timeLimit
    }

    private fun removeNotReadyRoutineIfNeeded(queuedRoutine: QueuedRoutine) {
        if (!isTimedOut(queuedRoutine, notReadyExpiryDuration)) { return }
        routineQueue.removeFirst()
        executeCallbacks(queuedRoutine)
    }

    private fun removeExpiredEntries() {
        while (routineQueue.isNotEmpty()) {
            val next = routineQueue.firstOrNull() ?: return
            if (!isTimedOut(next, next.options.expiryDuration)) { return }

            routineQueue.removeFirst()
            executeCallbacks(next)
        }
    }

    private fun executeCallbacks(queuedRoutine: QueuedRoutine) {
        queuedRoutine.options.callback?.onComplete()
        queuedRoutine.context.attackContexts.getAll().forEach { it.invokeCallback() }
    }

}