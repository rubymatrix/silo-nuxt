package xim.poc.game.configuration.v0

import xim.poc.CustomModelSettings
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.ActorStateManager
import xim.poc.game.CombatStat.maxHp
import xim.poc.game.CombatStat.maxMp
import xim.poc.game.configuration.ActorBehaviors
import xim.poc.game.configuration.MonsterAggroConfig
import xim.poc.game.configuration.MonsterDefinition
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.standardBlurConfig
import xim.poc.game.configuration.v0.V0MonsterFamilies.acuexFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.ahrimanFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.amphiptereFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.apkalluFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.batFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.beeFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.beetleFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.behemothFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.belladonnaFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.birdFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.blossomFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.bombFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.buffaloFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.bugardFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.bugbearFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.bztavianFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.carbuncleFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.cerberusFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.chapuliFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.clotFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.clusterFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.coeurlFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.corpselightFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.corseFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.crabFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.craklawFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.craverFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.crawlerErucaFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.crawlerFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.demonDarkKnightFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.demonWarriorFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.dhalmelFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.diremiteFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.dollFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.dualElementalFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.eftFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.fenrirFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.flanFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.flockBatFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.flyFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.funguarFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.fungusFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.galluFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.gargouilleFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.garudaFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.ghostFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.giantGnatFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.gigasIceFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.gigasLightningFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.gigasStandardFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.gnoleFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.goblinThiefFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.golemFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.goobbueFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.gorgerFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.harpeiaFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.hecteyesFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.houndFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.iceElementalFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.iceRaptorFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.ifritFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.jagilFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.ladybugFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.leechFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.leviathanFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.magicPotFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.makeDefinition
import xim.poc.game.configuration.v0.V0MonsterFamilies.mandragoraFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.mantidFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.mineExplosiveFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.moblinMageFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.moblinMeleeFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.morbolFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.objectFetter
import xim.poc.game.configuration.v0.V0MonsterFamilies.opoOpoFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.orcBlackmageFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.orcWarriorFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.orobonFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.panoptFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.pixieFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.pteraketosFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.pugilFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.quadavPaladinFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.quadavRedMageFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.quadavWhiteMageFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.rabbitFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.rafflesiaFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.ramFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.ramuhFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.receptacleFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.redDragonFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.redWyrmFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.rockfinFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.saplingFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.seaMonkFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.seedCrystalFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.seedThrallFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.seetherFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.selhteusTrust
import xim.poc.game.configuration.v0.V0MonsterFamilies.shadowLordSFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.sheepFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.shivaFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.skeletonBlmFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.skeletonWarFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.slugFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.snapweedFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.snollFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.snollTzarFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.snowHareFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.spiderFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.taurusFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.thinkerFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.tigerFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.titanFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.treantFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.trollKingFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.trollPaladinFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.trollRangerFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.trollWarriorFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.tulfaireFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.twitherymFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.uragniteFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.vampyrFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.wamouraFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.wamouracampaFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.wandererFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.warMachineFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.waspFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.weaponRdmFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.weeperFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.wivreFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.wormFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.wyvernFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.yagudoBardFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.yagudoSamuraiFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.yagudoWhiteMageFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.yggdreantFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.yovraFamily
import xim.poc.game.configuration.v0.V0MonsterFamilies.yztargFamily
import xim.poc.game.configuration.v0.behaviors.*
import xim.poc.game.configuration.v0.behaviors.zitah.*
import xim.poc.game.configuration.v0.constants.*
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.configuration.v0.escha.EschaDifficulty.*
import xim.poc.game.configuration.v0.escha.plus
import xim.poc.game.configuration.v0.pet.*
import xim.poc.gl.ByteColor

object V0MonsterDefinitions {

    val definitions: List<MonsterDefinition>

    init {
        definitions = ArrayList()

        definitions += makeDefinition(id = mobWildRabbit_2, name = "Wild Rabbit", family = rabbitFamily, level = 1).copy(
            mobSkills = listOf(mskillDustCloud_2, mskillWhirlClaws_3)
        )

        definitions += makeDefinition(id = mobMinerBee_3, name = "Miner Bee", family = beeFamily, level = 1).copy(
            mobSkills = listOf(mskillPollen_79)
        )

        definitions += makeDefinition(id = mobSeaboardVulture_4, name = "Seaboard Vulture", family = birdFamily, level = 2).copy(
            mobSkills = listOf(mskillWingCutter_367)
        )

        definitions += makeDefinition(id = mobAtomicCluster_5, name = "Atomic Cluster", family = clusterFamily, level = 2, maxHpScale = 3f).copy(
            expRewardScale = 2f,
        )

        definitions += makeDefinition(id = mobBigclaw_6, name = "Bigclaw", family = crabFamily, level = 2, lookVariant = 1).copy(
            mobSkills = listOf(mskillBubbleShower_186, mskillBubbleCurtain_187, mskillScissorGuard_189)
        )

        definitions += makeDefinition(id = mobMakara_7, name = "Makara", family = pugilFamily, level = 2)

        definitions += makeDefinition(id = mobCherryTree_8, family = treantFamily, name = "Cherry Tree", level = 3, lookVariant = 1, maxHpScale = 4f, statOverride = mapOf(maxMp to 100)).copy(
            behaviorId = ActorBehaviors.register(mobCherryTree_8) { MobCherryTreeBehavior(it) },
            mobSkills = listOf(mskillDrillBranch_72, mskillPineconeBomb_73, mskillLeafstorm_75),
            mobSpells = listOf(spellStonega_189),
            expRewardScale = 4f,
            targetSize = 1f,
        )

        definitions += makeDefinition(id = mobCherrySapling_9, name = "Cherry Sapling", family = saplingFamily, level = 1).copy(
            behaviorId = ActorBehaviors.register(mobCherrySapling_9) { MobCherrySaplingBehavior(it) },
            expRewardScale = 0f
        )

        definitions += makeDefinition(id = mobColkhab_10, name = "Colkhab", family = bztavianFamily, level = 5, maxHpScale = 4f).copy(
            behaviorId = ActorBehaviors.register(mobColkhab_10) { MobColkhabController(it) },
            movementControllerFn = { MobColkhabMovementController() },
            expRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobWagglingWasp_11, name = "Waggling Wasp", family = waspFamily, level = 2, maxHpScale = 2f).copy(
            behaviorId = ActorBehaviors.register(mobWagglingWasp_11) { MobColkhabBeeController(it) },
            expRewardScale = 0f,
        )

        definitions += makeDefinition(id = mobFerociousFunguar_12, name = "Ferocious Fungar", family = funguarFamily, level = 3)

        definitions += makeDefinition(id = mobWildSheep_13, name = "Wild Sheep", family = sheepFamily, level = 4)

        definitions += makeDefinition(id = mobGadfly_14, name = "Gadfly", family = flyFamily, level = 4)

        definitions += makeDefinition(id = mobLapinion_15, name = "Lapinion", family = rabbitFamily, level = 4, lookVariant = 1)

        definitions += makeDefinition(id = mobLoathsomeLeech_16, name = "Loathsome Leech", family = leechFamily, level = 3)

        definitions += makeDefinition(id = mobBlanchedMandy_17, name = "Mandragora", family = mandragoraFamily, level = 3)

        definitions += makeDefinition(id = mobBelaboringWasp_18, name = "Belaboring Wasp", family = waspFamily, level = 4)

        definitions += makeDefinition(id = mobEchoBats, name = "Echo Bats", family = flockBatFamily, level = 3)

        definitions += makeDefinition(id = mobLuckybug, name = "Luckybug", family = ladybugFamily, level = 3, lookVariant = 1)

        definitions += makeDefinition(id = mobSupernalChapuli, name = "Supernal Chapuli", family = chapuliFamily, level = 4, maxHpScale = 4f).copy(
            mobSkills = listOf(mskillSensillaBlades_2690, mskillTegminaBuffet_2691, mskillSanguinarySlash_2692, mskillOrthopterror_2693),
            expRewardScale = 4f,
            baseBonusApplier = { it.storeTp += 200 },
            targetSize = 1f,
            customModelSettings = CustomModelSettings(scale = 1.5f),
        )

        definitions += makeDefinition(id = mobKraken_19, name = "Kraken", family = seaMonkFamily, level = 6, maxHpScale = 6f).copy(
            expRewardScale = 4f,
        )

        definitions += makeDefinition(id = mobEft_20, name = "Eft", family = eftFamily, level = 5)

        definitions += makeDefinition(id = mobJagil_21, name = "Jagil", family = jagilFamily, level = 5)

        definitions += makeDefinition(id = mobOpoopo_22, name = "Opo-opo", family = opoOpoFamily, level = 6)

        definitions += makeDefinition(id = mobAlraune_28, name = "Alraune", family = mandragoraFamily, level = 6, lookVariant = 1)

        definitions += makeDefinition(id = mobClot_23, name = "Clot", family = clotFamily, level = 7)

        definitions += makeDefinition(id = mobUragnite_24, name = "Uragnite", family = uragniteFamily, level = 7).copy(
            expRewardScale = 2f,
        )

        definitions += makeDefinition(id = mobWarMachine_25, name = "WarMachine", family = warMachineFamily, level = 8, maxHpScale = 5f, statOverride = mapOf(maxMp to 100)).copy(
            behaviorId = ActorBehaviors.register(mobWarMachine_25) { MobWarmachineController(it) },
            expRewardScale = 4f,
        )

        definitions += makeDefinition(id = mobExplosive_26, name = "Explosive", family = mineExplosiveFamily, level = 8).copy(
            behaviorId = ActorBehaviors.register(mobExplosive_26) { MobWarmachineExplosiveController(it) },
            expRewardScale = 0f,
        )

        definitions += makeDefinition(id = mobKnottedRoots_27, name = "Knotted Roots", family = objectFetter, level = 8, maxHpScale = 4f).copy(
            behaviorId = ActorBehaviors.register(mobKnottedRoots_27) { MobKnottedRootsBehavior(it) },
            look = ModelLook.npc(0x0AEA),
            movementControllerFn = { NoOpActorController() },
            staticPosition = true,
            expRewardScale = 4f,
        )

        definitions += makeDefinition(id = mobTchakka_29, name = "Tchakka", family = rockfinFamily, level = 10, maxHpScale = 8f).copy(
            behaviorId = ActorBehaviors.register(mobTchakka_29) { MobTchakkaController(it) },
            expRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobHoaryCraklaw_30, name = "Hoary Craklaw", family = craklawFamily, level = 9)

        definitions += makeDefinition(id = mobCareeningTwitherym_31, name = "Careening Twitherym", family = twitherymFamily, level = 9)

        definitions += makeDefinition(id = mobLeafdancerTwitherym_32, name = "Leafdancer Twitherym", family = twitherymFamily, level = 8, lookVariant = 1)

        definitions += makeDefinition(id = mobDuskprowlers_33, name = "Duskprowlers", family = flockBatFamily, level = 8)

        definitions += makeDefinition(id = mobBlightdella_34, name = "Blightdella", family = leechFamily, level = 8, lookVariant = 1)

        definitions += makeDefinition(id = mobRipsawJagil_35, name = "Ripsaw Jagil", family = jagilFamily, level = 8)

        definitions += makeDefinition(id = mobTarichuk_36, name = "Tarichuk", family = eftFamily, level = 8, lookVariant = 1)

        definitions += makeDefinition(id = mobPerfidiousCrab_37, name = "Perfidious Crab", family = crabFamily, level = 9)

        definitions += makeDefinition(id = mobScummySlug_38, name = "Scummy Slug", family = slugFamily, level = 9)

        definitions += makeDefinition(id = mobPolarHare_39, name = "Polar Hare", family = snowHareFamily, level = 10)

        definitions += makeDefinition(id = mobIceElemental_40, name = "Ice Elemental", family = iceElementalFamily, level = 10).copy(
            mobSpells = listOf(spellBlizzard_149, spellBlizzara_830, spellIceSpikes_250),
            expRewardScale = 2f,
        )

        definitions += makeDefinition(id = mobNivalRaptor_41, name = "Nival Raptor", family = iceRaptorFamily, level = 11)

        definitions += makeDefinition(id = mobBuffalo_42, name = "Buffalo", family = buffaloFamily, level = 11)

        definitions += makeDefinition(id = mobTiger_43, name = "Tiger", family = tigerFamily, level = 11)

        definitions += makeDefinition(id = mobSnoll_44, name = "Snoll", family = snollFamily, level = 11)

        definitions += makeDefinition(id = mobSnollTzar_45, name = "Snoll Tzar", family = snollTzarFamily, level = 12, maxHpScale = 6.5f).copy(
            behaviorId = ActorBehaviors.register(mobSnollTzar_45) { MobSnollTzarBehavior(it) },
            expRewardScale = 4f,
        )

        definitions += makeDefinition(id = mobMindertaur, name = "Mindertaur", family = taurusFamily, level = 11, maxHpScale = 2f, statOverride = mapOf(maxMp to 30)).copy(
            behaviorId = ActorBehaviors.register(mobMindertaur) { MobBrothersBehavior(it, mobEldertaur) },
            expRewardScale = 3f,
        )

        definitions += makeDefinition(id = mobEldertaur, name = "Eldertaur", family = taurusFamily, level = 11, maxHpScale = 2f, lookVariant = 2, statOverride = mapOf(maxMp to 30)).copy(
            behaviorId = ActorBehaviors.register(mobEldertaur) { MobBrothersBehavior(it, mobMindertaur) },
            expRewardScale = 3f,
            targetSize = 1.5f,
        )

        definitions += makeDefinition(id = mobWanderer_46, name = "Wanderer", family = wandererFamily, level = 12)

        definitions += makeDefinition(id = mobWeeper_47, name = "Weeper", family = weeperFamily, level = 13)

        definitions += makeDefinition(id = mobThinker_48, name = "Thinker", family = thinkerFamily, level = 14).copy(
            expRewardScale = 2f,
        )

        definitions += makeDefinition(id = mobGorger_49, name = "Gorger", family = gorgerFamily, level = 14).copy(
            expRewardScale = 2f,
        )

        definitions += makeDefinition(id = mobCraver_50, name = "Craver", family = craverFamily, level = 14).copy(
            expRewardScale = 2f,
        )

        definitions += makeDefinition(id = mobSeether_51, name = "Seether", family = seetherFamily, level = 13)

        definitions += makeDefinition(id = mobSelhteus_52, "Selh'teus", family = selhteusTrust, level = 15, statOverride = mapOf(maxHp to 1000)).copy(
            behaviorId = ActorBehaviors.register(mobSelhteus_52) { MobSelhteusBehavior(it) },
            movementControllerFn = { MobSelhteusController() },
        )

        definitions += makeDefinition(id = mobMemoryReceptacle_53, name = "Memory Receptacle", family = receptacleFamily, level = 15, lookVariant = 3).copy(
            behaviorId = ActorBehaviors.register(mobMemoryReceptacle_53) { MobReceptacleBehavior(it) },
            movementControllerFn = { NoOpActorController() },
            expRewardScale = 8f,
            baseAppearanceState = 2,
        )

        definitions += makeDefinition(id = mobProcreator_54, name = "Procreator", family = gorgerFamily, level = 15, maxHpScale = 2f).copy(
            look = ModelLook.npc(0x046D),
            mobSkills = listOf(mskillSpiritAbsorption_488, mskillVanityDrive_491, mskillStygianFlatus_494, mskillPromyvionBarrier_496),
            behaviorId = ActorBehaviors.register(mobProcreator_54) { MobProcreatorBehavior(it) },
            expRewardScale = 0f,
        )

        definitions += makeDefinition(id = mobCumulator_55, name = "Cumulator", family = craverFamily, level = 15, maxHpScale = 2f).copy(
            look = ModelLook.npc(0x0473),
            behaviorId = ActorBehaviors.register(mobCumulator_55) { MobCumulatorBehavior(it) },
            expRewardScale = 0f,
        )

        definitions += makeDefinition(id = mobAgonizer_56, name = "Agonizer", family = thinkerFamily, level = 15, maxHpScale = 2f).copy(
            look = ModelLook.npc(0x0476),
            mobSkills = listOf(mskillEmptyCutter_986, mskillNegativeWhirl_987, mskillStygianVapor_988, mskillTrinaryAbsorption_992, mskillTrinaryTap_995, mskillShadowSpread_996),
            behaviorId = ActorBehaviors.register(mobAgonizer_56) { MobAgonizerBehavior(it) },
            expRewardScale = 0f,
            baseAppearanceState = 1,
        )

        definitions += makeDefinition(id = mobOffspring_57, name = "Offspring", family = gorgerFamily, level = 9).copy(
            look = ModelLook.npc(0x0475),
            mobSkills = emptyList(),
            behaviorId = ActorBehaviors.register(mobOffspring_57) { MobOffspringController(it) },
            expRewardScale = 0f,
            baseAppearanceState = 2,
        )

        definitions += makeDefinition(id = mobWamoura_58, name = "Wamoura", family = wamouraFamily, level = 19).copy(
            expRewardScale = 1.5f
        )

        definitions += makeDefinition(id = mobWamouracampa_59, name = "Wamouracampa", family = wamouracampaFamily, level = 17)

        definitions += makeDefinition(id = mobApkallu_60, name = "Apkallu", family = apkalluFamily, level = 18)

        definitions += makeDefinition(id = mobEbonyPudding_61, name = "Ebony Pudding", family = flanFamily, level = 19).copy(
            expRewardScale = 1.5f,
            mobSpells = listOf(spellThunderII_165, spellThundagaII_195, spellBioII_231, spellThundara_836)
        )

        definitions += makeDefinition(id = mobTrollIronworker_62, name = "Troll Ironworker", family = trollWarriorFamily, level = 18)

        definitions += makeDefinition(id = mobTrollTargeteer_63, name = "Troll Targeteer", family = trollPaladinFamily, level = 18).copy(
            mobSpells = listOf(spellDiaII_24, spellFlash_112, spellBanishII_29, spellBanishgaII_39)
        )

        definitions += makeDefinition(id = mobTrollSmelter_64, name = "Troll Smelter", family = trollRangerFamily, level = 18)

        definitions += makeDefinition(id = mobGurfurlur, name = "Gurfurlur", family = trollKingFamily, level = 19, maxHpScale = 3f).copy(
            behaviorId = ActorBehaviors.register(mobGurfurlur) { MobGurfurlurBehavior(it) },
            movementControllerFn = { MobGurfurlurController() },
            expRewardScale = 4f,
            customModelSettings = CustomModelSettings(scale = 1.5f),
            targetSize = 1f,
            engageMusicId = 139,
        )

        definitions += makeDefinition(id = mobAssassinFly_65, name = "Assassin Fly", family = flyFamily, lookVariant = 1, level = 18)

        definitions += makeDefinition(id = mobVolcanicLeech_66, name = "Volcanic Leech", family = leechFamily, level = 18)

        definitions += makeDefinition(id = mobFriarsLantern_67, name = "Friar's Lantern", family = bombFamily, level = 18)

        definitions += makeDefinition(id = mobMagmaEruca_68, name = "Magma Eruca", family = crawlerErucaFamily, level = 18)

        definitions += makeDefinition(id = mobCerberus_69, name = "Cerberus", family = cerberusFamily, level = 20, statLevel = 21, maxHpScale = 2f).copy(
            behaviorId = ActorBehaviors.register(mobCerberus_69) { MobCerberusBehavior(it) },
            expRewardScale = 8f,
            targetSize = 4f,
            customModelSettings = CustomModelSettings(scale = 1.33f),
        )

        definitions += makeDefinition(id = mobCerberusFetter_70, name = "", family = objectFetter, level = 20).copy(
            behaviorId = ActorBehaviors.register(mobCerberusFetter_70) { MobCerberusFetterBehavior(it) },
            expRewardScale = 0f,
            targetable = false,
        )

        definitions += makeDefinition(id = mobSeedMandragora_71, name = "Seed Mandragora", family = mandragoraFamily, level = 21, lookVariant = 2, additionalMobSkills = listOf(mskillDemonicFlower_2154)).copy(
            behaviorId = ActorBehaviors.register(mobSeedMandragora_71) { MobSeedBehavior(it) },
            expRewardScale = 2f,
            aggroConfig = MonsterAggroConfig.standardSightAggro,
            engageMusicId = 47,
        )

        definitions += makeDefinition(id = mobDancingWeapon_72, name = "Dancing Weapon", family = weaponRdmFamily, level = 20).copy(
            mobSpells = listOf(spellDiaII_24, spellBlink_53, spellAquaveil_55, spellSlow_56, spellHaste_57, spellParalyze_58, spellEnfire_100, spellFireII_145)
        )

        definitions += makeDefinition(id = mobMagicPot_73, name = "Magic Pot", family = magicPotFamily, level = 21).copy(
            mobSpells = listOf(spellAeroII_155, spellAerogaII_185)
        )

        definitions += makeDefinition(id = mobClipper_74, name = "Clipper", family = crabFamily, level = 19)

        definitions += makeDefinition(id = mobGreaterPugil_75, name = "Greater Pugil", family = pugilFamily, level = 19)

        definitions += makeDefinition(id = mobSeedGoblin_76, name = "Seed Goblin", family = goblinThiefFamily, level = 21, lookVariant = 1, additionalMobSkills = listOf(mskillSaucepan_2158), maxHpScale = 1.5f).copy(
            behaviorId = ActorBehaviors.register(mobSeedGoblin_76) { MobSeedBehavior(it) },
            expRewardScale = 2f,
            engageMusicId = 47,
        )

        definitions += makeDefinition(id = mobSeedYagudo_77, name = "Seed Yagudo", family = yagudoWhiteMageFamily, level = 22, lookVariant = 1, additionalMobSkills = listOf(mskillFeatherMaelstrom_2157), maxHpScale = 1.5f).copy(
            behaviorId = ActorBehaviors.register(mobSeedYagudo_77) { MobSeedBehavior(it) },
            expRewardScale = 3f,
            engageMusicId = 47,
            mobSpells = listOf(spellDiaII_24, spellBanishII_29, spellBanishgaII_39, spellProtectII_44, spellShellII_49)
        )

        definitions += makeDefinition(id = mobSeedQuadav_78, name = "Seed Quadav", family = quadavRedMageFamily, level = 22, lookVariant = 1, additionalMobSkills = listOf(mskillThunderousYowl_2156), maxHpScale = 1.5f).copy(
            behaviorId = ActorBehaviors.register(mobSeedQuadav_78) { MobSeedBehavior(it) },
            expRewardScale = 3f,
            engageMusicId = 47,
        )

        definitions += makeDefinition(id = mobSeedOrc_79, name = "Seed Orc", family = orcWarriorFamily, level = 22, lookVariant = 1, additionalMobSkills = listOf(mskillPhantasmalDance_2155), maxHpScale = 1.5f).copy(
            behaviorId = ActorBehaviors.register(mobSeedOrc_79) { MobSeedBehavior(it) },
            expRewardScale = 3f,
            engageMusicId = 47,
        )

        definitions += makeDefinition(id = mobGiantGateKeeper_80, name = "Giant Gatekeeper", family = gigasStandardFamily, level = 21)

        definitions += makeDefinition(id = mobGiantLobber_81, name = "Giant Lobber", family = gigasLightningFamily, level = 21)

        definitions += makeDefinition(id = mobChaosIdol_82, name = "Chaos Idol", family = dollFamily, level = 21)

        definitions += makeDefinition(id = mobPorphyrion_83, name = "Porphyrion", family = gigasIceFamily, level = 22, maxHpScale = 1.5f).copy(
            expRewardScale = 2f,
            targetSize = 1.5f,
            customModelSettings = CustomModelSettings(scale = 1.5f),
        )

        definitions += makeDefinition(id = mobSeedCrystal_84, name = "Seed Crystal", family = seedCrystalFamily, level = 23, maxHpScale = 3f).copy(
            behaviorId = ActorBehaviors.register(mobSeedCrystal_84) { MobSeedCrystalBehavior(it) },
            autoAttackRange = 100f,
            expRewardScale = 5f,
            engageMusicId = 47,
            movementControllerFn = { NoOpActorController() },
            mobSpells = listOf(spellFiragaIII_176, spellBlizzagaIII_181, spellAerogaIII_186, spellStonegaIII_191, spellThundagaIII_196, spellWatergaIII_201)
        )

        definitions += makeDefinition(id = mobSeedThrall_85, name = "Seed Thrall", family = seedThrallFamily, level = 20, maxHpScale = 0.33f).copy(
            behaviorId = ActorBehaviors.register(mobSeedThrall_85) { MobSeedThrallBehavior(it) },
            expRewardScale = 0f,
            engageMusicId = 47,
        )

        definitions += makeDefinition(id = mobHyoscya_86, name = "Hyoscya", family = belladonnaFamily, level = 24, maxHpScale = 3f).copy(
            behaviorId = ActorBehaviors.register(mobHyoscya_86) { MobHyoscyaBehavior(it) },
            expRewardScale = 4f,
            rpRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobRafflesia_87, name = "Hyoscya's Rafflesia", family = rafflesiaFamily, level = 22, maxHpScale = 0.33f).copy(
            behaviorId = ActorBehaviors.register(mobRafflesia_87) { MobHyoscyaPetBehavior(it) },
            customModelSettings = CustomModelSettings(scale = 0.5f),
            expRewardScale = 0f,
            mobSkills = emptyList(),
        )

        definitions += makeDefinition(id = mobCheekyOpoOpop_88, name = "Cheeky Opo-Opo", family = opoOpoFamily, level = 23)

        definitions += makeDefinition(id = mobTwitherym_89, name = "Twitherym", family = twitherymFamily, level = 23)

        definitions += makeDefinition(id = mobSnapweed_90, name = "Snapweed", family = snapweedFamily, level = 23)

        definitions += makeDefinition(id = mobCorpseFlower_91, name = "Corpse Flower", family = rafflesiaFamily, level = 24).copy(
            expRewardScale = 2f
        )

        definitions += makeDefinition(id = mobYumcax_92, name = "Yumcax", family = yggdreantFamily, level = 25, maxHpScale = 4f).copy(
            behaviorId = ActorBehaviors.register(mobYumcax_92) { MobYumcaxBehavior(it) },
            targetSize = 2f,
            expRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobFlatusAcuex_93, name = "Flatus Acuex", family = acuexFamily, level = 22)

        definitions += makeDefinition(id = mobLividUmbril_94, name = "Livid Umbril", family = V0MonsterFamilies.umbrilFamily, level = 23).copy(
            expRewardScale = 2f
        )

        definitions += makeDefinition(id = mobAsperousMarolith_95, name = "Asperous Marolith", family = V0MonsterFamilies.marolithFamily, level = 23, lookVariant = 1).copy(
            expRewardScale = 2f
        )

        definitions += makeDefinition(id = mobTenebrousObdella_96, name = "Tenebrous Obdella", family = leechFamily, level = 22, lookVariant = 1)

        definitions += makeDefinition(id = mobBalasBats_97, name = "Balas Bats", family = flockBatFamily, level = 22)

        definitions += makeDefinition(id = mobPallidFunguar_98, name = "Pallid Funguar", family = funguarFamily, level = 22)

        definitions += makeDefinition(id = mobSubterraneSpider_99, name = "Subterrane Spider", family = spiderFamily, level = 22, lookVariant = 2)

        definitions += makeDefinition(id = mobPungentFungus_100, name = "Pungent Fungus", family = fungusFamily, level = 23)

        definitions += makeDefinition(id = mobNumbingBlossom_101, name = "Numbing Blossom", family = blossomFamily, level = 23)

        definitions += makeDefinition(id = mobYumcaxWatcher_102, name = "Yumcax's Watcher", family = panoptFamily, level = 23, maxHpScale = 0.33f).copy(
            behaviorId = ActorBehaviors.register(mobYumcaxWatcher_102) { MobYumcaxWatcherBehavior(it) },
            expRewardScale = 0f,
        )

        definitions += makeDefinition(id = mobShadowLord_103, name = "Shadow Lord", family = shadowLordSFamily, level = 28, maxHpScale = 5f).copy(
            behaviorId = ActorBehaviors.register(mobShadowLord_103) { MobShadowLordSBehavior(it) },
            movementControllerFn = { MobShadowLordSMovementController() },
            targetSize = 1.5f,
            onSpawn = { it.faceToward(ActorStateManager.player()) },
            aggroConfig = MonsterAggroConfig.compose(MonsterAggroConfig.extendedSightAggro, MonsterAggroConfig.extendedSoundAggro),
            expRewardScale = 10f,
        )

        definitions += makeDefinition(id = mobIronQuadav_104, name = "Iron Quadav", family = quadavPaladinFamily, level = 26, lookVariant = 1).copy(
            behaviorId = ActorBehaviors.register(mobIronQuadav_104) { MobQuadavIronBehavior(it) },
            movementControllerFn = { MobBeastmanPartyController() },
            mobSkills = listOf(mskillDiamondShell_1947, mskillHowl_506),
            mobSpells = listOf(spellFlash_112, spellBanishgaIII_40),
            expRewardScale = 1.5f
        )

        definitions += makeDefinition(id = mobVajraQuadav_105, name = "Vajra Quadav", family = quadavWhiteMageFamily, level = 26, lookVariant = 1, maxHpScale = 0.5f).copy(
            behaviorId = ActorBehaviors.register(mobVajraQuadav_105) { MobQuadavVajraBehavior(it) },
            movementControllerFn = { MobBeastmanPartyController(10f, 12f) },
            mobSkills = listOf(mskillShellGuard_358),
            expRewardScale = 1.5f
        )

        definitions += makeDefinition(id = mobYagudoTemplar_106, name = "Yagudo Templar", family = yagudoSamuraiFamily, level = 26, lookVariant = 1).copy(
            behaviorId = ActorBehaviors.register(mobYagudoTemplar_106) { MobYagudoTemplarBehavior(it) },
            movementControllerFn = { MobBeastmanPartyController() },
            mobSkills = listOf(mskillFeatherStorm_361, mskillSweep_364, mskillHowl_508),
            expRewardScale = 1.5f
        )

        definitions += makeDefinition(id = mobYagudoChanter_107, name = "Yagudo Chanter", family = yagudoBardFamily, level = 26, lookVariant = 1, maxHpScale = 0.75f).copy(
            behaviorId = ActorBehaviors.register(mobYagudoChanter_107) { MobYagudoChanterBehavior(it) },
            movementControllerFn = { MobBeastmanPartyController(8f, 10f) },
            mobSpells = listOf(spellMagesBalladII_387, spellVictoryMarch_420, spellCarnageElegy_422),
            expRewardScale = 1.5f
        )

        definitions += makeDefinition(id = mobOrcishVeteran_108, name = "Orcish Veteran", family = orcWarriorFamily, level = 26, lookVariant = 2).copy(
            behaviorId = ActorBehaviors.register(mobOrcishVeteran_108) { MobOrcishVeteranBehavior(it) },
            movementControllerFn = { MobBeastmanPartyController() },
            mobSkills = listOf(mskillBattleDance_353, mskillHowl_510),
            expRewardScale = 1.5f
        )

        definitions += makeDefinition(id = mobOrcishProphetess_109, name = "Orcish Prophetess", family = orcBlackmageFamily, level = 26, lookVariant = 1, maxHpScale = 0.75f).copy(
            behaviorId = ActorBehaviors.register(mobOrcishProphetess_109) { MobOrcishProphetessBehavior(it) },
            movementControllerFn = { MobBeastmanPartyController(8f, 10f) },
            mobSpells = listOf(spellFiragaIII_176, spellFiragaIV_177),
            mobSkills = listOf(mskillBattleDance_353, mskillHowl_510),
            expRewardScale = 1.5f
        )

        definitions += makeDefinition(id = mobFustyGnole_110, name = "Fusty Gnole", family = gnoleFamily, level = 25).copy(
            expRewardScale = 2f
        )

        definitions += makeDefinition(id = mobCointeach_111, name = "Cointeach", family = corpselightFamily, level = 25).copy(
            mobSpells = listOf(spellBlizzagaIII_181, spellThundagaIII_196)
        )

        definitions += makeDefinition(id = mobDireGargouille_112, name = "Dire Gargouille", family = gargouilleFamily, level = 25)

        definitions += makeDefinition(id = mobDemonWarrior_113, name = "Demon Warrior", family = demonWarriorFamily, level = 25)

        definitions += makeDefinition(id = mobDemonBefouler_114, name = "Demon Befouler", family = demonDarkKnightFamily, level = 25).copy(
            mobSpells = listOf(spellDrainII_246, spellAspirII_248, spellStun_252)
        )

        definitions += makeDefinition(id = mobVarkolak_115, name = "Varkolak", family = vampyrFamily, level = 27).copy(
            expRewardScale = 2f
        )

        definitions += makeDefinition(id = mobBerserkerDemon_116, name = "Berserker Demon", family = demonWarriorFamily, level = 26, lookVariant = 1)

        definitions += makeDefinition(id = mobEclipseDemon_117, name = "Eclipse Demon", family = demonDarkKnightFamily, level = 26, lookVariant = 1).copy(
            mobSpells = listOf(spellDrainII_246, spellAspirII_248, spellStun_252)
        )

        definitions += makeDefinition(id = mobTitanotaur_118, name = "Titanotaur", family = taurusFamily, level = 27, lookVariant = 1).copy(
            expRewardScale = 2f
        )

        definitions += makeDefinition(id = mobDoomLens_119, name = "Doom Lens", family = ahrimanFamily, level = 26, lookVariant = 1).copy(
            mobSpells = listOf(spellAeroIII_156, spellAerogaIII_186, spellTornado_208, spellBlazeSpikes_249)
        )

        definitions += makeDefinition(id = mobZirnitra_120, name = "Zirnitra", family = amphiptereFamily, level = 27, maxHpScale = 3f).copy(
            behaviorId = ActorBehaviors.register(mobZirnitra_120) { MobZirnitraController(it) },
            baseAppearanceState = 1,
            expRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobZirnitrasFetter_121, name = "Zirnitra's Fetter", family = objectFetter, level = 27, lookVariant = 2).copy(
            behaviorId = ActorBehaviors.register(mobZirnitrasFetter_121) { MobZirnitraFetterController(it) },
            targetable = false,
            targetSize = 2f,
            customModelSettings = CustomModelSettings(effectScale = 1.33f),
            expRewardScale = 0f,
        )

        definitions += makeDefinition(id = mobShadowLordClone_122, name = "Shadow Clone", family = shadowLordSFamily, level = 28, maxHpScale = 0.33f).copy(
            behaviorId = ActorBehaviors.register(mobShadowLordClone_122) { MobShadowLordCloneBehavior(it) },
            customModelSettings = CustomModelSettings(scale = 0.5f),
            expRewardScale = 0f,
        )

        definitions += makeDefinition(id = mobShadowEye_123, name = "Shadow Eye", family = objectFetter, level = 28, maxHpScale = 0.25f).copy(
            behaviorId = ActorBehaviors.register(mobShadowEye_123) { MobShadowEyeBehavior(it) },
            look = ModelLook.npc(0xB11),
            expRewardScale = 0f,
        )

        definitions += makeDefinition(id = mobShadowImage_124, name = "Shadow Image", family = objectFetter, level = 28).copy(
            behaviorId = ActorBehaviors.register(mobShadowImage_124) { MobShadowImageBehavior(it) },
            movementControllerFn = { NoOpActorController() },
            expRewardScale = 0f,
            look = ModelLook.npc(0x87B),
            targetable = false,
            baseAppearanceState = 2,
        )

        // === Other areas
        miningAreaMobs(definitions)
        appendAbysseaLaTheine(definitions)
        eschaZiTahMobs(definitions)
        appendParadox(definitions)
        appendPets(definitions)

        // === Debug
        definitions += makeDefinition(id = mobTrainingDummy_288_500, name = "Training Dummy", family = objectFetter, level = 40).copy(
            behaviorId = ActorBehaviors.register(mobTrainingDummy_288_500) { MobSlashController(it) },
            movementControllerFn = { NoOpActorController() },
            expRewardScale = 0f,
            look = ModelLook.npc(0x0B5C),
        )
    }

    private fun miningAreaMobs(definitions: MutableList<MonsterDefinition>) {
        val miningMobDefinitions = ArrayList<MonsterDefinition>()

        // Zeruhn
        miningMobDefinitions += makeDefinition(id = mobBurrowerWorm_M1, name = "Burrower Worm", family = wormFamily, level = 6).copy(
            mobSpells = listOf(spellStone_159, spellStonega_189, spellRasp_238),
            aggroConfig = MonsterAggroConfig.none,
        )

        miningMobDefinitions += makeDefinition(id = mobDingBats_M2, name = "Ding Bats", family = flockBatFamily, level = 5)

        miningMobDefinitions += makeDefinition(id = mobDiggerLeech_M3, name = "Digger Leech", family = leechFamily, level = 7)

        // North Gustaberg (S)
        miningMobDefinitions += makeDefinition(id = mobWalkingSapling_M, name = "Walking Sapling", family = saplingFamily, level = 14)

        miningMobDefinitions += makeDefinition(id = mobManeatingHornet_M, name = "Maneating Hornet", family = beeFamily, level = 15)

        miningMobDefinitions += makeDefinition(id = mobHugeSpider_M, name = "Huge Spider", family = spiderFamily, level = 16)

        miningMobDefinitions += makeDefinition(id = mobLesserWivre_M, name = "Lesser Wivre", family = wivreFamily, level = 18)

        // Gusgen
        miningMobDefinitions += makeDefinition(id = mobOreEater_M, name = "Ore Eater", family = wormFamily, level = 21).copy(
            mobSpells = listOf(spellStoneIII_161, spellStonegaIII_191, spellRasp_238)
        )

        miningMobDefinitions += makeDefinition(id = mobMautheDoog_M, name = "Mauthe Doog", family = houndFamily, level = 23)

        miningMobDefinitions += makeDefinition(id = mobWightBlm_M, name = "Wight", family = skeletonBlmFamily, level = 24).copy(
            mobSpells = listOf(spellBioII_231, spellDrain_245, spellAspir_247, spellIceSpikes_250, spellStun_252)
        )

        miningMobDefinitions += makeDefinition(id = mobWightWar_M, name = "Wight", family = skeletonWarFamily, level = 24)

        miningMobDefinitions += makeDefinition(id = mobBanshee_M, name = "Banshee", family = ghostFamily, level = 26)

        // Oldton
        miningMobDefinitions += makeDefinition(id = mobBugbearDeathsman_M, name = "Bugbear Deathsman", family = bugbearFamily, level = 32)

        miningMobDefinitions += makeDefinition(id = mobPurgatoryBat_M, name = "Purgatory Bat", family = batFamily, level = 28)

        miningMobDefinitions += makeDefinition(id = mobMoblinAidman_M, name = "Moblin Aidman", family = moblinMageFamily, level = 30).copy(
            look = ModelLook.npc(0x2B4),
            mobSpells = listOf(spellCureIV_4, spellProtectIV_46, spellShellIV_51, spellDiaII_24, spellBanishIV_31, spellBanishgaIV_41)
        )

        miningMobDefinitions += makeDefinition(id = mobMoblinRoadman_M, name = "Moblin Roadman", family = moblinMeleeFamily, level = 30).copy(
            look = ModelLook.npc(0x2AE),
            baseDelay = 210,
        )

        miningMobDefinitions += makeDefinition(id = mobMoblinEngineman_M, name = "Moblin Engineman", family = moblinMageFamily, level = 30).copy(
            look = ModelLook.npc(0x2B7),
            mobSpells = listOf(spellFireIV_147, spellFiragaIV_177, spellBurn_235, spellBlazeSpikes_249)
        )

        miningMobDefinitions += makeDefinition(id = mobMoblinJunkman_M, name = "Moblin Junkman", family = moblinMeleeFamily, level = 30).copy(
            look = ModelLook.npc(0x2CC),
            baseBonusApplier = { it.doubleDamage += 20 }
        )

        miningMobDefinitions += makeDefinition(id = mobMoblinHangman_M, name = "Moblin Hangman", family = moblinMageFamily, level = 30).copy(
            look = ModelLook.npc(0x2CF),
            mobSpells = listOf(spellBioII_231, spellDrainII_246, spellAspirII_248, spellStun_252)
        )

        definitions += miningMobDefinitions.map { it.copy(
            expRewardScale = 0f,
            rpRewardScale = 1f,
        ) }
    }

    private fun appendAbysseaLaTheine(definitions: MutableList<MonsterDefinition>) {
        val customModelSettings = CustomModelSettings(scale = 1.5f, blurConfig = standardBlurConfig(ByteColor(0x80, 0x60, 0x20, 0x30)))

        definitions += makeDefinition(id = mobPlateauGlider_135_001, name = "Plateau Glider", family = flyFamily, level = 15).copy(
            aggroConfig = MonsterAggroConfig.standardSoundAggro,
        )

        definitions += makeDefinition(id = mobLaTheineLiege_135_002, name = "La Theine Liege", family = flyFamily, level = 16, maxHpScale = 2f).copy(
            aggroConfig = MonsterAggroConfig.standardSoundAggro,
            customModelSettings = customModelSettings,
            notoriousMonster = true,
            targetSize = 0.5f,
        )

        definitions += makeDefinition(id = mobFarfadet_135_003, name = "Farfadet", family = pixieFamily, level = 15).copy(
            aggroConfig = MonsterAggroConfig.standardSoundAggro,
        )

        definitions += makeDefinition(id = mobBabaYaga_135_004, name = "Baba Yaga", family = pixieFamily, level = 16, maxHpScale = 2f).copy(
            mobSkills = listOf(mskillZephyrArrow_1937, mskillLetheArrows_1938, mskillCyclonicTurmoil_1943),
            aggroConfig = MonsterAggroConfig.standardSoundAggro,
            customModelSettings = customModelSettings,
            notoriousMonster = true,
        )

        definitions += makeDefinition(id = mobCarabosse_135_005, name = "Carabosse", family = pixieFamily, level = 17, lookVariant = 1, maxHpScale = 2.5f).copy(
            behaviorId = ActorBehaviors.register(mobCarabosse_135_005) { MobCarabosseController(it) },
            expRewardScale = 4f,
        )

        definitions += makeDefinition(id = mobAkash_135_006, name = "Akash", family = carbuncleFamily, level = 17, maxHpScale = 2f).copy(
            expRewardScale = 2f,
        )

        definitions += makeDefinition(id = mobHadalGigas_135_007, name = "Hadal Gigas", family = gigasIceFamily, level = 15)

        definitions += makeDefinition(id = mobPantagruel_135_008, name = "Pantagruel", family = gigasIceFamily, level = 16, maxHpScale = 2f).copy(
            behaviorId = ActorBehaviors.register(mobPantagruel_135_008) { MobPantagruelBehavior(it) },
            customModelSettings = customModelSettings,
            notoriousMonster = true,
            targetSize = 1.5f,
        )

        definitions += makeDefinition(id = mobDemersalGigas_135_009, name = "Demersal Gigas", family = gigasLightningFamily, level = 15)


        definitions += makeDefinition(id = mobGrandgousier_135_010, name = "Grandgousier", family = gigasLightningFamily, level = 16, maxHpScale = 2f).copy(
            behaviorId = ActorBehaviors.register(mobGrandgousier_135_010) { MobGrandgousierController(it) },
            customModelSettings = customModelSettings,
            notoriousMonster = true,
            targetSize = 1.5f,
        )

        definitions += makeDefinition(id = mobBathyalGigas_135_011, name = "Bathyal Gigas", family = gigasStandardFamily, level = 15)

        definitions += makeDefinition(id = mobAdamastor_135_012, name = "Adamastor", family = gigasStandardFamily, level = 16, maxHpScale = 2f).copy(
            customModelSettings = customModelSettings,
            notoriousMonster = true,
            targetSize = 1.5f,
        )

        definitions += makeDefinition(id = mobBriareus_135_013, name = "Briareus", family = gigasStandardFamily, level = 17, maxHpScale = 4f).copy(
            behaviorId = ActorBehaviors.register(mobBriareus_135_013) { MobBriareusController(it) },
            look = ModelLook.npc(0x02E3),
            expRewardScale = 4f,
            targetSize = 3f,
        )

        definitions += makeDefinition(id = mobHammeringRam_135_014, name = "Hammering Ram", family = ramFamily, level = 15)

        definitions += makeDefinition(id = mobTrudgingThomas_135_015, name = "Trudging Thomas", family = ramFamily, level = 16, maxHpScale = 2f).copy(
            customModelSettings = customModelSettings,
            notoriousMonster = true,
            targetSize = 3f,
        )

        definitions += makeDefinition(id = mobAnglerTiger_135_016, name = "Angler Tiger", family = tigerFamily, level = 15)

        definitions += makeDefinition(id = mobMegantereon_135_017, name = "Megantereon", family = tigerFamily, level = 16, maxHpScale = 2f).copy(
            customModelSettings = customModelSettings,
            notoriousMonster = true,
            targetSize = 1f,
        )

        definitions += makeDefinition(id = mobIrateSheep_135_018, "Irate Sheep", family = sheepFamily, level = 15)

        definitions += makeDefinition(id = mobCankercap_135_019, name = "Cankercap", family = funguarFamily, level = 15, lookVariant = 1)

        definitions += makeDefinition(id = mobTopplingTuber_135_020, name = "Toppling Tuber", family = funguarFamily, level = 16, lookVariant = 1, maxHpScale = 2f).copy(
            customModelSettings = customModelSettings,
            notoriousMonster = true,
        )

        definitions += makeDefinition(id = mobHadhayosh_135_021, name = "Hadhayosh", family = behemothFamily, level = 18, statLevel = 19, lookVariant = 1, maxHpScale = 4f).copy(
            behaviorId = ActorBehaviors.register(mobHadhayosh_135_021) { MobHadhayoshBehavior(it) },
            expRewardScale = 4f,
        )

        definitions += makeDefinition(id = mobOvni_135_022, name = "Ovni", family = yovraFamily, level = 18, maxHpScale = 3f).copy(
            expRewardScale = 4f,
        )

    }

    private fun eschaZiTahMobs(definitions: MutableList<MonsterDefinition>) {
        fun t1LevelFn(difficulty: EschaDifficulty): Int {
            return when (difficulty) {
                S1 -> 29
                S2 -> 30
                S3 -> 31
                S4 -> 32
                S5 -> 33
            }
        }

        fun t2LevelFn(difficulty: EschaDifficulty): Int {
            return when (difficulty) {
                S1 -> 33
                S2 -> 34
                S3 -> 35
                S4 -> 36
                S5 -> 37
            }
        }

        definitions += makeDefinition(id = mobEschanWorm_288_001, name = "Eschan Worm", family = wormFamily, level = 27).copy(
            mobSpells = listOf(spellStoneIII_161, spellStonegaIII_191, spellQuake_210, spellRasp_238)
        )

        definitions += makeDefinition(id = mobEschanCrawler_288_003, name = "Eschan Crawler", family = crawlerFamily, level = 28)

        definitions += makeDefinition(id = mobEschanObdella_288_002, name = "Eschan Obdella", family = leechFamily, level = 26, lookVariant = 1)

        definitions += makeDefinition(id = mobEschanCouerl_288_004, name = "Eschan Couerl", family = coeurlFamily, level = 28)

        definitions += makeDefinition(id = mobEschanDhalmel_288_005, name = "Eschan Dhalmel", family = dhalmelFamily, level = 29)

        definitions += makeDefinition(id = mobEschanWeapon_288_006, name = "Eschan Weapon", family = weaponRdmFamily, level = 30)

        definitions += makeDefinition(id = mobEschanVulture_288_007, name = "Eschan Vulture", family = birdFamily, level = 29)

        definitions += makeDefinition(id = mobEschanSorcerer_288_008, name = "Eschan Sorcerer", family = skeletonBlmFamily, level = 29).copy(
            mobSpells = listOf(spellBioII_231, spellDrain_245, spellAspir_247, spellIceSpikes_250, spellStun_252)
        )

        definitions += makeDefinition(id = mobEschanCorse_288_009, name = "Eschan Corse", family = corseFamily, level = 30).copy(
            mobSpells = listOf(spellBlizzardIV_152, spellBlizzagaIII_181, spellIceSpikes_250)
        )

        definitions += makeDefinition(id = mobEschanGoobbue_288_010, name = "Eschan Goobbue", family = goobbueFamily, level = 30, statLevel = 33)

        definitions += makeDefinition(id = mobEschanSnapweed_288_011, name = "Eschan Snapweed", family = snapweedFamily, level = 30, statLevel = 32)

        definitions += makeDefinition(id = mobEschanWasp_288_012, name = "Eschan Wasp", family = waspFamily, level = 30, statLevel = 31)

        definitions += makeDefinition(id = mobEschanCrab_288_013, name = "Eschan Crab", family = crabFamily, level = 30, statLevel = 31)

        definitions += makeDefinition(id = mobEschanPugil_288_014, name = "Eschan Pugil", family = pugilFamily, level = 30, statLevel = 32)

        definitions += makeDefinition(id = mobEschanTarichuk_288_015, name = "Eschan Tarichuk", family = eftFamily, lookVariant = 1, level = 30, statLevel = 33)

        definitions += makeDefinition(id = mobEschanBugard_288_016, name = "Eschan Bugard", family = bugardFamily, level = 30, statLevel = 34)

        definitions += makeDefinition(id = mobAziDahaka_288_050, name = "Azi Dahaka", family = redWyrmFamily, level = 30, statLevel = 38, maxHpScale = 1.33f).copy(
            behaviorId = ActorBehaviors.register(mobAziDahaka_288_050) { MobAziDahakaBehavior(it) },
            mobSpells = listOf(spellFiraja_496, spellThundaja_500)
        )

        definitions += makeDefinition(id = mobAziDragon_288_051, name = "Azi's Dragon", family = redDragonFamily, level = 30, statLevel = 36, maxHpScale = 0.25f).copy(
            behaviorId = ActorBehaviors.register(mobAziDragon_288_051) { MobAziDahakaPetBehavior(it) },
            expRewardScale = 0f,
        )

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobWepwawet_288_100 + difficulty
            makeDefinition(id = monsterId, name = "Wepwawet", family = cerberusFamily, level = 28, statLevel = t1LevelFn(difficulty), lookVariant = 1, maxHpScale = 2f).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobWepwawetBehavior(difficulty, it) },
                expRewardScale = 4f + difficulty.value,
                customModelSettings = CustomModelSettings(scale = 1.33f),
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobAglaophotis_288_105 + difficulty
            makeDefinition(id = monsterId, name = "Aglaophotis", family = rafflesiaFamily, level = 28, statLevel = t1LevelFn(difficulty), lookVariant = 1, maxHpScale = 2f).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobAglaophotisBehavior(difficulty, it) },
                expRewardScale = 4f + difficulty.value,
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobVidala_288_110 + difficulty
            makeDefinition(id = monsterId, name = "Vidala", family = tigerFamily, level = 28, statLevel = t1LevelFn(difficulty), lookVariant = 1, maxHpScale = 2f, statOverride = mapOf(maxMp to 200)).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobVidalaBehavior(difficulty, it) },
                mobSkills = listOf(mskillRoar_14, mskillRazorFang_15, mskillClawCyclone_17, mskillPredatoryGlare_1424),
                expRewardScale = 4f + difficulty.value,
                mobSpells = listOf(spellBreakga_365),
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobGestalt_288_115 + difficulty
            makeDefinition(id = monsterId, name = "Gestalt", family = hecteyesFamily, level = 28, statLevel = t1LevelFn(difficulty), lookVariant = 1, maxHpScale = 2f).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobGestaltBehavior(difficulty, it) },
                expRewardScale = 4f + difficulty.value,
                mobSpells = listOf(spellFiragaIV_177, spellFireV_148, spellFlare_204, spellBlazeSpikes_249),
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobRevetaur_288_120 + difficulty
            makeDefinition(id = monsterId, name = "Revetaur", family = taurusFamily, level = 28, statLevel = t1LevelFn(difficulty), lookVariant = 2, maxHpScale = 2f).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobRevetaurBehavior(difficulty, it) },
                mobSkills = listOf(mskillMow_244, mskillUnblestArmor_247, mskillLethalTriclip_2133, mskillLithicRay_2277),
                expRewardScale = 4f + difficulty.value,
                targetSize = 3f,
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobTangataManu_288_125 + difficulty
            makeDefinition(id = monsterId, name = "Tangata Manu", family = harpeiaFamily, level = 28, statLevel = t1LevelFn(difficulty), maxHpScale = 2f).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobTangataManuBehavior(difficulty, it) },
                mobSpells = listOf(spellProtectIV_46, spellShellIV_51, spellHasteII_511),
                expRewardScale = 4f + difficulty.value,
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobGulltop_288_130 + difficulty
            makeDefinition(id = monsterId, name = "Gulltop", family = beetleFamily, level = 28, statLevel = t1LevelFn(difficulty), lookVariant = 2, maxHpScale = 2f).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobGulltopBehavior(difficulty, it) },
                mobSpells = listOf(spellAeroIV_157, spellAerogaIV_187, spellSleepga_273, spellSilencega_359),
                expRewardScale = 4f + difficulty.value,
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobVyala_288_135 + difficulty
            makeDefinition(id = monsterId, name = "Vyala", family = coeurlFamily, level = 28, statLevel = t1LevelFn(difficulty), lookVariant = 2, maxHpScale = 2f, statOverride = mapOf(maxMp to 100)).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobVyalaBehavior(difficulty, it) },
                mobSpells = listOf(spellCureIV_4, spellTemper_493, spellHasteII_511),
                expRewardScale = 4f + difficulty.value,
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobAngrboda_288_140 + difficulty
            makeDefinition(id = monsterId, name = "Angrboda", family = golemFamily, level = 28, statLevel = t1LevelFn(difficulty), lookVariant = 1, maxHpScale = 2f, statOverride = mapOf(maxMp to 100)).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobAngrbodaBehavior(difficulty, it) },
                mobSpells = listOf(spellBlink_53, spellEnfire_100, spellFireIV_147, spellFiragaIV_177, spellSlowga_357),
                expRewardScale = 4f + difficulty.value,
                customModelSettings = CustomModelSettings(scale = 1.5f),
                targetSize = 2.25f,
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobCunnast_288_145 + difficulty
            makeDefinition(id = monsterId, name = "Cunnast", family = wyvernFamily, level = 28, statLevel = t1LevelFn(difficulty), lookVariant = 2, maxHpScale = 2f).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobCunnastBehavior(difficulty, it) },
                expRewardScale = 4f + difficulty.value,
                customModelSettings = CustomModelSettings(scale = 1.25f),
                targetSize = 1.5f,
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobFerrodon_288_150 + difficulty
            makeDefinition(id = monsterId, name = "Ferrodon", family = wivreFamily, level = 28, statLevel = t1LevelFn(difficulty), lookVariant = 1, maxHpScale = 2f, statOverride = mapOf(maxMp to 100)).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobFerrodonBehavior(difficulty, it) },
                mobSpells = listOf(spellStoneIV_162, spellFireIV_147, spellStonegaIV_192, spellFiragaIV_177),
                expRewardScale = 4f + difficulty.value,
                customModelSettings = CustomModelSettings(scale = 1.5f),
                targetSize = 3f,
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobLustfulLydia_288_155 + difficulty
            makeDefinition(id = monsterId, name = "Lustful Lydia", family = morbolFamily, level = 28, statLevel = t1LevelFn(difficulty), lookVariant = 1, maxHpScale = 2f).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobLustfulLydiaBehavior(difficulty, it) },
                expRewardScale = 4f + difficulty.value,
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobIonos_288_200 + difficulty
            makeDefinition(id = monsterId, name = "Ionos", family = diremiteFamily, level = 30, statLevel = t2LevelFn(difficulty), lookVariant = 1, maxHpScale = 2f).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobIonosBehavior(difficulty, it) },
                expRewardScale = 4f + difficulty.value,
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobNosoi_288_205 + difficulty
            makeDefinition(id = monsterId, name = "Nosoi", family = tulfaireFamily, level = 30, statLevel = t2LevelFn(difficulty), lookVariant = 1, maxHpScale = 2f, statOverride = mapOf(maxMp to 200)).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobNosoiBehavior(difficulty, it) },
                mobSpells = listOf(spellBlizzardIV_152, spellBlizzagaIV_182),
                expRewardScale = 4f + difficulty.value,
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobUmdhlebi_288_210 + difficulty
            makeDefinition(id = monsterId, name = "Umdhlebi", family = belladonnaFamily, level = 30, statLevel = t2LevelFn(difficulty), maxHpScale = 2f, statOverride = mapOf(maxMp to 200)).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobUmdhlebiBehavior(difficulty, it) },
                mobSpells = listOf(spellAeroIV_157, spellAerogaIV_187, spellComet_219, spellBioII_231, spellDrainII_246, spellAspirII_248, spellSilencega_359),
                expRewardScale = 4f + difficulty.value,
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobSensualSandy_288_215 + difficulty
            makeDefinition(id = monsterId, name = "Sensual Sandy", family = morbolFamily, level = 30, statLevel = t2LevelFn(difficulty), lookVariant = 2, maxHpScale = 2f).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobSensualSandyBehavior(difficulty, it) },
                expRewardScale = 4f + difficulty.value,
                targetSize = 3f,
                customModelSettings = CustomModelSettings(scale = 2f)
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobBrittlis_288_220 + difficulty
            makeDefinition(id = monsterId, name = "Brittlis", family = giantGnatFamily, level = 30, statLevel = t2LevelFn(difficulty), maxHpScale = 2f).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobBrittlisBehavior(difficulty, it) },
                expRewardScale = 4f + difficulty.value,
                mobSpells = listOf(spellSleepga_273, spellParalyga_356, spellSilencega_359, spellBreakga_365),
            )
        }

        definitions += EschaDifficulty.values().map { difficulty ->
            val monsterId = mobKamohoalii_288_225 + difficulty
            makeDefinition(id = monsterId, name = "Kamohoalii", family = orobonFamily, level = 30, statLevel = t2LevelFn(difficulty), lookVariant = 1, maxHpScale = 2f).copy(
                behaviorId = ActorBehaviors.register(monsterId) { MobKamohoaliiBehavior(difficulty, it) },
                expRewardScale = 4f + difficulty.value,
            )
        }

        definitions += makeDefinition(id = mobFleetstalker_288_300, name = "Fleetstalker", family = yztargFamily, level = 32, statLevel = 39, lookVariant = 1, maxHpScale = 2f).copy(
            behaviorId = ActorBehaviors.register(mobFleetstalker_288_300) { MobFleetstalkerBehavior(it) },
            expRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobFleetstalkerFireElemental_288_502, name = "Baelfyr", family = dualElementalFamily, level = 32, statLevel = 39, lookVariant = 1, maxHpScale = 0.01f).copy(
            behaviorId = ActorBehaviors.register(mobFleetstalkerFireElemental_288_502) { MobFleetstalkerElementalBehavior(it) },
            mobSkills = listOf(mskillSearingTempest_2479, mskillBlindingFulgor_2480),
            expRewardScale = 0f,
        )

        definitions += makeDefinition(id = mobFleetstalkerEarthElemental_288_503, name = "Byrgen", family = dualElementalFamily, level = 32, statLevel = 39, maxHpScale = 0.01f).copy(
            behaviorId = ActorBehaviors.register(mobFleetstalkerEarthElemental_288_503) { MobFleetstalkerElementalBehavior(it) },
            mobSkills = listOf(mskillEntomb_2485, mskillTenebralCrush_2486),
            expRewardScale = 0f,
        )

        definitions += makeDefinition(id = mobShockmaw_288_305, name = "Shockmaw", family = pteraketosFamily, level = 32, statLevel = 39, maxHpScale = 2.5f).copy(
            behaviorId = ActorBehaviors.register(mobShockmaw_288_305) { MobShockmawController(it) },
            expRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobUrmahlullu_288_310, name = "Urmahlullu", family = behemothFamily, level = 32, statLevel = 39, lookVariant = 2, maxHpScale = 2.5f).copy(
            behaviorId = ActorBehaviors.register(mobUrmahlullu_288_310) { MobUrmahlulluController(it) },
            expRewardScale = 8f,
            customModelSettings = CustomModelSettings(scale = 0.5f),
            targetSize = 1.25f,
            facesTarget = true,
        )

        definitions += makeDefinition(id = mobUrmahlulluCrystal_288_505, name = "Meteorite", family = objectFetter, level = 32, statLevel = 39, maxHpScale = 0.3f).copy(
            behaviorId = ActorBehaviors.register(mobUrmahlulluCrystal_288_505) { MobUrmahlulluCrystalController(it) },
            expRewardScale = 0f,
            look = ModelLook.npc(0x092F),
        )

        definitions += makeDefinition(id = mobBlazewing_288_315, name = "Blazewing", family = mantidFamily, level = 32, statLevel = 39, lookVariant = 1, maxHpScale = 2.5f).copy(
            behaviorId = ActorBehaviors.register(mobBlazewing_288_315) { MobBlazewingController(it) },
            expRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobBlazewingFly_288_504, name = "Bloodswiller Fly", family = flyFamily, level = 32, statLevel = 39, lookVariant = 1, maxHpScale = 0.03f).copy(
            behaviorId = ActorBehaviors.register(mobBlazewingFly_288_504) { MobBlazewingFlyController(it) },
            movementControllerFn = { MobBlazewingFlyMovementController() },
            expRewardScale = 0f,
            customModelSettings = CustomModelSettings(scale = 0.5f),
        )

        definitions += makeDefinition(id = mobPazuzu_288_320, name = "Pazuzu", family = galluFamily, level = 32, statLevel = 39, maxHpScale = 2.5f).copy(
            behaviorId = ActorBehaviors.register(mobPazuzu_288_320) { MobPazuzuController(it) },
            expRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobPazuzuFetter_288_501, name = "Pazuzu's Fetter", family = objectFetter, level = 32, statLevel = 39).copy(
            behaviorId = ActorBehaviors.register(mobPazuzuFetter_288_501) { MobPazuzuFetter(it) },
            movementControllerFn = { NoOpActorController() },
            expRewardScale = 0f,
            targetable = false,
        )

        definitions += makeDefinition(id = mobAlpluachra_288_325, name = "Alpluachra", family = pixieFamily, level = 32, statLevel = 39, lookVariant = 3, maxHpScale = 2.5f).copy(
            behaviorId = ActorBehaviors.register(mobAlpluachra_288_325) { MobAlpluachraController(it) },
            expRewardScale = 8f,
            mobSkills = listOf(mskillZephyrArrow_1937, mskillLetheArrows_1938, mskillCyclonicTurmoil_1943, mskillCyclonicTorrent_1944, mskillCyclonicBlight_2438),
        )

        definitions += makeDefinition(id = mobBucca_288_330, name = "Bucca", family = pixieFamily, level = 32, statLevel = 39, lookVariant = 1, maxHpScale = 2.5f).copy(
            behaviorId = ActorBehaviors.register(mobBucca_288_330) { MobBuccaController(it) },
            movementControllerFn = { ForcedWanderController() },
            mobSpells = listOf(spellParalyga_356, spellSlowga_357, spellSilencega_359),
            expRewardScale = 0f,
            targetable = false,
        )

        definitions += makeDefinition(id = mobPuca_288_335, name = "Puca", family = pixieFamily, level = 32, statLevel = 39, lookVariant = 2, maxHpScale = 2.5f).copy(
            behaviorId = ActorBehaviors.register(mobPuca_288_335) { MobPucaController(it) },
            movementControllerFn = { ForcedWanderController() },
            mobSpells = listOf(spellFiraja_496, spellAeroja_498),
            expRewardScale = 0f,
            targetable = false,
        )

        definitions += makeDefinition(id = mobWrathare_288_400, name = "Wrathare", family = rabbitFamily, level = 34, statLevel = 41, maxHpScale = 2.5f, statOverride = mapOf(maxMp to 100)).copy(
            behaviorId = ActorBehaviors.register(mobWrathare_288_400) { MobWrathareBehavior(it) },
            expRewardScale = 12f,
            customModelSettings = CustomModelSettings(scale = 2f),
        )

        definitions += makeDefinition(id = mobWrathareClone_288_506, name = "Wrathare", family = rabbitFamily, level = 1, statLevel = 1).copy(
            behaviorId = ActorBehaviors.register(mobWrathareClone_288_506) { MobWrathareCloneBehavior(it) },
            expRewardScale = 0f,
        )

    }

    private fun appendParadox(definitions: MutableList<MonsterDefinition>) {
        definitions += makeDefinition(id = mobIfrit_100_000, name = "Ifrit", family = ifritFamily, level = 25, maxHpScale = 3f).copy(
            behaviorId = ActorBehaviors.register(mobIfrit_100_000) { MobCloisterAvatarBehavior(it, mskillInferno_592) },
            mobSpells = listOf(spellFlareII_205, spellFiraja_496),
            expRewardScale = 0f,
            rpRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobTitan_100_005, name = "Titan", family = titanFamily, level = 25, maxHpScale = 3f).copy(
            behaviorId = ActorBehaviors.register(mobTitan_100_005) { MobCloisterAvatarBehavior(it, mskillEarthenFury_601) },
            mobSpells = listOf(spellQuakeII_211, spellStoneja_499),
            expRewardScale = 0f,
            rpRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobLeviathan_100_010, name = "Leviathan", family = leviathanFamily, level = 25, maxHpScale = 3f).copy(
            behaviorId = ActorBehaviors.register(mobLeviathan_100_010) { MobCloisterAvatarBehavior(it, mskillTidalWave_610) },
            mobSpells = listOf(spellFloodII_215, spellSlowga_357, spellWaterja_501),
            expRewardScale = 0f,
            rpRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobGaruda_100_015, name = "Garuda", family = garudaFamily, level = 25, maxHpScale = 3f).copy(
            behaviorId = ActorBehaviors.register(mobGaruda_100_015) { MobCloisterAvatarBehavior(it, mskillAerialBlast_619) },
            mobSpells = listOf(spellTornadoII_209, spellHastega_358, spellAeroja_498),
            expRewardScale = 0f,
            rpRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobShiva_100_020, name = "Shiva", family = shivaFamily, level = 25, maxHpScale = 3f).copy(
            behaviorId = ActorBehaviors.register(mobShiva_100_020) { MobCloisterAvatarBehavior(it, mskillDiamondDust_628) },
            mobSpells = listOf(spellFreezeII_207, spellParalyga_356, spellBlizzaja_497),
            expRewardScale = 0f,
            rpRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobRamuh_100_025, name = "Ramuh", family = ramuhFamily, level = 25, maxHpScale = 3f).copy(
            behaviorId = ActorBehaviors.register(mobRamuh_100_025) { MobCloisterAvatarBehavior(it, mskillJudgmentBolt_637) },
            mobSpells = listOf(spellBurstII_213, spellThundaja_500),
            expRewardScale = 0f,
            rpRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobCarbuncle_100_030, name = "Carbuncle", family = carbuncleFamily, level = 27, maxHpScale = 3f).copy(
            behaviorId = ActorBehaviors.register(mobCarbuncle_100_030) { MobCloisterAvatarBehavior(it, mskillSearingLight_656) },
            mobSpells = listOf(spellHoly_21, spellBanishgaV_42),
            expRewardScale = 0f,
            rpRewardScale = 8f,
        )

        definitions += makeDefinition(id = mobFenrir_100_035, name = "Fenrir", family = fenrirFamily, level = 27, maxHpScale = 3f).copy(
            behaviorId = ActorBehaviors.register(mobFenrir_100_035) { MobCloisterFenrirBehavior(it, mskillHowlingMoon_582) },
            expRewardScale = 0f,
            rpRewardScale = 8f,
        )

    }

    private fun appendPets(definitions: MutableList<MonsterDefinition>) {
        definitions += makeDefinition(id = petColkhab, name = "Colkhab", level = 1, family = objectFetter, statOverride = mapOf(maxMp to 15)).copy(
            behaviorId = ActorBehaviors.register(petColkhab) { PetColkhabBehavior(it) },
            look = ModelLook.npc(0x9C6),
        )

        definitions += makeDefinition(id = petYumcax, name = "Yumcax", level = 1, family = objectFetter, statOverride = mapOf(maxMp to 20)).copy(
            behaviorId = ActorBehaviors.register(petYumcax) { PetYumcaxBehavior(it) },
            look = ModelLook.npc(0x9D0),
        )

        definitions += makeDefinition(id = petSelhteus, name = "Selh'teus", level = 1, family = objectFetter, statOverride = mapOf(maxMp to 30)).copy(
            behaviorId = ActorBehaviors.register(petSelhteus) { PetSelhteusBehavior(it) },
            look = ModelLook.npc(0xC16),
            customModelSettings = CustomModelSettings(scale = 0.5f)
        )

        definitions += makeDefinition(id = petHadhayosh, name = "Hadhayosh", level = 1, family = objectFetter, statOverride = mapOf(maxMp to 20)).copy(
            behaviorId = ActorBehaviors.register(petHadhayosh) { PetHadhayoshBehavior(it) },
            look = ModelLook.npc(0x195),
            customModelSettings = CustomModelSettings(scale = 0.25f)
        )

        definitions += makeDefinition(id = petTchakka, name = "Tchakka", level = 1, family = objectFetter, statOverride = mapOf(maxMp to 30)).copy(
            behaviorId = ActorBehaviors.register(petTchakka) { PetTchakkaBehavior(it) },
            look = ModelLook.npc(0x9CB),
        )

        definitions += makeDefinition(id = petAkash, name = "Akash", level = 1, family = objectFetter, statOverride = mapOf(maxMp to 10)).copy(
            behaviorId = ActorBehaviors.register(petAkash) { PetAkashBehavior(it) },
            look = ModelLook.npc(0x317),
        )

        definitions += makeDefinition(id = petSlime, name = "Slime", level = 1, family = objectFetter, statOverride = mapOf(maxMp to 5)).copy(
            behaviorId = ActorBehaviors.register(petSlime) { PetSlimeBehavior(it) },
            look = ModelLook.npc(0xB41),
        )

        definitions += makeDefinition(id = petWyvern, name = "Wyvern", level = 1, family = objectFetter).copy(
            behaviorId = ActorBehaviors.register(petWyvern) { PetWyvernBehavior(it) },
            look = ModelLook.npc(0x018),
        )
    }

}