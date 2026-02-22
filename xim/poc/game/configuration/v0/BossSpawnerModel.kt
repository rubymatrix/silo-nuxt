package xim.poc.game.configuration.v0

import xim.poc.ActorManager
import xim.poc.ModelLook
import xim.poc.game.ActorState
import xim.resource.DatId

sealed interface BossSpawnerModel {

    val look: ModelLook

    fun show(actorState: ActorState)

    fun hide(actorState: ActorState)

}

object ClassicBossSpawnerModel: BossSpawnerModel {

    override val look: ModelLook
        get() = ModelLook.npc(0x96F)

    override fun show(actorState: ActorState) {
        actorState.targetable = true
        actorState.appearanceState = 1
        ActorManager[actorState.id]?.enqueueModelRoutine(DatId.pop)  // The transition [sp01] is unimplemented...
    }

    override fun hide(actorState: ActorState) {
        actorState.targetable = false
        actorState.appearanceState = 0
    }

}

object FreshBossSpawnerModel: BossSpawnerModel {

    override val look: ModelLook
        get() = ModelLook.npc(0x98F)

    override fun show(actorState: ActorState) {
        actorState.targetable = true
        actorState.appearanceState = 1
    }

    override fun hide(actorState: ActorState) {
        actorState.targetable = false
        actorState.appearanceState = 0
    }

}

object BlueBossSpawnerModel: BossSpawnerModel {

    override val look: ModelLook
        get() = ModelLook.npc(0x90E)

    override fun show(actorState: ActorState) {
        actorState.targetable = true
        ActorManager[actorState.id]?.playRoutine(DatId.main)
    }

    override fun hide(actorState: ActorState) {
        actorState.targetable = false
        ActorManager[actorState.id]?.playRoutine(DatId.kill)
    }

}

// The transitions [sp01] and [sp10] seem to be programmed backwards relative to steady-state...?
object StarryBossSpawnerModel: BossSpawnerModel {

    override val look: ModelLook
        get() = ModelLook.npc(0xAEB)

    override fun show(actorState: ActorState) {
        actorState.targetable = true
        actorState.appearanceState = 1
        ActorManager[actorState.id]?.playRoutine(DatId("efon"))
    }

    override fun hide(actorState: ActorState) {
        actorState.targetable = false
        actorState.appearanceState = 1
        ActorManager[actorState.id]?.playRoutine(DatId("efof"))
    }

}