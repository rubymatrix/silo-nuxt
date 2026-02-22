package xim.poc.game.configuration.v0.behaviors.zitah

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.constants.mobUrmahlulluCrystal_288_505
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.Event
import xim.resource.AoeType
import xim.util.PI_f
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MobUrmahlulluController(actorState: ActorState): V0MonsterController(actorState) {

    private val startingPosition = Vector3f()

    private var hasSpawnedCrystals = false
    private val crystals = ArrayList<ActorPromise>()

    override fun onInitialized(): List<Event> {
        startingPosition.copyFrom(actorState.position)

        dynamicStatusResistance = StatusResistanceTracker().also {
            it[StatusEffect.Stun] = OccurrenceLimitStrategy(resetInterval = 10.minutes, maxOccurrencesInInterval = 3)
        }

        return super.onInitialized()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actorState.isDead()) { applyBuff() }

        maybeEagerlyCompleteMeteor()

        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Silence)
        aggregate.knockBackResistance += 100
        aggregate.refresh += 10
        aggregate.spellInterruptDown += 100

        if (crystals.any { it.isAlive() }) {
            aggregate.physicalDamageTaken -= 100
            aggregate.magicalDamageTaken -= 100
        } else if (actorState.hasStatusEffect(StatusEffect.Warcry)) {
            aggregate.regain += 250
            aggregate.physicalDamageTaken += 33
            aggregate.magicalDamageTaken += 33
        } else {
            aggregate.physicalDamageTaken -= 33
            aggregate.magicalDamageTaken -= 33
        }

    }

    override fun getSpells(): List<SkillId> {
        return listOf(spellComet_219, spellMeteorII_244)
    }

    override fun getSkills(): List<SkillId> {
        return if (!hasSpawnedCrystals && actorState.getHpp() <= 0.66f) {
            listOf(mskillEclipticMeteor_2330)
        } else if (actorState.hasStatusEffect(StatusEffect.Warcry)) {
            listOf(mskillThunderbolt_373, mskillWildHorn_372, mskillAmnesicBlast_2135)
        } else if (Random.nextBoolean()) {
            listOf(mskillHowl_377)
        } else {
            listOf(mskillThunderbolt_373, mskillShockWave_375, mskillFlameArmor_376, mskillAccursedArmor_2134)
        }
    }

    override fun onSkillBeginCharging(castingState: CastingState): List<Event> {
        if (castingState.skill == mskillEclipticMeteor_2330) { spawnCrystals() }
        return emptyList()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillEclipticMeteor_2330) { despawnCrystals() }
        return emptyList()
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        return if (skill == mskillEclipticMeteor_2330) {
            60.seconds
        } else if (skill == mskillAmnesicBlast_2135) {
            1.seconds
        } else if (skill == mskillWildHorn_372) {
            1.seconds
        } else  {
            null
        }
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        return if (skill == mskillEclipticMeteor_2330) { SkillRangeInfo(100f, 100f, AoeType.Source) } else { null }
    }

    override fun hasDirectionalAutoAttacks(): Boolean {
        return true
    }

    private fun spawnCrystals() {
        hasSpawnedCrystals = true

        for (i in 0 until 3) {
            val offset = Matrix4f().rotateYInPlace(2f * PI_f * i/3f).transform(Vector3f.X * 10f)

            crystals += V0MonsterHelper.spawnMonster(
                monsterDefinition = MonsterDefinitions[mobUrmahlulluCrystal_288_505],
                position = startingPosition + offset,
            ).onReady {
                (it.behaviorController as? MobUrmahlulluCrystalController)?.leader = actorState
            }
        }
    }

    private fun despawnCrystals() {
        crystals.forEach { it.onReady(GameV0Helpers::defeatActor) }
    }

    private fun applyBuff() {
        val livingCrystals = crystals.count { it.isAlive() }

        if (livingCrystals == 0) {
            actorState.expireStatusEffect(StatusEffect.MagicAtkBoost)
            return
        }

        val mab = actorState.getOrGainStatusEffect(StatusEffect.MagicAtkBoost, duration = 1.seconds)
        mab.potency = livingCrystals * 100
        mab.counter = livingCrystals * 100
        mab.displayCounter = true
        mab.canDispel = false

        listOf(StatusEffect.Protect, StatusEffect.Shell).forEach {
            val dt = actorState.getOrGainStatusEffect(it, duration = 1.seconds)
            dt.potency = 100
            dt.counter = 100
            dt.displayCounter = true
            dt.canDispel = false
        }
    }

    private fun maybeEagerlyCompleteMeteor() {
        val castingState = actorState.getCastingState() ?: return
        if (castingState.skill != mskillEclipticMeteor_2330) { return }
        if (!castingState.isCharging() || castingState.progress() < 0.1f) { return }

        if (crystals.none { it.isAlive() }) {
            castingState.adjustProgressForward(castingState.castTime * 0.95f)
        }
    }

}

class MobUrmahlulluCrystalController(actorState: ActorState): V0MonsterController(actorState) {

    lateinit var leader: ActorState

    override fun update(elapsedFrames: Float): List<Event> {
        if (leader.isDead()) {
            GameV0Helpers.defeatActor(actorState)
            return emptyList()
        }

        actorState.syncEnmity(leader)
        if (actorState.isDead()) { return emptyList() }
        return super.update(elapsedFrames)
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.movementSpeed -= 100
    }

}