package xim.poc.gl;

import xim.util.interpolate
import js.typedarrays.Float32Array

class Color(val rgba: Float32Array) {

    constructor() : this(Float32Array(4)) {
        rgba(0f, 0f, 0f, 0f)
    }

    constructor(r: Float, g: Float, b: Float, a: Float) : this(Float32Array(4)) {
        rgba(r, g, b, a)
    }

    constructor(r: Int, g: Int, b: Int, a: Int) : this(Float32Array(4)) {
        rgba(r, g, b, a)
    }

    constructor(byteColor: ByteColor) : this(Float32Array(4)) {
        rgba(byteColor.r, byteColor.g, byteColor.b, byteColor.a)
    }

    companion object {
        fun interpolate(c0: Color, c1: Color, t: Float): Color {
            return Color(
                c0.r().interpolate(c1.r(), t),
                c0.g().interpolate(c1.g(), t),
                c0.b().interpolate(c1.b(), t),
                c0.a().interpolate(c1.a(), t),
            )
        }

        val GREY = Color(0.75f, 0.75f, 0.75f, 1f)
        val ALPHA_25 = Color(1f, 1f, 1f, 0.25f)
        val HALF_ALPHA = Color(1f, 1f, 1f, 0.5f)
        val ALPHA_75 = Color(1f, 1f, 1f, 0.75f)
        val NO_MASK = Color(1f, 1f, 1f, 1f)
        val ZERO = Color(0f, 0f, 0f, 0f)
    }

    fun r(value: Float) {
        rgba[0] = value
    }

    fun g(value: Float) {
        rgba[1] = value
    }

    fun b(value: Float) {
        rgba[2] = value
    }

    fun a(value: Float) {
        rgba[3] = value
    }

    fun a(value: Int) {
        rgba[3] = value/255f
    }

    fun r() = rgba[0]
    fun g() = rgba[1]
    fun b() = rgba[2]
    fun a() = rgba[3]

    fun multiplyAlphaInPlace(value: Float) {
        rgba[3] *= value
    }

    fun rgba(r: Int, g: Int, b: Int, a: Int) {
        r(r / 255f)
        g(g / 255f)
        b(b / 255f)
        a(a / 255f)
    }

    fun rgba(r: Float, g: Float, b: Float, a: Float) {
        r(r)
        g(g)
        b(b)
        a(a)
    }

    fun copyFrom(other: Color) {
        for (i in 0 until 4) {
            rgba[i] = other.rgba[i]
        }
    }

    fun copyFrom(other: ByteColor) {
        rgba(other.r, other.g, other.b, other.a)
    }

    fun modulateRgbInPlace(other: Color, mf: Float) : Color {
        r(mf * other.r() * r())
        g(mf * other.g() * g())
        b(mf * other.b() * b())
        return this
    }

    fun modulateInPlace(other: Color, mf: Float) : Color {
        r(mf * other.r() * r())
        g(mf * other.g() * g())
        b(mf * other.b() * b())
        a(mf * other.a() * a())
        return this
    }

    fun addInPlace(other: Color) : Color {
        r(other.r() + r())
        g(other.g() + g())
        b(other.b() + b())
        a(other.a() + a())
        return this
    }

    fun withMultiplied(factor: Float) : Color {
        return Color(r() * factor, g() * factor, b() * factor, a() * factor)
    }

    fun withAlpha(alpha: Float): Color {
        return Color(r(), g(), b(), alpha)
    }

    fun withMultipliedAlpha(opacity: Float) : Color {
        return Color(r(), g(), b(), a() * opacity)
    }

    fun clamp(max: Float = 1f): Color {
        for (i in 0 until 4) {
            rgba[i] = rgba[i].coerceIn(0f, max)
        }
        return this
    }

    operator fun times(other: Color) : Color {
        return Color(r() * other.r(), g() * other.g(), b() * other.b(), a() * other.a())
    }

    operator fun plus(other: Color): Color {
        return Color(r() + other.r(), g() + other.g(), b() + other.b(), a() + other.a())
    }

    override fun toString(): String {
        return "(${rgba[0]}, ${rgba[1]}, ${rgba[2]}, ${rgba[3]})"
    }

}
