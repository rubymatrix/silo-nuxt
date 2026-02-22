package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.AutoAttackDirection.*
import xim.poc.game.configuration.constants.*

class FamilyPteraketosController(actorState: ActorState): V0MonsterController(actorState) {

    override fun getAutoAttackAbilities(): List<SkillId> {
        val target = ActorStateManager[actorState.getTargetId()] ?: return emptyList()

        return when (actorState.getDirectionalAutoAttack(target)) {
            North -> listOf(mskillPteraketosAutoAttack_2606, mskillPteraketosAutoAttack_2607, mskillPteraketosAutoAttack_2608)
            East, SouthEast, NorthEast, -> listOf(mskillPteraketosAutoAttack_2609)
            West, SouthWest, NorthWest -> listOf(mskillPteraketosAutoAttack_2610)
            South -> listOf(mskillPteraketosAutoAttack_2611)
        }
    }

}