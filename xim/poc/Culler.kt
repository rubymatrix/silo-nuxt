package xim.poc

import xim.poc.camera.Camera
import xim.poc.game.ActorState
import xim.resource.SpacePartitioningNode
import xim.resource.ZoneObjId
import xim.resource.ZoneResource

sealed interface CullContext
object ZoneObjectCullContext: CullContext
class ShadowCullContext(val actorState: ActorState): CullContext
object DecalCullContext: CullContext

object Culler {

    fun getZoneObjects(camera: Camera, zoneResource: ZoneResource, cullContext: CullContext, limit: Set<ZoneObjId>) : Set<ZoneObjId> {
        val collected = HashSet<ZoneObjId>()
        check(camera, zoneResource.zoneSpaceTreeRoot, limit, collected)

        // Best effort early filter
        collected.removeAll {
            val obj = zoneResource.zoneObj[it]

            if (obj.skipDuringDecalRendering && (cullContext is ShadowCullContext || cullContext is DecalCullContext)) {
                true
            } else {
                val precomputedBox = obj.getPrecomputedBoundingBox()
                if (precomputedBox == null) { false } else { !camera.isVisible(precomputedBox) }
            }
        }

        return collected
    }

    private fun check(camera: Camera, node: SpacePartitioningNode, limit: Set<ZoneObjId>, collected: HashSet<ZoneObjId>) {
        if (!camera.isVisible(node.boundingBox)) {
            return
        }

        if (node.leafNode) {
            collected += if (limit.isEmpty()) { node.containedObjects } else { node.containedObjects.filter { limit.contains(it) } }
            return
        }

        for (child in node.children) {
            if (child == null) { continue }
            check(camera, child, limit, collected)
        }
    }

}