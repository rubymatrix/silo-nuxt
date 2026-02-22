package xim.math

data class Vector4f(var x: Float, var y: Float, var z: Float, var w: Float) {

    companion object {
        fun fromPosition(pos: Vector3f) : Vector4f {
            return Vector4f(pos.x, pos.y, pos.z, 1f)
        }
    }

    constructor() : this(0f, 0f, 0f, 1f)

    constructor(vector3f: Vector3f, float: Float) : this (vector3f.x, vector3f.y, vector3f.z, float)

    fun copyFrom(x: Float, y: Float, z: Float, w: Float) {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
    }

    fun copyFrom(other: Vector4f) {
        x = other.x
        y = other.y
        z = other.z
        w = other.w
    }

    fun perspectiveDivide(): Vector3f {
        return Vector3f(x/w, y/w, z/w)
    }

    operator fun plus(other: Vector4f) : Vector4f {
        return Vector4f(x + other.x, y + other.y, z + other.z, w + other.w)
    }

    operator fun minus(other: Vector4f) : Vector4f {
        return Vector4f(x - other.x, y - other.y, z - other.z, w - other.w)
    }

}