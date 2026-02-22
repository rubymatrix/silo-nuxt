package xim.poc.game.configuration.v0.mining

import xim.poc.ActorAssociation
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.audio.SystemSound
import xim.poc.game.ActorStateManager
import xim.poc.game.QueryMenuOption
import xim.poc.game.QueryMenuResponse
import xim.poc.game.UiStateHelper
import xim.poc.game.configuration.*
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.GameV0SaveStateHelper
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.ui.ShiftJis
import xim.resource.table.ZoneNameTable
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

object MineEntranceInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        UiStateHelper.openQueryMode("Where will you go?", getOptions(), callback = this::handleQueryResponse)
    }

    private fun getOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Nowhere.", value = -1)

        val configurations = MiningZoneConfigurations.zones

        for (i in configurations.indices) {
            val configuration = configurations[i]

            val color = if (canEnter(configuration)) { "" } else { "${ShiftJis.colorGrey}" }
            options += QueryMenuOption(color + ZoneNameTable.first(configuration.startingPosition) + " - [M. Level:${configuration.requiredMiningLevel}]", i)
        }

        return options
    }

    private fun handleQueryResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null || response.value < 0) { return QueryMenuResponse.pop }

        val miningConfiguration = MiningZoneConfigurations.zones.getOrNull(response.value) ?: return QueryMenuResponse.pop
        if (!canEnter(miningConfiguration)) {
            return QueryMenuResponse.noop(SystemSound.Invalid)
        }

        val script = EventScript(
            listOf(
                ActorRoutineEventItem(fileTableIndex = 0x113c8, actorId = ActorStateManager.playerId, eagerlyComplete = true),
                WaitRoutine(Fps.secondsToFrames(1.9f)),
                FadeOutEvent(1.seconds),
                WarpZoneEventItem(miningConfiguration.startingPosition, fade = false),
                RunOnceEventItem { GameV0.setZoneLogic { MiningZoneInstance(miningConfiguration) } },
                WaitRoutine(Fps.secondsToFrames(0.2f)),
                FadeInEvent(1.seconds),
            )
        )

        EventScriptRunner.runScript(script)
        return QueryMenuResponse.pop
    }

    private fun canEnter(configuration: MiningZoneConfiguration): Boolean {
        return GameV0SaveStateHelper.getState().mining.level >= configuration.requiredMiningLevel
    }

}