package xim.resource

import xim.math.Vector3f
import xim.poc.EnvironmentManager
import xim.poc.gl.ByteColor
import xim.poc.gl.Color
import xim.poc.gl.DiffuseLight
import xim.util.interpolate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class SkyBoxSlice(
    val color: ByteColor,
    val elevation: Float,
) {

    companion object {
        fun interpolate(s0: SkyBoxSlice, s1: SkyBoxSlice, t: Float) : SkyBoxSlice {
            val color = ByteColor.interpolate(s0.color, s1.color, t)
            val elevation = s0.elevation.interpolate(s1.elevation, t)
            return SkyBoxSlice(color, elevation)
        }
    }

}

data class SkyBox(
    val radius: Float,
    val slices: List<SkyBoxSlice>,
    val spokes: Int,
) {

    companion object {
        fun interpolate(a: SkyBox, b: SkyBox, t: Float): SkyBox {
            val spokes = a.spokes.interpolate(b.spokes, t)
            val slices = a.slices.zip(b.slices)
                .map { SkyBoxSlice.interpolate(it.first, it.second, t) }
            val radius = a.radius.interpolate(b.radius, t)

            return SkyBox(radius, slices, spokes)
        }
    }

}

data class LightingParams(
    val ambientColor: Color,
    val fog: FogParams,
    val lights: List<DiffuseLight>
) {
    companion object {
        val noOpLighting = LightingParams(
            ambientColor = Color.NO_MASK,
            fog = FogParams.noOpFog,
            lights = emptyList()
        )

        fun interpolate(lp0: LightingParams, lp1: LightingParams, t: Float): LightingParams {
            val ambient = Color.interpolate(lp0.ambientColor, lp1.ambientColor, t)
            val fog = FogParams.interpolate(lp0.fog, lp1.fog, t)
            val lights = lp0.lights.zip(lp1.lights).map { DiffuseLight.interpolate(it.first, it.second, t) }
            return LightingParams(ambient, fog, lights)
        }

    }
}

data class FogParams(val near: Float, val far: Float, val color: ByteColor) {
    companion object {
        fun interpolate(fog0: FogParams, fog1: FogParams, t: Float): FogParams {
            val near = fog0.near.interpolate(fog1.near, t)
            val far = fog0.far.interpolate(fog1.far, t)
            val color = ByteColor.interpolate(fog0.color, fog1.color, t)
            return FogParams(near, far, color)
        }

        val noOpFog = FogParams(near = 0f, far = -1f, ByteColor.zero)
    }
}

class InterpolatedEnvironmentLighting(
    val modelLighting: LightingParams,
    val terrainLighting: LightingParams,
    val indoors: Boolean
) {

    companion object {
        fun interpolate(ev0: EnvironmentLighting, ev1: EnvironmentLighting, t: Float): InterpolatedEnvironmentLighting {
            val timeOfDayInSeconds = EnvironmentManager.getClock().currentTimeOfDayInSeconds()

            val m0 = ev0.getModelLightingParams(timeOfDayInSeconds)
            val t0 = ev0.getTerrainLightingParams(timeOfDayInSeconds)

            val m1 = ev1.getModelLightingParams(timeOfDayInSeconds)
            val t1 = ev1.getTerrainLightingParams(timeOfDayInSeconds)

            val model = LightingParams.interpolate(m0, m1, t)
            val terrain = LightingParams.interpolate(t0, t1, t)

            return InterpolatedEnvironmentLighting(model, terrain, ev0.indoors)
        }

        fun interpolate(ev0: InterpolatedEnvironmentLighting, ev1: InterpolatedEnvironmentLighting, t: Float): InterpolatedEnvironmentLighting {
            val model = LightingParams.interpolate(ev0.modelLighting, ev1.modelLighting, t)
            val terrain = LightingParams.interpolate(ev0.terrainLighting, ev1.terrainLighting, t)
            return InterpolatedEnvironmentLighting(model, terrain, ev0.indoors)
        }
    }

    constructor(ev: EnvironmentLighting, timeOfDayInSeconds: Long = EnvironmentManager.getClock().currentTimeOfDayInSeconds()):
            this(ev.getModelLightingParams(timeOfDayInSeconds), ev.getTerrainLightingParams(timeOfDayInSeconds), ev.indoors)

}

class EnvironmentLighting(
    val modelLighting: LightConfig,
    val terrainLighting: LightConfig,
    val indoors: Boolean,
) {

    companion object {
        private const val biasThreshold = 0xCC
        private const val biasThresholdF = 0xCC.toFloat() / 0xFF.toFloat()

        private val colorBias = arrayOf(1.4f, 1.36f, 1.45f)
        private val noOpBias = arrayOf(1f, 1f, 1f)
    }

    fun getTerrainLightingParams(timeOfDayInSeconds: Long) : LightingParams {
        return getLightingParams(terrainLighting, timeOfDayInSeconds, modelLighting = false)
    }

    fun getModelLightingParams(timeOfDayInSeconds: Long) : LightingParams {
        return getLightingParams(modelLighting, timeOfDayInSeconds, modelLighting = true)
    }

    private fun getLightingParams(lightConfig: LightConfig, timeOfDayInSeconds: Long, modelLighting: Boolean) : LightingParams {
        if (indoors) {
            val diffuse = DiffuseLight(direction = lightConfig.indoorDiffuseDirection,
                color = diffuseToColor(lightConfig.sunLightColor, lightConfig.diffuseMultiplier),
            )

            return LightingParams(
                ambientColor = ambientToColor(lightConfig.ambientColor),
                fog = lightConfig.getFogParams(),
                lights = listOf(diffuse)
            )
        }

        val sun = DiffuseLight(
            direction = getDirectionOfSunDiffuseLight(timeOfDayInSeconds),
            color = diffuseToColor(lightConfig.sunLightColor, lightConfig.diffuseMultiplier),
        )

        val moon = DiffuseLight(
            direction = getDirectionOfMoonDiffuseLight(timeOfDayInSeconds),
            color = diffuseToColor(lightConfig.moonLightColor, lightConfig.diffuseMultiplier),
        )

        val lights = if (modelLighting) {
            listOf(modelLightMix(sun = sun, moon = moon))
        } else {
            listOf(sun, moon)
        }

        return LightingParams(
            ambientColor = ambientToColor(lightConfig.ambientColor),
            fog = lightConfig.getFogParams(),
            lights = lights
        )
    }

    private fun getDirectionOfSunDiffuseLight(timeOfDayInSeconds: Long) : Vector3f {
        val anglePerSecond = (0.5f * PI.toFloat()) / (6 * 60 * 60)
        val angle = timeOfDayInSeconds * anglePerSecond
        return Vector3f(sin(angle), cos(angle), 0.0f).normalizeInPlace()
    }

    private fun getDirectionOfMoonDiffuseLight(timeOfDayInSeconds: Long) : Vector3f {
        return getDirectionOfSunDiffuseLight(timeOfDayInSeconds) * -1f
    }

    private fun diffuseToColor(byteColor: ByteColor, intensity: Float): Color {
        val diffuse = Color(byteColor).withMultiplied(intensity)

        val applyBias = (diffuse.r() < biasThresholdF) && (diffuse.g() < biasThresholdF) && (diffuse.b() < biasThresholdF)
        val bias = if (applyBias) { colorBias } else { noOpBias }
        for (i in 0 until 3) { diffuse.rgba[i] *= bias[i] }

        if (applyBias) { diffuse.clamp() }
        return diffuse
    }

    private fun ambientToColor(byteColor: ByteColor): Color {
        val applyBias = (byteColor.r < biasThreshold) && (byteColor.g < biasThreshold) && (byteColor.b < biasThreshold)
        val bias = if (applyBias) { colorBias } else { noOpBias }

        val r = bias[0] * byteColor.r / 510f
        val g = bias[1] * byteColor.g / 510f
        val b = bias[2] * byteColor.b / 510f
        val a = byteColor.a / 128f // Unused
        return Color(r, g, b, a).clamp(0.5f)
    }

    private fun modelLightMix(sun: DiffuseLight, moon: DiffuseLight): DiffuseLight {
        val currentSeconds = EnvironmentManager.getClock().currentTimeOfDayInSeconds()
        val currentMinute = currentSeconds / 60

        // In game, models swap from moon to sun @ 06:00; and from sun to moon @ 18:00
        // The game applies a bit of interpolation around these points to smooth the transition
        val t = if (currentMinute < 355) {
            0f
        } else if (currentMinute < 365) {
            (currentMinute.toFloat() - 355f) / (365f - 355f)
        } else if (currentMinute < 1075) {
            1f
        } else if (currentMinute < 1085) {
            (1085f - currentMinute.toFloat()) / (1085f - 1075f)
        } else {
            0f
        }

        return DiffuseLight(Vector3f.lerp(moon.direction, sun.direction, t), Color.interpolate(moon.color, sun.color, t))
    }

}

class LightConfig (
    val sunLightColor: ByteColor,
    val moonLightColor: ByteColor,
    val ambientColor: ByteColor,
    val fogColor: ByteColor,
    val fogEnd: Float,
    val fogStart: Float,
    val diffuseMultiplier: Float,
    val indoorDiffuseDirection: Vector3f = getDirectionOfIndoorDiffuseLight(moonLightColor),
) {

    companion object {
        fun read(byteReader: ByteReader) = LightConfig(
            sunLightColor = byteReader.nextRGBA(),
            moonLightColor = byteReader.nextRGBA(),
            ambientColor = byteReader.nextRGBA(),
            fogColor = byteReader.nextRGBA(),
            fogEnd = byteReader.nextFloat(),
            fogStart = byteReader.nextFloat(),
            diffuseMultiplier = byteReader.nextFloat()
        )

        private fun getDirectionOfIndoorDiffuseLight(moonLightColor: ByteColor): Vector3f {
            val x = moonLightColor.r.toByte().toFloat() / 128f
            val y = moonLightColor.g.toByte().toFloat() / 128f
            val z = moonLightColor.b.toByte().toFloat() / 128f
            return Vector3f(x, y, z).normalizeInPlace() * -1f
        }
    }

    fun getFogParams(): FogParams {
        return FogParams(fogStart, fogEnd, fogColor)
    }

}

class EnvironmentSection(val sectionHeader: SectionHeader) : ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val environmentResource = read(byteReader)
        return ParserResult.from(environmentResource)
    }

    private fun read(byteReader: ByteReader): EnvironmentResource {
        byteReader.offsetFromDataStart(sectionHeader, 0x0)

        val indoorFlag = byteReader.next32()
        if (indoorFlag > 1) { oops(byteReader) }
        val indoors = indoorFlag == 1

        expectZero(byteReader.next32())
        expectZero(byteReader.next32())

        val modelLighting = LightConfig.read(byteReader)
        expectZero(byteReader.next32())

        val terrainLighting = LightConfig.read(byteReader)
        expectZero(byteReader.next32())

        val environmentLighting = EnvironmentLighting(modelLighting = modelLighting, terrainLighting = terrainLighting, indoors = indoors)

        val clearColor = byteReader.nextRGBA()
        byteReader.next32()
        byteReader.next32()
        val drawDistance = byteReader.nextFloat()

        // Sky box related?
        byteReader.next16()
        val sphereSpokeCount = byteReader.next16()

        val unkColor = byteReader.nextRGBA()
        expectZero(byteReader.next32())
        val skyBoxRadius = byteReader.nextFloat()

        // Skybox-def
        val colors = (0 until 8).map { byteReader.nextRGBA() }
        val sizes = (0 until 8).map { byteReader.nextFloat() }
        val slices = colors.zip(sizes).map { SkyBoxSlice(it.first, it.second) }
        val skyBox = SkyBox(skyBoxRadius, slices, sphereSpokeCount)

        expectZero(byteReader.next32())

        return EnvironmentResource(
            id = sectionHeader.sectionId,
            skyBox = skyBox,
            environmentLighting = environmentLighting,
            drawDistance = drawDistance,
            clearColor = Color(clearColor),
        )
    }

}
