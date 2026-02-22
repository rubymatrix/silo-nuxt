package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.*
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillRangeInfo
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.extendedSourceRange
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.Event
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class MobLustfulLydiaBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    private val skillQueue = ArrayDeque<MobSkillId>()

    override fun update(elapsedFrames: Float): List<Event> {
        if (skillQueue.isEmpty()) {
            skillQueue += listOf(mskillImpale_60, mskillVampiricLash_61).shuffled()
            skillQueue += mskillSweetBreath_64
            skillQueue += mskillBadBreath_63
        }

        return super.update(elapsedFrames)
    }

    override fun getSkills(): List<SkillId> {
        return listOf(skillQueue.first())
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        skillQueue.removeFirstOrNull()
        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun onSkillInterrupted(skill: SkillId): List<Event> {
        if (skill == mskillSweetBreath_64 && actorState.hasStatusEffect(StatusEffect.Stun)) {
            MiscEffects.playExclamationProc(actorState.id, ExclamationProc.Blue)
            actorState.frozenTimer.reset(10.seconds - 1.seconds * difficulty.value)
            skillQueue.clear()
        }

        return super.onSkillInterrupted(skill)
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        return if (skill == mskillBadBreath_63) { ZERO } else { null }
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        return if (skill == mskillBadBreath_63) { return extendedSourceRange } else { null }
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify)

        aggregate.storeTp += 5 * difficulty.value
        aggregate.haste += 4 * difficulty.value
    }

}