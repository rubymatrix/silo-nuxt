package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.mskillLightningRoar_406
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.intPotency
import xim.poc.game.event.Event
import kotlin.time.Duration.Companion.seconds

class MobGrandgousierController(actorState: ActorState): V0MonsterController(actorState) {

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill != mskillLightningRoar_406) { return emptyList() }

        val status = actorState.gainStatusEffect(StatusEffect.ShockSpikes, duration = 15.seconds)
        status.potency = intPotency(actorState, 0.05f)
        status.secondaryPotency = 1f
        status.canDispel = false

        return emptyList()
    }

}