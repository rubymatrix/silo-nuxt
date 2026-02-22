package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.escha.EschaDifficulty

class MobFerrodonBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.spellInterruptDown += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify)
        aggregate.autoAttackScale = 1f + (1f - actorState.getHpp()) * (0.1f * difficulty.value)
    }

}