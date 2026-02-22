package xim.poc.gl

import js.typedarrays.Float32Array
import web.gl.WebGL2RenderingContext
import web.gl.WebGL2RenderingContext.Companion.DYNAMIC_DRAW
import web.gl.WebGL2RenderingContext.Companion.UNIFORM_BUFFER
import web.gl.WebGLBuffer
import web.gl.WebGLUniformLocation
import xim.math.Matrix4f
import xim.poc.gl.XimSkinnedLocations.Companion.maxNumJoints
import xim.resource.JointInstance

class XimSkinnedLocations(val ximProgram: GLProgram, val context: WebGL2RenderingContext) {

    companion object {
        const val maxNumJoints = 128
    }

    val jointUniformBuffer: WebGLBuffer

    val uDiffuseTexture: WebGLUniformLocation
    val uSpecularTexture: WebGLUniformLocation

    val uProjMatrix: WebGLUniformLocation
    val uModelMatrix: WebGLUniformLocation
    val uViewMatrix: WebGLUniformLocation
    val uDiscardThreshold: WebGLUniformLocation
    val uComputeSpecular: WebGLUniformLocation
    val uColorMask: WebGLUniformLocation
    val uEffectColor: WebGLUniformLocation

    val uWrapTexture: WebGLUniformLocation
    val uWrapTextureOffset: WebGLUniformLocation
    val uWrapTextureColor: WebGLUniformLocation

    val ambientLightColor: WebGLUniformLocation

    val pointLights: Array<UniformPointLight>

    val diffuseLight0: UniformDiffuseLight
    val diffuseLight1: UniformDiffuseLight

    val fog: UniformFog

    init {
        uDiffuseTexture = getUniformLoc("diffuseTexture")
        uSpecularTexture = getUniformLoc("specularHighlight")

        uProjMatrix = getUniformLoc("uProjMatrix")
        uModelMatrix = getUniformLoc("uModelMatrix")
        uViewMatrix = getUniformLoc("uViewMatrix")
        uDiscardThreshold = getUniformLoc("discardThreshold")
        uComputeSpecular = getUniformLoc("computeSpecular")
        uColorMask = getUniformLoc("colorMask")
        uEffectColor = getUniformLoc("uEffectColor")

        uWrapTexture = getUniformLoc("uWrapTexture")
        uWrapTextureOffset = getUniformLoc("uWrapTextureOffset")
        uWrapTextureColor = getUniformLoc("uWrapTextureColor")

        ambientLightColor = getUniformLoc("ambientLightColor")
        pointLights = arrayOf(
            ShaderConstants.getUniformPointLight(ximProgram, "pointLights[0]"),
            ShaderConstants.getUniformPointLight(ximProgram, "pointLights[1]"),
            ShaderConstants.getUniformPointLight(ximProgram, "pointLights[2]"),
            ShaderConstants.getUniformPointLight(ximProgram, "pointLights[3]"),
        )

        diffuseLight0 = ShaderConstants.getUniformDiffuseLight(ximProgram, "diffuseLights[0]")
        diffuseLight1 = ShaderConstants.getUniformDiffuseLight(ximProgram, "diffuseLights[1]")

        fog = ShaderConstants.getUniformFog(ximProgram, "fog")

        jointUniformBuffer = context.createBuffer()!!
        context.bindBuffer(UNIFORM_BUFFER, jointUniformBuffer)
        context.bufferData(UNIFORM_BUFFER, maxNumJoints * 64, DYNAMIC_DRAW)

        val blockIndex = context.getUniformBlockIndex(ximProgram.programId, "JointMatrices")
        context.uniformBlockBinding(ximProgram.programId, blockIndex, 0)
        context.bindBufferBase(UNIFORM_BUFFER, 0, jointUniformBuffer)
    }

    private fun getUniformLoc(name: String) : WebGLUniformLocation {
        return context.getUniformLocation(ximProgram.programId, name) ?: throw IllegalStateException("$name is not defined in XimProgram")
    }

}

object XimSkinnedShader {

    private lateinit var locations: XimSkinnedLocations

    private val jointStorage = Float32Array(maxNumJoints * 16)

    fun getLocations(ximProgram: GLProgram, context: WebGL2RenderingContext) : XimSkinnedLocations {
        loadLocations(ximProgram, context)
        return locations
    }

    fun setJointMatrices(ximProgram: GLProgram, joints: List<JointInstance>, context: WebGL2RenderingContext) {
        loadLocations(ximProgram, context)

        if (joints.isEmpty()) {
            jointStorage.set(Matrix4f.identity.m, 0)
        } else {
            for (i in joints.indices) { jointStorage.set(joints[i].currentTransform.m, i * 16) }
        }

        context.bindBuffer(UNIFORM_BUFFER, locations.jointUniformBuffer)
        context.bufferSubData(UNIFORM_BUFFER, 0, jointStorage, 0, joints.size * 16)
    }

    private fun loadLocations(ximProgram: GLProgram, context: WebGL2RenderingContext) {
        if (!this::locations.isInitialized) {
            locations = XimSkinnedLocations(ximProgram, context)
        }
    }


    const val vertShader = """${ShaderConstants.version}

${ShaderConstants.pointLightStruct}

${ShaderConstants.diffuseLightStruct}

layout (std140) uniform JointMatrices
{
    mat4 joints[${maxNumJoints}];
};

uniform mat4 uProjMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uModelMatrix;

uniform vec4 ambientLightColor;
uniform vec3 diffuseLightDir;
uniform vec4 diffuseLightColor;
uniform float diffuseIntensity;

layout(location=0) in vec3 position0;
layout(location=1) in vec3 position1;

layout(location=2) in vec3 normal0;
layout(location=3) in vec3 normal1;

layout(location=4) in vec2 textureCoords;

layout(location=5) in float jointWeight;
layout(location=6) in float joint0;
layout(location=7) in float joint1;

layout(location=8) in vec4 vertexColor;

uniform float computeSpecular;

out vec2 frag_textureCoords;
out vec4 frag_cameraSpacePos;
out vec4 frag_color;
out vec3 frag_normals;
out vec3 cam_normals;

${ShaderConstants.diffuseLightCalcFn}

${ShaderConstants.pointLightCalcFn}

void main(){
	frag_textureCoords = textureCoords;

    mat3 invTransModel = transpose(inverse(mat3(uModelMatrix)));
	vec4 skinnedNormal = jointWeight * (joints[int(joint0)] * vec4(normal0, 0.0)) + (1.0-jointWeight) * (joints[int(joint1)] * vec4(normal1, 0.0));
    vec3 worldSpaceNormal = normalize(invTransModel * normalize(skinnedNormal.xyz));
    frag_normals = worldSpaceNormal;
    cam_normals = normalize(uViewMatrix * vec4(worldSpaceNormal, 0.0)).xyz;

	vec4 modelSpacePos = (joints[int(joint0)] * vec4(position0, jointWeight)) + (joints[int(joint1)] * vec4(position1,1.0-jointWeight));
    vec4 worldSpacePos = uModelMatrix * modelSpacePos;
    vec4 camSpacePos = uViewMatrix * worldSpacePos;
    
    vec4 df0 = diffuseLightCalc(worldSpaceNormal, vertexColor, diffuseLights[0]);
    vec4 df1 = diffuseLightCalc(worldSpaceNormal, vertexColor, diffuseLights[1]);

    vec4 pl;
    for (int i = 0; i < 4; i++) {
        pl += pointLightCalc(worldSpacePos, worldSpaceNormal, vertexColor, pointLights[i]);
    }
    
    frag_color = clamp(vec4((ambientLightColor + pl + df0 + df1).rgb, vertexColor.a), 0.0, 1.0);
    frag_cameraSpacePos = camSpacePos;

	gl_Position = uProjMatrix * camSpacePos;
}
"""


    const val fragShader = """${ShaderConstants.version}
precision highp float;

uniform vec4 colorMask;
uniform vec4 uEffectColor;
uniform float discardThreshold;

uniform sampler2D diffuseTexture;
uniform samplerCube specularHighlight;

uniform sampler2D uWrapTexture;
uniform vec2 uWrapTextureOffset;
uniform vec4 uWrapTextureColor;

in vec2 frag_textureCoords;
in vec4 frag_color;

uniform float computeSpecular;
in vec4 frag_cameraSpacePos;
in vec3 frag_normals;
in vec3 cam_normals;

${ShaderConstants.fogStruct}
${ShaderConstants.fogCalcFn}

out vec4 outColor;

void main() {
    vec4 diffusePixel = texture(diffuseTexture, frag_textureCoords); 
    vec4 coloredPixel;

    if (computeSpecular > 0.0) {
         coloredPixel = vec4(2.0 * frag_color.rgb * diffusePixel.rgb, 2.0 * diffusePixel.a * 0.25); // TODO - alpha should modulate with t-factor
    } else {
        coloredPixel = vec4(2.0 * frag_color.rgb * diffusePixel.rgb, 4.0 * frag_color.a * diffusePixel.a);
    }
    
    if (computeSpecular > 0.0) {
        vec3 specularRay = reflect(vec3(0.0, 0.0, 1.0), cam_normals.xyz); // Corresponds to D3DRS_LOCALVIEWER = false
        vec4 spec_color = texture(specularHighlight, specularRay);

        coloredPixel.rgb = coloredPixel.rgb + coloredPixel.a * spec_color.rgb;
        coloredPixel.a = coloredPixel.a + spec_color.a;
    } 
    
    if (coloredPixel.a < discardThreshold) { discard; }

    vec4 baseWrapPixel = texture(uWrapTexture, frag_normals.xy + uWrapTextureOffset);
    vec4 coloredWrapPixel = 4.0 * uWrapTextureColor * baseWrapPixel;
    coloredPixel.rgb += (coloredWrapPixel.rgb * coloredWrapPixel.a);
    
	outColor = uEffectColor * colorMask * fogCalc(frag_cameraSpacePos.xyz, coloredPixel) * 4.0;
}        
"""

}