package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.SceneManager
import xim.poc.game.ActorStateManager
import xim.poc.game.GameEngine
import xim.poc.game.UiStateHelper
import xim.poc.game.actor.components.resetRecastStates
import xim.poc.tools.DestinationZoneConfig
import xim.poc.tools.ZoneChanger

class ChangeZoneEvent(
    val sourceId: ActorId,
    val destination: DestinationZoneConfig
): Event {

    override fun apply(): List<Event> {
        val events = ArrayList<Event>()

        val state = ActorStateManager[sourceId] ?: return emptyList()

        events += GameEngine.releaseDependents(state)
        events += BattleDisengageEvent(sourceId)

        val currentZoneConfig = SceneManager.getCurrentScene().config

        if (currentZoneConfig.matches(destination.zoneConfig)) {
            ZoneChanger.onZoneIn()
        } else {
            if (state.isPlayer()) { ActorManager.clear() }
            state.zone = ZoneSettings(
                zoneId = destination.zoneConfig.zoneId,
                subAreaId = null,
                entryId = destination.zoneConfig.entryId,
                mogHouseSetting = destination.zoneConfig.mogHouseSetting,
            )
        }

        if (destination.options.fullyRevive) {
            state.setHp(state.getMaxHp())
            state.setMp(state.getMaxMp())
            state.resetRecastStates()
            ActorManager[sourceId]?.onRevive()
        }

        UiStateHelper.clear()

        return events
    }

}