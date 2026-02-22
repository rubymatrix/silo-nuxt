package xim.poc.ui

import js.typedarrays.Uint8Array
import web.gl.GLint
import web.gl.WebGL2RenderingContext
import web.gl.WebGLTexture
import xim.poc.gl.ByteColor
import xim.poc.gl.GlDisplay
import xim.poc.gl.TextureReference
import xim.poc.tools.TexturePreviewer
import xim.resource.DatId
import xim.resource.DirectoryResource
import xim.resource.TextureResource
import xim.resource.UiElement

object InventoryItemBorders {

    private val textureGradients = listOf(
        listOf(ByteColor(0xb8, 0xb8, 0xb8, 0x80), ByteColor(0xa0, 0xa0, 0xa0, 0x80), ByteColor(0x88, 0x88, 0x88, 0x80)),
        listOf(ByteColor(0x38, 0x80, 0xa8, 0x80), ByteColor(0x40, 0x70, 0x90, 0x80), ByteColor(0x48, 0x60, 0x78, 0x80)),
        listOf(ByteColor(0xa8, 0x90, 0x38, 0x80), ByteColor(0x88, 0x78, 0x48, 0x80), ByteColor(0x70, 0x68, 0x50, 0x80)),
    )

    private val borderElements = HashMap<Int, UiElement>()

    fun getBorderElement(rank: Int?): UiElement? {
        rank ?: return null
        val colors = textureGradients.getOrNull(rank - 1) ?: return null
        return borderElements.getOrPut(rank) { makeBorderResource(rank, colors[0], colors[1], colors[2]) }
    }

    private fun makeBorderResource(rank: Int, c0: ByteColor, c1: ByteColor, c2: ByteColor): UiElement {
        val name = "item    border$rank "
        val resource = TextureResource(DatId.zero, name, TextureReference(makeBorderTexture(c0, c1, c2), 32, 32, name))
        DirectoryResource.setGlobalTexture(resource)
        TexturePreviewer.register(resource)
        return UiElement.basic32x32(resource.name)
    }

    private fun makeBorderTexture(c0: ByteColor, c1: ByteColor, c2: ByteColor): WebGLTexture {
        val context = GlDisplay.getContext()
        val textureId = context.createTexture() ?: throw IllegalStateException()

        context.bindTexture(WebGL2RenderingContext.TEXTURE_2D, textureId)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_MAG_FILTER, WebGL2RenderingContext.NEAREST as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_MIN_FILTER, WebGL2RenderingContext.NEAREST as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_WRAP_S, WebGL2RenderingContext.REPEAT as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_2D, WebGL2RenderingContext.TEXTURE_WRAP_T, WebGL2RenderingContext.REPEAT as GLint)

        val data = Uint8Array(4*32*32)

        for (i in 0 until 32) {
            for (j in 0 until 32) {
                val color = if ((i < 2 || j < 2) || (i >= 30 || j >= 30)) {
                    c0
                } else if ((i < 4 || j < 4) || (i >= 28 || j >= 28)) {
                    c1
                } else if ((i < 5 || j < 5) || (i >= 27 || j >= 27)) {
                    c2
                } else {
                    continue
                }

                data[4*((i*32)+j)+0] = color.r.toByte()
                data[4*((i*32)+j)+1] = color.g.toByte()
                data[4*((i*32)+j)+2] = color.b.toByte()
                data[4*((i*32)+j)+3] = color.a.toByte()
            }
        }

        context.texImage2D(
            target = WebGL2RenderingContext.TEXTURE_2D,
            level = 0,
            internalformat = WebGL2RenderingContext.RGBA as GLint,
            width = 32,
            height = 32,
            border = 0,
            format = WebGL2RenderingContext.RGBA,
            type = WebGL2RenderingContext.UNSIGNED_BYTE,
            pixels = data
        )

        return textureId
    }

}