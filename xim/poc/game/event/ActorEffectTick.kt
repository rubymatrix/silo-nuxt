package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.GameEngine
import xim.poc.game.StatusEffect

class ActorEffectTickResult(
    val hpDelta: Int,
    val mpDelta: Int,
    val tpDelta: Int,
)

class ActorEffectTick(
    val actorId: ActorId,
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[actorId] ?: return emptyList()
        if (actorState.isDead()) { return emptyList() }

        val result = GameEngine.getActorEffectTickResult(actorState)
        actorState.gainHp(result.hpDelta)
        actorState.gainMp(result.mpDelta)
        actorState.gainTp(result.tpDelta)

        val sleep = actorState.getStatusEffect(StatusEffect.Sleep)
        if (sleep != null && sleep.counter == 0 && result.hpDelta < 0) {
            actorState.expireStatusEffect(StatusEffect.Sleep)
        }

        if (actorState.isDead()) {
            return listOf(ActorDefeatedEvent(defeated = actorId, defeatedBy = null, actionContext = null))
        }

        return emptyList()
    }

}