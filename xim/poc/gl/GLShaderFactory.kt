package xim.poc.gl;

import web.gl.GLenum
import web.gl.WebGL2RenderingContext
import web.gl.WebGL2RenderingContext.Companion.COMPILE_STATUS
import web.gl.WebGL2RenderingContext.Companion.FRAGMENT_SHADER
import web.gl.WebGL2RenderingContext.Companion.VERTEX_SHADER
import web.gl.WebGLShader

class GLShaderFactory(private val webgl: WebGL2RenderingContext) {

    fun getGlShader(shaderSource: String, shaderType: String): GLShader {
        val shaderId = createShader(shaderSource, shaderType)
        return GLShader(shaderId)
    }

    private fun createShader(shaderSource: String, shaderType: String): WebGLShader {
        val shaderId = webgl.createShader(getShaderType(shaderType))!!

        webgl.shaderSource(shaderId, shaderSource);
        webgl.compileShader(shaderId);

        if (!(webgl.getShaderParameter(shaderId, COMPILE_STATUS) as Boolean)) {
            val shaderLog = webgl.getShaderInfoLog(shaderId)

            val lines = shaderSource.split("\n")
            for (i in lines.indices) {
                println("[${i+1}] ${lines[i]}")
            }

            throw RuntimeException("Couldn't compile shader [$shaderType] $shaderLog\n")
        }

        return shaderId
    }

    private fun getShaderType(type: String): GLenum {
        return when (type) {
            "vert" -> VERTEX_SHADER
            "frag" -> FRAGMENT_SHADER
            else -> throw RuntimeException("Unknown shader type: $type")
        }
    }
}
