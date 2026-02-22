package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.MobSkills
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.constants.mobExplosive_26
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.Event
import xim.resource.SpellElement
import xim.resource.TerrainType
import xim.resource.table.SpellInfoTable.toSpellInfo
import xim.util.Fps
import xim.util.FrameTimer
import xim.util.multiplyInPlace
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class MobWarmachineController(actorState: ActorState): V0MonsterController(actorState) {

    private val spawnedBombs = ArrayList<ActorPromise>()

    private val skillTable = WeightedTable(
        mskillBlastbomb_382 to 0.5,
        mskillFirebomb_381 to 0.25,
        mskillFountain_383 to 0.15,
        mskillBurst_379 to 0.05,
        mskillFlameArrow_380 to 0.05,
    )

    override fun getSkills(): List<SkillId> {
        return listOf(skillTable.getRandom())
    }

    override fun wantsToUseSkill(): Boolean {
        return actorState.getTpp() > 0.3
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.statusDurationMultipliers.multiplyInPlace(StatusEffect.Sleep, 0.33f)
        aggregate.movementSpeed = -30
        aggregate.storeTp += 20
    }

    override fun onDefeated(): List<Event> {
        spawnedBombs.forEach { it.onReady(GameV0Helpers::defeatActor) }
        return emptyList()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill !is MobSkillId) { return emptyList() }
        val skillRange = MobSkills[primaryTargetContext.skill].rangeInfo

        val bombsInEffectRange = spawnedBombs.mapNotNull { it.resolveIfReady() }
            .filter { SkillTargetEvaluator.isInEffectRange(rangeInfo = skillRange, source = actorState, primaryTarget = primaryTargetContext.targetState, additionalTarget = it) }
            .mapNotNull { it.behaviorController as? MobWarmachineExplosiveController }

        if (primaryTargetContext.skill == mskillFirebomb_381 || primaryTargetContext.skill == mskillBlastbomb_382) {
            bombsInEffectRange.forEach { it.setExplosionTimer(ZERO) }
        }

        if (primaryTargetContext.skill == mskillFountain_383) {
            bombsInEffectRange.forEach { it.disarm() }
        }

        if (primaryTargetContext.skill == mskillBlastbomb_382) {
            val position = actorState.getCastingState()?.context?.targetAoeCenter ?: actorState.position
            AttackContext.compose(primaryTargetContext.context) { spawnBomb(position) }
        }

        return emptyList()
    }

    private fun spawnBomb(position: Vector3f) {
        val monsterDefinition = MonsterDefinitions[mobExplosive_26]

        spawnedBombs += V0MonsterHelper.spawnMonster(
            monsterDefinition = monsterDefinition,
            position = position,
            actorType = ActorType.Enemy,
        ).onReady { it.syncEnmity(actorState) }
    }

}

class MobWarmachineExplosiveController(actorState: ActorState): V0MonsterController(actorState) {

    private val explodeTimer = FrameTimer(30.seconds)
    private var disarmed = false

    override fun update(elapsedFrames: Float): List<Event> {
        if (isInWater()) { disarm() }

        if (disarmed) {
            GameV0Helpers.defeatActor(actorState)
            return emptyList()
        }

        explodeTimer.update(elapsedFrames)
        return super.update(elapsedFrames)
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun wantsToUseSkill(): Boolean {
        return explodeTimer.isReady()
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.None
    }

    override fun ignoresSkillRangeChecks(skill: SkillId): Boolean {
        return true
    }

    override fun onDamaged(context: ActorDamagedContext): List<Event> {
        if (context.skill is SpellSkillId && context.skill.toSpellInfo().element == SpellElement.Water) {
            AttackContext.compose(context.actionContext) { disarm() }
        }
        return emptyList()
    }

    fun setExplosionTimer(duration: Duration) {
        explodeTimer.reset(duration)
    }

    fun disarm() {
        disarmed = true
    }

    private fun isInWater(): Boolean {
        if (actorState.age < Fps.toFrames(5.seconds)) { return false }

        for ((_, result) in actorState.lastCollisionResult.collisionsByArea) {
            if (result.any { it.terrainType == TerrainType.DeepWater || it.terrainType == TerrainType.ShallowWater }) {
                return true
            }
        }

        return false
    }

}