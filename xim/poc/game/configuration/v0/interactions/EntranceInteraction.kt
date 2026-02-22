package xim.poc.game.configuration.v0.interactions

import xim.poc.ActorAssociation
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.poc.game.QueryMenuOption
import xim.poc.game.QueryMenuResponse
import xim.poc.game.UiStateHelper
import xim.poc.game.configuration.*
import xim.poc.game.configuration.v0.GameTower
import xim.poc.game.configuration.v0.GameTower.canEnterTower2
import xim.poc.game.configuration.v0.GameTower.canEnterTower3
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.abyssea.AbysseaConfigurations
import xim.poc.game.configuration.v0.constants.mobAziDahaka_288_050
import xim.poc.game.configuration.v0.escha.EschaConfigurations
import xim.poc.game.configuration.v0.escha.EschaZiTah
import xim.poc.game.configuration.v0.tower.TowerConfiguration
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.ShiftJis
import xim.resource.table.ZoneNameTable
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

object EntranceInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        UiStateHelper.openQueryMode("What will you do?", getOptions(), callback = this::handleQueryResponse)
    }

    private fun getOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Do not use.", value = -1)
        options += QueryMenuOption("Tower - 1", value = 1)

        if (GameTower.hasClearedFloor(30)) {
            val color = if (canEnterTower2()) { ShiftJis.colorClear } else { ShiftJis.colorGrey }
            options += QueryMenuOption("${color}Tower - 2", value = 2)
        }

        if (GameTower.hasClearedFloor(50)) {
            val color = if (canEnterTower3()) { ShiftJis.colorClear } else { ShiftJis.colorGrey }
            options += QueryMenuOption("${color}Tower - 3", value = 3)
        }

        return options
    }

    private fun handleQueryResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null || response.value < 0) { return QueryMenuResponse.pop }

        if (response.value == 2 && !canEnterTower2()) {
            val requiredAbyssea = AbysseaConfigurations[1]
            val monsterName = MonsterDefinitions[requiredAbyssea.questMonster].name
            val zoneName = ZoneNameTable.first(requiredAbyssea.startingPosition)
            ChatLog("Defeat [$monsterName] in [$zoneName] to proceed.", ChatLogColor.Error)
            return QueryMenuResponse.noop
        }

        if (response.value == 3) {
            if (!canEnterTower3()) {
                val monsterName = MonsterDefinitions[mobAziDahaka_288_050].name
                val zoneName = ZoneNameTable.first(EschaConfigurations[EschaZiTah].startingPosition)
                ChatLog("Defeat [$monsterName] in [$zoneName] to proceed.", ChatLogColor.Error)
            }
            ChatLog("=== Tower 3 is not yet implemented ===", ChatLogColor.SystemMessage)
            return QueryMenuResponse.noop
        }

        val minMaxFloor = when (response.value) {
            1 -> (1 to 30)
            2 -> (31 to 50)
            else -> return QueryMenuResponse.popAll
        }

        val options = ArrayList<QueryMenuOption>()

        val floors = TowerConfiguration.getAll().entries.sortedBy { it.key }
            .filter { it.value.floorNumber >= minMaxFloor.first && it.value.floorNumber <= minMaxFloor.second }
            .filter { GameTower.hasClearedFloor(it.value.floorNumber - 1) }

        for ((_, floor) in floors) {
            val bonusColor = if (GameTower.hasClearedFloor(floor.floorNumber)) { ShiftJis.colorGold } else { "" }
            val optionBonusText = if (floor.bonusText != null) {
                " ${ShiftJis.leftRoundedBracket}${bonusColor}${floor.bonusText}${ShiftJis.colorClear}${ShiftJis.rightRoundedBracket}"
            } else {
                ""
            }
            options += QueryMenuOption("${floor.floorDisplayName}${optionBonusText}", floor.floorNumber)
        }

        UiStateHelper.openQueryMode("Which floor?", options, callback = { enterBattle(it) })
        return QueryMenuResponse.noop
    }

    private fun enterBattle(queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null) { return QueryMenuResponse.pop }

        val floor = TowerConfiguration[queryMenuOption.value]
        val battleLocation = floor.battleLocation

        val script = EventScript(
            listOf(
                ActorRoutineEventItem(fileTableIndex = 0x113c8, actorId = ActorStateManager.playerId, eagerlyComplete = true),
                WaitRoutine(Fps.secondsToFrames(1.9f)),
                FadeOutEvent(1.seconds),
                WarpZoneEventItem(battleLocation.startingPosition, fade = false),
                RunOnceEventItem {
                    GameTower.resetTowerState(startingFloor = queryMenuOption.value)
                    GameTower.emitFloorNumber()
                    GameV0.setupBattle(floor = queryMenuOption.value)
                },
                WaitRoutine(Fps.secondsToFrames(0.2f)),
                FadeInEvent(1.seconds),
            )
        )

        EventScriptRunner.runScript(script)
        return QueryMenuResponse.popAll
    }

}