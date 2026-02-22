package xim.poc.game

import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.*
import xim.poc.audio.AudioManager
import xim.poc.audio.SystemSound
import xim.poc.browser.GameKey
import xim.poc.browser.WheelDirection
import xim.poc.game.actor.components.getRangedAttackItems
import xim.poc.game.configuration.EventScriptRunner
import xim.poc.game.configuration.SkillRangeInfo
import xim.poc.game.configuration.constants.rangedAttack
import xim.poc.gl.Color
import xim.poc.tools.UiPosition
import xim.poc.tools.ZoneChanger
import xim.poc.ui.*
import xim.poc.ui.EquipScreenUiState.equipContext
import xim.resource.*
import xim.util.Fps.millisToFrames
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

enum class ParentRelative {
    RightOfParent,
    TopOfParent,
}

class HandlerContext(val local: UiState, val cursorPos: Int)

data class UiState(
    val additionalDraw: ((UiState) -> Unit)? = null,
    val focusMenu: String? = null,
    val dynamicFocusMenu: (() -> String?)? = null,
    val menuStacks: MenuStacks? = null,
    val appendType: AppendType = AppendType.Append,
    val drawParent: Boolean = false,
    val drawBorder: Boolean = true,
    val childStates: (() -> List<UiState>)? = null,
    val parentRelative: ParentRelative? = null,
    val subTargetSelect: Boolean = false,
    val onPushed: (() -> Boolean)? = null,
    val onPopped: (() -> Unit)? = null,
    val scrollSettings: ScrollSettings? = null,
    val resetCursorIndexOnPush: Boolean = true,
    val defaultCursorIndex: Int? = null,
    val locksMovement: Boolean = false,
    val hideGauge: Boolean = false,
    val uiPositionKey: UiPosition? = null,
    val grabFocusOnHover: (() -> Boolean)? = null,
    val componentDisabledFn: (Int) -> Boolean = { false },
    val handler: (handlerInput: HandlerContext) -> Boolean,
) {
    var cursorIndex: Int = 0
    var latestPosition: Vector2f? = null
    var latestMenu: UiMenu? = null
    var latestParent: UiState? = null

    fun resetCursorIndex() {
        cursorIndex = 0
        scrollSettings?.lowestViewableItemIndex = 0
    }

    fun applyCursorBounds() {
        val scrollSettings = this.scrollSettings ?: return

        val totalItems = scrollSettings.numElementsProvider()

        val maxItemIndex = (totalItems - 1).coerceAtLeast(0)
        val maxCursorIndex = scrollSettings.numElementsInPage - 1

        if (scrollSettings.lowestViewableItemIndex + maxCursorIndex > maxItemIndex) {
            val sub = scrollSettings.lowestViewableItemIndex + maxCursorIndex - maxItemIndex
            scrollSettings.lowestViewableItemIndex -= sub
            scrollSettings.lowestViewableItemIndex = scrollSettings.lowestViewableItemIndex.coerceAtLeast(0)
        }

        cursorIndex = cursorIndex.coerceAtMost(min(maxItemIndex, maxCursorIndex))
    }

    fun isValidCursorPosition(cursorPos: Int): Boolean {
        val scrollSettings = this.scrollSettings ?: return true
        val totalItems = scrollSettings.numElementsProvider()
        return cursorPos < totalItems
    }

    fun getFocusMenu(): String? {
        return focusMenu ?: dynamicFocusMenu?.invoke()
    }

}

data class ScrollSettings(
    val numElementsInPage: Int,
    val numElementsProvider: () -> Int,
) {
    var lowestViewableItemIndex = 0
}

data class UiStateFrame(
    val state: UiState,
    val parent: UiStateFrame?,
)

class PendingAction(
    val targetFilter: TargetFilter,
    val rangeInfo: SkillRangeInfo? = null,
    val execute: () -> Unit,
)

data class QueryMenuOption(val text: String, val value: Int)

class QueryMenuResponse private constructor(val pop: Boolean = false, val popAll: Boolean = false, val soundType: SystemSound? = null) {
    companion object {
        val noop = QueryMenuResponse()
        val pop = QueryMenuResponse(pop = true)
        val popAll = QueryMenuResponse(popAll = true)

        fun noop(sound: SystemSound? = null) = QueryMenuResponse(soundType = sound)
        fun pop(sound: SystemSound? = null) = QueryMenuResponse(pop = true, soundType = sound)
    }
}

object UiStateHelper {

    var defaultContext: UiState
    lateinit var actionContext: UiState
    lateinit var magicTypeSelectContext: UiState
    lateinit var magicSelectContext: UiState
    lateinit var subTargetContext: UiState
    lateinit var menuWindowContext: UiState
    lateinit var inventoryContext: UiState
    lateinit var itemUseContext: UiState
    lateinit var itemSortContext: UiState
    lateinit var pendingYesNoContext: UiState

    lateinit var returnToHomePointContext: UiState
    lateinit var statusWindowContext: UiState

    lateinit var abilityTypeContext: UiState

    lateinit var chatLogContext: UiState
    lateinit var expandedChatLogContext: UiState
    lateinit var buffContext: UiState

    lateinit var battleGaugeUi: UiState

    private var pendingAction: PendingAction? = null
    private var pendingYesNoCommand: (() -> Unit)? = null
    private var magicFilter: MagicType = MagicType.None
    private var itemFilter: ItemListType? = null
    private val subTargetAoeIndicator = AoeIndicator()

    private val jobAbilitySelectMenu = HashMap<AbilityType, UiState>()

    private val submittedFrames = ArrayDeque<UiState>()

    init {
        defaultContext = UiState(
            dynamicFocusMenu = { getDefaultContextMenu() },
            additionalDraw = { drawDefaultContextMenu(it) }
        ) { handleDefaultContextMenu(it.cursorPos) }

        actionContext = UiState(
            dynamicFocusMenu = { getActionContextMenu() },
            menuStacks = MenuStacks.LogStack,
        ) { handleActionContextMenu(it.cursorPos) }

        abilityTypeContext = UiState(
            focusMenu = "menu    abiselec",
            menuStacks = MenuStacks.LogStack,
        ) {
            if (it.cursorPos == 0 && isEnterPressed()) {
                pushState(makeAbilityMenu(AbilityType.JobAbility), SystemSound.MenuSelect)
                true
            } else if (it.cursorPos == 1 && isEnterPressed()) {
                pushState(makeAbilityMenu(AbilityType.WeaponSkill), SystemSound.MenuSelect)
                true
            } else if (it.cursorPos == 2 && isEnterPressed()) {
                val canUseRangedAttack = ActorStateManager.player().getRangedAttackItems() != null
                if (!canUseRangedAttack) {
                    AudioManager.playSystemSoundEffect(SystemSound.Invalid)
                    return@UiState false
                }

                if (!GameEngine.canBeginSkill(ActorStateManager.playerId, rangedAttack)) {
                    AudioManager.playSystemSoundEffect(SystemSound.Invalid)
                    return@UiState false
                }

                pendingAction = PendingAction(
                    targetFilter = GameEngine.getSkillTargetFilter(rangedAttack),
                    rangeInfo = GameEngine.getRangedAttackRangeInfo(ActorStateManager.player()),
                    execute = {
                        val player = ActorManager.player()
                        GameClient.submitStartRangedAttack(player.id, player.subTarget!!)
                    }
                )
                pushState(subTargetContext, SystemSound.MenuSelect)
                true
            } else if (it.cursorPos == 3 && isEnterPressed()) {
                pushState(makeAbilityMenu(AbilityType.PetCommand), SystemSound.MenuSelect)
                true
            } else if (it.cursorPos == 4 && isEnterPressed()) {
                MountSelectUi.push()
                true
            } else if (isEscPressed()) {
                popState()
                true
            } else {
                false
            }
        }

        magicTypeSelectContext = UiState(
            focusMenu = "menu    magselec",
            menuStacks = MenuStacks.LogStack,
            drawParent = true,
        ) { 
            if (keyPressed(GameKey.UiLeft) || isEscPressed()) {
                popState()
                true
            } else if (isEnterPressed()) {
                val newMagicFilter = when(it.cursorPos) {
                    0 -> MagicType.WhiteMagic
                    1 -> MagicType.BlackMagic
                    2 -> MagicType.Songs
                    3 -> MagicType.Ninjutsu
                    4 -> MagicType.Summoning
                    5 -> MagicType.BlueMagic
                    6 -> MagicType.Geomancy
                    else -> throw IllegalStateException()
                }

                changeMagicFilter(newMagicFilter)
                pushState(magicSelectContext, SystemSound.MenuSelect)
                true
            } else {
                false
            }
        }

        magicSelectContext = UiState(
            additionalDraw = { SpellSelectUi.drawSpells(it, magicFilter); SpellSelectUi.drawRecast(currentItemIndex(), magicFilter) },
            focusMenu = "menu    magic   ",
            menuStacks = MenuStacks.LogStack,
            appendType = AppendType.StackAndAppend,
            resetCursorIndexOnPush = false,
            scrollSettings = ScrollSettings(numElementsInPage = 12) { SpellSelectUi.getSpellItems(magicFilter).size },
        ) { 
            if (isEnterPressed()) {
                val player = ActorManager.player()

                val spellIndex = currentItemIndex()
                val skill = SpellSelectUi.getSelectedSpell(spellIndex, magicFilter) ?: return@UiState true

                if (!GameEngine.canBeginSkill(player.id, skill)) {
                    AudioManager.playSystemSoundEffect(SystemSound.Invalid)
                    return@UiState true
                }

                pendingAction = PendingAction(
                    targetFilter = GameEngine.getSkillTargetFilter(skill),
                    rangeInfo = GameEngine.getRangeInfo(ActorStateManager.player(), skill),
                    execute = { SpellSelectUi.castSelectedSpell(spellIndex, ActorManager.player().subTarget!!, magicFilter) }
                )

                pushState(subTargetContext, SystemSound.MenuSelect)
                true
            } else if (isEscPressed()) {
                popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        subTargetContext = UiState(
            subTargetSelect = true,
            onPushed = {
                val selectedTarget = PlayerTargetSelector.onStartSelectingSubTarget(pendingAction!!.targetFilter)
                if (selectedTarget == null) {
                    popState(SystemSound.Invalid)
                    false
                } else {
                    if (getPendingAoeInfo() != null) { updateSubTargetAoeIndicator(); subTargetAoeIndicator.show() }
                    true
                }
            },
            onPopped = {
                subTargetAoeIndicator.hide()
                PlayerTargetSelector.onFinishedSelectingSubTarget()
            }
        ) { 
            val pending = pendingAction!!
            updateSubTargetAoeIndicator()

            if (!PlayerTargetSelector.updateSubTarget(pending.targetFilter)) {
                popState(SystemSound.MenuClose)
                true
            } else if (isEnterPressed()) {
                if (isSubTargetTooFar()) {
                    AudioManager.playSystemSoundEffect(SystemSound.Invalid)
                } else {
                    pending.execute()
                    popState(SystemSound.MenuClose)
                    popState()
                }
                true
            } else if (keyPressed(GameKey.TargetCycle)) {
                PlayerTargetSelector.targetCycle(subTarget = true, targetFilter = pending.targetFilter)
                updateSubTargetAoeIndicator()
                true
            } else if (targetDirectly()){
                true
            } else if (isEscPressed()) {
                popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        menuWindowContext = UiState(
            focusMenu = "menu    menuwind",
            resetCursorIndexOnPush = false,
            hideGauge = true,
            menuStacks = MenuStacks.PartyStack,
            appendType = AppendType.HorizontalOnly,
        ) { 
            if (it.cursorPos == 0 && isEnterPressed()) {
                pushState(statusWindowContext, SystemSound.MenuSelect)
                true
            } else if (it.cursorPos == 1 && isEnterPressed()) {
                pushState(equipContext, SystemSound.MenuSelect)
                true
            } else if (it.cursorPos == 3 && isEnterPressed()) {
                pushState(inventoryContext, SystemSound.MenuSelect)
                true
            } else if (it.cursorPos == 11 && isEnterPressed()) {
                MapDrawer.toggle()
                AudioManager.playSystemSoundEffect(SystemSound.MenuSelect)
                true
            } else if (isEscPressed()) {
                popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        inventoryContext = UiState(
            additionalDraw = { InventoryUi.drawInventoryItems(it, itemTypeFilter = itemFilter) },
            focusMenu = "menu    inventor",
            resetCursorIndexOnPush = false,
            scrollSettings = ScrollSettings(numElementsInPage = 10) { InventoryUi.getItems(itemTypeFilter = itemFilter).size },
            childStates = { listOf(itemSortContext) },
            hideGauge = true,
            onPopped = { itemFilter = null },
        ) {
            if (isEscPressed() || keyPressed(GameKey.OpenInventoryMenu)) {
                popState(SystemSound.MenuClose)
                true
            } else if (isEnterPressed()) {
                InventoryUi.getSelectedItem(it.local, itemTypeFilter = itemFilter) ?: return@UiState true
                pushState(itemUseContext, SystemSound.MenuSelect)
                true
            } else if (keyPressed(GameKey.UiContext)) {
                pushState(itemSortContext, SystemSound.MenuSelect)
                true
            } else {
                false
            }
        }

        itemUseContext = UiState(
            focusMenu = "menu    iuse    ",
            drawParent = true,
            menuStacks = MenuStacks.PartyStack,
            appendType = AppendType.HorizontalOnly,
            componentDisabledFn = {
                val currentItem = InventoryUi.getSelectedItem(inventoryContext, itemTypeFilter = itemFilter)
                val usable = currentItem?.info()?.usableItemInfo != null
                if (it == 0) { !usable } else { false }
            },
        ) { 
            if (isEscPressed()) {
                popState(SystemSound.MenuClose)
                true
            } else if (it.cursorPos == 0 && isEnterPressed()) {
                val currentItem = InventoryUi.getSelectedItem(inventoryContext, itemTypeFilter = itemFilter)
                val skill = currentItem?.skill()

                if (currentItem == null || skill == null) {
                    popState(SystemSound.Invalid)
                    return@UiState true
                }

                if (currentItem.info().itemType == InventoryItemType.Crystal) {
                    GameState.getGameMode().onSelectedCrystal(ActorStateManager.playerId, currentItem)
                    return@UiState true
                }

                pendingAction = PendingAction(
                    targetFilter = GameEngine.getSkillTargetFilter(skill),
                    rangeInfo = GameEngine.getRangeInfo(ActorStateManager.player(), skill),
                    execute = { InventoryUi.useSelectedInventoryItem(currentItem, ActorManager.player().subTarget!!) }
                )

                pushState(subTargetContext, SystemSound.MenuSelect)
                true
            } else if (it.cursorPos == 1 && isEnterPressed()) {
                val currentItem = InventoryUi.getSelectedItem(inventoryContext, itemTypeFilter = itemFilter)!!
                pendingYesNoCommand = { GameClient.submitDiscardItem(ActorStateManager.playerId, currentItem); popState() }
                pushState(pendingYesNoContext, SystemSound.MenuSelect)
                true
            } else {
                false
            }
        }

        itemSortContext = UiState(
            focusMenu = "menu    mgcsortw",
            drawParent = true,
            menuStacks = MenuStacks.PartyStack,
            appendType = AppendType.HorizontalOnly,
            grabFocusOnHover = { true },
        ) { 
            if (isEscPressed()) {
                popState(SystemSound.MenuClose)
                true
            } else if (it.cursorPos == 0 && isEnterPressed()) {
                pendingYesNoCommand = { GameClient.submitInventorySort(ActorStateManager.playerId) }
                pushState(pendingYesNoContext, SystemSound.MenuSelect)
                true
            } else {
                false
            }
        }

        pendingYesNoContext = UiState(
            focusMenu = "menu    sortyn  ",
            drawParent = true,
            menuStacks = MenuStacks.PartyStack,
            appendType = AppendType.HorizontalOnly,
            defaultCursorIndex = 1,
        ) { 
            if (isEscPressed()) {
                pendingYesNoCommand = null
                popState(SystemSound.MenuClose)
                true
            } else if (it.cursorPos == 0 && isEnterPressed()) {
                pendingYesNoCommand?.invoke()
                pendingYesNoCommand = null
                popState(SystemSound.MenuSelect)
                true
            } else if (it.cursorPos == 1 && isEnterPressed()) {
                pendingYesNoCommand = null
                popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        returnToHomePointContext = yesNoMenu("Return to Home Point?",
            onYes = { GameState.getGameMode().onReturnToHomePoint(ActorStateManager.player()); popState() },
            onNo = { popState(SystemSound.MenuClose) }
        )

        statusWindowContext = UiState(
            focusMenu = "menu    statuswi",
            drawParent = true,
            additionalDraw = { StatusWindowUi.draw(it) },
            uiPositionKey = UiPosition.Equipment,
        ) { 
            if (isEscPressed()) {
                popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        ChatLog.registerFakeMenu()
        chatLogContext = UiState(
            focusMenu = ChatLog.fakeUiMenuName,
            menuStacks = MenuStacks.LogStack,
            appendType = AppendType.StackAndAppend,
            additionalDraw = { ChatLog.draw(it) },
        ) {
            if (isEnterPressed()) {
                pushState(expandedChatLogContext, SystemSound.MenuSelect)
                true
            } else if (keyPressed(GameKey.UiContext)) {
                popState()
                pushState(buffContext)
                true
            } else if (isEscPressed()) {
                popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        expandedChatLogContext = UiState(
            onPushed = { ChatLog.toggleExpand(true); true },
            onPopped = { ChatLog.toggleExpand(false) },
            drawParent = true
        ) { 
            if (isEscPressed()) {
                popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }

        buffContext = UiState(
            focusMenu = "menu    buff    ",
            onPushed = { if (StatusEffectUi.isValid()) { true } else { popState(); false } },
            drawBorder = false,
            uiPositionKey = UiPosition.StatusBar,
            additionalDraw = { StatusEffectUi.draw(it) }
        ) {
            if (ActorStateManager.player().getStatusEffects().isEmpty()) {
                popState()
                true
            } else if (keyPressed(GameKey.UiContext)) {
                popState()
                true
            } else if (isEnterPressed()){
                StatusEffectUi.expireCurrentBuff(buffContext)
                true
            } else if (isEscPressed()) {
                popState(SystemSound.MenuClose)
                true
            } else {
                StatusEffectUi.navigateCursor(buffContext)
            }
        }

        battleGaugeUi = UiState(
            focusMenu = "menu    gaugewin",
            uiPositionKey = UiPosition.StatusBar,
            additionalDraw = { BattleGaugeUi.draw(it) },
        ) {  false }

    }

    private var currentState = UiStateFrame(defaultContext, null)
    private var inputThrottler = 0f

    fun update(elapsedFrames: Float) {
        inputThrottler -= elapsedFrames
        if (inputThrottler > 0f) {
            return
        }

        val current = current()
        current.state.applyCursorBounds()

        val handlerContext = HandlerContext(current.state, current.state.cursorIndex)
        if (current.state.handler.invoke(handlerContext)) {
            inputThrottler = millisToFrames(100)
        } else if (moveCursor()) {
            inputThrottler = millisToFrames(100)
        } else if (advanceItemDescriptionPage()) {
            inputThrottler = millisToFrames(100)
        }
    }

    fun draw() {
        if (shouldShowBattleGauge()) { drawFrame(UiStateFrame(battleGaugeUi, parent = null), focus = false) }
        if (!isInCurrentFrame(chatLogContext)) { drawFrame(UiStateFrame(chatLogContext, parent = null), focus = false) }
        if (!isInCurrentFrame(buffContext)) { drawFrame(UiStateFrame(buffContext, parent = null), focus = false) }
        if (ActorStateManager.player().isFishing()) { FishHppUi.draw() }
        drawFrame(current(), true)

        submittedFrames.forEach { drawFrame(UiStateFrame(it, null), focus = false) }
        submittedFrames.clear()
    }

    fun isSubTargetMode(): Boolean {
        return current().state.subTargetSelect
    }

    fun submitInfoFrame(uiState: UiState) {
        submittedFrames += uiState
    }

    private fun drawFrame(frame: UiStateFrame, focus: Boolean, skipParents: Boolean = false) {
        if (!skipParents && frame.state.drawParent && frame.parent != null) {
            frame.state.latestParent = frame.parent.state
            drawFrame(frame.parent, false)
        }

        if (focus && frame.state.subTargetSelect) {
            val subTarget = ActorManager[ActorManager.player().subTarget]
            if (subTarget != null) { PartyUi.drawTargetPointer(subTarget, getSubTargetColorMask(subTarget)) }
        }

        val cursorPos = if (focus) { frame.state.cursorIndex } else { -1 }

        val offsetOverride = if (frame.state.parentRelative != null) {
            val parent = frame.parent!!
            val parentLatestPos = parent.state.latestPosition!!
            val parentLatestSettings = parent.state.latestMenu!!

            when(frame.state.parentRelative) {
                ParentRelative.RightOfParent -> Vector2f(parentLatestPos.x + parentLatestSettings.frame.size.x + 2f, parentLatestPos.y)
                ParentRelative.TopOfParent -> Vector2f(parentLatestPos.x, parentLatestPos.y + parentLatestSettings.frame.size.y + 2f)
            }
        } else {
            null
        }

        val focusMenu = frame.state.getFocusMenu()
        if (focusMenu != null) {
            frame.state.latestMenu = UiResourceManager.getMenu(focusMenu)?.uiMenu
            frame.state.latestPosition = UiElementHelper.drawMenu(
                menuName = focusMenu,
                cursorIndex = cursorPos,
                offsetOverride = offsetOverride,
                menuStacks = frame.state.menuStacks,
                appendType = frame.state.appendType,
                scrollSettings = frame.state.scrollSettings,
                drawFrame = frame.state.drawBorder,
                uiPosition = frame.state.uiPositionKey,
                componentStateFn = componentStateFn(frame),
            )
        }

        frame.state.additionalDraw?.invoke(frame.state)

        if (focus) {
            registerButtonHoverListeners(frame)
        }

        if (frame.state.grabFocusOnHover?.invoke() == true && !focus && !isInCurrentFrame(frame.state)) {
            registerHoverFocusListener(frame)
        }

        if (focus && frame.state.scrollSettings != null) {
            registerScrollListener(frame)
        }

        if (focus) {
            val children = frame.state.childStates?.invoke() ?: emptyList()
            for (child in children) { drawFrame(UiStateFrame(child, frame), focus = false, skipParents = true) }
        }
    }

    private fun componentStateFn(frame: UiStateFrame): (Int) -> UiComponentType {
        return {
            if (frame.state.componentDisabledFn.invoke(it)) {
                UiComponentType.Disabled
            } else if (frame.state.cursorIndex == it) {
                UiComponentType.Selected
            } else {
                UiComponentType.Default
            }
        }
    }

    fun movementLocked(): Boolean {
        return current().state.locksMovement
    }

    fun cursorLocked(): Boolean {
        return current().state.getFocusMenu() != null
    }

    fun clear(systemSound: SystemSound? = null) {
        while (currentState.parent != null) { popState() }
        if (systemSound != null) { AudioManager.playSystemSoundEffect(systemSound) }
    }

    fun isFocus(uiState: UiState) : Boolean {
        return current().state == uiState
    }

    fun popActionContext() {
        if (current().state == actionContext) { popState() }
    }

    private fun current(): UiStateFrame {
        return currentState
    }

    fun pushState(newState: UiState, systemSound: SystemSound? = null) {
        currentState = UiStateFrame(newState, current())
        if (newState.resetCursorIndexOnPush) { newState.resetCursorIndex() } else { newState.applyCursorBounds() }

        if (newState.defaultCursorIndex != null) { newState.cursorIndex = newState.defaultCursorIndex }

        val success = newState.onPushed?.invoke() ?: true
        if (success && systemSound != null) { AudioManager.playSystemSoundEffect(systemSound) }
    }

    fun popState(systemSound: SystemSound? = null) {
        currentState.state.onPopped?.invoke()
        currentState = currentState.parent!!
        if (systemSound != null) { AudioManager.playSystemSoundEffect(systemSound) }
    }

    fun isEnterPressed() : Boolean {
        return keyPressed(GameKey.UiEnter)
    }

    fun isEscPressed(): Boolean {
        return keyPressed(GameKey.UiExit)
    }

    fun keyPressed(key: GameKey): Boolean {
        return MainTool.platformDependencies.keyboard.isKeyPressed(key)
                || getFakeTouchPresses().contains(key)
    }

    fun keyPressedOrRepeated(key: GameKey): Boolean {
        return MainTool.platformDependencies.keyboard.isKeyPressedOrRepeated(key)
                || getFakeTouchPresses().contains(key)
    }

    fun getDirectionalInput(): UiMenuCursorKey? {
        return if (keyPressedOrRepeated(GameKey.UiLeft)) { UiMenuCursorKey.Left }
        else if (keyPressedOrRepeated(GameKey.UiRight)) { UiMenuCursorKey.Right }
        else if (keyPressedOrRepeated(GameKey.UiUp)) { UiMenuCursorKey.Up }
        else if (keyPressedOrRepeated(GameKey.UiDown)) { UiMenuCursorKey.Down}
        else { null }
    }

    private fun moveCursor(): Boolean {
        val key = getDirectionalInput() ?: return false

        val current = current().state
        val startingIndex = current.cursorIndex

        val focusMenuName = current.getFocusMenu() ?: return false
        val menu = UiResourceManager.getMenu(focusMenuName)?.uiMenu ?: return false

        val scrollSettings = current.scrollSettings
        val startingView = scrollSettings?.lowestViewableItemIndex

        if (scrollSettings != null) {
            val numTotalItems = scrollSettings.numElementsProvider.invoke()
            val itemIndex = scrollSettings.lowestViewableItemIndex + current.cursorIndex

            val maxItemIndex = (numTotalItems - 1).coerceAtLeast(0)
            val highestViewableItemIndex = scrollSettings.lowestViewableItemIndex + min(maxItemIndex, scrollSettings.numElementsInPage - 1)

            val maxCursorIndex = scrollSettings.numElementsInPage - 1

            if (itemIndex == 0 && key == UiMenuCursorKey.Up) {
                current.cursorIndex = min(maxItemIndex, maxCursorIndex)
                scrollSettings.lowestViewableItemIndex = max(0, numTotalItems - scrollSettings.numElementsInPage)
            } else if (current.cursorIndex == 0 && key == UiMenuCursorKey.Up) {
                scrollSettings.lowestViewableItemIndex -= 1
            } else if (itemIndex == maxItemIndex && key == UiMenuCursorKey.Down) {
                current.cursorIndex = 0
                scrollSettings.lowestViewableItemIndex = 0
            } else if (current.cursorIndex == maxCursorIndex && key == UiMenuCursorKey.Down) {
                scrollSettings.lowestViewableItemIndex += 1
            } else if (key == UiMenuCursorKey.Left && scrollSettings.lowestViewableItemIndex == 0) {
                current.cursorIndex = 0
            } else if (key == UiMenuCursorKey.Left) {
                val amountToSub = min(scrollSettings.numElementsInPage, scrollSettings.lowestViewableItemIndex)
                scrollSettings.lowestViewableItemIndex -= amountToSub
            } else if (key == UiMenuCursorKey.Right && (highestViewableItemIndex == maxItemIndex || maxItemIndex < scrollSettings.numElementsInPage)) {
                current.cursorIndex = min(maxItemIndex, maxCursorIndex)
            } else if (key == UiMenuCursorKey.Right) {
                val amountToAdd = min(scrollSettings.numElementsInPage, maxItemIndex - highestViewableItemIndex)
                scrollSettings.lowestViewableItemIndex += amountToAdd
            } else {
                navigateCursor(menu, key)
            }
        } else {
            navigateCursor(menu, key)
        }

        val endingIndex = current.cursorIndex
        val endingView = scrollSettings?.lowestViewableItemIndex

        if (startingIndex != endingIndex || startingView != endingView) { AudioManager.playSystemSoundEffect(SystemSound.CursorMove) }
        return true
    }

    private fun getFakeTouchPresses(): Set<GameKey> {
        val touches = MainTool.platformDependencies.keyboard.getTouchData().filter { it.isControlTouch() }

        val gameKeys = HashSet<GameKey>()

        for (touch in touches) {
            val (dX, dY) = touch.getDeltaFromStart()

            if (dX < -0.1) { gameKeys += GameKey.UiLeft }
            else if (dX > 0.1) { gameKeys += GameKey.UiRight }
            else if (dY < -0.1) { gameKeys += GameKey.UiUp }
            else if (dY > 0.1) { gameKeys += GameKey.UiDown }
        }

        val click = MainTool.platformDependencies.keyboard.getClickEvents().lastOrNull()
        if (click != null && !click.consumed) {
            if (click.normalizedScreenPosition.x > 0.9 && click.normalizedScreenPosition.y < 0.1) {
                 gameKeys += GameKey.OpenMainMenu
            } else if (click.isLongClick()) {
                if (click.normalizedScreenPosition.x > 0.5) { gameKeys += GameKey.UiExit }
            } else if (click.rightClick) {
                if (hasActiveUi()) { gameKeys += GameKey.UiExit }
            } else if (hasActiveUi()) {
                gameKeys += GameKey.UiEnter
            }
        }

        return gameKeys
    }


    private fun navigateCursor(menu: UiMenu, key: UiMenuCursorKey) {
        if (menu.elements.isEmpty()) { return }
        val current = current()
        val currentElement = menu.elements[current.state.cursorIndex]

        val next = currentElement.next[key] ?: return
        if (next == -1) { return }

        current.state.cursorIndex = next - 1
    }

    private fun advanceItemDescriptionPage(): Boolean {
        return if (keyPressed(GameKey.OpenMainMenu)) {
            InventoryUi.advanceItemDescriptionPage()
            true
        } else {
            false
        }
    }

    fun currentItemIndex(): Int {
        val current = current()
        return current.state.cursorIndex + (current.state.scrollSettings?.lowestViewableItemIndex ?: 0)
    }

    private fun handleDefaultEnter(suppressActionMenu: Boolean = false) {
        val player = ActorStateManager.player()
        val targetState = ActorStateManager.playerTarget()

        if (targetState == null) {
            val foundTarget = PlayerTargetSelector.targetCycle()
            if (!foundTarget && !suppressActionMenu) { pushState(actionContext, SystemSound.TargetConfirm) }
            return
        }

        if (targetState.isStaticNpc()) {
            GameClient.submitInteractWithNpc(player.id, targetState.id)
        } else if (!suppressActionMenu) {
            pushState(actionContext, SystemSound.TargetConfirm)
        }
    }

    private fun getDefaultContextMenu(): String? {
        val player = ActorManager.player()

        return if (ZoneChanger.isChangingZones()) {
            null
        } else if (player.isDisplayedDead()) {
            "menu    dead    "
        } else {
            null
        }
    }

    private fun drawDefaultContextMenu(uiState: UiState) {
        if (ZoneChanger.isChangingZones()) { return }
        Compass.draw()

        val player = ActorManager.player()
        if (!player.isDisplayedDead()) { return }

        val initialTime = 60.seconds
        val time = initialTime - ActorStateManager[player.id]!!.timeSinceDeath()

        val minutes = time.inWholeMinutes
        val seconds = (time.inWholeSeconds % 60).toString().padStart(2, '0')

        UiElementHelper.drawString("${minutes}:${seconds}", offset = uiState.latestPosition!! + Vector2f(78f, 5f), Font.FontShp)
    }

    fun hasActiveUi(): Boolean {
        return current().state != defaultContext
    }

    fun getPendingActionTargetFilter(): TargetFilter? {
        if (current().state != subTargetContext) { return null }
        return pendingAction?.targetFilter
    }

    fun interactWithTarget() {
        handleDefaultEnter(suppressActionMenu = true)
    }

    private fun changeMagicFilter(magicType: MagicType) {
        if (magicFilter != magicType) { magicSelectContext.resetCursorIndex() }
        magicFilter = magicType
    }

    private fun handleDefaultContextMenu(cursorPos: Int): Boolean {
        val player = ActorManager.player()

        return if (ZoneChanger.isChangingZones() || EventScriptRunner.isRunningScript() || player.state.isFishing()) {
            true
        } else if (player.isDisplayedDead()) {
            if (isEnterPressed()) {
                pushState(returnToHomePointContext, SystemSound.MenuSelect)
                true
            } else {
                false
            }
        } else {
            if (isEnterPressed()) {
                handleDefaultEnter()
                true
            } else if (targetDirectly()){
                true
            } else if (keyPressed(GameKey.FaceTarget)) {
                GameClient.submitFacingUpdate(ActorStateManager.player(), ActorStateManager.playerTarget())
                true
            } else if (keyPressed(GameKey.TargetCycle)) {
                PlayerTargetSelector.targetCycle()
                true
            } else if (isEscPressed()) {
                PlayerTargetSelector.clearTarget()
                true
            } else if (keyPressed(GameKey.OpenMainMenu)) {
                pushState(menuWindowContext, SystemSound.MenuSelect)
                true
            } else if (keyPressed(GameKey.UiContext)){
                pushState(chatLogContext, SystemSound.TargetConfirm)
                true
            } else if (keyPressed(GameKey.OpenEquipMenu)) {
                pushState(equipContext, SystemSound.TargetConfirm)
                true
            } else if (keyPressed(GameKey.OpenInventoryMenu)) {
                pushState(inventoryContext, SystemSound.TargetConfirm)
                true
            } else {
                false
            }
        }
    }

    private fun targetDirectly(): Boolean {
        return if (keyPressed(GameKey.TargetSelf)){
            PlayerTargetSelector.targetPartyMember(0)
            true
        } else if (keyPressed(GameKey.TargetParty2)){
            PlayerTargetSelector.targetPartyMember(1)
            true
        } else if (keyPressed(GameKey.TargetParty3)){
            PlayerTargetSelector.targetPartyMember(2)
            true
        } else if (keyPressed(GameKey.TargetParty4)){
            PlayerTargetSelector.targetPartyMember(3)
            true
        } else if (keyPressed(GameKey.TargetParty5)){
            PlayerTargetSelector.targetPartyMember(4)
            true
        } else if (keyPressed(GameKey.TargetParty6)){
            PlayerTargetSelector.targetPartyMember(5)
            true
        } else {
            false
        }
    }

    private fun getActionContextMenu(): String {
        val items = getCurrentActions()
        ActionMenuBuilder.register("custom  actions ", items)
        return "custom  actions "
    }

    private fun handleActionContextMenu(cursorPos: Int): Boolean {
        val actions = getCurrentActions()
        val current = actions.getOrNull(cursorPos)

        if (current == null) {
            clear()
            return true
        }

        return if (current == ActionMenuItem.Attack && isEnterPressed()) {
            GameClient.submitPlayerEngage()
            popState(SystemSound.MenuSelect)
            true
        } else if (current == ActionMenuItem.Abilities && isEnterPressed()) {
            pushState(abilityTypeContext, SystemSound.MenuSelect)
            true
        } else if (current == ActionMenuItem.Magic && isEnterPressed()) {
            changeMagicFilter(MagicType.None)
            pushState(magicSelectContext, SystemSound.MenuSelect)
            true
        } else if (current == ActionMenuItem.Trust && isEnterPressed()) {
            changeMagicFilter(MagicType.Trust)
            pushState(magicSelectContext, SystemSound.MenuSelect)
            true
        } else if (current == ActionMenuItem.Magic && keyPressed(GameKey.UiRight)) {
            pushState(magicTypeSelectContext)
            true
        } else if (current == ActionMenuItem.Check && isEnterPressed()) {
            val player = ActorStateManager.player()
            GameState.getGameMode().onCheck(player.id, player.getTargetId())
            true
        } else if (current == ActionMenuItem.Items && isEnterPressed()) {
            itemFilter = ItemListType.UsableItem
            pushState(inventoryContext, SystemSound.MenuSelect)
            true
        } else if (current == ActionMenuItem.Release && isEnterPressed()) {
            val player = ActorStateManager.player()
            GameClient.submitReleaseTrust(player.id, player.getTargetId())
            true
        } else if (current == ActionMenuItem.Dismount && isEnterPressed()) {
            GameClient.submitDismountEvent(ActorManager.player())
            true
        } else if (current == ActionMenuItem.Disengage && isEnterPressed()) {
            GameClient.submitPlayerDisengage()
            AudioManager.playSystemSoundEffect(SystemSound.MenuSelect)
            true
        } else if (current == ActionMenuItem.Fish && isEnterPressed()) {
            GameClient.submitFishingRequest(ActorStateManager.playerId)
            AudioManager.playSystemSoundEffect(SystemSound.MenuSelect)
            popState()
            true
        } else if (keyPressed(GameKey.TargetCycle)) {
            PlayerTargetSelector.targetCycle()
            true
        } else if (isEscPressed()) {
            popState(SystemSound.MenuClose)
            true
        } else if (keyPressed(GameKey.OpenMainMenu)) {
            pushState(menuWindowContext, SystemSound.MenuSelect)
            true
        } else {
            false
        }
    }

    private fun getCurrentActions(): List<ActionMenuItem> {
        val actions = ArrayList<ActionMenuItem>()

        val player = ActorManager.player()
        val playerState = ActorStateManager.player()
        val targetState = ActorStateManager[playerState.targetState.targetId]

        if (playerState.isIdle() && targetState != null && targetState.isEnemy()) {
            actions += ActionMenuItem.Attack
        }

        actions += ActionMenuItem.Magic
        actions += ActionMenuItem.Abilities
        actions += ActionMenuItem.Trust
        actions += ActionMenuItem.Items

        if (playerState.mountedState != null) {
            actions += ActionMenuItem.Dismount
        }

        val playerParty = PartyManager[ActorStateManager.playerId]
        if (targetState != null && playerParty.contains(targetState.id) && targetState.owner == player.id) {
            actions += ActionMenuItem.Release
        }

        if (player.isDisplayEngaged() && !player.state.isOccupied()) {
            actions += ActionMenuItem.Disengage
        }

        if (player.state.isIdle() && SceneManager.getCurrentScene().canFish(playerState) && GameState.getGameMode().canFish(playerState)) {
            actions += ActionMenuItem.Fish
        }

        if (targetState != null) {
            actions += ActionMenuItem.Check
        }

        return actions
    }

    fun openQueryMode(
        prompt: String,
        options: List<QueryMenuOption>,
        closeable: Boolean = true,
        systemSound: SystemSound? = null,
        drawFn: ((QueryMenuOption) -> Unit)? = null,
        callback: (QueryMenuOption?) -> QueryMenuResponse,
    ) {
        pushState(UiState(
            focusMenu = "menu    query   ",
            locksMovement = true,
            menuStacks = MenuStacks.LogStack,
            appendType = AppendType.StackAndAppend,
            additionalDraw = { QueryUi.draw(it, prompt, options); drawFn?.invoke(options[currentItemIndex()]) },
            scrollSettings = ScrollSettings(numElementsInPage = 3) { options.size },
        ) { 
            val (response, defaultSound) = if (isEnterPressed()) {
                Pair(callback.invoke(options[currentItemIndex()]), SystemSound.MenuSelect)
            } else if (closeable && isEscPressed()) {
                Pair(callback.invoke(null), SystemSound.MenuClose)
            } else {
                Pair(null, null)
            }

            val sound = response?.soundType ?: defaultSound

            if (response != null) {
                if (response.pop) {
                    popState(sound)
                } else if (response.popAll) {
                    clear(sound)
                } else {
                    AudioManager.playSystemSoundEffect(response.soundType ?: SystemSound.MenuSelect)
                }
                true
            } else {
                false
            }
        }, systemSound = systemSound)
    }

    private fun yesNoMenu(text: String, onYes: () -> Unit, onNo: () -> Unit, closeable: Boolean = true): UiState {
        return UiState(
            additionalDraw = { UiElementHelper.drawString(text, Vector2f(8f, 8f) + it.latestPosition!!)},
            focusMenu = "menu    comyn   ",
            drawParent = true,
        ) { 
            if (it.cursorPos == 0 && isEnterPressed()) {
                onYes.invoke()
                true
            } else if (it.cursorPos == 1 && isEnterPressed()) {
                onNo.invoke()
                true
            } else if (closeable && isEscPressed()) {
                popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }
    }

    private fun makeAbilityMenu(type: AbilityType): UiState {
        return jobAbilitySelectMenu.getOrPut(type) { makeAbilityMenuInternal(type) }
    }

    private fun makeAbilityMenuInternal(type: AbilityType): UiState {
        return UiState(
            additionalDraw = { AbilitySelectUi.draw(type, it); AbilitySelectUi.drawRecast(currentItemIndex(), type) },
            focusMenu = "menu    magic   ",
            menuStacks = MenuStacks.LogStack,
            appendType = AppendType.StackAndAppend,
            resetCursorIndexOnPush = false,
            scrollSettings = ScrollSettings(numElementsInPage = 12) { AbilitySelectUi.getItems(type).size },
        ) { 
            if (isEnterPressed()) {
                val itemIndex = currentItemIndex()

                val subTypeMenu = AbilitySelectUi.getSubAbilityMenuType(type, itemIndex)
                if (subTypeMenu != null) {
                    pushState(makeAbilityMenu(subTypeMenu), SystemSound.MenuSelect)
                } else {
                    val skill = AbilitySelectUi.getSelectedAbility(type, itemIndex) ?: return@UiState true

                    if (!GameEngine.canBeginSkill(ActorStateManager.playerId, skill)) {
                        AudioManager.playSystemSoundEffect(SystemSound.Invalid)
                        return@UiState true
                    }

                    pendingAction = PendingAction(
                        targetFilter = GameEngine.getSkillTargetFilter(skill),
                        rangeInfo = GameEngine.getRangeInfo(ActorStateManager.player(), skill),
                        execute = { AbilitySelectUi.useSelectedAbility(type, itemIndex, ActorManager.player().subTarget!!) }
                    )
                    pushState(subTargetContext, SystemSound.MenuSelect)
                }

                true
            } else if (isEscPressed()) {
                popState(SystemSound.MenuClose)
                true
            } else {
                false
            }
        }
    }

    private fun isInCurrentFrame(uiState: UiState): Boolean {
        var current: UiStateFrame? = current()
        if (current?.state == uiState) { return true }

        while (current != null) {
            current = current.parent
            if (uiState == current?.state) { return true }
        }

        return false
    }

    private fun getPendingAoeInfo(): SkillRangeInfo? {
        return pendingAction?.rangeInfo
    }

    private fun updateSubTargetAoeIndicator() {
        val subTarget = ActorManager.player().subTarget
        val aoeSkillInfo = pendingAction?.rangeInfo
        if (subTarget != null && aoeSkillInfo != null) { subTargetAoeIndicator.configure(ActorStateManager.playerId, subTarget, aoeSkillInfo) }
        subTargetAoeIndicator.update()
    }

    private fun isSubTargetTooFar(): Boolean {
        val maxRange = pendingAction?.rangeInfo?.maxTargetDistance ?: return true
        val subTarget = ActorStateManager[ActorManager.player().subTarget] ?: return true
        return Vector3f.distance(ActorStateManager.player().position, subTarget.position) >= maxRange
    }

    private fun getSubTargetColorMask(subTarget: Actor): Color {
        val maxRange = pendingAction?.rangeInfo?.maxTargetDistance ?: return Color.NO_MASK
        val distance = Vector3f.distance(ActorStateManager.player().position, subTarget.getState().position)

        return if (distance < maxRange * 0.8f) {
            Color(r = 128, g = 128, b = 255, a = 255)
        } else if (distance < maxRange) {
            Color(r = 200, g = 200, b = 128, a = 255)
        } else {
            Color(r = 255, g = 128, b = 128, a = 255)
        }
    }

    private fun shouldShowBattleGauge(): Boolean {
        if (!ActorManager.player().isDisplayEngagedOrEngaging()) { return false }
        var current: UiStateFrame? = current()

        while (current != null) {
            if (current.state.hideGauge) { return false }
            current = current.parent
        }

        return true
    }

    private fun registerScrollListener(frame: UiStateFrame) {
        val position = frame.state.latestPosition ?: return
        val size = frame.state.latestMenu?.frame?.size ?: return
        val scrollSettings = frame.state.scrollSettings ?: return

        ClickHandler.registerScrollListener(position = Vector2f().copyFrom(position), size = Vector2f().copyFrom(size)) {
            when (it.direction) {
                WheelDirection.Up -> {
                    scrollSettings.lowestViewableItemIndex = (scrollSettings.lowestViewableItemIndex - 1).coerceAtLeast(0)
                }
                WheelDirection.Down -> {
                    val numItems = scrollSettings.numElementsProvider.invoke()
                    val maximumIndex = (numItems - scrollSettings.numElementsInPage).coerceAtLeast(0)
                    scrollSettings.lowestViewableItemIndex = (scrollSettings.lowestViewableItemIndex + 1).coerceAtMost(maximumIndex)
                }
            }
            true
        }
    }

    private fun registerHoverFocusListener(frame: UiStateFrame) {
        val position = frame.state.latestPosition ?: return
        val size = frame.state.latestMenu?.frame?.size ?: return

        ClickHandler.registerUiHoverListener(position = Vector2f().copyFrom(position), size = Vector2f().copyFrom(size)) {
            pushState(frame.state)
            true
        }
    }

    private fun registerButtonHoverListeners(frame: UiStateFrame) {
        val menu = frame.state.latestMenu ?: return
        val position = frame.state.latestPosition ?: return

        for (i in menu.elements.indices) {
            val element = menu.elements[i]
            if (!element.selectable) { continue }

            ClickHandler.registerUiHoverListener(position = position + element.offset, size = element.size) {
                if (it.pointerHasMoved && frame.state.cursorIndex != i && frame.state.isValidCursorPosition(i)) {
                    frame.state.cursorIndex = i
                    AudioManager.playSystemSoundEffect(SystemSound.CursorMove)
                }
                true
            }
        }
    }

}