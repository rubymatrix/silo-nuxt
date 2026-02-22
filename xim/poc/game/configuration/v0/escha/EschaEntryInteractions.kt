package xim.poc.game.configuration.v0.escha

import xim.poc.ActorAssociation
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.poc.game.QueryMenuOption
import xim.poc.game.QueryMenuResponse
import xim.poc.game.UiStateHelper
import xim.poc.game.configuration.*
import xim.poc.game.configuration.v0.GameTower
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.game.configuration.v0.ZoneLogic
import xim.poc.gl.ByteColor
import xim.poc.tools.ZoneConfig
import xim.resource.table.ZoneNameTable
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

private fun makeScript(zoneConfiguration: ZoneConfig, zoneLogicFn: (() -> ZoneLogic)?): EventScript {
    return EventScript(
        listOf(
            ActorRoutineEventItem(fileTableIndex = 0x1149E, actorId = ActorStateManager.playerId, eagerlyComplete = true),
            WaitRoutine(Fps.secondsToFrames(1.9f)),
            FadeOutEvent(1.seconds),
            WarpZoneEventItem(zoneConfiguration, fade = false),
            RunOnceEventItem {
                zoneLogicFn?.let { GameV0.setZoneLogic(it) }
                ActorManager.player().renderState.effectColor = ByteColor.zero
            },
            WaitRoutine(Fps.secondsToFrames(0.2f)),
            ActorRoutineEventItem(fileTableIndex = 0x1149F, actorId = ActorStateManager.playerId, eagerlyComplete = true),
            FadeInEvent(1.seconds),
        )
    )
}

object EschaEntranceInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        UiStateHelper.openQueryMode("Where will you go?", getOptions(), callback = this::handleQueryResponse)
    }

    private fun getOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Nowhere.", value = -1)

        val configurations = EschaConfigurations.getAll()
            .filter { GameTower.hasClearedFloor(it.value.requiredFloorClear) }

        for ((index, configuration) in configurations) {
            options += QueryMenuOption(ZoneNameTable.first(configuration.startingPosition), index)
        }

        return options
    }

    private fun handleQueryResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null || response.value < 0) { return QueryMenuResponse.pop }

        val zoneConfiguration = EschaConfigurations[response.value]

        val script = makeScript(zoneConfiguration.startingPosition) { EschaZoneInstance(zoneConfiguration)  }
        EventScriptRunner.runScript(script)

        return QueryMenuResponse.popAll
    }

}

object EschaExitInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        UiStateHelper.openQueryMode("What will you do?", getOptions(), callback = this::handleQueryResponse)
    }

    private fun getOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Do not use.", value = -1)
        options += QueryMenuOption("Return to base camp.", value = 1)
        return options
    }

    private fun handleQueryResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null || response.value < 0) { return QueryMenuResponse.pop }

        if (response.value == 1) {
            enterCamp()
        }

        return QueryMenuResponse.pop
    }

    private fun enterCamp() {
        val startingPosition = GameV0.configuration.startingZoneConfig
        val script = makeScript(startingPosition, zoneLogicFn = null)
        EventScriptRunner.runScript(script)
    }

}