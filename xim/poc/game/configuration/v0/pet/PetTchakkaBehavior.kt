package xim.poc.game.configuration.v0.pet

import xim.poc.game.ActorState
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.SpellSkillId
import xim.poc.game.configuration.constants.mskillCarcharianVerve_2758
import xim.poc.game.configuration.v0.V0AbilityCost
import xim.poc.game.event.Event
import xim.resource.AbilityCost
import xim.resource.AbilityCostType
import xim.util.FrameTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class PetTchakkaBehavior(actorState: ActorState): V0PetBehavior(actorState) {

    private val auraTimer = FrameTimer(90.seconds, initial = ZERO)

    override fun updatePetBehavior(elapsedFrames: Float): List<Event> {
        auraTimer.update(elapsedFrames)

        actorState.appearanceState = if (auraTimer.isNotReady()) { 1 } else { 0 }
        applyAuraBonus()

        return emptyList()
   }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        if (skill != mskillCarcharianVerve_2758) { return super.getSkillCastTimeOverride(skill) }
        return 1.seconds
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId != mskillCarcharianVerve_2758) { return super.getSkillApplierOverride(skillId) }
        return SkillApplier(targetEvaluator = SkillApplierHelper.TargetEvaluator.noop())
    }

    override fun getSkillCostOverride(skill: MobSkillId): V0AbilityCost? {
        if (skill != mskillCarcharianVerve_2758) { return super.getSkillCostOverride(skill) }
        return V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = actorState.getMaxMp()))
    }

    override fun wantsToUseSkill(): Boolean {
        val owner = getOwner() ?: return false
        val castingState = owner.getCastingState() ?: return false

        if (castingState.skill !is SpellSkillId) { return false }
        return castingState.targetId != owner.id
    }

    override fun getSkills(): List<SkillId> {
        return listOf(mskillCarcharianVerve_2758)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        auraTimer.reset()
        return super.onSkillExecuted(primaryTargetContext)
    }

    private fun applyAuraBonus() {
        if (actorState.appearanceState != 1) { return }
        val owner = getOwner() ?: return

        val atkBonus = owner.getOrGainStatusEffect(StatusEffect.AttackBoost2, 3.seconds)
        atkBonus.potency = 5

        val mabBonus = owner.getOrGainStatusEffect(StatusEffect.MagicAtkBoost2, 3.seconds)
        mabBonus.potency = 12
    }

}