package xim.poc.game.configuration.v0

import xim.math.Vector3f
import xim.poc.ActorController
import xim.poc.ModelLook
import xim.poc.game.ActorPromise
import xim.poc.game.ActorType
import xim.poc.game.GameState
import xim.poc.game.configuration.MonsterDefinition
import xim.poc.game.event.InitialActorState
import xim.util.PI_f
import xim.util.RandHelper

object V0MonsterHelper {

    fun spawnMonster(
        monsterDefinition: MonsterDefinition,
        position: Vector3f,
        actorType: ActorType = ActorType.Enemy,
        movementControllerFn: () -> ActorController = monsterDefinition.movementControllerFn,
        look: ModelLook = monsterDefinition.look,
        mutator: (InitialActorState) -> InitialActorState = { it },
    ): ActorPromise {
        val initialActorState = InitialActorState(
            monsterId = monsterDefinition.id,
            name = monsterDefinition.name,
            type = actorType,
            position = position,
            modelLook = look,
            rotation = 2 * PI_f * RandHelper.rand(),
            scale = monsterDefinition.customModelSettings.scale,
            targetSize = monsterDefinition.targetSize,
            autoAttackRange = monsterDefinition.autoAttackRange,
            movementController = movementControllerFn.invoke(),
            behaviorController = monsterDefinition.behaviorId,
            appearanceState = monsterDefinition.baseAppearanceState,
            staticPosition = monsterDefinition.staticPosition,
            facesTarget = monsterDefinition.facesTarget,
            targetable = monsterDefinition.targetable,
        )

        val modifiedState = mutator.invoke(initialActorState)
        return GameState.getGameMode().spawnMonster(monsterDefinition.id, modifiedState)
    }

}
