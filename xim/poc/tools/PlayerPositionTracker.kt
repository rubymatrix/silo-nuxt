package xim.poc.tools

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.dom.clear
import org.w3c.dom.*
import xim.poc.ActorManager
import xim.poc.Area
import xim.poc.SceneManager
import xim.util.toTruncatedString

object PlayerPositionTracker {

    private val lastAreas = ArrayList<Area>()

    private val details by lazy { document.getElementById("PlayerPositionTracker") as HTMLDetailsElement }

    fun update() {
        if (!details.open) { return }

        (document.getElementById("copyLink") as HTMLButtonElement).onclick = { copyLink() }
        (document.getElementById("copyVec3") as HTMLButtonElement).onclick = { copyVec3() }

        val playerActor = ActorManager.player()
        (document.getElementById("npcPosX") as HTMLInputElement).value = playerActor.displayPosition.x.toString()
        (document.getElementById("npcPosY") as HTMLInputElement).value = playerActor.displayPosition.y.toString()
        (document.getElementById("npcPosZ") as HTMLInputElement).value = playerActor.displayPosition.z.toString()

        updateAreaList()
    }

    private fun copyLink() {
        val clipboard = window.navigator.clipboard
        val scene = SceneManager.getNullableCurrentScene() ?: return

        val areaString = "areaId=${scene.getMainArea().id}"
        val subAreaString = scene.getSubArea()?.let { "subAreaId=${it.id}" }

        val position = ActorManager.player().displayPosition
        val x = "x=${position.x.toTruncatedString(2)}"
        val y = "y=${position.y.toTruncatedString(2)}"
        val z = "z=${position.z.toTruncatedString(2)}"

        val parts = listOfNotNull(areaString, subAreaString, x, y, z)
        clipboard.writeText(window.location.origin + "?" + parts.joinToString("&"))
    }

    private fun copyVec3() {
        val clipboard = window.navigator.clipboard
        val position = ActorManager.player().displayPosition
        clipboard.writeText("Vector3f(x=${position.x.toTruncatedString(2)}f,y=${position.y.toTruncatedString(2)}f,z=${position.z.toTruncatedString(2)}f)")
    }

    private fun updateAreaList() {
        val areas = SceneManager.getCurrentScene().getAreas()
        if (lastAreas == areas) { return }

        lastAreas.clear()
        lastAreas.addAll(areas)

        val zoneRef = document.getElementById("ZoneRef") as HTMLElement
        zoneRef.clear()

        areas.forEach {
            val child = document.createElement("span") as HTMLSpanElement
            child.innerText = "[${it.id}] ${it.resourceId}"
            zoneRef.appendChild(child)
            zoneRef.appendChild(document.createElement("br"))
        }
    }

}