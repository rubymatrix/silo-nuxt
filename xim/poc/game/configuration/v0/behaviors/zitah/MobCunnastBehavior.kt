package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillBlazingShriek_2678
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.Event
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MobCunnastBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    private var blazingShriekToggle = false

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.spellInterruptDown += 80
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Bind)
        if (blazingShriekToggle) { aggregate.tpRequirementBypass = true }
    }

    override fun wantsToUseSkill(): Boolean {
        return blazingShriekToggle || super.wantsToUseSkill()
    }

    override fun getSkills(): List<SkillId> {
        return if (blazingShriekToggle) { listOf(mskillBlazingShriek_2678) } else { super.getSkills() }
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        return if (skill == mskillBlazingShriek_2678) { 2.seconds - 200.milliseconds * difficulty.value } else { null }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill is MobSkillId) { blazingShriekToggle = primaryTargetContext.skill != mskillBlazingShriek_2678 }
        return super.onSkillExecuted(primaryTargetContext)
    }

}