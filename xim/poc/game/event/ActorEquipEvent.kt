package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.GameState
import xim.poc.game.StatusEffect
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.equipAll
import xim.poc.game.actor.components.removeInvalidEquipment
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.resource.EquipSlot

class ActorEquipEvent(
    val sourceId: ActorId,
    val equipment: Map<EquipSlot, InternalItemId?>,
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()
        if (actorState.isDead()) { return emptyList() }

        val lockedSlots = actorState.getStatusEffect(StatusEffect.Encumbrance)?.potency ?: 0
        val encumberedSlots = equipment.filter { it.key.mask and lockedSlots != 0 }
        if (encumberedSlots.isNotEmpty()) {
            if (actorState.isPlayer()) { ChatLog("Cannot change equipment while encumbered.", ChatLogColor.Error) }
            return emptyList()
        }

        actorState.equipAll(equipment)
        actorState.removeInvalidEquipment()

        return GameState.getGameMode().onGearChange(actorState, equipment)
    }

}