package xim.poc.game.configuration.v0.abyssea

import xim.poc.NoOpActorController
import xim.poc.game.ActorPromise
import xim.poc.game.ActorType
import xim.poc.game.GameEngine
import xim.poc.game.configuration.NoActionBehaviorId
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.event.InitialActorState

class AbysseaExitNpc(val definition: AbysseaConfiguration): FloorEntity {

    val promise = spawn()

    override fun update(elapsedFrames: Float) { }

    override fun cleanup() {
        promise.onReady { GameEngine.submitDeleteActor(it.id) }
    }

    private fun spawn(): ActorPromise {
        return GameEngine.submitCreateActorState(
            InitialActorState(
                name = "Cavernous Maw",
                type = ActorType.StaticNpc,
                position = definition.entrancePosition,
                modelLook = definition.entranceLook,
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
                maxTargetDistance = 10f,
            )
        ).onReady {
            GameV0.interactionManager.registerInteraction(it.id, AbysseaExitInteraction)
        }
    }

}