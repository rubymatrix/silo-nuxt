package xim.poc

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.audio.AudioManager
import xim.poc.audio.SoundEffectInstance
import xim.poc.gl.*
import xim.poc.gl.RenderState
import xim.resource.*
import xim.util.Fps.secondsToFrames
import xim.util.OnceLogger
import xim.util.interpolate
import kotlin.math.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val GameClock = AdjustableClock()

interface Clock {
    fun currentTimeOfDayInSeconds(): Long

    fun getFullDayInterpolation(): Float
}

enum class DayOfWeek(val index: Int) {
    Fire(0),
    Earth(1),
    Water(2),
    Wind(3),
    Ice(4),
    Lightning(5),
    Light(6),
    Dark(7)
}

enum class Weather(val id: String) {
    fine("fine"),
    suny("suny"),
    clod("clod"),
    mist("mist"),
    dryw("dryw"),
    heat("heat"),
    rain("rain"),
    squl("squl"),
    dust("dust"),
    sand("sand"),
    wind("wind"),
    stom("stom"),
    snow("snow"),
    bliz("bliz"),
    thdr("thdr"),
    bolt("bolt"),
    aura("aura"),
    ligt("ligt"),
    fogd("fogd"),
    dark("dark"),
}

enum class MoonPhase(val index: Int) {
    NewMoon(0),
    WaxingCrescent0(1),
    WaxingCrescent1(2),
    FirstQuarterMoon(3),
    WaxingGibbous0(4),
    WaxingGibbous1(5),
    FullMoon(6),
    WaningGibbous0(7),
    WaningGibbous1(8),
    LastQuarterMoon(9),
    WaningCrescent0(10),
    WaningCrescent1(11),
}

class AdjustableClock : Clock {
    companion object {
        private const val secondsIn24Hours: Long = 24 * 60 * 60
        private val framesPerInGameMinute: Double = 2.4 * secondsToFrames(1)
    }

    private var currentMinute: Int = 0

    private var frameCounter: Double = 0.0

    fun currentHour() = currentMinute / 60
    fun currentMinute() = currentMinute % 60

    override fun currentTimeOfDayInSeconds(): Long {
        val seconds = (floor(frameCounter/ framesPerInGameMinute * 60)).roundToInt()
        return (currentMinute * 60 + seconds).toLong()
    }

    override fun getFullDayInterpolation(): Float {
        return currentTimeOfDayInSeconds().toFloat() / secondsIn24Hours.toFloat()
    }

    fun setCurrentHour(hour: Int) {
        currentMinute = (currentMinute % 60) + hour * 60
    }

    fun setCurrentMinute(minute: Int) {
        currentMinute = (currentMinute / 60) * 60 + minute
    }

    fun advanceFrames(elapsedFrames: Double) {
        frameCounter += elapsedFrames
        while (frameCounter > framesPerInGameMinute) {
            currentMinute += 1
            frameCounter -= framesPerInGameMinute

            if (currentMinute >= 60*24) {
                currentMinute = 0
                EnvironmentManager.advanceDayOfWeek()
            }
        }
    }

}

private class InterpolatedEnvSource(val skyBox: SkyBox, val environmentLighting: InterpolatedEnvironmentLighting, val drawDistance: Float, val clearColor: Color) {
    companion object {
        fun interpolate(e0: InterpolatedEnvSource, e1: InterpolatedEnvSource, t: Float) : InterpolatedEnvSource {
            val skyBox = SkyBox.interpolate(e0.skyBox, e1.skyBox, t)
            val lighting = InterpolatedEnvironmentLighting.interpolate(e0.environmentLighting, e1.environmentLighting, t)
            val drawDistance = e0.drawDistance.interpolate(e1.drawDistance, t)
            val color = Color.interpolate(e0.clearColor, e1.clearColor, t)
            return InterpolatedEnvSource(skyBox, lighting, drawDistance, color)
        }

        fun interpolate(e0: EnvironmentResource, e1: EnvironmentResource, t: Float) : InterpolatedEnvSource {
            val i0 = InterpolatedEnvSource(e0.skyBox, InterpolatedEnvironmentLighting(e0.environmentLighting), e0.drawDistance, e0.clearColor)
            val i1 = InterpolatedEnvSource(e1.skyBox, InterpolatedEnvironmentLighting(e1.environmentLighting), e1.drawDistance, e1.clearColor)
            return interpolate(i0, i1, t)
        }
    }

    constructor(skyBox: SkyBox, environmentLighting: EnvironmentLighting, drawDistance: Float, clearColor: Color):
            this(skyBox, InterpolatedEnvironmentLighting(environmentLighting), drawDistance, clearColor)

    fun getClearColor(): Color {
        return if (environmentLighting.indoors) { clearColor } else { Color(skyBox.slices[0].color) }
    }

}

private class WeatherTransition(val latestInterpolation: HashMap<DatId, InterpolatedEnvSource>, val fadeParameters: FadeParameters) {

    fun getDelta(): Float {
        return 1f - fadeParameters.getOpacity()
    }

    fun isComplete(): Boolean {
        return fadeParameters.isComplete()
    }
}

class SkyBoxMesh(skyBoxConfig: SkyBox) {

    private val skyBoxMeshes: ArrayList<MeshBuffer> = ArrayList()
    private val timeOfCreation = GameClock.currentTimeOfDayInSeconds()

    init {
        initializeSkyBoxMeshes(skyBoxConfig)
    }

    fun isExpired() : Boolean {
        val currentTime = GameClock.currentTimeOfDayInSeconds()
        return currentTime < timeOfCreation || (currentTime > timeOfCreation + 60)
    }

    fun discard() {
        skyBoxMeshes.forEach { it.release() }
    }

    fun drawSky(drawer: Drawer) {
        skyBoxMeshes.forEach { drawer.drawXim(DrawXimCommand(meshes = listOf(it))) }
    }

    private fun initializeSkyBoxMeshes(skyBox: SkyBox) {
        if (skyBox.radius <= 0f) { return }

        val rotationY = ArrayList<Matrix4f>()
        val spokes = skyBox.spokes
        if (spokes == 0) {
            return
        }

        val thetaStep = (2 * PI / spokes).toFloat()
        for (i in 0 until spokes) {
            val theta = thetaStep * i
            rotationY.add(Matrix4f().rotateYInPlace(theta))
        }

        val layers = ArrayList<ArrayList<Vector3f>>()
        for (i in 0 until 8) {
            val layer = ArrayList<Vector3f>(spokes)
            layers.add(layer)

            // 0 -> 0; 1 -> 0.5 PI
            val phi = -0.5f * PI.toFloat() * skyBox.slices[i].elevation
            val rotationZ = Matrix4f().rotateZInPlace(phi)

            for (j in 0 until spokes) {
                val position = Vector3f(skyBox.radius, 0f, 0f)
                rotationZ.transformInPlace(position)
                rotationY[j].transformInPlace(position)
                layer.add(position)
            }
        }

        for (i in 0 until 7) {
            val bufferBuilder = GlBufferBuilder((spokes + 1) * 2)

            for (j in 0 until spokes) {
                bufferBuilder.appendColoredPosition(layers[i][j], skyBox.slices[i].color)
                bufferBuilder.appendColoredPosition(layers[i + 1][j], skyBox.slices[i + 1].color)
            }

            bufferBuilder.appendColoredPosition(layers[i][0], skyBox.slices[i].color)
            bufferBuilder.appendColoredPosition(layers[i + 1][0], skyBox.slices[i + 1].color)

            val buffer = bufferBuilder.build()
            skyBoxMeshes.add(MeshBuffer(
                    numVertices = (spokes + 1) * 2,
                    meshType = MeshType.TriStrip,
                    glBuffer = buffer,
                    renderState = RenderState(depthMask = false)
                ))
        }
    }
}


object EnvironmentManager {

    private const val sunMoonDistance = 900f // Measured in E. Saru

    fun getClock(): Clock {
        return GameClock
    }

    val weatherTypes = ArrayList<DatId>()
    var dayOfWeek: DayOfWeek = DayOfWeek.Wind
    var moonPhase: MoonPhase = MoonPhase.FullMoon

    val clearColor = Color()

    private val latestInterpolation = HashMap<DatId, InterpolatedEnvSource>()
    private lateinit var currentWeather: DatId
    private lateinit var currentScene: Scene

    private var currentSoundEffect: SoundEffectInstance? = null
    private var audioTransitioning: FadeParameters? = null

    private var currentSkyBoxMesh: SkyBoxMesh? = null
    private var weatherTransition: WeatherTransition? = null

    fun setScene(scene: Scene) {
        currentScene = scene

        weatherTypes.clear()
        weatherTypes.addAll(getMainWeatherDirectories().keys)

        currentSoundEffect?.player?.stop()
        audioTransitioning = null

        weatherTransition = null

        currentSkyBoxMesh?.discard()
        currentSkyBoxMesh = null

        currentWeather = weatherTypes.firstOrNull { it == DatId.weatherSunny} ?: weatherTypes[0]
        updateWeatherEffects()
    }

    fun update(elapsedFrames: Float) {
        WindFactor.update(elapsedFrames)
        if (weatherTransition?.isComplete() == true) { weatherTransition = null }
        latestInterpolation.clear()

        clearColor.copyFrom(getInterpolatedEnvResource().getClearColor())
    }

    fun updateWeatherAudio(area: Area, actorEnvironmentId: DatId?) {
        val res = getInterpolatedEnvResource(area, actorEnvironmentId)
        updateWeatherAudioEffect(res.environmentLighting.indoors)
    }

    fun getMoonPhase() = moonPhase

    fun getDayOfWeek() = dayOfWeek

    fun getWeather() = currentWeather

    fun setCurrentHour(hour: Int) {
        GameClock.setCurrentHour(hour)
    }

    fun setCurrentMinute(minute: Int) {
        GameClock.setCurrentMinute(minute)
    }

    fun advanceTime(elapsedFrames: Double) {
        GameClock.advanceFrames(elapsedFrames)
    }

    fun advanceDayOfWeek() {
        val nextIndex = (dayOfWeek.index + 1) % DayOfWeek.values().size
        dayOfWeek = DayOfWeek.values()[nextIndex]
    }

    fun getTerrainLighting(area: Area, zoneObject: ZoneObject): LightingParams {
        return getTerrainLighting(area, zoneObject.environmentLink)
    }

    fun getMainAreaTerrainLighting(environmentId: DatId?): LightingParams {
        return getTerrainLighting(currentScene.getMainArea(), environmentId)
    }

    private fun getTerrainLighting(area: Area, environmentId: DatId?): LightingParams {
        val res = getInterpolatedEnvResource(area, environmentId)
        return res.environmentLighting.terrainLighting
    }

    fun getMainAreaModelLighting(environmentId: DatId?): LightingParams {
        return getModelLighting(currentScene.getMainArea(), environmentId)
    }

    fun getModelLighting(area: Area, environmentId: DatId?): LightingParams {
        val res = getInterpolatedEnvResource(area, environmentId)
        return res.environmentLighting.modelLighting
    }

    fun drawSkyBox(drawer: Drawer) {
        val current = currentSkyBoxMesh
        if (current != null && !current.isExpired() && weatherTransition == null) {
            current.drawSky(drawer)
            return
        }

        current?.discard()
        val config = getSkyBoxConfig()
        currentSkyBoxMesh = SkyBoxMesh(config)
        currentSkyBoxMesh?.drawSky(drawer)
    }

    fun getDrawDistance(area: Area?, environmentId: DatId?): Float {
        val res = getInterpolatedEnvResource(area ?: SceneManager.getCurrentScene().getMainArea(), environmentId)
        return res.drawDistance
    }

    fun switchWeather(newWeatherType: DatId, interpolationTime: Duration = 3.33.seconds) {
        if (currentWeather == newWeatherType) {
            return
        }

        if (!weatherTypes.contains(newWeatherType)) {
            return
        }

        val fadeOut = FadeParameters.fadeOut(interpolationTime)
        EffectManager.applyFadeParameter(WeatherAssociation(currentWeather), fadeOut)
        currentWeather = newWeatherType

        updateWeatherEffects()
        EffectManager.applyFadeParameter(WeatherAssociation(newWeatherType), FadeParameters.fadeIn(interpolationTime))

        weatherTransition = WeatherTransition(HashMap(latestInterpolation), fadeOut)
        latestInterpolation.clear()
    }

    fun getMoonPosition(): Vector3f {
        val timeOfDayInSeconds = getClock().currentTimeOfDayInSeconds()
        val anglePerSecond = (0.5f * PI.toFloat()) / (6 * 60 * 60)
        val angle = PI.toFloat() + timeOfDayInSeconds * anglePerSecond
        return Vector3f(sin(angle), cos(angle), 0.0f).normalizeInPlace() * sunMoonDistance
    }

    fun getSunPosition() : Vector3f {
        val timeOfDayInSeconds = getClock().currentTimeOfDayInSeconds()
        val anglePerSecond = (0.5f * PI.toFloat()) / (6 * 60 * 60)
        val angle = timeOfDayInSeconds * anglePerSecond
        return Vector3f(sin(angle), cos(angle), 0.0f).normalizeInPlace() * sunMoonDistance
    }

    private fun getSkyBoxConfig(): SkyBox = getInterpolatedEnvResource().skyBox

    private fun getInterpolatedEnvResource(): InterpolatedEnvSource {
        return getInterpolatedEnvResource(currentScene.getMainArea(), DatId.weather)
    }

    private fun getInterpolatedEnvResource(area: Area, envId: DatId?) : InterpolatedEnvSource {
        return if (envId != null) {
            getInterpolatedEnvResource(area, envId)
        } else {
            getInterpolatedEnvResource()
        }
    }

    private fun getInterpolatedEnvResource(area: Area, environmentId: DatId) : InterpolatedEnvSource {
        val cached = latestInterpolation[environmentId]
        if (cached != null) { return cached }

        val environmentDirectory = getAreaEnvironmentDirectories(area, environmentId)
            ?: getAreaEnvironmentDirectories(currentScene.getMainArea(), environmentId)

        // TODO this was needed for "Silver Knife" & "Garlaige Citadel [S]", where many objects refer to [ev01], but it's not defined...
        if (environmentDirectory == null) {
            if (environmentId != DatId.weather) {
                OnceLogger.error("[$environmentId] Requested environment couldn't be found - defaulting to [${DatId.weather}]")
                return getInterpolatedEnvResource()
            } else {
                throw IllegalStateException("Couldn't resolve any environment configuration")
            }
        }

        // TODO For "Apollyon", the default weather is only "Dark", but [ev01] doesn't have "Dark"...
        var weatherTypeDirectory = environmentDirectory[currentWeather]
        if (weatherTypeDirectory == null) {
            if (environmentId == DatId.weather) {
                throw IllegalStateException("Invalid weather for default environment: $currentWeather")
            } else {
                OnceLogger.error(("Sub-environment [$environmentId] Doesn't have weather [$currentWeather. Arbitrarily selecting..."))
                weatherTypeDirectory = environmentDirectory.values.first()
            }
        }

        val envResourcesByHour = weatherTypeDirectory.collectByType(EnvironmentResource::class)
            .associateBy { it.id.toHourOfDay() }
            .entries.toList()
            .sortedBy { it.key }

        val currentHour = GameClock.currentHour()

        val floorEntry = envResourcesByHour.filter { it.key <= currentHour }.maxByOrNull { it.key } ?: envResourcesByHour.last()
        val ceilEntry = envResourcesByHour.filter { it.key > currentHour }.minByOrNull { it.key } ?: envResourcesByHour.first()

        val interpolatedEnvSource = if (floorEntry.key == ceilEntry.key) {
            InterpolatedEnvSource(floorEntry.value.skyBox, floorEntry.value.environmentLighting, floorEntry.value.drawDistance, floorEntry.value.clearColor)
        } else {
            val v = (GameClock.currentHour() * 60 + GameClock.currentMinute()).toFloat()
            val t0 = (floorEntry.key * 60).toFloat()
            val t1 = (if (ceilEntry.key == 0) { 24 } else { ceilEntry.key } * 60).toFloat()
            val t = (v - t0) / (t1 - t0)

            InterpolatedEnvSource.interpolate(floorEntry.value, ceilEntry.value, t)
        }

        val finalEnvSource = applyWeatherTransition(weatherTransition, interpolatedEnvSource, environmentId)

        latestInterpolation[environmentId] = finalEnvSource
        return finalEnvSource
    }

    private fun applyWeatherTransition(weatherTransition: WeatherTransition?, currentEnv: InterpolatedEnvSource, envId: DatId): InterpolatedEnvSource {
        if (weatherTransition == null) { return currentEnv }
        val previousEnv = weatherTransition.latestInterpolation[envId] ?: return currentEnv
        return InterpolatedEnvSource.interpolate(previousEnv, currentEnv, weatherTransition.getDelta())
    }

    private fun updateWeatherEffects() {
        val weatherTypeDir = getMainWeatherDirectory(currentWeather)
        val skyEffects = weatherTypeDir.collectByTypeRecursive(EffectResource::class)
        skyEffects.forEach { EffectManager.registerEffect(WeatherAssociation(currentWeather), it) }
    }

    private fun updateWeatherAudioEffect(indoors: Boolean) {
        if (audioTransitioning?.isComplete() == false) {
            return
        }

        val weatherTypeDir = getMainWeatherDirectory(currentWeather)

        val soundEffectDir = if (indoors && weatherTypeDir.hasSubDirectory(DatId.indoors)) {
            weatherTypeDir.getSubDirectory(DatId.indoors)
        } else {
            weatherTypeDir
        }

        val soundEffects = soundEffectDir.collectByType(SoundPointerResource::class).filter { it.id.isNumeric() }
        val soundEffectToUse = getTimeOfDayResource(soundEffects)

        val current = currentSoundEffect
        if (current != null && current.resource.fileId == soundEffectToUse?.fileId) {
            return
        }

        current?.applyFade(FadeParameters.defaultFadeOut())

        val fadeIn = FadeParameters.defaultFadeIn()
        audioTransitioning = fadeIn

        if (soundEffectToUse != null) {
            currentSoundEffect = AudioManager.playSoundEffect(soundEffectToUse, WeatherAssociation(currentWeather), looping = true, highPriority = true)
            currentSoundEffect?.applyFade(fadeIn)
        } else {
            currentSoundEffect = null
        }
    }

    private fun <T : DatResource> getTimeOfDayResource(resources: List<T>) : T? {
        val currentTime = (GameClock.currentHour() * 100) + GameClock.currentMinute()

        return resources.filter { it.id.toNumber() <= currentTime }.maxByOrNull { it.id.toNumber() }
            ?: resources.maxByOrNull { it.id.toNumber() }
    }

    private fun getMainWeatherDirectories(): Map<DatId, DirectoryResource> {
        return getAreaEnvironmentDirectories(currentScene.getMainArea(), DatId.weather)
            ?: throw IllegalStateException("Main area weather not defined!")
    }

    private fun getMainWeatherDirectory(weatherType: DatId): DirectoryResource {
        return getMainWeatherDirectories()[weatherType] ?: throw IllegalStateException("[$currentWeather] is not defined")
    }

    private fun getAreaEnvironmentDirectories(area: Area, environmentId: DatId): Map<DatId, DirectoryResource>? {
        val root = area.root.getNullableSubDirectory(environmentId) ?: return null
        val baseWeatherDirs = root.getSubDirectories().associateBy { it.id }

        val customWeatherDirs = currentScene.config.customDefinition?.customWeather?.get(environmentId) ?: return baseWeatherDirs
        return baseWeatherDirs + customWeatherDirs
    }

}

object WindFactor {

    private var targetWind = 1f
    private var currentWind = 0f
    private val stepAmount = 1f / secondsToFrames(2)

    fun getWindFactor() = currentWind.coerceIn(0f, 1f)

    fun update(elapsedFrames: Float) {
        if (currentWind <= 0f) {
            targetWind = 1f
        }

        if (currentWind >= 1f) {
            targetWind = 0f
        }

        currentWind += elapsedFrames * stepAmount * sign(targetWind - currentWind)
    }

}