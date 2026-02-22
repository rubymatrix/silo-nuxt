package xim.poc.game.configuration.v0.behaviors

import xim.poc.ModelLook
import xim.poc.game.ActorState
import xim.poc.game.event.Event

private enum class CraverForm(val look: Int, val appearanceState: Int) {
    Dark(0x46e, 1),
    Water(0x46e, 2),
    Thunder(0x46f, 1),
    Earth(0x46f, 2),
    Light(0x471, 1),
    Fire(0x471, 2),
    Ice(0x472, 1),
    Wind(0x472, 2),
}

class FamilyCraverController(actorState: ActorState): V0MonsterController(actorState) {

    private val form = CraverForm.values().random()

    override fun onInitialized(): List<Event> {
        actorState.setBaseLook(ModelLook.npc(form.look))
        actorState.appearanceState = form.appearanceState
        return emptyList()
    }

}