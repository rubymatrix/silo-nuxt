package xim.poc.game.configuration.v0.mining

import xim.poc.ActorId
import xim.poc.game.QueryMenuOption
import xim.poc.game.QueryMenuResponse
import xim.poc.game.UiStateHelper
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.ui.ChatLog
import xim.poc.ui.ShiftJis

object MiningHelpInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        val queryOptions = listOf(
            QueryMenuOption("Nothing.", -1),
            QueryMenuOption("Meld Accessories.", 0),
            QueryMenuOption("View 'Prowess' Bonuses.", 1)
        )

        UiStateHelper.openQueryMode(prompt = "What will you do?", options = queryOptions, callback = { handleResponse(npcId, it) })

    }

    private fun handleResponse(npcId: ActorId, queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value < 0) { return QueryMenuResponse.pop }

        when (queryMenuOption.value) {
            0 -> AccessoryGemMeldInteractionUi.push()
            1 -> displayProwessBonuses()
        }

        return QueryMenuResponse.noop(sound = null)
    }


    private fun displayProwessBonuses() {
        val miningInstance = GameV0.getCurrentMiningZoneInstance() ?: return

        ChatLog("Defeat monsters to gain 'Prowess', which greatly improves mining capability.")

        val bonuses = miningInstance.getProwessBonus()
        var hasBonuses = false

        val bonusString = StringBuilder()
        bonusString.appendLine("\nCurrent bonuses:")

        if (bonuses.miningPowerBonus > 1) {
            bonusString.appendLine("${ShiftJis.solidSquare} Gathering power: +${(bonuses.miningPowerBonus-1)*100}%")
            hasBonuses = true
        }

        if (bonuses.attemptBonus > 0) {
            bonusString.appendLine("${ShiftJis.solidSquare} Gathering attempts: +${bonuses.attemptBonus}")
            hasBonuses = true
        }

        if (bonuses.expBonus > 1) {
            bonusString.appendLine("${ShiftJis.solidSquare} Gathering experience: +${(bonuses.expBonus-1)*100}%")
            hasBonuses = true
        }

        if (bonuses.yieldBonus > 0) {
            bonusString.appendLine("${ShiftJis.solidSquare} Gathering yield: +${bonuses.yieldBonus}")
            hasBonuses = true
        }

        if (hasBonuses) {
            ChatLog(bonusString.toString().trimEnd())
        }
    }

}