package xim.poc.tools

import xim.poc.ActorManager
import xim.poc.MainTool
import xim.poc.gl.DrawXimCommand
import xim.resource.CollisionObject
import xim.resource.ZoneResource

object CollisionViewer {

    private val toDraw = HashSet<CollisionObject>()

    fun enqueueCollisionObject(collisionObject: CollisionObject) {
        toDraw += collisionObject
    }

    fun drawCollisionObjects() {
        toDraw.forEach {
            MainTool.drawer.drawXim(DrawXimCommand(meshes = listOf(it.collisionMesh.meshBuffer), modelTransform = it.transformInfo.toWorldSpace))
        }

        toDraw.clear()
    }

    fun drawZoneCollision(zoneDat: ZoneResource) {
        zoneDat.zoneCollisionMeshes.forEach { grouping ->
            grouping.collisionObjects.forEach { enqueueCollisionObject(it) }
        }

        drawCollisionObjects()
    }

    fun drawLocalCollisionMap(zoneDat: ZoneResource) {
        val collisionObjectGroupList = zoneDat.zoneCollisionMap?.getCollisionObjects(ActorManager.player().displayPosition)
        collisionObjectGroupList?.forEach { collisionObjectGroup ->
            collisionObjectGroup.collisionObjects.forEach { enqueueCollisionObject(it) }
        }

        drawCollisionObjects()
    }

}