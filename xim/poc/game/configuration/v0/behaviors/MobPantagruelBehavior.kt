package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillIceRoar_407
import xim.poc.game.configuration.constants.mskillMoribundHack_2111
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.intPotency
import xim.poc.game.event.Event
import kotlin.random.Random

class MobPantagruelBehavior(actorState: ActorState): V0MonsterController(actorState) {

    override fun getSkills(): List<SkillId> {
        return if (Random.nextBoolean() && actorState.hasStatusEffect(StatusEffect.IceSpikes)) {
            listOf(mskillMoribundHack_2111)
        } else {
            super.getSkills()
        }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillIceRoar_407) {
            val statusEffect = actorState.gainStatusEffect(StatusEffect.IceSpikes)
            statusEffect.potency = intPotency(actorState, 0.1f)
            statusEffect.secondaryPotency = 1f
            statusEffect.canDispel = false
        } else if (primaryTargetContext.skill == mskillMoribundHack_2111) {
            actorState.expireStatusEffect(StatusEffect.IceSpikes)
        }

        return emptyList()
    }

}