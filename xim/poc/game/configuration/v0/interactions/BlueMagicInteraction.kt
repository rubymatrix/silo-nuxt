package xim.poc.game.configuration.v0.interactions

import xim.math.Vector2f
import xim.poc.ActorId
import xim.poc.UiElementHelper
import xim.poc.UiResourceManager
import xim.poc.audio.SystemSound
import xim.poc.game.ActorStateManager
import xim.poc.game.GameEngine.displayName
import xim.poc.game.ScrollSettings
import xim.poc.game.UiState
import xim.poc.game.UiStateHelper
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.SpellSkillId
import xim.poc.game.configuration.constants.spellNull_0
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0SpellDefinitions
import xim.poc.game.configuration.v0.getLearnedSpells
import xim.poc.game.configuration.v0.interactions.BlueMagicUi.drawBlueEquip
import xim.poc.game.configuration.v0.interactions.BlueMagicUi.drawBlueHelp
import xim.poc.game.configuration.v0.interactions.BlueMagicUi.drawBlueInventory
import xim.poc.game.configuration.v0.interactions.BlueMagicUi.equipSpell
import xim.poc.game.configuration.v0.interactions.BlueMagicUi.getLearnedSpells
import xim.poc.game.configuration.v0.interactions.BlueMagicUiState.blueEquip
import xim.poc.game.configuration.v0.interactions.BlueMagicUiState.blueHelp
import xim.poc.game.configuration.v0.interactions.BlueMagicUiState.blueInventory
import xim.poc.gl.Color
import xim.poc.tools.UiPosition
import xim.poc.ui.ShiftJis
import xim.poc.ui.SpellSelectUi
import xim.util.Fps
import kotlin.time.DurationUnit

object BlueMagicInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        open(SystemSound.MenuSelect)
    }

    fun open(sound: SystemSound? = null) {
        UiStateHelper.pushState(blueEquip, sound)
    }

}

private object BlueMagicUiState {

    val blueHelp: UiState
    val blueInventory: UiState
    val blueEquip: UiState

    init {
        blueHelp = UiState(
            focusMenu = "menu    bluehelp",
            additionalDraw = { drawBlueHelp() },
            uiPositionKey = UiPosition.Equipment,
        ) {
            false
        }

        blueInventory = UiState(
            focusMenu = "menu    bluinven",
            additionalDraw = { drawBlueInventory() },
            scrollSettings = ScrollSettings(10) { getLearnedSpells().size },
            resetCursorIndexOnPush = false,
            childStates = { listOf(blueHelp) },
            drawParent = true,
            locksMovement = true,
            uiPositionKey = UiPosition.Equipment,
        ) {
            if (UiStateHelper.isEnterPressed()) {
                equipSpell()
                UiStateHelper.popState(SystemSound.MenuSelect)
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        blueEquip = UiState(
            focusMenu = "menu    bluequip",
            additionalDraw = { drawBlueEquip() },
            childStates = { listOf(blueInventory, blueHelp) },
            scrollSettings = ScrollSettings(10) { 20 },
            locksMovement = true,
            uiPositionKey = UiPosition.Equipment,
        ) {
            if (UiStateHelper.isEnterPressed()) {
                UiStateHelper.pushState(blueInventory, SystemSound.MenuSelect)
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

    }

}

object BlueMagicUi {

    fun getLearnedSpells(): List<SpellSkillId> {
        return ActorStateManager.player().getLearnedSpells().spellIds
    }

    fun getEquippedSpells(): List<SpellSkillId> {
        return ActorStateManager.player().getLearnedSpells().equippedSpells.toList()
    }

    fun equipSpell() {
        val actorSpells = ActorStateManager.player().getLearnedSpells()

        val equipSlot = getSelectedIndex(blueEquip)
        if (equipSlot >= actorSpells.equippedSpells.size) { return }

        val inventorySlot = getSelectedIndex(blueInventory)
        val inventorySpell = actorSpells.spellIds.getOrNull(inventorySlot) ?: return

        val currentlyEquippedSlot = actorSpells.equippedSpells.indexOfFirst { it == inventorySpell }

        if (currentlyEquippedSlot == equipSlot) {
            actorSpells.equippedSpells[equipSlot] = spellNull_0
        } else {
            if (currentlyEquippedSlot >= 0) {
                actorSpells.equippedSpells[currentlyEquippedSlot] = actorSpells.equippedSpells[equipSlot]
            }
            actorSpells.equippedSpells[equipSlot] = inventorySpell
        }
    }

    fun drawBlueEquip() {
        val scrollSettings = blueEquip.scrollSettings ?: return
        val stackPos = blueEquip.latestPosition ?: return

        val offset = Vector2f()

        for (i in 0 until 10) {
            val itemIndex = scrollSettings.lowestViewableItemIndex + i

            val current = i == blueEquip.cursorIndex
            val mask = if (current) { Color(1f, 0.75f, 0f, 1f) } else { Color.NO_MASK }
            UiElementHelper.drawUiElement(lookup = "menu    keytops3", index = 400+itemIndex, color = mask, position = offset + stackPos + Vector2f(4f, 4f))

            val skill = getEquippedSpells().getOrNull(itemIndex)

            if (skill != null && skill != spellNull_0) {
                val (spellIcon, spellIndex) = SpellSelectUi.getSpellIcon(skill)
                UiElementHelper.drawUiElement(lookup = spellIcon, index = spellIndex, position = offset + stackPos + Vector2f(22f, 4f))
                UiElementHelper.drawString(text = skill.displayName(), offset = offset + stackPos + Vector2f(42f, 8f))
            }

            offset.y += 18f
        }
    }

    fun drawBlueInventory() {
        val scrollSettings = blueInventory.scrollSettings ?: return
        val stackPos = blueInventory.latestPosition ?: return

        val offset = Vector2f()

        for (i in 0 until 10) {
            val itemIndex = scrollSettings.lowestViewableItemIndex + i
            val skill = getLearnedSpells().getOrNull(itemIndex)

            if (skill != null) {
                val (spellIcon, spellIndex) = SpellSelectUi.getSpellIcon(skill)
                UiElementHelper.drawUiElement(lookup = spellIcon, index = spellIndex, position = offset + stackPos + Vector2f(4f, 4f))

                val textColor = if (isEquipped(skill)) { ShiftJis.colorItem } else { ShiftJis.colorWhite }
                val spellName = "$textColor${skill.displayName()}${ShiftJis.colorClear}"

                UiElementHelper.drawString(text = spellName, offset = offset + stackPos + Vector2f(22f, 8f))
            }

            offset.y += 18f
        }
    }

    fun drawBlueHelp() {
        val stackPos = blueHelp.latestPosition ?: return

        val skill = if (UiStateHelper.isFocus(blueEquip)) {
            getEquippedSpells().getOrNull(getSelectedIndex(blueEquip)) ?: return
        } else {
            getLearnedSpells().getOrNull(getSelectedIndex(blueInventory)) ?: return
        }

        if (skill == spellNull_0) { return }
        val message = getBlueMagicHelpText(skill)

        val menu = UiResourceManager.getMenu("menu    bluehelp") ?: return
        menu.uiMenu.frame.size.y = 8f + 16f * message.split("\n").size

        val offset = stackPos + Vector2f(6f, 6f)
        UiElementHelper.drawString(text = message, offset = offset)
    }

    fun getBlueMagicHelpText(skill: SpellSkillId): String {
        val player = ActorStateManager.player()
        val baseCost = GameV0.getSkillBaseCost(player, skill)

        val (recastTime, _) = GameV0Helpers.getBaseSpellRecast(skill)
        val recastTimeDuration = Fps.framesToSeconds(recastTime)

        val baseCastTime = Fps.framesToSeconds(GameV0Helpers.getSpellBaseCastTime(skill))

        val description = V0SpellDefinitions.getDescription(skill).prependIndent("  ")

        val spellName = skill.displayName()
        return "$spellName\n$description\n${baseCost.type.name.uppercase()}: ${baseCost.value}    Cast: ${baseCastTime.toString(DurationUnit.SECONDS, decimals = 1)}    Recast: ${recastTimeDuration.toString(DurationUnit.SECONDS, decimals = 0)}"
    }

    private fun getSelectedIndex(uiState: UiState): Int {
        val scroll = uiState.scrollSettings ?: return 0
        return scroll.lowestViewableItemIndex + uiState.cursorIndex
    }

    private fun isEquipped(skillId: SkillId): Boolean {
        return getEquippedSpells().any { it == skillId }
    }

}
