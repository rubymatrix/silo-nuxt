package xim.poc.game.configuration.v0.behaviors

import xim.poc.ModelLook
import xim.poc.game.ActorState
import xim.poc.game.event.Event

private enum class GorgerForm(val look: Int, val appearanceState: Int) {
    Dark(0x469, 1),
    Water(0x469, 2),
    Thunder(0x46A, 1),
    Earth(0x46A, 2),
    Light(0x46B, 1),
    Fire(0x46B, 2),
    Ice(0x46C, 1),
    Wind(0x46C, 2),
}

class FamilyGorgerController(actorState: ActorState): V0MonsterController(actorState) {

    private val form = GorgerForm.values().random()

    override fun onInitialized(): List<Event> {
        actorState.setBaseLook(ModelLook.npc(form.look))
        actorState.appearanceState = form.appearanceState
        return emptyList()
    }

}