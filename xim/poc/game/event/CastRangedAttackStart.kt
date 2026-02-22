package xim.poc.game.event

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.actor.components.getEquipment
import xim.poc.game.actor.components.getRangedAttackItems
import xim.poc.game.configuration.constants.rangedAttack
import xim.resource.AoeType
import xim.resource.EquipSlot

class CastRangedAttackStart(
    val sourceId: ActorId,
    val targetId: ActorId,
) : Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()
        if (!actorState.isIdleOrEngaged()) { return emptyList() }

        if (actorState.type == ActorType.Pc && actorState.getRangedAttackItems() == null) { return emptyList() }

        val targetState = ActorStateManager[targetId] ?: return emptyList()

        if (!GameEngine.canBeginSkillOnTarget(actorState, targetState, rangedAttack)) { return emptyList() }

        val gameMode = GameState.getGameMode()
        val castTime = gameMode.getSkillCastTime(actorState, rangedAttack)
        val lockTime = gameMode.getSkillActionLockTime(actorState, rangedAttack)
        val rangeInfo = gameMode.getSkillRangeInfo(actorState, rangedAttack)
        val movementLockTime = gameMode.getSkillMovementLock(actorState, rangedAttack)

        val targetAoeCenter = if (rangeInfo.type == AoeType.Target && !rangeInfo.tracksTarget) { Vector3f(targetState.position) } else { null }

        val context = CastingStateContext(
            rangedItemId = actorState.getEquipment(EquipSlot.Range)?.internalId,
            ammoItemId = actorState.getEquipment(EquipSlot.Ammo)?.internalId,
            movementLockOverride = movementLockTime,
            targetAoeCenter = targetAoeCenter,
        )

        val castingState = CastingState(castTime = castTime, sourceId = sourceId, targetId = targetId, skill = rangedAttack, lockTime = lockTime, context = context)
        actorState.initiateAction(castingState)

        return actorState.behaviorController.onSkillBeginCharging(castingState)
    }

}