package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.v0.V0MonsterDefinitions
import xim.poc.game.event.Event

class FamilyGnoleBehavior(actorState: ActorState): V0MonsterController(actorState) {

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isIdleOrEngaged()) { actorState.appearanceState = getDesiredAppearanceState() }
        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        if (actorState.appearanceState == 1) {
            aggregate.autoAttackScale = 0.5f
            aggregate.haste += 50
        }
    }

    private fun getDesiredAppearanceState(): Int {
        return if (actorState.getHpp() < 0.5f) { 1 } else { 0 }
    }

}