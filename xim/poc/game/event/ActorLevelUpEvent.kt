package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.GameEngine
import xim.poc.game.MiscEffects
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor

class ActorLevelUpEvent(
    val sourceId: ActorId,
    val actionContext: AttackContext? = null,
): Event {

    override fun apply(): List<Event> {
        val state = ActorStateManager[sourceId] ?: return emptyList()
        if (state.isDead()) { return emptyList() }

        state.updateCombatStats(GameEngine.computeCombatStats(sourceId))
        state.setHp(state.getMaxHp())
        state.setMp(state.getMaxMp())

        AttackContext.compose(actionContext) {
            val actor = ActorManager[sourceId]
            ChatLog("${state.name} attains level ${state.getMainJobLevel().level}!", ChatLogColor.Action)
            if (actor != null) { MiscEffects.playEffect(source = actor, effect = MiscEffects.Effect.LevelUp) }
        }

        return emptyList()
    }

}