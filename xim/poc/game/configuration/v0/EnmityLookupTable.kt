package xim.poc.game.configuration.v0

import xim.poc.ActorId


object EnmityLookupTable {

    private var lookupTable = HashMap<ActorId, HashSet<ActorId>>()
    private var previousLookupTable = HashMap<ActorId, HashSet<ActorId>>()

    fun flip() {
        previousLookupTable = lookupTable
        lookupTable = HashMap()
    }

    fun add(source: ActorId, target: ActorId) {
        lookupTable.getOrPut(target) { HashSet() }.add(source)
    }

    operator fun get(actorId: ActorId): Collection<ActorId> {
        return lookupTable[actorId] ?: previousLookupTable[actorId] ?: emptyList()
    }

}