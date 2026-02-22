package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.AttackContext
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillNumbshroom_55
import xim.poc.game.configuration.constants.mskillQueasyshroom_54
import xim.poc.game.configuration.constants.mskillShakeshroom_56
import xim.poc.game.event.Event
import kotlin.random.Random

class FamilyFunguarBehavior(actorState: ActorState): V0MonsterController(actorState) {

    override fun getSkills(): List<SkillId> {
        val stateSkill =  when(actorState.appearanceState) {
            0 -> mskillQueasyshroom_54
            1 -> mskillNumbshroom_55
            2 -> mskillShakeshroom_56
            else -> null
        }

        return if (stateSkill != null && Random.nextBoolean()) { listOf(stateSkill) } else { super.getSkills() }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        val resultingAppearanceState = when (primaryTargetContext.skill) {
            mskillQueasyshroom_54 -> 1
            mskillNumbshroom_55 -> 2
            mskillShakeshroom_56 -> 3
            else -> null
        }

        if (resultingAppearanceState != null) {
            AttackContext.compose(primaryTargetContext.context) { actorState.appearanceState = resultingAppearanceState }
        }

        return super.onSkillExecuted(primaryTargetContext)
    }

}