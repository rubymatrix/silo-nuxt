package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.configuration.ActorCollisionType
import xim.poc.game.configuration.SkillSelection
import xim.poc.game.configuration.SkillSelector
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.event.Event
import xim.resource.TargetFlag
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.seconds

class FamilyObstacleBehavior(actorState: ActorState, val skillId: SkillId): V0MonsterController(actorState) {

    private val skillTimer = FrameTimer(30.seconds).resetRandom(lowerBound = 10.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        skillTimer.update(elapsedFrames)
        return super.update(elapsedFrames)
    }

    override fun wantsToUseSkill(): Boolean {
        return skillTimer.isReady()
    }

    override fun selectSkill(): SkillSelection? {
        skillTimer.reset()
        return SkillSelector.selectSkill(actorState, listOf(skillId), ActorStateManager.player())
    }

    override fun getSkillEffectedTargetType(skillId: SkillId): Int {
        return TargetFlag.Enemy.flag
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.None
    }

}