package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillCannonball_1562
import xim.poc.game.configuration.constants.mskillThermalPulse_1561
import xim.poc.game.configuration.constants.mskillVitriolicSpray_1560
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class FamilyWamouracampaBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val curlTimer = FrameTimer(30.seconds, initial = Random.nextInt(0, 30).seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isIdleOrEngaged()) {
            updateCurlTimer(elapsedFrames)
        }

        return super.update(elapsedFrames)
    }

    override fun getSkills(): List<SkillId> {
        return super.getSkills() + if (isCurled()) {
            listOf(mskillCannonball_1562)
        } else {
            listOf(mskillVitriolicSpray_1560, mskillThermalPulse_1561)
        }
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.movementSpeed -= 10

        if (isCurled()) {
            aggregate.physicalDamageTaken -= 25
        } else {
            aggregate.magicalDamageTaken -= 25
        }
    }

    private fun updateCurlTimer(elapsedFrames: Float) {
        curlTimer.update(elapsedFrames)
        if (!curlTimer.isReady()) { return }

        actorState.appearanceState = 1 - actorState.appearanceState
        curlTimer.reset()
    }

    private fun isCurled(): Boolean {
        return actorState.appearanceState == 1
    }

}