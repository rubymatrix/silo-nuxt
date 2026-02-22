package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ModelLook
import xim.poc.Mount
import xim.poc.NoOpActorController
import xim.poc.game.ActorMount
import xim.poc.game.ActorStateManager
import xim.poc.game.ActorType
import xim.poc.game.StatusEffect

class ActorMountEvent(
    val sourceId: ActorId,
    val index: Int
): Event {
    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()
        if (!source.isIdle()) { return emptyList() }
        if (source.mountedState != null) { return emptyList() }

        val mountActorId = ActorStateManager.nextId()
        source.mountedState = Mount(index, mountActorId)

        val initialActorState = InitialActorState(
            presetId = mountActorId,
            name = "(Mount)",
            type = ActorType.StaticNpc,
            position = source.position,
            modelLook = ModelLook.fileTableIndex(0x019131 + index),
            rotation = source.rotation,
            movementController = NoOpActorController(),
            dependentSettings = DependentSettings(sourceId, ActorMount),
            targetable = false,
        )

        source.gainStatusEffect(StatusEffect.Mounted)
        return listOf(ActorCreateEvent(initialActorState))
    }
}