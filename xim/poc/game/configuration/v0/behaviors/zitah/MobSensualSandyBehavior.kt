package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.*
import xim.poc.game.configuration.ActorDamagedContext
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillRancidBreath_3248
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.spellDamageDoT
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.AttackAddedEffectType
import xim.poc.game.event.Event
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MobSensualSandyBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    private var wantsToUseRancidBreath = false

    override fun getSkills(): List<SkillId> {
        return if (wantsToUseRancidBreath) { listOf(mskillRancidBreath_3248) } else { super.getSkills() }
    }

    override fun wantsToUseSkill(): Boolean {
        return wantsToUseRancidBreath || targetIsTerrorized() || actorState.getTpp() > 0.25f
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        return when (skill) {
            mskillRancidBreath_3248 -> 1500.milliseconds - 200.milliseconds * difficulty.value
            else -> null
        }
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify)

        aggregate.storeTp += 33
        aggregate.tpRequirementBypass = wantsToUseRancidBreath || targetIsTerrorized()

        aggregate.autoAttackEffects += AutoAttackEffect(
            effectPower = 0,
            effectType = AttackAddedEffectType.Poison,
            procChance = 5 + 2 * difficulty.value,
            statusEffect = spellDamageDoT(StatusEffect.Poison, potency = 0.5f, duration = 15.seconds),
        )
    }

    override fun onDamaged(context: ActorDamagedContext): List<Event> {
        if (context.skillChainStep is SkillChainStep && !actorState.isOccupied() && !actorState.hasStatusActionLock()) {
            wantsToUseRancidBreath = true
        }

        return super.onDamaged(context)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        wantsToUseRancidBreath = false
        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun onSkillInterrupted(skill: SkillId): List<Event> {
        wantsToUseRancidBreath = false
        return super.onSkillInterrupted(skill)
    }

    private fun targetIsTerrorized(): Boolean {
        val target = ActorStateManager[actorState.getTargetId()] ?: return false
        return target.hasStatusEffect(StatusEffect.Terror)
    }

}