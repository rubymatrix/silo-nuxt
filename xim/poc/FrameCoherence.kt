package xim.poc

import xim.math.Vector3f
import xim.poc.game.ActorCollision
import xim.resource.ZoneObjId

private class ActorShadowCoherence(val actorId: ActorId) {

    private val referencePosition = Vector3f()
    private var visibleObjects: Map<Area, Set<ZoneObjId>?>? = null

    fun getOrPut(fn: () -> Map<Area, Set<ZoneObjId>?>): Map<Area, Set<ZoneObjId>?> {
        val actor = ActorManager[actorId] ?: return emptyMap()

        val currentAreas = SceneManager.getCurrentScene().getAreas()
        val outdatedAreas = visibleObjects?.keys?.containsAll(currentAreas) == false

        val outdatedPosition = Vector3f.distanceSquared(referencePosition, actor.displayPosition) >= 0.1f

        if (visibleObjects == null || outdatedAreas || outdatedPosition) {
            referencePosition.copyFrom(actor.displayPosition)
            visibleObjects = fn.invoke()
        }

        return visibleObjects!!
    }

}


object FrameCoherence {

    private val shadowCoherence = HashMap<ActorId, ActorShadowCoherence>()
    private val npcCollisionCoherence = HashMap<ActorId, TerrainCollisionResult>()

    fun getActorShadowObjects(actorId: ActorId, resolver: () -> Map<Area, Set<ZoneObjId>?>): Map<Area, Set<ZoneObjId>?> {
        val coherence = shadowCoherence.getOrPut(actorId) { ActorShadowCoherence(actorId) }
        return coherence.getOrPut(resolver)
    }

    fun getNpcCollision(actorId: ActorId, resolver: () -> SceneCollisionResult): SceneCollisionResult {
        val cached = npcCollisionCoherence[actorId]
        if (cached != null) { return cached }

        val sceneCollision = resolver.invoke()
        if (sceneCollision is TerrainCollisionResult) { npcCollisionCoherence[actorId] = sceneCollision }

        return sceneCollision
    }

    fun clear() {
        shadowCoherence.clear()
        clearCachedNpcCollision()
    }

    fun clearCachedNpcCollision() {
        npcCollisionCoherence.clear()
    }

}