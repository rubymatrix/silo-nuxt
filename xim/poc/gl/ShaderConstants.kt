package xim.poc.gl

import web.gl.WebGLUniformLocation
import js.typedarrays.Float32Array
import xim.math.Vector3f
import xim.resource.FogParams

class UniformPointLight(
    val program: GLProgram,
    val position: WebGLUniformLocation,
    val color: WebGLUniformLocation,
    val range: WebGLUniformLocation,
    val attenuation: WebGLUniformLocation,
) {

    private var previous: UniformPointLightData? = null

    fun set(data: UniformPointLightData) {
        if (data == previous) { return }
        previous = data

        program.setUniform3f(position, data.position)
        program.setUniform4f(color, data.color)
        program.setUniform(range, data.range)
        program.setUniform3f(attenuation, data.attenuation)
    }

}

class UniformDiffuseLight(
    val program: GLProgram,
    val direction: WebGLUniformLocation,
    val color: WebGLUniformLocation,
) {

    private var previous: UniformDiffuseLightData? = null

    fun set(data: UniformDiffuseLightData) {
        if (data == previous) { return }
        previous = data

        program.setUniform3f(direction, data.dir)
        program.setUniform4f(color, data.color)
    }
}


class UniformPointLightData(
    val position: Float32Array,
    val color: Float32Array,
    val range: Float,
    val attenuation: Float32Array,
) {

    companion object {
        val noOp = UniformPointLightData(
            position = Float32Array(3),
            color = Float32Array(4),
            range = 0f,
            attenuation = Float32Array(3)
        )
    }

    constructor(pointLight: PointLight, constAttenuation: Float = 0f, linearAttenuation: Float = 0f) : this(
        position = pointLight.position.toTypedArray(),
        color = pointLight.color.rgba,
        range = pointLight.range,
        attenuation = Vector3f(constAttenuation, linearAttenuation, pointLight.attenuationQuad).toTypedArray(),
    )
}

class UniformDiffuseLightData(
    val dir: Float32Array,
    val color: Float32Array,
) {

    companion object {
        val noOp = UniformDiffuseLightData(
            dir = Float32Array(3),
            color = Float32Array(4),
        )
    }

    constructor(diffuseLight: DiffuseLight) : this(
        dir = diffuseLight.direction.toTypedArray(),
        color = diffuseLight.color.rgba,
    )

}

class UniformFog (
    val program: GLProgram,
    val near: WebGLUniformLocation,
    val far: WebGLUniformLocation,
    val color: WebGLUniformLocation,
) {

    private var previous: UniformFogData? = null

    fun set(data: UniformFogData) {
        if (data == previous) { return }
        previous = data

        program.setUniform(near, data.near)
        program.setUniform(far, data.far)
        program.setUniform4f(color, data.color.toRgbaArray())
    }

}

data class UniformFogData(
    val near: Float,
    val far: Float,
    val color: ByteColor,
) {

    companion object {
        val noOp = UniformFogData(
            near = 0f,
            far = Float.MAX_VALUE,
            color = ByteColor.zero,
        )
    }

    constructor(fogParams: FogParams) : this(
        near = fogParams.near,
        far = fogParams.far,
        color = fogParams.color
    )

}

object ShaderConstants {

    fun getUniformPointLight(glProgram: GLProgram, uniformName: String): UniformPointLight {
        return UniformPointLight(
            program = glProgram,
            position = glProgram.getUniformLocation("$uniformName.$position"),
            color = glProgram.getUniformLocation("$uniformName.$color"),
            range = glProgram.getUniformLocation("$uniformName.$range"),
            attenuation = glProgram.getUniformLocation("$uniformName.$attenuation"),
        )
    }

    fun getUniformDiffuseLight(glProgram: GLProgram, uniformName: String) : UniformDiffuseLight {
        return UniformDiffuseLight(
            program = glProgram,
            direction = glProgram.getUniformLocation("$uniformName.$diffuseLightDir"),
            color = glProgram.getUniformLocation("$uniformName.$diffuseLightColor"),
        )
    }

    fun getUniformFog(glProgram: GLProgram, uniformName: String) : UniformFog {
        return UniformFog(
            program = glProgram,
            near = glProgram.getUniformLocation("$uniformName.$fogNear"),
            far = glProgram.getUniformLocation("$uniformName.$fogFar"),
            color = glProgram.getUniformLocation("$uniformName.$fogColor"),
        )
    }

    const val version = "#version 300 es"

    const val position = "position"
    const val color = "color"
    const val range = "range"
    const val attenuation = "attenuation"

    const val diffuseLightDir = "diffuseLightDir"
    const val diffuseLightColor = "diffuseLightColor"

    const val fogNear = "near";
    const val fogFar = "far"
    const val fogColor = "color";

    const val pointLightStruct =
"""struct PointLight
{
  vec3 $position;
  vec4 $color;
  float $range;
  vec3 $attenuation;
};
uniform PointLight pointLights[4];"""

    const val pointLightCalcFn =
"""vec4 pointLightCalc(in vec4 worldPos, in vec3 transformedNormal, in vec4 vertexColor, in PointLight pl) {
    float plDistance = distance(worldPos.xyz, pl.position);

    float constAttenuation = pl.$attenuation.x;
    float linearAttenuation = plDistance*pl.$attenuation.y;
    float quadraticAttenuation = plDistance*plDistance*pl.$attenuation.z;
    float plDistanceFactor = (plDistance > pl.range) ? 0.0 : 1.0 / (constAttenuation + linearAttenuation + quadraticAttenuation);
    
    vec3 plDirection = normalize(pl.position - worldPos.xyz);
    float plNormalFactor = dot(transformedNormal, plDirection);
    return clamp(vertexColor * plNormalFactor * plDistanceFactor * pl.color, 0.0, 1.0);
}"""

    const val diffuseLightStruct =
"""struct DiffuseLight
{
  vec3 diffuseLightDir;
  vec4 diffuseLightColor;
};
uniform DiffuseLight diffuseLights[2];"""

    const val diffuseLightCalcFn =
"""vec4 diffuseLightCalc(in vec3 transformedNormal, in vec4 vertexColor, in DiffuseLight diffuseLight) {
    float diffuseFactor = clamp(dot(transformedNormal, diffuseLight.$diffuseLightDir), 0.0, 1.0);
    return vertexColor * diffuseFactor * diffuseLight.$diffuseLightColor;
}"""

    const val fogStruct =
"""struct Fog
{
  float $fogNear;
  float $fogFar;
  vec4 $fogColor;  
};
uniform Fog fog;"""

    const val fogCalcFn =
"""vec4 fogCalc(in vec3 cameraSpacePosition, in vec4 baseColor) {
    float distance = length(cameraSpacePosition);

    float fogFactor = (fog.$fogFar - distance) / (fog.$fogFar - fog.$fogNear);
    float clampedFogFactor = clamp(fogFactor, 0.0, 1.0);

    vec4 mixed = baseColor * clampedFogFactor + fog.$fogColor * (1.0 - clampedFogFactor);
    return vec4(mixed.rgb, baseColor.a);
}"""

}