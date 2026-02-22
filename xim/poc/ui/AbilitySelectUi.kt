package xim.poc.ui

import xim.math.Vector2f
import xim.poc.*
import xim.poc.game.ActorStateManager
import xim.poc.game.GameClient
import xim.poc.game.GameEngine
import xim.poc.game.GameEngine.displayName
import xim.poc.game.UiState
import xim.poc.game.actor.components.getRecastDelay
import xim.poc.game.configuration.constants.*
import xim.poc.gl.ByteColor
import xim.resource.AbilityType
import xim.resource.table.AbilityInfoTable.toAbilityInfo
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object AbilitySelectUi {

    private const val iconSet1 = "menu    magiconw"
    private const val iconSet2 = "menu    magico2w"

    fun getItems(type: AbilityType): List<AbilitySkillId> {
        return GameEngine.getActorAbilityList(ActorStateManager.playerId, type)
    }

    fun draw(type: AbilityType, uiState: UiState) {
        val stackPos = uiState.latestPosition ?: return
        val offset = Vector2f(0f, 0f)

        val scrollSettings = uiState.scrollSettings!!
        val items = getItems(type)

        for (i in scrollSettings.lowestViewableItemIndex until scrollSettings.lowestViewableItemIndex + scrollSettings.numElementsInPage) {
            if (i >= items.size) { break }
            val ability = items[i]

            val name = ability.displayName()
            val (iconSet, iconId) = getAbilityIcon(ability)

            val textColor = if (GameEngine.canBeginSkill(ActorStateManager.playerId, ability)) { ByteColor.half } else { ByteColor.grey }

            UiElementHelper.drawUiElement(lookup = iconSet, index = iconId, position = offset + stackPos + Vector2f(16f, 4f))
            UiElementHelper.drawString(text = name, offset = offset + stackPos + Vector2f(36f, 8f), color = textColor)
            offset.y += 16f
        }
    }

    fun getSelectedAbility(type: AbilityType, index: Int): SkillId? {
        return getItems(type).getOrNull(index)
    }

    fun getSubAbilityMenuType(type: AbilityType, index: Int): AbilityType? {
        val item = getItems(type).getOrNull(index) ?: return null

        return when (item) {
            skillBloodPactRage_603 -> AbilityType.PetAbility
            skillBloodPactWard_684 -> AbilityType.PetWard
            skillPhantomRoll_609 -> AbilityType.PhantomRoll
            skillQuickDraw_636 -> AbilityType.QuickDraw
            skillSambas_694 -> AbilityType.Samba
            skillWaltzes_695 -> AbilityType.Waltz
            skillJigs_710 -> AbilityType.Jig
            skillSteps_711 -> AbilityType.Step
            skillFlourishesI_712 -> AbilityType.FlourishI
            skillFlourishesII_725 -> AbilityType.FlourishII
            skillFlourishesIII_775 -> AbilityType.FlourishIII
            else -> null
        }
    }

    fun useSelectedAbility(type: AbilityType, index: Int, targetId: ActorId) {
        val item = getItems(type).getOrNull(index) ?: return
        val source = ActorManager.player()
        GameClient.submitUseAbility(item, sourceId = source.id, targetId = targetId)
    }

    fun drawRecast(index: Int, type: AbilityType) {
        val skill = getSelectedAbility(type, index) ?: return
        val cost = GameEngine.getSkillBaseCost(ActorStateManager.player(), skill)
        val infoWindowPos = UiElementHelper.drawMenu(menuName = "menu    subwindo", menuStacks = MenuStacks.PartyStack) ?: return

        if (type == AbilityType.WeaponSkill) {
            UiElementHelper.drawString("TP Cost", offset = infoWindowPos + Vector2f(8f, 8f), font = Font.FontShp)
            UiElementHelper.drawString(cost.value.toString(), offset = infoWindowPos + Vector2f(106f, 8f), font = Font.FontShp, alignment = TextAlignment.Right)
        }

        val recastTime = ActorStateManager.player()
            .getRecastDelay(skill)
            ?.getRemaining()
            ?: 0.toDuration(DurationUnit.SECONDS)
        val recastMinutes = recastTime.inWholeMinutes
        val recastSeconds = (recastTime.inWholeSeconds % 60).toString().padStart(2, '0')

        UiElementHelper.drawString("Next", offset = infoWindowPos + Vector2f(8f, 24f), font = Font.FontShp)
        UiElementHelper.drawString("${recastMinutes}:${recastSeconds}", offset = infoWindowPos + Vector2f(106f, 24f), font = Font.FontShp, alignment = TextAlignment.Right)
    }

    fun getAbilityIcon(skillId: AbilitySkillId): Pair<String, Int> {
        val abilityInfo = skillId.toAbilityInfo()
        return if (abilityInfo.hiResIconId >= 1000) {
            Pair(iconSet2, abilityInfo.hiResIconId - 1000)
        } else {
            Pair(iconSet1, abilityInfo.hiResIconId)
        }
    }
}