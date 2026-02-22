package xim.resource

import kotlinx.serialization.Serializable
import xim.poc.ActionTargetFilter
import xim.poc.ItemModelSlot
import xim.poc.browser.DatLoader
import xim.poc.browser.ParserContext.Companion.staticResource
import xim.poc.game.configuration.constants.ItemSkillId
import xim.poc.game.configuration.constants.SkillId
import xim.resource.table.LoadableResource
import xim.util.Fps.secondsToFrames

// Note: This is mainly a copy of: https://github.com/Windower/POLUtils/blob/master/PlayOnline.FFXI/Things/Item.cs

enum class Skill(val raw: Int) {
    None(0x00),
    HandToHand(0x01),
    Dagger(0x02),
    Sword(0x03),
    GreatSword(0x04),
    Axe(0x05),
    GreatAxe(0x06),
    Scythe(0x07),
    PoleArm(0x08),
    Katana(0x09),
    GreatKatana(0x0a),
    Club(0x0b),
    Staff(0x0c),
    AutomatonMelee(0x16),
    AutomatonRange(0x17),
    AutomatonMagic(0x18),
    Ranged(0x19),
    Marksmanship(0x1a),
    Thrown(0x1b),
    DivineMagic(0x20),
    HealingMagic(0x21),
    EnhancingMagic(0x22),
    EnfeeblingMagic(0x23),
    ElementalMagic(0x24),
    DarkMagic(0x25),
    SummoningMagic(0x26),
    Ninjutsu(0x27),
    Singing(0x28),
    StringInstrument(0x29),
    WindInstrument(0x2a),
    BlueMagic(0x2b),
    Geomancy(0x2c),
    Handbell(0x2d),
    Fishing(0x30),

    Unknown(-1),
    ;

    companion object {
        fun toSkill(raw: Int): Skill {
            return Skill.values().firstOrNull { it.raw == raw } ?: Unknown
        }
    }

}

@Serializable
enum class EquipSlot(val mask: Int) {
    None(0x0000),
    Main(0x0001),
    Sub(0x0002),
    Range(0x0004),
    Ammo(0x0008),
    Head(0x0010),
    Body(0x0020),
    Hands(0x0040),
    Legs(0x0080),
    Feet(0x0100),
    Neck(0x0200),
    Waist(0x0400),
    LEar(0x0800),
    REar(0x1000),
    LRing(0x2000),
    RRing(0x4000),
    Back(0x8000),
    ;

    fun toModelSlot(): ItemModelSlot? {
        return when (this) {
            Main -> ItemModelSlot.Main
            Sub -> ItemModelSlot.Sub
            Range -> ItemModelSlot.Range
            Head -> ItemModelSlot.Head
            Body -> ItemModelSlot.Body
            Hands -> ItemModelSlot.Hands
            Legs -> ItemModelSlot.Legs
            Feet -> ItemModelSlot.Feet
            else -> null
        }
    }

}

enum class ItemListType {
    Armor,
    Item,
    UsableItem,
    Weapon,
    Currency,
}

enum class InventoryItemType(val index: Int, val sortOrder: Int = 999) {
    None(0),
    Item(1),
    QuestItem(2),
    Fish(3),
    Weapon(4, sortOrder = 2),
    Armor(5, sortOrder = 3),
    Linkshell(6),
    UsableItem(7, sortOrder = 1),
    Crystal(8, sortOrder = 0),
    Currency(9),
    Furnishing(10),
    Plant(11),
    Flowerpot(12),
    PuppetItem(13),
    Mannequin(14),
    Book(15),
    RacingForm(16),
    BettingSlip(17),
    SoulPlate(18),
    Reflector(19),
    ItemType20(20),
    LotteryTicket(21),
    MazeTabula_M(22),
    MazeTabula_R(23),
    MazeVoucher(24),
    MazeRune(25),
    ItemType_26(26),
    StorageSlip(27),
    LegionPass(28),
    Grimoire(29),
    CraftingSet(31),
    ;

    companion object {
        fun from(id: Int): InventoryItemType {
            return InventoryItemType.values().firstOrNull { it.index == id } ?: throw IllegalStateException("Unknown item type: $id")
        }
    }
}

data class ArmorInfo(
    val shieldSize: Int = 0,
)

data class WeaponInfo(
    val skill: Skill = Skill.None,
    val delay: Int? = null,
)

data class EquipmentItemInfo(
    val races: Int? = null,
    val equipSlots: List<EquipSlot> = emptyList(),
    val armorInfo: ArmorInfo = ArmorInfo(),
    val weaponInfo: WeaponInfo = WeaponInfo(),
)

data class UsableItemInfo(val skill: SkillId, val activationDelay: Int) {

    fun castTimeInFrames(): Float {
        return secondsToFrames(activationDelay * 0.25f)
    }

}

data class InventoryItemInfo(
    val type: ItemListType,
    val itemId: Int,
    val resourceId: Int,
    val flags: Int,
    val name: String,
    val logName: String,
    val textureName: String,
    val logNamePlural: String,
    val description: String,
    val stackSize: Int,
    val equipmentItemInfo: EquipmentItemInfo?,
    val usableItemInfo: UsableItemInfo?,
    val textureReader: ByteReader,
    val targetFlags: Int,
    var itemType: InventoryItemType,
) {

    val textureResource: TextureResource
        get() = lazyLoadTexture()

    val targetFilter by lazy { ActionTargetFilter(targetFlags) }

    fun isStackable(): Boolean {
        return stackSize > 1
    }

    fun isH2H(): Boolean {
        return skill() == Skill.HandToHand
    }

    fun isTwoHanded(): Boolean {
        val skill = skill()
        return skill == Skill.GreatAxe || skill == Skill.GreatSword || skill == Skill.GreatKatana || skill == Skill.PoleArm || skill == Skill.Scythe || skill == Skill.Staff
    }

    fun isShield(): Boolean {
        return type == ItemListType.Armor && equipmentItemInfo?.equipSlots?.singleOrNull() == EquipSlot.Sub
    }

    fun isMainHandWeapon(): Boolean {
        return equipmentItemInfo?.equipSlots?.contains(EquipSlot.Main) ?: false
    }

    fun isGrip(): Boolean {
        return type == ItemListType.Weapon && equipmentItemInfo?.equipSlots?.singleOrNull() == EquipSlot.Sub
    }

    fun isBowOrGun(): Boolean {
        val skill = equipmentItemInfo?.weaponInfo?.skill ?: return false
        return skill == Skill.Marksmanship || skill == Skill.Ranged
    }

    fun isRingOrEarring(): Boolean {
        val equipSlots = equipmentItemInfo?.equipSlots ?: return false
        return equipSlots.contains(EquipSlot.REar) || equipSlots.contains(EquipSlot.RRing)
    }

    fun skill(): Skill {
        return equipmentItemInfo?.weaponInfo?.skill ?: Skill.None
    }

    fun logName(quantity: Int): String {
        return if (quantity > 1) { logNamePlural } else { logName }
    }

    fun isRare(): Boolean = (flags and 0x8000) != 0
    fun isExclusive(): Boolean = (flags and 0x6040) != 0

    private fun lazyLoadTexture(): TextureResource {
        val existing = DirectoryResource.getGlobalTexture(textureName)
        if (existing != null) { return existing }

        val resource = TextureSection.read(textureReader) ?: throw IllegalStateException("Failed to load texture for: $itemId")
        DirectoryResource.setGlobalTexture(resource)
        return resource
    }

}

object InventoryItems : LoadableResource {
    private var prefetchInitiated = false

    private val itemListDats = listOf(
        Pair(ItemListType.Item, "ROM/118/106.DAT"),
        Pair(ItemListType.UsableItem, "ROM/118/107.DAT"),
        Pair(ItemListType.Weapon, "ROM/118/108.DAT"),
        Pair(ItemListType.Armor, "ROM/118/109.DAT"),
        Pair(ItemListType.Currency, "ROM/174/48.DAT"),
        Pair(ItemListType.Armor, "ROM/286/73.DAT"),
        Pair(ItemListType.Item, "ROM/301/115.DAT"),
    )

    private var loadCount = 0

    private val inventoryItemsById = HashMap<Int, InventoryItemInfo>()

    override fun isFullyLoaded(): Boolean {
        return loadCount == itemListDats.size
    }

    override fun preload() {
        if (prefetchInitiated) { return }
        prefetchInitiated = true

        itemListDats.forEach {
            DatLoader.load(it.second, staticResource).onReady { wrapper ->
                val bytes = wrapper.getAsBytes()
                bytes.rotateRight(0x5)

                val parsed = InventoryItemListParser.parse(it.first, wrapper.getAsBytes())
                parsed.forEach { i -> inventoryItemsById[i.itemId] = i }
                loadCount += 1
            }
        }
    }

    operator fun get(itemId: Int): InventoryItemInfo {
        return inventoryItemsById[itemId] ?: throw IllegalStateException("No such item: $itemId")
    }

    fun getAll(): List<InventoryItemInfo> {
        return inventoryItemsById.values.toList()
    }

    fun mutate(index: Int, fn: (InventoryItemInfo) -> InventoryItemInfo) {
        inventoryItemsById[index] = fn.invoke(get(index))
    }

    fun getFurnitureModelId(inventoryItemInfo: InventoryItemInfo): Int? {
        val id = inventoryItemInfo.itemId

        return if (id < 256) {
            0x80A0 + (id - 1)
        } else if (id in 264..461) {
            0xC8F7 + (id - 264)
        } else {
            0xF55B + (id - 3584)
        }
    }

    fun ItemSkillId.toItemInfo(): InventoryItemInfo {
        return get(id)
    }

}

private object InventoryItemListParser {

    private const val itemSize = 0xC00
    private const val textureDataOffset = 0x284
    private const val textureNameOffset = 0x285

    fun parse(listType: ItemListType, byteReader: ByteReader): List<InventoryItemInfo> {
        val items = ArrayList<InventoryItemInfo>()

        while (byteReader.hasMore()) {
            val before = byteReader.position
            items += parseItem(listType, byteReader)
            byteReader.position = before + itemSize
            if (listType == ItemListType.Currency) { break }
        }

        return items
    }

    fun parseItem(listType: ItemListType, byteReader: ByteReader): InventoryItemInfo {
        val baseOffset = byteReader.position

        val itemId = byteReader.next32()
        val flags = byteReader.next16()
        val stackSize = byteReader.next16()
        val rawType = byteReader.next16()
        val resourceId = byteReader.next16()
        val validTargets = byteReader.next16()

        var equipmentItemInfo: EquipmentItemInfo? = null
        var usableItemInfo: UsableItemInfo? = null

        if (listType == ItemListType.Armor || listType == ItemListType.Weapon) {
            val level = byteReader.next16()
            val slotFlags = byteReader.next16()
            val races = byteReader.next16()
            val jobs = byteReader.next32()
            val superiorLevel = byteReader.next16()

            var armorInfo: ArmorInfo? = null
            var weaponInfo: WeaponInfo? = null

            if (listType == ItemListType.Armor) {
                val shieldSize = byteReader.next16()
                armorInfo = ArmorInfo(shieldSize)
            } else {
                val unk4 = byteReader.next16()
                val damage = byteReader.next16()
                val delay = byteReader.next16()
                val dps = byteReader.next16()
                val skill = Skill.toSkill(byteReader.next8())
                val jugSize = byteReader.next8()
                val unk1 = byteReader.next32()
                weaponInfo = WeaponInfo(skill = skill, delay = delay)
            }

            val maxCharges = byteReader.next8()
            val castingTime = byteReader.next8()
            val useDelay = byteReader.next16()
            val reuseDelay = byteReader.next32()
            val unk2 = byteReader.next16()
            val iLevel = byteReader.next8()
            val unk5 = byteReader.next8()
            val unk3 = byteReader.next32()

            val equipSlots = EquipSlot.values().filter { (it.mask and slotFlags) != 0 }
            val parsedRaces = if (races == 0) { null } else { races }
            equipmentItemInfo = EquipmentItemInfo(parsedRaces, equipSlots, armorInfo ?: ArmorInfo(), weaponInfo ?: WeaponInfo())
        } else if (listType == ItemListType.Item) {
            // TODO
            byteReader.next16()
            byteReader.next32()
            byteReader.next32()
        } else if (listType == ItemListType.UsableItem) {
            val activationDelay = byteReader.next16()
            byteReader.next32()
            byteReader.next32()
            byteReader.next32()
            usableItemInfo = UsableItemInfo(findSkillId(itemId), activationDelay)
        } else if (listType == ItemListType.Currency) {
            byteReader.next16()
        }

        val stringBase = byteReader.position
        val stringCount = byteReader.next32()
        val strings = ArrayList<String?>(stringCount)

        for (i in 0 until stringCount) {
            val stringOffset = byteReader.next32()
            val flag = byteReader.next32()

            if (flag != 0) {
                strings += null
                continue
            }

            val current = byteReader.position
            byteReader.position = stringBase + stringOffset

            strings += readString(byteReader)
            byteReader.position = current
        }

        val name = strings[0] ?: throw IllegalStateException("Null string: $byteReader")
        val logName = strings[2] ?: throw IllegalStateException("Null string: $byteReader")
        val logNamePlural = strings[3] ?: throw IllegalStateException("Null string: $byteReader")
        val description = strings[4] ?: throw IllegalStateException("Null string: $byteReader")

        val textureReader = ByteReader(byteReader.bytes)
        textureReader.position = baseOffset + textureDataOffset

        byteReader.position = baseOffset + textureNameOffset
        val textureName = byteReader.nextString(0x10)

        byteReader.position = baseOffset + itemSize - 0x01
        if (byteReader.next8() != 0xFF) {
            oops(byteReader)
        }

        val type = try {
            InventoryItemType.from(rawType)
        } catch (t: Throwable) {
            println("Unknown type: $rawType for $name")
            InventoryItemType.None
        }

        return InventoryItemInfo(
            type = listType,
            itemId = itemId,
            itemType = type,
            resourceId = resourceId,
            flags = flags,
            name = name,
            logName = logName,
            logNamePlural = logNamePlural,
            description = description,
            textureName = textureName,
            textureReader = textureReader,
            stackSize = stackSize,
            equipmentItemInfo = equipmentItemInfo,
            targetFlags = validTargets,
            usableItemInfo = usableItemInfo,
        )
    }

    private fun readString(byteReader: ByteReader): String? {
        val enabled = byteReader.next32()
        if (enabled == 0) { return null }

        for (i in 0 until 6) { expectZero32(byteReader) }

        return byteReader.nextZeroTerminatedString()
    }

    private fun findSkillId(itemId: Int): SkillId {
        return ItemSkillId(itemId)
    }

}