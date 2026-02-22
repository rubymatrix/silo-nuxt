package xim.poc.game.configuration.v0

import xim.poc.game.AugmentId
import xim.poc.game.actor.components.CapacityAugment
import xim.resource.SpellElement
import kotlin.math.ceil
import kotlin.math.roundToInt

object AccessoryMeldCapHelper {

    private val ringAugments = mapOf(
        AugmentId.StoreTp to SpellElement.Earth,
        AugmentId.CriticalHitRate to SpellElement.Lightning,
        AugmentId.DoubleAttack to SpellElement.Water,
        AugmentId.WeaponSkillDamage to SpellElement.Fire,
        AugmentId.MagicAttackBonus to SpellElement.Ice,
        AugmentId.Haste to SpellElement.Wind,
        AugmentId.ConserveMp to SpellElement.Dark,
        AugmentId.ConserveTp to SpellElement.Light,
    )

    private val tierCaps = mapOf(
        1 to 1,
        2 to 2,
        3 to 3,
        4 to 5,
        5 to 7,
        6 to 9,
        7 to 11,
        8 to 13,
        9 to 15,
    )

    fun getAugmentId(element: SpellElement): AugmentId? {
        return ringAugments.entries.firstOrNull { it.value == element }?.key
    }

    fun ringMeldCaps(tier: Int, element: SpellElement? = null): Map<AugmentId, Int> {
        val tierCap = tierCaps[tier] ?: throw IllegalStateException("No such ring cap definition: $tier")

        return ringAugments.mapValues {
            val capMultiplier = if (it.value == element) { 1.5f } else { 1f }
            ceil(tierCap * capMultiplier).roundToInt()
        }
    }

    fun ringInitialMelds(tier: Int, element: SpellElement): List<CapacityAugment> {
        val augment = getAugmentId(element) ?: return emptyList()
        val meldCap = ringMeldCaps(tier, element)[augment] ?: return emptyList()
        return listOf(CapacityAugment(augment, meldCap/3))
    }

}