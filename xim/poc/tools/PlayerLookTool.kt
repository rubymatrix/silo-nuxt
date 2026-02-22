package xim.poc.tools

import web.dom.document
import web.html.HTMLButtonElement
import web.html.HTMLInputElement
import web.html.HTMLSpanElement
import xim.poc.Actor
import xim.poc.EquipmentLook
import xim.poc.ItemModelSlot
import xim.poc.RaceGenderConfig
import xim.poc.game.ActorStateManager
import xim.poc.game.StatusEffect
import xim.resource.table.EquipmentModelTable
import kotlin.time.Duration.Companion.seconds

object PlayerLookTool {

    private val enabled by lazy {
        ItemModelSlot.values().associateWith { "${it.name}OverrideEnabled" }
            .mapValues { document.getElementById(it.value) }
            .filterValues { it != null }
            .mapValues { (it.value as HTMLInputElement).also { c -> c.onchange = { internalUpdate() } } }
    }

    private val values by lazy {
        ItemModelSlot.values().associateWith { "${it.name}OverrideValue" }
            .mapValues { document.getElementById(it.value) }
            .filterValues { it != null }
            .mapValues { it.value as HTMLInputElement }
            .onEach { it.value.min = "0" }
    }

    private val paths by lazy {
        ItemModelSlot.values().associateWith { "${it.name}Path" }
            .mapValues { document.getElementById(it.value) }
            .filterValues { it != null }
            .mapValues { it.value as HTMLSpanElement }
    }

    private var setup = false
    private var latestOverride: EquipmentLook? = null

    fun update() {
        if (!setup) { setup() }

        latestOverride = if (enabled.none { it.value.checked }) {
            null
        } else {
            internalUpdate()
        }
    }

    fun getDebugOverride(actor: Actor): EquipmentLook? {
        return if (actor.isPlayer()) { latestOverride } else { null }
    }

    private fun internalUpdate(): EquipmentLook? {
        val player = ActorStateManager.player()

        val playerLook = player.getBaseLook()
        val playerRace = playerLook.race ?: return null

        ItemModelSlot.values().forEach {
            val value = values[it]
            value?.max = getMaxIndex(playerRace, it).toString()

            val current = value?.value?.toIntOrNull()
            if (current != null) { value.value = current.coerceIn(0, getMaxIndex(playerRace, it)).toString() }
        }

        val equipmentLook = playerLook.equipment.copy()

        for (itemSlot in ItemModelSlot.values()) {
            if (enabled[itemSlot]?.checked != true) { continue }

            var value = values[itemSlot]?.value?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            value = value.coerceAtMost(getMaxIndex(playerRace, itemSlot))

            val path = getPath(playerRace, itemSlot, value)
            val currentText = paths[itemSlot]?.innerText
            val newText = path ?: "Not found"
            if (currentText != newText) { paths[itemSlot]?.innerText = newText }

            if (path != null) { equipmentLook[itemSlot] = value }
        }

        return equipmentLook
    }

    private fun getPath(raceGenderConfig: RaceGenderConfig, itemModelSlot: ItemModelSlot, itemModelId: Int): String? {
        return try {
            EquipmentModelTable.getItemModelPath(raceGenderConfig, itemModelSlot, itemModelId)
        } catch (e: Exception) {
            null
        }
    }

    private fun getMaxIndex(raceGenderConfig: RaceGenderConfig, itemModelSlot: ItemModelSlot): Int {
        return (EquipmentModelTable.getNumEntries(raceGenderConfig, itemModelSlot) - 1).coerceAtLeast(0)
    }

    private fun setup() {
        setup = true
        val costumeInput = (document.getElementById("CostumeValue") as HTMLInputElement)

        (document.getElementById("CostumeButton") as HTMLButtonElement).onclick = {
            applyCostume(costumeInput)
        }

        (document.getElementById("NextCostume") as HTMLButtonElement).onclick = {
            applyCostume(costumeInput)
            val currentValue = costumeInput.value.toIntOrNull(0x10) ?: 0x0
            costumeInput.value = (currentValue + 1).toString(0x10)
        }
    }

    private fun applyCostume(costumeInput: HTMLInputElement) {
        val costumeValue = costumeInput.value.toIntOrNull(0x10) ?: return
        val effect = ActorStateManager.player().gainStatusEffect(StatusEffect.Costume)
        effect.counter = costumeValue
        effect.preventsAction = false
    }

}