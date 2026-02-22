package xim.resource

import xim.math.Vector3f
import kotlin.math.floor
import kotlin.math.roundToInt

class PointList(val points: List<Vector3f>) {

    fun getSplinePosition(progress: Float): Vector3f {
        if (progress >= 1.0f) { return points.last() }

        val lineProgress = (points.size - 1) * progress
        val floorIndex = floor(lineProgress).roundToInt()
        val portionProgress = (lineProgress - floorIndex)

        val p0 = points[floorIndex]
        val p1 = points[floorIndex + 1]

        val pPrev = points.getOrElse(floorIndex - 1) { p0 }
        val pNext = points.getOrElse(floorIndex + 2) { p1 }

        return Vector3f.catmullRomSpline(pPrev = pPrev, p0 = p0, p1 = p1, pNext = pNext, t = portionProgress)
    }

    fun getLerpPosition(progress: Float): Vector3f {
        if (progress >= 1.0f) { return points.last() }

        val lineProgress = (points.size - 1) * progress
        val floorIndex = floor(lineProgress).roundToInt()
        val portionProgress = (lineProgress - floorIndex)

        val p0 = points[floorIndex]
        val p1 = points[floorIndex + 1]

        return Vector3f.lerp(a = p0, b = p1, t = portionProgress)
    }

    fun asReversed(): PointList {
        return PointList(points.reversed())
    }

}

class PointListSection(val sectionHeader: SectionHeader) : ResourceParser {

    lateinit var pointList: PointList

    override fun getResource(byteReader: ByteReader): ParserResult {
        read(byteReader)
        val resource = PointListResource(sectionHeader.sectionId, pointList)
        return ParserResult.from(resource)
    }

    private fun read(byteReader: ByteReader) {
        byteReader.offsetFromDataStart(sectionHeader, 0x0)

        val numPoints = byteReader.next32()
        expectZero(byteReader.next32())
        expectZero(byteReader.next32())
        expectZero(byteReader.next32())

        val points = ArrayList<Vector3f>()
        for (i in 0 until numPoints) {
            points.add(byteReader.nextVector3f())
            byteReader.nextFloat() // always 1f
        }

        pointList = PointList(points)
    }

}
