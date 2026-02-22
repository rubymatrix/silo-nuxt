package xim.util

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLDetailsElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import kotlin.collections.set

object Timer {

    private val timingOutput by lazy { document.getElementById("Timing") as HTMLTextAreaElement }
    private val timingDetails by lazy { document.getElementById("TimingDetails") as HTMLDetailsElement }
    private val timingFilter by lazy { document.getElementById("timingFilter") as HTMLInputElement }

    private val times = HashMap<String, Double>()

    private var count = 0
    private var intervalStart = 0.0

    fun <T> time(name: String, condition: Boolean = true, block: () -> T) : T {
        if (!timingDetails.open || !condition) { return block.invoke() }

        val before = window.performance.now()
        val res = block.invoke()
        val after = window.performance.now()

        val duration = after - before
        times[name] = times[name]?.plus(duration) ?: duration

        return res
    }

    fun report() {
        if (!timingDetails.open) { return }
        count += 1

        if (count < 60) { return }
        count = 0

        timingOutput.value = ""

        val intervalEnd = window.performance.now()

        val durationInSeconds = (intervalEnd - intervalStart) / 1000.0
        append("FPS", 60.0 * 60.0 / durationInSeconds)

        times["TrackedTotal"] = times.entries.filter { it.key != "Loop" && !it.key.contains('.') }
            .map { it.value }
            .fold(0.0) { a, b -> a + b }

        val filter = timingFilter.value.lowercase()

        times.entries
            .filter { filter.isBlank() || it.key.lowercase().contains(filter) }
            .sortedByDescending { it.value }
            .forEach { append(it.key, it.value) }

        clear()

        intervalStart = window.performance.now()
    }

    fun clear() {
        times.clear()
    }

    private fun append(name: String, time: Double) {
        timingOutput.value += "${name}: ${(time.toFloat()/60f).toTruncatedString(decimals = 3)}\n"
    }

}