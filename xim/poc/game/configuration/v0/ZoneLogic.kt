package xim.poc.game.configuration.v0

import xim.poc.game.ActorState
import xim.poc.game.AttackContext
import xim.poc.game.configuration.v0.navigation.BattleLocationNavigator
import xim.poc.game.event.Event
import xim.poc.tools.ZoneConfig

interface ZoneLogic {

    fun update(elapsedFrames: Float)

    fun cleanUp()

    fun getCurrentNavigator(): BattleLocationNavigator? = null

    fun getEntryPosition(): ZoneConfig

    fun toNew(): ZoneLogic

    fun onActorDefeated(source: ActorState, target: ActorState, context: AttackContext?): List<Event> = emptyList()

    fun getMusic(): Int? = null

}