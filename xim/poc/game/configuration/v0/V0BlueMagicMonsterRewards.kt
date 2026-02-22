package xim.poc.game.configuration.v0

import xim.poc.game.configuration.MonsterId
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.constants.*
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.configuration.v0.escha.plus

object V0BlueMagicMonsterRewards {

    private val rewards = HashMap<MonsterId, SpellSkillId>()

    init {
        rewards[mobWildRabbit_2] = spellFootKick_577
        rewards[mobMinerBee_3] = spellPollen_549
        rewards[mobBabaYaga_135_004] = spellZephyrArrow_556
        rewards[mobCarabosse_135_005] = spellAutumnBreeze_528
        rewards[mobBriareus_135_013] = spellGrandSlam_622
        rewards[mobTopplingTuber_135_020] = spellQueasyshroom_599
        rewards[mobHadhayosh_135_021] = spellThunderbolt_736

        eschaReward(mobWepwawet_288_100, spellMagmaHoplon_755)
        eschaReward(mobAglaophotis_288_105, spellBloodyCaress_756)
        eschaReward(mobVidala_288_110, spellPredatoryGlare_757)
        eschaReward(mobGestalt_288_115, spellCatharsis_761)
        eschaReward(mobRevetaur_288_120, spellLethalTriclip_758)
        eschaReward(mobTangataManu_288_125, spellKaleidoscopicFury_759)
        eschaReward(mobGulltop_288_130, spellRhinowrecker_760)
        eschaReward(mobVyala_288_135, spellChargedWhisker_680)
        eschaReward(mobAngrboda_288_140, spellVolcanicWrath_754)
        eschaReward(mobCunnast_288_145, spellRadiantBreath_565)
        eschaReward(mobFerrodon_288_150, spellDemoralizingRoar_659)
        eschaReward(mobLustfulLydia_288_155, spellBadBreath_604)
        eschaReward(mobNosoi_288_205, spellFreezingGale_762)
    }

    operator fun get(monsterId: MonsterId?): SpellSkillId? {
        return rewards[monsterId]
    }

    fun getAll(): Map<MonsterId, SpellSkillId> = rewards

    private fun eschaReward(mobId: MonsterId, spellId: SpellSkillId) {
        for (difficulty in EschaDifficulty.values()) { rewards[mobId + difficulty] = spellId }
    }

}