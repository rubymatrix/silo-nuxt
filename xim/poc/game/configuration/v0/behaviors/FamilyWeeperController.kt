package xim.poc.game.configuration.v0.behaviors

import xim.poc.ModelLook
import xim.poc.game.ActorState
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.Event
import kotlin.random.Random

private enum class WeeperForm(val look: Int, val appearanceState: Int, val skill: SkillId) {
    Dark(0x458, 1, mskillMemoryofDark_972),
    Water(0x458, 2, mskillMemoryofWater_971),
    Thunder(0x459, 1, mskillMemoryofLightning_970),
    Earth(0x459, 2, mskillMemoryofEarth_969),
    Light(0x45A, 1, mskillMemoryofLight_968),
    Fire(0x45A, 2, mskillMemoryofFire_965),
    Ice(0x45B, 1, mskillMemoryofIce_966),
    Wind(0x45B, 2, mskillMemoryofWind_967),
}

class FamilyWeeperController(actorState: ActorState): V0MonsterController(actorState) {

    private val form = WeeperForm.values().random()

    override fun onInitialized(): List<Event> {
        actorState.setBaseLook(ModelLook.npc(form.look))
        actorState.appearanceState = form.appearanceState
        return emptyList()
    }

    override fun getSkills(): List<SkillId> {
        return if (Random.nextBoolean()) {
            listOf(form.skill)
        } else {
            super.getSkills()
        }
    }

}