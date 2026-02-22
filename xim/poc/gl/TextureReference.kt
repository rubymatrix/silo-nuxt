package xim.poc.gl;

import web.gl.WebGLTexture


class TextureReference(private val id: WebGLTexture, val width: Int, val height: Int, val name: String) {

    var released = false

    fun release() {
        if (!released) { GlDisplay.getContext().deleteTexture(id) }
        released = true
    }

    fun id(): WebGLTexture {
        if (released) { throw IllegalStateException("Already released texture. Name: $name") }
        return id
    }

}
