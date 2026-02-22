package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillVolcanicWrath_2679
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.basicMagicalWs
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.spellDamageDoT
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.AttackEffects
import xim.poc.game.event.AttackStatusEffect
import kotlin.time.Duration.Companion.seconds

class MobAngrbodaBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.spellInterruptDown += 80
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Bind)
    }

    override fun getSkills(): List<SkillId> {
        return super.getSkills() + if (actorState.getHpp() < 0.66) { listOf(mskillVolcanicWrath_2679, mskillVolcanicWrath_2679, mskillVolcanicWrath_2679) } else { emptyList() }
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        return if (skillId == mskillVolcanicWrath_2679) {
            val hpDownEffect = 0.75f - 0.05f * difficulty.offset
            val burnPotency = 0.25f + 0.05f * difficulty.offset
            val burnStrDown = 0.8f - 0.10f * difficulty.offset

            SkillApplier(targetEvaluator = basicMagicalWs(
                attackEffects = AttackEffects(attackStatusEffects = listOf(
                    AttackStatusEffect(statusEffect = StatusEffect.HPDown, baseDuration = 15.seconds) { it.statusState.secondaryPotency = hpDownEffect },
                    spellDamageDoT(StatusEffect.Burn, duration = 15.seconds, potency = burnPotency, secondaryPotency = burnStrDown),
                )),
            ) { 2f })
        } else {
            super.getSkillApplierOverride(skillId)
        }
    }

}