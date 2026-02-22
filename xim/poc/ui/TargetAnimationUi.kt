package xim.poc.ui

import xim.math.Vector2f
import xim.poc.ActorManager
import xim.poc.AppendType
import xim.poc.MenuStacks
import xim.poc.UiElementHelper
import xim.poc.audio.SystemSound
import xim.poc.game.ScrollSettings
import xim.poc.game.UiState
import xim.poc.game.UiStateHelper
import xim.resource.DatId
import xim.resource.DirectoryResource
import xim.resource.EffectRoutineResource

object TargetAnimationUi {

    private val basicDoorAnimations = listOf(DatId.open, DatId.close, DatId.opened, DatId.closed)

    private val uiState = UiState(
        additionalDraw = { draw(it) },
        focusMenu = "menu    magic   ",
        menuStacks = MenuStacks.LogStack,
        appendType = AppendType.StackAndAppend,
        resetCursorIndexOnPush = false,
        scrollSettings = ScrollSettings(numElementsInPage = 12) { getItems().size },
    ) {
        if (ActorManager.player().target == null) {
            UiStateHelper.popState(SystemSound.MenuClose)
            true
        } else if (UiStateHelper.isEnterPressed()) {
            val itemIndex = UiStateHelper.currentItemIndex()
            selectedOption(itemIndex)
            UiStateHelper.popState(SystemSound.MenuClose)
            true
        } else if (UiStateHelper.isEscPressed()) {
            UiStateHelper.popState(SystemSound.MenuClose)
            true
        } else {
            false
        }
    }

    fun push() {
        UiStateHelper.pushState(uiState, SystemSound.MenuSelect)
    }

    fun getItems(): List<DatId> {
        return getDats().sortedBy { it.id }
    }

    fun draw(uiState: UiState) {
        val stackPos = uiState.latestPosition ?: return
        val offset = Vector2f(0f, 0f)

        val scrollSettings = uiState.scrollSettings!!
        val items = getItems()

        for (i in scrollSettings.lowestViewableItemIndex until scrollSettings.lowestViewableItemIndex + scrollSettings.numElementsInPage) {
            if (i >= items.size) { break }
            val animId = items[i]

            UiElementHelper.drawString(text = animId.id, offset = offset + stackPos + Vector2f(36f, 8f))
            offset.y += 16f
        }
    }

    fun selectedOption(option: Int) {
        val item = getItems().getOrNull(option) ?: return
        val target = ActorManager[ActorManager.player().target] ?: return
        target.playRoutine(item)
    }

    fun isBasicDoor(): Boolean {
        val items = getItems()
        return items.size <= 4 && basicDoorAnimations.containsAll(getItems())
    }

    private fun getDats(): List<DatId> {
        return getCurrentDir().map { it.collectByType(EffectRoutineResource::class) }
            .flatten()
            .map { it.id }
    }

    private fun getCurrentDir(): List<DirectoryResource> {
        val target = ActorManager[ActorManager.player().target] ?: return emptyList()
        return target.actorModel?.model?.getAnimationDirectories() ?: emptyList()
    }

}