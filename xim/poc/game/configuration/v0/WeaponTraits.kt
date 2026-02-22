package xim.poc.game.configuration.v0

import xim.poc.game.AugmentId.*
import xim.poc.game.configuration.v0.AugmentRestriction.*
import xim.poc.game.configuration.v0.ItemTraitCustomId.*
import xim.resource.table.AugmentTable

object WeaponTraits {

    private val tpPathTraits = mapOf(
        1 to listOf(),
        2 to listOf(makeConserveTp(30), makeBluTpGain(50)),
        3 to listOf(makeConserveTp(35), makeBluTpGain(100)),
        4 to listOf(makeConserveTp(40), makeBluTpGain(150)),
        5 to listOf(makeConserveTp(45), makeBluTpGain(200)),
        6 to listOf(makeConserveTp(50), makeBluTpGain(250)),
        7 to listOf(makeConserveTp(55), makeBluTpGain(300)),
        8 to listOf(makeConserveTp(60), makeBluTpGain(350)),
        9 to listOf(makeConserveTp(65), makeBluTpGain(400)),
        10 to listOf(makeConserveTp(70), makeBluTpGain(450)),
        11 to listOf(makeConserveTp(75), makeBluTpGain(500)),
    )

    private val multiAttackPathTraits = mapOf(
        1 to listOf(makeOccAttack2x(50), makeConvertDamageToMp(50)),
        2 to listOf(makeOccAttack2x(60), makeConvertDamageToMp(50)),
        3 to listOf(makeOccAttack2x(70), makeConvertDamageToMp(50)),
        4 to listOf(makeOccAttack2x3x(40), makeConvertDamageToMp(50)),
        5 to listOf(makeOccAttack2x3x(50), makeConvertDamageToMp(50)),
        6 to listOf(makeOccAttack2x3x(60), makeConvertDamageToMp(50)),
        7 to listOf(makeOccAttack2x3x(70), makeConvertDamageToMp(50)),
        8 to listOf(makeOccAttack2x4x(40), makeConvertDamageToMp(50)),
        9 to listOf(makeOccAttack2x4x(50), makeConvertDamageToMp(50)),
        10 to listOf(makeOccAttack2x4x(60), makeConvertDamageToMp(50)),
        11 to listOf(makeOccAttack2x4x(70), makeConvertDamageToMp(50)),
    )

    private val critPathTraits = mapOf(
        1 to listOf(makeCritRate(20), makeCritDamage(50)),
        2 to listOf(makeCritRate(22), makeCritDamage(50)),
        3 to listOf(makeCritRate(24), makeCritDamage(50)),
        4 to listOf(makeCritRate(26), makeCritDamage(50)),
        5 to listOf(makeCritRate(28), makeCritDamage(50)),
        6 to listOf(makeCritRate(30), makeCritDamage(50)),
        7 to listOf(makeCritRate(32), makeCritDamage(50)),
        8 to listOf(makeCritRate(34), makeCritDamage(50)),
        9 to listOf(makeCritRate(36), makeCritDamage(50)),
        10 to listOf(makeCritRate(38), makeCritDamage(50)),
        11 to listOf(makeCritRate(40), makeCritDamage(50)),
    )

    private val magicPathTraits = mapOf(
        1 to listOf(makeOccQuickens(35), makeSpontaneityConserveMp(100), makeSpontaneityMbbII(20)),
        2 to listOf(makeOccQuickens(40), makeSpontaneityConserveMp(100), makeSpontaneityMbbII(25)),
        3 to listOf(makeOccQuickens(45), makeSpontaneityConserveMp(100), makeSpontaneityMbbII(30)),
        4 to listOf(makeOccQuickens(50), makeSpontaneityConserveMp(100), makeSpontaneityMbbII(35)),
        5 to listOf(makeOccQuickens(55), makeSpontaneityConserveMp(100), makeSpontaneityMbbII(40)),
        6 to listOf(makeOccQuickens(60), makeSpontaneityConserveMp(100), makeSpontaneityMbbII(45)),
        7 to listOf(makeOccQuickens(65), makeSpontaneityConserveMp(100), makeSpontaneityMbbII(45)),
        8 to listOf(makeOccQuickens(70), makeSpontaneityConserveMp(100), makeSpontaneityMbbII(50)),
        9 to listOf(makeOccQuickens(75), makeSpontaneityConserveMp(100), makeSpontaneityMbbII(55)),
        10 to listOf(makeOccQuickens(80), makeSpontaneityConserveMp(100), makeSpontaneityMbbII(60)),
        11 to listOf(makeOccQuickens(100), makeSpontaneityConserveMp(100), makeSpontaneityMbbII(65)),
    )

    private val wsPathTraits = mapOf(
        1 to listOf(extendsSkillChainDuration(4), makeRestraint(5)),
        2 to listOf(extendsSkillChainDuration(4), makeRestraint(6)),
        3 to listOf(extendsSkillChainDuration(4), makeRestraint(7)),
        4 to listOf(extendsSkillChainDuration(4), makeRestraint(8)),
        5 to listOf(extendsSkillChainDuration(4), makeRestraint(9)),
        6 to listOf(extendsSkillChainDuration(4), makeRestraint(10)),
        7 to listOf(extendsSkillChainDuration(4), makeRestraint(11)),
        8 to listOf(extendsSkillChainDuration(4), makeRestraint(12)),
        9 to listOf(extendsSkillChainDuration(4), makeRestraint(13)),
        10 to listOf(extendsSkillChainDuration(4), makeRestraint(14)),
        11 to listOf(extendsSkillChainDuration(4), makeRestraint(15)),
    )

    fun getWeaponTraits(tier: Int, path: WeaponPath): List<ItemTrait> {
        return (when (path) {
            WeaponPath.Tp -> tpPathTraits[tier]
            WeaponPath.MultiAttack -> multiAttackPathTraits[tier]
            WeaponPath.WeaponSkill -> wsPathTraits[tier]
            WeaponPath.Magic -> magicPathTraits[tier]
            WeaponPath.Crit -> critPathTraits[tier]
        }) ?: emptyList()
    }

    private fun makeOccAttack2x(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(OccAttack2x), potency = potency, handRestriction = HandOnly) {
            "Auto Attack: ${AugmentTable.getAugmentName(OccAttack2x)}"
        }
    }

    private fun makeOccAttack2x3x(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(OccAttack2x3x), potency = potency, handRestriction = HandOnly) {
            "Auto Attack: ${AugmentTable.getAugmentName(OccAttack2x3x)}"
        }
    }

    private fun makeOccAttack2x4x(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(OccAttack2x4x), potency = potency, handRestriction = HandOnly) {
            "Auto Attack: ${AugmentTable.getAugmentName(OccAttack2x4x)}"
        }
    }

    private fun makeConserveTp(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(ConserveTp), potency = potency, handRestriction = None) {
            AugmentTable.getAugmentName(ConserveTp, potency)
        }
    }

    private fun makeCritRate(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(CriticalHitRate), potency = potency, handRestriction = MainOnly) {
            "Main: " + AugmentTable.getAugmentName(CriticalHitRate, potency)
        }
    }

    private fun makeCritDamage(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(CriticalHitDamage), potency = potency, handRestriction = MainOnly) {
            "Main: " + AugmentTable.getAugmentName(CriticalHitDamage, potency)
        }
    }

    private fun makeOccQuickens(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitCustom(WeaponSkillSpontaneity), potency = potency, handRestriction = MainOnly) {
            "Main: Occ. grants \"Spontaneity\""
        }
    }

    private fun makeSpontaneityConserveMp(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitCustom(SpontaneityConserveMp), potency = potency, handRestriction = MainOnly) {
            "Spontaneity: \"Conserve MP\" +$potency"
        }
    }

    private fun makeSpontaneityMbbII(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitCustom(SpontaneityMbbII), potency = potency, handRestriction = MainOnly) {
            "Spontaneity: \"Magic burst damage II\" +$potency%"
        }
    }

    private fun makeConvertDamageToMp(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitCustom(ConvertDamageToMp), potency = potency, handRestriction = HandOnly) {
            "Occ. convert damage dealt to MP"
        }
    }

    private fun makeRestraint(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitCustom(Restraint), potency = potency, handRestriction = MainOnly) {
            "Main: \"Restraint\" +$potency"
        }
    }

    private fun makeBluTpGain(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitCustom(BluTpGain), potency = potency, handRestriction = None) {
            "Blue Magic: Gain $potency TP"
        }
    }

    private fun extendsSkillChainDuration(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitCustom(ExtendsSkillChainDuration), potency = potency, handRestriction = None) {
            "Extends Skillchain duration"
        }
    }

    fun doubleAttack(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(DoubleAttack), potency = potency, handRestriction = None) {
            AugmentTable.getAugmentName(DoubleAttack, potency)
        }
    }

    fun maxHpBonus(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(HP), potency = potency, handRestriction = None) {
            AugmentTable.getAugmentName(HP, potency)
        }
    }

    fun tpBonus(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(TpBonus), potency = potency, handRestriction = None) {
            AugmentTable.getAugmentName(TpBonus, potency)
        }
    }

    fun kickAttacks(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(KickAttacks), potency = potency, handRestriction = None) {
            AugmentTable.getAugmentName(KickAttacks, potency)
        }
    }

    fun impetus(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitCustom(Impetus), potency = potency, handRestriction = None) {
            "\"Impetus\""
        }
    }

    fun retaliation(): ItemTrait {
        return ItemTrait(ItemTraitCustom(Retaliation), potency = 100, handRestriction = None) {
            "\"Retaliation\""
        }
    }

    fun petWyvern(): ItemTrait {
        return ItemTrait(ItemTraitCustom(Wyvern), potency = 100, handRestriction = None) {
            "\"Wyvern\""
        }
    }

    fun refresh(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(Refresh), potency = potency, handRestriction = None) {
            AugmentTable.getAugmentName(Refresh, potency)
        }
    }

    fun regen(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(Regen), potency = potency, handRestriction = None) {
            AugmentTable.getAugmentName(Regen, potency)
        }
    }

    fun regain(potency: Int): ItemTrait {
        return ItemTrait(ItemTraitAugment(LatentRegain), potency = potency, handRestriction = None) {
            AugmentTable.getAugmentName(LatentRegain, potency)
        }
    }

}