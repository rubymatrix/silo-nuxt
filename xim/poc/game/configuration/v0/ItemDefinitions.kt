package xim.poc.game.configuration.v0

import xim.poc.game.AugmentId
import xim.poc.game.CombatStat
import xim.poc.game.CombatStats
import xim.poc.game.actor.components.CapacityAugment
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.ItemAugmentId
import xim.poc.game.actor.components.SlottedAugment
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.MonsterId
import xim.poc.game.configuration.WeightedTable
import xim.poc.game.configuration.WeightedTable.Companion.uniform
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.AccessoryMeldCapHelper.ringInitialMelds
import xim.poc.game.configuration.v0.AccessoryMeldCapHelper.ringMeldCaps
import xim.poc.game.configuration.v0.Floor1AugmentPools.bronzeEquipmentAugmentPool
import xim.poc.game.configuration.v0.Floor1AugmentPools.standardBackAugments
import xim.poc.game.configuration.v0.Floor1AugmentPools.standardWaistAugments
import xim.poc.game.configuration.v0.GemHelper.makeGems
import xim.poc.game.configuration.v0.ItemAbilities.getAbilities
import xim.poc.game.configuration.v0.ItemAugmentDefinitions.qualityBonus
import xim.poc.game.configuration.v0.ItemDefinitions.wdmg
import xim.poc.game.configuration.v0.WeaponPathHelper.WeaponConfig
import xim.poc.game.configuration.v0.WeaponPathHelper.makeOffPath
import xim.poc.game.configuration.v0.constants.*
import xim.poc.game.configuration.v0.interactions.ItemId
import xim.resource.DatId
import xim.resource.InventoryItemType
import xim.resource.InventoryItems
import xim.resource.SpellElement
import xim.resource.SpellElement.*
import kotlin.math.roundToInt

enum class WeaponPath {
    WeaponSkill,
    Crit,
    Magic,
    MultiAttack,
    Tp,
}

data class ItemCapacityAugment(val augmentId: AugmentId, val potency: Int, val capacity: Int)

data class ItemBuildUpMaterial(val itemId: Int, val quantity: Int)

data class ItemBuildUpOption(val destinationId: ItemId, val rankRequirement: Int, val itemRequirement: ItemBuildUpMaterial?)

data class ItemMysterySlotAugment(val augmentId: AugmentId, val valueProvider: MysteryValueProvider)

data class ItemRankSettings(
    val canRankUp: Boolean = false,
    val rankDistribution: ItemRankDistribution,
) {
    companion object {
        fun fixed(value: Int) = ItemRankSettings(canRankUp = false, rankDistribution = ItemRankFixed(value))
        fun leveling() = ItemRankSettings(canRankUp = true, rankDistribution = ItemRankFixed(1))
        fun uniformLeveling(min: Int, max: Int) = ItemRankSettings(canRankUp = true, rankDistribution = ItemRankUniformDistribution(min, max))
    }

}

data class ItemDropDefinition(
    val itemId: Int,
    val quantity: Int = 1,
    val temporary: Boolean = false,
    val slottedAugment: SlottedAugment? = null,
    val rankSettings: ItemRankSettings? = null,
)

enum class ItemTraitCustomId {
    WeaponSkillImmanence,
    WeaponSkillSpontaneity,
    SpontaneityConserveMp,
    SpontaneityMbbII,
    ConvertDamageToMp,
    ExtendsSkillChainDuration,
    Restraint,
    Impetus,
    Retaliation,
    BluTpGain,
    Wyvern,
}

sealed interface ItemTraitId
class ItemTraitAugment(val id: AugmentId): ItemTraitId
class ItemTraitCustom(val id: ItemTraitCustomId): ItemTraitId

enum class AugmentRestriction {
    None,
    MainOnly,
    HandOnly,
}

data class ItemTrait(
    val itemTraitId: ItemTraitId,
    val potency: Int,
    val handRestriction: AugmentRestriction,
    val displayTextProvider: ((ItemTrait) -> String),
)

data class ItemPet(
    val petId: MonsterId,
    val spawnAnimation: DatId = DatId.spop
)

data class ItemDefinition(
    val id: Int,
    val internalLevel: Int,
    val damage: Int = 0,
    val maxDamage: Int = damage,
    val magicDamage: Int = 0,
    val maxMagicDamage: Int = magicDamage,
    val delay: Int = 0,
    val combatStats: CombatStats = CombatStats(),
    val ranked: Boolean = false,
    val canRankUp: Boolean = false,
    val shopBuyable: Boolean = true,
    val shopSellable: Boolean = true,
    val dynamicQuality: Boolean = false,
    val staticAugments: List<Pair<AugmentId, Int>>? = null,
    val meldable: Boolean = false,
    val slotted: Boolean = false,
    val mysterySlot: ItemMysterySlotAugment? = null,
    val applyRandomSlots: Boolean = false,
    val initialCapacity: Int? = null,
    val initialMelds: List<CapacityAugment> = emptyList(),
    val augmentSlots: List<WeightedTable<ItemAugmentId>> = emptyList(),
    val capacityAugment: ItemCapacityAugment? = null,
    val upgradeOptions: List<ItemBuildUpOption> = emptyList(),
    val meldBonusCaps: Map<AugmentId, Int> = emptyMap(),
    val abilities: List<AbilitySkillId> = emptyList(),
    val traits: List<ItemTrait> = emptyList(),
    val pet: ItemPet? = null,
    val rare: Boolean = false,
) {

    fun scale(inventoryItem: InventoryItem): ItemDefinition {
        val scaledStats = combatStats * qualityBonus(inventoryItem)
        return copy(combatStats = scaledStats)
    }

    fun toDescription(inventoryItem: InventoryItem): String {
        val info = InventoryItems[id]

        val str = StringBuilder()

        if (info.itemType == InventoryItemType.Weapon) {
            val damage = DamageCalculator.getWeaponDamage(inventoryItem)
            str.appendLine("DMG:$damage  Delay:$delay")
        }

        var addedStat = false
        for (combatStat in CombatStat.values()) {
            val stat = combatStats[combatStat]
            if (stat != 0) { str.append("${combatStat.displayName}+$stat "); addedStat = true }
        }
        if (addedStat) { str.appendLine() }

        for (trait in traits) {
            str.appendLine(trait.displayTextProvider.invoke(trait))
        }

        pet?.let {
            val definition = MonsterDefinitions[it.petId]
            str.appendLine("Pet: ${definition.name}")
        }

        return str.toString()
    }

}

object ItemDefinitions {

    val definitionsById: Map<Int, ItemDefinition>

    val reinforcePointItems: List<ItemDefinition>

    val potions: Set<ItemId>
    val ethers: Set<ItemId>
    val remedies: Set<ItemId>

    private val standardArmorAugments = listOf(
        bronzeEquipmentAugmentPool,
        bronzeEquipmentAugmentPool,
        bronzeEquipmentAugmentPool,
    )

    init {
        val definitions = ArrayList<ItemDefinition>()
        reinforcePointItems = ArrayList()
        potions = HashSet()
        ethers = HashSet()
        remedies = HashSet()

        // Misc Stones
        definitions += ItemDefinition(id = itemFernStone_9211, internalLevel = 5)

        // Snowslit Stone
        definitions += ItemDefinition(id = 8930, internalLevel = 1)
        definitions += ItemDefinition(id = 8931, internalLevel = 10)
        definitions += ItemDefinition(id = 8932, internalLevel = 20)

        // Snowtip Stone
        definitions += ItemDefinition(id = 8939, internalLevel = 25)
        definitions += ItemDefinition(id = 8940, internalLevel = 30)

        // Leafslit
        definitions += ItemDefinition(id = 8933, internalLevel = 5, mysterySlot = ItemMysterySlotAugment(augmentId = AugmentId.WeaponSkillDamage, valueProvider = MysteryItemTierRange.x3))
        definitions += ItemDefinition(id = 8934, internalLevel = 15, mysterySlot = ItemMysterySlotAugment(augmentId = AugmentId.MagicAttackBonus, valueProvider = MysteryItemTierRange.x3))
        definitions += ItemDefinition(id = 8935, internalLevel = 35, mysterySlot = ItemMysterySlotAugment(augmentId = AugmentId.MagicBurstDamage, valueProvider = MysteryItemTierRange.x3))

        // Leaftip
        definitions += ItemDefinition(id = 8942, internalLevel = 5, mysterySlot = ItemMysterySlotAugment(augmentId = AugmentId.DoubleAttack, valueProvider = MysteryItemTierRange.x3))
        definitions += ItemDefinition(id = 8943, internalLevel = 15, mysterySlot = ItemMysterySlotAugment(augmentId = AugmentId.TripleAttack, valueProvider = MysteryItemTierRange.x2))
        definitions += ItemDefinition(id = 8944, internalLevel = 35, mysterySlot = ItemMysterySlotAugment(augmentId = AugmentId.QuadrupleAttack, valueProvider = MysteryItemTierRange.x1))

        // Leafdim
        definitions += ItemDefinition(id = 8951, internalLevel = 5, mysterySlot = ItemMysterySlotAugment(augmentId = AugmentId.StoreTp, valueProvider = MysteryItemTierRange.x3))
        definitions += ItemDefinition(id = 8952, internalLevel = 15, mysterySlot = ItemMysterySlotAugment(augmentId = AugmentId.SkillChainDamage, valueProvider = MysteryItemTierRange.x3))
        definitions += ItemDefinition(id = 8953, internalLevel = 35, mysterySlot = ItemMysterySlotAugment(augmentId = AugmentId.LatentRegain, valueProvider = MysteryItemTierRange.x10))

        // Leaforb
        definitions += ItemDefinition(id = 8960, internalLevel = 5, mysterySlot = ItemMysterySlotAugment(augmentId = AugmentId.FastCast, valueProvider = MysteryItemTierRange.x5))
        definitions += ItemDefinition(id = 8961, internalLevel = 15, mysterySlot = ItemMysterySlotAugment(augmentId = AugmentId.Haste, valueProvider = MysteryItemTierRange.x2))
        definitions += ItemDefinition(id = 8962, internalLevel = 35, mysterySlot = ItemMysterySlotAugment(augmentId = AugmentId.SubtleBlow, valueProvider = MysteryItemTierRange.x3))

        // Eschalixers
        definitions += ItemDefinition(id = 3974, internalLevel = 1).also { reinforcePointItems += it }
        definitions += ItemDefinition(id = 3975, internalLevel = 5).also { reinforcePointItems += it }
        definitions += ItemDefinition(id = 3976, internalLevel = 10).also { reinforcePointItems += it }
        definitions += ItemDefinition(id = 4051, internalLevel = 15).also { reinforcePointItems += it }
        definitions += ItemDefinition(id = 4052, internalLevel = 20).also { reinforcePointItems += it }
        definitions += ItemDefinition(id = 4053, internalLevel = 25).also { reinforcePointItems += it }
        definitions += ItemDefinition(id = 9084, internalLevel = 30).also { reinforcePointItems += it }
        definitions += ItemDefinition(id = 9085, internalLevel = 35).also { reinforcePointItems += it }
        definitions += ItemDefinition(id = 9086, internalLevel = 40).also { reinforcePointItems += it }

        // Consumables
        definitions += ItemDefinition(id = itemDustyPotion_5431.id, internalLevel = 1).also { potions += it.id }
        definitions += ItemDefinition(id = itemPotion_4112.id, internalLevel = 5).also { potions += it.id }
        definitions += ItemDefinition(id = itemHiPotion_4116.id, internalLevel = 10).also { potions += it.id }
        definitions += ItemDefinition(id = itemHyperPotion_5254.id, internalLevel = 20).also { potions += it.id }
        definitions += ItemDefinition(id = itemCoalitionPotion_5986.id, internalLevel = 30).also { potions += it.id }
        definitions += ItemDefinition(id = itemMaxPotion_4124.id, internalLevel = 40).also { potions += it.id }
        definitions += ItemDefinition(id = itemXPotion_4120.id, internalLevel = 50).also { potions += it.id }

        definitions += ItemDefinition(id = itemDustyEther_5432.id, internalLevel = 1).also { ethers += it.id }
        definitions += ItemDefinition(id = itemEther_4128.id, internalLevel = 5).also { ethers += it.id }
        definitions += ItemDefinition(id = itemHiEther_4132.id, internalLevel = 10).also { ethers += it.id }
        definitions += ItemDefinition(id = itemHyperEther_5255.id, internalLevel = 20).also { ethers += it.id }
        definitions += ItemDefinition(id = itemCoalitionEther_5987.id, internalLevel = 30).also { ethers += it.id }
        definitions += ItemDefinition(id = itemSuperEther_4136.id, internalLevel = 40).also { ethers += it.id }
        definitions += ItemDefinition(id = itemProEther_4140.id, internalLevel = 50).also { ethers += it.id }

        definitions += ItemDefinition(id = itemRemedy_4155.id, internalLevel = 5).also { remedies += it.id }
        definitions += ItemDefinition(id = itemPanacea_4149.id, internalLevel = 30).also { remedies += it.id }

        // Weapons
        definitions += WeaponPathHelper.makePath(WeaponPath.Tp,
            WeaponConfig(tier = 1, itemId = weaponOnionSword_16534, upgradeOptions = listOf(weaponXiphos_16530, weaponSapara_16551)),
            WeaponConfig(tier = 2, itemId = weaponXiphos_16530, upgradeOptions = listOf(weaponIronSword_16536, weaponFalchion_16558)),
            WeaponConfig(tier = 3, itemId = weaponIronSword_16536, upgradeOptions = listOf(weaponMythrilSword_16537, weaponOnionSwordII_21608)),
            WeaponConfig(tier = 4, itemId = weaponMythrilSword_16537, upgradeOptions = listOf(weaponFirmament_17664)),
            WeaponConfig(tier = 5, itemId = weaponFirmament_17664, upgradeOptions = listOf(weaponWingSword_16542)),
            WeaponConfig(tier = 6, itemId = weaponWingSword_16542, upgradeOptions = listOf(weaponRuneBlade_16563)),
            WeaponConfig(tier = 7, itemId = weaponRuneBlade_16563), //, upgradeOptions = listOf(weaponColada_20677)),
            WeaponConfig(tier = 8, itemId = weaponColada_20677, upgradeOptions = listOf(weaponTwinnedBlade_21623)),
            WeaponConfig(tier = 9, itemId = weaponTwinnedBlade_21623, upgradeOptions = listOf(weaponClaidheamhSoluis_20718)),
            WeaponConfig(tier = 10, itemId = weaponClaidheamhSoluis_20718, upgradeOptions = listOf(weaponSequence_20695)),
            WeaponConfig(tier = 11, itemId = weaponSequence_20695),
        )

        definitions += WeaponPathHelper.makePath(WeaponPath.MultiAttack,
            WeaponConfig(tier = 2, itemId = weaponSapara_16551, upgradeOptions = listOf(weaponScimitar_16552, weaponIronSword_16536)),
            WeaponConfig(tier = 3, itemId = weaponScimitar_16552, upgradeOptions = listOf(weaponTulwar_16553)),
            WeaponConfig(tier = 4, itemId = weaponTulwar_16553, upgradeOptions = listOf(weaponHanger_16554)),
            WeaponConfig(tier = 5, itemId = weaponHanger_16554, upgradeOptions = listOf(weaponKilij_17660)),
            WeaponConfig(tier = 6, itemId = weaponKilij_17660, upgradeOptions = listOf(weaponSteelKilij_17739)),
            WeaponConfig(tier = 7, itemId = weaponSteelKilij_17739), //, upgradeOptions = listOf(weaponDarksteelKilij_17724)),
            WeaponConfig(tier = 8, itemId = weaponDarksteelKilij_17724, upgradeOptions = listOf(weaponAdamanKilij_17727)),
            WeaponConfig(tier = 9, itemId = weaponAdamanKilij_17727, upgradeOptions = listOf(weaponTyrfing_16540)),
            WeaponConfig(tier = 10, itemId = weaponTyrfing_16540, upgradeOptions = listOf(weaponTizona_18986)),
            WeaponConfig(tier = 11, itemId = weaponTizona_18986),
        )

        definitions += WeaponPathHelper.makePath(WeaponPath.WeaponSkill,
            WeaponConfig(tier = 3, itemId = weaponFalchion_16558, upgradeOptions = listOf(weaponNadrs_17650, weaponShotel_17701, weaponOnionSwordII_21608)),
            WeaponConfig(tier = 4, itemId = weaponNadrs_17650, upgradeOptions = listOf(weaponDarksteelFalchion_16559)),
            WeaponConfig(tier = 5, itemId = weaponDarksteelFalchion_16559, upgradeOptions = listOf(weaponCutlass_16560)),
            WeaponConfig(tier = 6, itemId = weaponCutlass_16560, upgradeOptions = listOf(weaponPlatinumCutlass_16562)),
            WeaponConfig(tier = 7, itemId = weaponPlatinumCutlass_16562), //, upgradeOptions = listOf(weaponErlkingsBlade_17763)),
            WeaponConfig(tier = 8, itemId = weaponErlkingsBlade_17763, upgradeOptions = listOf(weaponEtherealSword_21624)),
            WeaponConfig(tier = 9, itemId = weaponEtherealSword_21624, upgradeOptions = listOf(weaponConcordia_17765)),
            WeaponConfig(tier = 10, itemId = weaponConcordia_17765, upgradeOptions = listOf(weaponZomorrodnegar_21633)),
            WeaponConfig(tier = 11, itemId = weaponZomorrodnegar_21633),
        )

        definitions += WeaponPathHelper.makePath(WeaponPath.Magic,
            WeaponConfig(tier = 4, itemId = weaponOnionSwordII_21608, upgradeOptions = listOf(weaponXiutleato_20731)),
            WeaponConfig(tier = 5, itemId = weaponXiutleato_20731, upgradeOptions = listOf(weaponFiretongue_20668)),
            WeaponConfig(tier = 6, itemId = weaponFiretongue_20668, upgradeOptions = listOf(weaponBlizzardBrand_20666)),
            WeaponConfig(tier = 6, itemId = weaponTalekeeper_18903, upgradeOptions = listOf(weaponBlizzardBrand_20666), buyable = false),
            WeaponConfig(tier = 7, itemId = weaponBlizzardBrand_20666), //, upgradeOptions = listOf(weaponAernSword_20674)),
            WeaponConfig(tier = 8, itemId = weaponAernSword_20674, upgradeOptions = listOf(weaponAernSwordII_20675)),
            WeaponConfig(tier = 9, itemId = weaponAernSwordII_20675, upgradeOptions = listOf(weaponSanusEnsis_18909)),
            WeaponConfig(tier = 10, itemId = weaponSanusEnsis_18909, upgradeOptions = listOf(weaponFermionSword_20694)),
            WeaponConfig(tier = 11, itemId = weaponFermionSword_20694),
        )

        definitions += WeaponPathHelper.makePath(WeaponPath.Crit,
            WeaponConfig(tier = 4, itemId = weaponShotel_17701, upgradeOptions = listOf(weaponDissector_17699)),
            WeaponConfig(tier = 5, itemId = weaponDissector_17699, upgradeOptions = listOf(weaponNibiruBlade_20710)),
            WeaponConfig(tier = 6, itemId = weaponNibiruBlade_20710, upgradeOptions = listOf(weaponArkSaber_18912)),
            WeaponConfig(tier = 7, itemId = weaponArkSaber_18912), //, upgradeOptions = listOf(weaponExtinction_21638)),
            WeaponConfig(tier = 8, itemId = weaponExtinction_21638, upgradeOptions = listOf(weaponHofud_17745)),
            WeaponConfig(tier = 9, itemId = weaponHofud_17745, upgradeOptions = listOf(weaponAcclimator_20715)),
            WeaponConfig(tier = 10, itemId = weaponAcclimator_20715, upgradeOptions = listOf(weaponAlmace_19399)),
            WeaponConfig(tier = 11, itemId = weaponAlmace_19399),
        )

        // Off-path
        definitions += makeOffPath(
            itemId = weaponRouter_20847,
            tier = 7,
            internalLevel = 30,
            damageRange = 2.0,
            delay = 500,
            abilities = listOf(skillRagingRush_86, skillKeenEdge_84, skillSteelCyclone_88, skillFellCleave_91, skillWarriorsCharge_661, skillBloodRage_779),
            traits = listOf(WeaponTraits.retaliation(), WeaponTraits.doubleAttack(100), WeaponTraits.tpBonus(500)),
        )

        definitions += makeOffPath(
            itemId = weaponChastisers_20523,
            tier = 7,
            internalLevel = 30,
            damageRange = 1.25,
            delay = 480,
            abilities = listOf(skillHowlingFist_7, skillShijinSpiral_15, skillFinalHeaven_10, skillTornadoKick_13, skillChakra_550, skillDodge_549),
            traits = listOf(WeaponTraits.impetus(2), WeaponTraits.kickAttacks(100), WeaponTraits.tpBonus(500)),
        )

        definitions += makeOffPath(
            itemId = weaponAnnealedLance_20938,
            tier = 7,
            internalLevel = 30,
            damageRange = 2.0,
            delay = 500,
            abilities = listOf(skillWheelingThrust_119, skillImpulseDrive_120, skillSonicThrust_123, skillStardiver_125, skillSmitingBreath_830, skillRestoringBreath_831),
            traits = listOf(WeaponTraits.petWyvern(), WeaponTraits.tpBonus(500)),
        )

        // Chakram
        definitions += ItemDefinition(id = weaponChakram_17284, internalLevel = 1, damage = 50, maxDamage = 100, delay = 180, ranked = true)
        definitions += ItemDefinition(id = weaponTrollbane_18694, internalLevel = 18, damage = 300, maxDamage = 500, delay = 180, ranked = true)

        // Bronze Attire
        definitions += ItemDefinition(id = 12448, internalLevel = 1, ranked = true, combatStats = CombatStats(vit = 1, int = 5), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12576, internalLevel = 1, ranked = true, combatStats = CombatStats(vit = 1, str = 5), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12704, internalLevel = 1, ranked = true, combatStats = CombatStats(vit = 1, dex = 5), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12832, internalLevel = 1, ranked = true, combatStats = CombatStats(vit = 1, mnd = 5), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12960, internalLevel = 1, ranked = true, combatStats = CombatStats(vit = 1, agi = 5), augmentSlots = standardArmorAugments)

        // Cherry Yukata
        definitions += ItemDefinition(id = 11316, internalLevel = 1, shopBuyable = false, shopSellable = false, augmentSlots = emptyList(), rare = true)
        definitions += ItemDefinition(id = 11317, internalLevel = 1, shopBuyable = false, shopSellable = false, augmentSlots = emptyList(), rare = true)

        // Leather Attire
        definitions += ItemDefinition(id = 12440, internalLevel = 3, ranked = true, combatStats = CombatStats(vit = 1, int = 7), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12568, internalLevel = 3, ranked = true, combatStats = CombatStats(vit = 2, str = 7), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12696, internalLevel = 3, ranked = true, combatStats = CombatStats(vit = 1, dex = 7), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12824, internalLevel = 3, ranked = true, combatStats = CombatStats(vit = 2, mnd = 7), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12952, internalLevel = 3, ranked = true, combatStats = CombatStats(vit = 1, agi = 7), augmentSlots = standardArmorAugments)

        // Xux Attire (Colkhab)
        definitions += ItemDefinition(id = 27781, internalLevel = 4, shopBuyable = false, ranked = true, combatStats = CombatStats(vit = 2, int = 8), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 28201, internalLevel = 4, shopBuyable = false, ranked = true, combatStats = CombatStats(vit = 2, mnd = 8), augmentSlots = standardArmorAugments)

        // Doublet Attire
        definitions += ItemDefinition(id = 12464, internalLevel = 5, ranked = true, combatStats = CombatStats(vit = 2, int = 9), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12592, internalLevel = 5, ranked = true, combatStats = CombatStats(vit = 2, str = 9), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12720, internalLevel = 5, ranked = true, combatStats = CombatStats(vit = 2, dex = 9), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12848, internalLevel = 5, ranked = true, combatStats = CombatStats(vit = 2, mnd = 9), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12976, internalLevel = 5, ranked = true, combatStats = CombatStats(vit = 2, agi = 9), augmentSlots = standardArmorAugments)

        // Hebenus Attire (Swimwear)
        definitions += ItemDefinition(id = 23871, internalLevel = 1, shopBuyable = false, shopSellable = false, augmentSlots = emptyList(), rare = true)
        definitions += ItemDefinition(id = 23872, internalLevel = 1, shopBuyable = false, shopSellable = false, augmentSlots = emptyList(), rare = true)
        definitions += ItemDefinition(id = 23873, internalLevel = 1, shopBuyable = false, shopSellable = false, augmentSlots = emptyList(), rare = true)
        definitions += ItemDefinition(id = 23874, internalLevel = 1, shopBuyable = false, shopSellable = false, augmentSlots = emptyList(), rare = true)

        // Shneddick Attire
        definitions += ItemDefinition(id = 27762, internalLevel = 7, ranked = true, combatStats = CombatStats(vit = 2, int = 11), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 27908, internalLevel = 7, ranked = true, combatStats = CombatStats(vit = 3, str = 11), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 28047, internalLevel = 7, ranked = true, combatStats = CombatStats(vit = 2, dex = 11), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 28189, internalLevel = 7, ranked = true, combatStats = CombatStats(vit = 3, mnd = 11), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 28328, internalLevel = 7, ranked = true, combatStats = CombatStats(vit = 2, agi = 11), augmentSlots = standardArmorAugments)

        // Chocaliztli Attire
        definitions += ItemDefinition(id = 27780, internalLevel = 9, shopBuyable = false, ranked = true, combatStats = CombatStats(vit = 2, int = 13), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 28343, internalLevel = 9, shopBuyable = false, ranked = true, combatStats = CombatStats(vit = 3, agi = 13), augmentSlots = standardArmorAugments)

        // Wool Attire
        definitions += ItemDefinition(id = 12467, internalLevel = 10, ranked = true, combatStats = CombatStats(vit = 2, int = 14), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12595, internalLevel = 10, ranked = true, combatStats = CombatStats(vit = 3, str = 14), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12730, internalLevel = 10, ranked = true, combatStats = CombatStats(vit = 3, dex = 14), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12851, internalLevel = 10, ranked = true, combatStats = CombatStats(vit = 3, mnd = 14), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12979, internalLevel = 10, ranked = true, combatStats = CombatStats(vit = 3, agi = 14), augmentSlots = standardArmorAugments)

        // Shade Attire
        definitions += ItemDefinition(id = 15165, internalLevel = 12, ranked = true, combatStats = CombatStats(vit = 3, int = 17), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 14426, internalLevel = 12, ranked = true, combatStats = CombatStats(vit = 4, str = 17), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 14858, internalLevel = 12, ranked = true, combatStats = CombatStats(vit = 3, dex = 17), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 14327, internalLevel = 12, ranked = true, combatStats = CombatStats(vit = 4, mnd = 17), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 15315, internalLevel = 12, ranked = true, combatStats = CombatStats(vit = 3, agi = 17), augmentSlots = standardArmorAugments)

        // Aurore Attire
        definitions += ItemDefinition(id = 11504, internalLevel = 16, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 5, int = 24), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 13760, internalLevel = 16, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 5, str = 24), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12746, internalLevel = 16, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 5, dex = 24), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 14257, internalLevel = 16, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 5, mnd = 24), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 11414, internalLevel = 16, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 5, agi = 24), augmentSlots = standardArmorAugments)

        // Misc Abyssea-1 Drops
        definitions += ItemDefinition(id = itemJuogi_14336, internalLevel = 1, shopBuyable = false, shopSellable = false, augmentSlots = emptyList(), rare = true)
        definitions += ItemDefinition(id = itemTeutatesSubligar_15427, internalLevel = 1, shopBuyable = false, shopSellable = false, augmentSlots = emptyList(), rare = true)

        definitions += ItemDefinition(id = itemTimarliJawshan_13791, internalLevel = 17, ranked = true, combatStats = CombatStats(vit = 6, str = 27), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = itemTimarliDastanas_14060, internalLevel = 17, ranked = true, combatStats = CombatStats(vit = 5, dex = 27), augmentSlots = standardArmorAugments)

        // Yigit Attire
        definitions += ItemDefinition(id = 16064, internalLevel = 18, ranked = true, combatStats = CombatStats(vit = 6, int = 29), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 14527, internalLevel = 18, ranked = true, combatStats = CombatStats(vit = 6, str = 29), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 14935, internalLevel = 18, ranked = true, combatStats = CombatStats(vit = 6, dex = 29), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 15606, internalLevel = 18, ranked = true, combatStats = CombatStats(vit = 6, mnd = 29), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 15690, internalLevel = 18, ranked = true, combatStats = CombatStats(vit = 6, agi = 29), augmentSlots = standardArmorAugments)

        // Kacura Attire
        definitions += ItemDefinition(id = 10886, internalLevel = 20, ranked = true, combatStats = CombatStats(vit = 7, int = 35), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 10458, internalLevel = 20, ranked = true, combatStats = CombatStats(vit = 7, str = 35), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 10508, internalLevel = 20, ranked = true, combatStats = CombatStats(vit = 7, dex = 35), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 11976, internalLevel = 20, ranked = true, combatStats = CombatStats(vit = 7, mnd = 35), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 10606, internalLevel = 20, ranked = true, combatStats = CombatStats(vit = 7, agi = 35), augmentSlots = standardArmorAugments)

        // Mirke Wardecors
        definitions += ItemDefinition(id = 11314, internalLevel = 22, shopBuyable = false, ranked = true, combatStats = CombatStats(vit = 8, str = 41), augmentSlots = standardArmorAugments)

        // Taeon Attire
        definitions += ItemDefinition(id = 26735, internalLevel = 23, ranked = true, combatStats = CombatStats(vit = 9, int = 45), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 26893, internalLevel = 23, ranked = true, combatStats = CombatStats(vit = 9, str = 45), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 27047, internalLevel = 23, ranked = true, combatStats = CombatStats(vit = 9, dex = 45), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 27234, internalLevel = 23, ranked = true, combatStats = CombatStats(vit = 9, mnd = 45), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 27404, internalLevel = 23, ranked = true, combatStats = CombatStats(vit = 9, agi = 45), augmentSlots = standardArmorAugments)

        // Buremte Attire
        definitions += ItemDefinition(id = 27767, internalLevel = 24, shopBuyable = false, ranked = true, combatStats = CombatStats(vit = 10, int = 50), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 28050, internalLevel = 24, shopBuyable = false, ranked = true, combatStats = CombatStats(vit = 10, dex = 50), augmentSlots = standardArmorAugments)

        // Ebon Frock Attire
        definitions += ItemDefinition(id = itemEbonBeret_12141, internalLevel = 25, ranked = true, combatStats = CombatStats(vit = 11, int = 55), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = itemEbonFrock_12177, internalLevel = 25, ranked = true, combatStats = CombatStats(vit = 11, str = 55), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = itemEbonMitts_12213, internalLevel = 25, ranked = true, combatStats = CombatStats(vit = 11, dex = 55), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = itemEbonSlops_12249, internalLevel = 25, ranked = true, combatStats = CombatStats(vit = 11, mnd = 55), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = itemEbonClogs_12285, internalLevel = 25, ranked = true, combatStats = CombatStats(vit = 11, agi = 55), augmentSlots = standardArmorAugments)

        definitions += ItemDefinition(id = itemNocturnusMail_11354, internalLevel = 27, shopBuyable = false, ranked = true, combatStats = CombatStats(vit = 13, str = 65), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = itemNocturnusHelm_11501, internalLevel = 27, shopBuyable = false, ranked = true, combatStats = CombatStats(vit = 13, int = 65), augmentSlots = standardArmorAugments)

        definitions += ItemDefinition(id = armorRawhideMask_26794, internalLevel = 28, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 14, int = 71), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = armorRawhideVest_26950, internalLevel = 28, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 15, str = 71), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = armorRawhideGloves_27100, internalLevel = 28, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 14, dex = 71), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = armorRawhideTrousers_27285, internalLevel = 28, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 14, mnd = 71), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = armorRawhideBoots_27460, internalLevel = 28, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 14, agi = 71), augmentSlots = standardArmorAugments)

        definitions += ItemDefinition(id = armorPsyclothTiara_26796, internalLevel = 30, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 17, int = 84), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = armorPsyclothVest_26952, internalLevel = 30, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 17, str = 84), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = armorPsyclothManillas_27102, internalLevel = 30, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 17, dex = 84), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = armorPsyclothLappas_27287, internalLevel = 30, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 17, mnd = 84), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = armorPsyclothBoots_27462, internalLevel = 30, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 17, agi = 84), augmentSlots = standardArmorAugments)

        definitions += ItemDefinition(id = armorAnnointKalasiris_26960, internalLevel = 30, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 17, str = 84), augmentSlots = standardArmorAugments, traits = listOf(WeaponTraits.refresh(1)))
        definitions += ItemDefinition(id = armorKubiraMeikogai_26959, internalLevel = 30, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 17, str = 84), augmentSlots = standardArmorAugments, traits = listOf(WeaponTraits.regen(15)))
        definitions += ItemDefinition(id = armorMakoraMeikogai_26961, internalLevel = 30, ranked = true, shopBuyable = false, combatStats = CombatStats(vit = 17, str = 84), augmentSlots = standardArmorAugments, traits = listOf(WeaponTraits.regain(150)))

        definitions += ItemDefinition(id = armorEnforcersHarness_26962, internalLevel = 32, ranked = true, shopBuyable = false, traits = listOf(WeaponTraits.refresh(1), WeaponTraits.regen(15), WeaponTraits.regain(150)), combatStats = CombatStats(vit = 22, str = 101, int = 17), augmentSlots = standardArmorAugments)

        // Judge's Attire (Debug)
        definitions += ItemDefinition(id = 12523, internalLevel = 50, ranked = true, combatStats = CombatStats(vit = 100, int = 500), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12551, internalLevel = 50, ranked = true, combatStats = CombatStats(vit = 100, str = 500), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12679, internalLevel = 50, ranked = true, combatStats = CombatStats(vit = 100, dex = 500), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12807, internalLevel = 50, ranked = true, combatStats = CombatStats(vit = 100, mnd = 500), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 12935, internalLevel = 50, ranked = true, combatStats = CombatStats(vit = 100, agi = 500), augmentSlots = standardArmorAugments)
        definitions += ItemDefinition(id = 13606, internalLevel = 50, ranked = true, combatStats = CombatStats(maxHp = 500), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = 13215, internalLevel = 50, ranked = true, combatStats = CombatStats(maxHp = 500), augmentSlots = standardWaistAugments)

        // Back
        definitions += ItemDefinition(id = itemRabbitMantle_13594, internalLevel = 1, ranked = true, combatStats = CombatStats(maxHp = 5), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = itemCape_13583, internalLevel = 3, ranked = true, combatStats = CombatStats(maxHp = 7), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = itemCottonCape_13584, internalLevel = 5, ranked = true, combatStats = CombatStats(maxHp = 9), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = itemBesiegerMantle_11528, internalLevel = 7, ranked = true, combatStats = CombatStats(maxHp = 11), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = itemLizardMantle_13592, internalLevel = 10, ranked = true, combatStats = CombatStats(maxHp = 14), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = itemTalismanCape_15485, internalLevel = 12, ranked = true, combatStats = CombatStats(maxHp = 17), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = itemSharpeyeMantle_11562, internalLevel = 16, ranked = true, shopBuyable = false, combatStats = CombatStats(maxHp = 24), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = itemIntensifyingCape_15492, internalLevel = 18, ranked = true, combatStats = CombatStats(maxHp = 29), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = itemAuroraMantle_13639, internalLevel = 20, ranked = true, combatStats = CombatStats(maxHp = 35), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = 28640, internalLevel = 23, ranked = true, combatStats = CombatStats(maxHp = 45), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = itemViatorCape_16246, internalLevel = 25, ranked = true, combatStats = CombatStats(maxHp = 55), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = armorDispersersCape_27606, internalLevel = 28, ranked = true, shopBuyable = false, combatStats = CombatStats(maxHp = 71), augmentSlots = standardBackAugments)
        definitions += ItemDefinition(id = armorThaumCape_27607, internalLevel = 30, ranked = true, shopBuyable = false, combatStats = CombatStats(maxHp = 84), augmentSlots = standardBackAugments)

        // Belt
        definitions += ItemDefinition(id = itemBloodStone_13199, internalLevel = 1, ranked = true, combatStats = CombatStats(maxHp = 5), augmentSlots = standardWaistAugments)
        definitions += ItemDefinition(id = itemLeatherBelt_13192, internalLevel = 3, ranked = true, combatStats = CombatStats(maxHp = 7), augmentSlots = standardWaistAugments)
        definitions += ItemDefinition(id = itemFriarsRope_13211, internalLevel = 5, ranked = true, combatStats = CombatStats(maxHp = 9), augmentSlots = standardWaistAugments)
        definitions += ItemDefinition(id = itemWarriorsBelt_13194, internalLevel = 7, ranked = true, combatStats = CombatStats(maxHp = 11), augmentSlots = standardWaistAugments)
        definitions += ItemDefinition(id = itemLizardBelt_13193, internalLevel = 10, ranked = true, combatStats = CombatStats(maxHp = 14), augmentSlots = standardWaistAugments)
        definitions += ItemDefinition(id = itemTalismanObi_15881, internalLevel = 12, ranked = true, combatStats = CombatStats(maxHp = 17), augmentSlots = standardWaistAugments)
        definitions += ItemDefinition(id = itemAristoBelt_11745, internalLevel = 16, ranked = true, shopBuyable = false, combatStats = CombatStats(maxHp = 24), augmentSlots = standardWaistAugments)
        definitions += ItemDefinition(id = itemPreciseBelt_15886, internalLevel = 18, ranked = true, combatStats = CombatStats(maxHp = 29), augmentSlots = standardWaistAugments)
        definitions += ItemDefinition(id = itemSwordbelt_13198, internalLevel = 20, ranked = true, combatStats = CombatStats(maxHp = 35), augmentSlots = standardWaistAugments)
        definitions += ItemDefinition(id = 28459, internalLevel = 23, ranked = true, combatStats = CombatStats(maxHp = 45), augmentSlots = standardWaistAugments)
        definitions += ItemDefinition(id = itemDemagoguesSash_15924, internalLevel = 25, ranked = true, combatStats = CombatStats(maxHp = 55), augmentSlots = standardWaistAugments)
        definitions += ItemDefinition(id = armorLuciditySash_28416, internalLevel = 28, ranked = true, shopBuyable = false, combatStats = CombatStats(maxHp = 71), augmentSlots = standardWaistAugments)
        definitions += ItemDefinition(id = armorSinewBelt_28417, internalLevel = 30, ranked = true, shopBuyable = false, combatStats = CombatStats(maxHp = 84), augmentSlots = standardWaistAugments)

        // Earrings
        definitions += ItemDefinition(id = itemGreenEarring_13343, internalLevel = 25, shopBuyable = false, rare = true, staticAugments = listOf(AugmentId.Haste to 10, AugmentId.StoreTp to 8))
        definitions += ItemDefinition(id = itemSunEarring_13344, internalLevel = 25, shopBuyable = false, rare = true, staticAugments = listOf(AugmentId.WeaponSkillDamage to 10, AugmentId.MagicAttackBonus to 8))
        definitions += ItemDefinition(id = itemZirconEarring_13345, internalLevel = 25, shopBuyable = false, rare = true, staticAugments = listOf(AugmentId.MagicAttackBonus to 10, AugmentId.Haste to 8))
        definitions += ItemDefinition(id = itemPurpleEarring_13346, internalLevel = 25, shopBuyable = false, rare = true, staticAugments = listOf(AugmentId.CriticalHitRate to 10, AugmentId.DoubleAttack to 8))
        definitions += ItemDefinition(id = itemAquamrneEarring_13347, internalLevel = 25, shopBuyable = false, rare = true, staticAugments = listOf(AugmentId.DoubleAttack to 10, AugmentId.WeaponSkillDamage to 8))
        definitions += ItemDefinition(id = itemYellowEarring_13348, internalLevel = 25, shopBuyable = false, rare = true, staticAugments = listOf(AugmentId.StoreTp to 10, AugmentId.CriticalHitRate to 8))
        definitions += ItemDefinition(id = itemNightEarring_13349, internalLevel = 25, shopBuyable = false, rare = true, staticAugments = listOf(AugmentId.ConserveMp to 12, AugmentId.DoubleAttack to 7, AugmentId.StoreTp to 7, AugmentId.MagicAttackBonus to 7))
        definitions += ItemDefinition(id = itemMoonEarring_13350, internalLevel = 25, shopBuyable = false, rare = true, staticAugments = listOf(AugmentId.ConserveTp to 12, AugmentId.WeaponSkillDamage to 7, AugmentId.Haste to 7, AugmentId.CriticalHitRate to 7))

        // Pet Items
        definitions += ItemDefinition(id = armorHuaniCollar_28384, internalLevel = 1, pet = ItemPet(petColkhab), rare = true, shopBuyable = false, shopSellable = false)
        definitions += ItemDefinition(id = armorQuanpurNecklace_28387, internalLevel = 1, pet = ItemPet(petYumcax), rare = true, shopBuyable = false, shopSellable = false)
        definitions += ItemDefinition(id = itemMoonAmulet_13114, internalLevel = 1, pet = ItemPet(petSelhteus), rare = true, shopBuyable = false, shopSellable = false)
        definitions += ItemDefinition(id = itemStormGorget_13167, internalLevel = 1, pet = ItemPet(petHadhayosh), rare = true, shopBuyable = false, shopSellable = false)
        definitions += ItemDefinition(id = armorAtzintliNecklace_28385, internalLevel = 1, pet = ItemPet(petTchakka), rare = true, shopBuyable = false, shopSellable = false)
        definitions += ItemDefinition(id = itemCallersPendant_11619, internalLevel = 1, pet = ItemPet(petAkash), rare = true, shopBuyable = false, shopSellable = false)
        definitions += ItemDefinition(id = itemBeastCollar_13121, internalLevel = 1, pet = ItemPet(petSlime, spawnAnimation = DatId.pop), rare = true, shopBuyable = false, shopSellable = false)

        // Mining
        definitions += (itemFireCrystal_4096.id .. itemDarkCrystal_4103.id).map { ItemDefinition(id = it, internalLevel = 1) }
        definitions += (itemFireCluster_4104.id .. itemDarkCluster_4111.id).map { ItemDefinition(id = it, internalLevel = 20) }

        definitions += ItemDefinition(id = itemCopperOre_640, internalLevel = 3)
        definitions += ItemDefinition(id = itemZincOre_642, internalLevel = 5)

        definitions += ItemDefinition(id = itemSilverOre_736, internalLevel = 8)
        definitions += (itemRedRock_769 .. itemWhiteRock_776).map { ItemDefinition(id = it, internalLevel = 10) }

        definitions += ItemDefinition(id = itemGranite_1465, internalLevel = 12)
        definitions += ItemDefinition(id = itemMythrilOre_644, internalLevel = 15)

        definitions += ItemDefinition(id = itemGoldOre_737, internalLevel = 20)
        definitions += (itemFireOre_1255 .. itemDarkOre_1262).map { ItemDefinition(id = it, internalLevel = 25) }

        definitions += ItemDefinition(id = itemAluminumOre_678, internalLevel = 28)
        definitions += ItemDefinition(id = itemPlatinumOre_738, internalLevel = 30)

        definitions += (itemFlameGeode_3297 .. itemShadowGeode_3304).map { ItemDefinition(id = it, internalLevel = 35) }
        definitions += ItemDefinition(id = itemOrichalcumOre_739, internalLevel = 40)

        // Crafting: Materials
        definitions += ItemDefinition(id = itemCopperIngot_648, internalLevel = 3)
        definitions += ItemDefinition(id = itemBrassIngot_650, internalLevel = 5)
        definitions += ItemDefinition(id = itemSilverIngot_744, internalLevel = 10)
        definitions += ItemDefinition(id = itemMythrilIngot_653, internalLevel = 15)
        definitions += ItemDefinition(id = itemGoldIngot_745, internalLevel = 20)
        definitions += ItemDefinition(id = itemPlatinumIngot_746, internalLevel = 28)

        // Crafting: Gems
        definitions += makeGems()

        // Rings
        definitions += ItemDefinition(id = itemCopperRing_13454, internalLevel = 3, meldable = true, initialCapacity = 30, meldBonusCaps = ringMeldCaps(tier = 1))
        definitions += ItemDefinition(id = itemCopperRing1_13492, internalLevel = 3, meldable = true, initialCapacity = 42, meldBonusCaps = ringMeldCaps(tier = 1))

        definitions += ItemDefinition(id = itemBrassRing_13465, internalLevel = 5, meldable = true, initialCapacity = 30, meldBonusCaps = ringMeldCaps(tier = 2))
        definitions += ItemDefinition(id = itemBrassRing1_13493, internalLevel = 5, meldable = true, initialCapacity = 42, meldBonusCaps = ringMeldCaps(tier = 2))

        definitions += ItemDefinition(id = itemSilverRing_13456, internalLevel = 10, meldable = true, initialCapacity = 30, meldBonusCaps = ringMeldCaps(tier = 3))
        definitions += ItemDefinition(id = itemSilverRing1_13518, internalLevel = 10, meldable = true, initialCapacity = 42, meldBonusCaps = ringMeldCaps(tier = 3))

        definitions += listOf(
            itemSardonyxRing_13444 to Fire,
            itemClearRing_13470 to Ice,
            itemAmberRing_13473 to Earth,
            itemTourmalineRing_13468 to Wind,
            itemAmethystRing_13471 to Lightning,
            itemLapisLazuliRing_13472 to Water,
            itemOpalRing_13443 to Light,
            itemOnyxRing_13474 to Dark,
        ).map { ItemDefinition(id = it.first, internalLevel = 12, meldable = true, initialCapacity = 30, meldBonusCaps = ringMeldCaps(tier = 3, element = it.second), initialMelds = ringInitialMelds(tier = 3, element = it.second)) }

        definitions += listOf(
            itemCourageRing_13522 to Fire,
            itemTranquilityRing_13525 to Ice,
            itemStaminaRing_13526 to Earth,
            itemReflexRing_13521 to Wind,
            itemKnowledgeRing_13523 to Lightning,
            itemBalanceRing_13524 to Water,
            itemEnergyRing_13527 to Light,
            itemHopeRing_13528 to Dark,
        ).map { ItemDefinition(id = it.first, internalLevel = 12, meldable = true, initialCapacity = 42, meldBonusCaps = ringMeldCaps(tier = 3, element = it.second), initialMelds = ringInitialMelds(tier = 3, element = it.second)) }

        definitions += ItemDefinition(id = itemMythrilRing_13446, internalLevel = 15, meldable = true, initialCapacity = 30, meldBonusCaps = ringMeldCaps(tier = 4))
        definitions += ItemDefinition(id = itemMythrilRing1_13519, internalLevel = 15, meldable = true, initialCapacity = 42, meldBonusCaps = ringMeldCaps(tier = 4))

        definitions += listOf(
            itemGarnetRing_13477 to Fire,
            itemGosheniteRing_13478 to Ice,
            itemSpheneRing_13481 to Earth,
            itemPeridotRing_13476 to Wind,
            itemAmetrineRing_13479 to Lightning,
            itemTurquoiseRing_13480 to Water,
            itemPearlRing_13483 to Light,
            itemBlackRing_13482 to Dark,
        ).map { ItemDefinition(id = it.first, internalLevel = 18, meldable = true, initialCapacity = 30, meldBonusCaps = ringMeldCaps(tier = 4, element = it.second), initialMelds = ringInitialMelds(tier = 4, element = it.second)) }

        definitions += listOf(
            itemPuissanceRing_13530 to Fire,
            itemWisdomRing_13531 to Ice,
            itemVerveRing_13534 to Earth,
            itemAlacrityRing_13529 to Wind,
            itemDeftRing_13532 to Lightning,
            itemSolaceRing_13533 to Water,
            itemLoyaltyRing_13536 to Light,
            itemAuraRing_13535 to Dark,
        ).map { ItemDefinition(id = it.first, internalLevel = 18, meldable = true, initialCapacity = 42, meldBonusCaps = ringMeldCaps(tier = 4, element = it.second), initialMelds = ringInitialMelds(tier = 4, element = it.second)) }

        definitions += ItemDefinition(id = itemGoldRing_13445, internalLevel = 22, meldable = true, initialCapacity = 30, meldBonusCaps = ringMeldCaps(tier = 5))
        definitions += ItemDefinition(id = itemGoldRing1_13520, internalLevel = 22, meldable = true, initialCapacity = 42, meldBonusCaps = ringMeldCaps(tier = 5))

        definitions += listOf(
            itemSunRing_13485 to Fire,
            itemZirconRing_13486 to Ice,
            itemChrysoberylRing_13489 to Earth,
            itemJadeiteRing_13484 to Wind,
            itemFluoriteRing_13487 to Lightning,
            itemAquamarineRing_13488 to Water,
            itemMoonRing_13491 to Light,
            itemPainiteRing_13490 to Dark,
        ).map { ItemDefinition(id = it.first, internalLevel = 25, meldable = true, initialCapacity = 30, meldBonusCaps = ringMeldCaps(tier = 5, element = it.second), initialMelds = ringInitialMelds(tier = 5, element = it.second)) }

        definitions += listOf(
            itemVictoryRing_13538 to Fire,
            itemGeniusRing_13539 to Ice,
            itemVigorRing_13542 to Earth,
            itemCelerityRing_13537 to Wind,
            itemGraceRing_13540 to Lightning,
            itemSerenityRing_13541 to Water,
            itemAllureRing_13544 to Light,
            itemMysticRing_13543 to Dark,
        ).map { ItemDefinition(id = it.first, internalLevel = 25, meldable = true, initialCapacity = 42, meldBonusCaps = ringMeldCaps(tier = 5, element = it.second), initialMelds = ringInitialMelds(tier = 5, element = it.second)) }

        definitions += ItemDefinition(id = itemPlatinumRing_13447, internalLevel = 30, meldable = true, initialCapacity = 30, meldBonusCaps = ringMeldCaps(tier = 6))
        definitions += ItemDefinition(id = itemPlatinumRing1_13498, internalLevel = 30, meldable = true, initialCapacity = 42, meldBonusCaps = ringMeldCaps(tier = 6))

        definitions += listOf(
            itemRubyRing_13449 to Fire,
            itemDiamondRing_13450 to Ice,
            itemEmeraldRing_13448 to Wind,
            itemTopazRing_13453 to Earth,
            itemSpinelRing_13451 to Lightning,
            itemSapphireRing_13452 to Water,
            itemAngelsRing_13463 to Light,
            itemDeathRing_13462 to Dark,
        ).map { ItemDefinition(id = it.first, internalLevel = 33, meldable = true, initialCapacity = 30, meldBonusCaps = ringMeldCaps(tier = 6, element = it.second), initialMelds = ringInitialMelds(tier = 6, element = it.second)) }

        definitions += listOf(
            itemTriumphRing_13305 to Fire,
            itemOmniscientRing_13306 to Ice,
            itemNimbleRing_13304 to Wind,
            itemRobustRing_13309 to Earth,
            itemAdroitRing_13307 to Lightning,
            itemCommunionRing_13308 to Water,
            itemHeavensRing_13311 to Light,
            itemHadesRing_13310 to Dark,
        ).map { ItemDefinition(id = it.first, internalLevel = 33, meldable = true, initialCapacity = 42, meldBonusCaps = ringMeldCaps(tier = 6, element = it.second), initialMelds = ringInitialMelds(tier = 6, element = it.second)) }

        definitionsById = definitions.associateBy { it.id }
        validate()
    }

    operator fun get(inventoryItem: InventoryItem): ItemDefinition {
        return getNullable(inventoryItem) ?: throw IllegalStateException("[${inventoryItem.id}] No such item definition")
    }

    fun getNullable(inventoryItem: InventoryItem): ItemDefinition? {
        val definition = definitionsById[inventoryItem.id] ?: return null
        return definition.scale(inventoryItem)
    }

    private fun validate() {
        for ((id, def) in definitionsById) {
            val info = InventoryItems[id]
            if (info.itemType == InventoryItemType.Weapon && !info.isGrip()) {
                check(def.damage > 0) { "[$def] Damage is unset for weapon" }
                check(def.delay > 0) { "[$def] Delay is unset for weapon" }
            }
        }
    }

    fun wdmg(tier: Int): Int {
        return when (tier) {
            1 -> 50
            2 -> 90
            3 -> 140
            4 -> 220
            5 -> 350
            6 -> 540
            7 -> 840
            8 -> 1320
            9 -> 2060
            10 -> 3210
            11 -> 5000
            12 -> 10_000
            else -> throw IllegalStateException("[wdmg] Undefined tier: $tier")
        }
    }

}

object WeaponPathHelper {

    data class WeaponConfig(val tier: Int, val itemId: Int, val upgradeOptions: List<ItemId> = emptyList(), val buyable: Boolean = true)

    fun makePath(path: WeaponPath, vararg configs: WeaponConfig): List<ItemDefinition> {
        val delay = when (path) {
            WeaponPath.MultiAttack -> 180
            WeaponPath.Tp -> 210
            WeaponPath.WeaponSkill -> 240
            WeaponPath.Magic -> 240
            WeaponPath.Crit -> 270
        }

        val damageRange = when (path) {
            WeaponPath.MultiAttack -> 0.9
            WeaponPath.Tp -> 1.0
            WeaponPath.Magic -> 1.25
            WeaponPath.WeaponSkill -> 1.25
            WeaponPath.Crit -> 1.35
        }

        return configs.map {
            val level = ((it.tier - 1) * 5).coerceAtLeast(1)

            val itemRequirements = when(it.tier) {
                1 -> ItemBuildUpMaterial(itemId = 3980, quantity = 1)
                2 -> ItemBuildUpMaterial(itemId = 3979, quantity = 1)
                3 -> ItemBuildUpMaterial(itemId = 1723, quantity = 1)
                4 -> ItemBuildUpMaterial(itemId = 2168, quantity = 1)
                5 -> ItemBuildUpMaterial(itemId = 4014, quantity = 1)
                6 -> ItemBuildUpMaterial(itemId = itemVulcaniteOre_9075, quantity = 1)
                else -> null
            }

            val upgradeOptions = it.upgradeOptions.map { ItemBuildUpOption(destinationId = it, rankRequirement = 15, itemRequirement = itemRequirements) }

            ItemDefinition(
                id = it.itemId,
                internalLevel = level,
                damage = (wdmg(it.tier) * damageRange).roundToInt(),
                maxDamage = (wdmg(it.tier + 1) * damageRange).roundToInt(),
                magicDamage = (wdmg(it.tier) * 1.35).roundToInt(),
                maxMagicDamage = (wdmg(it.tier + 1) * 1.35).roundToInt(),
                delay = delay,
                ranked = true,
                canRankUp = true,
                dynamicQuality = true,
                slotted = true,
                applyRandomSlots = true,
                upgradeOptions = upgradeOptions,
                abilities = getAbilities(tier = it.tier, weaponPath = path),
                traits = WeaponTraits.getWeaponTraits(it.tier, path),
                shopBuyable = it.buyable,
            )
        }
    }

    fun makeOffPath(tier: Int, itemId: ItemId, internalLevel: Int, damageRange: Double, delay: Int, abilities: List<AbilitySkillId>, traits: List<ItemTrait>): ItemDefinition {
        return ItemDefinition(
            id = itemId,
            internalLevel = internalLevel,
            damage = (wdmg(tier) * damageRange).roundToInt(),
            maxDamage = (wdmg(tier + 1) * damageRange).roundToInt(),
            magicDamage = (wdmg(tier) * 1.35).roundToInt(),
            maxMagicDamage = (wdmg(tier + 1) * 1.35).roundToInt(),
            delay = delay,
            ranked = true,
            canRankUp = true,
            dynamicQuality = true,
            slotted = true,
            applyRandomSlots = true,
            upgradeOptions = emptyList(),
            abilities = abilities,
            traits = traits,
            shopBuyable = false,
        )
    }

}

object GemHelper {

    private class GemTier(val itemLevel: Int, val potency: Int, val gems: List<Pair<SpellElement, Int>>)

    private val gemTiers = listOf(
        GemTier(itemLevel = 10, potency = 1, listOf(
            Fire to itemSardonyx_807,
            Ice to itemClearTopaz_809,
            Wind to itemTourmaline_806,
            Earth to itemAmber_814,
            Lightning to itemAmethyst_800,
            Water to itemLapisLazuli_795,
            Light to itemLightOpal_796,
            Dark to itemOnyx_799)),
        GemTier(itemLevel = 15, potency = 2, listOf(
            Fire to itemGarnet_790,
            Ice to itemGoshenite_808,
            Wind to itemPeridot_788,
            Earth to itemSphene_815,
            Lightning to itemAmetrine_811,
            Water to itemTurquoise_798,
            Light to itemPearl_792,
            Dark to itemBlackPearl_793)),
        GemTier(itemLevel = 22, potency = 3, listOf(
            Fire to itemSunstone_803,
            Ice to itemZircon_805,
            Wind to itemJadeite_784,
            Earth to itemChrysoberyl_801,
            Lightning to itemFluorite_810,
            Water to itemAquamarine_791,
            Light to itemMoonstone_802,
            Dark to itemPainite_797,
        )),
        GemTier(itemLevel = 30, potency = 4, listOf(
            Fire to itemRuby_786,
            Ice to itemDiamond_787,
            Wind to itemEmerald_785,
            Earth to itemTopaz_789,
            Lightning to itemSpinel_804,
            Water to itemSapphire_794,
            Light to itemAngelstone_813,
            Dark to itemDeathstone_812,
        )),
    )

    private val allGemIds = gemTiers.flatMap { it.gems.map { g -> g.second } }.toSet()

    fun makeGems(): List<ItemDefinition> {
        val definitions = ArrayList<ItemDefinition>()

        for (gemTier in gemTiers) {
            for ((gemElement, gemId) in gemTier.gems) {
                val augmentId = AccessoryMeldCapHelper.getAugmentId(element = gemElement) ?: continue
                definitions += ItemDefinition(
                    id = gemId,
                    internalLevel = gemTier.itemLevel,
                    capacityAugment = ItemCapacityAugment(augmentId, potency = gemTier.potency, capacity = 6),
                )
            }
        }

        return definitions
    }

    fun isGem(itemId: Int): Boolean {
        return allGemIds.contains(itemId)
    }

}

object Floor1AugmentPools {
    val bronzeEquipmentAugmentPool = uniform(12, 13, 14, 15, 16, 18, 23, 26, 27, 28, 30, 31, 34, 35, 36)
    val backAdditionalAugmentPool = uniform(12, 18, 22, 23, 24, 26, 27, 28)
    val waistAdditionalAugmentPool = uniform(12, 18, 22, 23, 24, 26, 27, 28)

    val standardBackAugments = listOf(backAdditionalAugmentPool, backAdditionalAugmentPool, backAdditionalAugmentPool)
    val standardWaistAugments = listOf(waistAdditionalAugmentPool, waistAdditionalAugmentPool, waistAdditionalAugmentPool)
}