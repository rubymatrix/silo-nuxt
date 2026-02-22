package xim.poc.game.configuration.v0.pet

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.GameEngine
import xim.poc.game.configuration.ActorCollisionType
import xim.poc.game.configuration.v0.CombatBonusAggregator
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.getNullableEnmityTable
import xim.poc.game.event.Event

abstract class V0PetBehavior(actorState: ActorState): V0MonsterController(actorState) {

    final override fun update(elapsedFrames: Float): List<Event> {
        val owner = ActorStateManager[actorState.owner]
        if (owner == null || owner.isDead()) {
            GameV0Helpers.defeatActor(actorState)
            return emptyList()
        }

        if (actorState.isIdle() && Vector3f.distance(owner.position, actorState.position) > 20f) {
            actorState.position.copyFrom(owner.position - Vector3f.X * 2f)
        }

        if (!actorState.isOccupied() && !actorState.isDead() && actorState.velocity.magnitude() < 1e-5f) {
            actorState.faceToward(owner)
        }

        if (actorState.isOccupied()) {
            return emptyList()
        }

        return super.update(elapsedFrames) + updatePetBehavior(elapsedFrames)
    }

    abstract fun updatePetBehavior(elapsedFrames: Float): List<Event>

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.refresh += 1
        aggregate.tpRequirementBypass = true

        val owner = getOwner() ?: return
        aggregate.movementSpeed = CombatBonusAggregator[owner].movementSpeed
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.None
    }

    override fun selectEngageTarget(): ActorId? {
        val owner = getOwner() ?: return null
        if (!owner.isEngaged()) { return null }

        val target = ActorStateManager[owner.getTargetId()] ?: return null
        if (target.getNullableEnmityTable()?.hasEnmity(owner.id) != true) { return null }

        return target.id
    }

    override fun onReadyToAutoAttack(): List<Event>? {
        return emptyList()
    }

    fun getOwner(): ActorState? {
        return ActorStateManager[actorState.owner]
    }

}