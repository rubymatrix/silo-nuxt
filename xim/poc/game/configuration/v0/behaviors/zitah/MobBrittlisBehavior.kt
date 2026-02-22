package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillSelection
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillManafont_435
import xim.poc.game.configuration.constants.spellImpact_503
import xim.poc.game.configuration.v0.DamageCalculator.rollAgainst
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.Event

class MobBrittlisBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    private val specialCharges = difficulty.value
    private var remainingSpecialCharges = specialCharges

    override fun wantsToUseSkill(): Boolean {
        return !specialIsActive() && (wantsToUseSpecial() || super.wantsToUseSkill())
    }

    override fun selectSkill(): SkillSelection? {
        return if (wantsToUseSpecial()) { SkillSelection(mskillManafont_435, actorState) } else { super.selectSkill() }
    }

    override fun getSpells(): List<SkillId> {
        return if (specialIsActive() && rollAgainst(100 - 10 * difficulty.offset)) {
            listOf(spellImpact_503)
        } else {
            super.getSpells()
        }
    }

    override fun wantsToCastSpell(): Boolean {
        return actorState.hasStatusEffect(StatusEffect.Manafont) || super.wantsToCastSpell()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillManafont_435) { remainingSpecialCharges -= 1 }
        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.spellInterruptDown += 100
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Silence)
        aggregate.fastCast += 3 * difficulty.value
        aggregate.refresh += 10

        if (actorState.hasStatusEffect(StatusEffect.Manafont)) {
            aggregate.allStatusResistRate += 100
            aggregate.magicAttackBonus += 20 + 5 * difficulty.value
            aggregate.fastCast += 12
        }
    }

    private fun wantsToUseSpecial(): Boolean {
        val interval = 1f / (specialCharges + 1)
        return actorState.getHpp() < interval * remainingSpecialCharges
    }

    private fun specialIsActive(): Boolean {
        return actorState.hasStatusEffect(StatusEffect.Manafont)
    }

}