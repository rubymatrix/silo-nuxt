package xim.resource

import xim.math.Matrix3f
import xim.math.Vector2f
import xim.poc.UiResourceManager
import xim.poc.gl.ByteColor

enum class UiFlipMode(val transform: Matrix3f) {
    None(Matrix3f()),
    Horizontal(Matrix3f().scaleInPlace(-1f, 1f)),
    Vertical(Matrix3f().scaleInPlace(1f, -1f)),
    Both(Matrix3f().scaleInPlace(-1f, -1f)),
}

data class UiVertex(val point: Vector2f, val color: ByteColor)

data class UiElementComponent(
    val vertices: List<UiVertex>,
    val width: Float,
    val height: Float,
    val uvWidth: Int,
    val uvHeight: Int,
    val uvOffsetX: Int,
    val uvOffsetY: Int,
    val textureName: String,
    val flipMode: UiFlipMode,
    val drawEnabled: Boolean,
)

class UiElement(val components: List<UiElementComponent>, val fileOffset: Int = 0) {

    companion object {
        fun basicSquare(textureName: String, size: Int, uvWidth: Int = size, uvHeight: Int = size, flipMode: UiFlipMode = UiFlipMode.None): UiElement {
            val dummyParent = UiElementGroup("parent", emptyList())
            val sizeF = size.toFloat()

            val component = UiElementComponent(
                vertices = listOf(
                    UiVertex(Vector2f(x = 0f, y = 0f), color = ByteColor(0x7F, 0x7F, 0x7F, 0x7F)),
                    UiVertex(Vector2f(x = sizeF, y = 0f), color = ByteColor(0x7F, 0x7F, 0x7F, 0x7F)),
                    UiVertex(Vector2f(x = 0f, y = sizeF), color = ByteColor(0x7F, 0x7F, 0x7F, 0x7F)),
                    UiVertex(Vector2f(x = sizeF, y = sizeF), color = ByteColor(0x7F, 0x7F, 0x7F, 0x7F)),
                ),
                width = sizeF,
                height = sizeF,
                uvWidth = uvWidth,
                uvHeight = uvHeight,
                uvOffsetX = 0,
                uvOffsetY = 0,
                textureName = textureName,
                flipMode = flipMode,
                drawEnabled = true,
            )

            return UiElement(listOf(component), 0)
        }

        fun basic1x1Flipped(textureName: String, uvWidth: Int = 32, uvHeight: Int = 32): UiElement {
            return basicSquare(textureName = textureName, size = 1, uvWidth = uvWidth, uvHeight = uvHeight, flipMode = UiFlipMode.Vertical)
        }

        fun basic32x32(textureName: String, uvWidth: Int = 32, uvHeight: Int = 32): UiElement {
            return basicSquare(textureName = textureName, size = 32, uvWidth = uvWidth, uvHeight = uvHeight)
        }

        fun screenElement(): UiElement {
            return basicSquare(textureName = "", size = 10_000, uvWidth = 1, uvHeight = 1)
        }

        fun fontMojiChar(textureName: String, minX: Float, maxX: Float, uvX: Int, uvY: Int): UiElementComponent {
            return UiElementComponent(
                vertices = listOf(
                    UiVertex(Vector2f(x = 0f, y = -4f), color = ByteColor(0x7F, 0x7F, 0x7F, 0x7F)),
                    UiVertex(Vector2f(x = 16f, y = -4f), color = ByteColor(0x7F, 0x7F, 0x7F, 0x7F)),
                    UiVertex(Vector2f(x = 0f, y = 12f), color = ByteColor(0x7F, 0x7F, 0x7F, 0x7F)),
                    UiVertex(Vector2f(x = 16f, y = 12f), color = ByteColor(0x7F, 0x7F, 0x7F, 0x7F)),
                ),
                width = (maxX - minX) + 1,
                height = 16f,
                uvWidth = 16,
                uvHeight = 16,
                uvOffsetX = uvX,
                uvOffsetY = uvY,
                textureName = textureName,
                flipMode = UiFlipMode.None,
                drawEnabled = true,
            )
        }
    }

}

class UiElementGroup(val name: String, val uiElements: List<UiElement>)

class UiElementGroupParser(private val sectionHeader: SectionHeader) : ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val group = read(byteReader)

        val resource = UiElementResource(sectionHeader.sectionId, group)
        UiResourceManager.register(resource)

        val entry = ParserEntry(resource, group.name)
        return ParserResult(entry)
    }

    private fun read(byteReader: ByteReader): UiElementGroup {
        byteReader.offsetFromDataStart(sectionHeader)
        val setName = byteReader.nextString(0x10)

        val numSets = byteReader.next8()
        val textureNames = ArrayList<String>(numSets)

        for (i in 0 until numSets) {
            textureNames.add(byteReader.nextString(0x10))
        }

        val count = byteReader.next16()
        val uiElements = ArrayList<UiElement>(count)
        val uiElementGroup = UiElementGroup(setName, uiElements)

        for (i in 0 until count) {
            uiElements.add(parseUiElement(uiElementGroup, byteReader))
        }

        return uiElementGroup
    }

    private fun parseUiElement(parent: UiElementGroup, byteReader: ByteReader): UiElement {
        val fileOffset = byteReader.position
        val numComponents = byteReader.next8()
        val components = ArrayList<UiElementComponent>(numComponents)

        for (componentIndex in 0 until numComponents) {
            components.add(parseUiElementComponent(parent, byteReader))
        }

        return UiElement(components, fileOffset)
    }

    private fun parseUiElementComponent(group: UiElementGroup, byteReader: ByteReader): UiElementComponent {
        var drawEnabled = true
        val positions = ArrayList<Vector2f>(4)

        for (i in 0 until 4) {
            positions.add(Vector2f(x = byteReader.next16Signed().toFloat(), y = byteReader.next16Signed().toFloat()))
        }

        val uvWidth = byteReader.next16()
        val uvHeight = byteReader.next16()

        val uvOffsetX = byteReader.next16()
        val uvOffsetY = byteReader.next16()

        val flipMode = when(val raw = byteReader.next8()) {
            0 -> UiFlipMode.None
            1 -> UiFlipMode.Horizontal
            2 -> UiFlipMode.Vertical
            3 -> UiFlipMode.Both
            else -> throw IllegalStateException("Unknown flip-mode value: $raw")
        }

        val colorMasks = ArrayList<ByteColor>(4)
        for (i in 0 until 4) {
            colorMasks.add(byteReader.nextRGBA())
        }

        val unk6 = byteReader.next8()
        val unk7 = byteReader.next8()

        // TODO This seems related to the border-rendering logic - for now, rely on custom logic
        if (unk7 == 2) {
            drawEnabled = false
        }

        val unk8 = byteReader.next8()
        val unk9 = byteReader.next8()

        val ref = byteReader.nextString(0x10).lowercase()

        // TODO - this seems correct - we only want to draw the border/background from the custom logic
        // But it also seems so hacky
        if (group.name != "menu    win00   " && ref == "menu    newtex  ") {
            drawEnabled = false
        }

        val vertices = positions.zip(colorMasks) { a, b -> UiVertex(a, b) }.toMutableList()
        val width = positions.maxOf { it.x } - positions.minOf { it.x } - 2
        val height = positions.maxOf { it.y } - positions.minOf { it.y } - 2

        return UiElementComponent(
            vertices = vertices,
            width = width,
            height = height,
            uvWidth = uvWidth,
            uvHeight = uvHeight,
            uvOffsetX = uvOffsetX,
            uvOffsetY = uvOffsetY,
            textureName = ref,
            flipMode = flipMode,
            drawEnabled = drawEnabled,
        )
    }
}
