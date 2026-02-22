package xim.poc.gl

import js.buffer.ArrayBuffer
import js.typedarrays.Float32Array
import js.typedarrays.Uint8Array
import web.gl.WebGL2RenderingContext
import web.gl.WebGL2RenderingContext.Companion.ARRAY_BUFFER
import web.gl.WebGL2RenderingContext.Companion.DYNAMIC_DRAW
import web.gl.WebGLBuffer
import web.gl.WebGLVertexArrayObject
import xim.resource.DirectoryResource
import xim.resource.UiElement
import xim.resource.UiElementComponent
import xim.resource.UiFlipMode
import xim.util.OnceLogger

typealias TextureName = String

class UiElementBufferOutput(val bufferOffset: Int, val allocated: Int)

object UiElementBuffer {

    private var numAllocated = 0

    private val sizeOfElementFloats = (6*3) + (6*2) + 6 // 6 positions + 6 UVs + 4 colors
    private val sizeOfElementBytes = 4 * sizeOfElementFloats

    private lateinit var vertexArray: WebGLVertexArrayObject
    private lateinit var vertexBuffer: WebGLBuffer

    private val uvs = Float32Array(8)

    fun reset() {
        numAllocated = 0
    }

    fun getVao(vertexAttributeSetter: () -> Unit): WebGLVertexArrayObject {
        val webgl = GlDisplay.getContext()
        if (!this::vertexBuffer.isInitialized) { initializeBuffer(webgl, vertexAttributeSetter) }
        return vertexArray
    }

    fun allocate(element: UiElement): Map<TextureName, UiElementBufferOutput> {
        return element.components.groupBy { it.textureName }
            .mapValues { allocate(it.key, it.value) }
            .filterValues { it.allocated > 0 }
    }

    private fun allocate(textureName: TextureName, components: List<UiElementComponent>): UiElementBufferOutput {
        val offset = numAllocated
        numAllocated += components.size

        val (tWidth, tHeight) = getTextureSize(textureName) ?: return UiElementBufferOutput(offset, 0)

        val buffer = ArrayBuffer(sizeOfElementBytes * components.size)
        val floatView = Float32Array(buffer)
        val bytesView = Uint8Array(buffer)

        var floatPos = 0
        var allocated = 0

        for (component in components) {
            if (!component.drawEnabled) { continue }
            allocated += 1

            val uvXMin = (component.uvOffsetX) / tWidth.toFloat()
            val uvXMax = (component.uvOffsetX + component.uvWidth) / tWidth.toFloat()

            val uvYMin = (component.uvOffsetY) / tHeight.toFloat()
            val uvYMax = (component.uvOffsetY + component.uvHeight) / tHeight.toFloat()

            when (component.flipMode) {
                UiFlipMode.None -> setupUvBuffer(uvXMin, uvXMax, uvYMin, uvYMax)
                UiFlipMode.Horizontal -> setupUvBuffer(uvXMax, uvXMin, uvYMin, uvYMax)
                UiFlipMode.Vertical -> setupUvBuffer(uvXMin, uvXMax, uvYMax, uvYMin)
                UiFlipMode.Both -> setupUvBuffer(uvXMax, uvXMin, uvYMax, uvYMin)
            }

            for (i in 0 until 3) {
                val vertex = component.vertices[i]

                floatView[floatPos++] = vertex.point.x
                floatView[floatPos++] = vertex.point.y
                floatView[floatPos++] = 0f

                floatView[floatPos++] = uvs[i * 2]
                floatView[floatPos++] = uvs[i * 2 + 1]

                bytesView[floatPos * 4 + 0] = vertex.color.r.toByte()
                bytesView[floatPos * 4 + 1] = vertex.color.g.toByte()
                bytesView[floatPos * 4 + 2] = vertex.color.b.toByte()
                bytesView[floatPos * 4 + 3] = vertex.color.a.toByte()
                floatPos += 1
            }

            for (i in 1 until 4) {
                val vertex = component.vertices[i]

                floatView[floatPos++] = vertex.point.x
                floatView[floatPos++] = vertex.point.y
                floatView[floatPos++] = 0f

                floatView[floatPos++] = uvs[i * 2]
                floatView[floatPos++] = uvs[i * 2 + 1]

                bytesView[floatPos * 4 + 0] = vertex.color.r.toByte()
                bytesView[floatPos * 4 + 1] = vertex.color.g.toByte()
                bytesView[floatPos * 4 + 2] = vertex.color.b.toByte()
                bytesView[floatPos * 4 + 3] = vertex.color.a.toByte()
                floatPos += 1
            }
        }

        val webgl = GlDisplay.getContext()
        webgl.bindBuffer(ARRAY_BUFFER, vertexBuffer)
        webgl.bufferSubData(ARRAY_BUFFER, offset * sizeOfElementBytes, buffer)

        return UiElementBufferOutput(offset, allocated)
    }

    private fun setupUvBuffer(xMin: Float, xMax: Float, yMin: Float, yMax: Float) {
        uvs[0] = xMin; uvs[1] = yMin
        uvs[2] = xMax; uvs[3] = yMin
        uvs[4] = xMin; uvs[5] = yMax
        uvs[6] = xMax; uvs[7] = yMax
    }

    private fun getTextureSize(textureName: TextureName): Pair<Int, Int>? {
        if (textureName.isBlank()) { return Pair(1, 1) }

        val texture = DirectoryResource.getGlobalTexture(textureName)
        if (texture == null) {
            OnceLogger.warn("[UI] Couldn't find [${textureName}].")
            return null
        }

        return Pair(texture.textureReference.width, texture.textureReference.height)
    }

    private fun initializeBuffer(webgl: WebGL2RenderingContext, vertexAttributeSetter: () -> Unit) {
        vertexArray = webgl.createVertexArray()!!
        webgl.bindVertexArray(vertexArray)

        vertexBuffer = webgl.createBuffer()!!
        webgl.bindBuffer(ARRAY_BUFFER, vertexBuffer)

        val bufferData = Float32Array(10_000 * sizeOfElementFloats)
        webgl.bufferData(ARRAY_BUFFER, bufferData, DYNAMIC_DRAW)

        vertexAttributeSetter.invoke()
    }

}