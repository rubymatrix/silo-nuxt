package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillSelection
import xim.poc.game.configuration.SkillSelector
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillAccursedArmor_2134
import xim.poc.game.configuration.constants.mskillAmnesicBlast_2135
import xim.poc.game.configuration.constants.mskillEclipticMeteor_2330
import xim.poc.game.event.Event

class MobHadhayoshBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private var eclipticMeteorCount = 0

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Bind)

        if (wantsToUseEclipticMeteor()) {
            aggregate.tpRequirementBypass = true
            aggregate.fullResist(StatusEffect.Stun)
        }
    }

    override fun getSkills(): List<SkillId> {
        return super.getSkills() + listOf(mskillAccursedArmor_2134, mskillAmnesicBlast_2135)
    }

    override fun wantsToUseSkill(): Boolean {
        return wantsToUseEclipticMeteor() || super.wantsToUseSkill()
    }

    override fun selectSkill(): SkillSelection? {
        return if (wantsToUseEclipticMeteor()) {
            SkillSelector.selectSkill(actorState, listOf(mskillEclipticMeteor_2330))
        } else {
            super.selectSkill()
        }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillEclipticMeteor_2330) { eclipticMeteorCount += 1 }
        return emptyList()
    }

    private fun wantsToUseEclipticMeteor(): Boolean {
        val threshold = 1f - 0.3167f * (eclipticMeteorCount + 1)
        return actorState.getHpp() <= threshold
    }

}