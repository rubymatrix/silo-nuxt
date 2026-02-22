package xim.poc.game

import xim.poc.Actor
import xim.poc.ActorId

class Party(leader: ActorId) {

    private val members = mutableListOf(leader)

    fun update(elapsedFrames: Float) {
        members.removeAll { ActorStateManager[it] == null }
    }

    fun addMember(actorId: ActorId) {
        members += actorId
    }

    fun removeMember(actorId: ActorId) {
        members.remove(actorId)
    }

    fun getIndex(actorId: ActorId): Int? {
        val index = members.indexOf(actorId)
        return if (index < 0) { null } else { index }
    }

    fun getStateByIndex(index: Int): ActorState? {
        return ActorStateManager[members.getOrNull(index)]
    }

    fun contains(actor: Actor): Boolean {
        return contains(actor.id)
    }

    fun contains(actorId: ActorId): Boolean {
        return getIndex(actorId) != null
    }

    fun getAll(): List<ActorId> {
        return members
    }

    fun getAllState(): List<ActorState> {
        return members.mapNotNull { ActorStateManager[it] }
    }

    fun size(): Int {
        return members.size
    }

}

object PartyManager {

    private val playerParty by lazy { Party(ActorStateManager.playerId) }

    fun update(elapsedFrames: Float) {
        playerParty.update(elapsedFrames)
    }

    operator fun get(actor: Actor): Party {
        return get(actor.id)
    }

    operator fun get(actorId: ActorId): Party {
        val index = playerParty.getIndex(actorId)
        return if (index == null) { Party(actorId) } else { playerParty }
    }

}