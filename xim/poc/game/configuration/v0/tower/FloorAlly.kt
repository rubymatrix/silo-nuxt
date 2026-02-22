package xim.poc.game.configuration.v0.tower

import xim.math.Vector3f
import xim.poc.game.*
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.MonsterId
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.V0MonsterHelper

class FloorAllyDefinition(
    val allyId: MonsterId,
)

class FloorAlly(val definition: FloorAllyDefinition, val position: Vector3f): FloorEntity {

    private val promise = spawn()

    override fun update(elapsedFrames: Float) {
    }

    override fun cleanup() {
        promise.onReady {
            leaveParty(it)
            GameEngine.submitDeleteActor(it.id)
        }
    }

    private fun leaveParty(actorState: ActorState) {
        val playerParty = PartyManager[ActorStateManager.playerId]
        playerParty.removeMember(actorState.id)
    }

    private fun spawn(): ActorPromise {
        val promise = V0MonsterHelper.spawnMonster(
            monsterDefinition = MonsterDefinitions[definition.allyId],
            actorType = ActorType.AllyNpc,
            position = Vector3f(position),
        )

        promise.onReady {
            val party = PartyManager[ActorStateManager.playerId]
            party.addMember(it.id)
        }

        return promise
    }

}