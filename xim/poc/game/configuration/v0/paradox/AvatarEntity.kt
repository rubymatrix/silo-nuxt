package xim.poc.game.configuration.v0.paradox

import xim.math.Vector3f
import xim.poc.game.ActorPromise
import xim.poc.game.GameEngine
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.V0MonsterHelper

class AvatarEntity(val definition: CloisterDefinition): FloorEntity {

    val promise = spawn()

    override fun update(elapsedFrames: Float) {  }

    override fun cleanup() {
        promise.onReady { GameEngine.submitDeleteActor(it.id) }
    }

    private fun spawn(): ActorPromise {
        val monsterDefinition = MonsterDefinitions[definition.monsterId]
        return V0MonsterHelper.spawnMonster(monsterDefinition = monsterDefinition, position = Vector3f(definition.enemyLocation)).onReady {
            it.faceDirection(definition.facingDirection)
        }
    }

}