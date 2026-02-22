package xim.poc.game.configuration.v0.pet

import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.CombatStat
import xim.poc.game.SkillChainStep
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillRangeInfo
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillFrizz_2871
import xim.poc.game.configuration.v0.SpellDamageCalculator
import xim.poc.game.configuration.v0.V0AbilityCost
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.standardSingleTargetRange
import xim.poc.game.configuration.v0.V0SpellDefinitions
import xim.poc.game.event.Event
import xim.resource.AbilityCost
import xim.resource.AbilityCostType
import xim.resource.SpellElement

class PetSlimeBehavior(actorState: ActorState): V0PetBehavior(actorState) {


    override fun updatePetBehavior(elapsedFrames: Float): List<Event> {
        return emptyList()
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId != mskillFrizz_2871) { return super.getSkillApplierOverride(skillId) }
        return SkillApplier(targetEvaluator = { frizzApplier(it) })
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        if (skill != mskillFrizz_2871) { return super.getSkillRangeOverride(skill) }
        return standardSingleTargetRange
    }

    override fun getSkillCostOverride(skill: MobSkillId): V0AbilityCost? {
        if (skill != mskillFrizz_2871) { return super.getSkillCostOverride(skill) }
        return V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = actorState.getMaxMp()))
    }

    override fun wantsToUseSkill(): Boolean {
        val target = ActorStateManager[actorState.getTargetId()] ?: return false

        val current = target.skillChainTargetState.skillChainState
        if (current !is SkillChainStep) { return false }

        return current.attribute.elements.contains(SpellElement.Fire)
    }

    override fun getSkills(): List<SkillId> {
        return listOf(mskillFrizz_2871)
    }

    private fun frizzApplier(context: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        val owner = getOwner() ?: return emptyList()
        val potency = owner.getMainJobLevel().level/5f

        return V0SpellDefinitions.spellResultToEvents(context, SpellDamageCalculator.computeDamage(
                potencyFn = { potency },
                skill = context.skill,
                attacker = owner,
                defender = context.targetState,
                originalTarget = context.primaryTargetState,
                attackStat = CombatStat.int,
                defendStat = CombatStat.int,
                context = context.context,
                numHits = 1,
            ))
    }

}