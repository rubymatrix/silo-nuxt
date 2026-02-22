package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.ActorManager
import xim.poc.game.*
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskill_2811
import xim.poc.game.configuration.constants.spellBlizzagaV_183
import xim.poc.game.configuration.standardBlurConfig
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.spellDamageDoT
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.AttackAddedEffectType
import xim.poc.game.event.AttackStatusEffect
import xim.poc.game.event.Event
import xim.poc.gl.ByteColor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class MobNosoiBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    private var skillCounter = 0

    override fun update(elapsedFrames: Float): List<Event> {
        setBlur()
        return super.update(elapsedFrames)
    }

    override fun getSkills(): List<SkillId> {
        return if (wantsToUseGale()) { listOf(mskill_2811) } else { super.getSkills() }
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        if (skill != mskill_2811) { return null }
        return 1.5.seconds - 0.125.seconds * difficulty.value
    }

    override fun getSpells(): List<SkillId> {
        val target = ActorStateManager[actorState.getTargetId()] ?: return super.getSpells()
        return if (target.hasStatusEffect(StatusEffect.Terror)) { listOf(spellBlizzagaV_183) } else { super.getSpells() }
    }

    override fun wantsToCastSpell(): Boolean {
        return targetIsTerrorized() || super.wantsToCastSpell()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.spellInterruptDown += 100
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Bind)

        val effectPower = spellDamageDoT(StatusEffect.Poison, potency = 0.5f + 0.1f * difficulty.value, duration = ZERO)

        aggregate.autoAttackEffects += AutoAttackEffect(0, AttackAddedEffectType.Poison, statusEffect = AttackStatusEffect(
            statusEffect = StatusEffect.Poison,
            baseDuration = 5.seconds + difficulty.value.seconds * 2,
        ) { effectPower.decorator.invoke(it) })
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskill_2811) {
            skillCounter = 0
        } else if (primaryTargetContext.skill is MobSkillId) {
            skillCounter += 1
        }
        return super.onSkillExecuted(primaryTargetContext)
    }

    private fun targetIsTerrorized(): Boolean {
        val target = ActorStateManager[actorState.getTargetId()] ?: return false
        return target.hasStatusEffect(StatusEffect.Terror)
    }

    private fun wantsToUseGale(): Boolean {
        return skillCounter >= 2
    }

    private fun setBlur() {
        if (actorState.isDead()) {
            ActorManager[actorState.id]?.actorModel?.customModelSettings?.blurConfig = null
            return
        }

        val blur = if (wantsToUseGale()) {
            standardBlurConfig(ByteColor(0x40, 0x40, 0x80, 0x20), radius = 90f)
        } else {
            null
        }

        ActorManager[actorState.id]?.actorModel?.customModelSettings?.blurConfig = blur
    }

}