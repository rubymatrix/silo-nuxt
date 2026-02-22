package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.StatusEffect
import xim.poc.ui.ChatLog
import xim.poc.ui.DamageTextManager
import xim.poc.ui.ShiftJis

class ActorHealedEvent(
    val sourceId: ActorId,
    val targetId: ActorId,
    val amount: Int,
    val actionContext: AttackContext? = null,
    val healType: ActorResourceType = ActorResourceType.HP,
    val displayMessage: Boolean = true,
): Event {

    override fun apply(): List<Event> {
        val sourceState = ActorStateManager[sourceId] ?: return emptyList()
        val targetState = ActorStateManager[targetId] ?: return emptyList()
        if (targetState.isDead()) { return emptyList() }

        when (healType) {
            ActorResourceType.HP -> targetState.gainHp(amount)
            ActorResourceType.MP -> targetState.gainMp(amount)
            ActorResourceType.TP -> targetState.gainTp(amount)
        }

        if (healType == ActorResourceType.HP && amount > 0) {
            targetState.expireStatusEffect(StatusEffect.Sleep)
        }

        val outEvents = ArrayList<Event>()

        AttackContext.compose(actionContext) {
            if (displayMessage) {
                ChatLog.addLine("${sourceState.name}${ShiftJis.rightArrow}${targetState.name} +$amount $healType.", actionContext = actionContext)
            }
            DamageTextManager.append(this)
        }

        return outEvents
    }

}