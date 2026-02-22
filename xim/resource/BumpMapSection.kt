package xim.resource

import js.typedarrays.Uint8Array
import web.gl.GLint
import web.gl.WebGL2RenderingContext
import web.gl.WebGLTexture
import xim.math.Vector3f
import xim.poc.gl.GlDisplay
import xim.poc.gl.TextureReference
import kotlin.math.roundToInt

class BumpMapSection(private val sectionHeader: SectionHeader) : ResourceParser {

    companion object {
        private const val strength = 1f
    }

    override fun getResource(byteReader: ByteReader): ParserResult {
        byteReader.offsetFrom(sectionHeader, 0x10)
        val resource = parse(byteReader)

        val entry = ParserEntry(resource)
        return ParserResult(entry)
    }

    private fun parse(byteReader: ByteReader): BumpMapResource {
        byteReader.next32()
        val width = byteReader.next16()
        val height = byteReader.next16()
        expectZero32(byteReader)
        expectZero32(byteReader)

        val name = byteReader.nextString(0x10)
        val heightMap = byteReader.subBuffer(size = width * height)

        // Transform heights to normals
        val normalMap = Uint8Array(length = 3 * width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                transformCoordinate(width = width, height = height, x = x, y = y, heightMap = heightMap, normalMap = normalMap)
            }
        }

        val texture = makeNormalTexture(buffer = normalMap, width = width, height = height)
        val textureReference = TextureReference(id = texture, width = width, height = height, name = name)
        return BumpMapResource(id = sectionHeader.sectionId, textureReference = textureReference)
    }

    private fun transformCoordinate(width: Int, height: Int, x: Int, y: Int, heightMap: Uint8Array, normalMap: Uint8Array) {
        val left = if (x == 0) { width - 1 } else { x - 1 }
        val right = if (x == width - 1) { 0 } else { x + 1 }
        val up = if (y == 0) { height - 1 } else { y - 1 }
        val down = if (y == height - 1) { 0 } else { y + 1 }

        val upLeft       = height(heightMap, width, x = left, y = up)
        val centerLeft   = height(heightMap, width, x = left, y = y)
        val bottomLeft   = height(heightMap, width, x = left, y = down)

        val upRight      = height(heightMap, width, x = right, y = up)
        val centerRight  = height(heightMap, width, x = right, y = y)
        val bottomRight  = height(heightMap, width, x = right, y = down)

        val centerTop    = height(heightMap, width, x = x, y = up)
        val centerBottom = height(heightMap, width, x = x, y = down)

        val dX = (upRight + 2f * centerRight + bottomRight) - (upLeft + 2f * centerLeft + bottomLeft)
        val dY = (bottomLeft + 2f * centerBottom + bottomRight) - (upLeft + 2f * centerTop + upRight)
        val dZ = 1f / strength

        val v = Vector3f(dX, dY, dZ).normalizeInPlace()

        val r = ((v.x * 0.5f + 0.5f) * 255f).roundToInt().toByte()
        val g = ((v.y * 0.5f + 0.5f) * 255f).roundToInt().toByte()
        val b = (v.z * 255f).roundToInt().toByte()

        normalMap[3 * (y * width + x) + 0] = r
        normalMap[3 * (y * width + x) + 1] = g
        normalMap[3 * (y * width + x) + 2] = b
    }

    private fun height(heightMap: Uint8Array, width: Int, x: Int, y: Int): Float {
        return heightMap[width * y + x].toFloat() / 255f
    }

    private fun makeNormalTexture(buffer: Uint8Array, width: Int, height: Int): WebGLTexture {
        val context = GlDisplay.getContext()
        val textureId = context.createTexture() ?: throw IllegalStateException()

        context.bindTexture(WebGL2RenderingContext.TEXTURE_2D, textureId)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_MAG_FILTER, WebGL2RenderingContext.LINEAR as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_MIN_FILTER, WebGL2RenderingContext.LINEAR as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_WRAP_S, WebGL2RenderingContext.REPEAT as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_WRAP_T, WebGL2RenderingContext.REPEAT as GLint)

        context.texImage2D(
            target = WebGL2RenderingContext.TEXTURE_2D,
            level = 0,
            internalformat = WebGL2RenderingContext.RGB8 as GLint,
            width = width,
            height = height,
            border = 0,
            format = WebGL2RenderingContext.RGB,
            type = WebGL2RenderingContext.UNSIGNED_BYTE,
            pixels = buffer
        )

        return textureId
    }

}