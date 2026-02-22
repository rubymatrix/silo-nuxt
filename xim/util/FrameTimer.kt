package xim.util

import kotlin.random.Random
import kotlin.time.Duration

class FrameTimer(period: Duration, initial: Duration = period) {

    private val periodFrames = Fps.toFrames(period)
    private var current = Fps.toFrames(initial)

    fun update(elapsedFrames: Float) {
        current -= elapsedFrames
        current = current.coerceAtLeast(0f)
    }

    fun isReady(): Boolean {
        return current <= 0f
    }

    fun isNotReady(): Boolean {
        return !isReady()
    }

    fun reset() {
        current = periodFrames
    }

    fun reset(value: Duration): FrameTimer {
        current = Fps.toFrames(value)
        return this
    }

    fun resetRandom(lowerBound: Duration): FrameTimer {
        val lowerFrames = Fps.toFrames(lowerBound)
        current = if (lowerFrames >= periodFrames) {
            lowerFrames
        } else {
            Random.nextDouble(lowerFrames.toDouble(), periodFrames.toDouble()).toFloat()
        }
        return this
    }

    override fun toString(): String {
        return "$current / $periodFrames"
    }

}