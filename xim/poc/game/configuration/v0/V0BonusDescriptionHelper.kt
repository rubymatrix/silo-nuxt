package xim.poc.game.configuration.v0

import xim.poc.game.ActorState
import xim.poc.game.AugmentId
import xim.poc.game.AugmentId.*
import xim.poc.game.toMultiplier
import xim.poc.game.toPenaltyMultiplier
import xim.poc.ui.BonusDescription
import xim.poc.ui.ShiftJis
import xim.resource.table.AugmentTable.getAugmentName
import xim.util.toTruncatedString
import kotlin.math.pow

object V0BonusDescriptionHelper {

    fun describeBonuses(actorState: ActorState): List<BonusDescription> {
        val bonuses = CombatBonusAggregator[actorState]
        val descriptions = ArrayList<BonusDescription>()

        if (bonuses.weaponSkillDamage > 0) {
            descriptions += BonusDescription(getAugmentName(WeaponSkillDamage, bonuses.weaponSkillDamage)) {
                "Multiplies damage from weapon skills by ${bonuses.weaponSkillDamage.toMultiplier().toTruncatedString(2)}"
            }
        }

        if (bonuses.magicAttackBonus > 0) {
            descriptions += BonusDescription(getAugmentName(MagicAttackBonus, bonuses.magicAttackBonus)) {
                "Multiplies damage from all spells by ${bonuses.magicAttackBonus.toMultiplier().toTruncatedString(2)}"
            }
        }

        if (bonuses.magicBurstDamage > 0) {
            descriptions += BonusDescription(getAugmentName(MagicBurstDamage, bonuses.magicBurstDamage)) {
                "Multiplies damage from magic bursts by ${bonuses.magicBurstDamage.toMultiplier().toTruncatedString(2)}"
            }
        }

        if (bonuses.storeTp > 0) {
            descriptions += BonusDescription(getAugmentName(StoreTp, bonuses.storeTp)) {
                "Multiplies TP gained by ${bonuses.storeTp.toMultiplier().toTruncatedString(2)}"
            }
        }

        if (bonuses.conserveTp > 0) {
            descriptions += BonusDescription(getAugmentName(ConserveTp, bonuses.conserveTp)) {
                "+${bonuses.conserveTp}% chance of reducing TP costs by 25${ShiftJis.longTilde}50%"
            }
        }

        if (bonuses.doubleAttack > 0) {
            descriptions += BonusDescription(getAugmentName(DoubleAttack, bonuses.doubleAttack)) {
                "Increases chance of attacking 2 times per hit (attacks and weapon skills) by +${bonuses.doubleAttack}%"
            }
        }

        if (bonuses.tripleAttack > 0) {
            descriptions += BonusDescription(getAugmentName(TripleAttack, bonuses.tripleAttack)) {
                "Increases chance of attacking 3 times per hit (attacks and weapon skills) by +${bonuses.tripleAttack}%"
            }
        }

        if (bonuses.quadrupleAttack > 0) {
            descriptions += BonusDescription(getAugmentName(QuadrupleAttack, bonuses.quadrupleAttack)) {
                "Increases chance of attacking 4 times per hit (attacks and weapon skills) by +${bonuses.quadrupleAttack}%"
            }
        }

        if (bonuses.skillChainDamage > 0) {
            descriptions += BonusDescription(getAugmentName(SkillChainDamage, bonuses.skillChainDamage)) {
                "Multiplies damage from skillchains by ${bonuses.skillChainDamage.toMultiplier().toTruncatedString(2)}"
            }
        }

        if (bonuses.subtleBlow > 0) {
            descriptions += BonusDescription(getAugmentName(SubtleBlow, bonuses.subtleBlow)) {
                """
                 Multiplies enemies' TP gain by ${bonuses.subtleBlow.toPenaltyMultiplier(0.25f, 1f).toTruncatedString(2)}
                   (Maximum total reduction: ${ShiftJis.multiplyX}0.25)
                """.trimIndent()
            }
        }

        if (bonuses.haste > 0) {
            descriptions += BonusDescription(withCap(Haste, bonuses.haste, 80)) {
                """
                 Maximum bonus: +80
                 Multiplies auto-attack delay by ${speedBaseTerm.pow(bonuses.haste).coerceAtLeast(0.2f).toTruncatedString(2)}
                   (Maximum total reduction: ${ShiftJis.multiplyX}0.20)
                 Multiplies spell recast time by ${speedBaseTerm.pow(0.5f * bonuses.haste).coerceAtLeast(0.2f).toTruncatedString(2)}
                   (Maximum total reduction: ${ShiftJis.multiplyX}0.20)
                """.trimIndent()
            }
        }

        if (bonuses.fastCast > 0) {
            descriptions += BonusDescription(withCap(FastCast, bonuses.fastCast, 80)) {
                """
                 Maximum bonus: +80
                 Multiplies spell cast time by ${speedBaseTerm.pow(bonuses.fastCast).coerceAtLeast(0.2f).toTruncatedString(2)}
                   (Maximum total reduction: ${ShiftJis.multiplyX}0.20)
                 Multiplies spell recast time by ${speedBaseTerm.pow(bonuses.fastCast).coerceAtLeast(0.2f).toTruncatedString(2)}
                   (Maximum total reduction: ${ShiftJis.multiplyX}0.20)
                """.trimIndent()
            }
        }

        if (bonuses.dualWield > 0) {
            descriptions += BonusDescription(withCap(DualWield, bonuses.dualWield, 10)) {
                """
                 Maximum bonus: +10
                 Multiplies auto-attack delay by ${speedBaseTerm.pow(bonuses.dualWield).coerceAtLeast(0.2f).toTruncatedString(2)}
                   (Maximum total reduction: ${ShiftJis.multiplyX}0.20)
                """.trimIndent()
            }
        }

        if (bonuses.physicalDamageTaken < 0) {
            descriptions += BonusDescription(withCap(PhysicalDamageTaken, bonuses.physicalDamageTaken, -50)) {
                """
                 Maximum bonus: -50
                 Multiplies physical damage taken by ${bonuses.physicalDamageTaken.toMultiplier(0.5f).toTruncatedString(2)}
                """.trimIndent()
            }
        }

        if (bonuses.magicalDamageTaken < 0) {
            descriptions += BonusDescription(withCap(MagicalDamageTaken, bonuses.magicalDamageTaken, -50)) {
                """
                 Maximum bonus: -50
                 Multiplies magic damage taken by ${bonuses.magicalDamageTaken.toMultiplier(0.5f).toTruncatedString(2)}
                """.trimIndent()
            }
        }

        if (bonuses.parryRate > 0) {
            descriptions += BonusDescription(withCap(ParryingRate, bonuses.parryRate, 70)) {
                """
                 Maximum bonus: 70
                 +${bonuses.parryRate}% chance to negate enemy auto-attacks.
                """.trimIndent()
            }
        }

        if (bonuses.spellInterruptDown > 0) {
            descriptions += BonusDescription(withCap(SpellInterruptDown, bonuses.spellInterruptDown, 100)) {
                """
                Multiplies chance of being interrupted by ${bonuses.spellInterruptDown.toPenaltyMultiplier()}
                """.trimIndent()
            }
        }

        if (bonuses.criticalHitRate > 0) {
            descriptions += BonusDescription(getAugmentName(CriticalHitRate, bonuses.criticalHitRate)) {
                """
                +${bonuses.criticalHitRate}% chance to land a critical-hit.
                """.trimIndent()
            }
        }

        if (bonuses.criticalHitDamage > 0) {
            descriptions += BonusDescription(getAugmentName(CriticalHitDamage, bonuses.criticalHitDamage)) {
                """
                Multiplies damage from critical hits by ${bonuses.criticalHitDamage.toMultiplier().toTruncatedString(2)}
                """.trimIndent()
            }
        }

        return descriptions
    }

    private fun withCap(augmentId: AugmentId, value: Int, cap: Int): String {
        val base = getAugmentName(augmentId, value)
        return if ((cap > 0 && value >= cap) || (cap < 0 && value <= cap)) {
            "${ShiftJis.colorInfo}$base"
        } else {
            base
        }
    }
}