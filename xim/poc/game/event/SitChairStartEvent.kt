package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.ActorSitChair
import xim.poc.game.ActorSitChairState
import xim.poc.game.ActorStateManager
import xim.poc.game.ActorType
import xim.poc.game.actor.components.RestingState

class SitChairStartEvent(
    val actorId: ActorId,
    val sitChairIndex: Int,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[actorId] ?: return emptyList()
        if (!source.isIdle()) { return emptyList() }

        if (sitChairIndex < 0 || sitChairIndex > 12) { return emptyList() }
        val modelIndex = 0x1889b + sitChairIndex

        val chairActorId = ActorStateManager.nextId()
        val action = ActorSitChairState(index = sitChairIndex, chairId = chairActorId)
        if (!source.initiateAction(action)) { return emptyList() }

        val component = RestingState(action)
        source.components[RestingState::class] = component

        val initialActorState = InitialActorState(
            presetId = chairActorId,
            name = "(Chair)",
            type = ActorType.StaticNpc,
            position = source.position,
            modelLook = ModelLook.fileTableIndex(modelIndex),
            rotation = source.rotation,
            movementController = NoOpActorController(),
            dependentSettings = DependentSettings(actorId, ActorSitChair),
            targetable = false,
        )

        return listOf(ActorCreateEvent(initialActorState))
    }

}