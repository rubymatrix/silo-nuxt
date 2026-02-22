package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.ActorController
import xim.poc.DefaultEnemyController
import xim.poc.EnvironmentManager
import xim.poc.game.ActorPromise
import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.standardSingleTargetRange
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.standardStaticTargetAoe
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.constants.mobWagglingWasp_11
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.Event
import xim.poc.velocityVectorTo
import xim.resource.DatId
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.seconds

private val droningPosition = Vector3f(x=-143.68f,y=0.35f,z=419.17f)

private val beeSpawnPositions = listOf(
    Vector3f(x = -127.28f, y = 0.28f, z = 432.12f),
    Vector3f(x = -142.94f, y = 0.50f, z = 406.27f),
    Vector3f(x = -150.89f, y = 0.55f, z = 429.68f),
)

class MobColkhabController(actorState: ActorState): V0MonsterController(actorState) {

    private val incisiveCooldown = FrameTimer(10.seconds)
    private var incisiveCounter = 0

    private val spawnedBees = ArrayList<ActorPromise>()

    private var droningCounter = 0

    override fun update(elapsedFrames: Float): List<Event> {
        adjustWeather()

        actorState.appearanceState = if (auraEnabled()) { 1 } else { 0 }
        actorState.targetable = !auraEnabled()

        if (auraEnabled()) { incisiveCooldown.update(elapsedFrames) }

        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Stun, StatusEffect.Sleep, StatusEffect.Bind)
        aggregate.knockBackResistance += 100
        aggregate.movementSpeed += 25
        aggregate.tpRequirementBypass = true
        aggregate.refresh += 10

        if (wantsToUseDroningWhirlwind()) {
            aggregate.physicalDamageTaken -= 90
            aggregate.magicalDamageTaken -= 90
        }
    }

    override fun wantsToUseSkill(): Boolean {
        return if (wantsToUseDroningWhirlwind()) {
            Vector3f.distance(actorState.position, droningPosition) < 1f
        } else if (auraEnabled()) {
            incisiveCooldown.isReady()
        } else {
            return actorState.getTpp() >= 0.5f
        }
    }

    override fun selectSkill(): SkillSelection? {
        val skills = if (wantsToUseDroningWhirlwind()) {
            listOf(mskillDroningWhirlwind_2749)
        } else if (auraEnabled()) {
            val skill = if (incisiveCounter % 2 == 0) { mskillIncisiveDenouement_2750 } else { spellAeroga_184 }
            listOf(skill)
        } else {
            listOf(mskillMandibularLashing_2746, mskillVespineHurricane_2747, mskillStingerVolley_2748)
        }

        return SkillSelector.selectSkill(actorState, skills)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        when (primaryTargetContext.skill) {
            mskillDroningWhirlwind_2749 -> {
                droningCounter += 1
                spawnBees()
                incisiveCooldown.reset()
            }
            mskillIncisiveDenouement_2750, spellAeroga_184 -> {
                incisiveCounter += 1
                incisiveCooldown.reset()
            }
            else -> { }
        }

        return emptyList()
    }

    override fun onDefeated(): List<Event> {
        actorState.appearanceState = 0
        adjustWeather()
        return emptyList()
    }

    override fun onReadyToAutoAttack(): List<Event>? {
        return if (auraEnabled() || wantsToUseDroningWhirlwind()) { emptyList() } else { super.onReadyToAutoAttack() }
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        return if (skill == spellAeroga_184) {
            standardStaticTargetAoe.copy(maxTargetDistance = 100f)
        } else {
            super.getSkillRangeOverride(skill)
        }
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.Object
    }

    fun getDesiredPosition(): Vector3f? {
        return if (wantsToUseDroningWhirlwind() || auraEnabled()) {
            Vector3f(x=-143.68f,y=0.35f,z=419.17f)
        } else {
            null
        }
    }

    private fun adjustWeather() {
        if (actorState.appearanceState == 0) {
            EnvironmentManager.switchWeather(DatId.weatherSunny)
        } else if (actorState.appearanceState == 1) {
            EnvironmentManager.switchWeather(DatId.weatherWindy)
        }
    }

    private fun wantsToUseDroningWhirlwind(): Boolean {
        val threshold = 1f - 0.25f * (droningCounter + 1)
        return actorState.getHpp() < threshold
    }

    private fun spawnBees() {
        (0 until droningCounter).forEach { spawnBee(it) }
    }

    private fun spawnBee(index: Int) {
        val monsterDefinition = MonsterDefinitions[mobWagglingWasp_11]
        val position = beeSpawnPositions[index]

        spawnedBees += V0MonsterHelper.spawnMonster(
            monsterDefinition = monsterDefinition,
            position = position,
        ).onReady {
            it.syncEnmity(actorState)
        }
    }

    private fun auraEnabled(): Boolean {
        return spawnedBees.any { it.isAlive() }
    }

}

class MobColkhabMovementController: ActorController {

    private val defaultDelegate = DefaultEnemyController()

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        val behaviorController = actorState.behaviorController
        val defaultBehavior = defaultDelegate.getVelocity(actorState, elapsedFrames)

        if (behaviorController !is MobColkhabController) { return defaultBehavior }

        val desiredPosition = behaviorController.getDesiredPosition() ?: return defaultBehavior

        if (Vector3f.distance(desiredPosition, actorState.position) < 1f) { return Vector3f.ZERO }

        return velocityVectorTo(actorState.position, desiredPosition, actorState.getMovementSpeed(), elapsedFrames)
    }

}

class MobColkhabBeeController(actorState: ActorState): V0MonsterController(actorState) {

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.tpRequirementBypass = true
        aggregate.movementSpeed -= 66
    }

    override fun wantsToUseSkill(): Boolean {
        return actorState.getHpp() <= 0.5f
    }

    override fun getSkills(): List<SkillId> {
        return listOf(mskillFinalSting_80)
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo {
        return standardSingleTargetRange.copy(maxTargetDistance = 100f)
    }

}