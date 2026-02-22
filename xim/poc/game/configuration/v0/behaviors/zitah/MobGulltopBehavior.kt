package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.*
import xim.poc.game.configuration.ActorDamagedContext
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.Event
import kotlin.time.Duration.Companion.seconds

class MobGulltopBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    private var evasionBonus = 0
    private var weightAuraEnabled = false

    override fun update(elapsedFrames: Float): List<Event> {
        if (weightAuraEnabled) { applyWeightToTarget() }
        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.spellInterruptDown += 90
        aggregate.knockBackResistance += 100

        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Silence)

        aggregate.refresh += 10
        aggregate.evasionRate += (5 * evasionBonus * difficulty.value).coerceAtMost(80)

        if (targetIsSleeping()) { aggregate.tpRequirementBypass = true }
    }

    override fun getSkills(): List<SkillId> {
        return if (targetIsSleeping()) { listOf(mskillRhinowrecker_2567) } else { super.getSkills() }
    }

    override fun wantsToUseSkill(): Boolean {
        return targetIsSleeping() || super.wantsToUseSkill()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillHiFreqField_83) { weightAuraEnabled = true }
        if (primaryTargetContext.skill is MobSkillId) { evasionBonus += 1 }
        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun onDamaged(context: ActorDamagedContext): List<Event> {
        val casting = actorState.getCastingState() ?: return super.onDamaged(context)

        if (!casting.isCharging()) { return super.onDamaged(context) }
        var interrupted = false

        if (casting.skill is SpellSkillId && context.skill is AbilitySkillId && weightAuraEnabled) {
            interrupted = true
            weightAuraEnabled = false
            MiscEffects.playExclamationProc(actorState.id, ExclamationProc.Blue)
        } else if (casting.skill is MobSkillId && context.skill is SpellSkillId && evasionBonus > 0) {
            interrupted = true
            evasionBonus = 0
            MiscEffects.playExclamationProc(actorState.id, ExclamationProc.Red)
        }

        if (interrupted) {
            casting.result = CastingInterrupted
            actorState.frozenTimer.reset(3.seconds)
        }

        return super.onDamaged(context)
    }

    private fun applyWeightToTarget() {
        val target = ActorStateManager[actorState.getTargetId()] ?: return
        if (actorState.getTargetingDistance(target) > 5f) { return }

        val debuff = target.getOrGainStatusEffect(StatusEffect.Weight, 1.seconds)
        debuff.potency = 10 * difficulty.value
    }

    private fun targetIsSleeping(): Boolean {
        val target = ActorStateManager[actorState.getTargetId()] ?: return false
        return target.hasStatusEffect(StatusEffect.Sleep)
    }

}