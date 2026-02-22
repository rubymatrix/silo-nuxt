package xim.resource

import xim.math.Vector3f

class WeaponTrace(
    val frameScale: Float,
    val rowStartTime: Int,
    val rowEndTime: Int,
    val endTime: Int,
    val numVertices: Int,
    val topVertices: List<Vector3f>,
    val bottomVertices: List<Vector3f>,
)

class WeaponTraceSection(val sectionHeader: SectionHeader): ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val resource = read(byteReader)
        return ParserResult.from(resource)
    }

    private fun read(byteReader: ByteReader): WeaponTraceResource {
        byteReader.position = sectionHeader.dataStartPosition

        // 0x0
        val numVertices = byteReader.next8()
        val frameScale = 30f / byteReader.next8()
        val modelJoint0 = byteReader.next8() // Seemingly unused
        val modelJoint1 = byteReader.next8() // Seemingly unused
        val scale = byteReader.nextVector3f()

        // 0x10
        val rowStartTime = byteReader.next8()
        val rowEndTime = byteReader.next8()
        val endTime = byteReader.next8()
        val u7 = byteReader.next8() // Always 0xCD?

        val topsStart = byteReader.next32() + sectionHeader.dataStartPosition
        val bottomsStart = byteReader.next32() + sectionHeader.dataStartPosition
        byteReader.next32() // Always 0x00CDCD00

        // 0x20 - vertex data
        if (byteReader.position != topsStart) { oops(byteReader, "Unexpected tops position") }
        val tops = readVertices(byteReader, numVertices = numVertices, scale = scale)

        if (byteReader.position != bottomsStart) { oops(byteReader, "Unexpected bottoms position") }
        val bottoms = readVertices(byteReader, numVertices = numVertices, scale = scale)

        return WeaponTraceResource(sectionHeader.sectionId, WeaponTrace(
            frameScale = frameScale,
            rowStartTime = rowStartTime,
            rowEndTime = rowEndTime,
            endTime = endTime,
            numVertices = numVertices,
            topVertices = tops,
            bottomVertices = bottoms,
        ))
    }

    private fun readVertices(byteReader: ByteReader, numVertices: Int, scale: Vector3f): List<Vector3f> {
        val vertices = ArrayList<Vector3f>(numVertices)

        for (i in 0 until numVertices) {
            val x = byteReader.next16Signed() * scale.x
            val y = byteReader.next16Signed() * scale.y
            val z = byteReader.next16Signed() * scale.z
            byteReader.next16Signed() // Always 1 - can ignore

            vertices += Vector3f(x, y, z)
        }

        return vertices
    }

}