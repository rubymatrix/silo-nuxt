package xim.poc.ui

import xim.poc.UiResourceManager
import xim.resource.*

object ShiftJis {
    val colorClear = Char(0x1E00)
    val colorWhite = Char(0x1E01)
    val colorItem = Char(0x1E02)
    val colorKey = Char(0x1E03)
    val colorAug = Char(0x1E06)
    val colorTemporary = Char(0x1E08)
    val colorInfo = Char(0x1E09)

    val colorGold = Char(0x1E20)
    val colorGrey = Char(0x1E21)
    val colorCustom = Char(0x1E22)
    val colorInvalid = Char(0x1E23)
    val colorQuarterAlpha = Char(0x1E24)

    val longTilde = Char(0x8160)
    val multiplyX = Char(0x817F)
    val solidSquare = Char(0x81A1)
    val solidDownTriangle = Char(0x81A5)
    val leftBracket = Char(0x816D)
    val rightBracket = Char(0x816E)
    val leftRoundedBracket = Char(0x8179)
    val rightRoundedBracket = Char(0x817A)
    val emptyStar = Char(0x8199)
    val solidStar = Char(0x819a)
    val rightArrow = Char(0x81a8)

    val specialFire = Char(0xEF1F)
    val specialIce = Char(0xEF20)
    val specialWind = Char(0xEF21)
    val specialEarth = Char(0xEF22)
    val specialLightning = Char(0xEF23)
    val specialWater = Char(0xEF24)
    val specialLight = Char(0xEF25)
    val specialDark = Char(0xEF26)

    fun toChar(spellElement: SpellElement): Char {
        return specialFire + spellElement.index
    }

}

object FontMojiHelper {

    private const val name = "font    moji    "
    private var registered = false

    fun register() {
        if (registered) { return }
        registered = true

        val font = make()
        val resource = UiElementResource(DatId("f_mj"), font)
        UiResourceManager.register(resource)
    }

    fun mapShiftJisToIndex(char: Char): Int {
        val upper = (char.code shr 0x8) and 0xFF
        val lower = (char.code and 0xFF)

        return if (upper == 0) {
            lower - 0x20
        } else if (upper == 0x81) {
            when {
                lower <= 0x7E -> 0x060 + (lower - 0x40 - 0x00)
                lower <= 0xAC -> 0x060 + (lower - 0x40 - 0x01)
                lower <= 0xBF -> 0x060 + (lower - 0x40 - 0x0C)
                lower <= 0xCE -> 0x060 + (lower - 0x40 - 0x14)
                lower <= 0xE8 -> 0x060 + (lower - 0x40 - 0x1F)
                lower <= 0xF7 -> 0x060 + (lower - 0x40 - 0x26)
                lower <= 0xFC -> 0x060 + (lower - 0x40 - 0x2A)
                else -> '?'.code - 0x20
            }
        } else if (upper == 0x82) {
            when {
                lower <= 0x58 -> 0x0F3 + (lower - 0x40 - 0x0F)
                lower <= 0x79 -> 0x0F3 + (lower - 0x40 - 0x16)
                lower <= 0x9A -> 0x0F3 + (lower - 0x40 - 0x1D)
                lower <= 0xF1 -> 0x0F3 + (lower - 0x40 - 0x21)
                else -> '?'.code - 0x20
            }
        } else if (upper == 0x83) {
            // Starts at index 0x184
            when {
                lower <= 0x7E -> 0x184 + (lower - 0x40 - 0x00)
                lower <= 0x96 -> 0x184 + (lower - 0x40 - 0x01)
                lower <= 0xB6 -> 0x184 + (lower - 0x40 - 0x09)
                lower <= 0xD6 -> 0x184 + (lower - 0x40 - 0x11)
                else -> '?'.code - 0x20
            }
        } else {
            '?'.code - 0x20
        }
    }

    private fun make(): UiElementGroup {
        val chars = ArrayList<UiElement>()
        val group = UiElementGroup(name = name, uiElements = chars)

        for (y in 0 until 9) {
            for (x in 0 until 64) {
                val width = charWidths.getOrElse(y * 64 + x) { Pair(0, 15) }
                val char = UiElement.fontMojiChar(textureName = name, minX = width.first.toFloat(), maxX = width.second.toFloat(), uvX = x * 16, uvY = y * 16)
                chars += UiElement(components = listOf(char), fileOffset = 0)
            }
        }

        return group
    }

    private val charWidths = listOf(
        Pair(0, 4),
        Pair(0, 4),
        Pair(0, 4),
        Pair(0, 8),
        Pair(0, 7),
        Pair(0, 11),
        Pair(0, 11),
        Pair(0, 3),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(1, 7),
        Pair(0, 3),
        Pair(1, 6),
        Pair(1, 2),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 4),
        Pair(0, 3),
        Pair(1, 7),
        Pair(1, 7),
        Pair(1, 7),
        Pair(0, 7),
        Pair(0, 11),
        Pair(0, 9),
        Pair(0, 9),
        Pair(0, 8),
        Pair(1, 10),
        Pair(0, 8),
        Pair(0, 7),
        Pair(0, 9),
        Pair(0, 9),
        Pair(0, 5),
        Pair(0, 8),
        Pair(0, 9),
        Pair(1, 8),
        Pair(0, 11),
        Pair(0, 8),
        Pair(0, 9),
        Pair(0, 8),
        Pair(0, 11),
        Pair(0, 9),
        Pair(0, 8),
        Pair(0, 8),
        Pair(0, 8),
        Pair(0, 9),
        Pair(0, 12),
        Pair(0, 9),
        Pair(0, 8),
        Pair(0, 8),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 5),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 5),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 3),
        Pair(0, 4),
        Pair(0, 7),
        Pair(0, 4),
        Pair(0, 10),
        Pair(0, 7),
        Pair(0, 8),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 5),
        Pair(0, 7),
        Pair(0, 5),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 9),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 5),
        Pair(1, 2),
        Pair(0, 7),
        Pair(0, 11),
        Pair(16, 0),
        Pair(16, 0),
        Pair(0, 4),
        Pair(0, 7),
        Pair(1, 4),
        Pair(1, 4),
        Pair(6, 9),
        Pair(6, 8),
        Pair(6, 8),
        Pair(4, 12),
        Pair(6, 11),
        Pair(0, 7),
        Pair(1, 7),
        Pair(0, 7),
        Pair(0, 5),
        Pair(0, 7),
        Pair(0, 7),
        Pair(0, 15),
        Pair(0, 15),
        Pair(4, 11),
        Pair(4, 13),
        Pair(4, 12),
        Pair(4, 15),
        Pair(4, 11),
        Pair(0, 15),
        Pair(0, 12),
        Pair(0, 13),
        Pair(0, 15),
        Pair(1, 14),
        Pair(0, 15),
        Pair(6, 10),
        Pair(0, 15),
        Pair(0, 15)
    )

}