package xim.poc.game.configuration.v0

import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.SlottedAugment
import xim.poc.game.configuration.v0.interactions.ItemId
import kotlin.random.Random

fun interface MysteryValueProvider {
    fun getRange(itemLevel: Int, materialLevel: Int): Pair<Int, Int>
}

class MysteryItemTierRange(val upperValueScalingFn: (Int) -> Int): MysteryValueProvider {

    companion object {
        val x1 = MysteryItemTierRange { (it * 1) }
        val x2 = MysteryItemTierRange { (it * 2) }
        val x3 = MysteryItemTierRange { (it * 3) }
        val x4 = MysteryItemTierRange { (it * 4) }
        val x5 = MysteryItemTierRange { (it * 5) }
        val x10 = MysteryItemTierRange { (it * 10) }
    }

    override fun getRange(itemLevel: Int, materialLevel: Int): Pair<Int, Int> {
        val tier = (itemLevel / 5) + 1
        return 1 to upperValueScalingFn.invoke(tier)
    }
}

object MysteryMeldRanges {

    private const val maxRandomDelta = -4

    fun getRange(inventoryItem: InventoryItem, upgradeMaterialId: ItemId): Pair<Int, Int>? {
        val upgradeMaterialDefinition = ItemDefinitions.definitionsById[upgradeMaterialId] ?: return null
        val upgradeMysterySlot = upgradeMaterialDefinition.mysterySlot ?: return null
        return upgradeMysterySlot.valueProvider.getRange(ItemDefinitions[inventoryItem].internalLevel, upgradeMaterialDefinition.internalLevel)
    }

    fun getRandomValue(inventoryItem: InventoryItem, upgradeMaterialId: ItemId): Int {
        val (minimum, maximum) = getRange(inventoryItem, upgradeMaterialId) ?: return 0
        return Random.nextInt(minimum, maximum + 1)
    }

    fun getRandomSlots(inventoryItem: InventoryItem, count: Int): List<SlottedAugment> {
        val itemLevel = ItemDefinitions[inventoryItem].internalLevel

        return ItemDefinitions.definitionsById.filterValues { it.mysterySlot != null }
            .filterValues { itemLevel - it.internalLevel > maxRandomDelta }
            .values
            .shuffled()
            .take(count)
            .map { SlottedAugment(it.mysterySlot!!.augmentId, getRandomValue(inventoryItem, it.id)) }
    }

}