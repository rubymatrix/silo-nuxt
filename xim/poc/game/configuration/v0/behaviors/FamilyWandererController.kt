package xim.poc.game.configuration.v0.behaviors

import xim.poc.ModelLook
import xim.poc.game.ActorState
import xim.poc.game.event.Event

private enum class WandererForm(val look: Int, val appearanceState: Int) {
    Dark(0x452, 1),
    Water(0x452, 2),
    Thunder(0x453, 1),
    Earth(0x453, 2),
    Light(0x454, 1),
    Fire(0x454, 2),
    Ice(0x456, 1),
    Wind(0x456, 2),
}

class FamilyWandererController(actorState: ActorState): V0MonsterController(actorState) {

    private val form = WandererForm.values().random()

    override fun onInitialized(): List<Event> {
        actorState.setBaseLook(ModelLook.npc(form.look))
        actorState.appearanceState = form.appearanceState
        return emptyList()
    }

}