package xim.poc.game.configuration

import xim.poc.game.StatusEffect
import xim.poc.game.StatusEffectState
import xim.util.FrameTimer
import kotlin.time.Duration

interface StatusResistanceStrategy {

    fun update(elapsedFrames: Float)

    fun onStatusGained(statusEffectState: StatusEffectState)

    fun getDurationMultiplier(): Float

    fun getResistance(): Int

}

class OccurrenceLimitStrategy(
    resetInterval: Duration,
    val maxOccurrencesInInterval: Int = 3
): StatusResistanceStrategy {

    private val resetTimer = FrameTimer(resetInterval, initial = Duration.ZERO)
    private var counter = 0

    override fun update(elapsedFrames: Float) {
        resetTimer.update(elapsedFrames)
        if (resetTimer.isReady()) { counter = 0 }
    }

    override fun onStatusGained(statusEffectState: StatusEffectState) {
        counter += 1
        if (resetTimer.isReady()) { resetTimer.reset() }
    }

    override fun getDurationMultiplier(): Float {
        return 1f / (counter + 1)
    }

    override fun getResistance(): Int {
        return if (counter >= maxOccurrencesInInterval) { 100 } else { 0 }
    }

}

class StatusResistanceTracker {

    private val strategies = HashMap<StatusEffect, StatusResistanceStrategy>()

    operator fun set(statusEffect: StatusEffect, strategy: StatusResistanceStrategy) {
        register(statusEffect, strategy)
    }

    fun register(statusEffect: StatusEffect, strategy: StatusResistanceStrategy) {
        strategies[statusEffect] = strategy
    }

    fun update(elapsedFrames: Float) {
        strategies.forEach { it.value.update(elapsedFrames) }
    }

    fun onStatusGained(statusEffectState: StatusEffectState) {
        strategies[statusEffectState.statusEffect]?.onStatusGained(statusEffectState)
    }

    fun getDurationMultiplier(): Map<StatusEffect, Float> {
        return strategies.mapValues { it.value.getDurationMultiplier() }
    }

    fun getResistance(): Map<StatusEffect, Int> {
        return strategies.mapValues { it.value.getResistance() }
    }

}