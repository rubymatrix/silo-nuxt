package xim.poc.game.configuration.assetviewer

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.MenuStacks
import xim.poc.audio.SystemSound
import xim.poc.game.*
import xim.poc.ui.JobSelectUi
import xim.resource.DatId

private enum class ChangeJobType { Main, Sub }

object ChangeJobInteraction {

    fun onInteraction(npcId: ActorId) {
        ActorManager[npcId]?.onReadyToDraw { it.playRoutine(DatId("job0")) }
        val state = ChangeJobInteractionState(npcId)
        state.push()
    }

}

private class ChangeJobInteractionState(val npcId: ActorId) {

    private val changeJobTypeContext: UiState
    private val changeJobContext: UiState

    private var changeJobType: ChangeJobType = ChangeJobType.Main

    init {
        changeJobContext = UiState(
            focusMenu = "menu    jobcselu",
            menuStacks = MenuStacks.LogStack,
            additionalDraw = { JobSelectUi.draw(it) }
        ) {
            if (!isCloseToNpc()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else if (UiStateHelper.isEnterPressed()) {
                val job = Job.byIndex(it.cursorPos + 1) ?: return@UiState true

                when (changeJobType) {
                    ChangeJobType.Main -> GameClient.submitChangeJob(ActorStateManager.playerId, mainJob = job)
                    ChangeJobType.Sub -> GameClient.submitChangeJob(ActorStateManager.playerId, subJob = job)
                }

                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        changeJobTypeContext = UiState(
            focusMenu = "menu    jobchang",
            menuStacks = MenuStacks.LogStack,
        ) {
            if (!isCloseToNpc()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else if (UiStateHelper.isEnterPressed()) {
                changeJobType = if (it.cursorPos == 0) { ChangeJobType.Main } else { ChangeJobType.Sub }
                UiStateHelper.pushState(changeJobContext, SystemSound.MenuSelect)
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }
    }

    fun push() {
        UiStateHelper.pushState(changeJobTypeContext)
    }

    private fun isCloseToNpc(): Boolean {
        val sourceState = ActorStateManager.player()
        val targetState = ActorStateManager[npcId] ?: return false
        return sourceState.getTargetingDistance(targetState) < 5f
    }

}