package xim.poc.tools

import kotlinx.browser.document
import kotlinx.dom.clear
import org.w3c.dom.*
import xim.resource.table.ZoneNameTable

object ZoneChangeTool {

    private var setup = false

    private lateinit var input: HTMLInputElement
    private lateinit var suggestions: HTMLDivElement

    private lateinit var nameIdMapping: Map<String, Int>

    fun setup() {
        if (setup) { return }
        setup = true

        val customSelect = document.getElementById("CustomZones") as HTMLSelectElement
        val customSelectGo = document.getElementById("CustomZoneGo") as HTMLButtonElement
        customSelectGo.onclick = {
            val selected = CustomZoneConfig.values().first {it.name == customSelect.value}
            ZoneChanger.beginChangeZone(selected.config)
        }

        for (customConfig in CustomZoneConfig.values().toList()) {
            val option = document.createElement("option") as HTMLOptionElement
            option.text = customConfig.name
            option.value = customConfig.name
            customSelect.appendChild(option)
        }

        val zoneSelect = document.getElementById("Zones") as HTMLSelectElement
        val zoneSelectGo = document.getElementById("ZoneGo") as HTMLButtonElement

        zoneSelectGo.onclick = {
            val zoneId = zoneSelect.value.toInt()
            ZoneChanger.beginChangeZone(ZoneConfig(zoneId = zoneId))
        }

        ZoneNameTable.getAllFirst().forEachIndexed { index, name ->
            if (index == 0) { return@forEachIndexed }

            val option = document.createElement("option") as HTMLOptionElement
            option.text = name
            option.value = index.toString()
            zoneSelect.appendChild(option)
        }

        nameIdMapping = ZoneNameTable.getAllFirst()
            .mapIndexed { index, name -> Pair("$name ($index)", index) }
            .associate { it.first to it.second }

        input = document.getElementById("ZoneInput") as HTMLInputElement
        input.oninput = { onChange() }
        input.value = ""

        suggestions = document.getElementById("ZoneSuggestions") as HTMLDivElement
    }

    private fun onChange() {
        suggestions.clear()

        val text = input.value.lowercase()
        if (text.length < 3) {
            return
        }

        nameIdMapping.entries
            .filter { it.key.lowercase().contains(text) }
            .take(10)
            .forEach { addButton(it) }
    }

    private fun addButton(entry: Map.Entry<String, Int>) {
        val button = document.createElement("button") as HTMLButtonElement
        button.onclick = {
            val id = entry.value
            ZoneChanger.beginChangeZone(ZoneConfig(zoneId = id))
        }

        button.innerText = entry.key
        suggestions.appendChild(button)

        val br = document.createElement("br") as HTMLBRElement
        suggestions.appendChild(br)
    }

}