package xim.math

import js.typedarrays.Float32Array
import kotlinx.serialization.Serializable
import xim.poc.camera.CameraReference
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random

enum class Axis { X, Y, Z }

@Serializable
data class Vector3f(var x: Float, var y: Float, var z: Float) {

    constructor(other: Vector3f) : this(other.x, other.y, other.z)
    constructor() : this (0f, 0f, 0f)

    companion object {

        val UP = Vector3f(0f, -1f, 0f)

        val ZERO = Vector3f(0f, 0f, 0f)

        val ONE = Vector3f(1f, 1f, 1f)

        val X = Vector3f(1f, 0f, 0f)
        val Y = Vector3f(0f, 1f, 0f)
        val Z = Vector3f(0f, 0f, 1f)

        val South = Vector3f(0f, 0f, -1f)
        val North = Vector3f(0f, 0f, 1f)
        val East = Vector3f(1f, 0f, 0f)
        val West = Vector3f(-1f, 0f, 0f)

        val NorthEast = (North + East).normalizeInPlace()
        val NorthWest = (North + West).normalizeInPlace()
        val SouthWest = (South + West).normalizeInPlace()
        val SouthEast = (South + East).normalizeInPlace()

        val NegX = Vector3f(-1f, 0f, 0f)
        val NegY = Vector3f(0f, -1f, 0f)
        val NegZ = Vector3f(0f, 0f, -1f)

        fun distance(a: Vector3f, b: Vector3f): Float {
            return sqrt(distanceSquared(a, b))
        }

        fun distanceSquared(a: Vector3f, b: Vector3f) : Float {
            val dX = a.x - b.x
            val dY = a.y - b.y
            val dZ = a.z - b.z
            return dX*dX + dY*dY + dZ * dZ
        }

        fun lerp(a: Vector3f, b: Vector3f, t: Float) : Vector3f {
            return a * (1.0f - t) + b * t
        }

        fun catmullRomSpline(pPrev: Vector3f, p0: Vector3f, p1: Vector3f, pNext: Vector3f, t: Float): Vector3f {
            val m0 = (p1 - pPrev) * 0.5f
            val m1 = (pNext - p0) * 0.5f

            val t3 = t*t*t
            val t2 = t*t

            val c0 = p0 * (2*t3 - 3*t2 + 1)
            val c1 = m0 * (t3 - 2*t2 + t)
            val c2 = p1 * (-2*t3 + 3*t2)
            val c3 = m1 * (t3 - t2)

            return c0 + c1 + c2 + c3
        }

    }

    fun set(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }

    operator fun set(axis: Axis, value: Float) {
        when (axis) {
            Axis.X -> x = value
            Axis.Y -> y = value
            Axis.Z -> z = value
        }
    }

    fun copyFrom(scalar: Float) {
        this.x = scalar
        this.y = scalar
        this.z = scalar
    }

    fun copyFrom(vector3f: Vector3f): Vector3f {
        this.x = vector3f.x
        this.y = vector3f.y
        this.z = vector3f.z
        return this
    }

    fun addInPlace(b: Vector3f) {
        x += b.x
        y += b.y
        z += b.z
    }

    fun subtract(b: Vector3f): Vector3f {
        return Vector3f(x - b.x, y - b.y, z - b.z)
    }

    fun cross(b: Vector3f) : Vector3f {
        val s1 = y * b.z - z * b.y
        val s2 = z * b.x - x * b.z
        val s3 = x * b.y - y * b.x
        return Vector3f(s1, s2, s3)
    }

    fun dot(b: Vector3f): Float {
        return x * b.x + y * b.y + z * b.z
    }

    fun normalize(): Vector3f {
        val magnitude = magnitude()
        return Vector3f(x/magnitude, y/magnitude, z/magnitude)
    }

    fun normalizeInPlace(): Vector3f {
        val magnitude = magnitude()
        x /= magnitude
        y /= magnitude
        z /= magnitude
        return this
    }

    fun magnitude(): Float {
        return sqrt(x * x + y * y + z * z)
    }

    fun magnitudeSquare(): Float {
        return x * x + y * y + z * z
    }

    fun toTypedArray(): Float32Array {
        val arr = Float32Array(3)
        arr[0] = x
        arr[1] = y
        arr[2] = z
        return arr
    }

    operator fun plusAssign(other: Vector3f) {
        this.x += other.x
        this.y += other.y
        this.z += other.z
    }

    operator fun plusAssign(value: Float) {
        this.x += value
        this.y += value
        this.z += value
    }

    operator fun plus(vector3f: Vector3f) : Vector3f {
        return Vector3f(this.x + vector3f.x, this.y + vector3f.y, this.z + vector3f.z)
    }

    operator fun plus(value: Float) : Vector3f {
        return Vector3f(x + value, y + value, z + value)
    }

    operator fun minus(vector3f: Vector3f) : Vector3f {
        return Vector3f(this.x - vector3f.x, this.y - vector3f.y, this.z - vector3f.z)
    }

    operator fun minusAssign(other: Vector3f) {
        this.x -= other.x
        this.y -= other.y
        this.z -= other.z
    }

    operator fun times(scalar: Float) : Vector3f {
        return Vector3f(x * scalar, y * scalar, z * scalar)
    }

    operator fun times(v: Vector3f): Vector3f {
        return Vector3f(x = x * v.x, y = y * v.y, z = z * v.z)
    }

    operator fun timesAssign(scalar: Float) {
        this.x *= scalar
        this.y *= scalar
        this.z *= scalar
    }

    operator fun timesAssign(v: Vector3f) {
        this.x *= v.x
        this.y *= v.y
        this.z *= v.z
    }

    fun addX(x: Float): Vector3f {
        this.x += x
        return this
    }

    fun withY(y: Float): Vector3f {
        val copy = Vector3f().copyFrom(this)
        copy.y = y
        return copy
    }

    fun withRandomHorizontalOffset(radius: Float): Vector3f {
        val offset = Matrix4f().rotateYInPlace(Random.nextDouble(-PI, PI).toFloat()).transform(Vector3f(radius, 0f, 0f))
        return this + offset
    }

    fun toScreenSpace() : Vector2f? {
        val position = Vector4f(this, 1.0f)
        val viewMatrix = CameraReference.getInstance().getViewMatrix()
        val projMatrix = CameraReference.getInstance().getProjectionMatrix()

        viewMatrix.transformInPlace(position)
        projMatrix.transformInPlace(position)

        if (position.w <= 0) {
            return null
        }

        val output = position.perspectiveDivide()

        // Raw screen space is [-1,1], so let's normalize
        val screenSpacePos = Vector2f(output.x, output.y)
        screenSpacePos.x = (screenSpacePos.x + 1f) * 0.5f
        screenSpacePos.y = (1f - screenSpacePos.y) * 0.5f
        return screenSpacePos
    }

    operator fun get(axis: Axis): Float {
        return when (axis) {
            Axis.X -> x
            Axis.Y -> y
            Axis.Z -> z
        }
    }

    fun rotate270() {
        val tx = x
        this.x = -z
        this.z = tx
    }

}