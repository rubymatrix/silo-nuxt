package xim.poc.browser

import kotlinx.browser.window
import xim.poc.gl.GpuTimer

class JsViewExecutor(val jsWindow: JsWindow) : ViewExecutor {

    private var lastTimestamp: Double = 0.0

    override fun beginDrawing(loopFunction: (t: Double) -> Unit) {
        loop(loopFunction,0.0)
    }

    private fun loop(loopFunction: (t: Double) -> Unit, timestamp: Double) {
        GpuTimer.report()

        GpuTimer.start()
        jsWindow.clear()

        val elapsed = timestamp - lastTimestamp

        try {
            loopFunction.invoke(elapsed)
        } catch (t: Throwable) {
            ErrorElement.show("The engine crashed!\n\n" + t.stackTraceToString())
            throw t
        }

        jsWindow.flush()
        lastTimestamp = timestamp

        GpuTimer.end()
        window.requestAnimationFrame { loop(loopFunction, it) }
    }

}