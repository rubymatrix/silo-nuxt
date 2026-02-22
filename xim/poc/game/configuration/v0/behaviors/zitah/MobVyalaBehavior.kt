package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.MobSkills
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class MobVyalaBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    override fun getSkills(): List<SkillId> {
        return if (actorState.getHpp() < 0.66) {
            listOf(mskillChargedWhisker_227, mskillBlinkofPeril_1953, mskillMortalBlast_2346, mskillPreternaturalGleam_2504)
        } else {
            super.getSkills()
        }
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.spellInterruptDown += 90
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Silence)
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        if (skill !is MobSkillId) { return null }

        if (skill == mskillChaoticEye_397 || skill == mskillBlaster_396 || skill == mskillBlinkofPeril_1953 || skill == mskillMortalBlast_2346) {
            return MobSkills[skill].castTime - 250.milliseconds * difficulty.offset
        }

        return null
    }

}