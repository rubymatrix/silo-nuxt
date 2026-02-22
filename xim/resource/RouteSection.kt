package xim.resource

import xim.math.Vector3f
import xim.util.Fps
import kotlin.math.*

class RouteSegment(val start: Vector3f, val end: Vector3f, val time: Float) {

    val direction = (end - start).normalizeInPlace()

}

class Route(val segments: List<RouteSegment>) {

    // This is just for debug - the information from the Route Segment should be used instead
    private val lengthPerSegment = Fps.secondsToFrames(10)
    val totalLength = segments.size * lengthPerSegment

    fun getPosition(cumulativeFrames: Float): Vector3f {
        if (cumulativeFrames >= totalLength) {
            return segments.last().end
        }

        val segment = segments[floor(cumulativeFrames / lengthPerSegment).toInt()]
        val f = cumulativeFrames.mod(lengthPerSegment) / lengthPerSegment

        return Vector3f.lerp(segment.start, segment.end, f)
    }

    fun getFacingDir(cumulativeFrames: Float): Float {
        if (cumulativeFrames >= totalLength) {
            val dir = segments.last().direction
            return atan2(dir.x, dir.z)
        }

        val currentSegmentIndex = floor(cumulativeFrames / lengthPerSegment).toInt()
        val currentSegmentProgress = cumulativeFrames.mod(lengthPerSegment) / lengthPerSegment

        val currentSegment = segments[currentSegmentIndex]

        val dir = if (currentSegmentProgress < 0.25f && currentSegmentIndex > 0) {
            val prev = segments[currentSegmentIndex - 1]
            val f = (currentSegmentProgress + 0.25f) / 0.5f
            Vector3f.lerp(prev.direction, currentSegment.direction, smooth(f))
        } else if (currentSegmentProgress > 0.75f && currentSegmentIndex < segments.size - 1) {
            val next = segments[currentSegmentIndex + 1]
            val f = (currentSegmentProgress - 0.75f) / 0.5f
            Vector3f.lerp(currentSegment.direction, next.direction, smooth(f))
        } else {
            currentSegment.direction
        }

        return atan2(dir.x, dir.z)
    }

    private fun smooth(x: Float): Float {
        val min = smoothFn(-1f)
        val max = smoothFn(1f)

        val xMod = (x - 0.5f) * 2f
        return (smoothFn(xMod) - min) / (max - min)
    }

    private fun smoothFn(x: Float): Float {
        return 1f / (1f + E.toFloat().pow(-3f*x))
    }

}

class RouteSection(val header: SectionHeader): ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val route = parse(byteReader)
        return ParserResult.from(RouteResource(header.sectionId, route))
    }

    private fun parse(byteReader: ByteReader): Route {
        byteReader.offsetFromDataStart(header)

        expectZero32(byteReader, 4)

        val entryCount = byteReader.next32()
        val unk = byteReader.next32()

        expectZero32(byteReader, 2)

        val segments = ArrayList<RouteSegment>(entryCount)

        for (i in 0 until entryCount) {
            val segStart = byteReader.nextVector3f()
            byteReader.nextFloat()

            val segEnd = byteReader.nextVector3f()
            byteReader.nextFloat()

            val time = byteReader.nextFloat()

            byteReader.next32()
            byteReader.next32()
            byteReader.next32()

            segments += RouteSegment(segStart, segEnd, time)
        }

        return Route(segments)
    }

}