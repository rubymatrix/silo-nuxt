package xim.poc.game.configuration.v0

import xim.poc.ActorId
import xim.poc.game.ActorState
import xim.poc.game.ActorStateComponent
import xim.poc.game.ActorStateManager
import xim.poc.game.ComponentUpdateResult

data class ActorEnmity(var totalEnmity: Int = 0): Comparable<ActorEnmity> {
    override fun compareTo(other: ActorEnmity): Int {
        return totalEnmity.compareTo(other.totalEnmity)
    }
}

class ActorEnmityTable: ActorStateComponent {

    private val table = HashMap<ActorId, ActorEnmity>()
    private var highestEnmityTarget: ActorId? = null

    override fun update(actorState: ActorState, elapsedFrames: Float): ComponentUpdateResult {
        if (actorState.isDead()) {
            clear()
            return ComponentUpdateResult(removeComponent = false)
        }

        highestEnmityTarget = null

        val itr = table.entries.iterator()
        var highestEnmity: Pair<ActorId, ActorEnmity>? = null

        while (itr.hasNext()) {
            val (targetId, enmity) = itr.next()

            val target = ActorStateManager[targetId]
            if (target == null || target.isDead()) {
                itr.remove()
                continue
            }

            if (enmity.totalEnmity == 0) {
                itr.remove()
                continue
            }

            val currentHighest = highestEnmity
            if (currentHighest == null || enmity > currentHighest.second) {
                highestEnmity = (targetId to enmity)
            }

            EnmityLookupTable.add(source = actorState.id, target = targetId)
        }

        highestEnmityTarget = highestEnmity?.first
        return ComponentUpdateResult(removeComponent = false)
    }

    fun clear() {
        highestEnmityTarget = null
        table.clear()
    }

    fun syncFrom(other: ActorEnmityTable) {
        table.clear()
        other.table.forEach { (key, value) -> table[key] = value.copy() }
    }

    fun add(target: ActorId, enmity: ActorEnmity) {
        val current = table.getOrPut(target) { ActorEnmity() }
        current.totalEnmity += enmity.totalEnmity
    }

    fun getHighestEnmityTarget(): ActorId? {
        return highestEnmityTarget
    }

    fun hasEnmity(id: ActorId): Boolean {
        return table.containsKey(id)
    }

}

fun ActorState.getEnmityTable(): ActorEnmityTable {
    return getComponentAs(ActorEnmityTable::class)
        ?: throw IllegalStateException("[$name] does not have an enmity table")
}

fun ActorState.getNullableEnmityTable(): ActorEnmityTable? {
    return getComponentAs(ActorEnmityTable::class)
}

fun ActorState.syncEnmity(syncSource: ActorState) {
    getNullableEnmityTable()?.syncFrom(syncSource.getEnmityTable())
}