package xim.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.sqrt

data class Quaternion(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 0f,
) {

    constructor(o: Quaternion): this(o.x, o.y, o.z, o.w)

    companion object {

        fun multiplyAndStore(qa: Quaternion, qb: Quaternion, store: Quaternion) {
            val wNew = qa.w*qb.w - qa.x*qb.x - qa.y *qb.y - qa.z*qb.z
            val xNew = qa.w*qb.x + qa.x*qb.w + qa.y *qb.z - qa.z*qb.y
            val yNew = qa.w*qb.y - qa.x*qb.z + qa.y *qb.w + qa.z*qb.x
            val zNew = qa.w*qb.z + qa.x*qb.y - qa.y *qb.x + qa.z*qb.w

            store.x = xNew
            store.y = yNew
            store.z = zNew
            store.w = wNew
        }

        fun dotProduct(qa: Quaternion, qb: Quaternion): Float {
            return (qa.x*qb.x) + (qa.y*qb.y) + (qa.z*qb.z) + (qa.w*qb.w)
        }

        fun nlerp(qa: Quaternion, qb: Quaternion, t: Float): Quaternion {
            val dot = dotProduct(qa, qb)
            val r = if (dot < 0) (qb * -1f) else { qb }
            return (qa * (1.0f - t) + r * t).normalizeInPlace()
        }

        fun slerp(qa: Quaternion, qb: Quaternion, t: Float) : Quaternion {
            val cosHalfTheta = dotProduct(qa, qb)
            if (abs(cosHalfTheta) >= 1f) {
                return qa
            }

            val qaA = if (cosHalfTheta < 0) { qa * -1f } else { qa }

            val halfTheta = acos(abs(cosHalfTheta))
            val sinHalfTheta = sin(halfTheta)
            if (abs(sinHalfTheta) < 0.001f) {
                return qaA * 0.5f + qb * 0.5f
            }

            val a = sin((1.0f - t) * halfTheta) / sinHalfTheta
            val b = sin(t * halfTheta) / sinHalfTheta
            return qaA * a + qb * b
        }

    }

    fun normalize(): Quaternion {
        val invMagnitude = 1f / sqrt(w*w + x*x + y*y + z*z)
        return Quaternion(x = x*invMagnitude, y = y*invMagnitude, z = z*invMagnitude, w = w*invMagnitude)
    }

    fun normalizeInPlace(): Quaternion {
        val invMagnitude = 1f / sqrt(w*w + x*x + y*y + z*z)
        w *= invMagnitude
        x *= invMagnitude
        y *= invMagnitude
        z *= invMagnitude
        return this
    }

    fun toMat4(): Matrix4f {
        val mat4 = Matrix4f()
        val m = mat4.m

        m[0] = 1 - 2 * y * y - 2 * z * z;
        m[1] = 2 * x * y + 2 * w * z;
        m[2] = 2 * x * z - 2 * w * y;

        m[4] = 2 * x * y - 2 * w * z;
        m[5] = 1 - 2 * x * x - 2 * z * z;
        m[6] = 2 * y * z + 2 * w * x;

        m[8] = 2 * x * z + 2 * w * y;
        m[9] = 2 * y * z - 2 * w * x;
        m[10] = 1 - 2 * x * x - 2 * y * y;

        m[15] = 1f

        return mat4
    }

    operator fun times(q: Quaternion): Quaternion {
        return Quaternion(
            w = w*q.w - x*q.x - y *q.y - z*q.z,
            x = w*q.x + x*q.w + y *q.z - z*q.y,
            y = w*q.y - x*q.z + y *q.w + z*q.x,
            z = w*q.z + x*q.y - y *q.x + z*q.w,
        )
    }

    operator fun timesAssign(q: Quaternion) {
        val wNew = w*q.w - x*q.x - y *q.y - z*q.z
        val xNew = w*q.x + x*q.w + y *q.z - z*q.y
        val yNew = w*q.y - x*q.z + y *q.w + z*q.x
        val zNew = w*q.z + x*q.y - y *q.x + z*q.w

        x = xNew
        y = yNew
        z = zNew
        w = wNew
    }

    operator fun times(constant: Float) : Quaternion {
        return Quaternion(x*constant, y*constant, z*constant, w*constant)
    }

    operator fun div(constant: Float) : Quaternion {
        return Quaternion(x/constant, y/constant, z/constant, w/constant)
    }

    operator fun plus(other: Quaternion) : Quaternion {
        return Quaternion(
            x = this.x + other.x,
            y = this.y + other.y,
            z = this.z + other.z,
            w = this.w + other.w,
        )
    }

}