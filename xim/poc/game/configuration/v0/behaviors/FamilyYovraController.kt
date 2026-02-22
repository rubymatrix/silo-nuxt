package xim.poc.game.configuration.v0.behaviors

import xim.poc.ActorManager
import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.Event
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

object YovraSkills {

    fun luminousDrape(): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            if (it.targetState.id != it.sourceState.id) { it.targetState.syncEnmity(it.sourceState) }
            emptyList()
        }
    }

}

class FamilyYovraController(actorState: ActorState): V0MonsterController(actorState) {

    private var hasDescended = false

    private val stateChangeTime = Fps.toFrames(2.5.seconds)
    private var stateChangeTimer = stateChangeTime

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isDead()) { return emptyList() }

        val renderState = ActorManager[actorState.id]?.renderState
        renderState?.forceHideShadow = actorState.appearanceState != 2

        return if (stateChangeTimer > 0f) {
            stateChangeTimer -= elapsedFrames
            emptyList()
        } else if (!actorState.isEngaged() && hasDescended) {
            hasDescended = false
            stateChangeTimer = stateChangeTime
            actorState.appearanceState = 3
            emptyList()
        } else if (actorState.isEngaged() && !hasDescended) {
            hasDescended = true
            stateChangeTimer = stateChangeTime
            actorState.appearanceState = 2
            emptyList()
        } else {
            super.update(elapsedFrames)
        }
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        if (stateChangeTimer > 0f) {
            aggregate.movementSpeed -= 100
        }

    }

}