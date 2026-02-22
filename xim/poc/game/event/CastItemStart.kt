package xim.poc.game.event

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.getInventory
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.ShiftJis
import xim.resource.AoeType

class CastItemStart(
    val sourceId: ActorId,
    val targetId: ActorId,
    val internalItemId: InternalItemId,
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()
        if (!actorState.isIdleOrEngaged()) { return emptyList() }

        val targetState = ActorStateManager[targetId] ?: return emptyList()

        val item = actorState.getInventory().getByInternalId(internalItemId) ?: return emptyList()
        val skill = item.skill() ?: return emptyList()

        if (!GameEngine.canBeginSkillOnTarget(actorState, targetState, skill)) { return emptyList() }

        val gameMode = GameState.getGameMode()
        val castTime = gameMode.getSkillCastTime(actorState, skill)
        val lockTime = gameMode.getSkillActionLockTime(actorState, skill)
        val rangeInfo = gameMode.getSkillRangeInfo(actorState, skill)
        val movementLockTime = gameMode.getSkillMovementLock(actorState, skill)

        val targetAoeCenter = if (rangeInfo.type == AoeType.Target && !rangeInfo.tracksTarget) { Vector3f(targetState.position) } else { null }
        val context = CastingStateContext(targetAoeCenter = targetAoeCenter, movementLockOverride = movementLockTime, itemId = internalItemId)

        val castingState = CastingState(castTime = castTime, sourceId = sourceId, targetId = targetId, skill = skill, lockTime = lockTime, context = context)
        actorState.initiateAction(castingState)

        val inventoryItemInfo = item.info()
        ChatLog("${actorState.name} uses a ${ShiftJis.colorItem}${inventoryItemInfo.logName}${ShiftJis.colorClear}.", ChatLogColor.Action)

        return actorState.behaviorController.onSkillBeginCharging(castingState)
    }

}
