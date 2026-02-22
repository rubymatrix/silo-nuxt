package xim.poc.tools

import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.MobSkillToBlueMagicOverride
import xim.poc.PcModel
import xim.poc.browser.DatLoader
import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.EffectDisplayer
import xim.poc.game.configuration.constants.AbilitySkillId
import xim.poc.game.configuration.constants.ItemSkillId
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.SpellSkillId
import xim.resource.*
import xim.resource.table.*

object SpellHelper {

    private var spellsPopulated = false

    fun setup() {
        populateSpellButtons()
    }

    private fun populateSpellButtons() {
        if (spellsPopulated) { return }
        spellsPopulated = true

        // Magic
        val magicInput = (document.getElementById("MagicSpellId") as HTMLSelectElement)

        SpellNameTable.getAllFirst().forEachIndexed { i, string ->
            val option = document.createElement("option") as HTMLOptionElement
            magicInput.appendChild(option)

            option.text = "$string [$i]"
            option.value = i.toString()
        }

        (document.getElementById("CastMagic") as HTMLButtonElement).onclick = {
            castSpellFromIndex(magicInput.value.toInt())
        }

        (document.getElementById("NextMagic") as HTMLButtonElement).onclick = {
            castSpellFromIndex(magicInput.value.toInt())
            magicInput.selectedIndex += 1; Unit
        }

        // Abilities
        val abilityInput = document.getElementById("JobAbilityId") as HTMLSelectElement
        AbilityNameTable.getAllFirst().forEachIndexed { i, string ->
            if (string.startsWith(".") || string.startsWith("#")) { return@forEachIndexed }

            val option = document.createElement("option") as HTMLOptionElement
            abilityInput.appendChild(option)

            option.text = "$string [$i]"
            option.value = i.toString()
        }

        (document.getElementById("UseAbility") as HTMLButtonElement).onclick = {
            useAbilityFromIndex(abilityInput.value.toInt())
        }

        (document.getElementById("NextAbility") as HTMLButtonElement).onclick = {
            useAbilityFromIndex(abilityInput.value.toInt())
            abilityInput.selectedIndex += 1; Unit
        }

        // MobSkills
        val mobSkillInput = document.getElementById("MobAbilityId") as HTMLSelectElement
        MobSkillNameTable.getAll().forEachIndexed { i, string ->
            val option = document.createElement("option") as HTMLOptionElement
            mobSkillInput.appendChild(option)

            option.text = "$string [$i]"
            option.value = i.toString()
        }

        (document.getElementById("MobAbility") as HTMLButtonElement).onclick = {
            useMobAbilityFromIndex(mobSkillInput.value.toInt())
        }

        (document.getElementById("NextMobAbility") as HTMLButtonElement).onclick = {
            useMobAbilityFromIndex(mobSkillInput.value.toInt())
            mobSkillInput.selectedIndex += 1; Unit
        }

        // Items
        val itemInput = (document.getElementById("ItemId") as HTMLSelectElement)

        InventoryItems.getAll().filter { it.type == ItemListType.UsableItem }.forEach {
            val option = document.createElement("option") as HTMLOptionElement
            itemInput.appendChild(option)

            option.text = it.name
            option.value = it.itemId.toString()
        }

        (document.getElementById("UseItem") as HTMLButtonElement).onclick = {
            useItemFromIndex(itemInput.value.toInt())
        }

        (document.getElementById("NextItem") as HTMLButtonElement).onclick = {
            useItemFromIndex(itemInput.value.toInt())
            itemInput.selectedIndex += 1; Unit
        }

        // Custom
        val pathInput = document.getElementById("ExecPathId") as HTMLInputElement
        val pathExec = document.getElementById("Exec") as HTMLButtonElement
        pathExec.onclick = { executeCustom(pathInput.value) }

        (document.getElementById("NextExec") as HTMLButtonElement).onclick = {
            executeCustom(pathInput.value)
            nextCustom(pathInput)
        }

    }

    private fun castSpellFromIndex(index: Int) {
        val animationId = SpellAnimationTable[index]
        val magicInfo = SpellInfoTable[index]
        println("Using magic: $magicInfo. Animation: 0x${animationId.toString(0x10)}")

        val dat = FileTableManager.getFilePath(animationId) ?: throw IllegalStateException("No such file for: $animationId")

        val context = EffectDisplayer.displayMain(dat, sourceId = ActorStateManager.playerId, primaryTargetId = getSpellTarget(), attackContext = AttackContext())
        context?.skillId = SpellSkillId(index)
    }

    private fun useAbilityFromIndex(index: Int) {
        val model = ActorManager.player().actorModel?.model
        val playerRaceGenderConfig = if (model is PcModel) { model.raceGenderConfig } else { null }

        val abilityInfo = AbilityInfoTable[index]
        val animationId = AbilityTable.getAnimationId(abilityInfo, playerRaceGenderConfig)
        val dat = FileTableManager.getFilePath(animationId)

        if (animationId == null || dat == null) {
            println("Using ability: $abilityInfo")
            println(AbilityDescriptionTable[index])
            return
        }

        println("Using ability: $abilityInfo. Animation: 0x${animationId.toString(0x10)} -> $dat")
        println(AbilityDescriptionTable[index])

        val context = EffectDisplayer.displayMain(dat, sourceId = ActorStateManager.playerId, primaryTargetId = getSpellTarget(), attackContext = AttackContext())
        context?.skillId = AbilitySkillId(index)
    }

    private fun useMobAbilityFromIndex(index: Int) {
        val abilityInfo = MobSkillInfoTable[index] ?: return
        val path = MobSkillInfoTable.getAnimationPath(abilityInfo) ?: return

        println("Using ability: $abilityInfo -> $path")

        val player = ActorStateManager.player()
        val target = player.targetState.targetId ?: player.id

        val context = EffectDisplayer.displayMain(path, sourceId = player.id, primaryTargetId = target, attackContext = AttackContext())
        context?.skillId = MobSkillId(index)

        DatLoader.load(path).onReady {
            val effectRoutines = it.getAsResource().collectByTypeRecursive(EffectRoutineResource::class)
            val hasKnockback = effectRoutines.any { er -> er.effectRoutineDefinition.effects.any { e -> e is KnockBackRoutine } }
            if (hasKnockback) { println("\tHas knockback!") }

            val hasBroadcast = effectRoutines.any { er -> er.effectRoutineDefinition.effects.any { e -> e is ToggleBroadcastEffect } }
            if (hasBroadcast) { println("\tHas broadcast!") }
        }
    }

    private fun useItemFromIndex(index: Int) {
        val itemInfo = InventoryItems[index]
        println("Using item: $itemInfo")

        val dat = ItemAnimationTable.getAnimationPath(itemInfo) ?: return

        val context = EffectDisplayer.displayMain(dat, sourceId = ActorStateManager.playerId, primaryTargetId = getSpellTarget(), attackContext = AttackContext())
        context?.skillId = ItemSkillId(index)
    }

    private fun getSpellTarget(): ActorId {
        val playerActor = ActorStateManager.player()
        return playerActor.targetState.targetId ?: playerActor.id
    }

    private fun executeCustom(pathInput: String) {
        if (pathInput.startsWith("msa ")) {
            val mobSkillAnimationId = pathInput.substring(4).toIntOrNull(0x10) ?: return
            val animation = MobSkillInfoTable.getAnimationPath(mobSkillAnimationId) ?: return

            val player = ActorStateManager.player()
            val targetId = player.targetState.targetId ?: player.id

            EffectDisplayer.displayMain(animation, sourceId = player.id, primaryTargetId = targetId, attackContext = AttackContext(appearanceState = player.appearanceState))
            return
        }

        if (pathInput.startsWith("ws ")) {
            val weaponSkillAnimationId = pathInput.substring(3).toIntOrNull(0x10) ?: return

            val player = ActorStateManager.player()
            val raceGenderConfig = player.getBaseLook().race ?: return

            val index = MainDll.getBaseWeaponSkillAnimationIndex(raceGenderConfig) + weaponSkillAnimationId
            val animation = FileTableManager.getFilePath(index) ?: return

            val targetId = player.targetState.targetId ?: player.id

            EffectDisplayer.displayMain(animation, sourceId = player.id, primaryTargetId = targetId, attackContext = AttackContext(appearanceState = player.appearanceState))
            return
        }


        val maybeFileId = pathInput.toIntOrNull(0x10)
        if (maybeFileId != null) {
            val path = FileTableManager.getFilePath(maybeFileId) ?: throw IllegalStateException("No such file: $maybeFileId")
            EffectDisplayer.displayMain(path, sourceId = ActorStateManager.playerId, primaryTargetId = getSpellTarget(), attackContext = AttackContext())
        } else {
            EffectDisplayer.displayMain(pathInput, sourceId = ActorStateManager.playerId, primaryTargetId = getSpellTarget(), attackContext = AttackContext())
        }
    }

    private fun nextCustom(pathInput: HTMLInputElement) {
        val input = pathInput.value

        if (input.startsWith("msa ")) {
            val idx = input.substring(4).toIntOrNull(0x10) ?: return
            val next = (idx + 1).toString(0x10)
            pathInput.value = "msa $next"
            return
        }

        if (input.startsWith("ws ")) {
            val idx = input.substring(3).toIntOrNull(0x10) ?: return
            val next = (idx + 1).coerceAtMost(0xFF).toString(0x10)
            pathInput.value = "ws $next"
            return
        }

        val current = input.toIntOrNull(0x10) ?: return
        pathInput.value = (current + 1).toString(0x10)
    }

}