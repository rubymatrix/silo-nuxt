package xim.poc.game.configuration.v0.pet

import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillRangeInfo
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillDroningWhirlwind_2749
import xim.poc.game.configuration.v0.V0AbilityCost
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.basicDebuff
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.standardSingleTargetRange
import xim.poc.game.event.AttackEffects
import xim.poc.game.event.Event
import xim.resource.AbilityCost
import xim.resource.AbilityCostType
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class PetColkhabBehavior(actorState: ActorState): V0PetBehavior(actorState) {

    private val auraTimer = FrameTimer(30.seconds, initial = ZERO)

    override fun updatePetBehavior(elapsedFrames: Float): List<Event> {
        auraTimer.update(elapsedFrames)

        actorState.appearanceState = if (auraTimer.isNotReady()) { 1 } else { 0 }
        applyAuraBonus()

        return emptyList()
   }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId != mskillDroningWhirlwind_2749) { return super.getSkillApplierOverride(skillId) }
        return SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(dispelCount = 1)))
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        if (skill != mskillDroningWhirlwind_2749) { return super.getSkillRangeOverride(skill) }
        return standardSingleTargetRange
    }

    override fun getSkillCostOverride(skill: MobSkillId): V0AbilityCost? {
        if (skill != mskillDroningWhirlwind_2749) { return super.getSkillCostOverride(skill) }
        return V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = actorState.getMaxMp()))
    }

    override fun wantsToUseSkill(): Boolean {
        val target = ActorStateManager[actorState.getTargetId()] ?: return false
        return target.getStatusEffects().any { it.statusEffect.buff && it.canDispel }
    }

    override fun getSkills(): List<SkillId> {
        return listOf(mskillDroningWhirlwind_2749)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        auraTimer.reset()
        return super.onSkillExecuted(primaryTargetContext)
    }

    private fun applyAuraBonus() {
        if (actorState.appearanceState != 1) { return }
        val owner = getOwner() ?: return

        val status = owner.getOrGainStatusEffect(StatusEffect.EvasionBoost, 3.seconds)
        status.potency = 10
    }

}