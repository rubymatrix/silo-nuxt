package xim.resource

import xim.math.Vector3f
import xim.poc.BoundingBox

data class ElevatorSettings(
    val bottomPosition: Float,
    val topPosition: Float,
)

data class ZoneInteraction (
    val position: Vector3f,
    val orientation: Vector3f,
    val size: Vector3f,
    val sourceId: DatId,
    val destId: DatId?,
    val param: Int,
    val elevatorSettings: ElevatorSettings,
    val terrainType: TerrainType?,
    val mapId: Int,
) {

    val boundingBox by lazy {
        // Avoid "flat" boxes
        val adjustedSize = Vector3f(size.x.coerceAtLeast(0.01f), size.y.coerceAtLeast(0.01f), size.z.coerceAtLeast(0.01f))
        BoundingBox.from(position, orientation, adjustedSize, verticallyCentered = true)
    }

    fun isZoneLine(): Boolean {
        return sourceId.id.first() == 'z' && destId != null
    }

    fun isZoneEntrance(): Boolean {
        return sourceId.id.first() == 'z' && destId == null
    }

    fun isFishingArea(): Boolean {
        return sourceId.id.first() == 'f'
    }

    fun isSubArea(): Boolean {
        return sourceId.id.first() == 'm'
    }

    fun isDoor(): Boolean {
        return sourceId.id.first() == '_'
    }

}

class ZoneInteractionSection(val sectionHeader: SectionHeader) : ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val interactions = read(byteReader)
        return ParserResult.from(ZoneInteractionResource(sectionHeader.sectionId, interactions))
    }

    private fun read(byteReader: ByteReader): List<ZoneInteraction> {
        byteReader.offsetFromDataStart(sectionHeader)

        val magic = byteReader.nextString(0x4)
        if (!magic.startsWith("RID")) { oops(byteReader) }

        val unk0 = byteReader.next32() // 2,4,5,6
        byteReader.position += 0x08

        val dataOffset = byteReader.next32()
        byteReader.offsetFromDataStart(sectionHeader, dataOffset)

        val numEntries = byteReader.next32()
        expectZero32(byteReader)
        expectZero32(byteReader)
        expectZero32(byteReader)

        val interactions = ArrayList<ZoneInteraction>(numEntries)

        for (i in 0 until numEntries) {
            val position = byteReader.nextVector3f()
            val orientation = byteReader.nextVector3f()
            val size = byteReader.nextVector3f()

            val sourceEventId = byteReader.nextDatId()
            val destEventId = byteReader.nextDatId().toNullIfZero()
            val param = byteReader.next32()

            val terrainFlags = byteReader.next16()
            val mapId = byteReader.next16()

            val ev0 = position.y + byteReader.next16Signed() / 256f
            val ev1 = position.y + byteReader.next16Signed() / 256f

            byteReader.next32()
            byteReader.next32()

            val terrainTypeIndex = (terrainFlags shr 0x4) and 0xF
            val terrainType = TerrainType.values().firstOrNull { it.index == terrainTypeIndex }

            interactions += ZoneInteraction(
                position = position,
                orientation = orientation,
                size = size,
                sourceId = sourceEventId,
                destId = destEventId,
                param = param,
                elevatorSettings = ElevatorSettings(bottomPosition = ev0, topPosition = ev1),
                mapId = mapId,
                terrainType = terrainType,
            )
        }

        return interactions
    }

}