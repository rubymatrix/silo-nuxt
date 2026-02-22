package xim.poc.tools

import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement
import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.DefaultEnemyController
import xim.poc.ModelLook
import xim.poc.game.ActorType
import xim.poc.game.GameEngine
import xim.poc.game.event.InitialActorState

object NpcSpawningTool {

    private val input by lazy { document.getElementById("NpcSpawnId") as HTMLInputElement }
    private val button by lazy { document.getElementById("NpcSpawn") as HTMLButtonElement }
    private val next by lazy { document.getElementById("NpcSpawnNext") as HTMLButtonElement }

    private val spawned = ArrayList<ActorId>()

    fun setup() {
        button.onclick = { spawn() }
        next.onclick = {
            spawn()

            if (input.value.startsWith("!")) {
                val value = input.value.substring(1).toIntOrNull(0x10) ?: 0x0
                input.value = "!${(value+1).toString(0x10)}"
            } else if (input.value.startsWith("^")) {
                val value = input.value.substring(1).toIntOrNull(0x10) ?: 0x0
                input.value = "^${(value+1).toString(0x10)}"
            } else {
                val currentValue = input.value.toIntOrNull(0x10) ?: 0x0
                input.value = (currentValue + 1).toString(0x10)
            }
        }
    }

    private fun spawn() {
        val rawInput = input.value
        val modelLook = if (rawInput.startsWith("!")) {
            val id = rawInput.substring(1, rawInput.length).toIntOrNull(0x10) ?: return
            ModelLook.fileTableIndex(id)
        } else if (rawInput.startsWith("^")) {
            val id = rawInput.substring(1, rawInput.length).toIntOrNull(0x10) ?: return
            ModelLook.npcWithBase(id)
        } else {
            val id = rawInput.toIntOrNull(0x10) ?: return
            ModelLook.npc(id)
        }

        spawn(modelLook)
    }

    fun spawn(modelLook: ModelLook) {
        spawned.forEach { GameEngine.submitDeleteActor(it) }
        spawned.clear()

        val position = Vector3f().copyFrom(ActorManager.player().displayPosition)
        position.x -= 2f

        val appearance = document.getElementById("appearanceInput") as HTMLInputElement
        val appearanceState = appearance.value.toIntOrNull() ?: 0

        GameEngine.submitCreateActorState(InitialActorState(
            name = "NpcTool",
            type = ActorType.Enemy,
            position = position,
            modelLook = modelLook,
            movementController = DefaultEnemyController(),
            appearanceState = appearanceState,
        )).onReady {
            spawned += it.id
        }
    }

}