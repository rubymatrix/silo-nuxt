package xim.poc.gl

import xim.math.Vector2f
import xim.math.Vector4f
import xim.resource.UiElement

class DrawXimUiCommand (
    val uiElement: UiElement,
    val position: Vector2f,
    val elementScale: Vector2f = Vector2f(1f, 1f),
    val scale: Vector2f = Vector2f(2f, 2f),
    val colorMask: Color = Color.NO_MASK,
    val rotation: Float = 0f,
    val blendFunc: BlendFunc = BlendFunc.Src_InvSrc_Add,

    val clipSize: Vector4f? = null // (x0, y0, x1, y1)
)

class DrawXimScreenOptions (
    val position: Vector2f = Vector2f.ZERO,
    val colorMask: Color = Color.NO_MASK,
    val blendEnabled: Boolean = false,
)