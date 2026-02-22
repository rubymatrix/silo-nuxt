package xim.poc.gl;

import js.typedarrays.Float32Array
import web.gl.*
import web.gl.WebGL2RenderingContext.Companion.TEXTURE0
import web.gl.WebGL2RenderingContext.Companion.TEXTURE1
import web.gl.WebGL2RenderingContext.Companion.TEXTURE2
import web.gl.WebGL2RenderingContext.Companion.TEXTURE3
import web.gl.WebGL2RenderingContext.Companion.TEXTURE4
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_2D
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_CUBE_MAP
import xim.math.Vector2f

class GLProgram(
        val programId: WebGLProgram,
        val webgl: WebGL2RenderingContext
) {

    fun bind(): GLProgram {
        webgl.useProgram(programId);
        return this;
    }

    fun setUniformMatrix3f(uniformLocation: WebGLUniformLocation, matrix: Float32Array): GLProgram {
        webgl.uniformMatrix3fv(uniformLocation, false, matrix, srcLength = null, srcOffset = null)
        return this
    }

    fun setUniformMatrix4f(uniformLoc: WebGLUniformLocation, matrix: Float32Array): GLProgram {
        webgl.uniformMatrix4fv(uniformLoc, false, matrix, srcLength = null, srcOffset = null)
        return this
    }

    fun setUniform(uniformLocation: WebGLUniformLocation , value: Boolean) : GLProgram {
        return setUniform(uniformLocation, if (value) { 1f } else { 0f })
    }

    fun setUniform(uniformLocation: WebGLUniformLocation , value: Int) : GLProgram {
        webgl.uniform1i(uniformLocation, value);
        return this;
    }

    fun setUniform(uniformLocation: WebGLUniformLocation , value: Float) : GLProgram {
        webgl.uniform1f(uniformLocation, value);
        return this;
    }

    fun setUniform2f(uniformLocation: WebGLUniformLocation , v0: Float, v1: Float) : GLProgram {
        webgl.uniform2f(uniformLocation, v0, v1)
        return this
    }

    fun setUniform2f(uniformLocation: WebGLUniformLocation, value: Vector2f) : GLProgram {
        webgl.uniform2f(uniformLocation, value.x, value.y)
        return this
    }

    fun setUniform3f(uniformLocation: WebGLUniformLocation, value: Float32Array) : GLProgram {
        webgl.uniform3fv(uniformLocation, value, srcLength = null, srcOffset = null)
        return this
    }

    fun setUniform4f(uniformLocation: WebGLUniformLocation , value: Float32Array) : GLProgram {
        webgl.uniform4fv(uniformLocation, value, srcLength = null, srcOffset = null)
        return this;
    }

    fun bindTexture(textureUniformLocation: WebGLUniformLocation, textureIndex: Int, texture: WebGLTexture) : GLProgram {
        webgl.activeTexture(mapToTexture(textureIndex))
        webgl.bindTexture(TEXTURE_2D, texture)
        setUniform(textureUniformLocation, textureIndex)
        return this
    }

    fun bindCubeTexture(textureUniformLocation: WebGLUniformLocation, textureIndex: Int, texture: WebGLTexture) : GLProgram {
        webgl.activeTexture(mapToTexture(textureIndex))
        webgl.bindTexture(TEXTURE_CUBE_MAP, texture)
        setUniform(textureUniformLocation, textureIndex)
        return this
    }

     fun getUniformLocation(uniformName: String) : WebGLUniformLocation {
         return webgl.getUniformLocation(programId, uniformName)!!;
     }

    private fun mapToTexture(index: Int): GLenum {
        return when(index) {
            0 -> TEXTURE0
            1 -> TEXTURE1
            2 -> TEXTURE2
            3 -> TEXTURE3
            4 -> TEXTURE4
            else -> throw IllegalArgumentException("Need to map more textures")
        }
    }

 }
