package xim.poc.game.actor.components

import kotlinx.serialization.Serializable
import xim.poc.game.*
import xim.poc.game.configuration.constants.SkillId
import xim.poc.ui.ShiftJis
import xim.resource.EquipSlot
import xim.resource.InventoryItems
import xim.util.addInPlace
import kotlin.math.min
import kotlin.random.Random

typealias InternalItemId = Long
typealias ItemAugmentId = Int

@Serializable
data class CapacityAugment(
    var augmentId: AugmentId,
    var potency: Int,
)

@Serializable
data class CapacityAugments(
    var capacityRemaining: Int = 0,
    var augments: MutableMap<AugmentId, CapacityAugment> = HashMap(),
) {

    fun add(augmentId: AugmentId, potency: Int) {
        val augment = getOrCreate(augmentId)
        augment.potency += potency
    }

    fun getOrCreate(augmentId: AugmentId, potency: Int = 0): CapacityAugment {
        return augments.getOrPut(augmentId) { CapacityAugment(augmentId = augmentId, potency = potency) }
    }

}

@Serializable
data class SlottedAugment(val augmentId: AugmentId, val potency: Int)

@Serializable
data class SlottedAugments(var slots: Int, val augments: MutableMap<Int, SlottedAugment>) {

    fun set(slotIndex: Int, augment: SlottedAugment) {
        augments[slotIndex] = augment
    }

    fun set(slotIndex: Int, augmentId: AugmentId, potency: Int) {
        set(slotIndex, SlottedAugment(augmentId, potency))
    }

    fun get(slotIndex: Int): SlottedAugment {
        return augments[slotIndex] ?: SlottedAugment(AugmentId.Blank, 0)
    }

}

@Serializable
data class ItemAugment(
    var rankPoints: Int = 0,
    var rankLevel: Int = 1,
    var maxRankLevel: Int = 1,
    var augmentIds: MutableList<ItemAugmentId> = ArrayList(),
)

@Serializable
data class InventoryItem(
    val id: Int,
    val internalId: InternalItemId = Random.nextLong(),
    var quantity: Int = 1,
    var augments: ItemAugment? = null,
    var fixedAugments: CapacityAugments? = null,
    var slottedAugments: SlottedAugments? = null,
    var internalQuality: Int = 0,
    var temporary: Boolean = false,
) {

    fun info() = InventoryItems[id]

    fun isStackable() = augments == null && fixedAugments == null && slottedAugments == null && info().isStackable()

    fun skill(): SkillId? = info().usableItemInfo?.skill

}

@Serializable
class Inventory(
    val currency: Currency = Currency(),
    val inventoryItems: MutableList<InventoryItem> = ArrayList(),
): ActorStateComponent {

    companion object {
        fun player() = ActorStateManager.player().getInventory()
    }

    override fun update(actorState: ActorState, elapsedFrames: Float): ComponentUpdateResult {
        return ComponentUpdateResult(removeComponent = false)
    }

    fun getInventoryItemsByEquipSlot(equipSlot: EquipSlot) : List<InventoryItem> {
        return inventoryItems.filter { InventoryItems[it.id].equipmentItemInfo?.equipSlots?.contains(equipSlot) ?: false }
    }

    fun addItem(id: Int, quantity: Int) {
        repeat(quantity) { addItem(id) }
    }

    fun addItem(inventoryItem: InventoryItem, stack: Boolean = true) {
        if (stack && inventoryItem.isStackable()) {
            addItem(inventoryItem.id, inventoryItem.quantity)
        } else {
            inventoryItems += inventoryItem
        }
    }

    fun addItem(id: Int) {
        val itemInfo = InventoryItems[id]

        if (!itemInfo.isStackable()) {
            inventoryItems += InventoryItem(id)
            return
        }

        val current = inventoryItems.firstOrNull { it.isStackable() && it.id == id }
        if (current != null) {
            current.quantity += 1
        } else {
            inventoryItems += InventoryItem(id, quantity = 1)
        }
    }

    fun discardItem(internalItemId: InternalItemId) {
        inventoryItems.removeAll { it.internalId == internalItemId }
    }

    fun discardItem(internalItemId: InternalItemId, amount: Int) {
        val item = getByInternalId(internalItemId) ?: return
        if (item.quantity > amount) { item.quantity -= amount } else { inventoryItems.remove(item) }
    }

    fun sort() {
        inventoryItems.sortWith(this::compare)
    }

    fun copyFrom(other: Inventory): Inventory {
        inventoryItems.clear()
        inventoryItems.addAll(other.inventoryItems)

        currency.currencies.clear()
        currency.currencies.putAll(other.currency.currencies)

        return this
    }

    fun getByItemId(itemId: Int): List<InventoryItem> {
        return inventoryItems.filter { it.id == itemId }
    }

    fun getByInternalId(internalItemId: InternalItemId?): InventoryItem? {
        internalItemId ?: return null
        return inventoryItems.firstOrNull { it.internalId == internalItemId }
    }

    fun discardTemporaryItems() {
        inventoryItems.removeAll { it.temporary }
    }

    private fun compare(a: InventoryItem, b: InventoryItem): Int {
        val aInfo = a.info()
        val bInfo = b.info()

        val typeComparison = aInfo.itemType.sortOrder.compareTo(bInfo.itemType.sortOrder)
        if (typeComparison != 0) { return typeComparison }

        val aMask = aInfo.equipmentItemInfo?.equipSlots?.firstOrNull()?.mask
        val bMask = bInfo.equipmentItemInfo?.equipSlots?.firstOrNull()?.mask

        if (aMask != null && bMask != null) {
            return aMask.compareTo(bMask)
        } else if (aMask != null) {
            return 1
        } else if (bMask != null) {
            return -1
        }

        return a.id.compareTo(b.id)
    }

}

fun ActorState.countNotEquippedItems(itemId: Int): Int {
    return getNotEquippedItems().filter { it.id == itemId }.sumOf { it.quantity }
}

fun ActorState.discardNotEquippedItems(itemId: Int, quantity: Int, validateOnly: Boolean = false): Boolean {
    var remainingToRemove = quantity
    val matching = getNotEquippedItems().filter { it.id == itemId }

    val toDiscard = HashMap<InternalItemId, Int>()

    for (match in matching) {
        val toRemove = min(match.quantity, remainingToRemove)
        toDiscard[match.internalId] = toRemove

        remainingToRemove -= toRemove
        if (remainingToRemove == 0) { break }
    }

    if (remainingToRemove > 0) { return false }

    if (!validateOnly) {
        for ((internalItemId, amount) in toDiscard) {
            getInventory().discardItem(internalItemId, amount)
        }
    }

    return true
}

fun ActorState.getInventory(): Inventory {
    return getOrCreateComponentAs(Inventory::class) { Inventory() }
}

fun ActorState.adjustCurrency(type: CurrencyType, amount: Int): Boolean {
    val current = getCurrency(type)
    if (current + amount < 0) { return false }

    getInventory().currency.currencies.addInPlace(type, amount)
    return true
}

fun ActorState.getCurrency(type: CurrencyType): Int {
    return getInventory().currency.currencies[type] ?: 0
}

object AugmentHelper {

    fun isMaxRank(augment: ItemAugment): Boolean {
        return augment.rankLevel >= augment.maxRankLevel
    }

    fun getRpToNextLevel(augment: ItemAugment): Int {
        if (isMaxRank(augment)) { return 0 }
        return GameState.getGameMode().getAugmentRankPointsNeeded(augment)
    }

    fun getQualityColorDisplay(item: InventoryItem): Char {
        return when (item.internalQuality) {
            0 -> ShiftJis.colorWhite
            1 -> ShiftJis.colorCustom
            2 -> ShiftJis.colorKey
            3 -> ShiftJis.colorGold
            else -> throw IllegalStateException("Unknown quality: ${item.internalQuality}")
        }
    }

}