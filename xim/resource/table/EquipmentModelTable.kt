package xim.resource.table

import xim.poc.ItemModelSlot
import xim.poc.RaceGenderConfig
import xim.resource.ByteReader
import xim.util.OnceLogger

private data class TableEntry(val tableOffset: Int, val entryCount: Int)

private data class RaceGenderTable(val entries: Map<ItemModelSlot, List<TableEntry>>)

object EquipmentModelTable {

    private val tables: Map<RaceGenderConfig, RaceGenderTable> by lazy { parse(MainDll.getEquipmentLookupTable()) }

    fun getItemModelPath(raceGenderConfig: RaceGenderConfig, itemModelSlot: ItemModelSlot, itemModelId: Int): String? {
        val table = tables[raceGenderConfig] ?: return null
        val subTable = table.entries[itemModelSlot] ?: return null

        var cumulativeEntryCount = 0

        for (subTableEntry in subTable) {
            if (itemModelId >= cumulativeEntryCount + subTableEntry.entryCount ) {
                cumulativeEntryCount += subTableEntry.entryCount
                continue
            }

            val offset = subTableEntry.tableOffset + (itemModelId - cumulativeEntryCount)
            return FileTableManager.getFilePath(offset)
                .also { OnceLogger.info("[${raceGenderConfig.name}][$itemModelSlot][$itemModelId] -> $it") }
        }

        OnceLogger.error("Failed to evaluate: [$raceGenderConfig] -> [$itemModelSlot] -> [$itemModelId]")
        return null
    }

    fun getNumEntries(raceGenderConfig: RaceGenderConfig, itemModelSlot: ItemModelSlot): Int {
        val table = tables[raceGenderConfig] ?: return 0
        val subTable = table.entries[itemModelSlot] ?: return 0
        return subTable.sumOf { it.entryCount }
    }

    private fun parse(byteReader: ByteReader): Map<RaceGenderConfig, RaceGenderTable> {
        val tables = HashMap<RaceGenderConfig, RaceGenderTable>()

        for (config in RaceGenderConfig.values()) {
            byteReader.position = 0x1B0 * (config.equipmentTableIndex - 1)
            tables[config] = parseRaceGenderTable(byteReader)
        }

        return tables
    }

    private fun parseRaceGenderTable(byteReader: ByteReader): RaceGenderTable {
        val basePosition = byteReader.position
        val table = HashMap<ItemModelSlot, List<TableEntry>>()

        for (slot in ItemModelSlot.values()) {
            byteReader.position = basePosition + 0x30 * (slot.prefix shr 0xC)
            val entries = ArrayList<TableEntry>(6)

            for (i in 0 until 6) {
                val entry = TableEntry(tableOffset = byteReader.next32(), entryCount = byteReader.next32())
                if (entry.tableOffset == 0x0) { continue }
                entries += entry
            }

            table[slot] = entries
        }

        return RaceGenderTable(entries = table)
    }

}