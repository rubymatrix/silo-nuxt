package xim.poc.tools

import kotlinx.browser.document
import kotlinx.dom.clear
import org.w3c.dom.*
import xim.poc.audio.AudioManager
import xim.poc.audio.BgmManager
import xim.poc.browser.LocalStorage
import xim.poc.browser.VolumeSettings

object AudioVolumeTool {

    private var audioSetup = false
    private val audioDetails by lazy { document.getElementById("audioDetails") as HTMLDetailsElement }

    fun setup() {
        if (audioSetup) { return }
        audioSetup = true

        val volumes = load()

        val bgmSlider = document.getElementById("bgmAudio") as HTMLInputElement
        bgmSlider.value = (volumes.backgroundMusicVolume * 100).toString()
        bgmSlider.onchange = { adjustBgmVolume(bgmSlider) }

        val ambientSlider = document.getElementById("ambientAudio") as HTMLInputElement
        ambientSlider.value = (volumes.ambientVolume * 100).toString()
        ambientSlider.onchange = { adjustAmbientVolume(ambientSlider) }

        val systemSlider = document.getElementById("systemAudio") as HTMLInputElement
        systemSlider.value = (volumes.systemSoundVolume * 100).toString()
        systemSlider.onchange = { adjustSystemVolume(systemSlider) }

        val effectSlider = document.getElementById("effectAudio") as HTMLInputElement
        effectSlider.value = (volumes.effectVolume * 100).toString()
        effectSlider.onchange = { adjustEffectVolume(effectSlider) }
    }

    fun update() {
        if (!audioDetails.open) { return }

        val container = document.getElementById("SoundEffects") as HTMLDivElement
        container.clear()

        val bgmLabel = document.createElement("label") as HTMLLabelElement
        bgmLabel.innerText = "BGM: " + (BgmManager.getCurrent() ?: "None")
        container.appendChild(bgmLabel)

        for (sound in AudioManager.soundEffects) {
            val br = document.createElement("br") as HTMLBRElement
            container.appendChild(br)

            val label = document.createElement("label") as HTMLLabelElement
            label.innerText = "[${sound.resource.path()}] ${sound.player.resourceName} (${sound.player.volume})"
            container.appendChild(label)
        }
    }

    private fun adjustBgmVolume(bgmSlider: HTMLInputElement) {
        AudioManager.adjustZoneMusicVolume(bgmSlider.value.toDouble() / 100.0)
        save()
    }

    private fun adjustAmbientVolume(ambientSlider: HTMLInputElement) {
        AudioManager.adjustAmbientVolume(ambientSlider.value.toDouble() / 100.0)
        save()
    }

    private fun adjustSystemVolume(slider: HTMLInputElement) {
        AudioManager.adjustSystemVolume(slider.value.toDouble() / 100.0)
        save()
    }

    private fun adjustEffectVolume(slider: HTMLInputElement) {
        AudioManager.adjustEffectVolume(slider.value.toDouble() / 100.0)
        save()
    }

    private fun save() {
        LocalStorage.changeConfiguration { it.volumeSettings = AudioManager.volumeSettings.copy() }
    }

    private fun load(): VolumeSettings {
        AudioManager.volumeSettings = LocalStorage.getConfiguration().volumeSettings.copy()
        return AudioManager.volumeSettings
    }

}