package xim.resource.table

import xim.poc.ItemModelSlot
import xim.poc.browser.DatLoader
import xim.poc.game.configuration.constants.armorKubiraMeikogai_26959
import xim.poc.game.configuration.constants.armorMakoraMeikogai_26961
import xim.resource.ByteReader
import xim.resource.InventoryItemInfo
import xim.resource.table.AdditionalModelMapping.additionalMappings

// https://github.com/LandSandBoat/server/blob/base/sql/item_equipment.sql
object ItemModelTable: LoadableResource {

    private lateinit var table: ByteReader
    private var preloaded = false

    override fun preload() {
        if (preloaded) { return }
        preloaded = true
        loadTable()
    }

    override fun isFullyLoaded() : Boolean {
        return this::table.isInitialized
    }

    operator fun get(item: InventoryItemInfo?) : Int {
        return getModelId(item)
    }

    fun getForcedMatches(slot: ItemModelSlot, modelId: Int): Set<ItemModelSlot> {
        return ItemModelMatcher.itemModelOverrides[modelId]?.get(slot) ?: emptySet()
    }

    private fun getModelId(item: InventoryItemInfo?) : Int {
        if (item == null) { return 0 }
        return getModelId(item.itemId)
    }

    private fun getModelId(itemId: Int) : Int {
        val additional = additionalMappings[itemId]
        if (additional != null) { return additional }

        table.position = itemId * 2
        return table.next16()
    }

    private fun loadTable() {
        DatLoader.load("landsandboat/ItemModelTable.DAT").onReady { table = it.getAsBytes() }
    }

}

private object ItemModelMatcher {

    private val swimwearOverride = mapOf(
        ItemModelSlot.Body to setOf(ItemModelSlot.Hands),
        ItemModelSlot.Legs to setOf(ItemModelSlot.Feet),
    )

    val itemModelOverrides = mapOf(
        495 to swimwearOverride,
        496 to swimwearOverride,
    )

}

private object AdditionalModelMapping {

    val additionalMappings = mapOf(
        23871 to 495,   // Hebenus Gilet
        23872 to 495,   // Hebenus Boxers
        23873 to 496,   // Hebenus Top
        23874 to 496,   // Hebenus Shorts
        26959 to 303,   // Kubira Meikogai
        26961 to 209,   // Makora Meikogai
        26962 to 51,    // Enforcer's Harness
    )

}