package xim.resource.table

import xim.poc.browser.DatLoader
import xim.resource.StringTableBlock
import xim.resource.StringTableEntry
import xim.resource.StringTableParser

class StringTable(val resourceName: String, val bitMask: Byte = 0): LoadableResource {

    private lateinit var names: MutableList<StringTableBlock>
    private var preloaded = false

    override fun preload() {
        if (preloaded) { return }
        preloaded = true
        loadTable()
    }

    override fun isFullyLoaded() : Boolean {
        return this::names.isInitialized
    }

    fun getAll(): List<StringTableBlock> {
        return names
    }

    fun getAllFirst(): List<String> {
        return names.map { it.entries.first().string }
    }

    fun first(id: Int): String = get(id)?.first() ?: ""

    fun mutate(index: Int, values: List<String>) {
        val entries = values.map { StringTableEntry(flag = 0, string = it) }
        names[index] = StringTableBlock(entries)
    }

    operator fun get(id: Int): StringTableBlock? {
        return names.getOrNull(id)
    }

    private fun loadTable() {
        DatLoader.load(resourceName).onReady { names = StringTableParser.read(bitMask = bitMask, byteReader = it.getAsBytes()).toMutableList() }
    }

}

operator fun StringTableBlock?.get(index: Int): String {
    return this?.getStringOrBlank(index) ?: ""
}