package xim.resource

import js.typedarrays.Uint8Array
import web.gl.GLenum
import web.gl.GLint
import web.gl.WebGL2RenderingContext
import web.gl.WebGLTexture
import xim.poc.gl.GlDisplay
import xim.poc.gl.TextureHelper
import xim.poc.gl.TextureReference
import xim.poc.tools.TexturePreviewer
import xim.util.OnceLogger.warn

class TextureSection(private val sectionHeader: SectionHeader) : ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        byteReader.offsetFrom(sectionHeader, 0x10)

        val result = read(byteReader, sectionHeader.sectionId) ?: return ParserResult.none()
        val entry = ParserEntry(result, result.name)
        return ParserResult(entry)
    }

    companion object {

        fun read(byteReader: ByteReader, datId: DatId = DatId.zero): TextureResource? {
            val type = byteReader.next8()
            if (type != 0x91 && type != 0xA1 && type != 0xB1) {
                warn("[$datId] [$byteReader] Don't know how to handle texture type: ${type.toString(0x10)}")
                return null
            }

            val textureName = byteReader.nextString(0x10)

            byteReader.next32() // 0x28?

            val width = byteReader.next32()
            val height = byteReader.next32()

            byteReader.next16() // 0x01?

            val bitCount = byteReader.next16()

            val filter = WebGL2RenderingContext.LINEAR

            expectZero(byteReader.next32())
            expectZero(byteReader.next32())
            expectZero(byteReader.next32())
            expectZero(byteReader.next32())
            expectZero(byteReader.next32())

            byteReader.next32() // 0x10 or 0x20?

            val textureId = when (type) {
                0x91 -> {
                    if (bitCount == 32) {
                        PaletteParser.parseNoPalette(byteReader = byteReader, width = width, height = height, filter = filter)
                    } else {
                        PaletteParser.parsePalette(byteReader = byteReader, width = width, height = height, filter = filter)
                    }
                }
                0xA1 -> {
                    val dxtType = byteReader.nextString(0x4)
                    byteReader.next32() // 100h, 200h, 400h, 800h, 1000h, 2000h, 4000h, 8000h, 10000h
                    byteReader.next32() // 40h, 80h, 100h, 200h, 400h

                    when (dxtType) {
                        "1TXD" -> DXTParser.parseDXTCompressed(name = textureName, byteReader = byteReader, type = "DXT1", width = width, height = height, filter = filter)
                        "3TXD" -> DXTParser.parseDXTCompressed(name = textureName, byteReader = byteReader, type = "DXT3", width = width, height = height, filter = filter)
                        else -> throw IllegalArgumentException("[$datId] Don't know texture-type $dxtType")
                    }
                }
                0xB1 -> {
                    byteReader.next32() // 1?
                    if (bitCount == 32) {
                        PaletteParser.parseNoPalette(byteReader = byteReader, width = width, height = height, filter = filter)
                    } else {
                        PaletteParser.parsePalette(byteReader = byteReader, width = width, height = height, filter = filter)
                    }
                }
                else -> {
                    throw IllegalStateException()
                }
            }

            val textureRef = TextureReference(textureId, width, height, textureName)

            val resource = TextureResource(datId, textureName, textureRef)
            TexturePreviewer.register(resource)

            return resource
        }

    }

}

object DXTParser {

    fun parseDXTCompressed(name: String, byteReader: ByteReader, type: String, width: Int, height: Int, filter: GLenum): WebGLTexture {
        val bufferSize = when (type) {
            "DXT1" -> width*height/2
            "DXT3" -> width*height
            else -> throw IllegalArgumentException("Unknown $type")
        }

        if (name.startsWith("cubemap")) {
            return makeCubeMap(byteReader = byteReader, width = width, height = height, type = type)
        }

        if (!GlDisplay.isCompressedTextureEnabled()) {
            return makeUncompressedTexture(byteReader = byteReader, width = width, height = height, type = type, filter = filter)
        }

        val textureData = byteReader.subBuffer(bufferSize)

        val context = GlDisplay.getContext()
        val textureId = context.createTexture() ?: throw IllegalStateException()

        context.bindTexture(WebGL2RenderingContext.TEXTURE_2D, textureId)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_MAG_FILTER, filter as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_MIN_FILTER, filter as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_WRAP_S, WebGL2RenderingContext.REPEAT as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_WRAP_T, WebGL2RenderingContext.REPEAT as GLint)

        context.compressedTexImage2D(
            target = WebGL2RenderingContext.TEXTURE_2D,
            level = 0,
            internalformat = GlDisplay.getCompressedTextureExtension(type),
            width = width,
            height = height,
            border = 0,
            srcData = textureData,
            srcOffset = null,
            srcLengthOverride = null,
        )

        return textureId
    }

    private fun makeCubeMap(byteReader: ByteReader, width: Int, height: Int, type: String): WebGLTexture {
        if (type != "DXT3") { throw IllegalStateException("Cubemaps are only implemented for DXT3") }

        val context = GlDisplay.getContext()
        val textureId = context.createTexture() ?: throw IllegalStateException()
        context.bindTexture(WebGL2RenderingContext.TEXTURE_CUBE_MAP, textureId)

        context.texParameteri(WebGL2RenderingContext.TEXTURE_CUBE_MAP, WebGL2RenderingContext.TEXTURE_MAG_FILTER, WebGL2RenderingContext.LINEAR as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_CUBE_MAP, WebGL2RenderingContext.TEXTURE_MIN_FILTER, WebGL2RenderingContext.LINEAR as GLint)

        // WebGL doesn't seem to like mixing compress & non-compressed textures for cube-map faces.
        // Not sure if there's actually a TextureResource that corresponds to the blank faces.
        val rgbaSource = TextureHelper.convertDXT3ToRGBA(byteReader, width, height)

        // Specular highlight facing the camera
        context.texImage2D(
            target = WebGL2RenderingContext.TEXTURE_CUBE_MAP_NEGATIVE_Z,
            level = 0,
            internalformat = WebGL2RenderingContext.RGBA as GLint,
            width = width,
            height = height,
            border = 0,
            format = WebGL2RenderingContext.RGBA,
            type = WebGL2RenderingContext.UNSIGNED_BYTE,
            pixels = rgbaSource
        )

        // The remaining faces. Alpha is set to 0x80 in order to match FFXI & simplify the shader
        val blankData = Uint8Array(rgbaSource.length)
        for (i in 3 until blankData.length step 4) { blankData[i] = 0x80.toByte() }

        val blankFaces = listOf(
            WebGL2RenderingContext.TEXTURE_CUBE_MAP_NEGATIVE_X,
            WebGL2RenderingContext.TEXTURE_CUBE_MAP_POSITIVE_X,
            WebGL2RenderingContext.TEXTURE_CUBE_MAP_NEGATIVE_Y,
            WebGL2RenderingContext.TEXTURE_CUBE_MAP_POSITIVE_Y,
            WebGL2RenderingContext.TEXTURE_CUBE_MAP_POSITIVE_Z
        )

        for (face in blankFaces) {
            context.texImage2D(
                target = face,
                level = 0,
                internalformat = WebGL2RenderingContext.RGBA as GLint,
                width = width,
                height = height,
                border = 0,
                format = WebGL2RenderingContext.RGBA,
                type = WebGL2RenderingContext.UNSIGNED_BYTE,
                pixels = blankData
            )
        }

        return textureId
    }

    private fun makeUncompressedTexture(byteReader: ByteReader, width: Int, height: Int, type: String, filter: GLenum): WebGLTexture {
        val context = GlDisplay.getContext()
        val textureId = context.createTexture() ?: throw IllegalStateException()

        val texture = when (type) {
            "DXT1" -> TextureHelper.convertDXT1ToRGBA(byteReader, width, height)
            "DXT3" -> TextureHelper.convertDXT3ToRGBA(byteReader, width, height)
            else -> throw IllegalStateException("Unknown DXT type: $type")
        }

        context.bindTexture(WebGL2RenderingContext.TEXTURE_2D, textureId)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_MAG_FILTER, filter as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_MIN_FILTER, filter as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_WRAP_S, WebGL2RenderingContext.REPEAT as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_WRAP_T, WebGL2RenderingContext.REPEAT as GLint)

        context.texImage2D(
            target = WebGL2RenderingContext.TEXTURE_2D,
            level = 0,
            internalformat = WebGL2RenderingContext.RGBA as GLint,
            width = width,
            height = height,
            border = 0,
            format = WebGL2RenderingContext.RGBA,
            type = WebGL2RenderingContext.UNSIGNED_BYTE,
            pixels = texture
        )

        return textureId
    }

}

object PaletteParser {

    fun parsePalette(byteReader: ByteReader, width: Int, height: Int, filter: GLenum): WebGLTexture {
        val colors = IntArray(256)
        for (i in 0 until 256) {
            colors[i] = byteReader.next32()
        }

        val texture = Uint8Array(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = colors[byteReader.next8()]

                // Needs to be flipped vertically
                val i = width * (height - (y + 1)) + x

                // Data is BGRA
                texture[i * 4 + 2] = (color ushr 0 and 0xFF).toByte()
                texture[i * 4 + 1] = (color ushr 8 and 0xFF).toByte()
                texture[i * 4 + 0] = (color ushr 16 and 0xFF).toByte()
                texture[i * 4 + 3] = (color ushr 24 and 0xFF).toByte()
            }
        }

        val context = GlDisplay.getContext()
        val textureId = context.createTexture() ?: throw IllegalStateException()

        context.bindTexture(WebGL2RenderingContext.TEXTURE_2D, textureId)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_MAG_FILTER, filter as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_MIN_FILTER, filter as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_WRAP_S, WebGL2RenderingContext.REPEAT as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_WRAP_T, WebGL2RenderingContext.REPEAT as GLint)

        context.texImage2D(
            target = WebGL2RenderingContext.TEXTURE_2D,
            level = 0,
            internalformat = WebGL2RenderingContext.RGBA as GLint,
            width = width,
            height = height,
            border = 0,
            format = WebGL2RenderingContext.RGBA,
            type = WebGL2RenderingContext.UNSIGNED_BYTE,
            pixels = texture
        )

        return textureId
    }

    fun parseNoPalette(byteReader: ByteReader, width: Int, height: Int, filter: GLenum): WebGLTexture {
        val texture = Uint8Array(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = byteReader.next32()

                // Needs to be flipped vertically
                val i = width * (height - (y + 1)) + x

                texture[i * 4 + 2] = (color ushr 0 and 0xFF).toByte()
                texture[i * 4 + 1] = (color ushr 8 and 0xFF).toByte()
                texture[i * 4 + 0] = (color ushr 16 and 0xFF).toByte()
                texture[i * 4 + 3] = (color ushr 24 and 0xFF).toByte()
            }
        }

        val context = GlDisplay.getContext()
        val textureId = context.createTexture() ?: throw IllegalStateException()

        context.bindTexture(WebGL2RenderingContext.TEXTURE_2D, textureId)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_MAG_FILTER, filter as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_MIN_FILTER, filter as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_WRAP_S, WebGL2RenderingContext.REPEAT as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_WRAP_T, WebGL2RenderingContext.REPEAT as GLint)

        context.texImage2D(
            target = WebGL2RenderingContext.TEXTURE_2D,
            level = 0,
            internalformat = WebGL2RenderingContext.RGBA as GLint,
            width = width,
            height = height,
            border = 0,
            format = WebGL2RenderingContext.RGBA,
            type = WebGL2RenderingContext.UNSIGNED_BYTE,
            pixels = texture
        )

        return textureId
    }

}