package xim.poc.game.configuration.v0.paradox

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.game.event.InitialActorState
import xim.poc.gl.ByteColor
import xim.poc.tools.ZoneConfig
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

class ParadoxTransfer(val location: Vector3f, val destination: ZoneConfig): FloorEntity {

    val promise = spawn()

    override fun update(elapsedFrames: Float) { }

    override fun cleanup() {
        promise.onReady { GameEngine.submitDeleteActor(it.id) }
    }


    private fun spawn(): ActorPromise {
        return GameEngine.submitCreateActorState(InitialActorState(
            name = "Seed Fragment",
            type = ActorType.Effect,
            position = location,
            modelLook = ModelLook.npc(0x935),
            movementController = NoOpActorController(),
            behaviorController = NoActionBehaviorId,
        )).onReady { GameV0.interactionManager.registerInteraction(it.id, ParadoxInteraction(destination)) }
    }

}

private class ParadoxInteraction(val destination: ZoneConfig): NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        UiStateHelper.openQueryMode("What will you do?", getOptions(), callback = this::handleQueryResponse)
    }

    private fun getOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()

        val optionText = if (destination.zoneId == ParadoxZoneInstance.zoneConfig.zoneId) { "Enter!" } else { "Leave." }
        options += QueryMenuOption(optionText, value = 1)

        options += QueryMenuOption("Nothing.", value = -1)
        return options
    }

    private fun handleQueryResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response?.value != 1) { return QueryMenuResponse.popAll }
        executeTransfer()
        return QueryMenuResponse.popAll
    }

    private fun executeTransfer() {
        val script = EventScript(
            listOf(
                ActorRoutineEventItem(fileTableIndex = 0x1139F, ActorStateManager.playerId, eagerlyComplete = true),
                WaitRoutine(Fps.secondsToFrames(1.9f)),
                FadeOutEvent(1.seconds),
                WarpZoneEventItem(destination, fade = false),
                RunOnceEventItem { setupZoneLogic() },
                WaitRoutine(Fps.secondsToFrames(0.2f)),
                RunOnceEventItem { ActorManager.player().renderState.effectColor = ByteColor.zero },
                ActorRoutineEventItem(fileTableIndex = 0x113A0, actorId = ActorStateManager.playerId, eagerlyComplete = true),
                FadeInEvent(1.seconds),
            )
        )

        EventScriptRunner.runScript(script)
    }

    private fun setupZoneLogic() {
        if (destination == ParadoxZoneInstance.zoneConfig) {
            GameV0.setZoneLogic { ParadoxZoneInstance() }
        }
    }

}