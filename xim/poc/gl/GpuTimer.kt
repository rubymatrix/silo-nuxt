package xim.poc.gl

import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import web.gl.GLenum
import web.gl.GLint
import web.gl.WebGL2RenderingContext
import web.gl.WebGLQuery

private enum class QueryState {
    Started,
    Ended,
}

private class GpuQuery(val id: WebGLQuery, var state: QueryState = QueryState.Started)

object GpuTimer {

    private const val extensionName = "EXT_disjoint_timer_query_webgl2"
    private val extensionValue = 0x88BF as GLenum

    private val webgl by lazy { GlDisplay.getContext() }
    private val valid by lazy { webgl.getExtension(extensionName) != null }
    private val gpuElapsed by lazy { document.getElementById("gpuElapsed") as HTMLInputElement }

    private var query: GpuQuery? = null

    private var samples = 0
    private var cumulativeResult = 0

    fun start() {
        if (!valid) { return }
        if (query != null) { return }

        val newQuery = webgl.createQuery() ?: return
        webgl.beginQuery(extensionValue, newQuery)

        query = GpuQuery(id = newQuery)
    }

    fun end() {
        if (!valid) { return }

        val current = query ?: return
        if (current.state != QueryState.Started) { return }

        webgl.endQuery(extensionValue)
        current.state = QueryState.Ended
    }

    // Must only be called after requestAnimationFrame
    fun report() {
        if (!valid) { return }

        val current = query ?: return
        if (current.state != QueryState.Ended) { return }

        val available = webgl.getQueryParameter(current.id, WebGL2RenderingContext.QUERY_RESULT_AVAILABLE) as Boolean?
        if (available != true) { return }

        val result = webgl.getQueryParameter(current.id, WebGL2RenderingContext.QUERY_RESULT) as GLint
        webgl.deleteQuery(current.id)
        query = null

        samples += 1
        cumulativeResult += result

        if (samples < 60) { return }

        val averageResult = cumulativeResult / 60.0
        gpuElapsed.value = (averageResult / 1000000.0).toString().substring(0..4)

        samples = 0
        cumulativeResult = 0
    }

}