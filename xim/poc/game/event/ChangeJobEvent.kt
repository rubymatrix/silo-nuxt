package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.poc.game.Job
import xim.poc.game.MiscEffects

class ChangeJobEvent(
    val sourceId: ActorId,
    val mainJobIndex: Int?,
    val subJobIndex: Int?,
): Event {

    override fun apply(): List<Event> {
        val state = ActorStateManager[sourceId] ?: return emptyList()
        if (!state.isIdle()) { return emptyList() }

        var changed = false

        val mainJob = mainJobIndex?.let { Job.byIndex(it) }
        if (mainJob != null && mainJob != state.jobState.mainJob) {
            changed = true
            state.jobState.mainJob = mainJob
        }

        val subJob = subJobIndex?.let { Job.byIndex(it) }
        if (subJob != null && subJob != state.jobState.subJob) {
            changed = true
            state.jobState.subJob = subJob
        }

        if (!changed) { return emptyList() }

        if (mainJob != null || subJob != null) {
            val actor = ActorManager[sourceId]
            val changer = ActorManager[state.targetState.targetId] ?: actor
            MiscEffects.playEffect(source = actor, target = changer, effect = MiscEffects.Effect.ChangeJobs)
        }

        return emptyList()
    }

}