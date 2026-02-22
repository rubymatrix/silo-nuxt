package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.seconds

open class MobCloisterAvatarBehavior(actorState: ActorState, val astralFlowSkill: MobSkillId): V0MonsterController(actorState) {

    private var hasUsedAstralFlow = false
    private var astralFlowTimer = FrameTimer(25.seconds)

    override fun onInitialized(): List<Event> {
        spellTimer = FrameTimer(20.seconds)
        return emptyList()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actorState.isEngaged()) { spellTimer.reset() }
        astralFlowTimer.update(elapsedFrames)
        return super.update(elapsedFrames)
    }

    override fun getSkills(): List<SkillId> {
        return if (wantsToAstralFlow()) {
            listOf(astralFlowSkill)
        } else if (hasUsedAstralFlow && astralFlowTimer.isReady()) {
            listOf(astralFlowSkill)
        } else {
            getRegularSkills()
        }
    }

    open fun getRegularSkills(): List<SkillId> {
        return super.getSkills() - astralFlowSkill
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Amnesia)
        aggregate.knockBackResistance += 100
        aggregate.spellInterruptDown += 95
        aggregate.refresh += 10

        val castingState = actorState.getCastingState() ?: return
        if (castingState.skill == astralFlowSkill) { aggregate.fullResist(StatusEffect.Stun) }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == astralFlowSkill) {
            hasUsedAstralFlow = true
            astralFlowTimer.reset()
        }
        return emptyList()
    }

    private fun wantsToAstralFlow(): Boolean {
        return !hasUsedAstralFlow && actorState.getHpp() < 0.5f
    }

}

class MobCloisterFenrirBehavior(actorState: ActorState, astralFlowSkill: MobSkillId): MobCloisterAvatarBehavior(actorState, astralFlowSkill) {

    private val skillGroupings = listOf(
        listOf(mskillMoonlitCharge_575, mskillCrescentFang_576, mskillEclipseBite_580),
        listOf(mskillLunarCry_577, mskillLunarRoar_579, mskillEclipticGrowl_578, mskillEclipticHowl_581),
        listOf(mskillLunarBay_3295, mskillImpact_3296),
    )

    private var selector = 0

    override fun getRegularSkills(): List<SkillId> {
        return skillGroupings[selector++ % skillGroupings.size]
    }

}