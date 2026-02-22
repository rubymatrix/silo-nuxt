package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.ActorController
import xim.poc.ActorId
import xim.poc.DefaultEnemyController
import xim.poc.WanderingController
import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.configuration.ActorCollisionType
import xim.poc.game.configuration.v0.getEnmityTable
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

open class FamilyAmphiptereBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val stateTransitionTimer = FrameTimer(4.5.seconds, initial = ZERO)

    override fun update(elapsedFrames: Float): List<Event> {
        actorState.targetable = isGrounded()

        stateTransitionTimer.update(elapsedFrames)
        if (!actorState.isDead()) { maybeChangeStates() }
        if (!stateTransitionTimer.isReady()) { return emptyList() }

        return super.update(elapsedFrames)
    }

    override fun selectEngageTarget(): ActorId? {
        if (isLanding() || isFlying()) { return null }
        return super.selectEngageTarget()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return if (isGrounded()) { ActorCollisionType.Object } else { ActorCollisionType.None }
    }

    fun hasAura(): Boolean {
        return actorState.appearanceState == 2
    }

    private fun maybeChangeStates() {
        if (hasAura()) { return }

        val hasEnmity = actorState.getEnmityTable().getHighestEnmityTarget() != null

        if (isFlying() && hasEnmity) {
            actorState.appearanceState = 0
            stateTransitionTimer.reset(4.5.seconds)
        } else if (isGrounded() && !hasEnmity) {
            actorState.appearanceState = 1
            stateTransitionTimer.reset(7.5.seconds)
        }
    }

    fun setAura(enable: Boolean) {
        actorState.appearanceState = if (enable) { 2 } else { 0 }
    }

    fun isLanding(): Boolean {
        return !stateTransitionTimer.isReady()
    }

    private fun isFlying(): Boolean {
        return actorState.appearanceState == 1
    }

    private fun isGrounded(): Boolean {
        return !isLanding() && !isFlying()
    }

}

class FamilyAmphiptereMovementBehavior: ActorController, WanderingController {

    private val delegate = DefaultEnemyController()

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        val behavior = actorState.behaviorController

        return if (behavior is FamilyAmphiptereBehavior && behavior.isLanding()) {
            Vector3f.ZERO
        } else {
            delegate.getVelocity(actorState, elapsedFrames)
        }
    }

    override fun isWandering(): Boolean {
        return delegate.isWandering()
    }

    override fun setWanderDestination(destination: Vector3f) {
        return delegate.setWanderDestination(destination)
    }

}