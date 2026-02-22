package xim.resource.table

import xim.poc.browser.DatLoader
import xim.poc.game.configuration.constants.itemCoalitionEther_5987
import xim.poc.game.configuration.constants.itemCoalitionPotion_5986
import xim.resource.ByteReader
import xim.resource.InventoryItemInfo


object ItemAnimationTable : LoadableResource {

    const val fileTableOffset = 0x1330

    private lateinit var items: Map<Int, Int>
    private var preloaded = false

    override fun preload() {
        if (preloaded) { return }
        preloaded = true
        DatLoader.load("landsandboat/ItemUsableTable.DAT").onReady { parse(it.getAsBytes()) }
    }

    override fun isFullyLoaded(): Boolean {
        return this::items.isInitialized
    }

    fun getAnimationPath(inventoryItemInfo: InventoryItemInfo): String? {
        val animationId = items[inventoryItemInfo.itemId] ?: return null
        val fileTableIndex = animationId + fileTableOffset
        return FileTableManager.getFilePath(fileTableIndex)
    }

    private fun parse(byteReader: ByteReader) {
        val items = HashMap<Int, Int>()

        while (byteReader.hasMore()) {
            val start = byteReader.position

            val id = byteReader.next16()
            val animId = byteReader.next16()

            items[id] = animId

            byteReader.position = start + 0x10
        }

        applyAdditionalSettings(items)
        this.items = items
    }

    private fun applyAdditionalSettings(items: HashMap<Int, Int>) {
        items[itemCoalitionPotion_5986.id] = 30
        items[itemCoalitionEther_5987.id] = 32
    }

}