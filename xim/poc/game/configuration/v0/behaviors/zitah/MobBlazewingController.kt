package xim.poc.game.configuration.v0.behaviors.zitah

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.ActorController
import xim.poc.game.*
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.OccurrenceLimitStrategy
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.StatusResistanceTracker
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.constants.mobBlazewingFly_288_504
import xim.poc.game.event.Event
import xim.poc.velocityVectorTo
import xim.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MobBlazewingController(actorState: ActorState): V0MonsterController(actorState) {

    private val flies = ArrayList<ActorPromise>()
    private val flyTimer = FrameTimer(20.seconds, initial = 2.seconds)

    private var fliesConsumed = 0

    private var deathReady = false

    private var framesSinceOccupied = 0f
    private val originalJumpPosition = Vector3f()

    override fun onInitialized(): List<Event> {
        dynamicStatusResistance = StatusResistanceTracker().also {
            it[StatusEffect.Stun] = OccurrenceLimitStrategy(resetInterval = 10.minutes, maxOccurrencesInInterval = 3)
        }
        return super.onInitialized()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isDead()) { return emptyList() }

        if (actorState.isOccupied()) {
            framesSinceOccupied = 0f
        } else {
            framesSinceOccupied += elapsedFrames
        }

        handleJumping()

        flyTimer.update(elapsedFrames)
        if (flyTimer.isReady()) { spawnFly() }

        return super.update(elapsedFrames)
    }

    override fun wantsToUseSkill(): Boolean {
        return wantsToJump() || wantsToUseDeath() || wantsToUseExorender() || super.wantsToUseSkill()
    }

    override fun getSkills(): List<SkillId> {
        return if (wantsToUseDeath()) {
            listOf(mskillDeathProphet_2500)
        } else if (wantsToUseExorender()) {
            listOf(mskillExorender_2630)
        } else if (wantsToJump()) {
            listOf(mskillMantidAutoAttack_2494)
        } else {
            super.getSkills()
        }
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify)
        aggregate.knockBackResistance += 100

        aggregate.multiplicativeStats.multiplyInPlace(CombatStat.str, 1f + fliesConsumed * 0.05f)
        aggregate.mobSkillFastCast += 20 * fliesConsumed

        if (wantsToUseDeath() || wantsToUseExorender()) { aggregate.tpRequirementBypass = true }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillExorender_2630) {
            consumeNearbyFlies(primaryTargetContext.context)
        } else if (primaryTargetContext.skill == mskillDeathProphet_2500) {
            deathReady = false
        } else if (primaryTargetContext.skill == mskillPreyingPosture_2499) {
            deathReady = true
        } else if (primaryTargetContext.skill == mskillMantidAutoAttack_2494) {
            originalJumpPosition.copyFrom(actorState.position)
        }

        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        return when (skill) {
            mskillExorender_2630 -> 2.seconds
            mskillDeathProphet_2500 -> 0.seconds
            else -> null
        }
    }

    override fun ignoresSkillRangeChecks(skill: SkillId): Boolean {
        return wantsToUseExorender()
    }

    override fun getAutoAttackAbilities(): List<SkillId> {
        return listOf(mskillMantidAutoAttack_2492, mskillMantidAutoAttack_2493)
    }

    private fun spawnFly() {
        flies.removeAll { it.isObsolete() }
        if (flies.size >= 8f) { return }

        flyTimer.reset()

        val rotation = PI_f * RandHelper.rand()
        val offset = Matrix4f().rotateYInPlace(rotation).transform(Vector3f.X) * 15f

        flies += V0MonsterHelper.spawnMonster(
            monsterDefinition = MonsterDefinitions[mobBlazewingFly_288_504],
            position = actorState.position + offset,
        ).onReady {
            (it.behaviorController as? MobBlazewingFlyController)?.leader = actorState
        }
    }

    private fun getNearbyFlies(): List<ActorState> {
        return flies.filter { !it.isObsolete() }
            .mapNotNull { it.resolveIfReady() }
            .filter { it.getTargetingDistance(actorState) <= 3f }
    }

    private fun consumeNearbyFlies(context: AttackContext) {
        val nearbyFlies = getNearbyFlies()
        nearbyFlies.forEach(GameV0Helpers::defeatActor)

        if (nearbyFlies.isEmpty()) { return }

        fliesConsumed += nearbyFlies.size
        AttackContext.compose(context) { MiscEffects.playEffect(actorState.id, MiscEffects.Effect.LevelUp)  }
    }

    private fun wantsToJump(): Boolean {
        if (actorState.timeSinceCreate() < 3.seconds) { return false }
        if (Fps.framesToSeconds(framesSinceOccupied) < 0.1.seconds) { return false }

        val target = ActorStateManager[actorState.getTargetId()] ?: return false
        val targetDistance = actorState.getTargetingDistance(target)

        return targetDistance >= 8f && targetDistance < 20f
    }

    private fun wantsToUseExorender(): Boolean {
        return getNearbyFlies().isNotEmpty()
    }

    private fun wantsToUseDeath(): Boolean {
        return deathReady && actorState.hasStatusEffect(StatusEffect.Warcry)
    }

    private fun handleJumping() {
        val castingState = actorState.getCastingState() ?: return
        if (castingState.skill != mskillMantidAutoAttack_2494) { return }

        val progress = castingState.getTotalProgress()
        if (progress > 0.5f) { return }

        val targetAoe = castingState.context.targetAoeCenter ?: return
        val delta = (2.5f * progress).coerceAtMost(0.925f)

        actorState.position.copyFrom(Vector3f.lerp(originalJumpPosition, targetAoe, delta))
    }

}

class MobBlazewingFlyController(actorState: ActorState): V0MonsterController(actorState) {

    lateinit var leader: ActorState

    override fun update(elapsedFrames: Float): List<Event> {
        if (leader.isDead()) { GameV0Helpers.defeatActor(actorState) }
        return super.update(elapsedFrames)
    }

    override fun wantsToUseSkill(): Boolean {
        return false
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.movementSpeed -= 50
    }

}

class MobBlazewingFlyMovementController: ActorController {

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        val behavior = actorState.behaviorController as? MobBlazewingFlyController ?: return Vector3f.ZERO

        val distance = actorState.getTargetingDistance(behavior.leader)
        if (distance < 3f) { return Vector3f.ZERO }

        val nextPosition = GameV0.pathNextPosition(actorState, elapsedFrames, behavior.leader.position)
            ?: behavior.leader.position

        return velocityVectorTo(actorState.position, nextPosition, actorState.getMovementSpeed(), elapsedFrames)
    }

}