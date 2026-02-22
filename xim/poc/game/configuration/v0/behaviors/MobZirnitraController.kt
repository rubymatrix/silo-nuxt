package xim.poc.game.configuration.v0.behaviors

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillReavingWind_2175
import xim.poc.game.configuration.constants.mskill_2178
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.behaviors.MobZirnitraController.Companion.auraKnockbackCooldown
import xim.poc.game.configuration.v0.constants.mobZirnitrasFetter_121
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.Event
import xim.util.Fps
import xim.util.FrameTimer
import xim.util.PI_f
import kotlin.time.Duration.Companion.seconds

class MobZirnitraController(actorState: ActorState): FamilyAmphiptereBehavior(actorState) {

    private val spawnedMonsters = ArrayList<ActorPromise>()
    private val auraTimer = FrameTimer(60.seconds)
    private var reavingWindCounter = 0

    companion object {
        val auraKnockbackCooldown = FrameTimer(2.seconds)
    }

    override fun onInitialized(): List<Event> {
        actorState.faceToward(ActorStateManager.player())
        return super.onInitialized()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        spawnedMonsters.removeAll { it.isObsolete() }

        auraKnockbackCooldown.update(elapsedFrames)

        auraTimer.update(elapsedFrames)
        if (hasAura() && auraTimer.isReady()) {
            setAura(enable = false)
            return emptyList()
        }

        return super.update(elapsedFrames)
    }

    override fun getSkills(): List<SkillId> {
        return if (wantsToUseReavingWind()) { listOf(mskillReavingWind_2175) } else { super.getSkills() - mskillReavingWind_2175 }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillReavingWind_2175) {
            reavingWindCounter += 1
            auraTimer.reset()
            auraKnockbackCooldown.reset()
            spawnFetters()
            setAura(enable = true)
        }

        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun onDefeated(): List<Event> {
        for (promise in spawnedMonsters) {
            promise.onReady(GameV0Helpers::defeatActor)
        }
        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Bind)
    }

    private fun wantsToUseReavingWind(): Boolean {
        return !hasAura() && when (reavingWindCounter) {
            0 -> actorState.getHpp() < 0.95f
            1 -> actorState.getHpp() < 0.45f
            else -> false
        }
    }

    private fun spawnFetters() {
        val rotation = Matrix4f().rotateYInPlace(actorState.rotation)

        val radiusInner = 6f
        spawnFetter(2 * PI_f, rotation.transform(Vector3f(radiusInner, 0f, 0f)))
        spawnFetter(2 * PI_f, rotation.transform(Vector3f(-radiusInner, 0f, 0f)))

        val radiusOuter = 16f
        spawnFetter(4 * PI_f, rotation.transform(Vector3f(radiusOuter, 0f, 0f)))
        spawnFetter(4 * PI_f, rotation.transform(Vector3f(-radiusOuter, 0f, 0f)))
        spawnFetter(4 * PI_f, rotation.transform(Vector3f(0f, 0f, radiusOuter)))
        spawnFetter(4 * PI_f, rotation.transform(Vector3f(0f, 0f, -radiusOuter)))
    }

    private fun spawnFetter(totalRotation: Float, position: Vector3f) {
        val fetterDefinition = MonsterDefinitions[mobZirnitrasFetter_121]

        spawnedMonsters += V0MonsterHelper.spawnMonster(
            monsterDefinition = fetterDefinition,
            position = position,
            actorType = ActorType.Enemy,
            movementControllerFn = { NoOpActorController() },
        ).onReady {
            it.syncEnmity(actorState)
            (it.behaviorController as MobZirnitraFetterController).totalRotation = totalRotation
        }
    }
}

class MobZirnitraFetterController(actorState: ActorState): V0MonsterController(actorState) {

    private val lifeTimer = FrameTimer(60.seconds)
    private val initialPosition = Vector3f()
    private var counter = 0f

    var totalRotation = 0f

    override fun onInitialized(): List<Event> {
        initialPosition.copyFrom(actorState.position)
        return super.onInitialized()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isDead()) { return emptyList() }

        lifeTimer.update(elapsedFrames)
        if (lifeTimer.isReady()) {
            GameV0Helpers.defeatActor(actorState)
            return emptyList()
        }

        counter += elapsedFrames
        val totalFrames = Fps.toFrames(60.seconds)
        val rotation = (counter/totalFrames) * totalRotation

        actorState.position.copyFrom(Matrix4f().rotateYInPlace(rotation).transform(initialPosition))
        actorState.faceToward(Vector3f(0f, 0f, 0f))

        return super.update(elapsedFrames)
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun wantsToUseSkill(): Boolean {
        if (!auraKnockbackCooldown.isReady()) { return false }

        val target = ActorStateManager[actorState.getTargetId()] ?: return false
        return actorState.getTargetingDistance(target) <= 4f
    }

    override fun selectSkill(): SkillSelection? {
        return SkillSelector.selectSkill(actorState, listOf(mskill_2178))
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskill_2178) { auraKnockbackCooldown.reset() }
        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.None
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.magicAttackBonus += 100
    }

}