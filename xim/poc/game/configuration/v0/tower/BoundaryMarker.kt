package xim.poc.game.configuration.v0.tower

import xim.math.Vector3f
import xim.poc.ActorManager
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.configuration.NoActionBehaviorId
import xim.poc.game.event.InitialActorState
import xim.resource.DatId

class BoundaryMarker {

    private var reference: ActorPromise? = null

    private val position = Vector3f()
    private val facingTarget = Vector3f()

    fun update() {
        if (reference.isNullOrObsolete()) { return makeActor() }
        val indicator = ActorStateManager[reference?.getIfReady()] ?: return

        indicator.position.copyFrom(position)
        indicator.faceToward(facingTarget)

        ActorManager[indicator.id]?.syncFromState()
    }

    fun configure(pos: Vector3f, targetPos: Vector3f) {
        position.copyFrom(pos)
        facingTarget.copyFrom(targetPos)
    }

    fun hide() {
        val state = reference?.resolveIfReady() ?: return
        if (!state.visible) { return }

        state.visible = false
        enqueueRoutine(DatId.kill)
    }

    fun show() {
        val state = reference?.resolveIfReady() ?: return
        if (state.visible) { return }

        state.visible = true
        enqueueRoutine(DatId.init)
    }

    private fun makeActor() {
        reference = GameEngine.submitCreateActorState(
            InitialActorState(
                name = "",
                type = ActorType.Effect,
                position = Vector3f(),
                staticPosition = true,
                targetable = false,
                modelLook = ModelLook.npc(0x964),
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
                popRoutines = emptyList(),
            )
        )
    }

    private fun enqueueRoutine(routineId: DatId) {
        reference?.onReady { ActorManager[it.id]?.onReadyToDraw { actor -> actor.enqueueModelRoutine(routineId) } }
    }

}