package xim.poc.game.configuration.v0.tower

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.GameTower
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.interactions.BlueMagicInteraction
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.game.configuration.v0.zones.BattleLocation
import xim.poc.game.event.InitialActorState
import xim.poc.gl.ByteColor
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

class FloorExit(val floorConfiguration: FloorConfiguration, val location: BattleLocation, val monsterSpawnerInstance: MonsterSpawnerInstance): FloorEntity {

    private var promise = spawn()

    override fun update(elapsedFrames: Float) { }

    override fun cleanup() {
        promise.onReady { GameEngine.submitDeleteActor(it.id) }
    }

    private fun spawn(): ActorPromise {
        return GameEngine.submitCreateActorState(
            InitialActorState(
                name = "Ethereal Ingress",
                type = ActorType.StaticNpc,
                position = location.exitPosition,
                modelLook = location.exitLook,
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
                maxTargetDistance = 10f,
            )
        ).onReady {
            GameV0.interactionManager.registerInteraction(it.id, FloorExitInteraction(this))
        }
    }

}

private class FloorExitInteraction(val floorExit: FloorExit) : NpcInteraction {

    private val floorConfiguration = floorExit.floorConfiguration
    private val monsterSpawnerInstance = floorExit.monsterSpawnerInstance

    override fun onInteraction(npcId: ActorId) {
        val hasCleared = GameTower.hasClearedFloor(floorConfiguration.floorNumber)
        val totalDefeated = monsterSpawnerInstance.getTotalDefeated()

        if (!hasCleared && totalDefeated < floorConfiguration.defeatRequirement) {
            val remaining = floorConfiguration.defeatRequirement - totalDefeated
            val word = if (remaining > 1) { "enemies" } else { "enemy" }
            ChatLog("Defeat $remaining more $word to proceed...", ChatLogColor.Info)
            return
        }

        UiStateHelper.openQueryMode(prompt = "What will you do?", options = getOptions()) { handleQueryResponse(it) }
    }

    private fun getOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        if (floorConfiguration.nextFloor != null) { options += QueryMenuOption("Proceed to next floor.", value = 1) }
        options += QueryMenuOption("Repeat this floor.", value = 2)
        options += QueryMenuOption("Return to base camp.", value = 3)
        options += QueryMenuOption("Manage Blue Magic.", value = 4)
        return options
    }

    private fun handleQueryResponse(response: QueryMenuOption?): QueryMenuResponse {
        when (response?.value) {
            1 -> nextFloor()
            2 -> GameV0.executeRepeatCurrentFloor(fullRestore = false)
            3 -> FloorEntranceInteraction.enterCamp()
            4 ->  {
                BlueMagicInteraction.open()
                return QueryMenuResponse.noop
            }
        }

        return QueryMenuResponse.pop
    }

    private fun nextFloor() {
        val nextFloorDefinition = GameTower.getNextFloorDefinition()
        val nextFloorNumber = GameTower.advanceTowerFloor()

        val script = EventScript(
            listOf(
                ActorRoutineEventItem(fileTableIndex = 0x114CB, actorId = ActorStateManager.playerId, eagerlyComplete = true),
                WaitRoutine(Fps.secondsToFrames(1.9f)),
                FadeOutEvent(1.seconds),
                WaitRoutine(Fps.secondsToFrames(0.1f)),
                WarpZoneEventItem(nextFloorDefinition.battleLocation.startingPosition, fade = false),
                RunOnceEventItem {
                    GameTower.emitFloorNumber()
                    GameV0.setupBattle(nextFloorNumber)
                },
                ActorRoutineEventItem(fileTableIndex = 0x114CC, actorId = ActorStateManager.playerId, eagerlyComplete = true),
                FadeInEvent(1.seconds),
            )
        )

        EventScriptRunner.runScript(script)
    }

}