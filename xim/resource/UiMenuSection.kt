package xim.resource

import xim.math.Vector2f
import xim.poc.UiResourceManager
import xim.util.OnceLogger

enum class UiMenuCursorKey {
    Up,
    Down,
    Right,
    Left,
}

enum class UiComponentType(val value: Int) {
    Default(0),
    Secondary(1), // Only used(?) in "Close Mog" as an alternative for "Open Mog"
    Selected(3),
    Disabled(4),
    Cursor(6),
}

class UiMenuElementOption(val elementIndex: Int, val elementGroupName: String)

class UiMenuElement(
    val offset: Vector2f,
    val size: Vector2f,
    val options: Map<UiComponentType, UiMenuElementOption>,
    val next: Map<UiMenuCursorKey, Int>,
    val selectable: Boolean,
) {
    fun deepCopy(): UiMenuElement {
        return UiMenuElement(offset = Vector2f().copyFrom(offset), size = Vector2f().copyFrom(size), options.toMutableMap(), next.toMutableMap(), selectable = selectable)
    }

    fun defaultOption(): UiMenuElementOption {
        return options[UiComponentType.Default] ?: throw IllegalStateException("UiMenuElement has no 'Default' option")
    }
}

class UiMenu(val name: String, val frame: UiMenuElement, val elements: List<UiMenuElement>)

class UiMenuSection(val sectionHeader: SectionHeader) : ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val menu = read(byteReader)
        val resource = UiMenuResource(sectionHeader.sectionId, menu)
        UiResourceManager.register(resource)
        return ParserResult.from(resource)
    }

    private fun read(byteReader: ByteReader) : UiMenu {
        byteReader.offsetFromDataStart(sectionHeader)

        val menuName = byteReader.nextString(0x10)

        val maybeType = byteReader.next8()
        val numElements = byteReader.next8()
        byteReader.align0x10()

        val elements = ArrayList<UiMenuElement>(numElements + 1)

        val frame = readElement(byteReader)

        for (i in 0 until numElements) {
            elements.add(readElement(byteReader))
        }

        return UiMenu(menuName, frame, elements)
    }

    private fun readElement(byteReader: ByteReader) : UiMenuElement {
        val start = byteReader.position
        val size = byteReader.next16()

        val xOffset = byteReader.next16Signed().toFloat()
        val yOffset = byteReader.next16Signed().toFloat()

        val unk0 = byteReader.next16()
        val unk1 = byteReader.next16()

        val width = byteReader.next16Signed()
        val height = byteReader.next16Signed()

        val unk2 = byteReader.next16Signed()
        val unk3 = byteReader.next16Signed()

        val elementIndex = byteReader.next8()

        val frameSetting0 = byteReader.next8()
        val frameSetting1 = byteReader.next8()

        val previousElementIndex = byteReader.next8Signed()
        val nextElementIndex = byteReader.next8Signed()
        val selectable = previousElementIndex != -1 && nextElementIndex != -1

        val nextMap = HashMap<UiMenuCursorKey, Int>()
        nextMap[UiMenuCursorKey.Up] = byteReader.next8Signed()
        nextMap[UiMenuCursorKey.Down] = byteReader.next8Signed()
        nextMap[UiMenuCursorKey.Right] = byteReader.next8Signed()
        nextMap[UiMenuCursorKey.Left] = byteReader.next8Signed()

        val elementConfig0 = byteReader.next16()
        val elementConfig1 = byteReader.next16()

        val unk4 = byteReader.next8()

        val elementsToRead = if (elementIndex == 0) { frameSetting1 } else { elementConfig0 }
        val options = HashMap<UiComponentType, UiMenuElementOption>(elementsToRead)

        for (i in 0 until elementsToRead) {
            val rawType = byteReader.next16()
            val uiElementIndex = byteReader.next16()
            val groupName = byteReader.nextString(0x10)

            val type = UiComponentType.values().firstOrNull { it.value == rawType }
            if (type == null) {
                OnceLogger.warn("[${sectionHeader.sectionId}] $byteReader unknown component type: $rawType;")
                continue
            }

            options[type] = UiMenuElementOption(elementIndex = uiElementIndex, elementGroupName = groupName)
        }

        if (!options.containsKey(UiComponentType.Default)) {
            OnceLogger.warn("[$sectionHeader][$byteReader] UiMenuElement does not have a 'Default' option: ${options.keys}")
        }

        // TODO - there's two weird zero-terminated Strings here

        byteReader.position = start + size
        return UiMenuElement(
            offset = Vector2f(xOffset, yOffset),
            size = Vector2f(width.toFloat(), height.toFloat()),
            options = options,
            next = nextMap,
            selectable = selectable,
        )
    }

}