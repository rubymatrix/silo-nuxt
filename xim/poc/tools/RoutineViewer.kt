package xim.poc.tools

import kotlinx.browser.document
import kotlinx.dom.clear
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDetailsElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import xim.poc.Area
import xim.poc.EffectManager
import xim.poc.SceneManager
import xim.poc.ZoneAssociation
import xim.resource.EffectRoutineInstance
import xim.resource.EffectRoutineResource
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.emptyList
import kotlin.collections.forEach
import kotlin.collections.set

object RoutineViewer {

    private val details by lazy { document.getElementById("routinesDetail") as HTMLDetailsElement }
    private val container by lazy { document.getElementById("routines") as HTMLDivElement }
    private val items = HashMap<String, HTMLDivElement>()

    private var areas: List<Area> = emptyList()

    fun update() {
        if (!details.open) { return }

        val newAreas = SceneManager.getNullableCurrentScene()?.getAreas() ?: return
        if (areas != newAreas) {
            areas = newAreas
            refresh()
        }

        for (area in areas) {
            val association = ZoneAssociation(area)
            val instances = HashMap<String, EffectRoutineInstance>()
            EffectManager.forEachEffectForAssociation(association) { instances[it.initialRoutine.path()] = it }
            items.forEach { updateInfo(it.value, instances[it.key]) }
        }
    }

    private fun refresh() {
        container.clear()
        items.clear()
        areas.forEach { huh(it) }
    }

    private fun huh(area: Area) {
        val association = ZoneAssociation(area)

        val zoneResource = area.getZoneResource()
        val routines = zoneResource.rootDirectory().collectByTypeRecursive(EffectRoutineResource::class)

        for (routine in routines) {
            val path = "[${area.id.toString(0x10)}] ${routine.path()}"

            val details = document.createElement("details") as HTMLDetailsElement
            container.appendChild(details)

            val summary = document.createElement("summary") as HTMLElement
            summary.innerText = path
            details.appendChild(summary)

            val play = document.createElement("button") as HTMLButtonElement
            play.innerText = "Play"
            details.appendChild(play)
            play.onclick = { playEffect(routine, association) }

            val stop = document.createElement("button") as HTMLButtonElement
            stop.innerText = "Stop"
            details.appendChild(stop)
            stop.onclick = { stopEffect(routine, association) }

            val infoDiv = document.createElement("div") as HTMLDivElement
            details.appendChild(infoDiv)
            items[path] = infoDiv
        }
    }

    private fun playEffect(effectRoutineResource: EffectRoutineResource, zoneAssociation: ZoneAssociation) {
        val path = effectRoutineResource.path()
        var anyMatch = false

        EffectManager.forEachEffectForAssociation(zoneAssociation) {
            if (it.initialRoutine.path() == path) { anyMatch = true }
        }

        if (!anyMatch) {
            EffectManager.registerRoutine(zoneAssociation, effectRoutineResource)
        }
    }

    private fun stopEffect(effectRoutineResource: EffectRoutineResource, zoneAssociation: ZoneAssociation) {
        val path = effectRoutineResource.path()
        EffectManager.removeEffectsForAssociation(zoneAssociation) {
            it.initialRoutine.path() == path
        }
    }

    private fun updateInfo(div: HTMLDivElement, effectRoutineInstance: EffectRoutineInstance?) {
        div.clear()

        if (effectRoutineInstance == null) {
            div.innerText = "No current instance"
            return
        }

        for (sequence in effectRoutineInstance.getSequences()) {
            sequence.scheduledStartTime?.let {
                val nextStartElem = document.createElement("span") as HTMLElement
                val h = it/60/60/60
                val m = (it/60/60%60).toString().padStart(2, '0')
                nextStartElem.innerText = "Scheduled start: ${h}:${m}"
                div.appendChild(nextStartElem)
                div.appendChild(document.createElement("br"))
            }
        }
    }

}