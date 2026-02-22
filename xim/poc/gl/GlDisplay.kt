package xim.poc.gl

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import web.gl.GLenum
import web.gl.WebGL2RenderingContext

object GlDisplay {

    private lateinit var context: WebGL2RenderingContext
    private var compressedTextures: dynamic = null

    fun getContext(): WebGL2RenderingContext {
        if (!this::context.isInitialized) {
            val canvas = document.getElementById("canvas") as HTMLCanvasElement
            context = canvas.getContext("webgl2") as WebGL2RenderingContext
            enableCompressedTextures()
        }

        return context
    }

    fun getCompressedTextureExtension(type: String): GLenum {
        // https://developer.mozilla.org/en-US/docs/Web/API/WEBGL_compressed_texture_s3tc
        return when(type) {
            "DXT3" -> compressedTextures["COMPRESSED_RGBA_S3TC_DXT3_EXT"] as GLenum
            "DXT1" -> compressedTextures["COMPRESSED_RGBA_S3TC_DXT1_EXT"] as GLenum
            else -> throw IllegalArgumentException("Unknown compressed type $type")
        }
    }

    fun isCompressedTextureEnabled(): Boolean {
        return compressedTextures != null
    }

    private fun enableCompressedTextures() {
        compressedTextures = context.getExtension("WEBGL_compressed_texture_s3tc")
            ?: context.getExtension("MOZ_WEBGL_compressed_texture_s3tc")
            ?: context.getExtension("WEBKIT_WEBGL_compressed_texture_s3tc")
    }

}