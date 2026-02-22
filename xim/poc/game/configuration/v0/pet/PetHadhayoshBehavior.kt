package xim.poc.game.configuration.v0.pet

import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillRangeInfo
import xim.poc.game.configuration.SkillTargetEvaluator
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillThunderbolt_373
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.V0AbilityCost
import xim.poc.game.configuration.v0.V0MobSkillDefinitions
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.basicDebuff
import xim.poc.game.event.Event
import xim.resource.AbilityCost
import xim.resource.AbilityCostType
import xim.resource.AoeType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PetHadhayoshBehavior(actorState: ActorState) : V0PetBehavior(actorState) {

    private val thunderboltCastTime = 0.25.seconds

    override fun updatePetBehavior(elapsedFrames: Float): List<Event> {
        return emptyList()
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        if (skill != mskillThunderbolt_373) { return super.getSkillCastTimeOverride(skill) }
        return thunderboltCastTime
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId != mskillThunderbolt_373) { return super.getSkillApplierOverride(skillId) }
        return SkillApplier(targetEvaluator = basicDebuff(statusEffect = StatusEffect.Stun, duration = 6.seconds))
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        if (skill != mskillThunderbolt_373) { return super.getSkillRangeOverride(skill) }
        return V0MobSkillDefinitions.standardSingleTargetRange
    }

    override fun getSkillCostOverride(skill: MobSkillId): V0AbilityCost? {
        if (skill != mskillThunderbolt_373) { return super.getSkillCostOverride(skill) }
        return V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = actorState.getMaxMp()))
    }

    override fun wantsToUseSkill(): Boolean {
        val owner = getOwner() ?: return false
        val target = ActorStateManager[actorState.getTargetId()] ?: return false

        val castingState = target.getCastingState() ?: return false
        if (!castingState.isCharging()) { return false }

        val castingTarget = ActorStateManager[castingState.targetId] ?: return false
        if (castingTarget.id != owner.id) { return false }

        val remaining = castingState.remainingChargeTime()
        if (remaining < thunderboltCastTime || remaining > thunderboltCastTime * 1.5) { return false }

        val rangeInfo = GameV0.getSkillRangeInfo(target, castingState.skill)
        if (rangeInfo.type == AoeType.None) { return true }

        return SkillTargetEvaluator.isInEffectRange(
            rangeInfo = rangeInfo,
            source = target,
            primaryTarget = castingTarget,
            additionalTarget = owner,
        )
    }

    override fun getSkills(): List<SkillId> {
        return listOf(mskillThunderbolt_373)
    }

}