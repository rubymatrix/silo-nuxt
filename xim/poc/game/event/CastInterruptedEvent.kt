package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.CastingInterrupted
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor

enum class CastInterruptReason {
    Generic,
    Paralyze,
}

class CastInterruptedEvent(
    val sourceId: ActorId,
    val castInterruptReason: CastInterruptReason = CastInterruptReason.Generic,
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()

        val current = actorState.getCastingState() ?: return emptyList()
        current.result = CastingInterrupted

        current.onExecute()

        when (castInterruptReason) {
            CastInterruptReason.Generic -> ChatLog.addLine("${actorState.name} interrupted!", ChatLogColor.Action)
            CastInterruptReason.Paralyze -> ChatLog.paralyzed(actorState.name)
        }

        return actorState.behaviorController.onSkillInterrupted(current.skill)
    }
}