package xim.poc.ui

import xim.math.Vector2f
import xim.poc.*
import xim.poc.game.ActorStateManager
import xim.poc.game.GameClient
import xim.poc.game.GameEngine
import xim.poc.game.GameEngine.displayName
import xim.poc.game.UiState
import xim.poc.game.actor.components.getRecastDelay
import xim.poc.game.configuration.constants.SpellSkillId
import xim.poc.game.configuration.constants.spellNull_0
import xim.poc.gl.ByteColor
import xim.resource.MagicType
import xim.resource.table.SpellInfoTable.toSpellInfo
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object SpellSelectUi {

    private const val iconSet1 = "menu    magiconw"
    private const val iconSet2 = "menu    magico2w"

    fun getSpellItems(magicType: MagicType): List<SpellSkillId> {
        val spellItems = GameEngine.getSpellList(ActorStateManager.playerId)
        if (magicType == MagicType.None) { return spellItems }
        return spellItems.filter { it != spellNull_0 && it.toSpellInfo().magicType == magicType }
    }

    fun drawSpells(uiState: UiState, magicType: MagicType) {
        val stackPos = uiState.latestPosition ?: return
        val offset = Vector2f(0f, 0f)

        val player = ActorManager.player()

        val scrollSettings = uiState.scrollSettings!!
        val items = getSpellItems(magicType)

        for (i in scrollSettings.lowestViewableItemIndex until scrollSettings.lowestViewableItemIndex + scrollSettings.numElementsInPage) {
            if (i >= items.size) { break }

            val skill = items[i]
            val spellName = skill.displayName()

            val (iconSet, iconId) = getSpellIcon(skill)

            val textColor = if (GameEngine.canBeginSkill(player.id, skill)) { ByteColor.half } else { ByteColor.grey }

            UiElementHelper.drawUiElement(lookup = iconSet, index = iconId, position = offset + stackPos + Vector2f(16f, 4f))
            UiElementHelper.drawString(text = spellName, offset = offset + stackPos + Vector2f(36f, 8f), color = textColor)
            offset.y += 16f
        }
    }

    fun drawRecast(index: Int, magicType: MagicType) {
        val skill = getSelectedSpell(index, magicType) ?: return
        val infoWindowPos = UiElementHelper.drawMenu(menuName = "menu    subwindo", menuStacks = MenuStacks.PartyStack) ?: return

        val skillCost = GameEngine.getSkillBaseCost(ActorStateManager.player(), skill)

        UiElementHelper.drawString("MP Cost", offset = infoWindowPos + Vector2f(8f, 8f), font = Font.FontShp)
        UiElementHelper.drawString(skillCost.value.toString(), offset = infoWindowPos + Vector2f(106f, 8f), font = Font.FontShp, alignment = TextAlignment.Right)

        val recastTime = ActorStateManager.player()
            .getRecastDelay(skill)
            ?.getRemaining()
            ?: 0.toDuration(DurationUnit.SECONDS)

        val recastMinutes = recastTime.inWholeMinutes
        val recastSeconds = (recastTime.inWholeSeconds % 60).toString().padStart(2, '0')

        UiElementHelper.drawString("Next", offset = infoWindowPos + Vector2f(8f, 24f), font = Font.FontShp)
        UiElementHelper.drawString("${recastMinutes}:${recastSeconds}", offset = infoWindowPos + Vector2f(106f, 24f), font = Font.FontShp, alignment = TextAlignment.Right)
    }

    fun getSelectedSpell(index: Int, magicType: MagicType): SpellSkillId? {
        return getSpellItems(magicType).getOrNull(index)
    }

    fun castSelectedSpell(index: Int, target: ActorId, magicType: MagicType) {
        ActorManager[target] ?: return
        val spellInfo = getSpellItems(magicType).getOrNull(index) ?: return
        val player = ActorManager.player()
        GameClient.submitStartCasting(player.id, target, spellInfo)
    }

    fun getSpellIcon(skill: SpellSkillId): Pair<String, Int> {
        val spellInfo = skill.toSpellInfo()
        return if (spellInfo.iconId < 0) {
            Pair(iconSet1, spellInfo.simpleIconId)
        } else if (spellInfo.iconId <= 676) {
            Pair(iconSet1, spellInfo.iconId)
        } else {
            Pair(iconSet2, spellInfo.iconId - 1000)
        }
    }

}