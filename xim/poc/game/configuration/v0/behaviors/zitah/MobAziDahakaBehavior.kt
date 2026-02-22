package xim.poc.game.configuration.v0.behaviors.zitah

import xim.math.Vector3f
import xim.poc.game.*
import xim.poc.game.configuration.ActorCollisionType
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.V0MobSkillDefinitions
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.constants.mobAziDragon_288_051
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.AttackAddedEffectType
import xim.poc.game.event.AttackStatusEffect
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class MobAziDahakaBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val stateTransitionTimer = FrameTimer(4.seconds, ZERO)

    private var startedFlying = false

    private var hasSpawnedPets = false
    private val spawnedMonsters = ArrayList<ActorPromise>()

    private var hundredFistsCounter = 0

    override fun onInitialized(): List<Event> {
        spellTimer = FrameTimer(20.seconds)
        return super.onInitialized()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        spawnedMonsters.removeAll { it.isObsolete() }
        actorState.targetable = stateTransitionTimer.isReady() && !isFlying()

        stateTransitionTimer.update(elapsedFrames)
        if (stateTransitionTimer.isNotReady()) { return emptyList() }

        if (isFlying() && !hasSpawnedPets) {
            spawnPets()
        }

        if (actorState.isIdleOrEngaged() && wantsToStartFlying()) {
            startFlying()
            return emptyList()
        }

        return super.update(elapsedFrames)
    }

    override fun getAutoAttackAbilities(): List<SkillId> {
        return if (isFlying()) { listOf(mskillInfernoBlast_1022) } else { super.getAutoAttackAbilities() }
    }

    override fun getSkills(): List<SkillId> {
        return if (wantsToLand()) {
            listOf(mskillTouchdown_1026)
        } else if(wantsToUseHundredFists()) {
            listOf(mskillHundredFists_434)
        } else {
            super.getSkills()
        }
    }

    override fun wantsToUseSkill(): Boolean {
        if (actorState.hasStatusEffect(StatusEffect.HundredFists)) { return false }
        return wantsToLand() || wantsToUseHundredFists() || super.wantsToUseSkill()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillTouchdown_1026) {
            actorState.appearanceState = 0
            stateTransitionTimer.reset()
        }

        if (primaryTargetContext.skill == mskillHundredFists_434) {
            hundredFistsCounter += 1
        }

        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun wantsToCastSpell(): Boolean {
        if (actorState.hasStatusEffect(StatusEffect.HundredFists)) { return false }
        return super.wantsToCastSpell()
    }

    override fun getSpells(): List<SkillId> {
        return if (isFlying()) {
            listOf(spellFiraja_496, spellThundaja_500)
        } else {
            listOf(spellBlazeSpikes_249, spellFireVI_849, spellThunderVI_853)
        }
    }

    override fun adjustVelocity(baseVelocity: Vector3f): List<Event> {
        if (stateTransitionTimer.isNotReady()) {
            baseVelocity.copyFrom(Vector3f.ZERO)
        }

        return emptyList()
    }

    override fun isRotationLocked(): Boolean {
        return stateTransitionTimer.isNotReady()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.movementSpeed += 50
        aggregate.knockBackResistance += 100
        aggregate.refresh += 10
        aggregate.spellInterruptDown += 100

        if (isFlying()) {
            aggregate.fastCast -= 10
        }

        if (!startedFlying) { return }

        val effectPower = V0MobSkillDefinitions.intPotency(actorState, 0.15f)

        aggregate.autoAttackEffects += AutoAttackEffect(effectPower, AttackAddedEffectType.Fire, statusEffect = AttackStatusEffect(
            statusEffect = StatusEffect.Plague,
            baseDuration = 10.seconds,
            baseChance = 1f,
        ) { it.statusState.potency = 5 })
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return if (isFlying()) { ActorCollisionType.None } else { ActorCollisionType.Object }
    }

    private fun wantsToStartFlying(): Boolean {
        return !startedFlying && actorState.getHpp() < 0.6f
    }

    private fun startFlying() {
        startedFlying = true
        actorState.appearanceState = 1
        stateTransitionTimer.reset()
        spellTimer.reset()
    }

    private fun wantsToLand(): Boolean {
        return isFlying() && spawnedMonsters.isEmpty()
    }

    private fun isFlying(): Boolean {
        return actorState.appearanceState == 1
    }

    private fun spawnPets() {
        hasSpawnedPets = true
        val petDefinition = MonsterDefinitions[mobAziDragon_288_051]

        for (i in 0 until 3) {
            spawnedMonsters += V0MonsterHelper.spawnMonster(
                monsterDefinition = petDefinition,
                position = Vector3f(actorState.position),
                actorType = ActorType.Enemy,
            ).onReady { it.syncEnmity(actorState) }
        }
    }

    private fun wantsToUseHundredFists(): Boolean {
        return when (hundredFistsCounter) {
            0 -> actorState.getHpp() < 0.9f
            1 -> actorState.getHpp() < 0.3f
            else -> false
        }
    }

}

class MobAziDahakaPetBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val skillCooldown = FrameTimer(6.seconds).resetRandom(3.seconds)

    private var hasUsed2hr = false

    override fun update(elapsedFrames: Float): List<Event> {
        if (wantsToUse2hr()) {
            val target = ActorStateManager[actorState.getTargetId()]
            if (target != null) { actorState.faceToward(target) }
        }

        skillCooldown.update(elapsedFrames)
        return super.update(elapsedFrames)
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun getSkills(): List<SkillId> {
        return if (wantsToUse2hr()) {
            listOf(mskillChaosBlade_927)
        }else {
            listOf(mskillFlameBreath_386, mskillBodySlam_389, mskillPetroEyes_392, mskillLodesong_395)
        }
    }

    override fun wantsToUseSkill(): Boolean {
        return skillCooldown.isReady()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillChaosBlade_927) { hasUsed2hr = true }
        skillCooldown.reset()
        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.tpRequirementBypass = true
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.Actor
    }

    private fun wantsToUse2hr(): Boolean {
        return !hasUsed2hr && actorState.getHpp() < 0.5f
    }

}
