package xim.math

import js.typedarrays.Float32Array

class Matrix3f {

    /*
        0 3 6
        1 4 7
        2 5 8
     */
    val m = Float32Array(9)

    init {
        identity()
    }

    companion object {
        fun truncate(m4: Matrix4f) : Matrix3f {
            val m = Matrix3f()
            m.m[0] = m4.m[0]
            m.m[1] = m4.m[1]
            m.m[2] = m4.m[2]

            m.m[3] = m4.m[4]
            m.m[4] = m4.m[5]
            m.m[5] = m4.m[6]

            m.m[6] = m4.m[8]
            m.m[7] = m4.m[9]
            m.m[8] = m4.m[10]
            return m
        }
    }

    fun identity() {
        for (i in 0 until 9) {
            m[i] = 0f
        }

        m[0] = 1f
        m[4] = 1f
        m[8] = 1f
    }

    fun scaleInPlace(x: Float, y: Float): Matrix3f {
        val transform = Matrix3f()
        transform.m[0] *= x
        transform.m[4] *= y
        multiplyInPlace(transform)
        return this
    }

    fun translateInPlace(x: Float, y: Float): Matrix3f {
        val transform = Matrix3f()
        transform.m[6] += x
        transform.m[7] += y
        multiplyInPlace(transform)
        return this
    }

    fun transpose(): Matrix3f {
        val t = Matrix3f()

        t.m[0] = m[0]
        t.m[1] = m[3]
        t.m[2] = m[6]

        t.m[3] = m[1]
        t.m[4] = m[4]
        t.m[5] = m[7]

        t.m[6] = m[2]
        t.m[7] = m[5]
        t.m[8] = m[8]

        return t
    }

    fun invert(): Matrix3f {
        val invDet = 1.0f / determinate()

        val i = Matrix3f()

        // calculate adjugate matrix
        i.m[0] =   m[4]*m[8] - m[7]*m[5]
        i.m[1] = -(m[1]*m[8] - m[7]*m[2])
        i.m[2] =   m[1]*m[5] - m[4]*m[2]

        i.m[3] = -(m[3]*m[8] - m[6]*m[5])
        i.m[4] =   m[0]*m[8] - m[6]*m[2]
        i.m[5] = -(m[0]*m[5] - m[2]*m[3])

        i.m[6] =   m[3]*m[7] - m[6]*m[4]
        i.m[7] = -(m[0]*m[7] - m[1]*m[6])
        i.m[8] =   m[0]*m[4] - m[1]*m[3]

        // multiply by invDet to get the inverse
        for (p in 0 until 9) {
            i.m[p] *= invDet
        }

        return i
    }

    fun determinate(): Float {
        val a =   m[4]*m[8] - m[7]*m[5]
        val b = -(m[1]*m[8] - m[7]*m[2])
        val c =   m[1]*m[5] - m[4]*m[2]
        return m[0] * a + m[3] * b + m[6] * c
    }

    fun multiplyInPlace(o: Matrix3f): Matrix3f {
        multiply(o, this)
        return this
    }

    fun multiply(o: Matrix3f, store: Matrix3f) {
        val data = Float32Array(9)

        for (row in 0 until 3) {
            for (col in 0 until 3) {
                data[col*3 + row] = dot(row=row, col=col, o=o)
            }
        }

        store.m.set(data)
    }

    fun transform(v: Vector3f): Vector3f {
        val x = (m[0] * v.x + m[3] * v.y + m[6] * v.z)
        val y = (m[1] * v.x + m[4] * v.y + m[7] * v.z)
        val z = (m[2] * v.x + m[5] * v.y + m[8] * v.z)
        return Vector3f(x, y, z)
    }

    private fun dot(row: Int, col: Int, o: Matrix3f): Float {
        val a = m[row + 0*3] * o.m[3*col + 0]
        val b = m[row + 1*3] * o.m[3*col + 1]
        val c = m[row + 2*3] * o.m[3*col + 2]
        return a+b+c
    }

}