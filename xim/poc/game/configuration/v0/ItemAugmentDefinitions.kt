package xim.poc.game.configuration.v0

import xim.poc.game.AugmentId
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.ItemAugmentId
import xim.poc.ui.ShiftJis
import xim.resource.table.AugmentTable
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

fun AugmentId.toDescription(potency: Int, bonusValue: Int? = null, maxValue: Int? = null, allMeldCaps: Boolean = false): String {
    val effectivePotency = potency + (bonusValue ?: 0)
    val augmentText = AugmentTable.getAugmentName(this, effectivePotency)

    val augmentDisplay = if (bonusValue != null) {
        "${ShiftJis.colorItem}${augmentText}${ShiftJis.colorInfo}"
    } else {
        augmentText
    }

    val bonusDisplay = if (bonusValue != null) {
        "${ShiftJis.colorItem}(+$bonusValue)${ShiftJis.colorInfo}"
    } else {
        ""
    }

    val maxValueDisplay = if ((bonusValue != null || allMeldCaps) && maxValue != null) {
        "${ShiftJis.colorItem}[$maxValue]${ShiftJis.colorInfo}"
    } else {
        ""
    }

    return "$augmentDisplay$bonusDisplay$maxValueDisplay"
}

fun interface ItemAugmentValue {
    fun calculate(item: InventoryItem): Int
}

data class ItemAugmentDefinition(
    val id: ItemAugmentId,
    val attribute: AugmentId,
    val valueFn: ItemAugmentValue,
)

object ItemAugmentDefinitions {

    const val maxPossibleRankLevel = 30

    val definitions: Map<ItemAugmentId, ItemAugmentDefinition>

    init {
        val defs = ArrayList<ItemAugmentDefinition>()

        // Standard combat stats
        defs += ItemAugmentDefinition(id = 3, attribute = AugmentId.STR) { standardScaling(initialValue = 4f, it) }
        defs += ItemAugmentDefinition(id = 4, attribute = AugmentId.DEX) { standardScaling(initialValue = 4f, it) }
        defs += ItemAugmentDefinition(id = 5, attribute = AugmentId.VIT) { standardScaling(initialValue = 4f, it) }
        defs += ItemAugmentDefinition(id = 6, attribute = AugmentId.AGI) { standardScaling(initialValue = 4f, it) }
        defs += ItemAugmentDefinition(id = 7, attribute = AugmentId.INT) { standardScaling(initialValue = 4f, it) }
        defs += ItemAugmentDefinition(id = 8, attribute = AugmentId.MND) { standardScaling(initialValue = 4f, it) }
        defs += ItemAugmentDefinition(id = 9, attribute = AugmentId.CHR) { standardScaling(initialValue = 4f, it) }

        defs += ItemAugmentDefinition(id = 12, attribute = AugmentId.FastCast) { rankTierScaling(8f, it) }
        defs += ItemAugmentDefinition(id = 13, attribute = AugmentId.DoubleAttack) { rankTierScaling(5f, it) }
        defs += ItemAugmentDefinition(id = 14, attribute = AugmentId.StoreTp) { rankTierScaling(6f, it) }
        defs += ItemAugmentDefinition(id = 15, attribute = AugmentId.Haste) { rankTierScaling(5f, it) }
        defs += ItemAugmentDefinition(id = 16, attribute = AugmentId.WeaponSkillDamage) { rankTierScaling(5f, it) }
        defs += ItemAugmentDefinition(id = 17, attribute = AugmentId.CriticalHitDamage) { rankTierScaling(5f, it) }
        defs += ItemAugmentDefinition(id = 18, attribute = AugmentId.PhysicalDamageTaken) { rankScaling(-3f, it) }
        defs += ItemAugmentDefinition(id = 19, attribute = AugmentId.Regen) { 1 }
        defs += ItemAugmentDefinition(id = 20, attribute = AugmentId.Refresh) { 1 }
        defs += ItemAugmentDefinition(id = 21, attribute = AugmentId.CriticalHitRate) { rankTierScaling(3f, it) }
        defs += ItemAugmentDefinition(id = 22, attribute = AugmentId.EnemyCriticalHitRate) { rankScaling(-3f, it) }
        defs += ItemAugmentDefinition(id = 23, attribute = AugmentId.SubtleBlow) { rankTierScaling(8f, it) }
        defs += ItemAugmentDefinition(id = 24, attribute = AugmentId.SpellInterruptDown) { rankTierScaling(8f, it) }
        defs += ItemAugmentDefinition(id = 25, attribute = AugmentId.HP) { standardScaling(initialValue = 5f, it) }
        defs += ItemAugmentDefinition(id = 26, attribute = AugmentId.MagicalDamageTaken) { rankScaling(-3f, it) }
        defs += ItemAugmentDefinition(id = 27, attribute = AugmentId.DamageTaken) { rankScaling(-2f, it) }
        defs += ItemAugmentDefinition(id = 28, attribute = AugmentId.ParryingRate) { rankTierScaling(4f, it) }
        defs += ItemAugmentDefinition(id = 30, attribute = AugmentId.MagicAttackBonus) { rankTierScaling(5f, it) }
        defs += ItemAugmentDefinition(id = 31, attribute = AugmentId.TripleAttack) { rankTierScaling(3f, it) }
        defs += ItemAugmentDefinition(id = 32, attribute = AugmentId.DoubleDamage) { rankTierScaling(3f, it) }
        defs += ItemAugmentDefinition(id = 33, attribute = AugmentId.ConserveTp) { rankTierScaling(3f, it) }
        defs += ItemAugmentDefinition(id = 34, attribute = AugmentId.MagicBurstDamage) { rankTierScaling(6f, it) }
        defs += ItemAugmentDefinition(id = 35, attribute = AugmentId.SkillChainDamage) { rankTierScaling(6f, it) }
        defs += ItemAugmentDefinition(id = 36, attribute = AugmentId.QuadrupleAttack) { rankTierScaling(2f, it) }
        defs += ItemAugmentDefinition(id = 37, attribute = AugmentId.TpBonus) { 0 }
        defs += ItemAugmentDefinition(id = 38, attribute = AugmentId.FollowUpAttack) { 0 }
        defs += ItemAugmentDefinition(id = 39, attribute = AugmentId.ElementalWeaponSkillDamage) { 0 }
        defs += ItemAugmentDefinition(id = 40, attribute = AugmentId.CriticalTpGain) { 0 }
        defs += ItemAugmentDefinition(id = 1023, attribute = AugmentId.Blank) { 0 }

        definitions = defs.associateBy { it.id }
    }

    operator fun get(augmentId: ItemAugmentId): ItemAugmentDefinition {
        return definitions[augmentId] ?: throw IllegalStateException("[$augmentId] No such augment definition")
    }

    private fun standardScaling(initialValue: Float, item: InventoryItem): Int {
        val itemLevel = ItemDefinitions[item].internalLevel
        val rawValue = ceil(initialValue * 1.084472f.pow(itemLevel)) * qualityBonus(item)
        return rawValue.roundToInt()
    }

    private fun rankScaling(initialValue: Float, item: InventoryItem): Int {
        val rawValue = initialValue * qualityBonus(item)
        return rawValue.roundToInt()
    }

    private fun rankTierScaling(initialValue: Float, item: InventoryItem): Int {
        val rawValue = initialValue * qualityBonus(item) * itemLevelBonus(item)
        return rawValue.roundToInt()
    }

    fun rankProportion(item: InventoryItem): Float {
        val augment = item.augments ?: return 0f
        return (augment.rankLevel - 1).toFloat() / (maxPossibleRankLevel - 1).toFloat()
    }

    fun getInternalQuality(augmentRank: Int): Int {
        return if (augmentRank <= 5) {
            0
        } else if (augmentRank <= 14) {
            1
        } else if (augmentRank <= 29) {
            2
        } else {
            3
        }
    }

    fun qualityBonus(item: InventoryItem): Float {
        return 1f + 0.5f * rankProportion(item)
    }

    fun itemLevelBonus(item: InventoryItem): Float {
        val itemLevel = ItemDefinitions[item].internalLevel
        return 1f + (itemLevel / 25f).coerceAtMost(1f)
    }

}