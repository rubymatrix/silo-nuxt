package xim.poc.game.configuration.v0.escha

import xim.math.Vector3f
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.ActorPromise
import xim.poc.game.ActorType
import xim.poc.game.GameEngine
import xim.poc.game.configuration.NoActionBehaviorId
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.event.InitialActorState

class EschaExitEntity(val location: Vector3f): FloorEntity {

    val promise = spawn()

    override fun update(elapsedFrames: Float) { }

    override fun cleanup() {
        promise.onReady { GameEngine.submitDeleteActor(it.id) }
    }

    private fun spawn(): ActorPromise {
        return GameEngine.submitCreateActorState(
            InitialActorState(
                name = "Confluence",
                type = ActorType.Effect,
                position = location,
                modelLook = ModelLook.npc(0x9BA),
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
                maxTargetDistance = 10f,
            )
        ).onReady {
            GameV0.interactionManager.registerInteraction(it.id, EschaExitInteraction)
        }
    }

}

