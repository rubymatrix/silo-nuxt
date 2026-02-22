package xim.poc.game.configuration

import kotlin.random.Random

class WeightedTable<T>(val entries: List<Pair<T, Double>>) {

    companion object {
        fun <T> single(entry: T): WeightedTable<T> {
            return WeightedTable(Pair(entry, 1.0))
        }

        fun <T> uniform(vararg entries: T): WeightedTable<T> {
            return WeightedTable(entries.map { it to 1.0 })
        }

        fun <T> uniform(entries: List<T>): WeightedTable<T> {
            return WeightedTable(entries.map { it to 1.0 })
        }

        fun <T> grouped(vararg entries: Pair<List<T>, Double>): WeightedTable<T> {
            val out = ArrayList<Pair<T, Double>>()
            for (entry in entries) { entry.first.forEach { out += it to entry.second } }
            return WeightedTable(out)
        }

    }

    constructor(vararg entries: Pair<T, Double>): this(entries.toList())

    private val sum = entries.sumOf { it.second }

    fun getRandom(): T {
        val rand = Random.nextDouble(sum)
        var cumulativeSum = 0.0

        for (entry in entries) {
            cumulativeSum += entry.second
            if (rand < cumulativeSum) { return entry.first }
        }

        return entries.last().first
    }

}