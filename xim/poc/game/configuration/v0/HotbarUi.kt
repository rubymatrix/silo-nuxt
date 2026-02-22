package xim.poc.game.configuration.v0

import xim.math.Vector2f
import xim.poc.*
import xim.poc.browser.Keybind
import xim.poc.browser.LocalStorage
import xim.poc.browser.ModifierKey
import xim.poc.game.*
import xim.poc.game.GameEngine.displayName
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.getEquipment
import xim.poc.game.actor.components.getInventory
import xim.poc.game.actor.components.getRecastDelay
import xim.poc.game.configuration.EventScriptRunner
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.interactions.BlueMagicUi
import xim.poc.gl.Color
import xim.poc.tools.UiPosition
import xim.poc.tools.UiPositionTool
import xim.poc.tools.ZoneChanger
import xim.poc.ui.AbilitySelectUi
import xim.poc.ui.ShiftJis
import xim.poc.ui.SpellSelectUi
import xim.resource.AbilityType
import xim.resource.EquipSlot
import xim.resource.SpellElement
import xim.resource.TargetFlag
import xim.resource.table.AbilityInfoTable.toAbilityInfo
import kotlin.time.DurationUnit

private enum class ActionType {
    Ability,
    Spell,
    RangedAttack,
    Item,
}

private enum class TargetType {
    Self,
    Target,
}

private class HotBarEntry(
    val id: Int,
    val actionType: ActionType,
    val targetType: TargetType,
    val skillId: SkillId? = null,
    val shortCode: String,
    val inventoryItem: InventoryItem? = null,
)

object HotbarUi {

    private val buttonSize = Vector2f(32f, 32f)

    private val hotbar1: Array<HotBarEntry?> = Array(10) { null }
    private val hotbar2: Array<HotBarEntry?> = Array(10) { null }
    private val hotbar3: Array<HotBarEntry?> = Array(10) { null }

    fun draw() {
        if (!isEnabled()) { return }

        val abilities = GameState.getGameMode().getActorAbilityList(ActorStateManager.playerId, AbilityType.WeaponSkill)
        val abilityMacro = abilities.map { toAbilityMacro(it) }
        for (i in 0 until 9) { hotbar1[i] = abilityMacro.getOrNull(i) }

        addPotionMacro()
        addEtherMacro()
        addRemedyMacro()
        addRangedAttackMacro()

        val spells = ActorStateManager.player().getLearnedSpells().equippedSpells
        val spellMacros = spells.map { toSpellMacro(it) }
        for (i in 0 until 10) { hotbar2[i] = spellMacros.getOrNull(i) }
        for (i in 10 until 20) { hotbar3[i-10] = spellMacros.getOrNull(i) }

        val parentMenu = UiStateHelper.chatLogContext.latestMenu ?: return
        val parentPos = UiStateHelper.chatLogContext.latestPosition ?: return
        val configuredPos = UiPositionTool.getOffset(UiPosition.Hotbar)
        val basePosition = configuredPos + parentPos + Vector2f(parentMenu.frame.size.x + 8f, 0f)

        val bindings = LocalStorage.getConfiguration().keyboardSettings

        for (i in hotbar1.indices) {
            val macro = hotbar1[i] ?: continue
            drawMacro(macro, column = i, row = 0, basePosition = basePosition, macroName = bindingDisplayName(bindings.hotbar1Bindings, i))
        }

        for (i in hotbar2.indices) {
            val macro = hotbar2[i] ?: continue
            drawMacro(macro, column = i, row = 1, basePosition = basePosition, macroName = bindingDisplayName(bindings.hotbar2Bindings, i))
        }

        for (i in hotbar3.indices) {
            val macro = hotbar3[i] ?: continue
            drawMacro(macro, column = i, row = 2, basePosition = basePosition, macroName = bindingDisplayName(bindings.hotbar3Bindings, i))
        }
    }

    private fun toAbilityMacro(it: SkillId): HotBarEntry? {
        if (it !is AbilitySkillId) { return null }
        return HotBarEntry(
            it.id,
            ActionType.Ability,
            guessTargetType(it),
            it,
            it.displayName().split(" ")[0].substring(0, 6)
        )
    }

    private fun toSpellMacro(it: SkillId): HotBarEntry? {
        if (it !is SpellSkillId) { return null }
        if (it == spellNull_0) { return null }
        return HotBarEntry(
            it.id,
            ActionType.Spell,
            guessTargetType(it),
            it,
            it.displayName().split(" ")[0].substring(0, 6)
        )
    }

    private fun addPotionMacro() {
        val strongest = ActorStateManager.player().getInventory().inventoryItems
            .filter { ItemDefinitions.potions.contains(it.id) }
            .maxByOrNull { ItemDefinitions[it].internalLevel } ?: return

        addItemMacro(strongest, 6)
    }

    private fun addEtherMacro() {
        val strongest = ActorStateManager.player().getInventory().inventoryItems
            .filter { ItemDefinitions.ethers.contains(it.id) }
            .maxByOrNull { ItemDefinitions[it].internalLevel } ?: return

        addItemMacro(strongest, 7)
    }

    private fun addRemedyMacro() {
        val strongest = ActorStateManager.player().getInventory().inventoryItems
            .filter { ItemDefinitions.remedies.contains(it.id) }
            .maxByOrNull { ItemDefinitions[it].internalLevel } ?: return

        addItemMacro(strongest, 8)
    }

    private fun addItemMacro(item: InventoryItem, slot: Int) {
        val skillId = ItemSkillId(item.id)
        val displayName = skillId.displayName().split(" ")[0].substring(0, 6)

        hotbar1[slot] = HotBarEntry(
            id = item.id,
            actionType = ActionType.Item,
            skillId = skillId,
            targetType = TargetType.Self,
            shortCode = displayName,
            inventoryItem = item
        )
    }

    private fun addRangedAttackMacro() {
        val player = ActorStateManager.player()
        val rangedItem = player.getEquipment(EquipSlot.Range) ?: return
        hotbar1[9] = HotBarEntry(
            id = rangedItem.info().itemId,
            actionType = ActionType.RangedAttack,
            skillId = rangedAttack,
            targetType = TargetType.Target,
            shortCode = "Ranged"
        )
    }

    fun handleInput() {
        if (!isEnabled()) { return }

        val keyboard = MainTool.platformDependencies.keyboard
        val keyboardSettings = LocalStorage.getConfiguration().keyboardSettings

        val pairs = listOf(
            hotbar1 to keyboardSettings.hotbar1Bindings,
            hotbar2 to keyboardSettings.hotbar2Bindings,
            hotbar3 to keyboardSettings.hotbar3Bindings,
        )

        for ((hotbar, bindings) in pairs) {
            for (i in 0 until 10) {
                val macro = hotbar[i] ?: continue
                val keybind = bindings.getOrNull(i) ?: continue

                if (!keyboard.isKeyPressed(keybind)) { continue }

                useMacro(macro)
                return
            }
        }
    }

    private fun useMacro(hotBarEntry: HotBarEntry) {
        val player = ActorStateManager.player()

        val target = when(hotBarEntry.targetType) {
            TargetType.Self -> player.id
            TargetType.Target -> player.targetState.targetId
        } ?: return

        if (!canUseMacro(hotBarEntry)) {
            return
        }

        when (hotBarEntry.actionType) {
            ActionType.Ability -> castAbility(hotBarEntry.skillId as AbilitySkillId, target)
            ActionType.Spell -> castSpell(hotBarEntry.skillId as SpellSkillId, target)
            ActionType.RangedAttack -> useRangedAttack(target)
            ActionType.Item -> useItem(hotBarEntry.inventoryItem, target)
        }
    }

    private fun isEnabled(): Boolean {
        return !UiStateHelper.isFocus(UiStateHelper.expandedChatLogContext)
    }

    private fun canUseMacro(hotBarEntry: HotBarEntry): Boolean {
        if (ZoneChanger.isChangingZones() || EventScriptRunner.isRunningScript()) { return false }

        val player = ActorStateManager.player()
        val skillId = hotBarEntry.skillId ?: return false

        val target = (when(hotBarEntry.targetType) {
            TargetType.Self -> player
            TargetType.Target -> ActorStateManager.playerTarget()
        }) ?: return false

        return GameEngine.canBeginSkillOnTarget(player, target, skillId)
    }

    private fun castAbility(skill: AbilitySkillId, target: ActorId) {
        GameClient.submitUseAbility(sourceId = ActorStateManager.playerId, targetId = target, skill = skill)
    }

    private fun castSpell(skill: SpellSkillId, target: ActorId) {
        GameClient.submitStartCasting(sourceId = ActorStateManager.playerId, targetId = target, skill = skill)
    }

    private fun useEquipSet(equipSetId: Int, target: ActorId) {
        val equipment = LocalStorage.getPlayerEquipmentSet(equipSetId) ?: return
        for ((slot, item) in equipment.getAllItems()) {
            GameClient.submitEquipItem(ActorStateManager.playerId, slot, item)
        }
    }

    private fun useRangedAttack(target: ActorId) {
        GameClient.submitStartRangedAttack(ActorStateManager.playerId, target)
    }

    private fun useItem(inventoryItem: InventoryItem?, target: ActorId) {
        inventoryItem ?: return
        GameClient.submitStartUsingItem(
            sourceId = ActorStateManager.playerId,
            target = target,
            inventoryItem = inventoryItem
        )
    }

    private fun drawMacro(hotBarEntry: HotBarEntry, row: Int, column: Int, basePosition: Vector2f, macroName: String) {
        val (iconSet, iconId) = when (hotBarEntry.actionType) {
            ActionType.Ability -> AbilitySelectUi.getAbilityIcon(hotBarEntry.skillId as AbilitySkillId)
            ActionType.Spell -> SpellSelectUi.getSpellIcon(hotBarEntry.skillId as SpellSkillId)
            ActionType.RangedAttack -> Pair(null, null)
            ActionType.Item -> Pair(null, null)
        }

        var skillHelp = ""

        if (hotBarEntry.actionType == ActionType.Spell) {
            skillHelp = skillCooldownHelper(hotBarEntry.skillId!!)
        } else if (hotBarEntry.skillId is AbilitySkillId && hotBarEntry.skillId.toAbilityInfo().type != AbilityType.WeaponSkill) {
            skillHelp = skillCooldownHelper(hotBarEntry.skillId)
        } else if (hotBarEntry.skillId != null) {
            skillHelp = skillChainHelper(hotBarEntry.skillId)
        }

        val canUse = canUseMacro(hotBarEntry)
        val mask = if (canUse) {
            Color.ALPHA_75
        } else {
            Color.ALPHA_25
        }

        val position = basePosition + Vector2f(column * 44f, (2 - row) * 50f)

        if (iconSet != null && iconId != null) {
            UiElementHelper.drawUiElement(
                lookup = iconSet,
                index = iconId,
                scale = Vector2f(2f, 2f),
                position = position,
                color = mask
            )
        } else if (hotBarEntry.actionType == ActionType.RangedAttack) {
            val item = ActorStateManager.player().getEquipment(EquipSlot.Range) ?: return
            UiElementHelper.drawInventoryItemIcon(
                item = item,
                position = position,
                scale = Vector2f(1f, 1f),
                mask = mask
            )
        } else if (hotBarEntry.actionType == ActionType.Item) {
            if (hotBarEntry.inventoryItem == null) { return }
            UiElementHelper.drawInventoryItemIcon(
                item = hotBarEntry.inventoryItem,
                position = position,
                scale = Vector2f(1f, 1f),
                mask = mask
            )
        }

        val textPosition = Vector2f(position.x, position.y - 4f)
        val textColor = if (canUse) {
            ShiftJis.colorWhite
        } else {
            ShiftJis.colorGrey
        }

        val magicBurst = isMagicBurst(hotBarEntry.skillId)
        val magicBurstHint = if (canUse && magicBurst) {
            ShiftJis.colorInfo
        } else { textColor }

        UiElementHelper.drawString(
            text = "$textColor$macroName\n${skillHelp}\n${magicBurstHint}${hotBarEntry.shortCode}",
            offset = textPosition,
            font = Font.FontShp,
            alignment = TextAlignment.Left
        )

        ClickHandler.registerUiClickHandler(position = position, size = buttonSize) { useMacro(hotBarEntry); true }
        ToolTipHelper.addToolTip(position = position, size = buttonSize) { toolTipHelp(hotBarEntry) }
    }

    private fun guessTargetType(skill: SkillId): TargetType {
        val targetFlags = GameEngine.getSkillTargetFlags(skill)
        return if (targetFlags and TargetFlag.Self.flag != 0) {
            TargetType.Self
        } else {
            TargetType.Target
        }
    }

    private fun skillCooldownHelper(skill: SkillId): String {
        val recast = ActorStateManager.player().getRecastDelay(skill) ?: return skillChainHelper(skill)
        return recast.getRemaining().toString(DurationUnit.SECONDS, decimals = 0)
    }

    private fun skillChainHelper(skill: SkillId): String {
        val targetState = ActorStateManager.playerTarget() ?: return ""

        val skillChainRequest = SkillChainRequest(
            attacker = ActorStateManager.player(),
            defender = targetState,
            currentState = targetState.skillChainTargetState.skillChainState,
            skill = skill,
        )

        val closingStep = GameV0.getSkillChainResult(skillChainRequest, ignoreWindow = true) ?: return ""
        if (closingStep !is SkillChainStep) { return "" }

        val isOpen = if (targetState.skillChainTargetState.isSkillChainWindowOpen()) { "" } else { "${ShiftJis.colorQuarterAlpha}" }

        val closingAttribute = closingStep.attribute
        return isOpen + if (closingAttribute.level <= 2) {
            closingAttribute.elements.map { ShiftJis.toChar(it) }.joinToString("")
        } else {
            val char = if (closingAttribute.elements.contains(SpellElement.Light)) {
                ShiftJis.specialLight
            } else {
                ShiftJis.specialDark
            }
            "$char$char"
        } + ShiftJis.colorClear
    }

    private fun isMagicBurst(skill: SkillId?): Boolean {
        skill ?: return false
        val targetState = ActorStateManager.playerTarget() ?: return false

        if (guessTargetType(skill) != TargetType.Target) { return false }

        return SpellDamageCalculator.getBaseMagicBurstBonus(
            attacker = ActorStateManager.player(),
            originalTarget = targetState,
            skill = skill,
        ) != null
    }

    private fun bindingDisplayName(keybinds: ArrayList<Keybind>, rowIndex: Int): String {
        val keybindIndex = rowIndex % 10
        val keybind = keybinds.getOrNull(keybindIndex) ?: return ""

        val prefix = when (keybind.modifierKey) {
            ModifierKey.None -> ""
            ModifierKey.Shift -> "s"
            ModifierKey.Alt -> "a"
            ModifierKey.Control -> "c"
        }

        return "$prefix${keybind.keyCode.last()}"
    }

    private fun toolTipHelp(hotBarEntry: HotBarEntry): String {
        val skill = hotBarEntry.skillId ?: return ""

        return when (skill) {
            is SpellSkillId -> BlueMagicUi.getBlueMagicHelpText(skill)
            is AbilitySkillId -> V0AbilityDefinitions.toDescription(skill)
            is ItemSkillId -> V0ItemDefinitions.toDescription(skill)
            is RangedAttackSkillId -> "Perform a ranged attack."
            else -> ""
        }
    }

}