package xim.poc.tools

import kotlinx.browser.document
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import xim.poc.ItemModelSlot
import xim.poc.ModelLook
import xim.poc.RaceGenderConfig
import xim.poc.game.ActorStateManager
import xim.poc.game.GameClient

object PlayerCustomizer {

    private var customizeSetup = false

    fun setup() {
        if (customizeSetup) { return }
        customizeSetup = true

        val player = ActorStateManager.player()
        val look = player.getBaseLook()

        val raceGenderSelect = (document.getElementById("RaceGenderSelect") as HTMLSelectElement)
        for (config in RaceGenderConfig.values()) {
            val option = document.createElement("option") as HTMLOptionElement
            option.text = config.name
            option.value = config.index.toString()
            raceGenderSelect.add(option)
        }
        raceGenderSelect.value = look.race?.index.toString()

        val faceSelect = (document.getElementById("FaceSelect") as HTMLSelectElement)
        for (i in 0 until 0x20) {
            val option = document.createElement("option") as HTMLOptionElement
            option.text = i.toString()
            option.value = i.toString()
            faceSelect.add(option)
        }
        faceSelect.value = look.equipment[ItemModelSlot.Face].toString()

        raceGenderSelect.onchange = { updatePlayerActor(raceGenderSelect, faceSelect) }
        faceSelect.onchange = { updatePlayerActor(raceGenderSelect, faceSelect) }
    }

    private fun updatePlayerActor(raceGenderSelect: HTMLSelectElement, faceSelect: HTMLSelectElement) {
        val config = RaceGenderConfig.values().first { it.index == raceGenderSelect.value.toInt() }
        val face = faceSelect.value.toInt()

        val playerState = ActorStateManager.player()
        val modelLook = ModelLook.pc(config, playerState.getCurrentLook().equipment.copy())
        modelLook.equipment[ItemModelSlot.Face] = face
        GameClient.submitUpdateBaseLook(playerState.id, modelLook)
    }

}