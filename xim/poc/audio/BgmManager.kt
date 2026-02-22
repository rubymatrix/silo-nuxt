package xim.poc.audio

import xim.poc.FadeParameters
import xim.poc.browser.SoundPlayer
import xim.util.Fps.secondsToFrames


object BgmManager {

    private var current: SoundPlayer? = null
    private val allPlayers = LinkedHashMap<Int, SoundPlayer>()

    fun getCurrent(): String? {
        return current?.resourceName
    }

    fun update(elapsedFrames: Float) {
        for ((_, soundPlayer) in allPlayers) {
            soundPlayer.update(elapsedFrames)
        }
    }

    fun playBgm(musicId: Int?, volume: Float, resume: Boolean) {
        val playing = current

        if (playing != null && playing.id == musicId) {
            return
        }

        if (playing != null) {
            playing.applyFade(FadeParameters.fadeOut(secondsToFrames(1f), removeOnComplete = false))
        }

        current = null
        if (musicId == null) { return }

        val musicIdStr = musicId.toString().padStart(3, '0')
        val resourceName = "sound/win/music/data/music${musicIdStr}.ogg"

        val cachedPlayer = allPlayers[musicId]
        if (!resume) { cachedPlayer?.stop() }

        val player = if (resume && cachedPlayer != null) {
            cachedPlayer
        } else {
            SoundPlayer(resourceName = resourceName, volume = volume, id = musicId, loop = true).also { it.play() }
        }

        allPlayers.remove(musicId)
        allPlayers[musicId] = player
        evictOldestPlayerIfNeeded()

        // Fade-in
        current = player
        current?.applyFade(FadeParameters.fadeIn(secondsToFrames(1f)))
    }

    fun adjustVolume(volume: Float) {
        allPlayers.values.forEach { it.adjustVolume(volume) }
    }

    private fun evictOldestPlayerIfNeeded() {
        if (allPlayers.size < 3) { return }

        val oldestPlayer = allPlayers.entries.firstOrNull() ?: return
        oldestPlayer.value.stop()
        allPlayers.remove(oldestPlayer.key)
    }

}