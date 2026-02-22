package xim.poc.audio

import xim.math.Vector3f
import xim.poc.*
import xim.poc.browser.DatLoader
import xim.poc.browser.ParserContext
import xim.poc.browser.SoundPlayer
import xim.poc.browser.VolumeSettings
import xim.poc.camera.CameraReference
import xim.poc.game.GameState
import xim.resource.DatId
import xim.resource.DirectoryResource
import xim.resource.SoundPointerResource
import xim.resource.TerrainType
import xim.util.Fps
import xim.util.OnceLogger
import xim.util.fallOff
import kotlin.time.Duration.Companion.seconds

data class BackgroundMusicResponse(val musicId: Int?, val resume: Boolean = true)

enum class SystemSound(val soundId: Int, val path: String) {
    CursorMove(1, "sound/win/se/se000/se000001.ogg"),
    MenuSelect(2, "sound/win/se/se000/se000002.ogg"),
    MenuClose(3, "sound/win/se/se000/se000003.ogg"),
    Invalid(4, "sound/win/se/se000/se000004.ogg"),
    TargetCycle(10, "sound/win/se/se000/se000010.ogg"),
    TargetConfirm(11, "sound/win/se/se000/se000011.ogg"),
}

class SoundEffectInstance(val resource: SoundPointerResource, val association: EffectAssociation, val player: SoundPlayer, private val positionFn: () -> Vector3f?, private val volumeFn: (Vector3f?) -> Float? = { null }) {

    var age = 0f

    fun play() {
        update(0f)
        player.play()
    }

    fun stop() {
        player.stop()
    }

    fun applyFade(fadeParameters: FadeParameters) {
        player.applyFade(fadeParameters)
    }

    fun update(elapsedFrames: Float) {
        age += elapsedFrames
        player.update(elapsedFrames)

        val position = positionFn.invoke()

        val baseVolume = volumeFn.invoke(position) ?: computeDefaultVolume(position)
        player.adjustVolume(baseVolume)

        val pan = computePan()
        player.adjustPan(pan)
    }

    fun isComplete() : Boolean {
        return player.isEnded()
    }

    private fun computeDefaultVolume(position: Vector3f?): Float {
        val distance = if (position != null) {
            Vector3f.distance(position, AudioManager.getListenerPosition())
        } else if (association is WeatherAssociation) {
            return AudioManager.volumeSettings.ambientVolume
        } else {
            0f
        }

        return AudioManager.volumeSettings.effectVolume * distance.fallOff(3f, 8f)
    }

    private fun computePan(): Float {
        val position = positionFn.invoke() ?: return 0f

        val camera = CameraReference.getInstance()
        val cameraSpacePosition = camera.getViewMatrix().transform(position)

        return (cameraSpacePosition.x / 5f).coerceIn(-1f, 1f)
    }

}

object AudioManager {

    private val dedudpeIds = setOf(
        7088,   // default [spop]
        7089,   // default [sdep]
        16163,  // trust [spop]
        16164,  // trust [sdep]
        32084,  // Leviathan's Tidal Wave
    )

    val soundEffects = ArrayList<SoundEffectInstance>()

    var volumeSettings = VolumeSettings()

    fun update(elapsedFrames: Float) {
        updateBgm()
        BgmManager.update(elapsedFrames)
        soundEffects.removeAll { it.isComplete() }
        soundEffects.forEach { it.update(elapsedFrames) }
    }

    fun playFootEffect(actor: Actor, actorAssociation: ActorAssociation, terrainType: TerrainType, soundDir: DirectoryResource) {
        val movementInfo = actor.actorModel?.getFootInfoDefinition() ?: return

        val soundEffectId = DatId("0${terrainType.index}${movementInfo.movementChar}${movementInfo.shakeFactor+1}")
        val sePointer = soundDir.getNullableChildAs(soundEffectId, SoundPointerResource::class)

        if (sePointer == null) {
            OnceLogger.warn("[SoundEffect] Couldn't find $soundEffectId")
            return
        }

        OnceLogger.info("[SoundEffect] Playing foot-sound ${sePointer.id}")
        playSoundEffect(sePointer, actorAssociation, highPriority = true, positionFn = { actor.displayPosition })
    }

    fun playSoundEffect(sePointer: SoundPointerResource, association: EffectAssociation, looping: Boolean = false, highPriority: Boolean = false, positionFn: () -> Vector3f? = { null }, volumeFn: (Vector3f?) -> Float? = { null }): SoundEffectInstance? {
        if (!highPriority && !looping && soundEffects.size > 32) { return null }
        if (shouldDedupeSound(sePointer)) { return null }

        val position = positionFn.invoke()

        val volume = volumeFn.invoke(position)
        if (!looping && volume != null && volume <= 0f) {
            return null
        }

        if (!looping && volume == null && position != null) {
            val listenerPos = getListenerPosition()
            if (Vector3f.distance(position, listenerPos) > 25f) { return null }
        }

        val resourceName = toSoundFileName(sePointer)
        val player = SoundPlayer(resourceName, volumeSettings.effectVolume, id = sePointer.soundId, loop = looping, timeSensitive = association !is WeatherAssociation)
        val instance = SoundEffectInstance(sePointer, association, player, positionFn, volumeFn)
        instance.play()

        soundEffects.add(instance)
        return instance
    }

    fun getListenerPosition(): Vector3f {
        return CameraReference.getInstance().getFovAdjustedPosition()
    }

    fun playSystemSoundEffect(systemSound: SystemSound) {
        SoundPlayer(systemSound.path, volumeSettings.systemSoundVolume, id = systemSound.soundId, loop = false).play()
    }

    fun preloadSoundEffect(soundPointerResource: SoundPointerResource) {
        DatLoader.load(toSoundFileName(soundPointerResource), parserContext = ParserContext.optionalResource)
    }

    fun preloadSystemSoundEffects() {
        SystemSound.values().forEach { DatLoader.load(it.path) }
    }

    private fun updateBgm() {
        val bgm = GameState.getGameMode().getCurrentBackgroundMusic()
        playBgm(bgm.musicId, bgm.resume)
    }

    private fun playBgm(musicId: Int?, resume: Boolean = true) {
        BgmManager.playBgm(musicId, volumeSettings.backgroundMusicVolume, resume)
    }

    fun adjustZoneMusicVolume(volume: Double) {
        volumeSettings.backgroundMusicVolume = volume.toFloat()
        BgmManager.adjustVolume(volume.toFloat())
    }

    fun adjustAmbientVolume(volume: Double) {
        volumeSettings.ambientVolume = volume.toFloat()
    }

    fun adjustSystemVolume(volume: Double) {
        volumeSettings.systemSoundVolume = volume.toFloat()
    }

    fun adjustEffectVolume(volume: Double) {
        volumeSettings.effectVolume = volume.toFloat()
    }

    private fun shouldDedupeSound(sePointer: SoundPointerResource): Boolean {
        if (!dedudpeIds.contains(sePointer.soundId)) { return false }

        val newestMatch = soundEffects.filter { it.resource.soundId == sePointer.soundId }
            .minByOrNull { it.age } ?: return false

        return Fps.framesToSeconds(newestMatch.age) < 1.seconds
    }

    private fun toSoundFileName(sePointer: SoundPointerResource): String {
        return "sound/win/se/se${sePointer.folderId}/se${sePointer.fileId}.ogg"
    }

}