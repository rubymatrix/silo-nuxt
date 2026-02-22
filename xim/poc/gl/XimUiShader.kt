package xim.poc.gl

import web.gl.WebGL2RenderingContext
import web.gl.WebGLUniformLocation

class XimUiLocations(val ximProgram: GLProgram, val context: WebGL2RenderingContext) {

    val texture: WebGLUniformLocation
    val colorMask: WebGLUniformLocation
    val clipLowerLeft: WebGLUniformLocation
    val clipUpperRight: WebGLUniformLocation

    val uProjMatrix: WebGLUniformLocation
    val uModelMatrix: WebGLUniformLocation
    val uViewMatrx: WebGLUniformLocation

    val position: Int
    val textureCoords: Int
    val vertexColor: Int

    init {
        texture = getUniformLoc("uiTexture")
        colorMask = getUniformLoc("colorMask")
        clipLowerLeft = getUniformLoc("clipLowerLeft")
        clipUpperRight = getUniformLoc("clipUpperRight")

        uProjMatrix = getUniformLoc("uProjMatrix")
        uModelMatrix = getUniformLoc("uModelMatrix")
        uViewMatrx = getUniformLoc("uViewMatrix")

        position = context.getAttribLocation(ximProgram.programId, "vert")
        textureCoords = context.getAttribLocation(ximProgram.programId, "uv_v")
        vertexColor = context.getAttribLocation(ximProgram.programId, "vertexColor")
    }

    private fun getUniformLoc(name: String) : WebGLUniformLocation {
        return context.getUniformLocation(ximProgram.programId, name) ?: throw IllegalStateException("$name is not defined in XimParticleProgram")
    }

}

object XimUiShader {

    private lateinit var locations: XimUiLocations

    fun getLocations(program: GLProgram, context: WebGL2RenderingContext) : XimUiLocations {
        loadLocations(program, context)
        return locations
    }

    fun getAttributeLocations(program: GLProgram, context: WebGL2RenderingContext): Collection<Int> {
        loadLocations(program, context)
        return listOf(locations.position, locations.textureCoords, locations.vertexColor)
    }

    private fun loadLocations(program: GLProgram, context: WebGL2RenderingContext) {
        if (!this::locations.isInitialized) {
            locations = XimUiLocations(program, context)
        }
    }


    val basicVertSource = """${ShaderConstants.version}
uniform mat4 uProjMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uModelMatrix;

in vec3 vert;
in vec2 uv_v;
in vec4 vertexColor;

out vec4 position;
out vec2 uv_f;
out vec4 frag_color;

void main(void) {
    uv_f = uv_v;
    frag_color = vertexColor;
    position = uModelMatrix * vec4(vert, 1.0);
    gl_Position = uProjMatrix * uViewMatrix * position;
}
"""

    val basicFragSource = """${ShaderConstants.version}
precision mediump float;

uniform vec4 colorMask;
uniform vec2 clipLowerLeft;
uniform vec2 clipUpperRight;
uniform sampler2D uiTexture;

in vec4 position;
in vec2 uv_f;
in vec4 frag_color;

out vec4 outColor;

void main(void) {
    if (position.x < clipLowerLeft.x || position.y < clipLowerLeft.y || position.x > clipUpperRight.x || position.y > clipUpperRight.y) { discard; }

    vec4 pixel = texture(uiTexture, uv_f);
    outColor = 2.0 * colorMask * vec4(pixel.rgb * frag_color.rgb, 2.0 * pixel.a * frag_color.a);
}
"""

}