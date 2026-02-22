package xim.poc.game.configuration.v0.behaviors.zitah

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.ActorController
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.behaviors.zitah.MobAlpluachraController.Companion.syncedSkills
import xim.poc.game.configuration.v0.constants.mobBucca_288_330
import xim.poc.game.configuration.v0.constants.mobPuca_288_335
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.Event
import xim.poc.velocityVectorTo
import xim.resource.AoeType
import xim.util.FrameTimer
import xim.util.RandHelper
import xim.util.interpolate
import xim.util.toRads
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MobAlpluachraController(actorState: ActorState): V0MonsterController(actorState) {

    companion object {
        val syncedSkills = listOf(mskillNornArrows_2262, mskillEldritchWind_2566)
    }

    private val skillTimer = FrameTimer(6.seconds)
    private val syncedSkillTimer = FrameTimer(45.seconds, initial = 10.seconds)

    private val assistants = ArrayList<ActorPromise>()

    override fun onInitialized(): List<Event> {
        spawnAssistants()

        dynamicStatusResistance = StatusResistanceTracker().also {
            it[StatusEffect.Stun] = OccurrenceLimitStrategy(resetInterval = 10.minutes, maxOccurrencesInInterval = 3)
        }

        return super.onInitialized()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        skillTimer.update(elapsedFrames)
        syncedSkillTimer.update(elapsedFrames)
        return super.update(elapsedFrames)
    }

    override fun getSkills(): List<SkillId> {
        return if (syncedSkillTimer.isReady()) { syncedSkills } else { super.getSkills() }
    }

    override fun wantsToUseSkill(): Boolean {
        return skillTimer.isReady() || super.wantsToUseSkill()
    }

    override fun onSkillBeginCharging(castingState: CastingState): List<Event> {
        if (syncedSkills.contains(castingState.skill)) { syncedSkillTimer.reset() }
        getAssistants().forEach { it.beginAction(castingState.skill) }
        return emptyList()
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        return if (syncedSkills.contains(skill)) { 2.7.seconds } else { null }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        skillTimer.reset()
        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify)
        aggregate.spellInterruptDown += 100
        aggregate.knockBackResistance += 100
        aggregate.refresh += 10
        aggregate.tpRequirementBypass = true
    }

    private fun spawnAssistants() {
        assistants += V0MonsterHelper.spawnMonster(
            monsterDefinition = MonsterDefinitions[mobBucca_288_330],
            position = Vector3f(actorState.position + Vector3f.X * 2f),
            actorType = ActorType.Enemy,
        )

        assistants += V0MonsterHelper.spawnMonster(
            monsterDefinition = MonsterDefinitions[mobPuca_288_335],
            position = Vector3f(actorState.position - Vector3f.X * 2f),
            actorType = ActorType.Enemy,
        )

        assistants.forEach { assistant -> assistant.onReady {
            (it.behaviorController as? AssistantController)?.leader = actorState
        } }
    }

    private fun getAssistants(): List<AssistantController> {
        return assistants.mapNotNull { it.resolveIfReady()?.behaviorController as? AssistantController }
    }

}

class MobBuccaController(actorState: ActorState): AssistantController(actorState)

class MobPucaController(actorState: ActorState): AssistantController(actorState)

abstract class AssistantController(actorState: ActorState): V0MonsterController(actorState) {

    lateinit var leader: ActorState
    private var queuedSkill: SkillId? = null

    private val spellMidpointPosition = Vector3f()
    private val spellFinalPosition = Vector3f()

    override fun onInitialized(): List<Event> {
        spellTimer = FrameTimer(10.seconds, initial = 5.seconds)
        return emptyList()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actorState.isDead() && leader.isDead()) {
            GameV0Helpers.defeatActor(actorState)
            return emptyList()
        }

        actorState.syncEnmity(syncSource = leader)
        adjustCastingPosition()
        maybeEagerlyCompleteSpell()

        return super.update(elapsedFrames)
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun wantsToUseSkill(): Boolean {
        return queuedSkill is MobSkillId
    }

    override fun getSkills(): List<SkillId> {
        return listOfNotNull(queuedSkill)
    }

    override fun wantsToCastSpell(): Boolean {
        return queuedSkill is SpellSkillId
    }

    override fun onSkillBeginCharging(castingState: CastingState): List<Event> {
        val targetCenter = castingState.context.targetAoeCenter ?: return emptyList()
        targetCenter.copyFrom(actorState.position)

        val target = ActorStateManager[castingState.targetId] ?: return emptyList()
        val offset = Vector3f(4f * RandHelper.rand(), 0f, 4f * RandHelper.rand())
        spellFinalPosition.copyFrom(target.position + offset)

        val direction = spellFinalPosition - actorState.position
        val midpoint = (actorState.position + spellFinalPosition) * 0.5f
        val cross = Vector3f.Y.cross(direction.normalize()).normalizeInPlace()
        spellMidpointPosition.copyFrom(midpoint + cross * curvature())

        return emptyList()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        queuedSkill = null
        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.refresh += 10
        aggregate.movementSpeed = -33
        aggregate.tpRequirementBypass = true
        aggregate.fastCast += 15.interpolate(0, leader.getHpp())
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.None
    }

    override fun ignoresSkillRangeChecks(skill: SkillId): Boolean {
        return true
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        if (skill !is SpellSkillId) { return null }
        return SkillRangeInfo(maxTargetDistance = 50f, effectRadius = 4f, type = AoeType.Target, tracksTarget = false)
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        return if (syncedSkills.contains(skill)) { 2.7.seconds } else { null }
    }

    fun beginAction(skill: SkillId) {
        queuedSkill = if (syncedSkills.contains(skill)) { skill } else { getSpells().random() }
    }

    private fun curvature(): Float {
        return RandHelper.sign() * RandHelper.posRand(10f)
    }

    private fun maybeEagerlyCompleteSpell() {
        val castingState = actorState.getCastingState() ?: return
        if (!castingState.isCharging() || castingState.skill !is SpellSkillId) { return }

        val target = ActorStateManager[castingState.targetId] ?: return

        if (!SkillTargetEvaluator.isInEffectRange(
            rangeInfo = GameV0.getSkillRangeInfo(actorState, castingState.skill),
            source = actorState,
            primaryTarget = target,
            additionalTarget = target,
        )) { return }

        castingState.eagerlyComplete()
    }

    private fun adjustCastingPosition() {
        val castingState = actorState.getCastingState() ?: return
        if (!castingState.isCharging()) { return }

        val targetCenter = castingState.context.targetAoeCenter ?: return
        val progress = castingState.progress()

        if (progress < 0.5f) {
            targetCenter.copyFrom(Vector3f.catmullRomSpline(
                pPrev = actorState.position,
                p0 = actorState.position,
                p1 = spellMidpointPosition,
                pNext = spellFinalPosition,
                t = progress * 2f
            ))
        } else {
            targetCenter.copyFrom(Vector3f.catmullRomSpline(
                pPrev = actorState.position,
                p0 = spellMidpointPosition,
                p1 = spellFinalPosition,
                pNext = spellFinalPosition,
                t = (progress - 0.5f) * 2f
            ))
        }
    }

}

class ForcedWanderController: ActorController {

    private var angle = 0f

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        if (actorState.isOccupied()) { return Vector3f.ZERO }

        angle = (angle + elapsedFrames * 0.5f).mod(360f)

        val destination = chooseNextPosition(actorState) ?: return Vector3f.ZERO
        if (Vector3f.distance(actorState.position, destination) < 0.5f) { return Vector3f.ZERO }

        val desiredPosition = GameState.getGameMode().pathNextPosition(actorState, elapsedFrames, destination) ?: destination
        return velocityVectorTo(actorState.position, desiredPosition, actorState.getMovementSpeed(), elapsedFrames)
    }

    private fun chooseNextPosition(actorState: ActorState): Vector3f? {
        val target = ActorStateManager[actorState.getTargetId()] ?: return null

        val desiredOffset = when (actorState.monsterId) {
            mobBucca_288_330 -> Vector3f(-10f, 0f, 0f)
            mobPuca_288_335 -> Vector3f(10f, 0f, 0f)
            else -> return null
        }

        Matrix4f().rotateYInPlace(angle.toRads()).transformInPlace(desiredOffset)
        return GameV0.getWanderPosition(actorState, desiredEnd = target.position + desiredOffset)
    }

}