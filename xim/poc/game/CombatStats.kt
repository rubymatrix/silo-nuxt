package xim.poc.game

import kotlin.math.roundToInt

enum class CombatStat(val displayName: String) {
    maxHp("HP"),
    maxMp("MP"),
    str("STR"),
    dex("DEX"),
    vit("VIT"),
    agi("AGI"),
    int("INT"),
    mnd("MND"),
    chr("CHR"),
    ;
}

data class CombatStatBuilder(
    var maxHp: Int = 0,
    var maxMp: Int = 0,
    var str: Int = 0,
    var dex: Int = 0,
    var vit: Int = 0,
    var agi: Int = 0,
    var int: Int = 0,
    var mnd: Int = 0,
    var chr: Int = 0,
) {

    fun build(): CombatStats {
        return CombatStats(
            maxHp = maxHp,
            maxMp = maxMp,
            str = str,
            dex = dex,
            vit = vit,
            agi = agi,
            int = int,
            mnd = mnd,
            chr = chr,
        )
    }

    operator fun set(combatStat: CombatStat, value: Int) {
        when (combatStat) {
            CombatStat.maxHp -> maxHp = value
            CombatStat.maxMp -> maxMp = value
            CombatStat.str -> str = value
            CombatStat.dex -> dex = value
            CombatStat.vit -> vit = value
            CombatStat.agi -> agi = value
            CombatStat.int -> int = value
            CombatStat.mnd -> mnd = value
            CombatStat.chr -> chr = value
        }
    }

    fun add(combatStat: CombatStat, value: Int) {
        when (combatStat) {
            CombatStat.maxHp -> maxHp += value
            CombatStat.maxMp -> maxMp += value
            CombatStat.str -> str += value
            CombatStat.dex -> dex += value
            CombatStat.vit -> vit += value
            CombatStat.agi -> agi += value
            CombatStat.int -> int += value
            CombatStat.mnd -> mnd += value
            CombatStat.chr -> chr += value
        }
    }

    fun add(combatStats: CombatStatBuilder) {
        maxHp += combatStats.maxHp
        maxMp += combatStats.maxMp
        str += combatStats.str
        dex += combatStats.dex
        vit += combatStats.vit
        agi += combatStats.agi
        int += combatStats.int
        mnd += combatStats.mnd
        chr += combatStats.chr
    }

    fun add(combatStats: CombatStats) {
        maxHp += combatStats.maxHp
        maxMp += combatStats.maxMp
        str += combatStats.str
        dex += combatStats.dex
        vit += combatStats.vit
        agi += combatStats.agi
        int += combatStats.int
        mnd += combatStats.mnd
        chr += combatStats.chr
    }

}

data class CombatStats(
    val maxHp: Int = 0,
    val maxMp: Int = 0,
    val str: Int = 0,
    val dex: Int = 0,
    val vit: Int = 0,
    val agi: Int = 0,
    val int: Int = 0,
    val mnd: Int = 0,
    val chr: Int = 0,
) {

    companion object {
        val defaultBaseStats = CombatStats(20, 20, 5, 5, 5, 5, 5, 5, 5)
    }

    operator fun get(combatStat: CombatStat): Int {
        return when (combatStat) {
            CombatStat.maxHp -> maxHp
            CombatStat.maxMp -> maxMp
            CombatStat.str -> str
            CombatStat.dex -> dex
            CombatStat.vit -> vit
            CombatStat.agi -> agi
            CombatStat.int -> int
            CombatStat.mnd -> mnd
            CombatStat.chr -> chr
        }
    }

    operator fun times(c: Float): CombatStats {
        return CombatStats(
            (maxHp * c).roundToInt(),
            (maxMp * c).roundToInt(),
            (str * c).roundToInt(),
            (dex * c).roundToInt(),
            (vit * c).roundToInt(),
            (agi * c).roundToInt(),
            (int * c).roundToInt(),
            (mnd * c).roundToInt(),
            (chr * c).roundToInt(),
        )
    }

    operator fun plus(c: CombatStats): CombatStats {
        return CombatStats(
            maxHp + c.maxHp,
            maxMp + c.maxMp,
            str + c.str,
            dex + c.dex,
            vit + c.vit,
            agi + c.agi,
            int + c.int,
            mnd + c.mnd,
            chr + c.chr,
        )
    }

    fun add(combatStat: CombatStat, value: Int): CombatStats {
        return when (combatStat) {
            CombatStat.maxHp -> copy(maxHp = maxHp + value)
            CombatStat.maxMp -> copy(maxMp = maxMp + value)
            CombatStat.str -> copy(str = str + value)
            CombatStat.dex -> copy(dex = dex + value)
            CombatStat.vit -> copy(vit = vit + value)
            CombatStat.agi -> copy(agi = agi + value)
            CombatStat.int -> copy(int = int + value)
            CombatStat.mnd -> copy(mnd = mnd + value)
            CombatStat.chr -> copy(chr = chr + value)
        }
    }

    fun multiply(combatStat: CombatStat, value: Float): CombatStats {
        return when (combatStat) {
            CombatStat.maxHp -> copy(maxHp = (maxHp * value).roundToInt())
            CombatStat.maxMp -> copy(maxMp = (maxMp * value).roundToInt())
            CombatStat.str -> copy(str = (str * value).roundToInt())
            CombatStat.dex -> copy(dex = (dex * value).roundToInt())
            CombatStat.vit -> copy(vit = (vit * value).roundToInt())
            CombatStat.agi -> copy(agi = (agi * value).roundToInt())
            CombatStat.int -> copy(int = (int * value).roundToInt())
            CombatStat.mnd -> copy(mnd = (mnd * value).roundToInt())
            CombatStat.chr -> copy(chr = (chr * value).roundToInt())
        }
    }

    fun multiply(combatStats: Map<CombatStat, Float>): CombatStats {
        return copy(
            maxHp = (maxHp * combatStats.getOrElse(CombatStat.maxHp) { 1f } ).roundToInt(),
            maxMp = (maxMp * combatStats.getOrElse(CombatStat.maxMp) { 1f } ).roundToInt(),
            str = (str * combatStats.getOrElse(CombatStat.str) { 1f } ).roundToInt(),
            dex = (dex * combatStats.getOrElse(CombatStat.dex) { 1f } ).roundToInt(),
            vit = (vit * combatStats.getOrElse(CombatStat.vit) { 1f } ).roundToInt(),
            agi = (agi * combatStats.getOrElse(CombatStat.agi) { 1f } ).roundToInt(),
            int = (int * combatStats.getOrElse(CombatStat.int) { 1f } ).roundToInt(),
            mnd = (mnd * combatStats.getOrElse(CombatStat.mnd) { 1f } ).roundToInt(),
            chr = (chr * combatStats.getOrElse(CombatStat.chr) { 1f } ).roundToInt(),
        )
    }

}
