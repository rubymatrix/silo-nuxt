package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.StatusEffect
import xim.poc.game.event.Event

class FamilyAhrimanBehavior(actorState: ActorState): V0MonsterController(actorState) {

    override fun onReadyToAutoAttack(): List<Event>? {
        return if (isTargetLocked()) { emptyList() } else { super.onReadyToAutoAttack() }
    }

    override fun wantsToCastSpell(): Boolean {
        return if (actorState.isIdle()) { super.wantsToCastSpell() } else { isTargetLocked() }
    }

    private fun isTargetLocked(): Boolean {
        val target = ActorStateManager[actorState.getTargetId()] ?: return false
        return target.hasStatusEffect(StatusEffect.Sleep) || target.hasStatusEffect(StatusEffect.Petrify) || target.hasStatusEffect(StatusEffect.Bind)
    }

}