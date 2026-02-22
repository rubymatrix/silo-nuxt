package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.ActorDamagedContext
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillDeathgnash_1721
import xim.poc.game.configuration.constants.mskillMayhemLantern_2383
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.basicDebuff
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.spellDamageDoT
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.AttackEffects
import xim.poc.game.event.AttackStatusEffect
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MobKamohoaliiBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    private val breakThreshold = 50 + 10 * difficulty.value

    private val regenTimer = FrameTimer(30.seconds)
    private val mayhemTimer = FrameTimer(60.seconds)

    override fun onInitialized(): List<Event> {
        restoreLanterns()
        return super.onInitialized()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        if (lanternsAreBroken()) {
            regenTimer.update(elapsedFrames)
        } else {
            mayhemTimer.update(elapsedFrames)
        }

        if (!actorState.isOccupied() && !actorState.isDead()) {
            if (regenTimer.isReady()) {
                restoreLanterns()
            } else if (shouldBreakLanterns()) {
                breakLanterns()
            }
        }

        return super.update(elapsedFrames)
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        return when (skill) {
            mskillMayhemLantern_2383 -> Duration.ZERO
            else -> null
        }
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId != mskillMayhemLantern_2383) { return super.getSkillApplierOverride(skillId) }

        val duration = 9.seconds + 3.seconds * difficulty.offset

        return SkillApplier(targetEvaluator = basicDebuff(attackEffects = AttackEffects(
            attackStatusEffects = listOf(
                spellDamageDoT(statusEffect = StatusEffect.Shock, potency = 1f, duration = duration),
                AttackStatusEffect(statusEffect = StatusEffect.Sleep, baseDuration = duration) { it.statusState.counter = 1 },
            ))
        ))
    }

    override fun getSkills(): List<SkillId> {
        return if (mayhemTimer.isReady()) {
            listOf(mskillMayhemLantern_2383)
        } else {
            super.getSkills() + mskillDeathgnash_1721
        }
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify)
    }

    override fun onDamaged(context: ActorDamagedContext): List<Event> {
        if (context.skill == null && context.skillChainStep == null) {
            actorState.consumeStatusEffectCharge(StatusEffect.Enchantment)
        }
        return emptyList()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        when (primaryTargetContext.skill) {
            mskillMayhemLantern_2383 -> mayhemTimer.reset()
            else -> Unit
        }

        return emptyList()
    }

    private fun lanternsAreBroken(): Boolean {
        return actorState.appearanceState == 1
    }

    private fun shouldBreakLanterns(): Boolean {
        return !lanternsAreBroken() && !actorState.hasStatusEffect(StatusEffect.Enchantment)
    }

    private fun breakLanterns() {
        actorState.appearanceState = 1
        mayhemTimer.reset()
        regenTimer.reset()
    }

    private fun restoreLanterns() {
        val lanterns = actorState.getOrGainStatusEffect(StatusEffect.Enchantment)
        lanterns.counter = breakThreshold
        lanterns.displayCounter = true

        actorState.appearanceState = 0
        mayhemTimer.reset()
        regenTimer.reset()
    }

}