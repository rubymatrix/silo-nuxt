package xim.poc.game.configuration.v0.pet

import xim.poc.game.ActorState
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillRangeInfo
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillUproot_2803
import xim.poc.game.configuration.v0.V0AbilityCost
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.standardSingleTargetRange
import xim.poc.game.event.Event
import xim.resource.AbilityCost
import xim.resource.AbilityCostType
import xim.util.FrameTimer
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class PetYumcaxBehavior(actorState: ActorState): V0PetBehavior(actorState) {

    private val auraTimer = FrameTimer(30.seconds, initial = ZERO)

    override fun updatePetBehavior(elapsedFrames: Float): List<Event> {
        auraTimer.update(elapsedFrames)

        actorState.appearanceState = if (auraTimer.isNotReady()) { 1 } else { 0 }
        applyAuraBonus()

        return emptyList()
   }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId != mskillUproot_2803) { return super.getSkillApplierOverride(skillId) }
        return SkillApplier(
            additionalSelfEvaluator = {
                val owner = getOwner() ?: return@SkillApplier emptyList()
                owner.getStatusEffects()
                    .filter { se -> se.statusEffect.debuff && (se.canErase || se.canEsuna) }
                    .forEach { se -> owner.expireStatusEffect(se.statusEffect) }

                emptyList()
            },
            targetEvaluator = SkillApplierHelper.TargetEvaluator.noop()
        )
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        if (skill != mskillUproot_2803) { return super.getSkillRangeOverride(skill) }
        return standardSingleTargetRange
    }

    override fun getSkillCostOverride(skill: MobSkillId): V0AbilityCost? {
        if (skill != mskillUproot_2803) { return super.getSkillCostOverride(skill) }
        return V0AbilityCost(baseCost = AbilityCost(type = AbilityCostType.Mp, value = actorState.getMaxMp()))
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        if (skill != mskillUproot_2803) { return super.getSkillCastTimeOverride(skill) }
        return 1.seconds
    }

    override fun wantsToUseSkill(): Boolean {
        val owner = getOwner() ?: return false
        return owner.getStatusEffects().any { it.statusEffect.debuff && (it.canErase || it.canEsuna) }
    }

    override fun getSkills(): List<SkillId> {
        return listOf(mskillUproot_2803)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        auraTimer.reset()
        return super.onSkillExecuted(primaryTargetContext)
    }

    private fun applyAuraBonus() {
        if (actorState.appearanceState != 1) { return }
        val owner = getOwner() ?: return
        val status = owner.getOrGainStatusEffect(StatusEffect.Regen2, duration = 3.seconds)
        status.potency = (owner.getMaxHp() * 0.03).roundToInt()
    }

}