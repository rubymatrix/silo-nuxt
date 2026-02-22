package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.GameState
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import kotlin.math.max

class ActorGainExpEvent(
    val actorId: ActorId,
    val expAmount: Int,
    val actionContext: AttackContext? = null
): Event {

    override fun apply(): List<Event> {
        val actor = ActorStateManager[actorId] ?: return emptyList()
        if (expAmount <= 0) { return emptyList() }

        AttackContext.compose(actionContext) {
            if (actor.isPlayer()) { ChatLog.addLine("Gained $expAmount EXP!", ChatLogColor.Info) }
        }

        val levelUp = gainExp(actor, expAmount)
        return if (levelUp) {
            listOf(ActorLevelUpEvent(actorId, actionContext = actionContext))
        } else {
            emptyList()
        }
    }

    private fun gainExp(actorState: ActorState, expAmount: Int): Boolean {
        val current = actorState.jobLevels[actorState.jobState.mainJob] ?: return false
        val beforeLevel = current.level
        val maximumLevel = GameState.getGameMode().getMaximumLevel(actorState)

        current.gainExp(expAmount, maximumLevel)
        val afterLevel = current.level

        return afterLevel > beforeLevel
    }

}