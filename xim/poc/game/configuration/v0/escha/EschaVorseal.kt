package xim.poc.game.configuration.v0.escha

import xim.poc.game.CombatBonusAggregate
import xim.poc.game.CombatStat
import xim.poc.game.configuration.MonsterId
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.escha.EschaDifficulty.*
import xim.poc.ui.ShiftJis
import xim.util.addInPlace
import xim.util.multiplyInPlace
import kotlin.math.pow

value class EschaVorsealId(val id: Int)

class EschaVorseal(
    val id: EschaVorsealId,
    val displayName: String,
    val bonusApplier: (Int, CombatBonusAggregate) -> Unit,
)

class EschaVorsealReward(val vorseals: Map<EschaDifficulty, EschaVorsealId>)

class EschaVorsealConfiguration(private val vorseals: Map<EschaVorsealId, EschaVorseal>) {
    operator fun get(vorsealId: EschaVorsealId): EschaVorseal {
        return vorseals[vorsealId] ?: throw IllegalStateException("Undefined vorseal: $vorsealId")
    }
}

object EschaZiTahVorseals {

    private val vorseals = HashMap<EschaVorsealId, EschaVorseal>()
    private var idCounter = 1

    private val statBoostVorseals = CombatStat.values().associateWith {
        EschaVorseal(id = nextId(), displayName = "${it.displayName}${ShiftJis.multiplyX}1.05") {
            level, aggregate -> aggregate.multiplicativeStats.multiplyInPlace(it, 1.05f.pow(level))
        }.also { vorseal -> vorseals[vorseal.id] = vorseal }
    }

    val tripleAttackVorseal = EschaVorseal(id = nextId(), displayName = "Triple Attack +8%") { level, aggregate ->
        aggregate.tripleAttack += 8 * level
    }.also { vorseals[it.id] = it }

    val storeTpVorseal = EschaVorseal(id = nextId(), displayName = "Store TP +20") { level, aggregate ->
        aggregate.storeTp += 20 * level
    }.also { vorseals[it.id] = it }

    val movementSpeedVorseal = EschaVorseal(id = nextId(), displayName = "Movement Speed +8%") { level, aggregate ->
        aggregate.movementSpeed += 8 * level
    }.also { vorseals[it.id] = it }

    val hasteVorseal = EschaVorseal(id = nextId(), displayName = "Haste +10%") { level, aggregate ->
        aggregate.haste += 10 * level
    }.also { vorseals[it.id] = it }

    val fastCastVorseal = EschaVorseal(id = nextId(), displayName = "Fast Cast +15%") { level, aggregate ->
        aggregate.fastCast += 15 * level
    }.also { vorseals[it.id] = it }

    val magicAttackBonusVorseal = EschaVorseal(id = nextId(), displayName = "Magic Attack Bonus +20") { level, aggregate ->
        aggregate.magicAttackBonus += 20 * level
    }.also { vorseals[it.id] = it }

    val magicBurstDamageVorseal = EschaVorseal(id = nextId(), displayName = "Magic Burst Damage +20") { level, aggregate ->
        aggregate.magicBurstDamage += 20 * level
    }.also { vorseals[it.id] = it }

    val curePotencyVorseal = EschaVorseal(id = nextId(), displayName = "Cure Potency +10%") { level, aggregate ->
        aggregate.curePotency += 10 * level
    }.also { vorseals[it.id] = it }

    val evasionVorseal = EschaVorseal(id = nextId(), displayName = "Evasion +5%") { level, aggregate ->
        aggregate.evasionRate += 5 * level
    }.also { vorseals[it.id] = it }

    val weaponSkillDamageVorseal = EschaVorseal(id = nextId(), displayName = "Weapon Skill Damage +15%") { level, aggregate ->
        aggregate.weaponSkillDamage += 15 * level
    }.also { vorseals[it.id] = it }

    val skillChainDamageVorseal = EschaVorseal(id = nextId(), displayName = "Skillchain Damage +15%") { level, aggregate ->
        aggregate.skillChainDamage += 15 * level
    }.also { vorseals[it.id] = it }

    val subtleBlowVorseal = EschaVorseal(id = nextId(), displayName = "Subtle Blow +20") { level, aggregate ->
        aggregate.subtleBlow += 20 * level
    }.also { vorseals[it.id] = it }

    val regenVorseal = EschaVorseal(id = nextId(), displayName = "Regen +15") { level, aggregate ->
        aggregate.regen += 15 * level
    }.also { vorseals[it.id] = it }

    val criticalHitRateVorseal = EschaVorseal(id = nextId(), displayName = "Critical Hit Rate +10%") { level, aggregate ->
        aggregate.criticalHitRate += 10 * level
    }.also { vorseals[it.id] = it }

    val conserveTpVorseal = EschaVorseal(id = nextId(), displayName = "Conserve TP +20") { level, aggregate ->
        aggregate.conserveTp += 20 * level
    }.also { vorseals[it.id] = it }

    val conserveMpVorseal = EschaVorseal(id = nextId(), displayName = "Conserve MP +20") { level, aggregate ->
        aggregate.conserveMp += 20 * level
    }.also { vorseals[it.id] = it }

    val criticalHitDamageVorseal = EschaVorseal(id = nextId(), displayName = "Critical Hit Damage +10%") { level, aggregate ->
        aggregate.criticalHitDamage += 10 * level
    }.also { vorseals[it.id] = it }

    val resistStatusVorseal = EschaVorseal(id = nextId(), displayName = "Resist Status Ailments +10") { level, aggregate ->
        aggregate.allStatusResistRate += 10 * level
    }.also { vorseals[it.id] = it }

    fun getConfiguration(): EschaVorsealConfiguration {
        return EschaVorsealConfiguration(vorseals)
    }

    operator fun get(combatStat: CombatStat): EschaVorsealId {
        return statBoostVorseals[combatStat]?.id ?: throw IllegalStateException("No such vorseal for $combatStat")
    }

    private fun nextId(): EschaVorsealId {
        return EschaVorsealId(idCounter++)
    }

}

object EschaVorsealBonusApplier {

    fun hasClearedDifficulty(monsterId: MonsterId, eschaDifficulty: EschaDifficulty): Boolean {
        return getClearedDifficulties(monsterId).contains(eschaDifficulty)
    }

    fun getClearedDifficulties(monsterId: MonsterId): Set<EschaDifficulty> {
        return if (GameV0Helpers.hasDefeated(monsterId + S5)) {
            setOf(S1, S2, S3, S4, S5)
        } else if (GameV0Helpers.hasDefeated(monsterId + S4)) {
            setOf(S1, S2, S3, S4)
        } else if (GameV0Helpers.hasDefeated(monsterId + S3)) {
            setOf(S1, S2, S3)
        } else if (GameV0Helpers.hasDefeated(monsterId + S2)) {
            setOf(S1, S2)
        } else if (GameV0Helpers.hasDefeated(monsterId + S1)) {
            setOf(S1)
        } else {
            emptySet()
        }
    }

    fun compute(zoneConfiguration: EschaConfiguration, aggregate: CombatBonusAggregate) {
        val vorsealLevels = HashMap<EschaVorsealId, Int>()

        for ((monsterId, rewards) in zoneConfiguration.vorsealRewards) {
            val clearedDifficulties = getClearedDifficulties(monsterId)

            for ((difficulty, vorsealId) in rewards.vorseals) {
                if (!clearedDifficulties.contains(difficulty)) { continue }
                vorsealLevels.addInPlace(vorsealId, 1)
            }
        }

        for ((vorsealId, vorsealLevel) in vorsealLevels) {
            val vorseal = zoneConfiguration.vorsealConfiguration[vorsealId]
            vorseal.bonusApplier.invoke(vorsealLevel, aggregate)
        }
    }

}