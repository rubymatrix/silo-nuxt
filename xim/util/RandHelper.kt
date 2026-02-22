package xim.util

import kotlin.math.*
import kotlin.random.Random

object RandHelper {
    val random = Random(0)

    fun posRand(max: Float): Float {
        if (max == 0f) return 0f
        return random.nextDouble(0.0, max.toDouble()).toFloat()
    }

    fun halfRand(): Float {
        return random.nextDouble(-0.5, 0.5).toFloat()
    }

    fun rand(): Float {
        return random.nextDouble(-1.0, 1.0).toFloat()
    }

    fun normal(standardDeviation: Float = 1f): Float {
        // Box-Muller Transform:
        val phi = 2 * PI * Random.nextDouble()
        val r = sqrt(-2.0 * log(1.0 - Random.nextDouble(), base = E))
        return (r * cos(phi)).toFloat() * standardDeviation
    }

    fun sign(): Float {
        return rand().sign
    }

}