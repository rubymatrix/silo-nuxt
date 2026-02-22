package xim.poc.game.configuration.v0

import xim.poc.game.configuration.v0.abyssea.AbysseaConfigurations
import xim.poc.game.configuration.v0.constants.mobAziDahaka_288_050
import xim.poc.game.configuration.v0.tower.FloorConfiguration
import xim.poc.game.configuration.v0.tower.TowerConfiguration
import xim.poc.game.configuration.v0.tower.TowerState
import xim.poc.ui.ChatLog
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

object GameTower {

    private var towerState = TowerState()

    fun resetTowerState(startingFloor: Int) {
        towerState = TowerState()
        towerState.currentFloor = startingFloor
    }

    fun advanceTowerFloor(): Int {
        towerState.currentFloor += 1
        return towerState.currentFloor
    }

    fun getCurrentFloor(): Int {
        return towerState.currentFloor
    }

    fun getHighestClearedFloor(): Int {
        val state = GameV0SaveStateHelper.getState()
        return state.highestClearedFloor
    }

    fun emitFloorNumber() {
        ChatLog("=== Floor: ${getCurrentFloorDefinition().floorDisplayName} ===")
    }

    fun getCurrentFloorDefinition(): FloorConfiguration {
        return TowerConfiguration[towerState.currentFloor]
    }

    fun getNextFloorDefinition(): FloorConfiguration {
        return TowerConfiguration[towerState.currentFloor + 1]
    }

    fun hasClearedFloor(floorNumber: Int): Boolean {
        return GameV0SaveStateHelper.getState().highestClearedFloor >= floorNumber
    }

    fun onClearedFloor(floorNumber: Int) {
        val state = GameV0SaveStateHelper.getState()

        if (floorNumber > state.highestClearedFloor) { onClearedNewFloor(floorNumber) }
        state.highestClearedFloor = max(state.highestClearedFloor, floorNumber)
    }

    fun canEnterTower2(): Boolean {
        val requiredAbyssea = AbysseaConfigurations[1]
        return GameV0Helpers.hasDefeated(requiredAbyssea.questMonster)
    }

    fun canEnterTower3(): Boolean {
        return GameV0Helpers.hasDefeated(mobAziDahaka_288_050)
    }

    private fun onClearedNewFloor(floorNumber: Int) {
        val notificationFn = TowerConfiguration[floorNumber].firstClearNotification ?: return
        HelpNotifier.notify(notificationFn.invoke(), 10.seconds)
    }

}