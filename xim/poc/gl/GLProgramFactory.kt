package xim.poc.gl

import web.gl.WebGL2RenderingContext
import web.gl.WebGL2RenderingContext.Companion.LINK_STATUS
import web.gl.WebGL2RenderingContext.Companion.VALIDATE_STATUS
import web.gl.WebGLProgram
import xim.util.OnceLogger

class GLProgramFactory(private val webgl: WebGL2RenderingContext) {

    fun getGLProgram(vertShader: GLShader, fragShader: GLShader): GLProgram {
        val programId = link(vertShader, fragShader)
        return GLProgram(programId, webgl)
    }

    private fun link(vertShader: GLShader, fragShader: GLShader): WebGLProgram {
        val programId = webgl.createProgram()!!

        webgl.attachShader(programId, vertShader.shaderId)
        webgl.attachShader(programId, fragShader.shaderId)

        webgl.linkProgram(programId)
        if (!(webgl.getProgramParameter(programId, LINK_STATUS) as Boolean)) {
            val error = webgl.getError()
            val programLog = webgl.getProgramInfoLog(programId)
            throw RuntimeException("Couldn't link program [error: $error]: $programLog")
        }

        webgl.validateProgram(programId)
        if (!(webgl.getProgramParameter(programId, VALIDATE_STATUS) as Boolean)) {
            val error = webgl.getError()
            val programLog = webgl.getProgramInfoLog(programId)
            OnceLogger.warn("Couldn't pre-validate program [error: $error]: $programLog")
        }

        return programId
    }

}
