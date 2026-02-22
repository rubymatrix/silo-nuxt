package xim.poc.game.configuration.v0

import xim.util.RandHelper
import kotlin.math.roundToInt
import kotlin.random.Random

sealed interface ItemRankDistribution {
    fun getRank(): Int
}

class ItemRankFixed(val value: Int): ItemRankDistribution {
    override fun getRank(): Int {
        return value
    }
}

class ItemRankNormalDistribution(val meanRank: Int, val maxRank: Int, val sd: Float = 10f): ItemRankDistribution {
    override fun getRank(): Int {
        var bonus = -1
        while (bonus < 0) { bonus = RandHelper.normal(sd).roundToInt() }

        bonus += meanRank
        return bonus.coerceIn(1, maxRank)
    }
}

class ItemRankUniformDistribution(val minRank: Int, val maxRank: Int): ItemRankDistribution {
    override fun getRank(): Int {
        return Random.nextInt(minRank, maxRank + 1)
    }
}