package xim.poc

import xim.math.Matrix3f
import xim.math.Matrix4f
import xim.math.Vector3f
import xim.resource.CollisionObject
import xim.resource.TerrainType
import xim.resource.TriFlags
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Sphere(val center: Vector3f, val radius: Float) {

    val radiusSq = radius * radius

    companion object {

        fun fromExtents(bottomLeft: Vector3f, topRight: Vector3f) : Sphere {
            val width = abs(topRight.x - bottomLeft.x)
            val height = abs(topRight.y - bottomLeft.y)
            val depth = abs(topRight.z - bottomLeft.z)

            val center = Vector3f(bottomLeft.x + 0.5f * width, bottomLeft.y + 0.5f * height, bottomLeft.z + 0.5f * depth)
            val radius = (topRight - center).magnitude()

            return Sphere(center, radius)
        }

        fun distance(a: Sphere, b: Sphere): Float {
            return (a.center - b.center).magnitude()
        }

        fun intersects(a: Sphere, b: Sphere) : Boolean {
            val distance = (a.center - b.center).magnitudeSquare()
            val radiusSum = a.radius + b.radius
            return distance <= radiusSum * radiusSum
        }

    }

}

class ExtentsBuilder {

    private val bottomLeft = Vector3f(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,)
    private val topRight = Vector3f(Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,)

    fun track(v: Vector3f): Vector3f {
        bottomLeft.x = min(bottomLeft.x, v.x)
        bottomLeft.y = min(bottomLeft.y, v.y)
        bottomLeft.z = min(bottomLeft.z, v.z)

        topRight.x = max(topRight.x, v.x)
        topRight.y = max(topRight.y, v.y)
        topRight.z = max(topRight.z, v.z)

        return v
    }

    fun toAxisAlignedBoundingBox(): AxisAlignedBoundingBox {
        return AxisAlignedBoundingBox(bottomLeft, topRight)
    }

    fun toBoundingBox(): BoundingBox {
        return BoundingBox.extents(bottomLeft, topRight)
    }

    fun toBoundingSphere(): Sphere {
        return Sphere.fromExtents(bottomLeft, topRight)
    }

    fun width(): Float {
        return abs(bottomLeft.x - topRight.x)
    }

    fun height(): Float {
        return abs(bottomLeft.y - topRight.y)
    }

    fun depth(): Float {
        return abs(bottomLeft.z - topRight.z)
    }

}

interface Box {
    fun getAxes(): List<Vector3f>
    fun getVertices(): List<Vector3f>
    fun getCenter(): Vector3f
    fun getRadiusSq(): Float

    fun getPlanes(): List<BoxPlane> {
        val axes = getAxes()
        val points = getVertices()

        return listOf(
            BoxPlane(normal = axes[0], p0 = points[0], p1 = points[3], p2 = points[4], p3 = points[7]), // Left
            BoxPlane(normal = axes[0], p0 = points[1], p1 = points[2], p2 = points[5], p3 = points[6]), // Right
            BoxPlane(normal = axes[1], p0 = points[0], p1 = points[1], p2 = points[3], p3 = points[2]), // Bottom
            BoxPlane(normal = axes[1], p0 = points[4], p1 = points[5], p2 = points[7], p3 = points[6]), // Top
            BoxPlane(normal = axes[2], p0 = points[0], p1 = points[1], p2 = points[4], p3 = points[5]), // Near
            BoxPlane(normal = axes[2], p0 = points[3], p1 = points[2], p2 = points[7], p3 = points[6]), // Far
        )
    }

}

class AxisAlignedBoundingBox(in0: Vector3f, in1: Vector3f) : Box {

    companion object {

        private val axes = listOf(Vector3f.X, Vector3f.Y, Vector3f.Z)

        fun scaled(scale: Vector3f, position: Vector3f, verticallyCentered: Boolean): AxisAlignedBoundingBox {

            return if (verticallyCentered) {
                val min = Vector3f(position.x - (scale.x * 0.5f), position.y - (scale.y * 0.5f), position.z - (scale.z * 0.5f))
                val max = Vector3f(position.x + (scale.x * 0.5f), position.y + (scale.y * 0.5f), position.z + (scale.z * 0.5f))
                AxisAlignedBoundingBox(min, max)
            } else {
                val min = Vector3f(position.x - (scale.x * 0.5f), position.y - scale.y, position.z - (scale.z * 0.5f))
                val max = Vector3f(position.x + (scale.x * 0.5f), position.y, position.z + (scale.z * 0.5f))
                AxisAlignedBoundingBox(min, max)
            }
        }

        fun intersects(a: AxisAlignedBoundingBox, b: AxisAlignedBoundingBox): Boolean {
            return  a.min.x <= b.max.x &&
                    a.max.x >= b.min.x &&
                    a.min.y <= b.max.y &&
                    a.max.y >= b.min.y &&
                    a.min.z <= b.max.z &&
                    a.max.z >= b.min.z
        }
    }

    val points: List<Vector3f>
    val min: Vector3f
    val max: Vector3f

    private val center: Vector3f
    private val radiusSq: Float

    init {
        val minX = min(in0.x, in1.x)
        val minY = min(in0.y, in1.y)
        val minZ = min(in0.z, in1.z)

        val maxX = max(in0.x, in1.x)
        val maxY = max(in0.y, in1.y)
        val maxZ = max(in0.z, in1.z)

        min = Vector3f(minX, minY, minZ)
        max = Vector3f(maxX, maxY, maxZ)

        points = listOf(
            min,
            Vector3f(maxX, minY, minZ),
            Vector3f(maxX, minY, maxZ),
            Vector3f(minX, minY, maxZ),

            Vector3f(minX, maxY, minZ),
            Vector3f(maxX, maxY, minZ),
            max,
            Vector3f(minX, maxY, maxZ),
        )

        center = (max + min) * 0.5f
        radiusSq = Vector3f.distanceSquared(max, center)
    }

    override fun getAxes(): List<Vector3f> {
        return axes
    }

    override fun getVertices(): List<Vector3f> {
        return points
    }

    override fun getRadiusSq(): Float {
        return radiusSq
    }

    override fun getCenter(): Vector3f {
        return center
    }

}

class BoundingBox(val vertices: List<Vector3f> = ArrayList(8)): Box {

    companion object {

        fun skewed(bottomLeft: Vector3f, topRight: Vector3f): BoundingBox {
            return BoundingBox(
                vertices = listOf(
                    Vector3f(bottomLeft.x, bottomLeft.y, bottomLeft.z),
                    Vector3f(topRight.x, bottomLeft.y, bottomLeft.z),
                    Vector3f(topRight.x, bottomLeft.y, topRight.z),
                    Vector3f(bottomLeft.x, bottomLeft.y, topRight.z),

                    Vector3f(bottomLeft.x, topRight.y, bottomLeft.z),
                    Vector3f(topRight.x, topRight.y, bottomLeft.z),
                    Vector3f(topRight.x, topRight.y, topRight.z),
                    Vector3f(bottomLeft.x, topRight.y, topRight.z),
                )
            )
        }

        fun extents(bottomLeft: Vector3f, topRight: Vector3f): BoundingBox {
            val width = abs(topRight.x - bottomLeft.x)
            val height = abs(topRight.y - bottomLeft.y)
            val depth = abs(topRight.z - bottomLeft.z)

            val position = Vector3f(bottomLeft.x + 0.5f * width, bottomLeft.y, bottomLeft.z + 0.5f * depth)
            return scaled(Vector3f(width, height, depth), position)
        }

        fun scaled(scale: Vector3f, position: Vector3f, verticallyCentered: Boolean = false): BoundingBox {
            val vertices: ArrayList<Vector3f> = ArrayList(8)

            val yBottom = if (verticallyCentered) { position.y + scale.y/2f } else { position.y }
            val yTop = if (verticallyCentered) { position.y - scale.y/2f } else { position.y - scale.y }

            // bottom
            vertices.add(Vector3f(position.x - scale.x/2f, yBottom, position.z - scale.z/2f))
            vertices.add(Vector3f(position.x + scale.x/2f, yBottom, position.z - scale.z/2f))
            vertices.add(Vector3f(position.x + scale.x/2f, yBottom, position.z + scale.z/2f))
            vertices.add(Vector3f(position.x - scale.x/2f, yBottom, position.z + scale.z/2f))

            // top
            vertices.add(Vector3f(position.x - scale.x/2f, yTop, position.z - scale.z/2f))
            vertices.add(Vector3f(position.x + scale.x/2f, yTop, position.z - scale.z/2f))
            vertices.add(Vector3f(position.x + scale.x/2f, yTop, position.z + scale.z/2f))
            vertices.add(Vector3f(position.x - scale.x/2f, yTop, position.z + scale.z/2f))

            return BoundingBox(vertices)
        }

        fun from(center: Vector3f, orientation: Vector3f, scale: Vector3f, verticallyCentered: Boolean): BoundingBox {
            return unit(verticallyCentered).transform(
                Matrix4f()
                .translateInPlace(center)
                .rotateZYXInPlace(orientation)
                .scaleInPlace(scale))
        }

        fun unit(verticallyCentered: Boolean = false): BoundingBox {
            return scaled(Vector3f.ONE, Vector3f.ZERO, verticallyCentered = verticallyCentered)
        }

    }

    private val extentTracker by lazy {
        val extentTracker = ExtentsBuilder()
        for (i in 0 until 8) { extentTracker.track(vertices[i]) }
        extentTracker
    }

    private val sphere by lazy {
        extentTracker.toBoundingSphere()
    }

    private val axes by lazy {
        val axes = ArrayList<Vector3f>(3)

        val center = (vertices[0] + vertices[6]) * 0.5f

        val leftCenter = (vertices[0] + vertices[7]) * 0.5f
        val topCenter = (vertices[4] + vertices[6]) * 0.5f
        val farCenter = (vertices[3] + vertices[6]) * 0.5f

        val left = (leftCenter - center)
        axes += if (left.magnitudeSquare() > 1e-7) { left.normalizeInPlace() } else { Vector3f.X }

        val up = (topCenter - center)
        axes += if (up.magnitudeSquare() > 1e-7) { up.normalizeInPlace() } else { Vector3f.Y }

        val forward = (farCenter - center)
        axes += if (forward.magnitudeSquare() > 1e-7) { forward.normalizeInPlace() } else { Vector3f.Z }

        axes
    }

    fun transform(transform: Matrix4f): BoundingBox {
        return BoundingBox(vertices.map { transform.transform(it) } )
    }

    fun toBoundingSphere(): Sphere {
        return sphere
    }

    fun width(): Float {
        return extentTracker.width()
    }

    fun height(): Float {
        return extentTracker.height()
    }

    override fun getAxes(): List<Vector3f> {
        return axes
    }

    override fun getVertices(): List<Vector3f> {
        return vertices
    }

    override fun getRadiusSq(): Float {
        return sphere.radiusSq
    }

    override fun getCenter(): Vector3f {
        return sphere.center
    }

}

class SkewBoundingBox(val positionNear: Vector3f, val positionFar: Vector3f, val scale: Float, val farScale: Float = scale) {

    val nearBottomLeft: Vector3f
    val nearBottomRight: Vector3f
    val nearTopLeft: Vector3f
    val nearTopRight: Vector3f
    val farBottomLeft: Vector3f
    val farBottomRight: Vector3f
    val farTopLeft: Vector3f
    val farTopRight: Vector3f

    init {
        val ray = (positionFar - positionNear).normalize()
        val leftVec = Vector3f.UP.cross(ray)
        if (leftVec.magnitudeSquare() < 0.001f) {
            leftVec.copyFrom(Vector3f.X.cross(ray))
        }
        leftVec.normalizeInPlace()
        val upVec = ray.cross(leftVec).normalize()

        val nearS = scale/2f
        val farS = farScale/2f

        nearBottomLeft =    positionNear + (leftVec * nearS) - (upVec * nearS)
        nearBottomRight =   positionNear - (leftVec * nearS) - (upVec * nearS)
        nearTopLeft =       positionNear + (leftVec * nearS) + (upVec * nearS)
        nearTopRight =      positionNear - (leftVec * nearS) + (upVec * nearS)

        farBottomLeft =     positionFar + (leftVec * farS) - (upVec * farS)
        farBottomRight =    positionFar - (leftVec * farS) - (upVec * farS)
        farTopLeft =        positionFar + (leftVec * farS) + (upVec * farS)
        farTopRight =       positionFar - (leftVec * farS) + (upVec * farS)
    }

    fun toBox(): Box {
        return BoundingBox(listOf(nearBottomLeft, nearBottomRight, farBottomRight, farBottomLeft, nearTopLeft, nearTopRight, farTopRight, farTopLeft))
    }

}

class Triangle(val t0: Vector3f, val t1: Vector3f, val t2: Vector3f, val normal: Vector3f, val material: TriFlags, val type: TerrainType, val obj: CollisionObject? = null) {

    val vertices = Array(3) { Vector3f() }

    init {
        vertices[0].copyFrom(t0)
        vertices[1].copyFrom(t1)
        vertices[2].copyFrom(t2)
    }

    fun transform(obj: CollisionObject, transform: Matrix4f, invTransposeTransform: Matrix3f): Triangle {
        val rt0 = transform.transform(t0, w = 1f)
        val rt1 = transform.transform(t1, w = 1f)
        val rt2 = transform.transform(t2, w = 1f)
        val rNormal = invTransposeTransform.transform(normal).normalizeInPlace()

        // Fix the winding of the triangle if it was reversed
        val crossNormal = (rt0 - rt1).cross(rt1 - rt2).normalizeInPlace()
        val windingCheck = crossNormal.dot(rNormal)
        val v = if (windingCheck > 0f) { listOf(rt2, rt1, rt0) } else { listOf(rt0, rt1, rt2) }

        return Triangle(t0 = v[0], t1 = v[1], t2 = v[2], normal = rNormal, material = material, type = type, obj = obj)
    }

    fun height(): Float {
        val maxH = max(max(t0.y, t1.y), t2.y)
        val minH = min(min(t0.y, t1.y), t2.y)
        return maxH - minH
    }

    fun getEdges() : List<Vector3f> {
        return listOf(
            t1.subtract(t0),
            t2.subtract(t1),
            t0.subtract(t2),
        )
    }

}


class Ray(val origin: Vector3f, val direction: Vector3f)

class BoxPlane(val normal: Vector3f, val p0: Vector3f, val p1: Vector3f, val p2: Vector3f, val p3: Vector3f)