package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.ActorController
import xim.poc.DefaultEnemyController
import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.event.AutoAttackSubType
import xim.poc.game.event.AutoAttackType
import xim.poc.game.event.Event
import xim.poc.game.event.MainHandAutoAttack

class MobGurfurlurBehavior(actorState: ActorState): V0MonsterController(actorState) {

    override fun onInitialized(): List<Event> {
        actorState.position.copyFrom(Vector3f(x=-60.00f,y=-23.00f,z=15.00f))
        actorState.setRotation(Vector3f.North)
        return emptyList()
    }

    override fun getAutoAttackTypes(): List<AutoAttackType> {
        return listOf(MainHandAutoAttack(subType = AutoAttackSubType.None), MainHandAutoAttack(subType = AutoAttackSubType.H2HOffHand))
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.autoAttackScale = 0.4f
    }

}

class MobGurfurlurController: ActorController {

    private val delegate = DefaultEnemyController()

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        return delegate.getVelocity(actorState, elapsedFrames)
    }

}