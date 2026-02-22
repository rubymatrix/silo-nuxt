package xim.poc.game

import xim.math.Vector3f
import xim.poc.ActionTargetFilter
import xim.poc.Actor
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.audio.AudioManager
import xim.poc.audio.SystemSound
import xim.poc.tools.ZoneNpcTool
import xim.resource.TargetFlag
import xim.util.Fps

fun interface TargetFilter {
    fun isValidTarget(sourceId: ActorId, targetId: ActorId): Boolean
}

private class TargetStack(var staleness: Float = 0f, val stack: ArrayList<Actor> = ArrayList()) {

    fun clear() {
        stack.clear()
    }

}

object PlayerTargetSelector {

    private val stalenessThreshold = Fps.secondsToFrames(5)

    private val targetStack = TargetStack()
    private val subTargetStack = TargetStack()
    private val tabTargetFilter = TargetFilter { a, b -> tabTargetFilter(a, b) }

    fun updateTarget(elapsedFrames: Float) {
        targetStack.staleness += elapsedFrames
        if (targetStack.staleness > stalenessThreshold) { targetStack.stack.clear() }

        subTargetStack.staleness += elapsedFrames
        if (subTargetStack.staleness > stalenessThreshold) { subTargetStack.stack.clear() }

        val playerActor = ActorManager.player()
        val targetId = playerActor.target

        if (targetId == null) {
            targetStack.stack.clear()
            return
        }

        val targetActor = ActorManager[targetId]
        val visible = ActorManager.getVisibleActors().contains(targetActor)

        if (visible && targetActor != null && steadyStateFilter(playerActor.id, targetActor.id)) {
            return
        }

        GameClient.submitClearTarget(playerActor.id)
        targetStack.stack.clear()
    }

    fun clearTarget() {
        val playerActor = ActorManager.player()
        if (playerActor.isTargetLocked()) { return }

        GameClient.submitClearTarget(playerActor.id)
        targetStack.clear()
    }

    fun targetPartyMember(index: Int) {
        val playerActor = ActorManager.player()
        val party = PartyManager[playerActor]
        val target = party.getStateByIndex(index) ?: return

        val subTargetMode = UiStateHelper.isSubTargetMode()
        if (playerActor.isTargetLocked() && !subTargetMode) { return }

        val pendingFilter = UiStateHelper.getPendingActionTargetFilter()
        if (pendingFilter != null && !pendingFilter.isValidTarget(playerActor.id, target.id)) { return }

        if (subTargetMode) {
            playerActor.subTarget = target.id
            subTargetStack.clear()
        } else {
            GameClient.submitTargetUpdate(playerActor.id, target.id)
            targetStack.clear()
        }

        AudioManager.playSystemSoundEffect(SystemSound.TargetCycle)
    }

    fun targetCycle(playSound: Boolean = true, subTarget: Boolean = false, targetFilter: TargetFilter = tabTargetFilter): Boolean {
        val playerActor = ActorManager.player()
        if (playerActor.isTargetLocked() && !subTarget) { return false }

        val stack = if (subTarget) { subTargetStack } else { targetStack }

        stack.stack.removeAll { !targetFilter.isValidTarget(playerActor.id, it.id) || it.id == playerActor.target }

        if (stack.stack.isEmpty()) {
            refreshTargetStack(stack, targetFilter)
            stack.stack.removeAll { it.id == playerActor.target }
        }

        val nextTarget = stack.stack.removeFirstOrNull() ?: return false

        // Don't reset the stack during active cycling
        stack.staleness = 0f

        if (subTarget) {
            playerActor.subTarget = nextTarget.id
        } else {
            GameClient.submitTargetUpdate(playerActor.id, nextTarget.id)
            ZoneNpcTool.printTargetInfo()
        }

        if (playSound) { AudioManager.playSystemSoundEffect(SystemSound.TargetCycle) }
        return true
    }

    fun onStartSelectingSubTarget(targetFilter: TargetFilter): Actor? {
        val player = ActorManager.player()

        refreshTargetStack(subTargetStack, targetFilter)
        if (subTargetStack.stack.isEmpty()) { return null }

        val target = ActorStateManager[player.target]

        val shouldPreferPlayer = target == null || (target.isEnemy() && targetFilter.isValidTarget(player.id, player.id))
        val preferredTarget = if (shouldPreferPlayer) { player.id } else { target?.id }

        if (preferredTarget != null && targetFilter.isValidTarget(player.id, preferredTarget)) {
            val headList = subTargetStack.stack.takeWhile { it.id != preferredTarget }
            subTargetStack.stack.removeAll(headList.toSet())
        }

        player.subTarget = subTargetStack.stack.removeFirst().id
        return ActorManager[player.subTarget]
    }

    fun updateSubTarget(targetFilter: TargetFilter): Boolean {
        val player = ActorManager.player()

        val currentTarget = ActorManager[player.subTarget]
        if (currentTarget == null || !targetFilter.isValidTarget(player.id, currentTarget.id)) {
            player.subTarget = null
            return false
        }

        return true
    }

    fun onFinishedSelectingSubTarget() {
        subTargetStack.clear()
    }

    private fun refreshTargetStack(stack: TargetStack, targetFilter: TargetFilter) {
        stack.staleness = 0f
        stack.clear()
        stack.stack.addAll(getTargetableActors(targetFilter))
    }

    private fun getTargetableActors(targetFilter: TargetFilter) : MutableList<Actor> {
        val player = ActorManager.player()
        val party = PartyManager[player]

        return ActorManager.getVisibleActors().asSequence()
            .filter { targetFilter.isValidTarget(player.id, it.id) }
            .filter { ActorManager.isVisible(it.id) }
            .sortedBy { angularDistance(player, it) }
            .sortedBy { if (party.contains(it) || it.getState().owner == player.id) { 1 } else { 0 } }
            .toMutableList()
    }

    private fun angularDistance(a: Actor, b: Actor): Float {
        val basicDistance = Vector3f.distance(a.displayPosition, b.displayPosition)
        val facingAngleFactor = 1f + a.state.getFacingAngle(b.state)
        return basicDistance + (facingAngleFactor * facingAngleFactor)
    }

    private fun tabTargetFilter(sourceId: ActorId, targetId: ActorId): Boolean  {
        if (sourceId == targetId) {
            return false
        }

        return steadyStateFilter(sourceId, targetId)
    }

    private fun steadyStateFilter(sourceId: ActorId, targetId: ActorId): Boolean {
        val sourceState = ActorStateManager[sourceId] ?: return false

        val target = ActorManager[targetId] ?: return false
        val targetState = ActorStateManager[targetId] ?: return false

        if (!targetState.targetable) {
            return false
        }

        if (targetState.isDead() && target.isDisplayInvisible()) {
            return false
        }

        if (target.isDisplayedDead() && targetState.type != ActorType.Pc) {
            return false
        }

        if (targetState.type == ActorType.Effect && targetState.name.isBlank()) {
            return false
        }

        if (Vector3f.distance(sourceState.position, targetState.position) > (targetState.maxTargetDistance ?: 50f)) {
            return false
        }

        if (!targetState.isDead() && ActionTargetFilter.areEnemies(sourceState, targetState)) {
            val filter = ActionTargetFilter(TargetFlag.Enemy.flag)
            if (!filter.targetFilter(sourceState, targetState)) { return false }
        }

        return true
    }

    fun tryDirectlyTarget(actor: Actor?): Boolean {
        if (UiStateHelper.hasActiveUi()) { return false }

        val playerActor = ActorManager.player()
        if (playerActor.isTargetLocked()) {
            return false
        }

        if (actor == null) {
            if (playerActor.target != null) {
                GameClient.submitClearTarget(playerActor.id)
                return true
            }
            return false
        }

        if (!steadyStateFilter(playerActor.id, actor.id)) {
            return false
        }

        if (playerActor.target == actor.id) {
            UiStateHelper.interactWithTarget()
            return true
        } else {
            GameClient.submitTargetUpdate(playerActor.id, actor.id)
            targetStack.clear()
        }

        AudioManager.playSystemSoundEffect(SystemSound.TargetCycle)
        return true
    }

    fun tryDirectlyEngage(actor: Actor): Boolean {
        if (UiStateHelper.hasActiveUi()) { return false }

        val playerActor = ActorManager.player()
        if (playerActor.isTargetLocked()) {
            return false
        }

        if (actor.getState().isDead()) {
            return false
        }

        if (!steadyStateFilter(playerActor.id, actor.id)) {
            return false
        }

        GameClient.submitPlayerEngage(actor.id)
        return true
    }

}