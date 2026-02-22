package xim.poc.gl

import web.gl.WebGL2RenderingContext
import web.gl.WebGLUniformLocation
import xim.poc.gl.ShaderConstants.fogCalcFn
import xim.poc.gl.ShaderConstants.getUniformDiffuseLight
import xim.poc.gl.ShaderConstants.getUniformFog
import xim.poc.gl.ShaderConstants.getUniformPointLight

class XimLocations(val ximProgram: GLProgram, val context: WebGL2RenderingContext) {

    val diffuseTexture: WebGLUniformLocation

    val uProjMatrix: WebGLUniformLocation
    val uModelMatrix: WebGLUniformLocation
    val uViewMatrix: WebGLUniformLocation
    val discardThreshold: WebGLUniformLocation
    val positionBlendWeight: WebGLUniformLocation
    val uColorMask: WebGLUniformLocation

    val ambientLightColor: WebGLUniformLocation

    val pointLight: Array<UniformPointLight>

    val diffuseLight0: UniformDiffuseLight
    val diffuseLight1: UniformDiffuseLight

    val fog: UniformFog

    val normalTexture: WebGLUniformLocation
    val enableNormalTexture: WebGLUniformLocation

    init {
        diffuseTexture = getUniformLoc("diffuseTexture")
        uProjMatrix = getUniformLoc("uProjMatrix")
        uModelMatrix = getUniformLoc("uModelMatrix")
        uViewMatrix = getUniformLoc("uViewMatrix")
        discardThreshold = getUniformLoc("discardThreshold")
        positionBlendWeight = getUniformLoc("positionBlendWeight")
        uColorMask = getUniformLoc("colorMask")

        ambientLightColor = getUniformLoc("ambientLightColor")

        pointLight = arrayOf(
            getUniformPointLight(ximProgram, "pointLights[0]"),
            getUniformPointLight(ximProgram, "pointLights[1]"),
            getUniformPointLight(ximProgram, "pointLights[2]"),
            getUniformPointLight(ximProgram, "pointLights[3]"),
        )

        diffuseLight0 = getUniformDiffuseLight(ximProgram, "diffuseLights[0]")
        diffuseLight1 = getUniformDiffuseLight(ximProgram, "diffuseLights[1]")

        fog = getUniformFog(ximProgram, "fog")

        normalTexture = getUniformLoc("normalTexture")
        enableNormalTexture = getUniformLoc("enableNormalTexture")
    }

    private fun getUniformLoc(name: String) : web.gl.WebGLUniformLocation {
        return context.getUniformLocation(ximProgram.programId, name) ?: throw IllegalStateException("$name is not defined in XimProgram")
    }

}

object XimShader {

    private lateinit var locations: XimLocations

    fun getLocations(ximProgram: GLProgram, context: WebGL2RenderingContext) : XimLocations {
        loadLocations(ximProgram, context)
        return locations
    }

    private fun loadLocations(ximProgram: GLProgram, context: WebGL2RenderingContext) {
        if (!this::locations.isInitialized) {
            locations = XimLocations(ximProgram, context)
        }
    }


    const val vertShader = """${ShaderConstants.version}

${ShaderConstants.pointLightStruct}
${ShaderConstants.pointLightCalcFn}

uniform sampler2D diffuseTexture;

uniform mat4 uProjMatrix;
uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;

uniform float positionBlendWeight;

layout(location=0) in vec3 position0;
layout(location=1) in vec3 position1;

layout(location=2) in vec3 normal;
layout(location=3) in vec3 tangent;

layout(location=4) in vec2 textureCoords;
layout(location=8) in vec4 vertexColor;

out vec2 frag_textureCoords;
out vec4 frag_vertexColor;
out vec4 frag_cameraPos;
out vec4 frag_worldPos;
out vec3 frag_normal;
out mat3 frag_TBN;
out vec4 frag_pointLightSum;

void main(){
	frag_textureCoords = textureCoords;
    
    mat3 invTransposeModel = transpose(inverse(mat3(uModelMatrix)));
    vec3 mNormal = normalize(invTransposeModel * normal);
    vec3 mTangent = normalize(invTransposeModel * tangent);
    vec3 mBitangent = normalize(cross(mTangent, mNormal));
    frag_TBN = mat3(mTangent, mBitangent, mNormal);
    
    vec3 position = position0 + positionBlendWeight * position1;
    vec4 worldPos = uModelMatrix * vec4(position, 1.0);
    vec4 cameraPos = uViewMatrix * worldPos;

    for (int i = 0; i < 4; i++) {
        frag_pointLightSum += pointLightCalc(worldPos, mNormal, vertexColor, pointLights[i]);
    }

    frag_vertexColor = vertexColor;
    frag_cameraPos = cameraPos;
    frag_worldPos = worldPos;
    frag_normal = mNormal;

	gl_Position = uProjMatrix * cameraPos;
}
"""


    const val fragShader = """${ShaderConstants.version}
precision highp float;

${ShaderConstants.diffuseLightStruct}
${ShaderConstants.diffuseLightCalcFn}

${ShaderConstants.fogStruct}
$fogCalcFn

uniform float discardThreshold;
uniform sampler2D diffuseTexture;
uniform vec4 colorMask;

uniform vec4 ambientLightColor;

uniform sampler2D normalTexture;
uniform float enableNormalTexture;

in vec2 frag_textureCoords;
in vec3 frag_normal;
in vec4 frag_vertexColor;
in vec4 frag_worldPos;
in vec4 frag_cameraPos;
in mat3 frag_TBN;
in vec4 frag_pointLightSum;

out vec4 outColor;

void main()
{
    vec3 lightingNormal;
    if (enableNormalTexture > 0.0) {
        vec3 sampledNormal = texture(normalTexture, frag_textureCoords).rgb;
        sampledNormal = sampledNormal * 2.0 - 1.0;
        lightingNormal = normalize(frag_TBN * sampledNormal);
    } else {
        lightingNormal = frag_normal;
    }

    // Lighting Calc
    vec4 finalAmbientColor = frag_vertexColor * ambientLightColor;

    vec4 df0 = diffuseLightCalc(lightingNormal, frag_vertexColor, diffuseLights[0]);
    vec4 df1 = diffuseLightCalc(lightingNormal, frag_vertexColor, diffuseLights[1]);

    vec4 litFragColor = clamp(vec4((finalAmbientColor + frag_pointLightSum + df0 + df1).rgb, frag_vertexColor.a), 0.0, 1.0);

    vec4 basePixel = texture(diffuseTexture, frag_textureCoords); 
    vec4 coloredPixel = vec4(2.0 * litFragColor.rgb * basePixel.rgb, 4.0 * litFragColor.a * basePixel.a);
    
    if (coloredPixel.a < discardThreshold) { discard; }
    
	outColor = colorMask * fogCalc(frag_cameraPos.xyz, coloredPixel);
}        
"""

}