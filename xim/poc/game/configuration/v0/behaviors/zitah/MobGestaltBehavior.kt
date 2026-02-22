package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillSelection
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.V0SpellDefinitions.applyBasicBuff
import xim.poc.game.configuration.v0.V0SpellDefinitions.sourcePercentageHealingMagic
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.Event
import kotlin.time.Duration.Companion.seconds

class MobGestaltBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    private var hasUsedChainSpell = false

    override fun wantsToUseSkill(): Boolean {
        return !chainspellIsActive() && (wantsToUseChainspell() || super.wantsToUseSkill())
    }

    override fun selectSkill(): SkillSelection? {
        return if (wantsToUseChainspell()) { SkillSelection(mskillChainspell_436, actorState) } else { super.selectSkill() }
    }

    override fun wantsToCastSpell(): Boolean {
        return actorState.hasStatusEffect(StatusEffect.Chainspell) || super.wantsToCastSpell()
    }

    override fun getSpells(): List<SkillId> {
        return if (chainspellIsActive()) { listOf(spellFireV_148, spellFlare_204, spellBlazeSpikes_249) } else { super.getSpells() }
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        return if (skillId == mskillChainspell_436) {
            val duration = 15.seconds + difficulty.offset.seconds
            SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.Chainspell, duration = duration))
        } else if (skillId == mskillCatharsis_184) {
            SkillApplier(targetEvaluator = sourcePercentageHealingMagic(10f))
        } else {
            null
        }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillChainspell_436) { hasUsedChainSpell = true }
        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.spellInterruptDown += 90
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Silence)
        aggregate.fastCast += 6 * difficulty.value
        aggregate.refresh += 10
    }

    private fun wantsToUseChainspell(): Boolean {
        return !hasUsedChainSpell && actorState.getHpp() < 0.5
    }

    private fun chainspellIsActive(): Boolean {
        return actorState.hasStatusEffect(StatusEffect.Chainspell)
    }

}