package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillChthonianRay_1103
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.basicDebuff
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.AttackStatusEffect
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MobRevetaurBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify)

        if (!targetIsDoomed()) {
            aggregate.tpRequirementBypass = true
        }
    }

    override fun getSkills(): List<SkillId> {
        return if (!targetIsDoomed()) { listOf(mskillChthonianRay_1103) } else { super.getSkills() }
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        return if (skillId == mskillChthonianRay_1103) {
            SkillApplier(targetEvaluator = basicDebuff(AttackStatusEffect(StatusEffect.Doom, 90.seconds, canResist = false)))
        } else {
            null
        }
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        return if (skill == mskillChthonianRay_1103) { ZERO } else { null }
    }

    override fun wantsToUseSkill(): Boolean {
        return !targetIsDoomed() || super.wantsToUseSkill()
    }

    private fun targetIsDoomed(): Boolean {
        return ActorStateManager[actorState.getTargetId()]?.hasStatusEffect(StatusEffect.Doom) == true
    }

}