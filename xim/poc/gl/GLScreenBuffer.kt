package xim.poc.gl

import web.gl.*
import web.gl.WebGL2RenderingContext.Companion.COLOR_ATTACHMENT0
import web.gl.WebGL2RenderingContext.Companion.DEPTH24_STENCIL8
import web.gl.WebGL2RenderingContext.Companion.DEPTH_STENCIL_ATTACHMENT
import web.gl.WebGL2RenderingContext.Companion.FRAMEBUFFER
import web.gl.WebGL2RenderingContext.Companion.FRAMEBUFFER_COMPLETE
import web.gl.WebGL2RenderingContext.Companion.NEAREST
import web.gl.WebGL2RenderingContext.Companion.RENDERBUFFER
import web.gl.WebGL2RenderingContext.Companion.RGBA
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_2D
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_MAG_FILTER
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_MIN_FILTER
import web.gl.WebGL2RenderingContext.Companion.UNSIGNED_BYTE
import xim.poc.GlobalDirectory
import xim.poc.tools.TexturePreviewer
import xim.resource.DatId
import xim.resource.DirectoryResource
import xim.resource.TextureLink
import xim.resource.TextureResource

class GLScreenBuffer(val webgl: WebGL2RenderingContext, val name: String, val width: Int, val height: Int, private val readOnlyDepthBuffer: WebGLRenderbuffer? = null) {

    private val id: WebGLFramebuffer = webgl.createFramebuffer()!!

    val texture: TextureLink

    private val glTexture: WebGLTexture
    private val depthBuffer: WebGLRenderbuffer

    private var ready = false

    init {
        glTexture = webgl.createTexture()!!
        webgl.bindTexture(TEXTURE_2D, glTexture)
        webgl.texImage2D(TEXTURE_2D, 0, RGBA as GLint, width, height, 0, RGBA, UNSIGNED_BYTE, pixels = null)
        webgl.texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, NEAREST as GLint)
        webgl.texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, NEAREST as GLint)
        webgl.bindTexture(TEXTURE_2D, null)

        depthBuffer = readOnlyDepthBuffer ?: createDepthBuffer()

        val textureReference = TextureReference(glTexture, width, height, name)
        val textureResource = TextureResource(DatId.zero, name, textureReference)
        DirectoryResource.setGlobalTexture(textureResource)
        TexturePreviewer.register(textureResource)

        bind()
        webgl.framebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0, TEXTURE_2D, glTexture, 0)
        webgl.framebufferRenderbuffer(FRAMEBUFFER, DEPTH_STENCIL_ATTACHMENT, RENDERBUFFER, depthBuffer)
        unbind()

        texture = TextureLink(name, GlobalDirectory.directoryResource)
    }

    fun isReady(): Boolean {
        if (ready) { return true }
        ready = webgl.checkFramebufferStatus(FRAMEBUFFER) == FRAMEBUFFER_COMPLETE
        return ready
    }

    fun bind(): GLScreenBuffer {
        webgl.bindFramebuffer(FRAMEBUFFER, id)
        return this
    }

    fun unbind() {
        webgl.bindFramebuffer(FRAMEBUFFER, null)
    }

    fun getDepthBuffer(): WebGLRenderbuffer {
        return depthBuffer
    }

    fun delete() {
        webgl.deleteFramebuffer(id)
        if (readOnlyDepthBuffer == null) { webgl.deleteRenderbuffer(depthBuffer) }
        webgl.deleteTexture(glTexture)
    }

    private fun createDepthBuffer(): WebGLRenderbuffer {
        val buffer = webgl.createRenderbuffer()!!
        webgl.bindRenderbuffer(RENDERBUFFER, buffer)
        webgl.renderbufferStorage(RENDERBUFFER, DEPTH24_STENCIL8, width, height)
        webgl.bindRenderbuffer(RENDERBUFFER, null)
        return buffer
    }

}