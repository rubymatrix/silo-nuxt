package xim.poc.game.configuration.assetviewer

import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.CombatStats
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.MonsterDefinition
import xim.poc.game.configuration.MonsterId
import xim.poc.game.configuration.NoActionBehaviorId
import xim.poc.game.configuration.constants.*

object AssetViewerMonsterDefinitions {

    val definitions: List<MonsterDefinition>

    init {
        definitions = ArrayList()

        definitions += MonsterDefinition(MonsterId(1), "Mandy", ModelLook.npc(0x012C), behaviorId = AssetViewerMonsterBehaviorId, mobSkills = listOf(
            mskillHeadButt_44, mskillDreamFlower_45, mskillWildOats_46, mskillPhotosynthesis_48, mskillLeafDagger_49, mskillScream_50
        ))

        definitions += MonsterDefinition(MonsterId(2), "Crawler", ModelLook.npc(0x018C), behaviorId = AssetViewerMonsterBehaviorId, mobSkills = listOf(
            mskillStickyThread_88, mskillPoisonBreath_89, mskillCocoon_90
        ))

        definitions += MonsterDefinition(MonsterId(3), "Gobbie", ModelLook.npc(0x01E5), behaviorId = AssetViewerMonsterBehaviorId, mobSkills = listOf(
            mskillGoblinRush_334, mskillBombToss_335, mskillBombToss_336,
        ))

        definitions += MonsterDefinition(MonsterId(4), "Yagudo", ModelLook.npc(0x0246), behaviorId = AssetViewerMonsterBehaviorId, mobSkills = listOf(
            mskillFeatherStorm_361, mskillDoubleKick_362, mskillParry_363, mskillSweep_364
        ))

        definitions += MonsterDefinition(MonsterId(5), "Bird", ModelLook.npc(0x01BD), behaviorId = AssetViewerMonsterBehaviorId, mobSkills = listOf(
            mskillHelldive_366, mskillWingCutter_367,
        ))

        definitions += MonsterDefinition(MonsterId(6), "Dhalmel", ModelLook.npc(0x014C), behaviorId = AssetViewerMonsterBehaviorId, mobSkills = listOf(
             mskillSonicWave_24, mskillStomping_25, mskillColdStare_28, mskillWhistle_29, mskillBerserk_30, mskillHealingBreeze_31,
        ))

        definitions += MonsterDefinition(MonsterId(7), "Fire", ModelLook.npc(0x01B4), behaviorId = AssetViewerMonsterBehaviorId) {
            it.gainStatusEffect(StatusEffect.BlazeSpikes); it.gainStatusEffect(StatusEffect.Enfire)
        }

        definitions += MonsterDefinition(MonsterId(8), "Ice", ModelLook.npc(0x01B5), behaviorId = AssetViewerMonsterBehaviorId) {
            it.gainStatusEffect(StatusEffect.IceSpikes); it.gainStatusEffect(StatusEffect.Enblizzard)
        }

        definitions += MonsterDefinition(MonsterId(9), "Earth", ModelLook.npc(0x01B7), behaviorId = AssetViewerMonsterBehaviorId)

        definitions += MonsterDefinition(MonsterId(10), "Dummy", ModelLook.npc(0x091B), NoActionBehaviorId, movementControllerFn = { NoOpActorController() }, baseCombatStats = CombatStats.defaultBaseStats.copy(maxHp = 999))

    }

}