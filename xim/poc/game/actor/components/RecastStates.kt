package xim.poc.game.actor.components

import xim.poc.game.ActorState
import xim.poc.game.ActorStateComponent
import xim.poc.game.ComponentUpdateResult
import xim.poc.game.configuration.constants.*
import xim.resource.table.AbilityInfoTable.toAbilityInfo
import xim.util.Fps
import kotlin.time.Duration

class RecastState(private var timeRemainingInFrames: Float) {

    fun update(elapsedFrames: Float) {
        timeRemainingInFrames -= elapsedFrames
    }

    fun getRemaining(): Duration {
        return Fps.framesToSecondsRoundedUp(timeRemainingInFrames)
    }

    fun isComplete(): Boolean {
        return timeRemainingInFrames <= 0f
    }

}

class RecastStates: ActorStateComponent {

    private val spellRecastState = HashMap<SpellSkillId, RecastState>()
    private val abilityRecastState = HashMap<Int, RecastState>()

    override fun update(actorState: ActorState, elapsedFrames: Float): ComponentUpdateResult {
        spellRecastState.forEach { it.value.update(elapsedFrames) }
        spellRecastState.entries.removeAll { it.value.isComplete() }

        abilityRecastState.forEach { it.value.update(elapsedFrames) }
        abilityRecastState.entries.removeAll { it.value.isComplete() }

        return ComponentUpdateResult(removeComponent = false)
    }

    fun clear() {
        spellRecastState.clear()
        abilityRecastState.clear()
    }

    fun getRecastState(skill: SkillId): RecastState? {
        return when (skill) {
            is SpellSkillId -> spellRecastState[skill]
            is AbilitySkillId -> abilityRecastState[skill.toAbilityInfo().recastId]
            is MobSkillId -> null
            is ItemSkillId -> null
            is RangedAttackSkillId -> null
        }
    }

    fun addRecastState(skill: SkillId, frameDuration: Float) {
        when (skill) {
            is SpellSkillId -> spellRecastState[skill] = RecastState(frameDuration)
            is AbilitySkillId -> abilityRecastState[skill.toAbilityInfo().recastId] = RecastState(frameDuration)
            else -> return
        }
    }

}

fun ActorState.getRecastStates(): RecastStates {
    return getOrCreateComponentAs(RecastStates::class) { RecastStates() }
}

fun ActorState.setRecastState(skill: SkillId, frameDuration: Float) {
    getRecastStates().addRecastState(skill, frameDuration)
}

fun ActorState.resetRecastStates() {
    getRecastStates().clear()
}

fun ActorState.getRecastDelay(skill: SkillId): RecastState? {
    return getRecastStates().getRecastState(skill)
}
