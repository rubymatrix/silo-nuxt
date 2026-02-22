package xim.poc.game.configuration

import xim.math.Vector3f
import xim.poc.ActionTargetFilter
import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.actor.components.getInventory
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluatorContext
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.*
import xim.resource.TargetFlag
import xim.util.Fps
import xim.util.PI_f
import kotlin.time.Duration.Companion.seconds

interface BehaviorId

object NoActionBehaviorId: BehaviorId
object AutoAttackOnlyBehaviorId: BehaviorId

value class MonsterBehaviorId(val id: MonsterId): BehaviorId
value class TrustBehaviorId(val id: Int): BehaviorId
value class NpcBehaviorId(val id: Int): BehaviorId

enum class ActorCollisionType(val canBeMoved: Boolean, val canMoveOthers: Boolean) {
    None(canBeMoved = false, canMoveOthers = false),
    Object(canBeMoved = false, canMoveOthers = true),
    Actor(canBeMoved = true, canMoveOthers = true),
    Passive(canBeMoved = true, canMoveOthers = false),
}

class ActorDamagedContext(
    val damageAmount: Int,
    val attacker: ActorState,
    val skill: SkillId?,
    val skillChainStep: SkillChainStep?,
    val actionContext: AttackContext?,
    val damageType: AttackDamageType,
)

class ActorAttackedContext(
    val attacker: ActorState,
)

class AutoAttackState(val actorState: ActorState) {

    companion object {
        private val filter = ActionTargetFilter(TargetFlag.Enemy.flag)
    }

    private var timeUntilAttack = Fps.toFrames(2.seconds)

    fun update(elapsedFrames: Float) {
        timeUntilAttack -= elapsedFrames

        val targetState = ActorStateManager[actorState.targetState.targetId]

        if (targetState == null || !canAutoAttack(targetState)) {
            timeUntilAttack = timeUntilAttack.coerceAtLeast(Fps.secondsToFrames(2))
        } else if (!isFacingTarget(targetState) || !isInAttackRange(targetState)) {
            timeUntilAttack = timeUntilAttack.coerceAtLeast(Fps.millisToFrames(100))
        }
    }

    fun isReadyToAutoAttack(): Boolean {
        val targetState = ActorStateManager[actorState.targetState.targetId] ?: return false
        return timeUntilAttack <= 0f && canAutoAttack(targetState) && isFacingTarget(targetState) && isInAttackRange(targetState)
    }

    fun isInAttackRange(): Boolean {
        val targetState = ActorStateManager[actorState.targetState.targetId] ?: return false
        return isInAttackRange(targetState)
    }

    fun resetAutoAttack() {
        timeUntilAttack = GameEngine.getAutoAttackRecast(actorState)
    }

    private fun canAutoAttack(targetState: ActorState): Boolean {
        if (!filter.targetFilter(actorState, targetState)) { return false }
        if (!GameEngine.canBeginAction(actorState)) { return false }
        return actorState.isEngaged() && !actorState.isDead() && !actorState.isOccupied()
    }

    private fun isFacingTarget(targetState: ActorState): Boolean {
        if (!actorState.facesTarget) { return true }
        return actorState.isFacingTowards(targetState, halfAngle = PI_f/3f)
    }

    private fun isInAttackRange(targetState: ActorState): Boolean {
        if (actorState.facesTarget && !actorState.isFacingTowards(targetState, halfAngle = PI_f/3f)) { return false }
        return actorState.getTargetingDistance(targetState) < actorState.autoAttackRange
    }

}

data class SkillSelection(
    val skill: SkillId,
    val targetState: ActorState,
)

object SkillSelector {

    fun selectSkill(actorState: ActorState, skills: List<SkillId>, selectedTarget: ActorState? = null): SkillSelection? {
        val validSkills = skills.mapNotNull { isSkillCurrentlyUsable(actorState, selectedTarget, it) }
        val heuristicOutput = SkillHeuristics.chooseSkill(actorState, validSkills) ?: return null
        return SkillSelection(heuristicOutput.skill, heuristicOutput.target)
    }

    private fun isSkillCurrentlyUsable(actorState: ActorState, selectedTarget: ActorState?, skill: SkillId): Pair<SkillId, ActorState>? {
        val inferredTarget = inferSkillTarget(actorState, selectedTarget, skill) ?: return null
        if (!GameEngine.canBeginSkillOnTarget(actorState, inferredTarget, skill)) { return null }
        return skill to inferredTarget
    }

    private fun inferSkillTarget(actorState: ActorState, selectedTarget: ActorState?, skill: SkillId): ActorState? {
        val targetFilter = GameEngine.getSkillTargetFilter(skill)

        if (selectedTarget != null) {
            val selectedIsValid = targetFilter.targetFilter(actorState, selectedTarget)
            return if (selectedIsValid) { selectedTarget } else { null }
        }

        if (targetFilter.targetFilter(actorState, actorState)) { return actorState }

        val inferredTarget = ActorStateManager[actorState.getTargetId()] ?: return null
        return if (targetFilter.targetFilter(actorState, inferredTarget)) { inferredTarget } else { null }
    }

    fun skillToEvent(actorState: ActorState, skill: SkillId, targetId: ActorId): Event? {
        return when (skill) {
            is SpellSkillId -> CastSpellStart(actorState.id, targetId, skill)
            is MobSkillId -> CastMobSkillStart(actorState.id, targetId, skill)
            is AbilitySkillId -> CastAbilityStart(actorState.id, targetId, skill)
            is RangedAttackSkillId -> CastRangedAttackStart(actorState.id, targetId)
            is ItemSkillId ->{
                val item = actorState.getInventory().inventoryItems.firstOrNull { it.id == skill.id } ?: return null
                CastItemStart(actorState.id, targetId, item.internalId)
            }
        }
    }

}

interface ActorBehaviorController {

    fun onInitialized(): List<Event> = emptyList()

    fun update(elapsedFrames: Float): List<Event>

    fun applyBehaviorBonuses(aggregate: CombatBonusAggregate) { }

    fun onAttacked(context: ActorAttackedContext): List<Event> = emptyList()

    fun onIncomingDamage(context: ActorDamagedContext): Int? = null

    fun onDamaged(context: ActorDamagedContext): List<Event> = emptyList()

    fun onDefeated(): List<Event> = emptyList()

    fun onAttackExecuted(actorAttackedEvent: ActorAttackedEvent): List<Event> = emptyList()

    fun onAutoAttackExecuted() { }

    fun getSkillApplierOverride(skillId: SkillId): SkillApplier? = null

    fun onSkillBeginCharging(castingState: CastingState): List<Event> = emptyList()

    fun onSkillExecuted(primaryTargetContext: TargetEvaluatorContext): List<Event> = emptyList()

    fun onSkillInterrupted(skill: SkillId): List<Event> = emptyList()

    fun onStatusEffectGained(statusEffectState: StatusEffectState) { }

    fun getActorCollisionType(): ActorCollisionType = ActorCollisionType.None

    fun ignoresSkillRangeChecks(skill: SkillId): Boolean = false

    fun isRotationLocked(): Boolean = false

    fun hasDirectionalAutoAttacks(): Boolean = false

    fun adjustVelocity(baseVelocity: Vector3f): List<Event> = emptyList()

}

interface ActorMonsterController: ActorBehaviorController {

    fun shouldPerformAggroCheck(): Boolean

    fun performAggroCheck()

}

class NoOpBehaviorController: ActorBehaviorController {
    override fun update(elapsedFrames: Float): List<Event> {
        return emptyList()
    }
}

class AutoAttackController(val actorState: ActorState): ActorBehaviorController {

    private val autoAttackState = AutoAttackState(actorState)

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isStaggerFrozen()) { return emptyList() }

        autoAttackState.update(elapsedFrames)
        if (!autoAttackState.isReadyToAutoAttack()) { return emptyList() }

        autoAttackState.resetAutoAttack()
        return listOf(AutoAttackEvent(actorState.id))
    }

    fun reset() {
        autoAttackState.resetAutoAttack()
    }

    fun isInAttackRange(): Boolean {
        return autoAttackState.isInAttackRange()
    }

}

object ActorBehaviors {

    private val behaviors = HashMap<BehaviorId, (ActorState) -> ActorBehaviorController>()

    init {
        register(NoActionBehaviorId) { NoOpBehaviorController() }
        register(AutoAttackOnlyBehaviorId) { AutoAttackController(it) }
    }

    fun register(monsterId: MonsterId, factory: (ActorState) -> ActorBehaviorController): BehaviorId {
        return register(MonsterBehaviorId(monsterId), factory)
    }

    fun register(behaviorId: BehaviorId, factory: (ActorState) -> ActorBehaviorController): BehaviorId {
        check(behaviors[behaviorId] == null) { "BehaviorId $behaviorId was already defined" }
        behaviors[behaviorId] = factory
        return behaviorId
    }

    fun getOrRegister(behaviorId: BehaviorId, factory: (ActorState) -> ActorBehaviorController): BehaviorId {
        behaviors.getOrPut(behaviorId) { factory }
        return behaviorId
    }

    fun createController(behaviorId: BehaviorId, actorState: ActorState): ActorBehaviorController {
        val factory = behaviors[behaviorId] ?: return NoOpBehaviorController()
        return factory.invoke(actorState)
    }

}