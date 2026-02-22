package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluator.Companion.compose
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.basicPhysicalDamage
import xim.poc.game.configuration.v0.V0MonsterFamilies
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.constants.mobRafflesia_87
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.ActorHealedEvent
import xim.poc.game.event.Event
import xim.resource.AoeType
import xim.resource.TargetFlag
import xim.util.FrameTimer
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

class MobHyoscyaBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val pets = ArrayList<ActorPromise>()

    private val frondTimer = FrameTimer(90.seconds).resetRandom(lowerBound = 15.seconds)
    private val fullBloomTimer = FrameTimer(30.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        pets.removeAll { it.isNullOrObsolete() }

        if (pets.any { it.isAlive() }) {
            frondTimer.reset()
            fullBloomTimer.update(elapsedFrames)
        } else {
            frondTimer.update(elapsedFrames)
            fullBloomTimer.reset()
        }

        return super.update(elapsedFrames)
    }

    override fun onDefeated(): List<Event> {
        killPets()
        return super.onDefeated()
    }

    override fun getSkills(): List<SkillId> {
        return if (wantsToUseBeautifulDeath()) {
            listOf(mskillBeautifulDeath_2629)
        } else if (wantsToUseFullBloom()) {
            listOf(mskillFullBloom_2627)
        } else if (frondTimer.isReady()) {
            listOf(mskillFrondFatale_2626)
        } else {
            super.getSkills()
        }
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.Object
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep)
        aggregate.knockBackResistance += 100
        aggregate.tpRequirementBypass = wantsToUseBeautifulDeath()
    }

    override fun selectSkill(): SkillSelection? {
        return if (wantsToUseFullBloom()) { SkillSelection(mskillFullBloom_2627, actorState) } else { super.selectSkill() }
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId == mskillFrondFatale_2626) {
            return SkillApplier(targetEvaluator = {
                AttackContext.compose(it.context) {
                    val costumeState = it.targetState.gainStatusEffect(StatusEffect.CostumeDebuff, duration = 15.seconds)
                    costumeState.counter = getCostumeValue()
                }
                emptyList()
            })
        }

        if (skillId == mskillFullBloom_2627) {
            return SkillApplier(
                primaryTargetEvaluator = {
                    val alivePets = pets.count { it.isAlive() }
                    val hpGained = (0.25 * actorState.getMaxHp() * alivePets).roundToInt()
                    listOf(ActorHealedEvent(sourceId = actorState.id, targetId = actorState.id, amount = hpGained))
                },
                targetEvaluator = basicPhysicalDamage { 100f },
            )
        }

        if (skillId == mskillBeautifulDeath_2629) {
            return SkillApplier(targetEvaluator = compose(
                basicPhysicalDamage { 5f },
                {
                    AttackContext.compose(it.context) { it.targetState.expireStatusEffect(StatusEffect.CostumeDebuff) }
                    emptyList()
                },
            ))
        }

        return null
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillFrondFatale_2626) {
            if (primaryTargetContext.allTargetStates.isEmpty()) { spawnPets() }
            return emptyList()
        }

        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        return if (skill == mskillFullBloom_2627 || skill == mskillBeautifulDeath_2629) {
            SkillRangeInfo(50f, 50f, AoeType.Source)
        } else {
            null
        }
    }

    override fun getSkillEffectedTargetType(skillId: SkillId): Int? {
        return if (skillId == mskillFullBloom_2627) {
            TargetFlag.Ally.flag
        } else {
            super.getSkillEffectedTargetType(skillId)
        }
    }

    override fun wantsToUseSkill(): Boolean {
        return wantsToUseBeautifulDeath() || super.wantsToUseSkill()
    }

    private fun wantsToUseFullBloom(): Boolean {
        return fullBloomTimer.isReady() && pets.any { it.isAlive() }
    }

    private fun wantsToUseBeautifulDeath(): Boolean {
        val target = ActorStateManager[actorState.getTargetId()] ?: return false
        val targetCostume = target.getStatusEffect(StatusEffect.CostumeDebuff) ?: return false
        return targetCostume.counter == getCostumeValue()
    }

    private fun getCostumeValue(): Int {
        return V0MonsterFamilies.rafflesiaFamily.looks.first().modelId
    }

    private fun spawnPets() {
        val petDefinition = MonsterDefinitions[mobRafflesia_87]

        pets += (0 until 3).map {
            V0MonsterHelper.spawnMonster(
                monsterDefinition = petDefinition,
                position = actorState.position.withRandomHorizontalOffset(2f),
            ).onReady {
                it.syncEnmity(actorState)
            }
        }
    }

    private fun killPets() {
        pets.forEach { it.onReady(GameV0Helpers::defeatActor) }
    }

}

class MobHyoscyaPetBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val seedSprayTimer = FrameTimer(5.seconds).resetRandom(2.5.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isIdleOrEngaged()) { seedSprayTimer.update(elapsedFrames) }
        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.tpRequirementBypass = true
        aggregate.movementSpeed -= 50
        aggregate.mobSkillFastCast -= 20
    }

    override fun wantsToUseSkill(): Boolean {
        return seedSprayTimer.isReady()
    }

    override fun getSkills(): List<SkillId> {
        return listOf(mskillSeedspray_1907)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        seedSprayTimer.reset()
        return emptyList()
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

}