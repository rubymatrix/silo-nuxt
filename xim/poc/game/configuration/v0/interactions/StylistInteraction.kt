package xim.poc.game.configuration.v0.interactions

import xim.poc.ActorId
import xim.poc.ItemModelSlot
import xim.poc.ModelLook
import xim.poc.RaceGenderConfig
import xim.poc.game.*

object StylistInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        UiStateHelper.openQueryMode("Change your look?", getMainOptions(), callback = this::handleMainResponse)
    }

    private fun getMainOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Do nothing.", value = -1)
        options += QueryMenuOption("Change race.", value = 0)
        options += QueryMenuOption("Change face.", value = 1)
        return options
    }

    private fun handleMainResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null || response.value < 0) { return QueryMenuResponse.pop }

        if (response.value == 0) {
            UiStateHelper.openQueryMode("Which race?", getRaceOptions(), callback = this::handleRaceResponse)
        } else {
            UiStateHelper.openQueryMode("Which face?", getFaceOptions(), callback = this::handleFaceResponse)
        }

        return QueryMenuResponse.noop
    }

    private fun getRaceOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Close.", value = -1)
        options += QueryMenuOption("Hume M", value = RaceGenderConfig.HumeM.index)
        options += QueryMenuOption("Hume F", value = RaceGenderConfig.HumeF.index)
        options += QueryMenuOption("Elvaan M", value = RaceGenderConfig.ElvaanM.index)
        options += QueryMenuOption("Elvaan F", value = RaceGenderConfig.ElvaanF.index)
        options += QueryMenuOption("Tarutaru M", value = RaceGenderConfig.TaruM.index)
        options += QueryMenuOption("Tarutaru F", value = RaceGenderConfig.TaruF.index)
        options += QueryMenuOption("Mithra", value = RaceGenderConfig.Mithra.index)
        options += QueryMenuOption("Galka", value = RaceGenderConfig.Galka.index)
        return options
    }

    private fun handleRaceResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null || response.value < 0) { return QueryMenuResponse.pop }
        val config = RaceGenderConfig.from(response.value) ?: return QueryMenuResponse.pop

        val playerState = ActorStateManager.player()
        val modelLook = ModelLook.pc(config, playerState.getCurrentLook().equipment.copy())
        GameClient.submitUpdateBaseLook(playerState.id, modelLook)
        return QueryMenuResponse.noop
    }

    private fun getFaceOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Close.", value = -1)
        for (i in 0 .. 15) {
            val index = 1 + i/2
            val letter = if((i % 2) == 0) { "A" } else { "B" }
            options += QueryMenuOption("Face $index$letter", i)
        }

        return options
    }

    private fun handleFaceResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null || response.value < 0) { return QueryMenuResponse.pop }
        val face = response.value

        val playerState = ActorStateManager.player()
        val baseLook = playerState.getBaseLook()
        val race = baseLook.race ?: return QueryMenuResponse.pop

        val modelLook = ModelLook.pc(race, playerState.getCurrentLook().equipment.copy())
        modelLook.equipment[ItemModelSlot.Face] = face
        GameClient.submitUpdateBaseLook(playerState.id, modelLook)
        return QueryMenuResponse.noop
    }

}
