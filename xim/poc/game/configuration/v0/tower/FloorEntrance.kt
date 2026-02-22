package xim.poc.game.configuration.v0.tower

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.game.event.InitialActorState
import xim.util.Fps


class FloorEntrance(val location: Vector3f, val look: ModelLook, val name: String = "Passage"): FloorEntity {

    val promise = spawn()

    override fun update(elapsedFrames: Float) { }

    override fun cleanup() {
        promise.onReady { GameEngine.submitDeleteActor(it.id) }
    }

    private fun spawn(): ActorPromise {
        return GameEngine.submitCreateActorState(
            InitialActorState(
                name = name,
                type = ActorType.StaticNpc,
                position = location,
                modelLook = look,
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
                maxTargetDistance = 10f,
            )
        ).onReady {
            GameV0.interactionManager.registerInteraction(it.id, FloorEntranceInteraction)
        }
    }

}

object FloorEntranceInteraction : NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        UiStateHelper.openQueryMode("What will you do?", getOptions(), callback = this::handleQueryResponse)
    }

    private fun getOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Do not use.", value = -1)
        options += QueryMenuOption("Return to base camp.", value = 1)
        return options
    }

    private fun handleQueryResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null || response.value < 0) { return QueryMenuResponse.pop }

        if (response.value == 1) {
            enterCamp()
        }

        return QueryMenuResponse.pop
    }

    fun enterCamp() {
        val startingPosition = GameV0.configuration.startingZoneConfig

        val script = EventScript(
            listOf(
                ActorRoutineEventItem(fileTableIndex = 0x113c8, actorId = ActorStateManager.playerId, eagerlyComplete = true),
                WaitRoutine(Fps.secondsToFrames(1.9f)),
                WarpZoneEventItem(startingPosition),
            )
        )

        EventScriptRunner.runScript(script)
    }

}