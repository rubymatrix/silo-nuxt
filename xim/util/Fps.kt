package xim.util

import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object Fps {

    private const val internalFps = 60.0

    fun millisToFrames(v: Float): Float {
        return internalFps.toFloat() * v / 1000f
    }

    fun millisToFrames(v: Double): Double {
        return internalFps * v / 1000.0
    }

    fun millisToFrames(v: Int): Float {
        return millisToFrames(v.toFloat())
    }

    fun millisToFrames(v: Long): Float {
        return millisToFrames(v.toFloat())
    }

    fun secondsToFrames(v: Int): Float {
        return secondsToFrames(v.toFloat())
    }

    fun secondsToFrames(v: Float): Float {
        return internalFps.toFloat() * v
    }

    fun framesToSeconds(f: Float): Duration {
        return (f / internalFps).seconds
    }

    fun framesToSecondsRoundedUp(f: Float): Duration {
        return ceil(f / internalFps).seconds
    }

    fun toFrames(duration: Duration): Float {
        return millisToFrames(duration.inWholeMilliseconds)
    }

}