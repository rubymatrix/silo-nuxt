package xim.poc.game.configuration.v0.pet

import xim.poc.game.ActorState
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillShiningRuby_652
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0AbilityCost
import xim.poc.game.configuration.v0.V0SpellDefinitions.applyBasicBuff
import xim.poc.game.event.Event
import xim.resource.AbilityCost
import xim.resource.AbilityCostType
import kotlin.time.Duration.Companion.seconds

class PetAkashBehavior(actorState: ActorState): V0PetBehavior(actorState) {

    override fun updatePetBehavior(elapsedFrames: Float): List<Event> {
        return emptyList()
    }

    override fun wantsToUseSkill(): Boolean {
        val owner = getOwner() ?: return false
        return owner.isEngaged() && GameV0Helpers.hasAnyEnmity(owner.id) && !owner.hasStatusEffect(StatusEffect.ShiningRuby)
    }

    override fun getSkills(): List<SkillId> {
        return listOf(mskillShiningRuby_652)
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId != mskillShiningRuby_652) { return null }
        return SkillApplier(targetEvaluator = applyBasicBuff(statusEffect = StatusEffect.ShiningRuby, duration = 60.seconds) { it.status.potency = 10 })
    }

    override fun getSkillCostOverride(skill: MobSkillId): V0AbilityCost? {
        if (skill != mskillShiningRuby_652) { return super.getSkillCostOverride(skill) }
        return V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = actorState.getMaxMp()))
    }

}