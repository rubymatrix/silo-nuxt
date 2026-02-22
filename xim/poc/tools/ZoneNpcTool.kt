package xim.poc.tools

import kotlinx.browser.document
import kotlinx.dom.clear
import org.w3c.dom.*
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.SceneManager
import xim.poc.game.ActorStateManager
import xim.resource.table.FileTableManager
import xim.resource.table.Npc
import xim.resource.table.NpcTable

object ZoneNpcTool {

    private val npcFilter by lazy { document.getElementById("npcFilter") as HTMLInputElement }
    private val npcDiv by lazy { document.getElementById("npcDiv") as HTMLDivElement }

    fun setup() {
        npcFilter.oninput = { filter() }

        npcDiv.clear()

        val zoneNpcs = SceneManager.getCurrentScene().getNpcs()
        for (npc in zoneNpcs.npcs) { addNpcDiv(npc) }

        filter()
    }

    fun printTargetInfo() {
        val targetId = ActorManager.player().target ?: return
        val npc = SceneManager.getCurrentScene().getNpcs().npcs.firstOrNull { it.id == targetId.id } ?: return
        println(npc)
    }

    private fun addNpcDiv(npc: Npc) {
        val div = document.createElement("div") as HTMLDivElement
        div.id = "${npc.id}-${npc.name}"
        npcDiv.appendChild(div)

        val button = document.createElement("button") as HTMLButtonElement
        div.appendChild(button)
        button.innerText = "Go!"
        button.onclick = {
            val actor = ActorStateManager[ActorId(npc.id)]
            actor?.let { ActorStateManager.player().position.copyFrom(it.position) }
        }

        val check = document.createElement("input") as HTMLInputElement
        div.appendChild(check)
        check.type = "checkbox"
        check.id = "${npc.id}-hide"

        check.onchange = {
            val actorState = ActorStateManager[npc.actorId]
            actorState?.disabled = check.checked
            actorState?.visible = !check.checked
            Unit
        }
        check.checked = npc.info.isDefaultDisabled()

        val span = document.createElement("span") as HTMLSpanElement
        div.appendChild(span)

        var innerText = "[${npc.id.toString(0x10)}] ${npc.name}: ${npc.info} "

        if (npc.info.look.type == 0) {
            innerText += FileTableManager.getFilePath(NpcTable.getNpcModelIndex(npc.info.look))
        }

        span.innerText = innerText
        npcDiv.appendChild(document.createElement("br"))
    }

    private fun filter() {
        val filterValue = npcFilter.value
        for (i in 0 until npcDiv.childElementCount) {
            val child = npcDiv.children[i] as HTMLElement
            val visible = filterValue.isBlank() || child.id.lowercase().contains(filterValue) || child.innerText.contains(filterValue)
            child.style.display = if (visible) { "" } else { "none" }
        }
    }

}