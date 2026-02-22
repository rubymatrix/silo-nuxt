package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.SceneManager
import xim.poc.game.*
import xim.poc.game.actor.components.getEquipment
import xim.resource.EquipSlot
import xim.resource.table.ItemModelTable
import xim.resource.table.MainDll

class FishingStartEvent(
    val actorId: ActorId,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[actorId] ?: return emptyList()
        if (!GameState.getGameMode().canFish(source)) { return emptyList() }

        val scene = SceneManager.getNullableCurrentScene() ?: return emptyList()
        if (!scene.canFish(source)) { return emptyList() }

        val sourceRace = source.getCurrentLook().race ?: return emptyList()

        val rangedItem = source.getEquipment(EquipSlot.Range) ?: return emptyList()
        val rangedItemModelId = ItemModelTable[rangedItem.info()]
        val rangedItemModelIndex = MainDll.getBaseFishingRodIndex(sourceRace) + rangedItemModelId

        val fishingRodActorId = ActorStateManager.nextId()
        source.fishingRod = fishingRodActorId

        val initialActorState = InitialActorState(
            presetId = fishingRodActorId,
            name = "(Rod)",
            type = ActorType.Effect,
            position = source.position,
            modelLook = ModelLook.fileTableIndex(rangedItemModelIndex),
            rotation = source.rotation,
            movementController = NoOpActorController(),
            dependentSettings = DependentSettings(actorId, ActorFishingRod),
            targetable = false,
        )

        source.initiateAction(ActorFishingAttempt())
        return listOf(ActorCreateEvent(initialActorState))
    }

}