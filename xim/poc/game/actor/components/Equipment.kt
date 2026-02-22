package xim.poc.game.actor.components

import kotlinx.serialization.Serializable
import xim.poc.ItemModelSlot
import xim.poc.RaceGenderConfig
import xim.poc.game.*
import xim.poc.game.configuration.constants.skillDualWield_1554
import xim.resource.AbilityType
import xim.resource.EquipSlot
import xim.resource.InventoryItemType
import xim.resource.Skill
import xim.resource.table.ItemModelTable

@Serializable
class Equipment(
    private val items: HashMap<EquipSlot, InternalItemId> = HashMap()
): ActorStateComponent {

    override fun update(actorState: ActorState, elapsedFrames: Float): ComponentUpdateResult {
        updateBaseLook(actorState)
        return ComponentUpdateResult(removeComponent = false)
    }

    fun copyFrom(equipment: Equipment): Equipment {
        items.clear()
        items.putAll(equipment.items)
        return this
    }

    fun getAllItems(): Map<EquipSlot, InternalItemId> {
        return items
    }

    fun setItem(equipSlot: EquipSlot, item: InventoryItem?) {
        if (item == null) {
            items.remove(equipSlot)
            return
        }

        val alreadyEquippedSlot = items.entries.firstOrNull { it.value == item.internalId }
        val replacedItem = items[equipSlot]
        if (alreadyEquippedSlot != null && replacedItem != null) { items[alreadyEquippedSlot.key] = replacedItem }

        items[equipSlot] = item.internalId
    }

    fun getItem(inventory: Inventory, equipSlot: EquipSlot): InventoryItem? {
        val internalId = items[equipSlot] ?: return null
        return inventory.getByInternalId(internalId)
    }

    fun validateSub(inventory: Inventory, canDualWield: Boolean): Boolean {
        val sub = getItem(inventory, EquipSlot.Sub) ?: return true
        val subInfo = sub.info()

        val main = getItem(inventory, EquipSlot.Main)
        val mainInfo = main?.info()

        if (sub.internalId == main?.internalId) { return false }

        if (mainInfo != null && mainInfo.isH2H()) { return false }

        if (subInfo.isShield()) { return mainInfo == null || !mainInfo.isTwoHanded() }
        if (subInfo.isGrip()) { return mainInfo != null && mainInfo.isTwoHanded() }
        if (!canDualWield && subInfo.itemType == InventoryItemType.Weapon) { return false }

        if (mainInfo == null || mainInfo.isTwoHanded()) { return false }

        return true
    }

    fun validateAmmo(inventory: Inventory): Boolean {
        val ammo = getItem(inventory, EquipSlot.Ammo)?.info() ?: return true
        val ranged = getItem(inventory, EquipSlot.Range)?.info() ?: return true

        return if (ranged.skill() == Skill.Marksmanship || ranged.skill() == Skill.Ranged || ranged.skill() == Skill.Fishing) {
            ranged.skill() == ammo.skill()
        } else {
            false
        }
    }

    fun validateRaceFlags(equipSlot: EquipSlot, inventory: Inventory, raceGenderConfig: RaceGenderConfig): Boolean {
        val item = getItem(inventory, equipSlot) ?: return true

        val info = item.info()
        if (info.equipmentItemInfo?.races == null)  { return true }

        return (info.equipmentItemInfo.races and raceGenderConfig.equipmentFlag) != 0
    }

    fun isEquipped(item: InventoryItem): Boolean {
        return items.values.any { it == item.internalId }
    }

    fun getEquippedSlot(item: InventoryItem): EquipSlot? {
        return items.entries.firstOrNull { it.value == item.internalId }?.key
    }

    private fun updateBaseLook(actorState: ActorState) {
        val modelOverrides = HashMap<ItemModelSlot, Int>()
        val styleSet = if (actorState.isPlayer()) { EquipSetHelper.getStyleSheet() } else { null }

        val baseLook = actorState.getBaseLook()
        val inventory = actorState.getInventory()

        for (slot in EquipSlot.values()) {
            val modelSlot = slot.toModelSlot() ?: continue
            val item = styleSet?.getItem(inventory, slot) ?: getItem(inventory, slot)
            val itemModelId = ItemModelTable[item?.info()]

            baseLook.equipment[modelSlot] = itemModelId
            ItemModelTable.getForcedMatches(modelSlot, itemModelId).forEach { modelOverrides[it] = itemModelId }
        }

        for ((slot, itemModelId) in modelOverrides) {
            baseLook.equipment[slot] = itemModelId
        }

        val mainWepItemInfo = getItem(inventory, EquipSlot.Main)?.info() ?: return

        if (mainWepItemInfo.skill() == Skill.HandToHand) {
            baseLook.equipment[ItemModelSlot.Sub] = baseLook.equipment[ItemModelSlot.Main]
        }
    }

}

fun ActorState.isDualWield(): Boolean {
    val sub = getEquipment(EquipSlot.Sub) ?: return false
    val subInfo = sub.info()

    val equipSlots = subInfo.equipmentItemInfo?.equipSlots ?: return false
    return equipSlots.contains(EquipSlot.Main) && equipSlots.contains(EquipSlot.Sub)
}

fun ActorState.isHandToHand(): Boolean {
    val main = getEquipment(EquipSlot.Main) ?: return false
    val mainInfo = main.info()
    return mainInfo.skill() == Skill.HandToHand
}

fun ActorState.getRangedAttackItems(): Pair<InventoryItem?, InventoryItem?>? {
    val ranged = getEquipment(EquipSlot.Range)
    val rangedSkill = ranged?.info()?.skill()

    val ammo = getEquipment(EquipSlot.Ammo)
    val ammoSkill = ammo?.info()?.skill()

    return if (rangedSkill == Skill.Thrown) {
        Pair(ranged, null)
    } else if (ranged != null && ranged.info().isBowOrGun()) {
        if (rangedSkill == ammoSkill) { Pair(ranged, ammo) } else { null }
    } else if (ranged == null && ammoSkill == Skill.Thrown) {
        Pair(null, ammo)
    } else {
        null
    }
}

fun ActorState.equipAll(equip: Map<EquipSlot, InternalItemId?>) {
    val equipment = getEquipmentComponent() ?: return
    val inventory = getInventory()

    for ((equipSlot, itemId) in equip) {
        val item = inventory.getByInternalId(itemId)
        equipment.setItem(equipSlot, item)
    }
}

fun ActorState.removeInvalidEquipment() {
    val equipment = getEquipmentComponent() ?: return
    val canDualWield = GameEngine.getActorAbilityList(id, AbilityType.JobTrait).contains(skillDualWield_1554)

    val inventory = getInventory()

    if (!equipment.validateSub(inventory, canDualWield)) { equipment.setItem(EquipSlot.Sub, null) }
    if (!equipment.validateAmmo(inventory)) { equipment.setItem(EquipSlot.Ammo, null) }

    val race = getBaseLook().race ?: return
    for (slot in EquipSlot.values()) {
        if (!equipment.validateRaceFlags(slot, inventory, race)) { equipment.setItem(slot, null) }
    }
}

fun ActorState.isEquipped(item: InventoryItem): Boolean {
    if (isPlayer()) {
        val styleSheet = EquipSetHelper.getStyleSheet()
        if (styleSheet.isEquipped(item)) { return true }
    }

    return getEquipmentComponent()?.isEquipped(item) == true
}

fun ActorState.getNotEquippedItems(): List<InventoryItem> {
    val inventory = getInventory()
    val equipment = getEquipmentComponent() ?: return inventory.inventoryItems
    return getInventory().inventoryItems.filter { !equipment.isEquipped(it) }
}

fun ActorState.getEquipment(equipSlot: EquipSlot) : InventoryItem? {
    return getEquipmentComponent()?.getItem(getInventory(), equipSlot)
}

fun ActorState.getEquipment(): Map<EquipSlot, InventoryItem?> {
    return EquipSlot.values().associateWith { getEquipment(it) }
}

fun ActorState.getEquippedSlot(item: InventoryItem): EquipSlot? {
    val equipment = getEquipmentComponent() ?: return null
    return equipment.getEquippedSlot(item)
}

private fun ActorState.getEquipmentComponent(): Equipment? {
    return getComponentAs(Equipment::class)
}

fun ActorState.getEquipmentComponentOrThrow(): Equipment {
    return getRequiredComponentAs(Equipment::class)
}