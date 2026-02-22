package xim.poc

import xim.math.Vector3f

data class Plane (val normal: Vector3f, val constant: Float) {

    constructor(normal: Vector3f, point: Vector3f) : this(normal, -normal.dot(point))

    constructor(p0: Vector3f, p1: Vector3f, p2: Vector3f) : this((p1 - p0).cross(p2 - p0), p0)

}

class Frustum(val planes: Array<Plane>) {

    constructor(left: Plane, right: Plane, bottom: Plane, top: Plane, near: Plane, far: Plane) : this(arrayOf(near, far, left, right, bottom, top) )

    fun intersects(boundingBox: List<Vector3f>) : Boolean {
         for (plane in planes) {
            var inside = false

            for (point in boundingBox) {
                val projection = plane.normal.dot(point) + plane.constant
                if (projection >= 0) {
                    inside = true
                    break
                }
            }

            if (!inside) {
                return false
            }
        }

        return true
    }

    fun getNearPlane(): Plane {
        return planes[0]
    }

}