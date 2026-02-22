package xim.poc.game.configuration.v0

import kotlinx.serialization.Serializable
import xim.poc.game.ActorState
import xim.poc.game.ActorStateComponent
import xim.poc.game.ComponentUpdateResult
import xim.poc.game.GameEngine.displayName
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.SpellSkillId
import xim.poc.game.configuration.constants.spellNull_0

@Serializable
class ActorSpells(
    val spellIds: ArrayList<SpellSkillId> = ArrayList(),
    val equippedSpells: Array<SpellSkillId> = Array(20) { spellNull_0 },
): ActorStateComponent {

    override fun update(actorState: ActorState, elapsedFrames: Float): ComponentUpdateResult {
        return ComponentUpdateResult(removeComponent = false)
    }

    fun learnSpell(skill: SkillId) {
        check(skill is SpellSkillId) { "Trying to learn non-spell: $skill" }
        if (spellIds.contains(skill)) { return }
        spellIds += skill
        spellIds.sortBy { it.displayName() }
    }

    fun copyFrom(other: ActorSpells) {
        spellIds.clear()
        spellIds.addAll(other.spellIds)

        other.equippedSpells.copyInto(equippedSpells)
    }

}

fun ActorState.getLearnedSpells(): ActorSpells {
    return getComponentAs(ActorSpells::class) ?: ActorSpells()
}