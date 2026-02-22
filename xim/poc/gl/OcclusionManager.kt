package xim.poc.gl

import web.gl.GLint
import web.gl.WebGL2RenderingContext.Companion.ANY_SAMPLES_PASSED
import web.gl.WebGL2RenderingContext.Companion.QUERY_RESULT
import web.gl.WebGL2RenderingContext.Companion.QUERY_RESULT_AVAILABLE
import web.gl.WebGLQuery
import xim.resource.Particle

private const val maxQueryAge = 2f

private class Query(
    val query: WebGLQuery,
    val previousValue: Int?,
    var value: Int? = null,
) {
    var age: Float = 0f
    var deleted = false
}

object OcclusionManager {

    private val webgl by lazy { GlDisplay.getContext() }

    private val queries = HashMap<Long, Query>()

    fun update(elapsedFrames: Float) {
        queries.values.forEach { it.age += elapsedFrames }
    }

    fun clear() {
        queries.values.filter { !it.deleted }.forEach { webgl.deleteQuery(it.query) }
        queries.clear()
    }

    fun executeQuery(particle: Particle, fn: () -> Unit) {
        val previous = queries[key(particle)]
        if (previous != null && previous.age < maxQueryAge) { return }

        val query = webgl.createQuery() ?: return
        queries[key(particle)] = Query(query, previous?.value ?: previous?.previousValue)

        webgl.beginQuery(ANY_SAMPLES_PASSED, query)
        fn.invoke()
        webgl.endQuery(ANY_SAMPLES_PASSED)
    }

    fun consumeQuery(particle: Particle): Int? {
        val query = queries[key(particle)] ?: return null
        if (query.value != null) { return query.value }

        val available = webgl.getQueryParameter(query.query, QUERY_RESULT_AVAILABLE) as Boolean?
        if (available != true) { return query.previousValue }

        val result = webgl.getQueryParameter(query.query, QUERY_RESULT) as GLint
        query.value = result

        webgl.deleteQuery(query.query)
        query.deleted = true

        return result
    }

    private fun key(particle: Particle): Long {
        return particle.internalId
    }

}