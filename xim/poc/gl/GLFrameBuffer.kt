package xim.poc.gl

import org.khronos.webgl.WebGLRenderingContext
import web.gl.GLint
import web.gl.WebGL2RenderingContext
import web.gl.WebGL2RenderingContext.Companion.COLOR_ATTACHMENT0
import web.gl.WebGL2RenderingContext.Companion.FRAMEBUFFER
import web.gl.WebGL2RenderingContext.Companion.FRAMEBUFFER_COMPLETE
import web.gl.WebGL2RenderingContext.Companion.LINEAR
import web.gl.WebGL2RenderingContext.Companion.RGBA
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_2D
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_MAG_FILTER
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_MIN_FILTER
import web.gl.WebGL2RenderingContext.Companion.UNSIGNED_BYTE
import web.gl.WebGLFramebuffer
import xim.poc.GlobalDirectory
import xim.poc.tools.TexturePreviewer
import xim.resource.DatId
import xim.resource.DirectoryResource
import xim.resource.TextureLink
import xim.resource.TextureResource

class GLFrameBuffer(val webgl: WebGL2RenderingContext, val name: String) {

    companion object {
        const val size = 512
    }

    private val id: WebGLFramebuffer = webgl.createFramebuffer()!!
    val texture: TextureLink = generateColorTexture()

    private var ready = false

    fun isReady(): Boolean {
        if (ready) { return true }
        ready = webgl.checkFramebufferStatus(FRAMEBUFFER) == FRAMEBUFFER_COMPLETE
        return ready
    }

    fun bind(): GLFrameBuffer {
        webgl.bindFramebuffer(FRAMEBUFFER, id)
        return this
    }

    fun unbind() {
        webgl.bindFramebuffer(FRAMEBUFFER, null)
    }

    fun clear(): GLFrameBuffer {
        webgl.clearColor(0f, 0f, 0f, 0f)
        webgl.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
        return this
    }

    private fun generateColorTexture(): TextureLink {
        val texture = webgl.createTexture()!!
        webgl.bindTexture(TEXTURE_2D, texture)
        webgl.texImage2D(TEXTURE_2D, 0, RGBA as GLint, size, size, 0, RGBA, UNSIGNED_BYTE, pixels = null)
        webgl.texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, LINEAR as GLint)
        webgl.texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, LINEAR as GLint)
        webgl.bindTexture(TEXTURE_2D, null)

        val name = "fbuffer ${name.padEnd(8, ' ')}"
        val textureReference = TextureReference(texture, size, size, name)
        val textureResource = TextureResource(DatId.zero, name, textureReference)
        DirectoryResource.setGlobalTexture(textureResource)
        TexturePreviewer.register(textureResource)

        bind()
        webgl.framebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0, TEXTURE_2D, texture, 0)
        unbind()

        return TextureLink(name, GlobalDirectory.directoryResource)
    }

}