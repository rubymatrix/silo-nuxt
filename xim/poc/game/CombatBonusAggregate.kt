package xim.poc.game

import xim.poc.game.event.AttackAddedEffectType
import xim.poc.game.event.AttackRetaliationEffectType
import xim.poc.game.event.AttackStatusEffect
import xim.poc.game.event.AutoAttackType
import xim.resource.EquipSlot
import xim.resource.SpellElement
import xim.util.addInPlace
import kotlin.time.Duration

fun Int.toMultiplier(min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE): Float {
    return (1f + (this/100f)).coerceIn(min, max)
}

fun Int.toPenaltyMultiplier(min: Float = 0f, max: Float = Float.MAX_VALUE): Float {
    return (1f - (this/100f)).coerceIn(min, max)
}

data class AutoAttackEffect(
    val effectPower: Int,
    val effectType: AttackAddedEffectType,
    val procChance: Int = 100,
    val statusEffect: AttackStatusEffect? = null,
)

data class RetaliationBonus(
    val power: Int,
    val type: AttackRetaliationEffectType,
    val statusEffect: AttackStatusEffect? = null,
)

data class CombatBonusSubAggregate(
    var occAttack2x: Int = 0,
    var occAttack3x: Int = 0,
    var occAttack4x: Int = 0,
    var occDoubleDamage: Int = 0,
    var occDamageToMp: Int = 0,
)

data class CombatBonusAggregate(
    val actorState: ActorState,
    var additiveStats: CombatStatBuilder = CombatStatBuilder(),
    var multiplicativeStats: MutableMap<CombatStat, Float> = HashMap(),
    var statusResistances: MutableMap<StatusEffect, Int> = HashMap(),
    var statusDurationMultipliers: MutableMap<StatusEffect, Float> = HashMap(),
    var subTypeAggregate: MutableMap<EquipSlot, CombatBonusSubAggregate> = HashMap(),
    var allStatusResistRate: Int = 0,
    var elementalResistance: HashMap<SpellElement, Int> = HashMap(),
    var regen: Int = 0,
    var refresh: Int = 0,
    var regain: Int = 0,
    var magicAttackBonus: Int = 0,
    var criticalHitRate: Int = 0,
    var enemyCriticalHitRate: Int = 0,
    var haste: Int = 0,
    var spellInterruptDown: Int = 0,
    var physicalDamageTaken: Int = 0,
    var magicalDamageTaken: Int = 0,
    var tpBonus: Int = 0,
    var guardRate: Int = 0,
    var parryRate: Int = 0,
    var evasionRate: Int = 0,
    var counterRate: Int = 0,
    var fastCast: Int = 0,
    var mobSkillFastCast: Int = 0,
    var autoAttackScale: Float = 1f,
    var storeTp: Int = 0,
    var doubleAttack: Int = 0,
    var tripleAttack: Int = 0,
    var quadrupleAttack: Int = 0,
    var dualWield: Int = 0,
    var subtleBlow: Int = 0,
    var weaponSkillDamage: Int = 0,
    var criticalHitDamage: Int = 0,
    var skillChainDamage: Int = 0,
    var conserveTp: Int = 0,
    var conserveMp: Int = 0,
    var saveTp: Int = 0,
    var magicBurstDamage: Int = 0,
    var magicBurstDamageII: Int = 0,
    var movementSpeed: Int = 0,
    var criticalTpGain: Int = 0,
    var elementalWeaponSkillDamage: Int = 0,
    var doubleDamage: Int = 0,
    var followUpAttack: Int = 0,
    var knockBackResistance: Int = 0,
    var boost: Int = 0,
    var curePotency: Int = 0,
    var canDualWield: Boolean = false,
    var tpRequirementBypass: Boolean = false,
    var autoAttackEffects: MutableList<AutoAttackEffect> = ArrayList(),
    var autoAttackRetaliationEffects: MutableList<RetaliationBonus> = ArrayList(),
    var skillChainWindowBonus: Duration = Duration.ZERO,
    var blueMagicTpGain: Int = 0,
    var restraint: Int = 0,
    var occImmanence: Int = 0,
    var occSpontaneity: Int = 0,
    var kickAttackRate: Int = 0,
    var impetus: Int = 0,
    var retaliation: Int = 0,
) {

    fun resist(statusEffect: StatusEffect, potency: Int) {
        statusResistances.addInPlace(statusEffect, potency)
    }

    fun fullResist(statusEffect: StatusEffect) {
        statusResistances.addInPlace(statusEffect, 100)
    }

    fun fullResist(vararg statusEffects: StatusEffect) {
        statusEffects.forEach { statusResistances.addInPlace(it, 100) }
    }

    operator fun get(autoAttackType: AutoAttackType): CombatBonusSubAggregate {
        return get(autoAttackType.equipSlot())
    }

    operator fun get(equipSlot: EquipSlot): CombatBonusSubAggregate {
        return subTypeAggregate.getOrPut(equipSlot) { CombatBonusSubAggregate() }
    }

}