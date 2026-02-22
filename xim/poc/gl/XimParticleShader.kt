package xim.poc.gl

import js.typedarrays.Float32Array
import web.gl.WebGL2RenderingContext
import web.gl.WebGL2RenderingContext.Companion.UNIFORM_BUFFER
import web.gl.WebGLBuffer
import web.gl.WebGLUniformLocation

class XimParticleLocations(val program: GLProgram, val context: WebGL2RenderingContext) {

    // Texture stage state
    val textureStage0: WebGLUniformLocation
    val textureStage0Translate: WebGLUniformLocation

    // Rendering State
    val textureFactor: WebGLUniformLocation
    val ignoreTextureAlpha: WebGLUniformLocation
    val discardThreshold: WebGLUniformLocation

    // Lighting
    val computeLighting: WebGLUniformLocation
    val ambientLightColor: WebGLUniformLocation
    val diffuseLight0: UniformDiffuseLight
    val diffuseLight1: UniformDiffuseLight
    val pointLight: UniformPointLight

    val computeFog: WebGLUniformLocation
    val fog: UniformFog

    // Haze
    val hazeEffect: WebGLUniformLocation
    val previousFrameTransform: WebGLUniformLocation

    // Transforms
    val uProjMatrix: WebGLUniformLocation
    val uModelMatrix: WebGLUniformLocation
    val uViewMatrix: WebGLUniformLocation
    val uModelViewMatrix: WebGLUniformLocation

    // Specular
    val uComputeSpecular: WebGLUniformLocation
    val uTextureSpecular: WebGLUniformLocation
    val uTextureHighlight: WebGLUniformLocation
    val uInvTransViewModelMatrix: WebGLUniformLocation
    val uSpecularMatrix: WebGLUniformLocation
    val uSpecularColor: WebGLUniformLocation

    // Batching
    val batchOffsetBuffer: WebGLBuffer

    init {
        textureStage0 = getUniformLoc("textureStage0")
        textureStage0Translate = getUniformLoc("textureStage0Translate")

        textureFactor = getUniformLoc("textureFactor")
        ignoreTextureAlpha = getUniformLoc("ignoreTextureAlpha")
        discardThreshold = getUniformLoc("discardThreshold")

        computeLighting = getUniformLoc("computeLighting")
        ambientLightColor = getUniformLoc("ambientLightColor")
        diffuseLight0 = ShaderConstants.getUniformDiffuseLight(program, "diffuseLights[0]")
        diffuseLight1 = ShaderConstants.getUniformDiffuseLight(program, "diffuseLights[1]")
        pointLight = ShaderConstants.getUniformPointLight(program, "pointLights[0]")

        computeFog = getUniformLoc("computeFog")
        fog = ShaderConstants.getUniformFog(program, "fog")

        hazeEffect = getUniformLoc("hazeEffect")
        previousFrameTransform = getUniformLoc("uPreviousFrameTransform")

        uProjMatrix = getUniformLoc("uProjMatrix")
        uModelMatrix = getUniformLoc("uModelMatrix")
        uViewMatrix = getUniformLoc("uViewMatrix")
        uModelViewMatrix = getUniformLoc("uModelViewMatrix")

        uComputeSpecular = getUniformLoc("computeSpecular")
        uTextureSpecular = getUniformLoc("textureSpecular")
        uTextureHighlight = getUniformLoc("textureHighlight")

        uInvTransViewModelMatrix = getUniformLoc("uInvTransViewModelMatrix")

        uSpecularMatrix = getUniformLoc("uSpecularMatrix")
        uSpecularColor = getUniformLoc("specularColor")

        batchOffsetBuffer = context.createBuffer()!!
        context.bindBuffer(UNIFORM_BUFFER, batchOffsetBuffer)
        context.bufferData(UNIFORM_BUFFER, 4096, WebGL2RenderingContext.DYNAMIC_DRAW)

        val blockIndex = context.getUniformBlockIndex(program.programId, "BatchOffsets")
        context.uniformBlockBinding(program.programId, blockIndex, 1)
        context.bindBufferBase(UNIFORM_BUFFER, 1, batchOffsetBuffer)
    }

    private fun getUniformLoc(name: String) : WebGLUniformLocation {
        return context.getUniformLocation(program.programId, name) ?: throw IllegalStateException("$name is not defined in XimParticleProgram")
    }

}

object XimParticleShader {

    private lateinit var locations: XimParticleLocations

    private var dirtyBuffer = false

    fun getLocations(program: GLProgram, context: WebGL2RenderingContext) : XimParticleLocations {
        loadLocations(program, context)
        return locations
    }

    fun setBatchOffsets(batchOffsets: Float32Array?, context: WebGL2RenderingContext) {
        if (batchOffsets == null && !dirtyBuffer) { return }
        dirtyBuffer = batchOffsets != null

        val offsets = batchOffsets ?: Float32Array(4)
        context.bufferSubData(UNIFORM_BUFFER, 0, offsets, 0, offsets.length)
    }

    private fun loadLocations(program: GLProgram, context: WebGL2RenderingContext) {
        if (!this::locations.isInitialized) {
            locations = XimParticleLocations(program, context)
        }
    }


    const val vertShader = """${ShaderConstants.version}
uniform sampler2D textureStage0;

layout (std140) uniform BatchOffsets
{
    vec4 uBatchOffsets[256];
};

uniform mat4 uProjMatrix;
uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uModelViewMatrix;

uniform mat3 uInvTransViewModelMatrix;

uniform mat3 uSpecularMatrix;

uniform mat4 uPreviousFrameTransform;

uniform float computeLighting;
uniform vec4 ambientLightColor;
${ShaderConstants.diffuseLightStruct}
${ShaderConstants.pointLightStruct}

layout(location=0) in vec3 position;
layout(location=2) in vec3 normal;
layout(location=4) in vec2 textureCoords;
layout(location=8) in vec4 vertexColor;

out vec2 frag_textureCoords;
out vec4 frag_cameraSpacePosition;
out vec3 frag_cameraSpaceNormal;
out vec4 frag_color;
out vec4 frag_hazePosition;

${ShaderConstants.diffuseLightCalcFn}
${ShaderConstants.pointLightCalcFn}

void main(){
	frag_textureCoords = textureCoords;

    vec4 batchOffset = uViewMatrix * uBatchOffsets[gl_InstanceID];
    vec4 cameraSpacePosition = uModelViewMatrix * vec4(position, 1.0) + batchOffset;

    frag_cameraSpacePosition = cameraSpacePosition;
    frag_cameraSpaceNormal = normalize(uInvTransViewModelMatrix * uSpecularMatrix * normal);

    if (computeLighting > 0.0) {
        mat3 invTransModelMatrix = transpose(inverse(mat3(uModelMatrix))); 
        vec4 worldSpacePos = uModelMatrix * vec4(position, 1.0);
        vec3 worldSpaceNormal = normalize(invTransModelMatrix * normal);
   
        vec4 df0 = diffuseLightCalc(worldSpaceNormal, vertexColor, diffuseLights[0]);
        vec4 df1 = diffuseLightCalc(worldSpaceNormal, vertexColor, diffuseLights[1]);

        vec4 pl0 = pointLightCalc(worldSpacePos, worldSpaceNormal, vertexColor, pointLights[0]);

        vec4 finalAmbientColor = vertexColor * ambientLightColor;
        frag_color = clamp(vec4((finalAmbientColor + df0 + df1 + pl0).rgb, vertexColor.a), 0.0, 1.0);
    } else {
        frag_color = vertexColor;
    }

    vec4 outPosition = uProjMatrix * cameraSpacePosition;
    
    vec4 previousPosition = uProjMatrix * (uPreviousFrameTransform * vec4(position, 1.0) + batchOffset);
    frag_hazePosition = 0.75 * outPosition +  0.25 * previousPosition;
   
	gl_Position = outPosition;
}
"""


    const val fragShader = """${ShaderConstants.version}
precision highp float;

uniform vec4 textureFactor;
uniform vec4 specularColor;

uniform sampler2D textureStage0;
uniform vec2 textureStage0Translate;

uniform float ignoreTextureAlpha;
uniform float computeSpecular;
uniform float discardThreshold;

uniform float hazeEffect;

uniform sampler2D textureSpecular;
uniform samplerCube textureHighlight;

in vec2 frag_textureCoords;
in vec4 frag_cameraSpacePosition;
in vec3 frag_cameraSpaceNormal;
in vec4 frag_color;
in vec4 frag_hazePosition;

out vec4 outColor;

uniform float computeFog;
${ShaderConstants.fogStruct}
${ShaderConstants.fogCalcFn}

void main()
{
    // Stage 0
    vec4 textureStage0Pixel;
     
    if (hazeEffect == 0.0) {
        textureStage0Pixel = texture(textureStage0, textureStage0Translate + frag_textureCoords); 
    } else {
        vec2 screenSpacePosition = 0.5 * (vec2(frag_hazePosition.xy / frag_hazePosition.w) + 1.0);
        vec2 hazeUv = textureStage0Translate + screenSpacePosition;
        textureStage0Pixel = texture(textureStage0, hazeUv);
        textureStage0Pixel.a = 0.25;
    }
    
    if (ignoreTextureAlpha > 0.0) {
        textureStage0Pixel.a = 0.5;
    }
    
    vec4 stage0Pixel = 2.0 * (frag_color * textureStage0Pixel);

    // Stage 1
    vec4 stage1Pixel = vec4(2.0 * stage0Pixel.rgb * textureFactor.rgb, 4.0 * stage0Pixel.a * textureFactor.a);
    
    // Stage 2 - specular
    vec4 stage2Pixel;

    if (computeSpecular > 0.0) {
        vec4 specularTexSample = texture(textureSpecular, frag_cameraSpaceNormal.xy);
        vec4 specularPixel = 2.0 * specularTexSample * specularColor;
        vec3 specularComponent = stage1Pixel.a * specularPixel.a * specularPixel.rgb;
        
        vec4 highlightSample = texture(textureHighlight, frag_cameraSpaceNormal);
        vec4 highlightPixel = 2.0 * highlightSample * specularColor;
        vec3 highlightComponent = stage1Pixel.a * highlightPixel.a * highlightPixel.rgb;
        
        stage2Pixel = vec4(stage1Pixel.rgb + specularComponent + highlightComponent, stage1Pixel.a);
    } else {
	    stage2Pixel = stage1Pixel;      
    }

    if (stage2Pixel.a < discardThreshold) {
        discard;
    }

    // Fog
    if (computeFog > 0.0) {
        outColor = fogCalc(frag_cameraSpacePosition.xyz, stage2Pixel);
    } else {
        outColor = stage2Pixel;
    }
}        
"""
}