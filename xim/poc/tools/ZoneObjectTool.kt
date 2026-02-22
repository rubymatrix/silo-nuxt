package xim.poc.tools

import kotlinx.browser.document
import kotlinx.dom.clear
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.*
import xim.poc.camera.CameraReference
import xim.poc.game.ActorStateManager
import xim.poc.gl.ByteColor
import xim.poc.gl.Drawer
import xim.resource.*
import xim.util.OnceLogger
import kotlin.math.max

object ZoneObjectTool {

    private var selectedObj: ZoneObject? = null

    fun setup(zoneDat: DirectoryResource) {
        val zoneResource = zoneDat.getSubDirectory(DatId.model).getOnlyChildByType(ZoneResource::class)

        val mySelect = document.getElementById("zoneObjects") as HTMLSelectElement
        mySelect.onchange = { activateZoneObject(mySelect.value.toInt(), zoneResource) }
        mySelect.clear()

        zoneResource.zoneObj.forEachIndexed { index, zoneObject -> appendZoneObject(index, mySelect) }

        mySelect.value = "0"
        activateZoneObject(0, zoneResource)

        setupShipInfo(zoneDat)
    }

    fun drawZoneDebugView(drawer: Drawer, area: Area, zoneResource: ZoneResource, effectLighting: EffectLighting?): Boolean {
        CollisionViewer.drawCollisionObjects()

        if (isCheckBox("collision")) {
            CollisionViewer.drawZoneCollision(zoneResource)
            return true
        }

        if (isCheckBox("qsp")) {
            QspViewer.drawQsp(zoneResource)
            return true
        }

        if (isCheckBox("collMap")) {
            CollisionViewer.drawLocalCollisionMap(zoneResource)
        }

        if (isCheckBox("onlyElement")) {
            val mySelect = document.getElementById("meshNum") as HTMLSelectElement
            val selected = mySelect.value.toInt()
            val meshNum = if (selected >= 0) { selected } else { null }

            val obj = selectedObj ?: return true
            ZoneDrawer.drawZoneObject(area = area, obj = obj, zoneResource = zoneResource, envDrawDistance = Float.MAX_VALUE, camera = CameraReference.getInstance(), meshNum = meshNum, effectLighting = effectLighting, debug = true)
            return true
        }


        if (isCheckBox("interactions")) {
            drawInteractions(zoneResource)
            drawPaths(zoneResource)
            drawRoutes(zoneResource)
        }

        return false
    }

    private fun appendZoneObject(index: Int, select: HTMLSelectElement) {
        val option = document.createElement("option") as HTMLOptionElement
        option.value = index.toString()
        option.text = index.toString()
        select.appendChild(option)
    }

    private fun activateZoneObject(zoneObjectIndex: Int, zoneDat: ZoneResource) {
        val obj = zoneDat.zoneObj[zoneObjectIndex]

        (document.getElementById("zoneObjectName") as HTMLElement).innerText = "${obj.id} [@${obj.fileOffset.toString(0x10)}]"
        selectedObj = obj
    }

    fun disableDrawDistanceForDebug(): Boolean {
        return !isCheckBox("drawDistanceEnabled")
    }

    fun disableFogForDebug(): Boolean {
        return !isCheckBox("fogEnabled")
    }

    fun cullingGroupOverride(): Int? {
        if (!isCheckBox("cullingOverride")) { return null }
        val ele = document.getElementById("cgroup") as HTMLInputElement
        return ele.value.toIntOrNull()
    }

    fun drawActor(actor: Actor): Boolean {
        if (isCheckBox("drawStandardJoint")) { drawStandardJoint(actor) }
        if (isCheckBox("drawBoundingBox")) { drawSkeletonBoundingBox(actor) }
        if (isCheckBox("drawSkeleton")) { drawSkeleton(actor); return true}
        return false
    }

    private fun drawSkeleton(actor: Actor) {
        val skeleton = actor.actorModel?.getSkeleton() ?: return
        val joints = skeleton.joints
        val facingRotation = Matrix4f().rotateYInPlace(actor.displayFacingDir)

        for (i in joints.indices) {
            val joint = joints[i]
            val transform = joint.currentTransform
            val position = actor.displayPosition + transform.transform(Vector3f(0f, 0f, 0f))

            if (joint.parent != null) {
                val parentPosition = actor.displayPosition + facingRotation.transform(joint.parent.currentTransform.transform(Vector3f(0f, 0f, 0f)))
                LineDrawingTool.drawLine(position, parentPosition, 0.001f)
            }
        }
    }

    private fun drawStandardJoint(actor: Actor) {
        val skeleton = actor.actorModel?.getSkeleton() ?: return
        val leftFoot = actor.getWorldSpaceJointPosition(StandardPosition.LeftFoot.referenceIndex)
        SphereDrawingTool.drawSphere(leftFoot, 0.1f, ByteColor.opaqueG)

        val rightFoot = actor.getWorldSpaceJointPosition(StandardPosition.RightFoot.referenceIndex)
        SphereDrawingTool.drawSphere(rightFoot, 0.1f, ByteColor.opaqueR)

        val jointHighlight = (document.getElementById("jointNum") as HTMLInputElement).value.toIntOrNull() ?: 0
//        skeleton.joints.getOrNull(jointHighlight)?.let { SphereDrawingTool.drawSphere(actor.displayPosition + it.currentTransform.getTranslationVector(), 0.1f, ByteColor.opaqueB) }
        actor.getJointPosition(jointHighlight).let { SphereDrawingTool.drawSphere(it, 0.1f, ByteColor.opaqueB) }

        if (actor.isPlayer()) {
            val stdRef = skeleton.getStandardJoint(jointHighlight)
            val joint = skeleton.getJoint(stdRef)

            val jointPos = actor.getWorldSpaceJointPosition(jointHighlight)
            LineDrawingTool.drawLine(start = jointPos, end = jointPos + joint.currentTransform.transform(Vector3f.X, w = 0f).normalize(), color = ByteColor.opaqueR)
            LineDrawingTool.drawLine(start = jointPos, end = jointPos + joint.currentTransform.transform(Vector3f.Y, w = 0f).normalize(), color = ByteColor.opaqueG)
            LineDrawingTool.drawLine(start = jointPos, end = jointPos + joint.currentTransform.transform(Vector3f.Z, w = 0f).normalize(), color = ByteColor.opaqueB)
        }

    }

    private fun drawSkeletonBoundingBox(actor: Actor) {
        val bbIndex = (document.getElementById("bbIndex") as HTMLInputElement).value.toIntOrNull() ?: return
        val bb = actor.getSkeletonBoundingBox(index = bbIndex) ?: return

        val skele = actor.actorModel?.getSkeleton() ?: return
        val rawBb = skele.resource.boundingBoxes[bbIndex]

        val state = ActorStateManager[actor.id] ?: return
        OnceLogger.info("BB Size: ${state.name} -> ${max(rawBb.width(), rawBb.height())}")

        BoxDrawingTool.enqueue(bb, ByteColor(0x00, 0x40, 0x00, 0x40))
    }

    private fun drawInteractions(zoneResource: ZoneResource) {
        val root = zoneResource.rootDirectory()
        val resources = root.collectByTypeRecursive(ZoneInteractionResource::class)

        val player = ActorManager.player()

        val playerBox = BoundingBox.from(center = player.displayPosition, orientation = Vector3f(0f, player.displayFacingDir, 0f), scale = Scene.interactionBoxSize, verticallyCentered = false)
        val command = BoxCommand(playerBox, ByteColor.opaqueR)

        BoxDrawingTool.enqueue(command)

        for (resource in resources) {
            for (interaction in resource.interactions) {

                val color = when {
                    interaction.isZoneLine() -> ByteColor.opaqueG
                    interaction.isFishingArea() -> ByteColor.opaqueB
                    else -> ByteColor.opaqueR
                }.copy(a = 40)

                BoxDrawingTool.enqueue(BoxCommand(interaction.boundingBox, color))
                if (SatCollider.boxBoxOverlap(playerBox, interaction.boundingBox)) { println("Colliding with: $interaction") }
            }
        }
    }

    private fun drawPaths(zoneResource: ZoneResource) {
        val root = zoneResource.rootDirectory()
        val resources = root.collectByTypeRecursive(PathResource::class)
        val player = ActorManager.player()

        for (resource in resources) {
            for (pair in resource.pathDefinition.segments) {
                LineDrawingTool.drawLine(pair.start.position, pair.end.position,
                    startThickness = pair.start.radius,
                    endThickness = pair.end.radius,
                    color = ByteColor(80, 240, 160, 255)
                )
            }

            val vertices = (resource.pathDefinition.segments.map { it.start } + resource.pathDefinition.segments.map { it.end }).distinct()
            vertices.forEach { SphereDrawingTool.drawSphere(it.position, it.radius, ByteColor(80, 240, 160, 255)) }

            val nearest = resource.pathDefinition.nearestPoint(player.displayPosition)
            if (nearest.second > 0) {
                LineDrawingTool.drawLine(player.displayPosition, nearest.first, startThickness = 0.1f, color = ByteColor(240, 160, 80, 255))
            }
        }
    }

    private fun drawRoutes(zoneResource: ZoneResource) {
        val root = zoneResource.rootDirectory()
        val resources = root.collectByTypeRecursive(RouteResource::class)
        val areaTransform = SceneManager.getCurrentScene().getAreaTransform() ?: return

        for (resource in resources) {
            for (pair in resource.route.segments) {
                LineDrawingTool.drawLine(areaTransform.inverseTransform.transform(pair.start), areaTransform.inverseTransform.transform(pair.end),
                    startThickness = 10f,
                    endThickness = 1f,
                    color = ByteColor(160, 120, 160, 255)
                )
            }
        }
    }

    private fun setupShipInfo(zoneDat: DirectoryResource) {
        val routes = zoneDat.collectByTypeRecursive(RouteResource::class)
        val select = document.getElementById("shipRouteSelect") as HTMLSelectElement
        select.clear()

        for (route in routes) {
            val option = document.createElement("option") as HTMLOptionElement
            option.text = route.id.id
            option.value = route.id.id
            select.appendChild(option)
        }

        val shipRouteProgress = document.getElementById("shipRouteProgress") as HTMLInputElement
        val updateProgress = fun() {
            val input = shipRouteProgress.value.toFloatOrNull()
            if (input != null) { SceneManager.getCurrentScene().setShipRouteProgress(input) }
        }
        shipRouteProgress.onchange = {updateProgress.invoke() }
        updateProgress.invoke()

        select.onchange = {
            val id = DatId(select.value)
            SceneManager.getCurrentScene().setShipRoute(id)
            updateProgress.invoke()
        }

    }

}