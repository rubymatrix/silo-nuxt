package xim.poc.game.configuration.v0

import xim.poc.ui.HelpWindowUi
import xim.util.Fps
import kotlin.time.Duration

private class Notification(val message: String, val duration: Duration) {
    var age = 0f
}

object HelpNotifier {

    private val pending = ArrayDeque<Notification>()
    private var current: Notification? = null

    fun notify(message: String, duration: Duration) {
        pending += Notification(message, duration)
    }

    fun update(elapsedFrames: Float) {
        if (current == null) { current = pending.removeFirstOrNull() }

        val showing = current ?: return HelpWindowUi.clearText()
        showing.age += elapsedFrames

        if (Fps.framesToSeconds(showing.age) >= showing.duration) {
            current = null
            return
        }

        HelpWindowUi.setText(showing.message)
    }

}