package xim.poc.gl

import web.gl.WebGL2RenderingContext
import web.gl.WebGLUniformLocation

class XimDecalLocations(val ximProgram: GLProgram, val context: WebGL2RenderingContext) {

    val diffuseTexture: WebGLUniformLocation

    val uProjMatrix: WebGLUniformLocation
    val uModelMatrix: WebGLUniformLocation
    val uViewMatrix: WebGLUniformLocation

    val uDecalProjMatrix: WebGLUniformLocation
    val uDecalViewMatrix: WebGLUniformLocation

    val positionBlendWeight: WebGLUniformLocation

    val textureFactor: WebGLUniformLocation

    val position0: Int
    val position1: Int

    init {
        diffuseTexture = getUniformLoc("diffuseTexture")
        uProjMatrix = getUniformLoc("uProjMatrix")
        uModelMatrix = getUniformLoc("uModelMatrix")
        uViewMatrix = getUniformLoc("uViewMatrix")

        uDecalProjMatrix = getUniformLoc("uDecalProjMatrix")
        uDecalViewMatrix = getUniformLoc("uDecalViewMatrix")

        positionBlendWeight = getUniformLoc("positionBlendWeight")

        textureFactor = getUniformLoc("textureFactor")

        position0 = getAttribLoc("position0")
        position1 = getAttribLoc("position1")
    }

    private fun getUniformLoc(name: String) : WebGLUniformLocation {
        return context.getUniformLocation(ximProgram.programId, name) ?: throw IllegalStateException("$name is not defined in XimProgram")
    }

    private fun getAttribLoc(name: String): Int {
        val loc = context.getAttribLocation(ximProgram.programId, name)
        if (loc < 0) throw IllegalStateException("$name is not a defined attr in XimProgram")
        return loc
    }

}

object XimDecalShader {

    private lateinit var locations: XimDecalLocations

    fun getLocations(ximProgram: GLProgram, context: WebGL2RenderingContext) : XimDecalLocations {
        loadLocations(ximProgram, context)
        return locations
    }

    fun getAttributeLocations(ximProgram: GLProgram, context: WebGL2RenderingContext): List<Int> {
        loadLocations(ximProgram, context)
        return listOf(locations.position0, locations.position1)
    }

    private fun loadLocations(ximProgram: GLProgram, context: WebGL2RenderingContext) {
        if (!this::locations.isInitialized) {
            locations = XimDecalLocations(ximProgram, context)
        }
    }


    const val vertShader = """${ShaderConstants.version}

uniform sampler2D diffuseTexture;

uniform mat4 uProjMatrix;
uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;

uniform mat4 uDecalProjMatrix;
uniform mat4 uDecalViewMatrix;

uniform float positionBlendWeight;

layout(location=0) in vec3 position0;
layout(location=1) in vec3 position1;

out vec4 decalPosition;

void main(){
    vec3 position = position0 + positionBlendWeight * position1;
    vec4 worldPos = uModelMatrix * vec4(position, 1.0);
	decalPosition = uDecalProjMatrix * uDecalViewMatrix * worldPos;
    gl_Position = uProjMatrix * uViewMatrix * worldPos;
}
"""


    const val fragShader = """${ShaderConstants.version}
precision highp float;

uniform sampler2D diffuseTexture;
uniform vec4 textureFactor;

in vec4 decalPosition;

out vec4 outColor;

void main()
{
    if (decalPosition.z < 0.0 || decalPosition.z > 2.0 || abs(decalPosition.x) > 1.0 || abs(decalPosition.y) > 1.0) {
      discard;
    }

    float uvX = clamp((decalPosition.x + 1.0) * 0.5, 0.0, 1.0);
    float uvY = clamp((decalPosition.y + 1.0) * 0.5, 0.0, 1.0);

    vec4 basePixel = texture(diffuseTexture, vec2(uvX, uvY)); 
    basePixel.a = 2.0 * basePixel.a * textureFactor.a;
	outColor = basePixel;
}        
"""

}