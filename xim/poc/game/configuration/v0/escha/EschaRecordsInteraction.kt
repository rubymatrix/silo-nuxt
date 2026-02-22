package xim.poc.game.configuration.v0.escha

import xim.poc.game.QueryMenuOption
import xim.poc.game.QueryMenuResponse
import xim.poc.game.UiStateHelper
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.MonsterId
import xim.poc.game.configuration.v0.escha.EschaDifficulty.S5
import xim.poc.game.configuration.v0.escha.EschaVorsealBonusApplier.hasClearedDifficulty
import xim.poc.ui.ChatLog
import xim.poc.ui.ShiftJis

class EschaRecordsUi(val zoneConfiguration: EschaConfiguration) {

    fun push() {
        val options = ArrayList<QueryMenuOption>()

        options += QueryMenuOption("Go back.", -1)

        for ((monsterId, _) in zoneConfiguration.vorsealRewards) {
            val monsterDefinition = MonsterDefinitions[monsterId]
            val defeatDisplay = if (hasClearedDifficulty(monsterId, S5)) {
                "${ShiftJis.colorGold}${ShiftJis.solidStar}${ShiftJis.colorClear}"
            } else {
                "${ShiftJis.emptyStar}"
            }

            val text = "${monsterDefinition.name} ${ShiftJis.leftRoundedBracket}${defeatDisplay}${ShiftJis.rightRoundedBracket}"
            options += QueryMenuOption(text, monsterId.id)
        }

        UiStateHelper.openQueryMode(prompt = "Monsters defeated:", options = options, callback = this::handleResponse)
    }

    private fun handleResponse(queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value < 0) { return QueryMenuResponse.pop }

        val monsterId = MonsterId(queryMenuOption.value)
        val monsterDefinition = MonsterDefinitions[monsterId]
        val rewardDefinitions = zoneConfiguration.vorsealRewards[monsterId] ?: return QueryMenuResponse.noop

        ChatLog("${monsterDefinition.name}:")

        val clearedDifficulties = EschaVorsealBonusApplier.getClearedDifficulties(monsterId)
        for ((difficulty, vorsealId) in rewardDefinitions.vorseals) {
            val color = if (clearedDifficulties.contains(difficulty)) { ShiftJis.colorGold } else { ShiftJis.colorWhite }
            val vorseal = zoneConfiguration.vorsealConfiguration[vorsealId]
            ChatLog("${color}${difficulty.displayName} ${ShiftJis.leftRoundedBracket}${vorseal.displayName}${ShiftJis.rightRoundedBracket}")
        }

        return QueryMenuResponse.noop
    }

}