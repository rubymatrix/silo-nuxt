package xim.poc

import web.gl.WebGL2RenderingContext.Companion.ARRAY_BUFFER
import web.gl.WebGL2RenderingContext.Companion.DYNAMIC_DRAW
import web.gl.WebGLBuffer
import xim.math.Vector3f
import xim.math.Vector3f.Companion.catmullRomSpline
import xim.poc.camera.CameraReference
import xim.poc.gl.*
import xim.poc.gl.RenderState
import xim.resource.MeshType
import xim.resource.WeaponTraceResource
import xim.resource.WeaponTraceRoutine
import xim.util.interpolate
import kotlin.math.roundToInt

class WeaponTraceCommand(
    val frames: Float,
    val rotation: Float,
    val weaponTraceResource: WeaponTraceResource,
    val weaponTraceRoutine: WeaponTraceRoutine,
    val actorAssociation: ActorAssociation,
)

object WeaponTraceDrawer {

    private const val subVertices = 8

    private val commands = ArrayList<WeaponTraceCommand>()

    fun enqueue(command: WeaponTraceCommand) {
        commands += command
    }

    fun draw() {
        WeaponTraceBufferManager.reset()
        if (commands.isEmpty()) { return }

        MainTool.drawer.setupXim(CameraReference.getInstance())

        commands.forEach(this::drawCommand)
        commands.clear()
    }

    private fun drawCommand(command: WeaponTraceCommand) {
        val buffer = prepareBuffer(command) ?: return
        val actor = command.actorAssociation.actor

        MainTool.drawer.drawXim(DrawXimCommand(
            meshes = listOf(buffer),
            translate = Vector3f(actor.displayPosition),
            rotation = Vector3f(0f, command.rotation, 0f),
        ))
    }

    private fun prepareBuffer(command: WeaponTraceCommand): MeshBuffer? {
        val trace = command.weaponTraceResource.trace
        val routine = command.weaponTraceRoutine

        val topColor = command.weaponTraceRoutine.color1
        val bottomColor = command.weaponTraceRoutine.color2

        val progress = ((command.frames - trace.rowStartTime) / (trace.rowEndTime - trace.rowStartTime)).coerceIn(0f, 1f)

        val startingIndex = ((trace.numVertices * progress).roundToInt() - 8).coerceAtLeast(0)
        val endingIndex = (startingIndex + 9).coerceAtMost(trace.numVertices)

        val numVertices = (2 * subVertices * (endingIndex - startingIndex - 1))
        val bufferBuilder = GlBufferBuilder(numVertices)

        val vertexAges = (0 until trace.numVertices).map {
            command.frames - trace.rowStartTime.interpolate(trace.rowEndTime, it.toFloat() / trace.numVertices.toFloat())
        }

        for (i in startingIndex until endingIndex - 1) {
            val prev = (i - 1).coerceAtLeast(0)
            val next = (i + 1).coerceAtMost(trace.numVertices - 1)
            val nextNext = (i + 2).coerceAtMost(trace.numVertices - 1)

            for (j in 0 until subVertices) {
                var topAlpha = topColor.a
                var bottomAlpha = bottomColor.a

                val baseTop = catmullRomSpline(trace.topVertices[prev], trace.topVertices[i], trace.topVertices[next], trace.topVertices[nextNext], j.toFloat()/subVertices)
                val baseBottom = catmullRomSpline(trace.bottomVertices[prev], trace.bottomVertices[i], trace.bottomVertices[next], trace.bottomVertices[nextNext], j.toFloat()/subVertices)

                val age = vertexAges[i].interpolate(vertexAges[next], j.toFloat()/subVertices)
                if (age < 0f) { topAlpha = 0; bottomAlpha = 0 }

                val fade = 1f.interpolate(0f, (age/10f).coerceAtMost(1f)) // TODO - there are fade parameters in the effect routine

                val squeezeFactor1 = (routine.squeeze1 * age).coerceAtMost(1f)
                val squeezeFactor2 = (routine.squeeze2 * age).coerceAtMost(1f)
                if (squeezeFactor1 + squeezeFactor2 >= 0.9f) { topAlpha = 0; bottomAlpha = 0 }

                val top = Vector3f.lerp(baseTop, baseBottom, squeezeFactor1)
                bufferBuilder.appendColoredPosition(top, topColor.withAlpha((topAlpha * fade).roundToInt()))

                val bottom = Vector3f.lerp(baseBottom, baseTop, squeezeFactor2)
                bufferBuilder.appendColoredPosition(bottom, bottomColor.withAlpha((bottomAlpha * fade).roundToInt()))
            }
        }

        val buffer = WeaponTraceBufferManager.claimBuffer(bufferBuilder) ?: return null
        return MeshBuffer(numVertices = numVertices, meshType = MeshType.TriStrip, glBuffer = buffer, renderState = RenderState(blendEnabled = true))
    }

}

private object WeaponTraceBufferManager {

    const val maxVertices = 256

    private lateinit var buffers: List<WebGLBuffer>
    private var claimedCount = 0

    fun claimBuffer(glBufferBuilder: GlBufferBuilder): WebGLBuffer? {
        if (!this::buffers.isInitialized) { setup() }

        val buffer = buffers.getOrNull(claimedCount++) ?: return null
        glBufferBuilder.overwrite(buffer)

        return buffer
    }

    fun reset() {
        claimedCount = 0
    }

    private fun setup() {
        buffers = (0 until 16).map { makeBuffer() }
    }

    private fun makeBuffer(): WebGLBuffer {
        val context = GlDisplay.getContext()
        val bufferId = context.createBuffer()!!
        context.bindBuffer(ARRAY_BUFFER, bufferId)
        context.bufferData(ARRAY_BUFFER, size = GlBufferBuilder.stride * maxVertices, DYNAMIC_DRAW)
        return bufferId
    }

}