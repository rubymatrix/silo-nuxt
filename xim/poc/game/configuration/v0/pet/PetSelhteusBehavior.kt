package xim.poc.game.configuration.v0.pet

import xim.poc.game.ActorState
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillRejuvenation_3366
import xim.poc.game.configuration.v0.V0AbilityCost
import xim.poc.game.configuration.v0.V0SpellDefinitions.targetPercentageHealingMagic
import xim.poc.game.event.Event
import xim.resource.AbilityCost
import xim.resource.AbilityCostType

class PetSelhteusBehavior(actorState: ActorState) : V0PetBehavior(actorState) {

    override fun updatePetBehavior(elapsedFrames: Float): List<Event> {
        return emptyList()
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId != mskillRejuvenation_3366) { return super.getSkillApplierOverride(skillId) }
        return SkillApplier(targetEvaluator = targetPercentageHealingMagic(percent = 50f))
    }

    override fun getSkillCostOverride(skill: MobSkillId): V0AbilityCost? {
        if (skill != mskillRejuvenation_3366) {
            return super.getSkillCostOverride(skill)
        }
        return V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = actorState.getMaxMp()))
    }

    override fun wantsToUseSkill(): Boolean {
        val owner = getOwner() ?: return false
        return owner.isEngaged() && owner.getHpp() < 0.5f
    }

    override fun getSkills(): List<SkillId> {
        return listOf(mskillRejuvenation_3366)
    }

}