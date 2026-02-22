package xim.poc

import xim.math.Vector2f
import xim.math.Vector4f
import xim.poc.UiElementHelper.offsetScaling
import xim.poc.browser.DatLoader
import xim.poc.game.CurrencyType
import xim.poc.game.ScrollSettings
import xim.poc.game.StatusEffectState
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.configuration.constants.AbilitySkillId
import xim.poc.gl.BlendFunc
import xim.poc.gl.ByteColor
import xim.poc.gl.Color
import xim.poc.gl.DrawXimUiCommand
import xim.poc.tools.UiPosition
import xim.poc.tools.UiPositionTool
import xim.poc.ui.AbilitySelectUi
import xim.poc.ui.ChatLogColor
import xim.poc.ui.FontMojiHelper
import xim.poc.ui.InventoryItemBorders
import xim.resource.*
import xim.resource.table.StatusEffectHelper
import xim.util.Fps.framesToSeconds
import xim.util.OnceLogger
import kotlin.math.floor
import kotlin.math.max

class FontChar(val char: Char, val element: UiElement?, val offset: Vector2f = Vector2f(0f, 0f), val colorOverride: ByteColor? = null, val clearColor: Boolean = false)
class PositionedCharacter(val fontChar: FontChar, val position: Vector2f, val color: ByteColor)

class FormattedString(val numLines: Int, val characters: List<PositionedCharacter>, val width: Float)

enum class TextAlignment {
    Left,
    Center,
    Right
}

enum class TextDirection {
    TopToBottom,
    BottomToTop,
}

enum class AppendType {
    StackAndAppend,
    Append,
    None,
    HorizontalOnly,
}

enum class MenuStacks(val menuStack: MenuStack) {
    PartyStack(MenuStack(Vector2f(290f, 580f), Vector2f())),
    LogStack(MenuStack(Vector2f(0f, 580f), Vector2f())),
}

enum class Font(val elementName: String) {
    FontMoji("font    moji    "),
    FontShp("font    fontshp "),
    FontFont("font    font    ")
}

class MenuStack(private val basePosition: Vector2f, val offset: Vector2f) {

    val currentPosition = Vector2f()

    init { reset() }

    fun reset() {
        currentPosition.copyFrom((basePosition + offset).scale(offsetScaling))
    }

    fun appendAndGetPosition(appendType: AppendType, menu: UiMenu): Vector2f {
        val heightDelta = menu.frame.size.y + 1.5f * UiElementHelper.globalUiScale.y

        return when (appendType) {
            AppendType.StackAndAppend -> {
                currentPosition.y -= heightDelta
                currentPosition
            }
            AppendType.Append -> {
                currentPosition - Vector2f(0f, heightDelta)
            }
            AppendType.None -> {
                currentPosition
            }
            AppendType.HorizontalOnly -> {
                Vector2f(currentPosition.x, menu.frame.offset.y)
            }
        }
    }

}

fun interface UiDrawCommand {
    fun draw()
}

object UiElementHelper {

    val offsetScaling = Vector2f(1f, 1f)
    val globalUiScale = Vector2f(1f, 1f)

    private var uiFrameCounter = 0f
    private var uiFrame = 0

    private val standardColors by lazy { fetchStandardColors() }

    private val enqueuedDrawCommands = ArrayList<UiDrawCommand>()

    fun update(elapsedFrames: Float) {
        uiFrameCounter += elapsedFrames
        if (uiFrameCounter > 8f) { uiFrameCounter -= 8f; uiFrame += 1 }

        MenuStacks.values().forEach { it.menuStack.reset() }
    }

    fun enqueueDraw(drawFn: UiDrawCommand) {
        enqueuedDrawCommands += drawFn
    }

    fun drawEnqueuedCommands() {
        enqueuedDrawCommands.forEach { it.draw() }
        enqueuedDrawCommands.clear()
    }

    fun getStandardTextColor(index: Int): ByteColor {
        return standardColors.getOrNull(index) ?: ByteColor.half
    }

    fun drawInventoryItemIcon(item: InventoryItem, position: Vector2f, scale: Vector2f = Vector2f(1f, 1f), mask: Color = Color.NO_MASK) {
        drawInventoryItemIcon(itemInfo = item.info(), position = position, scale = scale, mask = mask, customBorder = item.internalQuality)
    }

    fun drawInventoryItemIcon(itemInfo: InventoryItemInfo, position: Vector2f, scale: Vector2f = Vector2f(1f, 1f), mask: Color = Color.NO_MASK, customBorder: Int? = null) {
        val borderElement = InventoryItemBorders.getBorderElement(customBorder)
        if (borderElement != null) {
            MainTool.drawer.drawXimUi(
                DrawXimUiCommand(uiElement = borderElement, position = position, scale = globalUiScale, elementScale = scale, colorMask = mask)
            )
        }

        val texture = itemInfo.textureResource.name
        val dummyElement = UiElement.basic32x32(texture)

        MainTool.drawer.drawXimUi(
            DrawXimUiCommand(
                uiElement = dummyElement,
                position = position,
                scale = globalUiScale,
                elementScale = scale,
                colorMask = mask
            )
        )
    }

    fun drawStatusEffect(statusEffectState: StatusEffectState, position: Vector2f, scale: Vector2f = Vector2f(1f, 1f), color: ByteColor = ByteColor.half) {
        val linkedSkillId = statusEffectState.linkedSkillId
        val iconColor = Color(color).withMultiplied(2f)

        if (statusEffectState.statusEffect.displaySkillIcon && linkedSkillId is AbilitySkillId) {
            val (iconSet, iconIndex) = AbilitySelectUi.getAbilityIcon(linkedSkillId)
            drawUiElement(lookup = iconSet, index = iconIndex, position = position, scale = scale, color = iconColor)
        } else {
            val info = StatusEffectHelper[statusEffectState.statusEffect]
            val texture = info.icon.textureName

            val dummyElement = UiElement.basicSquare(texture, size = 18, uvHeight = 32, uvWidth = 32)
            val drawCommand = DrawXimUiCommand(uiElement = dummyElement, position = position, scale = globalUiScale, elementScale = scale, colorMask = iconColor)

            MainTool.drawer.drawXimUi(drawCommand)
        }

        val statusText = if (statusEffectState.displayCounter) {
            statusEffectState.counter.toString()
        } else {
            val framesRemaining = statusEffectState.remainingDuration ?: return
            val secondsRemaining = framesToSeconds(framesRemaining)

            if (secondsRemaining.inWholeHours > 0) {
                "${secondsRemaining.inWholeHours}h"
            } else if (secondsRemaining.inWholeMinutes > 0) {
                "${secondsRemaining.inWholeMinutes}m"
            } else {
                "${secondsRemaining.inWholeSeconds}s"
            }
        }


        val stringPos = Vector2f().copyFrom(position)
        stringPos.y += 12f
        stringPos.x += 8f

        drawString(statusText, stringPos, font = Font.FontShp, alignment = TextAlignment.Center, color = color)
    }

    fun drawUiElement(lookup: String, index: Int, position: Vector2f, color: Color = Color.NO_MASK, clipSize: Vector4f? = null, scale: Vector2f = Vector2f(1f, 1f), disableGlobalScale:Boolean = false, rotation: Float = 0f) {
        if (lookup.isEmpty()) { return }

        val uiResource = UiResourceManager.getElement(lookup)
        if (uiResource == null) {
            OnceLogger.warn("[UI] Couldn't find resource: [$lookup]")
            return
        }

        val uiElement = uiResource.uiElementGroup.uiElements[index]
        drawUiElement(uiElement = uiElement, position = position, color = color, clipSize = clipSize, scale = scale, disableGlobalScale = disableGlobalScale, rotation = rotation)
    }

    fun drawUiElement(uiElement: UiElement, position: Vector2f, color: Color = Color.NO_MASK, clipSize: Vector4f? = null, scale: Vector2f = Vector2f(1f, 1f), disableGlobalScale:Boolean = false, rotation: Float = 0f) {
        MainTool.drawer.drawXimUi(
            DrawXimUiCommand(
                uiElement = uiElement,
                position = position,
                elementScale = scale,
                scale = if (disableGlobalScale) { Vector2f(1f, 1f) } else { globalUiScale },
                colorMask = color,
                rotation = rotation,
                clipSize = clipSize,
            )
        )
    }

    fun currentCursorIndex(numAnimFrames: Int): Int {
        return uiFrame % numAnimFrames
    }

    fun drawDownloadingDataElement(offset: Vector2f) {
        val index = uiFrame % 5
        drawUiElement(lookup = "menu    keytops3", index = 165 + index, position = offset)

        val loadingCount = DatLoader.getLoadingCount()
        drawString("Remaining: $loadingCount", font = Font.FontMoji, offset = offset + Vector2f(50f, 25f))
    }

    fun drawBlackScreenCover(opacity: Float) {
        val color = Color(r = 0f, g = 0f, b = 0f, a = opacity)
        drawScreenOverlay(color, BlendFunc.Src_InvSrc_Add)
    }

    fun drawScreenOverlay(color: Color, blendFunc: BlendFunc) {
        val dummyElement = UiElement.screenElement()
        MainTool.drawer.drawXimUi(DrawXimUiCommand(uiElement = dummyElement, position = Vector2f(), scale = globalUiScale, colorMask = color, blendFunc = blendFunc))
    }

    fun drawMenu(
        menuName: String,
        cursorIndex: Int? = null,
        componentStateFn: (Int) -> UiComponentType = { UiComponentType.Default },
        offsetOverride: Vector2f? = null,
        menuStacks: MenuStacks? = null,
        uiPosition: UiPosition? = null,
        scrollSettings: ScrollSettings? = null,
        appendType: AppendType = AppendType.StackAndAppend,
        drawFrame: Boolean = true,
        elementPositionOverride: ((UiMenuElement) -> Vector2f)? = null,
        earlyDraw: ((Vector2f) -> Unit)? = null,
    ): Vector2f? {
        val menu = UiResourceManager.getMenu(menuName) ?: return null

        val configOffset = UiPositionTool.getOffset(uiPosition)

        val frame = menu.uiMenu.frame
        val framePosition = configOffset + if (menuStacks != null) {
            val frameOffsetScaling = if (menuStacks == MenuStacks.PartyStack) { offsetScaling } else { Vector2f(1f, 1f) }
            Vector2f(frame.offset.x, 0f).scale(frameOffsetScaling) + menuStacks.menuStack.appendAndGetPosition(appendType, menu.uiMenu)
        } else if (offsetOverride != null) {
            offsetOverride
        } else {
            Vector2f(frame.offset.x, frame.offset.y)
        }

        drawBorder(frame, framePosition)

        framePosition.x = floor(framePosition.x)
        framePosition.y = floor(framePosition.y)

        if (drawFrame) {
            val frameElement = frame.defaultOption()
            drawUiElement(frameElement.elementGroupName, frameElement.elementIndex, framePosition)
        }

        earlyDraw?.invoke(framePosition) // TODO - is there a better way to draw gauge bars? The "fill" needs to be drawn before the bar

        val components = menu.uiMenu.elements.mapIndexed { index, elementRef ->
            val position = (elementPositionOverride?.invoke(elementRef) ?: Vector2f(elementRef.offset.x, elementRef.offset.y))

            val componentState = componentStateFn.invoke(index)
            val element = elementRef.options[componentState] ?: elementRef.defaultOption()

            val resource = UiResourceManager.getElement(element.elementGroupName) ?: return@mapIndexed null
            val uiElement = resource.uiElementGroup.uiElements[element.elementIndex]

            translate(uiElement.components, position)
        }.filterNotNull().flatten()

        if (components.isNotEmpty()) {
            drawUiElement(uiElement = UiElement(components), position = framePosition)
        }

        val maybeCursor = frame.options[UiComponentType.Cursor]
        if (cursorIndex != null && maybeCursor != null && menu.uiMenu.elements.isNotEmpty()) {
            val cursorElement = UiResourceManager.getElement(maybeCursor.elementGroupName) ?: throw IllegalStateException("No cursor?")

            val element = menu.uiMenu.elements.getOrNull(cursorIndex)

            if (element != null) {
                val position = Vector2f(element.offset.x, element.offset.y) + framePosition
                val index = currentCursorIndex(cursorElement.uiElementGroup.uiElements.size)
                drawUiElement(lookup = maybeCursor.elementGroupName, index = index, position = position)
            }
        }

        drawScrollBar(frame, framePosition, scrollSettings)

        return framePosition
    }

    private fun drawBorder(frame: UiMenuElement, framePosition: Vector2f) {
        val cornerSize = 24f
        val sidesSize = 80f

        if (frame.size.x < cornerSize || frame.size.y < cornerSize) { // TODO - better way to tell if frame doesn't need a border?
            return
        }

        val element = UiResourceManager.getElement("menu    win00   ") ?: return

        val borderComponents = ArrayList<UiElementComponent>()

        val background = element.uiElementGroup.uiElements[0]
        val backgroundComponent = background.components[0]
        val backgroundScale = Vector2f(frame.size.x / 128f, frame.size.y / 128f)

        borderComponents += backgroundComponent.copy(
            vertices = backgroundComponent.vertices.map { UiVertex(it.point * backgroundScale, it.color) },
            uvWidth = (backgroundComponent.uvWidth * backgroundScale.x).toInt(),
            uvHeight = (backgroundComponent.uvHeight * backgroundScale.y).toInt(),
        )

        val topBottomWidth = (frame.size.x - 2 * cornerSize)
        val leftRightHeight = (frame.size.y - 2 * cornerSize)

        val topLeft = element.uiElementGroup.uiElements[1].components
        borderComponents += topLeft

        val topRight = element.uiElementGroup.uiElements[3].components
        borderComponents += translate(topRight, Vector2f(cornerSize + topBottomWidth, 0f))

        val bottomLeft = element.uiElementGroup.uiElements[6].components
        borderComponents += translate(bottomLeft, Vector2f(0f, leftRightHeight + cornerSize))

        val bottomRight = element.uiElementGroup.uiElements[8].components
        borderComponents += translate(bottomRight, Vector2f(topBottomWidth + cornerSize, leftRightHeight + cornerSize))

        if (leftRightHeight > 0) {
            val left = element.uiElementGroup.uiElements[4].components
            borderComponents += translate(scale(left, Vector2f(1f, leftRightHeight / sidesSize)), Vector2f(0f, cornerSize))

            val right = element.uiElementGroup.uiElements[5].components
            borderComponents += translate(scale(right, Vector2f(1f, leftRightHeight / sidesSize)), Vector2f(topBottomWidth + cornerSize, cornerSize))
        }

        // top, bottom
        if (topBottomWidth > 0) {
            val frameMenuElement = frame.defaultOption()

            var minX: Float? = null
            var maxX: Float? = null

            val frameElementGroup = UiResourceManager.getElement(frameMenuElement.elementGroupName)
            if (frameElementGroup != null) {
                val frameElement = frameElementGroup.uiElementGroup.uiElements[frameMenuElement.elementIndex]

                // For top, need to ensure that the title isn't overlapped. I don't see a good way to do this...
                // It's definitely related to the unknown flags in the UiElement

                minX = frameElement.components.filter { it.textureName == "menu    hfr1    " }
                    .flatMap { it.vertices }
                    .minOfOrNull { it.point.x }

                maxX = frameElement.components.filter { it.textureName == "menu    hfr1    " }
                    .flatMap { it.vertices }
                    .maxOfOrNull { it.point.x }
            }

            val topLeftScale = if (minX == null) { topBottomWidth / sidesSize } else { (minX - cornerSize) / sidesSize }

            val top = element.uiElementGroup.uiElements[2].components
            borderComponents += translate(scale(top, Vector2f(topLeftScale, 1f)), Vector2f(cornerSize, 0f))

            if (maxX != null) {
                val topRightScale = (topBottomWidth - (maxX - cornerSize)) / sidesSize
                if (topRightScale > 0f) {
                    borderComponents += translate(scale(top, Vector2f(topRightScale, 1f)), Vector2f(maxX, 0f))
                }
            }

            val bottom = element.uiElementGroup.uiElements[7].components
            borderComponents += translate(scale(bottom, Vector2f(topBottomWidth/sidesSize, 1f)), Vector2f(cornerSize, leftRightHeight + cornerSize-1f))
        }

        val backgroundElement = UiElement(borderComponents)
        drawUiElement(backgroundElement, position = framePosition)
    }

    private fun drawScrollBar(frame: UiMenuElement, framePosition: Vector2f, scrollSettings: ScrollSettings?) {
        if (scrollSettings == null) { return }

        val visibleItems = scrollSettings.numElementsInPage
        val totalItems = scrollSettings.numElementsProvider.invoke()

        if (visibleItems >= totalItems) { return }

        val capSize = Vector2f(8f, 4f)
        val barSize = 64f

        val scrollBarScale = (frame.size.y - 2 * capSize.y) / barSize
        val scrollBarFillScale = scrollBarScale * visibleItems.toFloat() / totalItems.toFloat()
        val scrollBarFillOffset = capSize.y + (frame.size.y - 2 * capSize.y) * (scrollSettings.lowestViewableItemIndex.toFloat() / totalItems.toFloat())

        drawUiElement(lookup = "menu    scroll  ", index = 3, position = framePosition + Vector2f(frame.size.x - capSize.x, scrollBarFillOffset), scale = Vector2f(1f, scrollBarFillScale))
        drawUiElement(lookup = "menu    scroll  ", index = 2, position = framePosition + Vector2f(frame.size.x - capSize.x, frame.size.y - capSize.y))
        drawUiElement(lookup = "menu    scroll  ", index = 1, position = framePosition + Vector2f(frame.size.x - capSize.x, capSize.y), scale = Vector2f(1f, scrollBarScale))
        drawUiElement(lookup = "menu    scroll  ", index = 0, position = framePosition + Vector2f(frame.size.x - capSize.x, 0f))
    }

    fun formatNumber(number: Int): String {
        var remainingPrice = number
        var output = ""

        while (remainingPrice > 1000) {
            val remainder = number % 1000
            output = (",${remainder.toString().padStart(3, '0')}") + output
            remainingPrice /= 1000
        }

        output = remainingPrice.toString() + output
        return output
    }

    fun formatPrice(currencyType: CurrencyType, price: Int): String {
        return "${formatNumber(price)} ${currencyType.displayName}"
    }

    fun drawString(text: String, offset: Vector2f, font: Font = Font.FontMoji, color: ByteColor = ByteColor.half, alignment: TextAlignment = TextAlignment.Left, scale: Vector2f = Vector2f(1f, 1f)) {
        val fontElement = UiResourceManager.getElement(font.elementName) ?: return

        val positions = ArrayList<PositionedCharacter>()

        var currentColor = color
        val currentPosition = Vector2f()

        for (char in text.toCharArray()) {
            if (char.code == 10) {
                currentPosition.x = 0f
                currentPosition.y += 16f
                continue
            }

            val fontChar = if (char.code < 128) {
                val index = (char - 32).code // Font doesn't include the first 32 non-render elements
                val element = fontElement.uiElementGroup.uiElements[index]
                FontChar(char, element)
            } else {
                val specialChar = map(char, font)

                if (specialChar.clearColor) {
                    currentColor = color
                    continue
                } else if (specialChar.colorOverride != null) {
                    currentColor = specialChar.colorOverride
                    continue
                }

                specialChar
            }

            val uiElement = fontChar.element ?: continue
            val element = uiElement.components[0]

            positions += PositionedCharacter(fontChar, Vector2f().copyFrom(currentPosition), currentColor)
            currentPosition.x += element.width
        }

        if (alignment == TextAlignment.Right) {
            val shiftFactor = currentPosition.x
            positions.forEach { it.position.x -= shiftFactor }
        } else if (alignment == TextAlignment.Center) {
            val shiftFactor = currentPosition.x/2f
            positions.forEach { it.position.x -= shiftFactor }
        }

        val stringElement = toUiElement(positions)
        drawUiElement(uiElement = stringElement, position = offset, scale = scale)
    }

    fun drawFormattedString(formattedString: FormattedString, clipSize: Vector4f? = null, offset: Vector2f = Vector2f(), scale: Vector2f = Vector2f.ONE) {
        val stringElement = toUiElement(formattedString.characters)
        drawUiElement(uiElement = stringElement, position = offset, clipSize = clipSize, scale = scale)
    }

    fun formatString(text: String, maxWidth: Int, textDirection: TextDirection, font: Font = Font.FontMoji, color: ByteColor = ChatLogColor.Normal.color): FormattedString? {
        val fontElement = UiResourceManager.getElement(font.elementName) ?: return null

        var numLines = 1
        val positions = ArrayList<PositionedCharacter>()

        var currentColor = color
        val currentPosition = Vector2f()
        var maxX = 0f

        for (char in text.toCharArray()) {
            if (char.code == 0x0A) {
                when (textDirection) {
                    TextDirection.TopToBottom -> currentPosition.y += 16f
                    TextDirection.BottomToTop -> positions.forEach { it.position.y -= 16f }
                }

                currentPosition.x = 0f
                numLines += 1
                continue
            }

            val fontChar = if (char.code < 128) {
                val index = (char - 32).code // Font doesn't include the first 32 non-render elements
                val element = fontElement.uiElementGroup.uiElements[index]
                FontChar(char, element)
            } else {
                val specialChar = map(char, font)

                if (specialChar.clearColor) {
                    currentColor = color
                    continue
                } else if (specialChar.colorOverride != null) {
                    currentColor = specialChar.colorOverride
                    continue
                }

                specialChar
            }

            val uiElement = fontChar.element ?: continue
            val element = uiElement.components[0]

            if (currentPosition.x + element.width > maxWidth) {
                linebreakLatestWord(currentPosition, positions, textDirection)
                numLines += 1
                if (textDirection == TextDirection.TopToBottom) { currentPosition.y += 16f }
            }

            positions += PositionedCharacter(fontChar, Vector2f().copyFrom(currentPosition), currentColor)
            currentPosition.x += element.width
            maxX = max(currentPosition.x, maxX)
        }

        return FormattedString(numLines, positions, maxX)
    }

    private fun linebreakLatestWord(position: Vector2f, positions: ArrayList<PositionedCharacter>, textDirection: TextDirection) {
        val maxLookBack = (positions.size - 16).coerceAtLeast(1)

        // Try to find a recent " ", and break on it
        for (i in positions.size - 1 downTo maxLookBack) {
            if (positions[i-1].fontChar.char != ' ') { continue }

            val posChar = positions[i]
            val adjustment = posChar.position.x

            for (j in i until positions.size) {
                positions[j].position.x -= adjustment
                if (textDirection == TextDirection.TopToBottom) { positions[j].position.y += 16f }
            }

            if (textDirection == TextDirection.BottomToTop) {
                for (j in 0 until i) { positions[j].position.y -= 16f }
            }

            position.x -= adjustment
            return
        }

        // Didn't find a recent " " - just break on the latest char
        position.x = 0f

        if (textDirection == TextDirection.BottomToTop) {
            for (pos in positions) { pos.position.y -= 16f }
        }
    }

    private fun map(char: Char, font: Font): FontChar {
        val encode = (char.code shr 0x8) and 0xFF
        return if (encode == 0x1E) {
            val index = (char.code and 0xFF)
            if (index == 0) {
                FontChar(Char(0x1E), element = null, clearColor = true)
            } else {
                FontChar(Char(0x1E), element = null, colorOverride = resolveColor(index))
            }
        } else if (encode == 0xEF) {
            val index = (char.code and 0xFF) - 0x20
            val elementIndex = index.toShort() + 1
            val element = UiResourceManager.getElement("font    usgaiji ")?.uiElementGroup?.uiElements?.getOrNull(elementIndex)
            return FontChar(char, element, Vector2f(0f, -5f))
        } else {
            val element = UiResourceManager.getElement(font.elementName)?.uiElementGroup?.uiElements?.getOrNull(FontMojiHelper.mapShiftJisToIndex(char))
            FontChar(char, element)
        }
    }

    private fun resolveColor(index: Int): ByteColor {
        return when (index) {
            1 -> getStandardTextColor(0)
            2 -> getStandardTextColor(4)
            3 -> getStandardTextColor(3)
            6 -> getStandardTextColor(1)
            8 -> getStandardTextColor(8)
            9 -> getStandardTextColor(5)
            32 -> getStandardTextColor(20)
            33 -> ByteColor(0x60, 0x60, 0x60, 0x80)
            34 -> ByteColor(0xA0, 0xA0, 0xA0, 0x80)
            35 -> ByteColor(0x80, 0x46, 0x46, 0x80)
            36 -> ByteColor(0x80, 0x80, 0x80, 0x20)
            else -> getStandardTextColor(0)
        }
    }

    private fun fetchStandardColors(): List<ByteColor> {
        val elementResource = UiResourceManager.getElement("menu    ncol    ") ?: return emptyList()
        val colors = ArrayList<ByteColor>()

        for (element in elementResource.uiElementGroup.uiElements) {
            colors += element.components[0].vertices[0].color
        }

        return colors
    }

    fun toUiElement(positionedChars: List<PositionedCharacter>): UiElement {
        val components = ArrayList<UiElementComponent>(positionedChars.size)

        for (char in positionedChars) {
            val element = char.fontChar.element ?: continue
            val component = element.components[0]

            val positionedVertices = component.vertices.map { UiVertex(point = it.point + char.position + char.fontChar.offset, color = char.color) }
            components += component.copy(vertices = positionedVertices)
        }

        return UiElement(components)
    }

    private fun translate(components: List<UiElementComponent>, offset: Vector2f): List<UiElementComponent> {
        return components.map { component -> component.copy(vertices = component.vertices.map { UiVertex(it.point + offset, it.color) }) }
    }

    private fun scale(components: List<UiElementComponent>, amount: Vector2f): List<UiElementComponent> {
        return components.map { component -> component.copy(vertices = component.vertices.map { UiVertex(it.point * amount, it.color) }) }
    }

}