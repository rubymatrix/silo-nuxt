package xim.poc.game.configuration.v0.abyssea

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.QueryMenuOption
import xim.poc.game.QueryMenuResponse
import xim.poc.game.UiStateHelper
import xim.poc.game.configuration.*
import xim.poc.game.configuration.v0.GameTower
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.resource.table.ZoneNameTable
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

object AbysseaEntranceInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        UiStateHelper.openQueryMode("Where will you go?", getOptions()) { handleQueryResponse(npcId, it) }
    }

    private fun getOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Nowhere.", value = -1)

        val configurations = AbysseaConfigurations.getAll()
            .filter { GameTower.hasClearedFloor(it.value.requiredFloorClear) }

        for ((index, configuration) in configurations) {
            options += QueryMenuOption(ZoneNameTable.first(configuration.startingPosition), index)
        }

        return options
    }

    private fun handleQueryResponse(npcId: ActorId, response: QueryMenuOption?): QueryMenuResponse {
        if (response == null || response.value < 0) { return QueryMenuResponse.pop }

        val zoneConfiguration = AbysseaConfigurations[response.value]
        val maw = ActorStateManager[npcId]

        val script = EventScript(
            listOf(
                ActorRoutineEventItem(fileTableIndex = 0xDE01, actorId = ActorStateManager.playerId, targetId = npcId, eagerlyComplete = true),
                InterpolateEventItem(2.seconds) { maw?.scale = 0.25f + it * 0.75f },
                WaitRoutine(2.seconds),
                FadeOutEvent(1.seconds),
                WarpZoneEventItem(zoneConfiguration.startingPosition, fade = false),
                RunOnceEventItem { GameV0.setZoneLogic { AbysseaZoneInstance(zoneConfiguration) } },
                WaitRoutine(Fps.secondsToFrames(0.2f)),
                FadeInEvent(1.seconds),
            )
        )

        EventScriptRunner.runScript(script)
        return QueryMenuResponse.popAll
    }

}

object AbysseaExitInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        UiStateHelper.openQueryMode("What will you do?", getOptions()) { handleQueryResponse(npcId, it) }
    }

    private fun getOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Do not use.", value = -1)
        options += QueryMenuOption("Return to base camp.", value = 1)
        return options
    }

    private fun handleQueryResponse(npcId: ActorId, response: QueryMenuOption?): QueryMenuResponse {
        if (response?.value != 1) { return QueryMenuResponse.pop }
        val startingPosition = GameV0.configuration.startingZoneConfig

        val script = EventScript(
            listOf(
                ActorRoutineEventItem(fileTableIndex = 0xDE01, actorId = ActorStateManager.playerId, targetId = npcId, eagerlyComplete = true),
                WaitRoutine(4.seconds),
                WarpZoneEventItem(startingPosition),
            )
        )

        EventScriptRunner.runScript(script)
        return QueryMenuResponse.popAll
    }

}