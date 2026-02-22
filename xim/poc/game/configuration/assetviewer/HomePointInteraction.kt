package xim.poc.game.configuration.assetviewer

import xim.poc.ActorContext
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.poc.game.QueryMenuOption
import xim.poc.game.QueryMenuResponse
import xim.poc.game.UiStateHelper
import xim.resource.DatId

object HomePointInteraction {

    fun onInteraction(npcId: ActorId) {
        UiStateHelper.openQueryMode("What will you do?", getOptions(), callback = { handleResult(npcId, it) })
    }

    private fun getOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Set this as your home point.", 0)
        options += QueryMenuOption("On second thought, never mind.", 1)
        return options
    }

    private fun handleResult(npcId: ActorId, queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value != 0) { return QueryMenuResponse.pop }
        UiStateHelper.openQueryMode("Set this as current home point?", getConfirmationOptions(), callback = { handleConfirmation(npcId, it) })
        return QueryMenuResponse.noop
    }

    private fun getConfirmationOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Yes.", 1)
        options += QueryMenuOption("No.", 0)
        return options
    }

    private fun handleConfirmation(npcId: ActorId, queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null) {
            return QueryMenuResponse.popAll
        } else if (queryMenuOption.value <= 0) {
            return QueryMenuResponse.pop
        }

        val actor = ActorManager[npcId]
        actor?.enqueueModelRoutineIfReady(DatId("bind"), actorContext = ActorContext(originalActor = npcId, primaryTargetId = ActorStateManager.playerId))
        return QueryMenuResponse.popAll
    }

}