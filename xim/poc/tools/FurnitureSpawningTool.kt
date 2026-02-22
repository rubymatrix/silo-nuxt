package xim.poc.tools

import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.ModelLook
import xim.poc.game.ActorType
import xim.poc.game.GameEngine
import xim.poc.game.event.InitialActorState
import xim.resource.DatId
import xim.resource.InventoryItemType
import xim.resource.InventoryItems
import xim.resource.table.FileTableManager

object FurnitureSpawningTool {

    private val select by lazy { document.getElementById("FurnitureSpawnId") as HTMLSelectElement }
    private val button by lazy { document.getElementById("FurnitureSpawn") as HTMLButtonElement }
    private val next by lazy { document.getElementById("FurnitureSpawnNext") as HTMLButtonElement }

    private val spawned = ArrayList<ActorId>()
    private var setup = false

    fun setup() {
        if (setup) { return }
        setup = true

        InventoryItems.getAll().filter { it.itemType == InventoryItemType.Furnishing }
            .sortedBy { it.itemId }
            .filter { it.itemId > 0 }
            .forEach {
                val child = document.createElement("option") as HTMLOptionElement
                child.text = it.name
                child.value = it.itemId.toString()
                select.appendChild(child)
            }

        button.onclick = { spawn() }
        next.onclick = {
            spawn()
            select.selectedIndex += 1
            Unit
        }
    }

    private fun spawn() {
        spawned.forEach { GameEngine.submitDeleteActor(it) }
        spawned.clear()

        val id = select.value.toIntOrNull() ?: return
        val item = InventoryItems[id]
        println("Spawning: $item")

        val modelId = InventoryItems.getFurnitureModelId(item) ?: return
        val modelResourcePath = FileTableManager.getFilePath(modelId) ?: return

        val position = Vector3f().copyFrom(ActorManager.player().displayPosition)
        position.x -= 2f

        GameEngine.submitCreateActorState(InitialActorState(
            name = modelResourcePath,
            type = ActorType.Enemy,
            position = position,
            modelLook = ModelLook.fileTableIndex(modelId),
            popRoutines = listOf(DatId("aper"), DatId("efon"), DatId.pop, DatId("@scd"))
        )).onReady {
            spawned += it.id
        }
    }

}