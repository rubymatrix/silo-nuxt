package xim.poc.game.configuration.v0

import xim.poc.ActorController
import xim.poc.DefaultEnemyController
import xim.poc.ModelLook
import xim.poc.ModelLook.Companion.npc
import xim.poc.ModelLook.Companion.npcWithBase
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.CombatStat.maxHp
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.intPotency
import xim.poc.game.configuration.v0.behaviors.*
import xim.poc.game.event.AttackAddedEffectType
import kotlin.math.roundToInt

class V0MonsterFamily(
    val damageScale: Float,
    val delay: Int,
    val statScaling: CombatStats,
    val looks: List<ModelLook>,
    val aggroConfig: MonsterAggroConfig = MonsterAggroConfig.none,
    val defaultAppearanceState: Int = 0,
    val defaultTargetSize: Float = 0f,
    val defaultBonusApplier: (CombatBonusAggregate) -> Unit = {},
    val defaultSkills: List<SkillId> = emptyList(),
    val autoAttackSkills: List<SkillId> = emptyList(),
    val facesTarget: Boolean = true,
    val targetable: Boolean = true,
    val behaviorControllerFn: ((ActorState) -> ActorBehaviorController)? = null,
    val movementControllerFn: () -> ActorController = { DefaultEnemyController() },
)

value class FamilyBehaviorId(val family: V0MonsterFamily): BehaviorId

object V0MonsterFamilies {

    private val hpTable = mapOf(
        1 to 30,
        2 to 40,
        3 to 80,
        4 to 120,
        5 to 250,
        6 to 300,
        7 to 350,
        8 to 425,
        9 to 525,
        10 to 850,
        11 to 950,
        12 to 1200,
        13 to 1500,
        14 to 1750,
        15 to 2500,
        16 to 3250,
        17 to 4000,
        18 to 4750,
        19 to 5500,
        20 to 10800,
        21 to 13500,
        22 to 16500,
        23 to 18500,
        24 to 20250,
        25 to 30000,
        26 to 32500,
        27 to 35000,
        28 to 42250,
        29 to 50787,
        30 to 61109,
        31 to 73485,
        32 to 88271,
        33 to 106122,
        34 to 127587,
        35 to 153449,
        36 to 184488,
        37 to 221786,
        38 to 266636,
        39 to 320583,
        40 to 385467,
        41 to 463475,
        42 to 557202,
        43 to 670033,
        44 to 805350,
        45 to 968205,
        46 to 1164301,
        47 to 1399528,
        48 to 1682724,
        49 to 2022938,
        50 to 2432612
    )
    
    val mandragoraFamily = V0MonsterFamily(
        damageScale = 0.8f,
        delay = 180,
        statScaling = CombatStats(maxHp = 115, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 100, int = 90, mnd = 90, chr = 100),
        looks = listOf(npc(0x012C), npc(0x012D), npc(0x0930)),
        defaultSkills = listOf(mskillHeadButt_44, mskillDreamFlower_45, mskillWildOats_46, mskillPhotosynthesis_48, mskillLeafDagger_49, mskillScream_50),
        defaultBonusApplier = { it.guardRate += 20 },
    )

    val beeFamily = V0MonsterFamily(
        damageScale = 0.8f,
        delay = 180,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 100, int = 90, mnd = 110, chr = 100),
        looks = listOf(npc(0x0110)),
        defaultSkills = listOf(mskillSharpSting_78, mskillPollen_79, mskillFinalSting_80),
    )

    val waspFamily = V0MonsterFamily(
        damageScale = 0.8f,
        delay = 180,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 100, int = 90, mnd = 110, chr = 100),
        looks = listOf(npc(0x0111)),
        defaultSkills = listOf(mskillSharpSting_78, mskillPollen_79, mskillFinalSting_80),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val twitherymFamily = V0MonsterFamily(
        damageScale = 0.8f,
        delay = 180,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 120, vit = 80, agi = 120, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x09E7), npc(0x09E8)),
        defaultSkills = listOf(mskillTempestuousUpheaval_2694, mskillSlicenDice_2695, mskillBlackout_2696),
    )

    val birdFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 210,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 100, vit = 95, agi = 105, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x01BD)),
        defaultSkills = listOf(mskillHelldive_366, mskillWingCutter_367),
    )

    val flockBatFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 210,
        statScaling = CombatStats(maxHp = 90, maxMp = 0, str = 100, dex = 110, vit = 90, agi = 110, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0104)),
        defaultSkills = listOf(mskillSonicBoom_137, mskillJetStream_139),
    )

    val flyFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 210,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 100, vit = 95, agi = 105, int = 105, mnd = 95, chr = 100),
        looks = listOf(npc(0x01C0), npc(0x01C1)),
        defaultSkills = listOf(mskillSomersault_62, mskillCursedSphere_403, mskillVenom_404),
    )

    val sheepFamily = V0MonsterFamily(
        damageScale = 1.15f,
        delay = 300,
        statScaling = CombatStats(maxHp = 120, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 95, int = 95, mnd = 100, chr = 100),
        looks = listOf(npc(0x0154)),
        defaultSkills = listOf(mskillLambChop_4, mskillRage_5, mskillSheepCharge_6, mskillSheepSong_8),
        defaultTargetSize = 0.5f,
        behaviorControllerFn = { FamilySheepBehavior(it) }
    )

    val ramFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 330,
        statScaling = CombatStats(maxHp = 120, maxMp = 0, str = 110, dex = 100, vit = 110, agi = 95, int = 95, mnd = 100, chr = 100),
        looks = listOf(npc(0x0158)),
        defaultTargetSize = 2f,
        defaultSkills = listOf(mskillRage_9, mskillRamCharge_10, mskillRumble_11, mskillGreatBleat_12, mskillPetribreath_13),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
        defaultBonusApplier = { it.knockBackResistance += 100 }
    )

    val snowHareFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 210,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 105, vit = 95, agi = 105, int = 95, mnd = 100, chr = 100),
        looks = listOf(npc(0x010E)),
        defaultSkills = listOf(mskillFootKick_1, mskillWhirlClaws_3, mskillWildCarrot_67, mskillSnowCloud_405),
    )

    val rabbitFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 210,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 105, vit = 95, agi = 105, int = 95, mnd = 100, chr = 100),
        looks = listOf(npc(0x10C), npc(0x791)),
        defaultSkills = listOf(mskillFootKick_1, mskillDustCloud_2, mskillWhirlClaws_3),
    )

    val tigerFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 105, maxMp = 0, str = 110, dex = 110, vit = 100, agi = 105, int = 90, mnd = 90, chr = 100),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
        looks = listOf(npc(0x134), npc(0x8C8)),
        defaultSkills = listOf(mskillRoar_14, mskillRazorFang_15, mskillClawCyclone_17),
        defaultTargetSize = 0.5f,
        defaultBonusApplier = { it.movementSpeed += 25 }
    )

    val opoOpoFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 110, vit = 100, agi = 100, int = 90, mnd = 105, chr = 100),
        looks = listOf(npc(0x01A0)),
        defaultSkills = listOf(mskillViciousClaw_32, mskillSpinningClaw_34, mskillClawStorm_35, mskillBlankGaze_36, mskillEyeScratch_38, mskillMagicFruit_39),
    )

    val buffaloFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 130, maxMp = 100, str = 100, dex = 90, vit = 110, agi = 90, int = 90, mnd = 110, chr = 100),
        looks = listOf(npc(0x054D)),
        defaultTargetSize = 1f,
        defaultSkills = listOf(mskillRampantGnaw_237, mskillBigHorn_238, mskillSnort_239, mskillRabidDance_240, mskillLowing_241),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val crabFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 90, dex = 100, vit = 110, agi = 90, int = 90, mnd = 105, chr = 100),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        looks = listOf(npc(0x0164), npc(0x0165)),
        defaultSkills = listOf(mskillBubbleShower_186, mskillBubbleCurtain_187, mskillBigScissors_188, mskillScissorGuard_189, mskillMetallicBody_192),
    )

    val craklawFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 125, maxMp = 100, str = 110, dex = 90, vit = 115, agi = 80, int = 80, mnd = 110, chr = 100),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        looks = listOf(npc(0x09FB)),
        defaultTargetSize = 1f,
        defaultSkills = listOf(mskillImpenetrableCarapace_2701, mskillRendingDeluge_2702, mskillSunderingSnip_2703, mskillViscidSpindrift_2704),
        autoAttackSkills = listOf(mskill_2698, mskill_2699, mskill_2700),
    )

    val eftFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0545), npc(0x0546)),
        defaultSkills = listOf(mskillToxicSpit_259, mskillGeistWall_260, mskillNumbingNoise_261, mskillNimbleSnap_262, mskillCyclotail_263),
    )

    val pugilFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 105, dex = 100, vit = 95, agi = 110, int = 105, mnd = 90, chr = 100),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        looks = listOf(npc(0x015C)),
        defaultSkills = listOf(mskillIntimidate_193, mskillAquaBall_194, mskillSplashBreath_195, mskillScrewdriver_196, mskillWaterWall_197, mskillWaterShield_198),
    )

    val jagilFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 105, dex = 100, vit = 95, agi = 110, int = 105, mnd = 90, chr = 100),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        looks = listOf(npc(0x015D)),
        defaultSkills = listOf(mskillIntimidate_193, mskillAquaBall_194, mskillSplashBreath_195, mskillWaterWall_197, mskillWaterShield_198, mskillRecoilDive_385),
    )

    val iceRaptorFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 210,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 110, vit = 90, agi = 110, int = 100, mnd = 90, chr = 100),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        looks = listOf(npc(0x013C)),
        defaultSkills = listOf(mskillRipperFang_118, mskillFrostBreath_121, mskillFrostBreath_121, mskillChompRush_123, mskillScytheTail_124),
        defaultBonusApplier = { it.movementSpeed += 25 }
    )

    val treantFamily = V0MonsterFamily(
        damageScale = 1.1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 120, maxMp = 0, str = 100, dex = 90, vit = 100, agi = 90, int = 110, mnd = 90, chr = 100),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        looks = listOf(npc(0x0184), npc(0x0186), npc(0x0187)),
        defaultSkills = listOf(mskillDrillBranch_72, mskillPineconeBomb_73, mskillLeafstorm_75, mskillEntangle_76),
        defaultTargetSize = 2f,
    )

    val saplingFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 100, vit = 105, agi = 95, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0188)),
        defaultSkills = listOf(mskillSproutSpin_429, mskillSlumberPowder_430, mskillSproutSmack_431),
    )

    val leechFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 110, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0114), npc(0x078E)),
        defaultSkills = listOf(mskillSuction_158, mskillAcidMist_159, mskillDrainkiss_161, mskillRegeneration_162, mskillMPDrainkiss_165),
    )

    val slugFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 140, maxMp = 100, str = 100, dex = 100, vit = 80, agi = 80, int = 120, mnd = 80, chr = 100),
        looks = listOf(npc(0x07E5)),
        defaultSkills = listOf(mskillFuscousOoze_1927, mskillPurulentOoze_1928, mskillCorrosiveOoze_1929),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val clotFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 115, agi = 85, int = 115, mnd = 85, chr = 100),
        looks = listOf(npc(0x0126)),
        defaultSkills = listOf(mskillFluidSpread_175, mskillDigest_177, mskillMucusSpread_1061, mskillEpoxySpread_1063),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        defaultBonusApplier = {
            it.physicalDamageTaken -= 50
            it.autoAttackEffects += AutoAttackEffect(effectPower = intPotency(it.actorState, 0.05f), effectType = AttackAddedEffectType.Drain)
        },
    )

    val uragniteFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 200, maxMp = 100, str = 85, dex = 85, vit = 100, agi = 85, int = 85, mnd = 85, chr = 100),
        looks = listOf(npc(0x0551)),
        defaultSkills = listOf(mskillVenomShell_1316, mskillPainfulWhip_1318, mskillSuctorialTentacle_1319),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        behaviorControllerFn = { FamilyUragniteController(it) },
    )

    val seaMonkFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 150,
        statScaling = CombatStats(maxHp = 120, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 100, int = 90, mnd = 90, chr = 100),
        looks = listOf(npc(0x0160)),
        defaultSkills = listOf(mskillTentacle_200, mskillInkJet_202, mskillHardMembrane_203, mskillCrossAttack_204, mskillRegeneration_205, mskillMaelstrom_206, mskillWhirlwind_207),
        defaultTargetSize = 1f,
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        defaultBonusApplier = { it.autoAttackScale = 0.5f }
    )

    val clusterFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 150, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 100, int = 110, mnd = 90, chr = 100),
        looks = listOf(npc(0x0123)),
        defaultSkills = emptyList(),
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSightAggro),
        behaviorControllerFn = { FamilyClusterController(it) },
    )

    val snollFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 120, maxMp = 100, str = 90, dex = 100, vit = 100, agi = 100, int = 110, mnd = 100, chr = 100),
        looks = listOf(npc(0x0116)),
        defaultSkills = listOf(mskillBerserk_270, mskillFreezeRush_271, mskillColdWave_272, mskillHypothermalCombustion_273),
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSightAggro),
    )

    val snollTzarFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 120, maxMp = 100, str = 90, dex = 100, vit = 100, agi = 100, int = 110, mnd = 100, chr = 100),
        looks = listOf(npc(0x011B)),
        defaultSkills = emptyList(),
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSightAggro),
    )

    val iceElementalFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 75, maxMp = 200, str = 100, dex = 100, vit = 100, agi = 75, int = 125, mnd = 100, chr = 100),
        looks = listOf(npc(0x01B5)),
        aggroConfig = MonsterAggroConfig.standardMagicAggro,
        defaultBonusApplier = {
            it.physicalDamageTaken -= 75
        },
    )

    val pixieFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 200, str = 90, dex = 100, vit = 90, agi = 100, int = 120, mnd = 120, chr = 100),
        looks = listOf(npc(0x7EE), npc(0x7EF), npc(0x7F0), npc(0x810)),
        defaultSkills = listOf(mskillZephyrArrow_1937, mskillLetheArrows_1938, mskillSpringBreeze_1939, mskillSummerBreeze_1940, mskillAutumnBreeze_1941, mskillWinterBreeze_1942, mskillCyclonicTurmoil_1943),
    )

    val warMachineFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 150, maxMp = 0, str = 100, dex = 90, vit = 100, agi = 90, int = 100, mnd = 90, chr = 100),
        looks = listOf(npc(0x01AC)),
        defaultSkills = emptyList(),
        defaultTargetSize = 1f,
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val gigasStandardFamily = V0MonsterFamily(
        damageScale = 1.1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 120, maxMp = 0, str = 110, dex = 90, vit = 100, agi = 90, int = 90, mnd = 90, chr = 100),
        looks = listOf(npc(0x02C4)),
        defaultSkills = listOf(mskillImpactRoar_408, mskillGrandSlam_409, mskillPowerAttack_411),
        defaultTargetSize = 0.5f,
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val gigasIceFamily = V0MonsterFamily(
        damageScale = 1.1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 110, dex = 90, vit = 100, agi = 90, int = 110, mnd = 90, chr = 100),
        looks = listOf(npc(0x0283)),
        defaultSkills = listOf(mskillIceRoar_407, mskillImpactRoar_408, mskillGrandSlam_409, mskillPowerAttack_410),
        defaultTargetSize = 0.5f,
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val gigasLightningFamily = V0MonsterFamily(
        damageScale = 1.1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 110, dex = 110, vit = 100, agi = 90, int = 90, mnd = 90, chr = 100),
        looks = listOf(npc(0x0282)),
        defaultSkills = listOf(mskillCatapult_402, mskillLightningRoar_406, mskillImpactRoar_408, mskillGrandSlam_409, mskillPowerAttack_410),
        defaultTargetSize = 0.5f,
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val wandererFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 210,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 105, vit = 95, agi = 105, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0452)),
        defaultSkills = listOf(mskillVanityDive_132, mskillEmptyBeleaguer_133, mskillMirage_134, mskillAuraofPersistence_135),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        behaviorControllerFn = { FamilyWandererController(it) },
    )

    val weeperFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 95, vit = 100, agi = 95, int = 105, mnd = 105, chr = 100),
        looks = listOf(npc(0x0458)),
        defaultSkills = listOf(mskillEmptyCutter_961, mskillVacuousOsculation_962, mskillHexagonBelt_963, mskillAuroralDrape_964),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        behaviorControllerFn = { FamilyWeeperController(it) },
    )

    val seetherFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 105, dex = 100, vit = 105, agi = 100, int = 95, mnd = 95, chr = 100),
        looks = listOf(npc(0x045D)),
        defaultSkills = listOf(mskillVanityStrike_997, mskillOccultation_999, mskillLamentation_1002),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        behaviorControllerFn = { FamilySeetherController(it) },
    )

    val thinkerFamily = V0MonsterFamily(
        damageScale = 1.1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0463)),
        defaultSkills = listOf(mskillEmptyCutter_986, mskillNegativeWhirl_987, mskillStygianVapor_988, mskillSpiritAbsorption_990, mskillSpiritTap_993),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        defaultTargetSize = 1f,
        behaviorControllerFn = { FamilyThinkerController(it) },
    )

    val gorgerFamily = V0MonsterFamily(
        damageScale = 1.05f,
        delay = 270,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0469)),
        defaultSkills = listOf(mskillQuadraticContinuum_485, mskillSpiritAbsorption_488, mskillVanityDrive_491, mskillStygianFlatus_494, mskillPromyvionBarrier_496),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        defaultTargetSize = 1f,
        behaviorControllerFn = { FamilyGorgerController(it) },
    )

    val craverFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 105, dex = 105, vit = 95, agi = 105, int = 95, mnd = 95, chr = 100),
        looks = listOf(npc(0x0469)),
        defaultSkills = listOf(mskillBrainSpike_973, mskillEmptyThrash_974, mskillPromyvionBrume_975, mskillMurk_976, mskillMaterialFend_977),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        defaultTargetSize = 1f,
        behaviorControllerFn = { FamilyCraverController(it) },
    )

    val receptacleFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 300,
        looks = listOf(npc(0x044E), npc(0x044F), npc(0x0450), npc(0x0451)),
        defaultSkills = emptyList(),
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
    )

    val bztavianFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x9C4)),
        defaultTargetSize = 1f,
        defaultSkills = emptyList(),
        autoAttackSkills = listOf(mskill_2743, mskill_2744, mskill_2745),
    )

    val rockfinFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x09C9)),
        defaultTargetSize = 1f,
        defaultSkills = emptyList(),
        autoAttackSkills = listOf(mskill_2752, mskill_2753, mskill_2754),
    )

    val selhteusTrust = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0C16)),
        defaultSkills = listOf(mskillLuminousLance_3365, mskillRevelation_3367),
    )

    val funguarFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyFunguarBehavior(it) },
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0178), npc(0x08C9)),
        defaultSkills = listOf(mskillFrogkick_52, mskillSpore_53, mskillSilenceGas_58, mskillDarkSpore_59),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val behemothFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 120, maxMp = 200, str = 110, dex = 100, vit = 100, agi = 95, int = 110, mnd = 95, chr = 100),
        looks = listOf(npc(0x194), npc(0x195), npc(0xB32)),
        defaultSkills = listOf(mskillWildHorn_372, mskillThunderbolt_373, mskillKickOut_374, mskillShockWave_375, mskillFlameArmor_376, mskillHowl_377),
        defaultTargetSize = 3f,
        aggroConfig = MonsterAggroConfig.standardSightAggro,
        facesTarget = false,
        defaultBonusApplier = { it.knockBackResistance += 100 }
    )

    val yovraFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyYovraController(it) },
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 120, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 115, mnd = 100, chr = 120),
        looks = listOf(npc(0x047C)),
        defaultSkills = listOf(mskillVitriolicBarrage_1114, mskillPrimalDrill_1115, mskillConcussiveOscillation_1116, mskillIonShower_1117, mskillTorrentialTorment_1118, mskillAsthenicFog_1119, mskillLuminousDrape_1120, mskillFluorescence_1121),
        defaultTargetSize = 3f,
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        facesTarget = false,
        defaultBonusApplier = { it.knockBackResistance += 100 }
    )

    val wamouraFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 110, maxMp = 100, str = 110, dex = 100, vit = 100, agi = 100, int = 100, mnd = 110, chr = 100),
        defaultSkills = listOf(mskillMagmaFan_1695, mskillErraticFlutter_1696, mskillProboscis_1697, mskillErosionDust_1698, mskillExuviation_1699),
        looks = listOf(npc(0x0710), npc(0x0711)),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val wamouracampaFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyWamouracampaBehavior(it) },
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 110, agi = 90, int = 100, mnd = 110, chr = 100),
        defaultSkills = listOf(mskillAmberScutum_1559, mskillHeatBarrier_1563),
        looks = listOf(npc(0x070A)),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val apkalluFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 210,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 100, vit = 95, agi = 105, int = 100, mnd = 100, chr = 100),
        defaultSkills = listOf(mskillYawn_1457, mskillWingSlap_1458, mskillBeakLunge_1459, mskillFrigidShuffle_1460, mskillWingWhirl_1461),
        looks = listOf(npc(0x06BB)),
        defaultAppearanceState = 1,
    )

    val flanFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyFlanBehavior(it) },
        damageScale = 1.1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 200, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 90, chr = 100),
        looks = listOf(npc(0x0707)),
        defaultSkills = listOf(mskillAmplification_1565, mskillBoilingPoint_1566, mskillXenoglossia_1567),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val trollWarriorFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyTrollHeavyBehavior(it) },
        damageScale = 1.2f,
        delay = 330,
        statScaling = CombatStats(maxHp = 110, maxMp = 0, str = 110, dex = 100, vit = 100, agi = 100, int = 90, mnd = 90, chr = 100),
        looks = listOf(npcWithBase(0x0696)),
        defaultSkills = listOf(mskillDiamondhide_1641, mskillEnervation_1642, mskillQuakeStomp_1643),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val trollPaladinFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyTrollHeavyBehavior(it) },
        damageScale = 1f,
        delay = 270,
        statScaling = CombatStats(maxHp = 110, maxMp = 100, str = 100, dex = 100, vit = 110, agi = 100, int = 80, mnd = 110, chr = 100),
        looks = listOf(npcWithBase(0x069B)),
        defaultSkills = listOf(mskillDiamondhide_1641, mskillEnervation_1642, mskillQuakeStomp_1643),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val trollRangerFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyTrollLightBehavior(it) },
        damageScale = 0.9f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 105, vit = 95, agi = 120, int = 95, mnd = 100, chr = 100),
        looks = listOf(npcWithBase(0x06AB)),
        defaultSkills = listOf(mskillDiamondhide_1488, mskillEnervation_1489, mskillQuakeStomp_1490, mskillZarraqa_1491, mskillZarbzan_1492),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val bombFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 110, maxMp = 100, str = 100, dex = 100, vit = 95, agi = 100, int = 100, mnd = 95, chr = 100),
        looks = listOf(npc(0x0118)),
        defaultSkills = listOf(mskillVulcanianImpact_86, mskillBerserk_254, mskillSelfDestruct_255, mskillHeatWave_256),
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSightAggro),
    )

    val crawlerErucaFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 110, maxMp = 0, str = 100, dex = 100, vit = 95, agi = 100, int = 105, mnd = 95, chr = 100),
        looks = listOf(npc(0x0196)),
        defaultSkills = listOf(mskillStickyThread_88, mskillCocoon_90, mskillIncinerate_1535),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val cerberusFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 115, maxMp = 200, str = 110, dex = 100, vit = 95, agi = 100, int = 110, mnd = 95, chr = 100),
        defaultTargetSize = 3f,
        looks = listOf(npc(0x0701), npc(0x0702)),
        defaultSkills = listOf(mskillLavaSpit_1529, mskillSulfurousBreath_1530, mskillScorchingLash_1531, mskillUlulation_1532, mskillMagmaHoplon_1533, mskillGatesofHades_1534),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
        facesTarget = false,
    )

    val objectFetter = V0MonsterFamily(
        damageScale = 1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x085B), npc(0x085C), npc(0x085D), npc(0x085E), npc(0x085F), npc(0x0860), npc(0x0861), npc(0x0862)),
        facesTarget = false,
    )

    val weaponRdmFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 200, str = 100, dex = 100, vit = 95, agi = 95, int = 110, mnd = 105, chr = 100),
        looks = listOf(npc(0x01C3)),
        defaultSkills = listOf(mskillSmiteofRage_257, mskillWhirlofRage_258),
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSoundAggro),
    )

    val magicPotFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 95, dex = 95, vit = 105, agi = 95, int = 110, mnd = 100, chr = 100),
        looks = listOf(npc(0x019C)),
        defaultSkills = listOf(mskillDoubleRay_264, mskillSpinningAttack_265, mskillSpectralBarrier_266, mskillMysteriousLight_267, mskillMindDrain_268, mskillBatteryCharge_269),
        aggroConfig = MonsterAggroConfig.standardMagicAggro,
    )

    val goblinThiefFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 210,
        statScaling = CombatStats(maxHp = 95, maxMp = 0, str = 95, dex = 110, vit = 95, agi = 110, int = 95, mnd = 95, chr = 100),
        looks = listOf(npc(0x01E4), npc(0x0931)),
        defaultSkills = listOf(mskillGoblinRush_334, mskillBombToss_335, mskillFrypan_825, mskillSmokebomb_826, mskillCrispyCandle_828, mskillParalysisShower_830),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val yagudoWhiteMageFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 240,
        statScaling = CombatStats(maxHp = 95, maxMp = 150, str = 95, dex = 100, vit = 100, agi = 100, int = 100, mnd = 115, chr = 100),
        looks = listOf(npc(0x0247), npc(0x0934)),
        defaultSkills = listOf(mskillFeatherStorm_361, mskillDoubleKick_362, mskillParry_363, mskillSweep_364, mskillHowl_508),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val quadavRedMageFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 240,
        statScaling = CombatStats(maxHp = 90, maxMp = 150, str = 95, dex = 100, vit = 105, agi = 100, int = 105, mnd = 105, chr = 100),
        looks = listOf(npc(0x028B), npc(0x0933)),
        defaultSkills = listOf(mskillOreToss_355, mskillHeadButt_356, mskillShellBash_357, mskillShellGuard_358, mskillHowl_506),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val orcWarriorFamily = V0MonsterFamily(
        damageScale = 1.1f,
        delay = 270,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 110, dex = 100, vit = 105, agi = 100, int = 90, mnd = 100, chr = 100),
        looks = listOf(npc(0x0266), npc(0x932), npc(0x7F6)),
        defaultSkills = listOf(mskillAerialWheel_349, mskillShoulderAttack_350, mskillSlamDunk_351, mskillArmBlock_352, mskillBattleDance_353, mskillHowl_510, mskillOrcishCounterstance_1945),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val dollFamily = V0MonsterFamily(
        damageScale = 1.15f,
        delay = 300,
        statScaling = CombatStats(maxHp = 105, maxMp = 100, str = 105, dex = 100, vit = 100, agi = 95, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0130)),
        defaultSkills = listOf(mskillKartstrahl_278, mskillBlitzstrahl_279, mskillPanzerfaust_280, mskillBerserk_281, mskillPanzerschreck_282, mskillTyphoon_283, mskillGravityField_285, mskillMeltdown_287),
        aggroConfig = MonsterAggroConfig.standardMagicAggro,
    )

    val seedCrystalFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x092F)),
        defaultSkills = listOf(mskillSeedofDeception_2160, mskillSeedofDeference_2161, mskillSeedofNihility_2162, mskillSeedofJudgment_2163),
        autoAttackSkills = listOf(mskillSeedAutoAttack_2159),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val seedThrallFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0000)),
    )

    val belladonnaFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 120, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 95, int = 100, mnd = 100, chr = 120),
        looks = listOf(npc(0x082B), npc(0x0B73)),
        autoAttackSkills = listOf(mskillBelladonnaAutoAttack_2621, mskillBelladonnaAutoAttack_2622, mskillBelladonnaAutoAttack_2623),
        defaultSkills = listOf(mskillNightStalker_2624, mskillAtropineSpore_2625, mskillDeracinator_2628),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val rafflesiaFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 105, dex = 100, vit = 100, agi = 95, int = 105, mnd = 95, chr = 100),
        defaultSkills = listOf(mskillSeedspray_1907, mskillViscidEmission_1908, mskillRottenStench_1909, mskillFloralBouquet_1910, mskillBloodyCaress_1911),
        looks = listOf(npc(0x07E8), npc(0x07EA)),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val snapweedFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        defaultSkills = listOf(mskillTicklingTendrils_2841, mskillStinkBomb_2842, mskillNectarousDeluge_2843, mskillNepenthicPlunge_2844, mskillInfaunalFlop_2845),
        looks = listOf(npc(0x09F6), npc(0x09F7), npc(0x09F8)),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val yggdreantFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 330,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        defaultSkills = listOf(mskillRootoftheProblem_2801, mskillPottedPlant_2802, mskillCanopierce_2804, mskillFireflyFandango_2805),
        autoAttackSkills = listOf(mskillYggdreantAutoAttack_2798, mskillYggdreantAutoAttack_2799, mskillYggdreantAutoAttack_2800),
        looks = listOf(npc(0x9CE), npc(0x09CF)),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val acuexFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyAcuexBehavior(it) },
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 90, vit = 110, agi = 90, int = 100, mnd = 110, chr = 100),
        defaultSkills = listOf(mskillFoulWaters_2718, mskillPestilentPlume_2719, mskillDeadeningHaze_2720),
        looks = listOf(npc(0xA0A), npc(0xA0B), npc(0xA0C)),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        defaultBonusApplier = {
            it.physicalDamageTaken -= 25
            it.magicalDamageTaken += 25
        }
    )

    val umbrilFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyUmbrilBehavior(it) },
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 115, dex = 100, vit = 90, agi = 100, int = 115, mnd = 90, chr = 100),
        defaultSkills = listOf(mskillParalyzingTriad_2714, mskillCrepuscularGrasp_2715, mskillNecroticBrume_2716, mskillTerminalBloom_2717),
        looks = listOf(npc(0xA05), npc(0xA06), npc(0xA07)),
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSoundAggro),
        defaultAppearanceState = 1,
        defaultBonusApplier = {
            it.physicalDamageTaken -= 12
            it.magicalDamageTaken += 12
        }
    )

    val marolithFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyMarolithBehavior(it) },
        movementControllerFn = { FamilyMarolithMovementBehavior() },
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 120, maxMp = 100, str = 110, dex = 95, vit = 110, agi = 85, int = 95, mnd = 95, chr = 100),
        defaultSkills = listOf(mskillMetamorphicBlast_2671, mskillEnervatingGrasp_2672 ,mskillOrogenicStorm_2673, mskillSubduction_2674),
        looks = listOf(npc(0x793), npc(0x794)),
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSoundAggro),
        defaultAppearanceState = 1,
    )

    val spiderFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 90, maxMp = 0, str = 110, dex = 105, vit = 100, agi = 100, int = 100, mnd = 95, chr = 100),
        defaultSkills = listOf(mskillSickleSlash_554, mskillAcidSpray_555, mskillSpiderWeb_556),
        looks = listOf(npc(0x136), npc(0x138), npc(0x139)),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        defaultAppearanceState = 1,
    )

    val fungusFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyObstacleBehavior(it, mskillFungusSpore_2772) },
        movementControllerFn = { NoOpActorController() },
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0xAD7)),
        targetable = false,
    )

    val blossomFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyObstacleBehavior(it, mskillBlossomSpore_2771) },
        movementControllerFn = { NoOpActorController() },
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0xAD8)),
        targetable = false,
    )

    val panoptFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyPanoptBehavior(it) },
        movementControllerFn = { FamilyPanoptMovementBehavior() },
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 90, maxMp = 200, str = 90, dex = 100, vit = 90, agi = 110, int = 120, mnd = 100, chr = 100),
        defaultSkills = listOf(mskillRetinalGlare_2774, mskillSylvanSlumber_2775, mskillCrushingGaze_2776, mskillVaskania_2777),
        looks = listOf(npc(0x205)),
        defaultAppearanceState = 2,
    )

    val wormFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyWormBehavior(it) },
        movementControllerFn = { FamilyWormMovementController() },
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 110, maxMp = 200, str = 100, dex = 100, vit = 85, agi = 85, int = 115, mnd = 100, chr = 100),
        defaultSkills = listOf(mskillFullforceBlow_168, mskillGastricBomb_169, mskillSandspin_170, mskillTremors_171, mskillMPAbsorption_172, mskillSoundVacuum_173),
        looks = listOf(npc(0x1A9), npc(0x1AA)),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        defaultBonusApplier = {
            it.knockBackResistance = 100
        }
    )

    val shadowLordSFamily = V0MonsterFamily(
        damageScale = 1.3f,
        delay = 330,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        defaultSkills = emptyList(),
        looks = listOf(npc(0x87B)),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val quadavPaladinFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 270,
        statScaling = CombatStats(maxHp = 100, maxMp = 150, str = 90, dex = 100, vit = 120, agi = 90, int = 90, mnd = 110, chr = 100),
        looks = listOf(npc(0x028B), npc(0x850)),
        defaultSkills = listOf(mskillDiamondShell_1947, mskillHowl_506),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val quadavWhiteMageFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 270,
        statScaling = CombatStats(maxHp = 100, maxMp = 150, str = 90, dex = 100, vit = 100, agi = 90, int = 100, mnd = 125, chr = 100),
        looks = listOf(npc(0x028B), npc(0x29F)),
        defaultSkills = listOf(mskillOreToss_355, mskillHeadButt_356, mskillShellBash_357, mskillShellGuard_358, mskillHowl_506),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val yagudoSamuraiFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 110, dex = 105, vit = 100, agi = 105, int = 95, mnd = 100, chr = 100),
        looks = listOf(npc(0x247), npc(0x81E)),
        defaultSkills = listOf(mskillFeatherStorm_361, mskillDoubleKick_362, mskillParry_363, mskillSweep_364, mskillHowl_508),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val yagudoBardFamily = V0MonsterFamily(
        damageScale = 0.7f,
        delay = 180,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 95, dex = 105, vit = 100, agi = 105, int = 95, mnd = 100, chr = 130),
        looks = listOf(npc(0x252), npc(0x253)),
        defaultSkills = listOf(mskillFeatherStorm_361, mskillDoubleKick_362, mskillParry_363, mskillSweep_364, mskillHowl_508),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val orcBlackmageFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 150, str = 90, dex = 100, vit = 100, agi = 100, int = 110, mnd = 90, chr = 100),
        looks = listOf(npc(0x27D), npc(0x419)),
        defaultSkills = listOf(mskillAerialWheel_349, mskillShoulderAttack_350, mskillSlamDunk_351, mskillArmBlock_352, mskillBattleDance_353, mskillHowl_510, mskillOrcishCounterstance_1945),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val gnoleFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyGnoleBehavior(it) },
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 120, maxMp = 0, str = 110, dex = 105, vit = 95, agi = 105, int = 95, mnd = 95, chr = 100),
        looks = listOf(npc(0x7F1)),
        defaultSkills = listOf(mskillFeveredPitch_1914, mskillCalloftheMoon_1915, mskillCalloftheMoon_1916, mskillPleniluneEmbrace_1917, mskillPleniluneEmbrace_1918, mskillNoxBlast_1919, mskillAsuranClaws_1920, mskillCacophony_1921),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val corpselightFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 85, maxMp = 200, str = 95, dex = 100, vit = 90, agi = 110, int = 120, mnd = 100, chr = 100),
        looks = listOf(npc(0x731)),
        defaultSkills = listOf(mskillCorpseBreath_2255, mskillLouringSkies_2569),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val ghostFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 200, str = 100, dex = 100, vit = 100, agi = 100, int = 110, mnd = 95, chr = 100),
        looks = listOf(npc(0x170), npc(0x171)),
        defaultSkills = listOf(mskillGraveReel_216, mskillEctosmash_217, mskillFearTouch_218, mskillTerrorTouch_219, mskillCurse_220, mskillDarkSphere_221, mskillPerdition_1538),
        defaultBonusApplier = { it.physicalDamageTaken -= 25 },
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val demonWarriorFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 110, dex = 100, vit = 105, agi = 100, int = 100, mnd = 95, chr = 100),
        looks = listOf(npc(0x2EB), npc(0x8AC)),
        defaultSkills = listOf(mskillSoulDrain_303, mskillHecatombWave_304, mskillDemonicHowl_307, mskillCondemnation_892, mskillQuadrastrike_893, mskillHellbornYawp_2116),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val demonDarkKnightFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 110, dex = 100, vit = 100, agi = 100, int = 110, mnd = 95, chr = 100),
        looks = listOf(npc(0x2F0), npc(0x8AD)),
        defaultSkills = listOf(mskillSoulDrain_303, mskillHecatombWave_304, mskillDemonicHowl_307, mskillCondemnation_892, mskillQuadrastrike_893, mskillHellbornYawp_2116),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val vampyrFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 150,
        statScaling = CombatStats(maxHp = 120, maxMp = 200, str = 110, dex = 100, vit = 100, agi = 90, int = 110, mnd = 95, chr = 120),
        looks = listOf(npc(0x735), npc(0x736)),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
        defaultSkills = listOf(mskillBloodrake_1850, mskillDecollation_1851, mskillNosferatusKiss_1852, mskillHeliovoid_1853, mskillWingsofGehenna_1854, mskillEternalDamnation_1855, mskillMinaxGlare_2278),
        defaultBonusApplier = {
            it.autoAttackScale = 0.5f
        }
    )

    val taurusFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 150,
        statScaling = CombatStats(maxHp = 120, maxMp = 0, str = 110, dex = 100, vit = 110, agi = 100, int = 95, mnd = 95, chr = 100),
        looks = listOf(npc(0x54F), npc(0x550), npc(0x956)),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
        defaultSkills = listOf(mskillTriclip_242, mskillBackSwish_243, mskillMow_244, mskillFrightfulRoar_245, mskillMortalRay_246, mskillUnblestArmor_247),
        defaultBonusApplier = {
            it.autoAttackScale = 0.5f
        }
    )

    val gargouilleFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyGargouilleBehavior(it) },
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 110, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x8B1)),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
        defaultSkills = listOf(mskillDarkOrb_2165, mskillDarkMist_2166, mskillTriumphantRoar_2167, mskillTerrorEye_2168, mskillBloodyClaw_2169, mskillShadowBurst_2170),
    )

    val ahrimanFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyAhrimanBehavior(it) },
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 200, str = 90, dex = 100, vit = 100, agi = 110, int = 115, mnd = 90, chr = 100),
        looks = listOf(npc(0x108), npc(0x109)),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
        defaultSkills = listOf(mskillBlindeye_292, mskillEyesOnMe_293, mskillHypnosis_294, mskillBindingWave_296, mskillAiryShield_297, mskillMagicBarrier_299, mskillLevel5Petrify_301, mskillDeathlyGlare_2512),
    )

    val amphiptereFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyAmphiptereBehavior(it) },
        movementControllerFn = { FamilyAmphiptereMovementBehavior() },
        facesTarget = false,
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 130, maxMp = 100, str = 110, dex = 100, vit = 100, agi = 95, int = 110, mnd = 95, chr = 100),
        looks = listOf(npc(0x8AE), npc(0x957)),
        defaultTargetSize = 5f,
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.extendedSightAggro, MonsterAggroConfig.extendedSoundAggro),
        defaultSkills = listOf(mskillTailLash_2171, mskillBloodyBeak_2172, mskillFeralPeck_2173, mskillWarpedWail_2174, mskillReavingWind_2175, mskillStormWing_2176, mskillCalamitousWind_2177),
    )

    val crawlerFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 110, maxMp = 0, str = 100, dex = 100, vit = 95, agi = 100, int = 95, mnd = 100, chr = 100),
        looks = listOf(npc(0x18C)),
        defaultSkills = listOf(mskillStickyThread_88, mskillPoisonBreath_89, mskillCocoon_90),
    )

    val hecteyesFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 90, maxMp = 200, str = 90, dex = 100, vit = 100, agi = 85, int = 120, mnd = 100, chr = 100),
        looks = listOf(npc(0x180), npc(0x181)),
        defaultSkills = listOf(mskillDeathRay_181, mskillHexEye_182, mskillPetroGaze_183, mskillCatharsis_184),
        defaultBonusApplier = { it.physicalDamageTaken -= 25 }
    )

    val harpeiaFamily = V0MonsterFamily(
        damageScale = 1.15f,
        delay = 270,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 105, dex = 100, vit = 95, agi = 115, int = 105, mnd = 95, chr = 100),
        looks = listOf(npc(0x813), npc(0x83A), npc(0x83E)),
        defaultSkills = listOf(mskillRendingTalons_2469, mskillShriekingGale_2470, mskillWingsofWoe_2471, mskillWingsofAgony_2472, mskillTyphoeanRage_2473, mskillRavenousWail_2474, mskillKaleidoscopicFury_2502, mskillKeraunosQuill_2555),
        autoAttackSkills = listOf(mskillHarpeiaAutoAttack_2466, mskillHarpeiaAutoAttack_2467, mskillHarpeiaAutoAttack_2468),
        defaultTargetSize = 1f,
    )

    val beetleFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 270,
        statScaling = CombatStats(maxHp = 115, maxMp = 100, str = 95, dex = 95, vit = 115, agi = 95, int = 95, mnd = 110, chr = 100),
        looks = listOf(npc(0x198), npc(0x199), npc(0x19A)),
        defaultSkills = listOf(mskillPowerAttack_82, mskillHiFreqField_83, mskillRhinoAttack_84, mskillRhinoGuard_85),
    )

    val coeurlFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 95, maxMp = 0, str = 95, dex = 110, vit = 95, agi = 110, int = 110, mnd = 95, chr = 100),
        looks = listOf(npc(0x16F), npc(0x8C6), npc(0x83C)),
        defaultSkills = listOf(mskillPetrifactiveBreath_224, mskillFrenziedRage_225, mskillPounce_226, mskillChargedWhisker_227, mskillBlaster_396, mskillChaoticEye_397),
        defaultBonusApplier = { it.movementSpeed += 25 }
    )

    val golemFamily = V0MonsterFamily(
        damageScale = 1.15f,
        delay = 300,
        statScaling = CombatStats(maxHp = 110, maxMp = 0, str = 110, dex = 90, vit = 110, agi = 90, int = 100, mnd = 95, chr = 100),
        looks = listOf(npc(0x1B0), npc(0x1B1)),
        defaultSkills = listOf(mskillCrystalShield_418, mskillHeavyStrike_419, mskillIceBreak_420, mskillThunderBreak_421, mskillCrystalRain_422, mskillCrystalWeapon_423, mskillCrystalWeapon_424, mskillCrystalWeapon_425, mskillCrystalWeapon_426),
        defaultTargetSize = 1.5f,
    )

    val wyvernFamily = V0MonsterFamily(
        damageScale = 1.1f,
        delay = 270,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 105, dex = 105, vit = 100, agi = 105, int = 105, mnd = 95, chr = 100),
        looks = listOf(npc(0x18E), npc(0x18F), npc(0x78F)),
        defaultSkills = listOf(mskillDispellingWind_557, mskillDeadlyDrive_558, mskillWindWall_559, mskillFangRush_560, mskillDreadShriek_561, mskillTailCrush_562, mskillBlizzardBreath_563, mskillThunderBreath_564, mskillRadiantBreath_565, mskillChaosBreath_566, mskillHurricaneBreath_1966),
        defaultTargetSize = 1f,
    )

    val wivreFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 120, maxMp = 0, str = 115, dex = 95, vit = 110, agi = 90, int = 90, mnd = 90, chr = 100),
        looks = listOf(npc(0x6FB), npc(0x6FC)),
        defaultSkills = listOf(mskillBatterhorn_1843, mskillClobber_1844, mskillDemoralizingRoar_1845,mskillBoilingBlood_1846, mskillGraniteSkin_1847, mskillCripplingSlam_1848),
        defaultTargetSize = 2f,
        facesTarget = false,
        defaultBonusApplier = { it.knockBackResistance += 100 },
    )

    val morbolFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 120, maxMp = 0, str = 105, dex = 105, vit = 100, agi = 95, int = 95, mnd = 95, chr = 100),
        looks = listOf(npc(0x17C), npc(0x17A), npc(0xB6B)),
        defaultSkills = listOf(mskillImpale_60, mskillVampiricLash_61, mskillBadBreath_63, mskillSweetBreath_64),
        defaultTargetSize = 1f,
    )

    val diremiteFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 210,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 95, dex = 105, vit = 95, agi = 105, int = 105, mnd = 95, chr = 100),
        looks = listOf(npc(0x549), npc(0xB6C)),
        defaultSkills = listOf(mskillDoubleClaw_106, mskillGrapple_107, mskillFilamentedHold_108, mskillSpinningTop_109),
    )

    val tulfaireFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 210,
        statScaling = CombatStats(maxHp = 110, maxMp = 0, str = 110, dex = 105, vit = 100, agi = 110, int = 95, mnd = 95, chr = 100),
        looks = listOf(npc(0xA23), npc(0xA24)),
        defaultSkills = listOf(mskillMoltingPlumage_2807, mskillPentapeck_2808, mskillSwoopingFrenzy_2809, mskillFromtheSkies_2810),
    )

    val giantGnatFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 200, str = 95, dex = 100, vit = 100, agi = 100, int = 115, mnd = 95, chr = 100),
        looks = listOf(npc(0x204), npc(0xB72)),
        autoAttackSkills = listOf(mskillGiantGnatAutoAttack_2778, mskillGiantGnatAutoAttack_2779, mskillGiantGnatAutoAttack_2780),
        defaultSkills = listOf(mskillFleshSyphon_2781, mskillUmbralExpunction_2782, mskillStickySituation_2783, mskillAbdominalAssault_2784, mskillMandibularMassacre_2785),
    )

    val orobonFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 120, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 120),
        looks = listOf(npc(0x6C1), npc(0x6C2)),
        defaultSkills = listOf(mskillGnash_1437, mskillVileBelch_1438, mskillHypnicLamp_1439, mskillSeismicTail_1440, mskillSeaspray_1441, mskillLeechingCurrent_1442),
        facesTarget = false,
        defaultTargetSize = 1f,
    )

    val redWyrmFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 120, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x260)),
        defaultSkills = listOf(mskillTebbadWing_1023, mskillSpikeFlail_1024, mskillFieryBreath_1025, mskillInfernoBlast_1027, mskillTebbadWing_1028, mskillAbsoluteTerror_1029, mskillHorridRoar_1030),
        facesTarget = false,
        defaultTargetSize = 4f,
    )

    val redDragonFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 120, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x1A5)),
        defaultSkills = listOf(mskillFlameBreath_386, mskillBodySlam_389, mskillPetroEyes_392, mskillThornsong_394),
        facesTarget = false,
        defaultTargetSize = 2f,
    )

    val ladybugFamily = V0MonsterFamily(
        damageScale = 0.9f,
        delay = 210,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 95, dex = 110, vit = 95, agi = 110, int = 95, mnd = 95, chr = 100),
        looks = listOf(npc(0x7E2), npc(0x7E3)),
        defaultSkills = listOf(mskillSuddenLunge_1922, mskillNoisomePowder_1923, mskillNepentheanHum_1924, mskillSpiralSpin_1925),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val chapuliFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 95, maxMp = 0, str = 105, dex = 105, vit = 95, agi = 110, int = 95, mnd = 95, chr = 100),
        looks = listOf(npc(0x9F1), npc(0x9F2), npc(0x9F3)),
        defaultSkills = listOf(mskillNaturesMeditation_2689, mskillSensillaBlades_2690, mskillTegminaBuffet_2691, mskillSanguinarySlash_2692),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val mineExplosiveFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0B63)),
        defaultSkills = listOf(mskillMineBlast_1582),
        movementControllerFn = { NoOpActorController() },
        facesTarget = false,
    )

    val carbuncleFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 180,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0317)),
        defaultSkills = listOf(mskillPoisonNails_651, mskillShiningRuby_652, mskillGlitteringRuby_653, mskillMeteorite_654, mskillHealingRubyII_655, mskillSearingLight_656, mskillHolyMist_3297)
    )

    val fenrirFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 110, str = 100, dex = 100, vit = 110, agi = 100, int = 110, mnd = 110, chr = 100),
        looks = listOf(npc(0x0318)),
        defaultSkills = listOf(mskillMoonlitCharge_575, mskillCrescentFang_576, mskillLunarCry_577, mskillEclipticGrowl_578, mskillLunarRoar_579, mskillEclipseBite_580, mskillEclipticHowl_581, mskillHowlingMoon_582, mskillLunarBay_3295, mskillImpact_3296),
        defaultTargetSize = 1.5f,
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSightAggro),
    )

    val ifritFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 120, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x0319)),
        defaultSkills = listOf(mskillPunch_584, mskillBurningStrike_586, mskillDoublePunch_587, mskillCrimsonRoar_588, mskillFlamingCrush_590, mskillMeteorStrike_591, mskillInferno_592),
        defaultTargetSize = 1f,
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSightAggro),
    )

    val titanFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 120, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x031A)),
        defaultSkills = listOf(mskillRockThrow_593, mskillRockBuster_595, mskillMegalithThrow_596, mskillEarthenWard_597, mskillMountainBuster_599, mskillGeocrush_600, mskillEarthenFury_601),
        defaultTargetSize = 1f,
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSightAggro),
    )

    val leviathanFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 120, chr = 100),
        looks = listOf(npc(0x031B)),
        defaultSkills = listOf(mskillBarracudaDive_602, mskillTailWhip_604, mskillSpringWater_605, mskillSpinningDive_608, mskillGrandFall_609, mskillTidalWave_610),
        defaultTargetSize = 1f,
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSightAggro),
    )

    val garudaFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 120, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x031C)),
        defaultSkills = listOf(mskillClaw_611, mskillWhisperingWind_613, mskillAerialArmor_615, mskillPredatorClaws_617, mskillWindBlade_618, mskillAerialBlast_619),
        defaultTargetSize = 1f,
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSightAggro),
    )

    val shivaFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 120, mnd = 100, chr = 100),
        looks = listOf(npc(0x031D)),
        defaultSkills = listOf(mskillAxeKick_620, mskillFrostArmor_622, mskillDoubleSlap_624, mskillRush_626, mskillHeavenlyStrike_627, mskillDiamondDust_628),
        defaultTargetSize = 1f,
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSightAggro),
    )

    val ramuhFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 120, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x031E)),
        defaultSkills = listOf(mskillShockStrike_629, mskillRollingThunder_631, mskillThunderspark_632, mskillLightningArmor_633, mskillChaoticStrike_635, mskillThunderstorm_636, mskillJudgmentBolt_637),
        defaultTargetSize = 1f,
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardMagicAggro, MonsterAggroConfig.standardSightAggro),
    )

    val skeletonWarFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 110, dex = 100, vit = 100, agi = 100, int = 100, mnd = 90, chr = 100),
        looks = listOf(npc(0x23C)),
        defaultSkills = listOf(mskillHellSlash_222, mskillHorrorCloud_223, mskillBlackCloud_228, mskillBloodSaber_229),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val skeletonBlmFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 110, mnd = 90, chr = 100),
        looks = listOf(npc(0x234)),
        defaultSkills = listOf(mskillHellSlash_222, mskillHorrorCloud_223, mskillBlackCloud_228, mskillBloodSaber_229),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val houndFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 110, vit = 90, agi = 120, int = 100, mnd = 90, chr = 100),
        looks = listOf(npc(0x16C)),
        defaultSkills = listOf(mskillHowling_209, mskillPoisonBreath_210, mskillRotGas_211, mskillDirtyClaw_212, mskillShadowClaw_213, mskillMethaneBreath_214),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val bugbearFamily = V0MonsterFamily(
        damageScale = 1.25f,
        delay = 180,
        statScaling = CombatStats(maxHp = 130, maxMp = 0, str = 110, dex = 95, vit = 110, agi = 90, int = 85, mnd = 85, chr = 100),
        looks = listOf(npc(0x54B)),
        defaultSkills = listOf(mskillHeavyBlow_101, mskillHeavyWhisk_102, mskillBionicBoost_103, mskillFlyingHipPress_104, mskillEarthshock_105),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
        defaultTargetSize = 1f,
        defaultBonusApplier = { it.autoAttackScale = 0.5f }
    )

    val batFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 105, int = 100, mnd = 95, chr = 100),
        looks = listOf(npc(0x100)),
        defaultSkills = listOf(mskillUltrasonics_136, mskillBloodDrain_138, mskillSubsonics_899, mskillMarrowDrain_900),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val moblinMeleeFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x2B4)),
        defaultSkills = listOf(mskillGoblinRush_334, mskillFrypan_825, mskillSmokebomb_826, mskillCrispyCandle_828, mskillParalysisShower_830),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val moblinMageFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 100, maxMp = 100, str = 100, dex = 100, vit = 100, agi = 100, int = 100, mnd = 100, chr = 100),
        looks = listOf(npc(0x2B4)),
        defaultSkills = listOf(mskillGoblinRush_334, mskillFrypan_825, mskillSmokebomb_826, mskillCrispyCandle_828, mskillParalysisShower_830),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
    )

    val dhalmelFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 105, maxMp = 0, str = 105, dex = 100, vit = 100, agi = 100, int = 90, mnd = 90, chr = 100),
        looks = listOf(npc(0x14C)),
        defaultSkills = listOf(mskillSonicWave_24, mskillStomping_25, mskillColdStare_28, mskillWhistle_29, mskillBerserk_30, mskillHealingBreeze_31),
        defaultTargetSize = 2f,
    )

    val corseFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 270,
        statScaling = CombatStats(maxHp = 95, maxMp = 200, str = 100, dex = 100, vit = 90, agi = 100, int = 120, mnd = 90, chr = 120),
        looks = listOf(npc(0x553)),
        defaultSkills = listOf(mskillMementoMori_274, mskillSilenceSeal_275, mskillEnvoutement_276, mskillDanseMacabre_277),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
    )

    val goobbueFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 270,
        statScaling = CombatStats(maxHp = 110, maxMp = 0, str = 110, dex = 90, vit = 90, agi = 90, int = 90, mnd = 90, chr = 100),
        looks = listOf(npc(0x128)),
        defaultSkills = listOf(mskillBlow_325, mskillBeatdown_327, mskillUppercut_328, mskillBlankGaze_330, mskillAntiphase_331),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        defaultTargetSize = 1f,
    )

    val trollKingFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 120, maxMp = 100, str = 105, dex = 100, vit = 105, agi = 100, int = 95, mnd = 95, chr = 100),
        looks = listOf(npc(0x74B)),
        defaultSkills = listOf(mskillSledgehammer_1546, mskillHeadSnatch_1547, mskillHaymaker_1548,  mskillIncessantFists_1549, mskillArcaneStomp_1550, mskillPleiadesRay_1551),
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.standardSoundAggro, MonsterAggroConfig.standardSightAggro),
        defaultBonusApplier = { it.knockBackResistance += 100 },
    )

    val bugardFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 110, maxMp = 0, str = 105, dex = 90, vit = 105, agi = 90, int = 90, mnd = 90, chr = 100),
        looks = listOf(npc(0x547)),
        defaultSkills = listOf(mskillTailRoll_126, mskillTusk_127, mskillScutum_128, mskillBoneCrunch_129, mskillAwfulEye_130, mskillHeavyBellow_131),
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        defaultTargetSize = 1f,
    )

    val yztargFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 110, maxMp = 100, str = 100, dex = 105, vit = 105, agi = 105, int = 100, mnd = 95, chr = 100),
        looks = listOf(npc(0x893), npc(0xB6A)),
        autoAttackSkills = listOf(mskillYztargAutoAttack_2663, mskillYztargAutoAttack_2664, mskillYztargAutoAttack_2665),
        defaultSkills = listOf(mskillCalcifyingClaw_2667, mskillDivestingStampede_2668, mskillBonebreakingBarrage_2669),
        aggroConfig = MonsterAggroConfig.standardSightAggro,
        defaultTargetSize = 1f,
    )

    val pteraketosFamily = V0MonsterFamily(
        behaviorControllerFn = { FamilyPteraketosController(it) },
        facesTarget = false,
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 130, maxMp = 100, str = 110, dex = 100, vit = 100, agi = 95, int = 110, mnd = 95, chr = 100),
        looks = listOf(npc(0x82C), npc(0xB71)),
        defaultTargetSize = 5f,
        aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.extendedSightAggro, MonsterAggroConfig.extendedSoundAggro),
        defaultSkills = emptyList(),
        defaultBonusApplier = { it.knockBackResistance += 100 },
    )

    val mantidFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 100, maxMp = 0, str = 110, dex = 110, vit = 95, agi = 110, int = 95, mnd = 95, chr = 100),
        looks = listOf(npc(0x838), npc(0x812)),
        defaultTargetSize = 1f,
        aggroConfig = MonsterAggroConfig.standardSoundAggro,
        autoAttackSkills = listOf(mskillMantidAutoAttack_2492, mskillMantidAutoAttack_2493, mskillMantidAutoAttack_2494),
        defaultSkills = listOf(mskillSlicingSickle_2495, mskillRaptorialClaw_2496, mskillPhlegmExpulsion_2497, mskillMaceratingBile_2498, mskillPreyingPosture_2499),
    )

    val galluFamily = V0MonsterFamily(
        damageScale = 1.2f,
        delay = 300,
        statScaling = CombatStats(maxHp = 110, maxMp = 100, str = 110, dex = 100, vit = 100, agi = 100, int = 110, mnd = 100, chr = 100),
        looks = listOf(npc(0x80B), npc(0x78D)),
        defaultTargetSize = 0.5f,
        aggroConfig = MonsterAggroConfig.standardSightAggro,
        autoAttackSkills = listOf(mskillGalluAutoAttack_2525, mskillGalluAutoAttack_2526, mskillGalluAutoAttack_2527),
        defaultSkills = listOf(mskillDiluvialWake_2528, mskillKurnugiCollapse_2529, mskillSearingHalitus_2530, mskillDivestingGale_2531, mskillBoltofPerdition_2532, mskillCripplingRime_2533),
    )

    val dualElementalFamily = V0MonsterFamily(
        damageScale = 1f,
        delay = 240,
        statScaling = CombatStats(maxHp = 80, maxMp = 200, str = 100, dex = 100, vit = 100, agi = 100, int = 110, mnd = 110, chr = 100),
        looks = listOf(npc(0x864), npc(0x865), npc(0x866), npc(0x867)),
        aggroConfig = MonsterAggroConfig.standardMagicAggro,
        defaultBonusApplier = {
            it.physicalDamageTaken -= 75
        },
    )

    fun makeDefinition(
        id: MonsterId,
        name: String,
        level: Int,
        statLevel: Int = level,
        family: V0MonsterFamily,
        maxHpScale: Float = 1f,
        lookVariant: Int = 0,
        statOverride: Map<CombatStat, Int> = emptyMap(),
        bonusApplier: ((CombatBonusAggregate) -> Unit)? = null,
        aggroConfig: MonsterAggroConfig? = null,
        additionalMobSkills: List<MobSkillId> = emptyList(),
    ): MonsterDefinition {

        val look = family.looks.getOrNull(lookVariant)
            ?: throw IllegalStateException("No such look variant for family: $name")

        val behaviorId = if (family.behaviorControllerFn != null) {
            ActorBehaviors.getOrRegister(FamilyBehaviorId(family), family.behaviorControllerFn)
        } else {
            V0DefaultMonsterBehavior
        }

        return MonsterDefinition(
            id = id,
            name = name,
            baseLevel = level,
            baseDamage = computeDamage(statLevel, family.damageScale),
            baseDelay = family.delay,
            look = look,
            mobSkills = family.defaultSkills + additionalMobSkills,
            autoAttackSkills = family.autoAttackSkills,
            baseCombatStats = computeBaseStats(statLevel, family, maxHpScale, statOverride),
            aggroConfig = aggroConfig ?: family.aggroConfig,
            baseBonusApplier = aggregateBonusApplier(family.defaultBonusApplier, bonusApplier),
            targetSize = family.defaultTargetSize,
            behaviorId = behaviorId,
            facesTarget = family.facesTarget,
            baseAppearanceState = family.defaultAppearanceState,
            movementControllerFn = family.movementControllerFn,
            targetable = family.targetable,
        )
    }

    private fun computeBaseStats(
        level: Int,
        family: V0MonsterFamily,
        maxHpScale: Float,
        statOverride: Map<CombatStat, Int> = emptyMap(),
    ): CombatStats {
        val levelMultiplier = GameV0Helpers.getPlayerLevelStatMultiplier(level)
        val baseStats = (CombatStats.defaultBaseStats * (2 * levelMultiplier)).copy(maxHp = hpTable[level] ?: throw IllegalStateException("Undefined HP"))
        val combatStatBuilder = CombatStatBuilder()

        for (combatStat in CombatStat.values()) {
            val override = statOverride[combatStat]
            if (override != null) {
                combatStatBuilder[combatStat] = override
                continue
            }

            var statValue = baseStats[combatStat]
            if (combatStat == CombatStat.maxMp) { statValue = statValue.coerceAtMost(100) }
            val additionalHpScale = if (combatStat == maxHp) { maxHpScale } else { 1f }

            combatStatBuilder[combatStat] = (statValue * (additionalHpScale * family.statScaling[combatStat]/100f)).roundToInt()
        }

        return combatStatBuilder.build()
    }

    fun computeDamage(level: Int, damageScale: Float): Int {
        val levelMultiplier = when {
            level < 10 -> 1f
            else -> 1.1f
        }

        val baseDamage = 50f * GameV0Helpers.getPlayerLevelStatMultiplier(level)
        return (baseDamage * levelMultiplier * damageScale).roundToInt()
    }

    private fun aggregateBonusApplier(vararg bonusApplier: ((CombatBonusAggregate) -> Unit)?): (CombatBonusAggregate) -> Unit {
        val appliers = bonusApplier.filterNotNull()
        if (appliers.size == 1) { return appliers.single() }
        return { combatBonus -> appliers.forEach { it.invoke(combatBonus) } }
    }

}