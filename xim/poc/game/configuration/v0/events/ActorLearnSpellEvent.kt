package xim.poc.game.configuration.v0.events

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.GameEngine.displayName
import xim.poc.game.configuration.constants.SpellSkillId
import xim.poc.game.configuration.constants.spellNull_0
import xim.poc.game.configuration.v0.HelpNotifier
import xim.poc.game.configuration.v0.getLearnedSpells
import xim.poc.game.event.Event
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.ShiftJis
import xim.resource.MagicType
import xim.resource.table.SpellInfoTable.toSpellInfo
import kotlin.time.Duration.Companion.seconds

class ActorLearnSpellEvent(
    val actorId: ActorId,
    val skill: SpellSkillId,
    val context: AttackContext? = null,
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[actorId] ?: return emptyList()

        val learnedSpells = actorState.getLearnedSpells()
        if (learnedSpells.spellIds.contains(skill)) { return emptyList() }

        learnedSpells.learnSpell(skill)

        AttackContext.compose(context) {
            HelpNotifier.notify("${actorState.name} learned ${ShiftJis.leftBracket}${skill.displayName()}${ShiftJis.rightBracket}!", 5.seconds)
            ChatLog("${actorState.name} learned ${skill.displayName()}!", ChatLogColor.SystemMessage)
        }

        val spellInfo = skill.toSpellInfo()
        if (spellInfo.magicType != MagicType.BlueMagic) { return emptyList() }

        val equippedSpells = learnedSpells.equippedSpells
        for (i in equippedSpells.indices) {
            if (equippedSpells[i] != spellNull_0) { continue }
            equippedSpells[i] = skill
            break
        }

        return emptyList()
    }

}