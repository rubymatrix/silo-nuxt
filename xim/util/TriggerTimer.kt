package xim.util

import kotlin.time.Duration

class TriggerTimer(duration: Duration) {

    var triggered = false
        private set

    private var current = Fps.toFrames(duration)

    fun update(elapsedFrames: Float): TriggerTimer {
        current -= elapsedFrames
        current = current.coerceAtLeast(0f)
        return this
    }

    fun isReadyToTrigger(): Boolean {
        return !triggered && current <= 0f
    }

    fun <T> triggerIfReady(triggerFn: () -> T) {
        if (isReadyToTrigger()) {
            triggered = true
            triggerFn.invoke()
        }
    }

}