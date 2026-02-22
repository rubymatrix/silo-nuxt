package xim.poc.game.configuration.assetviewer

import xim.poc.ActorId
import xim.poc.game.ActorState
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.*
import xim.poc.game.actor.components.getInventory
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.seconds

object AssetViewerMonsterBehaviorId: BehaviorId

class AssetViewerMonsterBehavior(val actorState: ActorState): ActorMonsterController {

    private val autoAttackDelegate = AutoAttackController(actorState)

    private val skillCooldownTimer = FrameTimer(15.seconds, initial = 3.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actorState.isEngaged() || actorState.isOccupied()) { return emptyList() }

        skillCooldownTimer.update(elapsedFrames)
        if (skillCooldownTimer.isReady()) {
            val skillEvent = useSkill()
            if (skillEvent.isNotEmpty()) { return skillEvent }
        }

        return autoAttackDelegate.update(elapsedFrames)
    }

    override fun onAttacked(context: ActorAttackedContext): List<Event> {
        if (actorState.isEngaged()) { return emptyList() }
        return listOf(BattleEngageEvent(
            sourceId = actorState.id,
            targetId = context.attacker.id,
        ))
    }

    override fun shouldPerformAggroCheck(): Boolean {
        return false
    }

    override fun performAggroCheck() {
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        skillCooldownTimer.reset()
        return super.onSkillExecuted(primaryTargetContext)
    }

    private fun useSkill(): List<Event> {
        val skills = MonsterDefinitions[actorState.monsterId]?.mobSkills ?: return emptyList()
        val skillSelection = SkillSelector.selectSkill(actorState, skills = skills) ?: return emptyList()
        return listOfNotNull(SkillSelector.skillToEvent(actorState, skillSelection.skill, skillSelection.targetState.id))
    }

}