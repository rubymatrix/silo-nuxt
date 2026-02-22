package xim.poc.game.event

import xim.poc.ActionTargetFilter
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.*
import xim.poc.game.actor.components.getInventory
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor

class ActorDefeatedEvent(
    val defeated: ActorId,
    val defeatedBy: ActorId?,
    val actionContext: AttackContext? = null,
): Event {

    override fun apply(): List<Event> {
        val events = ArrayList<Event>()

        val defeatedState = ActorStateManager[defeated] ?: return emptyList()
        defeatedState.setHp(0)

        events += defeatedState.behaviorController.onDefeated()
        events += BattleDisengageEvent(defeated)
        events += GameEngine.releaseDependents(defeatedState)

        defeatedState.expireStatusEffects { true }

        AttackContext.compose(actionContext) { displayDeath() }

        val defeatedByState = ActorStateManager[defeatedBy]
        if (defeated != defeatedBy && defeatedByState != null) {
            AttackContext.compose(actionContext) { ChatLog("${defeatedByState.name} defeats ${defeatedState.name}.", ChatLogColor.Action) }
        } else if (defeatedState.name.isNotBlank()) {
            AttackContext.compose(actionContext) { ChatLog("${defeatedState.name} falls to the ground.", ChatLogColor.Action) }
        }

        var creditedActor = (if (defeatedBy == null || defeated == defeatedBy) {
            ActorStateManager[defeatedState.targetState.targetId]
        } else {
            defeatedByState
        }) ?: return events

        if (creditedActor.owner != null) {
            creditedActor = ActorStateManager[creditedActor.owner] ?: return events
        }

        if (!ActionTargetFilter.areEnemies(defeatedState, creditedActor)) { return events }

        val creditedActors = PartyManager[creditedActor.id].getAllState().filter { it.type == ActorType.Pc }
        if (creditedActors.any { it.isPlayer() }) {
            events += transferInventory(defeatedState, ActorStateManager.player())
        }

        val pointsGainActors = creditedActors.filter { !it.isDead() }

        events += pointsGainActors.flatMap {
            GameState.getGameMode().onActorDefeated(it, defeatedState, actionContext)
        }

        events += pointsGainActors.map {
            val expAmount = GameState.getGameMode().getExperiencePointGain(it, defeatedState)
            ActorGainExpEvent(actorId = it.id, expAmount = expAmount, actionContext = actionContext)
        }

        events += pointsGainActors.map {
            ActorGainRpEvent(it.id, defeated, actionContext = actionContext)
        }

        return events
    }

    private fun displayDeath() {
        ActorManager[defeated]?.onDisplayDeath()
    }

    private fun transferInventory(source: ActorState, destination: ActorState): List<Event> {
        return source.getInventory().inventoryItems.map {
            InventoryItemTransferEvent(
                sourceId = source.id,
                destinationId = destination.id,
                inventoryItemId = it.internalId,
                actionContext = actionContext,
            )
        }
    }

}