package xim.resource

import xim.math.Vector3f
import xim.util.interpolate

class PathVertex(val position: Vector3f, val radius: Float, val unks: List<Int>) {
    val connections = ArrayList<PathVertex>()
}

class PathSegment(val start: PathVertex, val end: PathVertex) {

    init {
        start.connections += end
        end.connections += start
    }

    fun nearestPoint(point: Vector3f): Pair<Vector3f, Float> {
        val segmentDirection = (end.position - start.position).normalizeInPlace()
        val segmentToPoint = point - start.position

        val t = segmentToPoint.dot(segmentDirection)
        val segmentLength = Vector3f.distance(start.position, end.position)
        val partial = t / segmentLength

        val (center, radius) = if (partial <= 0) {
            Pair(start.position, start.radius)
        } else if (partial >= 1) {
            Pair(end.position, end.radius)
        } else {
            val position = start.position + (segmentDirection * t)
            val thickness = start.radius.interpolate(end.radius, partial)
            Pair(position, thickness)
        }

        val distanceToCenter = Vector3f.distance(center, point)
        return if (distanceToCenter <= radius) {
            // Inside the path, so distance is 0
            Pair(point, 0f)
        } else {
            val dir = (center - point).normalizeInPlace()
            val distanceToSphere = distanceToCenter - radius
            val nearestPoint = point + dir * distanceToSphere
            Pair(nearestPoint, distanceToSphere)
        }
    }

}


class Path(val segments: List<PathSegment>) {

    fun nearestPoint(position: Vector3f): Pair<Vector3f, Float> {
        return segments.map { it.nearestPoint(position) }.minBy { it.second }
    }

}

class PathSection(val sectionHeader: SectionHeader): ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val resource = read(byteReader)
        return ParserResult.from(resource)
    }

    fun read(byteReader: ByteReader): PathResource {
        byteReader.offsetFromDataStart(sectionHeader, 0x0)

        val magic = byteReader.nextString(4)
        if (!magic.startsWith("RAB")) { oops(byteReader, "It was $magic") }

        val unk0 = byteReader.next32() // always 7?
        val unk1 = byteReader.next32()
        expectZero32(byteReader)

        val verticesOffset = byteReader.next32() + sectionHeader.dataStartPosition
        val pairsOffset = byteReader.next32() + sectionHeader.dataStartPosition

        expectZero32(byteReader, count = 6)

        // Vertices
        if (byteReader.position != verticesOffset) { oops(byteReader) }

        val numVertices = byteReader.next32()
        expectZero32(byteReader, 3)

        val vertices = ArrayList<PathVertex>(numVertices)

        for (i in 0 until numVertices) {
            val position = byteReader.nextVector3f()
            val radius = byteReader.nextFloat()

            // Generally 0, but have seen some values that look like shorts
            val unks = ArrayList<Int>(4)
            for (j in 0 until 4) {
                unks += byteReader.next32()
            }

            vertices += PathVertex(position, radius, unks)
        }

        // Segments
        if (byteReader.position != pairsOffset) oops(byteReader)

        val numSegments = byteReader.next32()
        val segments = ArrayList<PathSegment>(numSegments)

        expectZero32(byteReader, count = 3)

        for (i in 0 until numSegments) {
            // TODO these might be u16 index+ u16 flag? or u24 index + u8 flag?
            val i0 = byteReader.next32() and 0xFFFFFF
            if (i0 >= numVertices || i0 < 0) { oops(byteReader, "$i0") }

            val i1 = byteReader.next32() and 0xFFFFFF
            if (i1 >= numVertices || i1 < 0) { oops(byteReader, "$i1") }

            segments += PathSegment(vertices[i0], vertices[i1])
        }

        // Remainder should just be 0
        while (byteReader.position < sectionHeader.sectionStartPosition + sectionHeader.sectionSize) {
            expectZero32(byteReader)
        }

        return PathResource(sectionHeader.sectionId, Path(segments))
    }

}