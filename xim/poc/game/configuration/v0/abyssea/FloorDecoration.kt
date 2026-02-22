package xim.poc.game.configuration.v0.abyssea

import xim.math.Vector3f
import xim.poc.ModelLook
import xim.poc.game.ActorPromise
import xim.poc.game.ActorType
import xim.poc.game.GameEngine
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.event.InitialActorState

class DecorationActorConfig(
    val look: ModelLook,
    val position: Vector3f,
    val rotation: Float = 0f,
    val scale: Float = 1f,
)

class FloorDecoration(val config: DecorationActorConfig): FloorEntity {

    val promise = spawn()

    override fun update(elapsedFrames: Float) {
    }

    override fun cleanup() {
        promise.onReady { GameEngine.submitDeleteActor(it.id) }
    }

    private fun spawn(): ActorPromise {
        return GameEngine.submitCreateActorState(InitialActorState(
            name = "",
            modelLook = config.look,
            position = Vector3f(config.position),
            rotation = config.rotation,
            scale = config.scale,
            type = ActorType.StaticNpc,
            targetable = false,
        ))
    }

}