package xim.poc

import xim.resource.DatId
import xim.resource.DatLink
import xim.resource.InfoResource
import xim.resource.MountDefinition

class Mount(val index: Int, val id: ActorId) {

    private val info = DatLink<InfoResource>(DatId.mount)

    fun getInfo(): MountDefinition? {
        val current = info.getIfPresent()
        if (current != null) { return current.mountDefinition }

        val model = ActorManager[id]?.actorModel?.model ?: return null

        if (model is NpcModel) {
            if (!model.resource.isReady()) { return null }
            val mountDef = info.getOrPut { model.resource.getAsResource().getNullableChildRecursivelyAs(it, InfoResource::class) }
            return mountDef?.mountDefinition
        } else {
            return null
        }
    }

    fun getRiderRotation(): Float {
        return getInfo()?.rotation ?: 0f
    }

}