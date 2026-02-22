package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillLambChop_4
import xim.poc.game.configuration.constants.mskillRage_5
import xim.poc.game.configuration.constants.mskillSheepCharge_6
import xim.poc.game.configuration.constants.mskillSheepSong_8

class FamilySheepBehavior(actorState: ActorState) : V0MonsterController(actorState) {

    override fun getSkills(): List<SkillId> {
        val skills = if (actorState.hasStatusEffect(StatusEffect.Berserk)) {
            listOf(mskillLambChop_4, mskillSheepCharge_6)
        } else {
            listOf(mskillLambChop_4, mskillRage_5, mskillSheepCharge_6, mskillSheepSong_8)
        }

        return skills
    }

}