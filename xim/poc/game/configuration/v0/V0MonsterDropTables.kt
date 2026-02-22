package xim.poc.game.configuration.v0

import xim.poc.game.configuration.ItemDropSlot
import xim.poc.game.configuration.ItemDropSlot.Companion.none
import xim.poc.game.configuration.MonsterId
import xim.poc.game.configuration.WeightedTable
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.ItemRankSettings.Companion.fixed
import xim.poc.game.configuration.v0.constants.*
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.configuration.v0.escha.plus
import xim.poc.game.configuration.v0.tower.DropTables.lvlMedicine
import xim.poc.game.configuration.v0.tower.DropTables.makeItemRankSettings

class MonsterDropTable(val dropTable: List<DropTableProvider> = emptyList())

object V0MonsterDropTables {

    private val dropTables: Map<MonsterId, MonsterDropTable>

    init {
        dropTables = HashMap()

        dropTables[mobCherryTree_8] = MonsterDropTable(listOf(
            genderedDropSlot(
                male = WeightedTable.single(ItemDropSlot(11316)),
                female = WeightedTable.single(ItemDropSlot(11317))
            ),
            drop(ItemDropSlot(itemId = itemBeastCollar_13121))
        ))

        dropTables[mobColkhab_10] = MonsterDropTable(listOf(
            basic(WeightedTable.single(ItemDropSlot(itemId = 27781, rankSettings = makeItemRankSettings(15)))),
            basic(WeightedTable.single(ItemDropSlot(itemId = 28201, rankSettings = makeItemRankSettings(15)))),
            basic(WeightedTable.uniform(ItemDropSlot(itemId = 3980))),
            drop(ItemDropSlot(itemId = armorHuaniCollar_28384))
        ))

        dropTables[mobKraken_19] = MonsterDropTable(listOf(
            genderedDropSlot(
                male = WeightedTable.single(ItemDropSlot(23871)),
                female = WeightedTable.single(ItemDropSlot(23873))
            ),
            genderedDropSlot(
                male = WeightedTable.single(ItemDropSlot(23873)),
                female = WeightedTable.single(ItemDropSlot(23874))
            ),
        ))

        dropTables[mobTchakka_29] = MonsterDropTable(listOf(
            basic(WeightedTable.single(ItemDropSlot(itemId = 27780, rankSettings = makeItemRankSettings(15)))),
            basic(WeightedTable.single(ItemDropSlot(itemId = 28343, rankSettings = makeItemRankSettings(15)))),
            basic(WeightedTable.uniform(ItemDropSlot(itemId = 3979))),
            drop(ItemDropSlot(itemId = armorAtzintliNecklace_28385)),
        ))

        dropTables[mobMemoryReceptacle_53] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemMoonAmulet_13114)),
            basic(WeightedTable.uniform(ItemDropSlot(itemId = 1723))),
        ))

        dropTables[mobCerberus_69] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 2168)),
        ))

        dropTables[mobSeedCrystal_84] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemMirkeWardecors_11314, rankSettings = makeItemRankSettings(18))),
        ))

        dropTables[mobYumcax_92] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 27767, rankSettings = makeItemRankSettings(15))),
            drop(ItemDropSlot(itemId = 28050, rankSettings = makeItemRankSettings(15))),
            drop(ItemDropSlot(itemId = 4014)),
            drop(ItemDropSlot(itemId = armorQuanpurNecklace_28387))
        ))

        dropTables[mobShadowLord_103] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemNocturnusHelm_11501, rankSettings = makeItemRankSettings(15))),
            drop(ItemDropSlot(itemId = itemNocturnusMail_11354, rankSettings = makeItemRankSettings(15))),
            drop(dropRate = 1.0/5.0, ItemDropSlot(itemId = weaponTalekeeper_18903, rankSettings = ItemRankSettings.leveling()))
        ))

        dropTables[mobIfrit_100_000] = MonsterDropTable(listOf(drop(ItemDropSlot(itemId = itemSunEarring_13344))))
        dropTables[mobTitan_100_005] = MonsterDropTable(listOf(drop(ItemDropSlot(itemId = itemYellowEarring_13348))))
        dropTables[mobLeviathan_100_010] = MonsterDropTable(listOf(drop(ItemDropSlot(itemId = itemAquamrneEarring_13347))))
        dropTables[mobGaruda_100_015] = MonsterDropTable(listOf(drop(ItemDropSlot(itemId = itemGreenEarring_13343))))
        dropTables[mobShiva_100_020] = MonsterDropTable(listOf(drop(ItemDropSlot(itemId = itemZirconEarring_13345))))
        dropTables[mobRamuh_100_025] = MonsterDropTable(listOf(drop(ItemDropSlot(itemId = itemPurpleEarring_13346))))
        dropTables[mobCarbuncle_100_030] = MonsterDropTable(listOf(drop(ItemDropSlot(itemId = itemMoonEarring_13350))))
        dropTables[mobFenrir_100_035] = MonsterDropTable(listOf(drop(ItemDropSlot(itemId = itemNightEarring_13349))))

        dropTables[mobPlateauGlider_135_001] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 4051)),
            drop(dropRate = 0.5, ItemDropSlot(itemId = itemTrInsectWing_2897)),
            drop(dropRate = 0.2, ItemDropSlot(itemId = itemVoyageJewel_3228)),
        ))

        dropTables[mobLaTheineLiege_135_002] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemVoyageCoin_3227)),
            drop(ItemDropSlot(itemId = itemVoyageJewel_3228), ItemDropSlot(itemId = itemVoyageJewel_3228, quantity = 2)),
            drop(ItemDropSlot(itemId = 4051), ItemDropSlot(itemId = 4051, quantity = 2)),
        ))

        dropTables[mobFarfadet_135_003] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 4051)),
            drop(dropRate = 0.5, ItemDropSlot(itemId = itemPiceousScale_2898)),
            drop(dropRate = 0.2, ItemDropSlot(itemId = itemVoyageJewel_3228)),
        ))

        dropTables[mobBabaYaga_135_004] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemVoyageCoin_3227)),
            drop(ItemDropSlot(itemId = itemVoyageJewel_3228), ItemDropSlot(itemId = itemVoyageJewel_3228, quantity = 2)),
            drop(ItemDropSlot(itemId = 4051), ItemDropSlot(itemId = 4051, quantity = 2)),
        ))

        dropTables[mobCarabosse_135_005] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemVoyageCoin_3227, quantity = 3)),
            basic(WeightedTable.single(ItemDropSlot(itemId = itemTeutatesSubligar_15427))),
            drop(ItemDropSlot(itemId = itemCarabossesGem_2930)),
            drop(dropRate = 2.0/3.0, ItemDropSlot(itemId = itemCarabossesGem_2930)),
            drop(ItemDropSlot(itemId = 4051, quantity = 3), ItemDropSlot(itemId = 4051, quantity = 4)),
        ))

        dropTables[mobAkash_135_006] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemVoyageCoin_3227, quantity = 2)),
            drop(ItemDropSlot(itemId = itemCallersPendant_11619)),
            drop(ItemDropSlot(itemId = 4051, quantity = 3), ItemDropSlot(itemId = 4051, quantity = 4)),
        ))

        dropTables[mobHadalGigas_135_007] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 4051)),
            drop(dropRate = 0.5, ItemDropSlot(itemId = itemOversizedSock_2895)),
            drop(dropRate = 0.2, ItemDropSlot(itemId = itemVoyageStone_3226)),
        ))

        dropTables[mobPantagruel_135_008] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemVoyageCoin_3227)),
            drop(ItemDropSlot(itemId = itemVoyageStone_3226), ItemDropSlot(itemId = itemVoyageStone_3226, quantity = 2)),
            drop(ItemDropSlot(itemId = 4051), ItemDropSlot(itemId = 4051, quantity = 2)),
        ))

        dropTables[mobDemersalGigas_135_009] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 4051)),
            drop(dropRate = 0.5, ItemDropSlot(itemId = itemMassiveArmband_2896)),
            drop(dropRate = 0.2, ItemDropSlot(itemId = itemVoyageStone_3226)),
        ))

        dropTables[mobGrandgousier_135_010] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemVoyageCoin_3227)),
            drop(ItemDropSlot(itemId = itemVoyageStone_3226), ItemDropSlot(itemId = itemVoyageStone_3226, quantity = 2)),
            drop(ItemDropSlot(itemId = 4051), ItemDropSlot(itemId = 4051, quantity = 2)),
        ))

        dropTables[mobBathyalGigas_135_011] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 4051)),
            drop(dropRate = 0.5, ItemDropSlot(itemId = itemTrophyShield_2894)),
            drop(dropRate = 0.2, ItemDropSlot(itemId = itemVoyageStone_3226)),
        ))

        dropTables[mobAdamastor_135_012] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemVoyageCoin_3227)),
            drop(ItemDropSlot(itemId = itemVoyageStone_3226), ItemDropSlot(itemId = itemVoyageStone_3226, quantity = 2)),
            drop(ItemDropSlot(itemId = 4051), ItemDropSlot(itemId = 4051, quantity = 2)),
        ))

        dropTables[mobBriareus_135_013] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemVoyageCoin_3227, quantity = 3)),
            basic(WeightedTable.single(ItemDropSlot(itemId = itemTeutatesSubligar_15427))),
            drop(ItemDropSlot(itemId = itemHelmofBriareus_2929)),
            drop(dropRate = 2.0/3.0, ItemDropSlot(itemId = itemHelmofBriareus_2929)),
            drop(ItemDropSlot(itemId = itemJuogi_14336)),
        ))

        dropTables[mobHammeringRam_135_014] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 4051)),
            drop(dropRate = 0.5, ItemDropSlot(itemId = itemRMuttonChop_2892)),
            drop(dropRate = 0.2, ItemDropSlot(itemId = itemVoyageCard_3229)),
        ))

        dropTables[mobTrudgingThomas_135_015] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemVoyageCoin_3227)),
            drop(ItemDropSlot(itemId = itemVoyageCard_3229), ItemDropSlot(itemId = itemVoyageCard_3229, quantity = 2)),
            drop(ItemDropSlot(itemId = 4051), ItemDropSlot(itemId = 4051, quantity = 2)),
        ))

        dropTables[mobAnglerTiger_135_016] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 4051)),
            drop(dropRate = 0.5, ItemDropSlot(itemId = itemGBlkTigerFang_2893)),
            drop(dropRate = 0.2, ItemDropSlot(itemId = itemVoyageCard_3229)),
        ))

        dropTables[mobMegantereon_135_017] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemVoyageCoin_3227)),
            drop(ItemDropSlot(itemId = itemVoyageCard_3229), ItemDropSlot(itemId = itemVoyageCard_3229, quantity = 2)),
            drop(ItemDropSlot(itemId = 4051), ItemDropSlot(itemId = 4051, quantity = 2)),
        ))

        dropTables[mobIrateSheep_135_018] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 4051)),
            drop(dropRate = 0.2, ItemDropSlot(itemId = itemVoyageCard_3229)),
        ))

        dropTables[mobCankercap_135_019] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 4051)),
            drop(dropRate = 0.5, lvlMedicine(20)),
        ))

        dropTables[mobTopplingTuber_135_020] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 4051), ItemDropSlot(itemId = 4051, quantity = 2)),
            drop(lvlMedicine(20)),
            drop(lvlMedicine(20)),
            drop(lvlMedicine(20)),
        ))

        dropTables[mobHadhayosh_135_021] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 4051, quantity = 5), ItemDropSlot(itemId = 4051, quantity = 6)),
            basic(WeightedTable.single(ItemDropSlot(itemId = itemTimarliDastanas_14060, rankSettings = makeItemRankSettings(15)))),
            drop(ItemDropSlot(itemId = itemStormGorget_13167)),
        ))

        dropTables[mobOvni_135_022] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = 4051, quantity = 4), ItemDropSlot(itemId = 4051, quantity = 10)),
            basic(WeightedTable.single(ItemDropSlot(itemId = itemTimarliJawshan_13791, rankSettings = makeItemRankSettings(12)))),
        ))

        dropTables[mobAziDahaka_288_050] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = itemVulcaniteOre_9075, quantity = 1))
        ))

        val gravewoodDrops = listOf(
            mobEschanWorm_288_001 to 1,
            mobEschanObdella_288_002 to 1,
            mobEschanCrawler_288_003 to 2,
            mobEschanCouerl_288_004 to 4,
            mobEschanDhalmel_288_005 to 6,
            mobEschanWeapon_288_006 to 8,
            mobEschanVulture_288_007 to 6,
            mobEschanSorcerer_288_008 to 6,
            mobEschanCorse_288_009 to 8,
            mobEschanGoobbue_288_010 to 20,
            mobEschanSnapweed_288_011 to 15,
            mobEschanWasp_288_012 to 12,
            mobEschanCrab_288_013 to 12,
            mobEschanPugil_288_014 to 15,
        )

        for ((mobId, quantity) in gravewoodDrops) {
            dropTables[mobId] = MonsterDropTable(listOf(drop(ItemDropSlot(itemId = itemGravewoodLog_9076, quantity = quantity))))
        }

        dropTables[mobEschanTarichuk_288_015] = MonsterDropTable(listOf(drop(ItemDropSlot(itemId = itemAshweed_9078, quantity = 1))))
        dropTables[mobEschanBugard_288_016] = MonsterDropTable(listOf(drop(ItemDropSlot(itemId = itemAshweed_9078, quantity = 2))))

        val ashweedDrops = listOf(
            mobIonos_288_200,
            mobNosoi_288_205,
            mobUmdhlebi_288_210,
            mobSensualSandy_288_215,
            mobBrittlis_288_220,
            mobKamohoalii_288_225,
        )

        for (difficulty in EschaDifficulty.values()) {
            val drop = drop(when (difficulty) {
                EschaDifficulty.S1 -> ItemDropSlot(itemId = itemAshweed_9078, quantity = 3)
                EschaDifficulty.S2 -> ItemDropSlot(itemId = itemAshweed_9078, quantity = 8)
                EschaDifficulty.S3 -> ItemDropSlot(itemId = itemAshweed_9078, quantity = 15)
                EschaDifficulty.S4 -> ItemDropSlot(itemId = itemAshweed_9078, quantity = 24)
                EschaDifficulty.S5 -> ItemDropSlot(itemId = itemAshweed_9078, quantity = 35)
            })

            for (monsterId in ashweedDrops) {
                dropTables[monsterId + difficulty] = MonsterDropTable(listOf(drop))
            }
        }

        dropTables[mobFleetstalker_288_300] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = weaponRouter_20847, rankSettings = fixed(30)))
        ))

        dropTables[mobShockmaw_288_305] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = weaponAnnealedLance_20938, rankSettings = fixed(30)))
        ))

        dropTables[mobUrmahlullu_288_310] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = weaponChastisers_20523, rankSettings = fixed(30)))
        ))

        dropTables[mobBlazewing_288_315] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = armorKubiraMeikogai_26959, rankSettings = fixed(30)))
        ))

        dropTables[mobPazuzu_288_320] = MonsterDropTable(listOf(
            drop(ItemDropSlot(itemId = armorMakoraMeikogai_26961, rankSettings = fixed(30)))
        ))

        dropTables[mobAlpluachra_288_325] = MonsterDropTable(listOf(drop(
            ItemDropSlot(itemId = armorAnnointKalasiris_26960, rankSettings = fixed(30)))
        ))

        dropTables[mobWrathare_288_400] = MonsterDropTable(listOf(drop(
            ItemDropSlot(itemId = armorEnforcersHarness_26962, rankSettings = fixed(30)))
        ))
    }

    operator fun get(monsterId: MonsterId): MonsterDropTable {
        return dropTables[monsterId] ?: MonsterDropTable()
    }

    private fun basic(table: WeightedTable<ItemDropSlot>): DropTableProvider {
        return DropTableProvider { table }
    }

    private fun genderedDropSlot(male: WeightedTable<ItemDropSlot>, female: WeightedTable<ItemDropSlot>): DropTableProvider {
        return DropTableProvider.genderDropTable(male, female)
    }

    private fun drop(vararg dropSlot: ItemDropSlot): DropTableProvider {
        return basic(WeightedTable.uniform(dropSlot.toList()))
    }

    private fun drop(dropSlots: List<ItemDropSlot>): DropTableProvider {
        return basic(WeightedTable.uniform(dropSlots))
    }

    private fun drop(dropRate: Double, dropSlot: ItemDropSlot): DropTableProvider {
        check(dropRate > 0 && dropRate < 1) { "Illegal drop rate: $dropRate" }
        return DropTableProvider { WeightedTable(listOf(dropSlot to dropRate, none to 1.0 - dropRate)) }
    }

    private fun drop(dropRate: Double, dropSlots: List<ItemDropSlot>): DropTableProvider {
        check(dropRate > 0 && dropRate < 1) { "Illegal drop rate: $dropRate" }

        val perItemDropRate = dropRate / dropSlots.size
        val drops = dropSlots.map { it to perItemDropRate } + (none to 1.0 - dropRate)

        return DropTableProvider { WeightedTable(drops) }
    }

}