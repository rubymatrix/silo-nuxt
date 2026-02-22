package xim.resource.table

import xim.poc.browser.DatLoader
import xim.resource.BasicStringTableParser

class BasicStringTable(val resourceName: String, val bitMask: Byte = 0) : LoadableResource {

    private lateinit var names: MutableList<String>
    private var preloaded = false

    override fun preload() {
        if (preloaded) { return }
        preloaded = true
        loadTable()
    }

    override fun isFullyLoaded() : Boolean {
        return this::names.isInitialized
    }

    operator fun get(id: Int): String {
        return names[id]
    }

    fun getAll(): List<String> {
        return names
    }

    fun mutate(index: Int, value: String) {
        names[index] = value
    }

    private fun loadTable() {
        DatLoader.load(resourceName).onReady { names = BasicStringTableParser.read(bitMask = bitMask, byteReader = it.getAsBytes()).toMutableList() }
    }

}