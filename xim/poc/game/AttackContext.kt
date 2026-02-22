package xim.poc.game

import xim.poc.ActorId
import xim.poc.game.configuration.constants.SkillId

fun interface EffectCallback {
    fun invoke()

    companion object {
        val noop = EffectCallback {  }

        fun compose(first: EffectCallback, second: EffectCallback): EffectCallback {
            return EffectCallback { first.invoke(); second.invoke() }
        }

    }

}

class AttackContexts(private val byTarget: Map<ActorId, AttackContext>) {

    companion object {
        fun single(targetId: ActorId, attackContext: AttackContext): AttackContexts {
            return AttackContexts(mapOf(targetId to attackContext))
        }

        fun noop(): AttackContexts {
            return AttackContexts(emptyMap())
        }
    }

    operator fun get(actorId: ActorId) = byTarget[actorId] ?: AttackContext.eagerlyInvokingContext()

    fun getAll(): Collection<AttackContext> {
        return byTarget.values
    }

}

data class AttackContext(
    var onHitEffect: Int = 0,
    var retaliationFlag: Int = 0,
    var effectArg: Int = 0,
    var rollSumFlag: Int = 0,
    var magicBurst: Boolean = false,
    var sourceFlag: Int = 0,
    var appearanceState: Int? = null,
    var appearanceCurrentDisplayState: Int = -1,
    var hitTypeFlag: Int = 0,
    var knockBackMagnitude: Int = 0,
    var skill: SkillId? = null,
    var skillChainStep: SkillChainStep? = null,
    private var effectCallback: EffectCallback = EffectCallback.noop,
) {

    companion object {
        fun from(attacker: ActorState, defender: ActorState): AttackContext {
            val context = AttackContext()
            context.setSourceFlag(attacker)
            return context
        }

        fun eagerlyInvokingContext(): AttackContext {
            return AttackContext().also { it.eagerlyInvoke = true; it.setNoTargetFlag() }
        }

        fun compose(actionContext: AttackContext?, callback: EffectCallback) {
            actionContext?.composeCallback(callback) ?: callback.invoke()
        }

    }

    private var eagerlyInvoke = false
    private var callbackInvoked = false

    fun invokeCallback() {
        if (!eagerlyInvoke && callbackInvoked) { return }
        callbackInvoked = true
        effectCallback.invoke()
    }

    private fun composeCallback(next: EffectCallback) {
        if (eagerlyInvoke) {
            next.invoke()
            return
        }

        effectCallback = EffectCallback.compose(effectCallback, next)
    }

    fun setSourceFlag(sourceState: ActorState) {
        sourceFlag = if (sourceState.monsterId != null) { 0 } else { 2 }
    }

    fun setCriticalHitFlag(enabled: Boolean = true) {
        effectArg = if (enabled) { 2 } else { 0 }
    }

    fun criticalHit(): Boolean {
        return effectArg == 2
    }

    fun setMissFlag() {
        hitTypeFlag = 1
        effectArg = 0
    }

    fun missed(): Boolean {
        return hitTypeFlag == 1
    }

    fun setGuardFlag() {
        hitTypeFlag = 2
        effectArg = 0
    }

    fun guarded(): Boolean {
        return hitTypeFlag == 2
    }

    fun setParryFlag() {
        hitTypeFlag = 3
        effectArg = 0
    }

    fun setCounteredFlag() {
        retaliationFlag = 0x3F
    }

    fun countered(): Boolean {
        return retaliationFlag == 0x3F
    }

    fun setNoTargetFlag() {
        effectArg = 4
    }

}