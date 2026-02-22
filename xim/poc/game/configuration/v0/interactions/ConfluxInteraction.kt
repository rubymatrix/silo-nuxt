package xim.poc.game.configuration.v0.interactions

import xim.math.Vector3f
import xim.poc.ActorAssociation
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.poc.game.QueryMenuOption
import xim.poc.game.QueryMenuResponse
import xim.poc.game.UiStateHelper
import xim.poc.game.configuration.*
import xim.resource.table.Npc
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

class ConfluxInteraction(val confluxes: List<Npc>): NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        UiStateHelper.openQueryMode("What will you do?", getOptions(npcId), callback = this::handleQueryResponse)
    }

    private fun getOptions(npcId: ActorId): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Do not use.", value = -1)

        confluxes.forEachIndexed { index, conflux ->
            if (npcId != conflux.actorId) { options += QueryMenuOption("Warp to ${conflux.name}.", value = index) }
        }

        return options
    }

    private fun handleQueryResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null || response.value < 0) { return QueryMenuResponse.pop }

        val playerAssociation = ActorAssociation(ActorManager.player())

        val destination = Vector3f(confluxes[response.value].info.position)
        destination.z -= 1f

        val script = EventScript(listOf(
            ActorRoutineEventItem(fileTableIndex = 0x11372, actorId = ActorStateManager.playerId, eagerlyComplete = true),
            WaitRoutine(Fps.secondsToFrames(1.33f)),
            FadeOutEvent(1.seconds),
            WarpSameZoneEventItem(destination),
            FadeInEvent(1.seconds),
            ActorRoutineEventItem(fileTableIndex = 0x11373, actorId = ActorStateManager.playerId, eagerlyComplete = true),
            WaitRoutine(Fps.secondsToFrames(1.33f)),
        ))

        EventScriptRunner.runScript(script)
        return QueryMenuResponse.pop
    }
}

