package xim.poc.ui

import xim.math.Vector2f
import xim.math.Vector4f
import xim.poc.TextDirection
import xim.poc.UiElementHelper
import xim.poc.UiResourceManager
import xim.poc.browser.WheelDirection
import xim.poc.game.*
import xim.poc.game.actor.components.AugmentHelper
import xim.poc.game.actor.components.InventoryItem
import xim.poc.gl.ByteColor
import xim.resource.*
import xim.resource.table.StatusEffectNameTable

enum class ChatLogColor(val color: ByteColor) {
    Normal(ByteColor(0x80, 0x80, 0x80, 0x80)),
    Info(ByteColor(0x80, 0x80, 0x65, 0x80)),
    Action(ByteColor(0x80, 0x80, 0x40, 0x80)),
    SystemMessage(ByteColor(0x6B, 0x39, 0x80, 0x80)),
    Error(ByteColor(0x80, 0x46, 0x46, 0x80)),
}

private class ChatLogLine(val text: UiElement, val numLines: Int)

object ChatLog {

    private const val expansionFactor = 4
    private const val numLines = 8
    private const val lineHeight = 16f

    private const val maxWidth = 500f

    private const val innerHeight = numLines * lineHeight
    private const val height = innerHeight + 10f
    private const val expandedHeight = expansionFactor * innerHeight + 10f

    const val fakeUiMenuName = "fake chatlog menu"

    private var registered = false
    private lateinit var chatLogFrame: UiMenuElement

    private val lines = ArrayDeque<ChatLogLine>()
    private var expanded = false

    private var startingLine = 0

    operator fun invoke(line: String?, chatLogColor: ChatLogColor = ChatLogColor.Normal, actionContext: AttackContext? = null) {
        addLine(line, chatLogColor, actionContext)
    }

    fun addLine(line: String?, chatLogColor: ChatLogColor = ChatLogColor.Normal, actionContext: AttackContext? = null) {
        line ?: return

        val prefixedLine = applyActionContextPrefix(line, actionContext)

        val formatted = UiElementHelper.formatString(prefixedLine, maxWidth = maxWidth.toInt() - 8, textDirection = TextDirection.BottomToTop, color = chatLogColor.color) ?: return
        val element = UiElementHelper.toUiElement(formatted.characters)

        lines.addFirst(ChatLogLine(element, formatted.numLines))
        while (lines.size > 80) { lines.removeLast() }

        startingLine = 0
    }

    fun skillChain(line: String, skillChainStep: SkillChainStep, chatLogColor: ChatLogColor = ChatLogColor.Normal) {
        val prefix = "[${skillChainStep.attribute.name}][Step:${skillChainStep.step}]"
        addLine(line = "${ShiftJis.colorInfo}${prefix}${ShiftJis.colorClear} $line", chatLogColor = chatLogColor)
    }

    fun statusEffectLost(actorName: String, statusEffect: StatusEffect) {
        val descriptions = StatusEffectNameTable[statusEffect.id] ?: return

        if (descriptions.first().first().isUpperCase()) {
            addLine("$actorName's ${descriptions[0]} effect wears off.")
        } else {
            addLine("$actorName is no longer ${descriptions[1]}.")
        }
    }

    fun statusEffectGained(actorName: String, statusEffect: StatusEffect, actionContext: AttackContext?) {
        val descriptions = StatusEffectNameTable[statusEffect.id] ?: return

        if (descriptions.first().first().isUpperCase()) {
            addLine("$actorName gains the effect of ${descriptions[0]}.", actionContext = actionContext)
        } else {
            addLine("$actorName is ${descriptions[1]}.", actionContext = actionContext)
        }
    }

    fun statusEffectResist(actorName: String, statusEffect: StatusEffect, actionContext: AttackContext?) {
        val descriptions = StatusEffectNameTable[statusEffect.id] ?: return

        if (descriptions.first().first().isUpperCase()) {
            addLine("$actorName resists ${descriptions[0]}!", actionContext = actionContext, chatLogColor = ChatLogColor.Info)
        } else {
            addLine("$actorName resists being ${descriptions[1]}!", actionContext = actionContext, chatLogColor = ChatLogColor.Info)
        }
    }

    fun paralyzed(actorName: String) {
        addLine("$actorName is paralyzed.", ChatLogColor.Action)
    }

    fun toggleExpand(state: Boolean) {
        expanded = state
        chatLogFrame.size.y = getHeight()
    }

    fun draw(uiState: UiState) {
        val logWindowPos = uiState.latestPosition ?: return

        val currentHeight = getHeight()
        val currentNumLines = getNumLines()

        val borderSize = Vector2f(8f, 8f)
        val lineOffset = Vector2f(0f, currentHeight - borderSize.y - lineHeight)

        val clipSize = Vector4f(logWindowPos.x, logWindowPos.y + borderSize.y, maxWidth, currentHeight - borderSize.y)
        registerScrollListener(clipSize)

        var totalLines = 0
        for (i in startingLine until lines.size) {
            val line = lines.getOrNull(i) ?: break
            val offset = logWindowPos + borderSize + lineOffset

            UiElementHelper.drawUiElement(line.text, position = offset, clipSize = clipSize)

            lineOffset.y -= (lineHeight * line.numLines)

            totalLines += line.numLines
            if (totalLines >= currentNumLines) { break }
        }
    }

    fun registerFakeMenu() {
        if (registered) { return }
        registered = true

        val originalMenu = UiResourceManager.getMenu("menu    log8    ") ?: throw IllegalStateException("Couldn't find 'menu    log8    '")
        val fakeMenu = UiMenu(fakeUiMenuName, originalMenu.uiMenu.frame.deepCopy(), emptyList())

        chatLogFrame = fakeMenu.frame
        chatLogFrame.size.x = maxWidth
        chatLogFrame.size.y = getHeight()

        UiResourceManager.register(UiMenuResource(DatId.zero, fakeMenu))
    }

    fun synthesisItemLoss(actorState: ActorState, itemInfo: InventoryItemInfo, quantity: Int) {
        addLine("${actorState.name} lost ${getItemString(itemInfo, quantity)}.", ChatLogColor.Info)
    }

    fun gainedItem(actorState: ActorState, inventoryItem: InventoryItem, displayQuantity: Int) {
        addLine("${actorState.name} obtains ${getItemString(inventoryItem, displayQuantity)}.", ChatLogColor.Info)
    }

    private fun getItemString(inventoryItem: InventoryItem, displayQuantity: Int): String {
        val info = inventoryItem.info()
        val logName = "$displayQuantity ${info.logName(displayQuantity)}"

        val color = if (inventoryItem.temporary) {
            ShiftJis.colorTemporary
        } else {
            AugmentHelper.getQualityColorDisplay(inventoryItem)
        }

        return "${color}${logName}${ShiftJis.colorClear}"
    }

    fun gainedItem(actorState: ActorState, itemInfo: InventoryItemInfo, displayQuantity: Int) {
        addLine("${actorState.name} obtains ${getItemString(itemInfo, displayQuantity)}.", ChatLogColor.Info)
    }

    private fun getItemString(info: InventoryItemInfo, displayQuantity: Int): String {
        val logName = "$displayQuantity ${info.logName(displayQuantity)}"
        return "${ShiftJis.colorItem}${logName}${ShiftJis.colorClear}"
    }

    private fun getExpansionFactor(): Int {
        return if (expanded) { expansionFactor } else { 1 }
    }

    private fun getHeight(): Float {
        return if (expanded) { expandedHeight } else { height }
    }

    private fun getNumLines(): Int {
        return numLines * getExpansionFactor()
    }

    private fun applyActionContextPrefix(line: String, actionContext: AttackContext?): String {
        actionContext ?: return line

        val prefix = StringBuilder()

        val skillName = actionContext.skill?.let { GameEngine.getSkillLogName(it) }
        if (skillName != null) { prefix.append("[$skillName]") }

        if (actionContext.magicBurst) { prefix.append("[Magic Burst]") }
        if (actionContext.guarded()) { prefix.append("[Guard]") }
        if (actionContext.countered()) { prefix.append("[Counter]") }

        if (prefix.isBlank()) { return line }

        return "${ShiftJis.colorInfo}$prefix${ShiftJis.colorClear} $line"
    }

    private fun registerScrollListener(clip: Vector4f) {
        ClickHandler.registerScrollListener(position = Vector2f(clip.x, clip.y), size = Vector2f(clip.z, clip.w)) {
            startingLine = when (it.direction) {
                WheelDirection.Up -> (startingLine + 1).coerceAtMost(lines.size - 1)
                WheelDirection.Down -> (startingLine - 1).coerceAtLeast(0)
            }
            true
        }
    }

}