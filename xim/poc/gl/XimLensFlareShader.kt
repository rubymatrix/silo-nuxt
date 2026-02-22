package xim.poc.gl

import web.gl.WebGL2RenderingContext
import web.gl.WebGLUniformLocation

class XimLensFlareLocations(val program: GLProgram, val context: WebGL2RenderingContext) {

    val textureStage0: WebGLUniformLocation
    val textureFactor: WebGLUniformLocation

    val uProjMatrix: WebGLUniformLocation
    val uModelMatrix: WebGLUniformLocation

    init {
        textureStage0 = getUniformLoc("textureStage0")
        textureFactor = getUniformLoc("textureFactor")
        uProjMatrix = getUniformLoc("uProjMatrix")
        uModelMatrix = getUniformLoc("uModelMatrix")
    }

    private fun getUniformLoc(name: String) : WebGLUniformLocation {
        return context.getUniformLocation(program.programId, name) ?: throw IllegalStateException("$name is not defined in XimLensFlareProgram")
    }

}

object XimLensFlareShader {

    private lateinit var locations: XimLensFlareLocations

    fun getLocations(program: GLProgram, context: WebGL2RenderingContext) : XimLensFlareLocations {
        loadLocations(program, context)
        return locations
    }

    private fun loadLocations(program: GLProgram, context: WebGL2RenderingContext) {
        if (!this::locations.isInitialized) {
            locations = XimLensFlareLocations(program, context)
        }
    }


    const val vertShader = """${ShaderConstants.version}
uniform mat4 uProjMatrix;
uniform mat4 uModelMatrix;

layout(location=0) in vec3 position;
layout(location=4) in vec2 textureCoords;
layout(location=8) in vec4 vertexColor;

out vec2 frag_textureCoords;
out vec4 frag_color;

void main(){
    frag_textureCoords = textureCoords;
    frag_color = vertexColor;
	gl_Position = uProjMatrix * uModelMatrix * vec4(position, 1.0);
}
"""


    const val fragShader = """${ShaderConstants.version}
precision highp float;

uniform vec4 textureFactor;
uniform sampler2D textureStage0;

in vec2 frag_textureCoords;
in vec4 frag_color;

out vec4 outColor;

void main()
{
    vec4 textureStage0Pixel = texture(textureStage0, frag_textureCoords);     
    vec4 stage0Pixel = 2.0 * (frag_color * textureStage0Pixel);
    outColor = vec4(2.0 * stage0Pixel.rgb * textureFactor.rgb, 4.0 * stage0Pixel.a * textureFactor.a);
}        
"""
}