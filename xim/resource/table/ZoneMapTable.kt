package xim.resource.table

import xim.math.Vector2f
import xim.poc.tools.ZoneConfig
import xim.resource.ByteReader

data class ZoneMap (
    val zoneId: Int,
    val subZoneId: Int,
    val fileId: Int,
    val size: Int,
    val offset: Vector2f
)

object ZoneMapTable {

    private val table: Map<Int, Map<Int, ZoneMap>> by lazy { parse(MainDll.getZoneMapTableReader()) }

    operator fun get(zone: ZoneConfig, subZoneId: Int) : ZoneMap? {
        val zoneId = zone.customDefinition?.zoneMapId ?: zone.zoneId
        return table[zoneId]?.get(subZoneId)
    }

    private fun parse(byteReader: ByteReader): Map<Int, Map<Int, ZoneMap>> {
        val table = HashMap<Int, HashMap<Int, ZoneMap>>()

        while (byteReader.hasMore()) {
            val base = byteReader.position

            val zoneId = byteReader.next16Signed() // 0-1
            val subZoneId = byteReader.next8() // 2
            val unk = byteReader.next8() // 3
            val jTableIndex = byteReader.next8() and 0xF // 4

            val size = 2560 / byteReader.next8()
            byteReader.next8() // 6
            byteReader.next8() // 7

            val fileTableOffset = byteReader.next16Signed() // 8-9
            val fileId = getFileTableOffset(jTableIndex) + fileTableOffset

            val xOffset = byteReader.next16Signed().toFloat()
            val yOffset = byteReader.next16Signed().toFloat()

            // 10~13 unknown; maybe offsets?

            val zoneMaps = table.getOrPut(zoneId) { HashMap() }
            zoneMaps[subZoneId] = ZoneMap(zoneId = zoneId, subZoneId = subZoneId, fileId = fileId, size = size, offset = Vector2f(xOffset, yOffset))

            val hasNext = byteReader.bytes[base+0x13].toInt()
            if (hasNext == 0x0) { break }
            byteReader.position = base + 0xE
        }

        return table
    }

    private fun getFileTableOffset(jTableIndex: Int): Int {
        return when (jTableIndex) {
            0 -> 0x14C0
            1 -> 0xD02F
            2 -> 0xD147
            3 -> 0x1592
            else -> throw IllegalStateException("$jTableIndex")
        }
    }

}