package xim.poc.game.configuration.v0.behaviors

import xim.poc.ModelLook
import xim.poc.game.ActorState
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillWanion_998
import xim.poc.game.event.Event

private enum class SeetherForm(val look: Int, val appearanceState: Int) {
    Dark(0x45D, 1),
    Water(0x45D, 2),
    Thunder(0x45F, 1),
    Earth(0x45F, 2),
    Light(0x460, 1),
    Fire(0x460, 2),
    Ice(0x461, 1),
    Wind(0x461, 2),
}

class FamilySeetherController(actorState: ActorState): V0MonsterController(actorState) {

    private val form = SeetherForm.values().random()

    override fun onInitialized(): List<Event> {
        actorState.setBaseLook(ModelLook.npc(form.look))
        actorState.appearanceState = form.appearanceState
        return emptyList()
    }

    override fun getSkills(): List<SkillId> {
        val debuffCount = actorState.getStatusEffects().count { it.statusEffect.debuff }
        return if (debuffCount >= 2) {
            listOf(mskillWanion_998)
        } else {
            super.getSkills()
        }
    }

}