package xim.poc.ui

import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.*
import xim.poc.game.*
import xim.poc.gl.ByteColor
import xim.poc.gl.Color

object PartyUi {

    fun draw() {
        val playerActor = ActorManager.player()

        val party = PartyManager[playerActor]
        val partyMembers = party.getAllState()

        val menu = when (partyMembers.size) {
            1 -> "menu    ptw0    "
            else -> "menu    ptw${partyMembers.size}    "
        }

        var cursorIndex: Int? = null
        for (member in partyMembers) {
            val targeted = if (UiStateHelper.isSubTargetMode()) {
                playerActor.subTarget == member.id
            } else {
                playerActor.target == member.id
            }

            if (targeted) { cursorIndex = party.getIndex(member.id) }
        }

        val menuPos = UiElementHelper.drawMenu(menu, cursorIndex = cursorIndex, menuStacks = MenuStacks.PartyStack) ?: return
        val offset = Vector2f().copyFrom(menuPos)
        offset.y += 2f

        for (member in partyMembers) {
            val memberState = ActorStateManager[member.id]
            if (memberState != null) { drawMemberStatus(memberState, offset) }
            offset.y += 20f
        }

        val pet = ActorStateManager[playerActor.getPetId()]
        if (pet != null) {
            offset.x -= 110f; offset.y -= 20f
            drawMemberStatus(pet, offset)
        }

        val targetId = if (UiStateHelper.isSubTargetMode()) { playerActor.subTarget } else { playerActor.target }
        val targetActor = ActorStateManager[targetId]
        if (targetActor != null) {
            val targetNameColor = when (targetActor.type) {
                ActorType.Pc -> ShiftJis.colorWhite
                ActorType.AllyNpc, ActorType.StaticNpc, ActorType.Effect -> ShiftJis.colorItem
                ActorType.Enemy -> getEnemyNameColor(targetActor)
            }

            val targetPos = UiElementHelper.drawMenu(menuName = "menu    targetwi", menuStacks = MenuStacks.PartyStack) ?: return

            val targetName = UiElementHelper.formatString(text = "${targetNameColor}${targetActor.name}", maxWidth = 999, font = Font.FontShp, textDirection = TextDirection.TopToBottom) ?: return
            val scale = (95f / targetName.width).coerceAtMost(1f)
            UiElementHelper.drawFormattedString(formattedString = targetName, offset = targetPos + Vector2f(8f, 10f), scale = Vector2f(scale, 1f))

            if (targetActor.displayHp) {
                val barScale = targetActor.getHp().toFloat() / targetActor.getMaxHp()
                UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 97, position = Vector2f(28f, 26f) + targetPos, scale = Vector2f(barScale, 1f))
                UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 38, position = Vector2f(25f, 26f) + targetPos)
            }
        }

        val targetModel = ActorManager[playerActor.target]
        if (targetModel != null) { drawTargetPointer(targetModel) }

        if (playerActor.isTargetLocked() && !UiStateHelper.isSubTargetMode()) {
            UiElementHelper.drawMenu(menuName = "menu    targetlo", menuStacks = MenuStacks.PartyStack, appendType = AppendType.None)
        }
    }

    fun drawTargetPointer(actor: Actor, color: Color = Color.NO_MASK) {
        val cursorPos = Vector3f().copyFrom(actor.displayPosition)
        cursorPos.y += actor.getScale() * (actor.actorModel?.getSkeleton()?.resource?.size?.y ?: 0f)
        val screenSpaceCursorPos = cursorPos.toScreenSpace()

        if (screenSpaceCursorPos != null) {
            screenSpaceCursorPos.x *= MainTool.platformDependencies.screenSettingsSupplier.width
            screenSpaceCursorPos.y *= MainTool.platformDependencies.screenSettingsSupplier.height
            UiElementHelper.drawUiElement(
                lookup = "anc     anc_l   ",
                index = UiElementHelper.currentCursorIndex(6),
                position = Vector2f(screenSpaceCursorPos.x, screenSpaceCursorPos.y),
                scale = UiElementHelper.globalUiScale,
                color = color,
                disableGlobalScale = true)
        }
    }

    private fun drawMemberStatus(actor: ActorState, offset: Vector2f) {
        val hpScale = actor.getHp().toFloat() / actor.getMaxHp()
        val mpScale = actor.getMp().toFloat() / actor.getMaxMp()

        UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 98, position = Vector2f(51f, 21f) + offset, scale = Vector2f(mpScale, 1f))
        UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 39, position = Vector2f(48f, 21f) + offset)

        UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 97, position = Vector2f(28f, 12f) + offset, scale = Vector2f(hpScale, 1f))
        UiElementHelper.drawUiElement(lookup = "menu    windowps", index = 38, position = Vector2f(25f, 12f) + offset)

        UiElementHelper.drawString(text = actor.name, offset = offset + Vector2f(4f, 4f), Font.FontShp)

        UiElementHelper.drawString(text = "${actor.getMp()}", offset = offset + Vector2f(98f, 16f), font = Font.FontShp, color = ByteColor.alpha75, alignment = TextAlignment.Right)
        UiElementHelper.drawString(text = "${actor.getHp()}", offset = offset + Vector2f(88f, 6f), font = Font.FontShp, alignment = TextAlignment.Right)
    }

    private fun getEnemyNameColor(state: ActorState): Char {
        return if (state.isDead()) {
            ShiftJis.colorGrey
        } else if (state.isEngaged()) {
            ShiftJis.colorInvalid
        } else {
            ShiftJis.colorInfo
        }
    }

}