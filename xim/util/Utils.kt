package xim.util

import kotlin.js.Date
import kotlin.math.*
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

const val PI_f = PI.toFloat()

object Utils {

    fun sometimes(probability: Double, runnable: () -> Unit) {
        if (Random.nextDouble(100.0) > 100 * probability) {
            runnable()
        }
    }

}

class Periodically(private val interval: Duration, startReady: Boolean = true) {

    private var previous: Duration = Date.now().milliseconds

    init {
        if (startReady) { previous -= interval }
    }

    fun ready(): Boolean {
        val now = Date.now().milliseconds
        val ready = now - previous > interval
        if (ready) { previous = now }
        return ready
    }

}

fun Int.interpolate (other: Int, t: Float): Int {
    return ((1.0f - t) * this + other * t).toInt()
}

fun Float.interpolate (other: Float, t: Float): Float {
    return (1.0f - t) * this + other * t
}

fun Float.fallOff(near: Float, far: Float): Float {
    return if (this < near) {
        1f
    } else if (this < far) {
        1f - (this - near) / (far - near)
    } else {
        0f
    }
}

fun Float.toRads(): Float {
    return this * PI_f / 180f
}

fun Float.toDegrees(): Float {
    return this * 180f / PI_f
}

fun Float.toTruncatedString(decimals: Int): String {
    val sign = sign(this)
    val signStr = if (sign < 0) { "-" } else { "" }
    val pos = abs(this)

    val wholePart = floor(pos).roundToInt().toString()
    val decimalPart = floor((pos - floor(pos)) * 10f.pow(decimals)).roundToInt().toString()
    return "$signStr$wholePart.${decimalPart.padStart(decimals, '0')}"
}

fun <T> MutableMap<T, Int>.addInPlace(key: T, value: Int, defaultValue: Int = 0) {
    this[key] = value + this.getOrElse(key) { defaultValue }
}

fun <T> MutableMap<T, Float>.addInPlace(key: T, value: Float, defaultValue: Float = 0f) {
    this[key] = value + this.getOrElse(key) { defaultValue }
}

fun <T> MutableMap<T, Float>.multiplyInPlace(key: T, value: Float, defaultValue: Float = 1f) {
    this[key] = value * this.getOrElse(key) { defaultValue }
}
