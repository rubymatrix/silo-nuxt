package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.ActorState
import xim.poc.game.AutoAttackEffect
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.intPotency
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.AttackAddedEffectType

class MobAglaophotisBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Bind)

        val waterEffectPower = intPotency(actorState, difficulty.value * 0.1f)

        aggregate.haste += 25 + 5 * difficulty.offset
        aggregate.autoAttackEffects += AutoAttackEffect(waterEffectPower, AttackAddedEffectType.Water)
    }

}