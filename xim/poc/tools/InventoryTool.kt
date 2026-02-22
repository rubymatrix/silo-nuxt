package xim.poc.tools

import kotlinx.browser.document
import kotlinx.dom.clear
import org.w3c.dom.HTMLBRElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import xim.poc.game.ActorStateManager
import xim.poc.game.GameEngine
import xim.poc.game.event.InventoryGainEvent
import xim.resource.InventoryItemInfo
import xim.resource.InventoryItems

object InventoryTool {

    private var setup = false

    private lateinit var input: HTMLInputElement
    private lateinit var suggestions: HTMLDivElement

    fun setup() {
        if (setup) { return }
        setup = true

        input = document.getElementById("ItemInput") as HTMLInputElement
        input.oninput = { onChange() }
        input.value = ""

        suggestions = document.getElementById("ItemSuggestions") as HTMLDivElement
    }

    private fun onChange() {
        suggestions.clear()

        val text = input.value.lowercase()
        if (text.length < 3) {
            return
        }

        val matches = HashSet<InventoryItemInfo>()
        matches += InventoryItems.getAll().filter { it.name.lowercase() == text }
        matches += InventoryItems.getAll().filter { it.name.lowercase().contains(text) }.take((10 - matches.size).coerceAtLeast(0))
        matches.forEach { addButton(it) }
    }

    private fun addButton(inventoryItemInfo: InventoryItemInfo) {
        val button = document.createElement("button") as HTMLButtonElement
        button.onclick = {
            println("Adding: $inventoryItemInfo")
            GameEngine.submitEvent(InventoryGainEvent(ActorStateManager.playerId, inventoryItemInfo.itemId))
        }
        button.innerText = "Add: ${inventoryItemInfo.name} [${inventoryItemInfo.itemId}]"

        suggestions.appendChild(button)

        val br = document.createElement("br") as HTMLBRElement
        suggestions.appendChild(br)
    }

}