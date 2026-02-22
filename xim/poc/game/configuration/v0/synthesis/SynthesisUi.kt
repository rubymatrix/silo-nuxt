package xim.poc.game.configuration.v0.synthesis

import xim.math.Vector2f
import xim.poc.*
import xim.poc.audio.AudioManager
import xim.poc.audio.SystemSound
import xim.poc.game.*
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.synthesis.SynthesisCompleteEvent.Companion.getMaximumCraftable
import xim.poc.ui.InventoryUi
import xim.poc.ui.QuantityInputController
import xim.resource.InventoryItemInfo
import xim.resource.InventoryItems

object SynthesisUi {

    private val synthesisRecipeContext: UiState
    private val synthesisInputContext: UiState
    private val synthesisAmountContext: UiState
    
    private val quantityInput = QuantityInputController {
        val selectedRecipe = getSelectedRecipe() ?: return@QuantityInputController 0
        getMaximumCraftable(ActorStateManager.playerId, selectedRecipe)
    }

    var synthesisType: SynthesisType = SynthesisType.Fire
        private set

    init {
        synthesisAmountContext = UiState(
            focusMenu = "menu    itemctrl",
            drawParent = true,
            parentRelative = ParentRelative.TopOfParent,
            additionalDraw = { drawItemControl(it) }
        ) {
            quantityInput.refresh()

            if (!canCraftSelectedRecipe()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else if (UiStateHelper.isEnterPressed()) {
                submitStartSynthesis(ActorStateManager.playerId, getSelectedRecipe(), quantity = quantityInput.value)
                UiStateHelper.popState(SystemSound.MenuSelect)
                true
            } else if (quantityInput.processInput()) {
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        synthesisInputContext = UiState(
            focusMenu = "menu    tskill1 ",
            drawParent = true,
            parentRelative = ParentRelative.RightOfParent,
            defaultCursorIndex = 8,
            additionalDraw = { drawRecipeInputItems(it) },
        ) {
            if (UiStateHelper.isEnterPressed() && it.cursorPos == 8) {
                if (!ActorStateManager.player().isIdle() || !canCraftSelectedRecipe()) {
                    AudioManager.playSystemSoundEffect(SystemSound.Invalid)
                } else if (canCraftMultiple()) {
                    UiStateHelper.pushState(synthesisAmountContext, SystemSound.MenuSelect)
                } else {
                    submitStartSynthesis(ActorStateManager.playerId, getSelectedRecipe(), quantity = 1)
                    AudioManager.playSystemSoundEffect(SystemSound.MenuSelect)
                }
                true
            } else if (UiStateHelper.isEnterPressed() && it.cursorPos == 9) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        synthesisRecipeContext = UiState(
            focusMenu = "menu    shop    ",
            childStates = { listOf(synthesisInputContext) },
            resetCursorIndexOnPush = false,
            hideGauge = true,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { getCurrentRecipes().size },
            additionalDraw = { drawRecipeInventory() }
        ) {
            if (UiStateHelper.isEnterPressed()) {
                UiStateHelper.pushState(synthesisInputContext)
                true
            } else if (UiStateHelper.isEscPressed()) {
                UiStateHelper.popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }
    }

    fun push(inventoryItem: InventoryItem) {
        synthesisType = SynthesisType.fromItemId(inventoryItem.id)
        UiStateHelper.pushState(synthesisRecipeContext, SystemSound.MenuSelect)
    }

    private fun getSelectedRecipe(): V0SynthesisRecipe? {
        val currentItemIndex = getSelectedRecipeIndex()
        return getCurrentRecipes().getOrNull(currentItemIndex)
    }

    private fun drawRecipeInventory() {
        val context = synthesisRecipeContext
        drawRecipeList(context)
    }

    private fun canCraftSelectedRecipe(): Boolean {
        val selectedRecipe = getSelectedRecipe() ?: return false
        return getMaximumCraftable(ActorStateManager.playerId, selectedRecipe) > 0
    }

    private fun canCraftMultiple(): Boolean {
        val selectedRecipe = getSelectedRecipe() ?: return false
        return InventoryItems[selectedRecipe.output.itemId].isStackable()
    }

    private fun drawRecipeInputItems(uiState: UiState) {
        val menu = uiState.latestMenu ?: return
        val menuOffset = uiState.latestPosition ?: return
        val recipe = getSelectedRecipe() ?: return

        recipe.input.forEachIndexed { index, input ->
            val itemInfo = InventoryItems[input]
            val offset = menuOffset + menu.elements[index].offset
            UiElementHelper.drawInventoryItemIcon(itemInfo = itemInfo, position = offset)
        }

        if (UiStateHelper.isFocus(uiState)) {
            val cursorIndex = uiState.cursorIndex

            val selectedItem = if (cursorIndex >= 8) {
                InventoryItems[recipe.output.itemId]
            } else if (cursorIndex < recipe.input.size) {
                InventoryItems[recipe.input[cursorIndex]]
            } else {
                return
            }

            drawItemPreview(selectedItem)
        }
    }

    private fun drawItemControl(uiState: UiState) {
        quantityInput.draw(uiState)
    }

    private fun drawRecipeList(uiState: UiState) {
        val stackPos = uiState.latestPosition ?: return
        val offset = Vector2f(0f, 0f)
        val menuSize = uiState.latestMenu?.frame?.size ?: return

        val recipes = getCurrentRecipes()

        val scrollSettings = uiState.scrollSettings!!
        for (i in scrollSettings.lowestViewableItemIndex until scrollSettings.lowestViewableItemIndex + scrollSettings.numElementsInPage) {
            if (i >= recipes.size) { break }

            val itemInfo = InventoryItems[recipes[i].output.itemId]

            val color = if (getMaximumCraftable(ActorStateManager.playerId, recipes[i]) > 0) {
                UiElementHelper.getStandardTextColor(1)
            } else {
                UiElementHelper.getStandardTextColor(0)
            }

            UiElementHelper.drawInventoryItemIcon(itemInfo = itemInfo, position = offset + stackPos + Vector2f(2f, 4f), scale = Vector2f(0.5f, 0.5f))
            UiElementHelper.drawString(text = itemInfo.name, offset = offset + stackPos + Vector2f(22f, 8f), color = color)

            val successRate = GameV0.getSynthesisSuccessRate(ActorStateManager.player(), recipes[i])
            val successText = "${successRate}%"
            UiElementHelper.drawString(text = successText, offset = offset + stackPos + Vector2f(menuSize.x - 14f, 8f), font = Font.FontShp, alignment = TextAlignment.Right)

            offset.y += 18f
        }

        if (UiStateHelper.isFocus(uiState)) {
            val selectedRecipe = getSelectedRecipe() ?: return
            val outputItem = InventoryItems[selectedRecipe.output.itemId]
            drawItemPreview(outputItem)
        }
    }

    private fun getSelectedRecipeIndex(): Int {
        val context = synthesisRecipeContext
        return context.cursorIndex + context.scrollSettings!!.lowestViewableItemIndex
    }

    private fun getCurrentRecipes(): List<V0SynthesisRecipe> {
        return GameV0.getKnownSynthesisRecipes(ActorStateManager.player(), synthesisType)
    }

    private fun drawItemPreview(itemInfo: InventoryItemInfo) {
        val fakeItem = GameV0.generateItem(itemInfo.itemId, quantity = 1)
        InventoryUi.drawSelectedInventoryItem(fakeItem, context = synthesisRecipeContext)
    }

    private fun submitStartSynthesis(actor: ActorId, recipe: V0SynthesisRecipe?, quantity: Int) {
        recipe ?: return
        GameEngine.submitEvent(SynthesisStartEvent(actor, recipe.getRecipeId(), quantity))
    }

}