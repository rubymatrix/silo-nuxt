package xim.poc.tools

import kotlinx.browser.document
import kotlinx.dom.clear
import org.w3c.dom.*
import xim.math.Vector3f
import xim.poc.ActorManager
import xim.poc.game.ActorStateManager
import xim.resource.Particle
import xim.resource.ParticleGenerator
import kotlin.random.Random
import kotlin.random.nextULong

data class CheckBoxId(val path: String) {
    companion object { fun from(pg: ParticleGenerator) = CheckBoxId(pg.resource.path()) }
}

private class CheckBoxEntry(val checkbox: HTMLInputElement, var generator: ParticleGenerator)

object ParticleGenTool {

    private val div by lazy { document.getElementById("particleGens") as HTMLDivElement }
    private val enableElement by lazy { document.getElementById("particleToolEnabled") as HTMLInputElement }

    private val particleGenCheckBoxes = HashMap<CheckBoxId, CheckBoxEntry>()

    private var initialized = false
    private var enabled = false

    fun update() {
        enabled = enableElement.checked

        if (!initialized) {
            initialized = true
            (document.getElementById("particleGensOn") as HTMLButtonElement).onclick = { particleGenCheckBoxes.values.forEach { it.checkbox.checked = false} }
            (document.getElementById("particleGensOff") as HTMLButtonElement).onclick = { particleGenCheckBoxes.values.forEach { it.checkbox.checked = true } }
            (document.getElementById("particleGensSort") as HTMLButtonElement).onclick = { sort() }
        }
    }

    fun add(particle: Particle) {
        if (!enabled) { return }

        val checkBoxId = CheckBoxId(particle.creator.resource.path())

        val current = particleGenCheckBoxes[checkBoxId]
        if (current != null) {
            current.generator = particle.creator
            return
        }

        appendChild(checkBoxId, particle)
    }

    fun isDrawingEnabled(particle: Particle): Boolean {
        if (!enabled) { return true }
        return particleGenCheckBoxes[CheckBoxId.from(particle.creator)]?.checkbox?.checked == false
    }

    fun clear() {
        particleGenCheckBoxes.clear()
        div.clear()
    }

    private fun sort() {
        val actorPosition = ActorManager.player().displayPosition

        val particles = particleGenCheckBoxes.values.map { it.generator }
            .mapNotNull { it.getActiveParticles().firstOrNull() }
            .sortedBy { Vector3f.distance(actorPosition, it.getWorldSpacePosition()) }

        clear()
        particles.forEach { appendChild(CheckBoxId.from(it.creator), it) }
    }

    private fun appendChild(checkBoxId: CheckBoxId, particle: Particle) {
        val pgDiv = document.createElement("div") as HTMLDivElement
        div.appendChild(pgDiv)

        val goButton = document.createElement("button") as HTMLButtonElement
        goButton.innerText = "Go"
        goButton.onclick = { ActorStateManager.player().position.copyFrom(particle.config.basePosition) }
        pgDiv.appendChild(goButton)

        if (particle.config.basePosition.magnitudeSquare() == 0f) { goButton.disabled = true }

        val checkBox = document.createElement("input") as HTMLInputElement
        checkBox.type = "checkbox"
        checkBox.id = Random.nextULong().toString()
        pgDiv.appendChild(checkBox)

        val label = document.createElement("label") as HTMLLabelElement
        label.innerText = particle.creator.resource.path()
        label.htmlFor = checkBox.id
        pgDiv.appendChild(label)

        val br = document.createElement("br") as HTMLBRElement
        pgDiv.appendChild(br)

        particleGenCheckBoxes[checkBoxId] = CheckBoxEntry(checkBox, particle.creator)
    }

}