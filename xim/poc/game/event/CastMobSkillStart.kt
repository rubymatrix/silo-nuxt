package xim.poc.game.event

import xim.math.Vector3f
import xim.poc.ActionTargetFilter
import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.configuration.constants.SkillId
import xim.poc.ui.AoeIndicatorColor
import xim.poc.ui.AoeIndicators
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.resource.AoeType
import xim.util.Fps

class CastMobSkillStart(
    val sourceId: ActorId,
    val targetId: ActorId,
    val skill: SkillId,
) : Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()
        if (!actorState.isIdleOrEngaged()) { return emptyList() }

        val targetState = ActorStateManager[targetId] ?: return emptyList()

        if (!GameEngine.canBeginSkillOnTarget(actorState, targetState, skill)) { return emptyList() }

        val gameMode = GameState.getGameMode()
        val castTime = gameMode.getSkillCastTime(actorState, skill)
        val lockTime = gameMode.getSkillActionLockTime(actorState, skill)
        val rangeInfo = gameMode.getSkillRangeInfo(actorState, skill)
        val movementLockTime = gameMode.getSkillMovementLock(actorState, skill)

        val targetAoeCenter = if (rangeInfo.type == AoeType.Target && !rangeInfo.tracksTarget) { Vector3f(targetState.position) } else { null }
        val context = CastingStateContext(targetAoeCenter = targetAoeCenter, movementLockOverride = movementLockTime)

        val castingState = CastingState(castTime = castTime, sourceId = sourceId, targetId = targetId, skill = skill, lockTime = lockTime, context = context)
        actorState.initiateAction(castingState)

        val skillName = GameEngine.getSkillLogName(skill)
        if (skillName != null) { ChatLog.addLine("${actorState.name} readies $skillName.", ChatLogColor.Action) }

        val effectedTargetFlags = GameEngine.getSkillEffectedTargetFlags(actorState, skill)
        val playerIsEffected = ActionTargetFilter(effectedTargetFlags, effectCheck = true)
            .targetFilter(actorState, ActorStateManager.player())

        if (playerIsEffected && castTime > Fps.millisToFrames(100) && rangeInfo.type != AoeType.None) {
            val color = if (actorState.isEnemy()) { AoeIndicatorColor.Danger } else { AoeIndicatorColor.Normal }
            AoeIndicators.claim(sourceId, targetId, rangeInfo, color, castingState) { !actorState.isChargingCast() }
        }

        return actorState.behaviorController.onSkillBeginCharging(castingState)
    }

}