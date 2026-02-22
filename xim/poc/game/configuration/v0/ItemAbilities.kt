package xim.poc.game.configuration.v0

import xim.poc.game.configuration.constants.*

object ItemAbilities {

    fun getAbilities(weaponPath: WeaponPath, tier: Int): List<AbilitySkillId> {
        return if (weaponPath == WeaponPath.Magic) {
            when (tier) {
                1 -> listOf(skillShiningBlade_36, skillBurningBlade_33)
                2 -> listOf(skillShiningBlade_36, skillBurningBlade_33, skillRedLotusBlade_34)
                3 -> listOf(skillShiningBlade_36, skillBurningBlade_33, skillRedLotusBlade_34, skillCyclone_20)
                4 -> listOf(skillShiningBlade_36, skillBurningBlade_33, skillSeraphBlade_37, skillCyclone_20, skillChainAffinity_606, skillBurstAffinity_607)
                5 -> listOf(skillFlashNova_172, skillSpiritsWithin_39, skillSeraphBlade_37, skillCyclone_20, skillChainAffinity_606, skillBurstAffinity_607)
                6 -> listOf(skillFlashNova_172, skillSpiritsWithin_39, skillCloudsplitter_76, skillCyclone_20, skillChainAffinity_606, skillBurstAffinity_607)
                7 -> listOf(skillFlashNova_172, skillSpiritsWithin_39, skillCloudsplitter_76, skillBoraAxe_75, skillChainAffinity_606, skillBurstAffinity_607)
                else -> emptyList()
            }
        } else {
            when (tier) {
                1 -> listOf(skillFastBlade_32, skillBurningBlade_33)
                2 -> listOf(skillFastBlade_32, skillBurningBlade_33, skillRedLotusBlade_34)
                3 -> listOf(skillFastBlade_32, skillBurningBlade_33, skillRedLotusBlade_34, skillCircleBlade_38)
                4 -> listOf(skillFastBlade_32, skillBurningBlade_33, skillVorpalBlade_40, skillCircleBlade_38, skillChainAffinity_606, skillBurstAffinity_607)
                5 -> listOf(skillSwiftBlade_41, skillSpiritsWithin_39, skillVorpalBlade_40, skillCircleBlade_38, skillChainAffinity_606, skillBurstAffinity_607)
                6 -> listOf(skillSwiftBlade_41, skillSpiritsWithin_39, skillSavageBlade_42, skillCircleBlade_38, skillChainAffinity_606, skillBurstAffinity_607)
                7 -> listOf(skillSwiftBlade_41, skillSpiritsWithin_39, skillSavageBlade_42, skillMistralAxe_71, skillChainAffinity_606, skillBurstAffinity_607)
                else -> emptyList()
            }
        }
    }

}