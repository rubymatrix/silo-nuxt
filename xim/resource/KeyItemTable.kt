package xim.resource

import xim.resource.table.LoadableResource
import xim.resource.table.StringTable

typealias KeyItemId = Int

object KeyItemTable: LoadableResource {

    private val table = StringTable("ROM/175/35.DAT", bitMask = 0xFF.toByte())
    private val keyItemsById by lazy { associateBlocksById() }

    override fun preload() {
        table.preload()
    }

    override fun isFullyLoaded(): Boolean {
        return table.isFullyLoaded()
    }

    fun getName(index: KeyItemId, quantity: Int): String {
        return if (quantity == 1) { getNameSingular(index) } else { getNamePlural(index) }
    }

    fun getNameSingular(index: KeyItemId): String {
        return keyItemsById[index]?.getString(index = 4) ?: ""
    }

    fun getNamePlural(index: KeyItemId): String {
        return keyItemsById[index]?.getString(index = 5) ?: ""
    }

    fun getDescription(index: KeyItemId): String {
        return keyItemsById[index]?.getString(index = 6) ?: ""
    }

    private fun associateBlocksById(): Map<KeyItemId, StringTableBlock> {
        return table.getAll().associateBy { it.entries.first().flag }
    }

}