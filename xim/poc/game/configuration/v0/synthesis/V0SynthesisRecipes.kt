package xim.poc.game.configuration.v0.synthesis

import xim.poc.SynthesisType
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.interactions.ItemId

typealias RecipeId = Int

private data class GemRecipe(
    val rawMaterial: Int,
    val ringNq: Int,
    val ringHq: Int,
)

class V0SynthesisOutput(val itemId: Int, val quantity: Int = 1)

class V0SynthesisRecipe(val recipeLevel: Int, val output: V0SynthesisOutput, val hqOutput: V0SynthesisOutput? = null, val input: List<Int>) {

    fun getRecipeId(): RecipeId {
        return output.itemId
    }

    fun getSynthesisType(): SynthesisType {
        return SynthesisType.Fire
    }

    fun getCrystalType(): ItemId {
        return itemFireCrystal_4096.id
    }

}

object V0SynthesisRecipes {

    val recipes: List<V0SynthesisRecipe>
    private val recipesById: Map<RecipeId, V0SynthesisRecipe>

    init {
        recipes = ArrayList()

        recipes += V0SynthesisRecipe(1, output = V0SynthesisOutput(itemCopperIngot_648), hqOutput = V0SynthesisOutput(itemCopperIngot_648, 3), input = listOf(itemCopperOre_640, itemCopperOre_640, itemCopperOre_640, itemCopperOre_640))
        recipes += V0SynthesisRecipe(3, output = V0SynthesisOutput(itemBrassIngot_650), hqOutput = V0SynthesisOutput(itemBrassIngot_650, 3), input = listOf(itemZincOre_642, itemCopperOre_640, itemCopperOre_640, itemCopperOre_640))
        recipes += V0SynthesisRecipe(7, output = V0SynthesisOutput(itemSilverIngot_744), hqOutput = V0SynthesisOutput(itemSilverIngot_744, 3), input = listOf(itemSilverOre_736, itemSilverOre_736, itemSilverOre_736, itemSilverOre_736))
        recipes += V0SynthesisRecipe(12, output = V0SynthesisOutput(itemMythrilIngot_653), hqOutput = V0SynthesisOutput(itemMythrilIngot_653, 3), input = listOf(itemMythrilOre_644, itemMythrilOre_644, itemMythrilOre_644, itemMythrilOre_644))
        recipes += V0SynthesisRecipe(19, output = V0SynthesisOutput(itemGoldIngot_745), hqOutput = V0SynthesisOutput(itemGoldIngot_745, 3), input = listOf(itemGoldOre_737, itemGoldOre_737, itemGoldOre_737, itemGoldOre_737))
        recipes += V0SynthesisRecipe(27, output = V0SynthesisOutput(itemPlatinumIngot_746), hqOutput = V0SynthesisOutput(itemPlatinumIngot_746, 3), input = listOf(itemPlatinumOre_738, itemPlatinumOre_738, itemPlatinumOre_738, itemPlatinumOre_738))

        recipes += V0SynthesisRecipe(2, output = V0SynthesisOutput(itemCopperRing_13454), hqOutput = V0SynthesisOutput(itemCopperRing1_13492), input = listOf(itemCopperIngot_648, itemCopperIngot_648))
        recipes += V0SynthesisRecipe(5, output = V0SynthesisOutput(itemBrassRing_13465), hqOutput = V0SynthesisOutput(itemBrassRing1_13493), input = listOf(itemBrassIngot_650, itemBrassIngot_650))
        recipes += V0SynthesisRecipe(10, output = V0SynthesisOutput(itemSilverRing_13456), hqOutput = V0SynthesisOutput(itemSilverRing1_13518), input = listOf(itemSilverIngot_744, itemSilverIngot_744))
        recipes += V0SynthesisRecipe(15, output = V0SynthesisOutput(itemMythrilRing_13446), hqOutput = V0SynthesisOutput(itemMythrilRing1_13519), input = listOf(itemMythrilIngot_653, itemMythrilIngot_653))
        recipes += V0SynthesisRecipe(22, output = V0SynthesisOutput(itemGoldRing_13445), hqOutput = V0SynthesisOutput(itemGoldRing1_13520), input = listOf(itemGoldIngot_745, itemGoldIngot_745))
        recipes += V0SynthesisRecipe(30, output = V0SynthesisOutput(itemPlatinumRing_13447), hqOutput = V0SynthesisOutput(itemPlatinumRing1_13498), input = listOf(itemPlatinumIngot_746, itemPlatinumIngot_746))

        val coloredRockRecipes = listOf(
            itemRedRock_769 to listOf(GemRecipe(itemSardonyx_807, itemSardonyxRing_13444, itemCourageRing_13522), GemRecipe(itemGarnet_790, itemGarnetRing_13477, itemPuissanceRing_13530)),
            itemBlueRock_770 to listOf(GemRecipe(itemLapisLazuli_795, itemLapisLazuliRing_13472, itemTranquilityRing_13525), GemRecipe(itemTurquoise_798, itemTurquoiseRing_13480, itemSolaceRing_13533)),
            itemYellowRock_771 to listOf(GemRecipe(itemAmber_814, itemAmberRing_13473, itemStaminaRing_13526), GemRecipe(itemSphene_815, itemSpheneRing_13481, itemVerveRing_13534)),
            itemGreenRock_772 to listOf(GemRecipe(itemTourmaline_806, itemTourmalineRing_13468, itemReflexRing_13521), GemRecipe(itemPeridot_788, itemPeridotRing_13476, itemAlacrityRing_13529)),
            itemTranslucentRock_773 to listOf(GemRecipe(itemClearTopaz_809, itemClearRing_13470, itemKnowledgeRing_13523), GemRecipe(itemGoshenite_808, itemGosheniteRing_13478, itemWisdomRing_13531)),
            itemPurpleRock_774 to listOf(GemRecipe(itemAmethyst_800, itemAmethystRing_13471, itemBalanceRing_13524), GemRecipe(itemAmetrine_811, itemAmetrineRing_13479, itemDeftRing_13532)),
            itemBlackRock_775 to listOf(GemRecipe(itemOnyx_799, itemOnyxRing_13474, itemEnergyRing_13527), GemRecipe(itemBlackPearl_793, itemBlackRing_13482, itemAuraRing_13535)),
            itemWhiteRock_776 to listOf(GemRecipe(itemLightOpal_796, itemOpalRing_13443, itemHopeRing_13528), GemRecipe(itemPearl_792, itemPearlRing_13483, itemLoyaltyRing_13536)),
        )

        for ((rock, rockRecipes) in coloredRockRecipes) {
            val (t0Gem, t0RingNq, t0RingHq) = rockRecipes[0]
            recipes += V0SynthesisRecipe(5, V0SynthesisOutput(t0Gem), V0SynthesisOutput(t0Gem, 3), listOf(rock, rock, rock, rock))
            recipes += V0SynthesisRecipe(12, V0SynthesisOutput(t0RingNq), V0SynthesisOutput(t0RingHq), listOf(t0Gem, itemSilverIngot_744, itemSilverIngot_744))
        }

        for ((rock, rockRecipes) in coloredRockRecipes) {
            val (t1Gem, t1RingNq, t1RingHq) = rockRecipes[1]
            recipes += V0SynthesisRecipe(13, V0SynthesisOutput(t1Gem), V0SynthesisOutput(t1Gem, 3), listOf(itemGranite_1465, rock, rock, rock))
            recipes += V0SynthesisRecipe(17, V0SynthesisOutput(t1RingNq), V0SynthesisOutput(t1RingHq), listOf(t1Gem, itemMythrilIngot_653, itemMythrilIngot_653))
        }

        val elementalOreRecipes = listOf(
            itemFireOre_1255 to listOf(GemRecipe(itemSunstone_803, itemSunRing_13485, itemVictoryRing_13538), GemRecipe(itemRuby_786, itemRubyRing_13449, itemTriumphRing_13305)),
            itemIceOre_1256 to listOf(GemRecipe(itemZircon_805, itemZirconRing_13486, itemGeniusRing_13539), GemRecipe(itemDiamond_787, itemDiamondRing_13450, itemOmniscientRing_13306)),
            itemWindOre_1257 to listOf(GemRecipe(itemJadeite_784, itemJadeiteRing_13484, itemCelerityRing_13537), GemRecipe(itemEmerald_785, itemEmeraldRing_13448, itemNimbleRing_13304)),
            itemEarthOre_1258 to listOf(GemRecipe(itemChrysoberyl_801, itemChrysoberylRing_13489, itemVigorRing_13542), GemRecipe(itemTopaz_789, itemTopazRing_13453, itemRobustRing_13309)),
            itemLightningOre_1259 to listOf(GemRecipe(itemFluorite_810, itemFluoriteRing_13487, itemGraceRing_13540), GemRecipe(itemSpinel_804, itemSpinelRing_13451, itemAdroitRing_13307)),
            itemWaterOre_1260 to listOf(GemRecipe(itemAquamarine_791, itemAquamarineRing_13488, itemSerenityRing_13541), GemRecipe(itemSapphire_794, itemSapphireRing_13452, itemCommunionRing_13308)),
            itemLightOre_1261 to listOf(GemRecipe(itemMoonstone_802, itemMoonRing_13491, itemAllureRing_13544), GemRecipe(itemAngelstone_813, itemAngelsRing_13463, itemHeavensRing_13311)),
            itemDarkOre_1262 to listOf(GemRecipe(itemPainite_797, itemPainiteRing_13490, itemMysticRing_13543), GemRecipe(itemDeathstone_812, itemDeathRing_13462, itemHadesRing_13310)),
        )

        for ((ore, oreRecipes) in elementalOreRecipes) {
            val (t0Gem, t0RingNq, t0RingHq) = oreRecipes[0]
            recipes += V0SynthesisRecipe(20, V0SynthesisOutput(t0Gem), V0SynthesisOutput(t0Gem, 3), listOf(ore, ore, ore, ore))
            recipes += V0SynthesisRecipe(25, V0SynthesisOutput(t0RingNq), V0SynthesisOutput(t0RingHq), listOf(t0Gem, itemGoldIngot_745, itemGoldIngot_745))
        }

        for ((ore, oreRecipes) in elementalOreRecipes) {
            val (t1Gem, t1RingNq, t1RingHq) = oreRecipes[1]
            recipes += V0SynthesisRecipe(29, V0SynthesisOutput(t1Gem), V0SynthesisOutput(t1Gem, 3), listOf(itemAluminumOre_678, ore, ore, ore))
            recipes += V0SynthesisRecipe(33, V0SynthesisOutput(t1RingNq), V0SynthesisOutput(t1RingHq), listOf(t1Gem, itemPlatinumIngot_746, itemPlatinumIngot_746))
        }

        recipesById = recipes.associateBy { it.getRecipeId() }
    }

    operator fun get(recipeId: RecipeId?): V0SynthesisRecipe? {
        return recipesById[recipeId]
    }

}