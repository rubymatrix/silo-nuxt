package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.ActorState
import xim.poc.game.CastingState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillSelection
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.V0SpellDefinitions.sourcePercentageHealingMagic
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.Event
import xim.resource.TargetFlag

class MobUmdhlebiBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    private var chainSpellCounter = 0
    private var attemptedFullBloom = false

    private val chainSpellQueue = ArrayDeque<SpellSkillId>()

    override fun onReadyToAutoAttack(): List<Event>? {
        return if (chainspellIsActive()) { emptyList() } else { super.onReadyToAutoAttack() }
    }

    override fun wantsToUseSkill(): Boolean {
        return !chainspellIsActive() && (wantsToUseChainspell() || super.wantsToUseSkill())
    }

    override fun selectSkill(): SkillSelection? {
        return if (wantsToUseChainspell()) {
            SkillSelection(mskillChainspell_436, actorState)
        } else if (wantsToUseFullBloom()) {
            attemptedFullBloom = true
            SkillSelection(mskillFullBloom_2627, actorState)
        } else {
            super.selectSkill()
        }
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId == mskillFullBloom_2627) {
            return SkillApplier(targetEvaluator = sourcePercentageHealingMagic(50f))
        }

        return super.getSkillApplierOverride(skillId)
    }

    override fun getSkillEffectedTargetType(skillId: SkillId): Int? {
        return if (skillId == mskillFullBloom_2627) { TargetFlag.Self.flag } else { super.getSkillEffectedTargetType(skillId) }
    }

    override fun wantsToCastSpell(): Boolean {
        return actorState.hasStatusEffect(StatusEffect.Chainspell) || super.wantsToCastSpell()
    }

    override fun getSpells(): List<SkillId> {
        return if (chainspellIsActive()) {
            populateChainSpellQueue()
            listOf(chainSpellQueue.first())
        } else {
            super.getSpells()
        }
    }

    override fun onSkillBeginCharging(castingState: CastingState): List<Event> {
        if (castingState.skill is SpellSkillId && chainspellIsActive()) { chainSpellQueue.removeFirst() }
        return emptyList()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillChainspell_436) {
            chainSpellCounter += 1
            chainSpellQueue.clear()
        }

        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.spellInterruptDown += 100
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Silence)
        aggregate.refresh += 10

        if (chainspellIsActive()) { aggregate.fullResist(StatusEffect.Stun) }
    }

    private fun wantsToUseChainspell(): Boolean {
        return when (chainSpellCounter) {
            0 -> actorState.getHpp() < 0.8
            1 -> actorState.getHpp() < 0.6
            2 -> actorState.getHpp() < 0.4
            else -> false
        }
    }

    private fun chainspellIsActive(): Boolean {
        return actorState.hasStatusEffect(StatusEffect.Chainspell)
    }

    private fun populateChainSpellQueue() {
        if (chainSpellQueue.isNotEmpty()) { return }
        chainSpellQueue += listOf(spellComet_219, spellComet_219, spellMeteor_218)
    }

    private fun wantsToUseFullBloom(): Boolean {
        return !attemptedFullBloom && actorState.getHpp() < 0.30
    }

}