package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.*
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.AttackRetaliationEffectType
import xim.poc.game.event.AttackStatusEffect
import xim.poc.game.event.Event
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds


class MobTangataManuBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.spellInterruptDown += 80
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify)

        aggregate.haste += 3 * difficulty.value

        if (actorState.hasStatusEffect(StatusEffect.GaleSpikes)) {
            val spikePower = ((0.06 * difficulty.value) * actorState.combatStats.mnd).roundToInt()
            val spikeSlowPotency = 10 * difficulty.value

            aggregate.autoAttackRetaliationEffects += RetaliationBonus(
                power = spikePower,
                type = AttackRetaliationEffectType.WindSpikes,
                statusEffect = AttackStatusEffect(StatusEffect.Slow, baseDuration = 8.seconds, baseChance = 1f) { it.statusState.potency = spikeSlowPotency }
            )
        }
    }

    override fun onSkillBeginCharging(castingState: CastingState): List<Event> {
        val autoAttackSkills = MonsterDefinitions[actorState.monsterId]?.autoAttackSkills ?: emptyList()
        if (autoAttackSkills.contains(castingState.skill)) { return emptyList() }

        val spikes = actorState.getOrGainStatusEffect(StatusEffect.GaleSpikes, (3 + difficulty.value).seconds)
        spikes.canDispel = false

        return emptyList()
    }

}