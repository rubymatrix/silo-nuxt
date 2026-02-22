package xim.poc.game

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.game.event.InitialActorState

class ActorPromise {

    private var actorId: ActorId? = null
    private val callbacks = ArrayList<(ActorState) -> Unit>()

    fun fulfill(actorState: ActorState) {
        if (actorId != null) { return }

        actorId = actorState.id
        callbacks.forEach { it.invoke(actorState) }
        callbacks.clear()
    }

    fun onReady(callback: (ActorState) -> Unit): ActorPromise {
        val current = actorId
        if (current == null) {
            callbacks += callback
            return this
        }

        val state = ActorStateManager[current] ?: return this
        callback.invoke(state)

        return this
    }

    fun ifReady(callback: (ActorState) -> Unit) {
        val current = ActorStateManager[actorId] ?: return
        callback.invoke(current)
    }

    fun getIfReady(): ActorId? {
        return actorId
    }

    fun resolveIfReady(): ActorState? {
        return ActorStateManager[actorId]
    }

    fun isObsolete(): Boolean {
        if (actorId == null) { return false }
        val current = ActorStateManager[actorId] ?: return true
        return current.isDead()
    }

    fun isAlive(): Boolean {
        if (actorId == null) { return false }
        val current = ActorStateManager[actorId] ?: return false
        return !current.isDead()
    }

}

fun ActorPromise?.isAlive(): Boolean {
    return this?.isAlive() ?: false
}

fun ActorPromise?.isNullOrObsolete(): Boolean {
    return this?.isObsolete() ?: true
}

fun ActorPromise?.cleanup() {
    this?.onReady { GameEngine.submitDeleteActor(it.id) }
}


object ActorStateManager {

    private var idCounter = 0
    val playerId = nextId()

    private val actorStates = HashMap<ActorId, ActorState>()

    fun getAll(): Map<ActorId, ActorState> {
        return actorStates
    }

    fun filter(fn: (ActorState) -> Boolean): Collection<ActorState> {
        return actorStates.values.filter { fn.invoke(it) }
    }

    fun any(predicate: (ActorState) -> Boolean): Boolean {
        return actorStates.values.any { predicate.invoke(it) }
    }


    fun getNearbyActors(position: Vector3f, maxDistance: Float): Collection<ActorState> {
        return filter { Vector3f.distance(it.position, position) <= maxDistance }
    }

    fun create(initialActorState: InitialActorState): ActorState {
        val actorId = initialActorState.presetId ?: nextId()
        val state = ActorState(actorId, initialActorState)
        actorStates[actorId] = state
        return state
    }

    fun delete(actorId: ActorId) {
        actorStates.remove(actorId)
    }

    operator fun get(actorId: ActorId?): ActorState? {
        return actorStates[actorId]
    }

    fun clear() {
        val player = player()
        val preserveIds = setOfNotNull(player.id, player.bubble, player.mountedState?.id)
        actorStates.entries.removeAll { !preserveIds.contains(it.key) }
    }

    fun player(): ActorState {
        return this[playerId] ?: throw IllegalStateException("Player wasn't created?")
    }

    fun playerTarget(): ActorState? {
        return get(player().targetState.targetId)
    }

    fun nextId(): ActorId {
        val id = ActorId(idCounter)
        idCounter += 1
        return id
    }

}