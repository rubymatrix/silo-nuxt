package xim.poc

import xim.poc.game.*
import xim.resource.TargetFlag

class ActionTargetFilter(val targetFlags: Int, val effectCheck: Boolean = false): TargetFilter {

    companion object {
        fun isPassive(targetState: ActorState): Boolean {
            return targetState.isStaticNpc()
        }

        fun areEnemies(actorState: ActorState, targetState: ActorState): Boolean {
            if (isPassive(targetState)) { return false }
            return (actorState.type == ActorType.Enemy && targetState.type != ActorType.Enemy) || (actorState.type != ActorType.Enemy && targetState.type == ActorType.Enemy)
        }

        fun passesProvokeCheck(sourceState: ActorState, targetState: ActorState): Boolean {
            val provoke = sourceState.getStatusEffect(StatusEffect.Provoke) ?: return true
            return targetState.id == sourceState.id || targetState.id == provoke.sourceId
        }

    }

    override fun isValidTarget(sourceId: ActorId, targetId: ActorId): Boolean {
        return targetFilter(sourceId, targetId)
    }

    fun targetFilter(sourceId: ActorId?, targetId: ActorId?): Boolean {
        val sourceState = ActorStateManager[sourceId] ?: return false
        val targetState = ActorStateManager[targetId] ?: return false
        return targetFilter(sourceState, targetState)
    }

    fun targetFilter(sourceState: ActorState, targetState: ActorState): Boolean {
        if (!targetState.targetable && sourceState.id != targetState.id) {
            return false
        }

        if (TargetFlag.Corpse.match(targetFlags) && !targetState.isDead()) {
            return false
        }

        if (!TargetFlag.Corpse.match(targetFlags) && targetState.isDead()) {
            return false
        }

        if (!effectCheck && !passesProvokeCheck(sourceState, targetState)) {
            return false
        }

        if (TargetFlag.Enemy.match(targetFlags) && areEnemies(sourceState, targetState)) {
            return true
        }

        if (TargetFlag.Self.match(targetFlags) && sourceState.id == targetState.id) {
            return true
        }

        if (TargetFlag.Player.match(targetFlags) && targetState.isPlayer()) {
            return true
        }

        if (TargetFlag.Party.match(targetFlags) && sourceState.id != targetState.id) {
            if (sourceState.isEnemy() && !areEnemies(sourceState, targetState)) { return true }

            val party = PartyManager[sourceState.id]
            if (party.contains(targetState.id)) { return true }

            if (sourceState.pet == targetState.id || targetState.pet == sourceState.id) { return true }
        }

        if (TargetFlag.Ally.match(targetFlags) && !isPassive(targetState) && !areEnemies(sourceState, targetState)) {
            return true
        }

        if (TargetFlag.Npc.match(targetFlags) && targetState.type == ActorType.AllyNpc) {
            return true
        }

        return false
    }

}