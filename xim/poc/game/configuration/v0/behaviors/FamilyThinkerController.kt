package xim.poc.game.configuration.v0.behaviors

import xim.poc.ModelLook
import xim.poc.game.ActorState
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillWindsofPromyvion_989
import xim.poc.game.event.Event
import kotlin.random.Random

private enum class ThinkerForm(val look: Int, val appearanceState: Int) {
    Dark(0x463, 1),
    Water(0x463, 2),
    Thunder(0x464, 1),
    Earth(0x464, 2),
    Light(0x466, 1),
    Fire(0x466, 2),
    Ice(0x467, 1),
    Wind(0x467, 2),
}

class FamilyThinkerController(actorState: ActorState): V0MonsterController(actorState) {

    private val form = ThinkerForm.values().random()

    override fun onInitialized(): List<Event> {
        actorState.setBaseLook(ModelLook.npc(form.look))
        actorState.appearanceState = form.appearanceState
        return emptyList()
    }

    override fun getSkills(): List<SkillId> {
        val hasDebuffs = actorState.getStatusEffects().any { it.canErase }

        return if (hasDebuffs && Random.nextBoolean()) {
            listOf(mskillWindsofPromyvion_989)
        } else {
            super.getSkills()
        }
    }

}