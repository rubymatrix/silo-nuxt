package xim.poc.game.configuration.v0.paradox

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.ActorPromise
import xim.poc.game.ActorStateManager
import xim.poc.game.ActorType
import xim.poc.game.GameEngine
import xim.poc.game.configuration.*
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.game.event.InitialActorState
import xim.poc.gl.ByteColor
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

class CloisterExit(val location: Vector3f, val crystalType: CrystalType): FloorEntity {

    val promise = spawn()

    override fun update(elapsedFrames: Float) { }

    override fun cleanup() {
        promise.onReady { GameEngine.submitDeleteActor(it.id) }
    }

    private fun spawn(): ActorPromise {
        return GameEngine.submitCreateActorState(
            InitialActorState(
                name = crystalType.exitName,
                type = ActorType.StaticNpc,
                position = location,
                modelLook = ModelLook.npc(0x032),
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
                maxTargetDistance = 10f,
            )
        ).onReady { GameV0.interactionManager.registerInteraction(it.id, CloisterExitInteraction) }
    }

}

object CloisterExitInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        val script = EventScript(
            listOf(
                ActorRoutineEventItem(fileTableIndex = 0x1139F, ActorStateManager.playerId, eagerlyComplete = true),
                WaitRoutine(Fps.secondsToFrames(1.9f)),
                FadeOutEvent(1.seconds),
                WarpZoneEventItem(ParadoxZoneInstance.zoneConfig, fade = false),
                RunOnceEventItem { GameV0.setZoneLogic { ParadoxZoneInstance() } },
                WaitRoutine(Fps.secondsToFrames(0.2f)),
                RunOnceEventItem { ActorManager.player().renderState.effectColor = ByteColor.zero },
                ActorRoutineEventItem(fileTableIndex = 0x113A0, actorId = ActorStateManager.playerId, eagerlyComplete = true),
                FadeInEvent(1.seconds),
            )
        )

        EventScriptRunner.runScript(script)
    }

}