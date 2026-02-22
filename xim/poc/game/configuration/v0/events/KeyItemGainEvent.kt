package xim.poc.game.configuration.v0.events

import xim.poc.game.AttackContext
import xim.poc.game.configuration.v0.GameV0SaveStateHelper
import xim.poc.game.configuration.v0.interactions.Quantity
import xim.poc.game.event.Event
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.ShiftJis.colorClear
import xim.poc.ui.ShiftJis.colorKey
import xim.resource.KeyItemId
import xim.resource.KeyItemTable

class KeyItemGainEvent(
    val keyItemId: KeyItemId,
    val quantity: Quantity,
    val context: AttackContext?,
): Event {

    override fun apply(): List<Event> {
        val keyItems = GameV0SaveStateHelper.getState().keyItems

        val currentCount = keyItems.getOrPut(keyItemId) { 0 }
        keyItems[keyItemId] = currentCount + quantity

        AttackContext.compose(context) {
            val quantityStr = if (quantity > 1) { "$quantity " } else { "" }
            ChatLog("Obtained key item: $colorKey$quantityStr${KeyItemTable.getName(keyItemId, quantity)}$colorClear.", ChatLogColor.Info)
        }

        return emptyList()
    }

}