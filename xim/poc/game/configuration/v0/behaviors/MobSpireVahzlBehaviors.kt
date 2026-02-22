package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.ActorController
import xim.poc.FollowPartyController
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.*
import xim.poc.game.configuration.v0.GameV0Helpers.hasAnyEnmity
import xim.poc.game.configuration.v0.constants.*
import xim.poc.game.event.ActorDamagedEvent
import xim.poc.game.event.AttackDamageType
import xim.poc.game.event.Event
import xim.poc.velocityVectorTo
import xim.resource.DatId
import xim.util.FrameTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class MobSelhteusBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private var hasUsedRejuvenation = false

    override fun onInitialized(): List<Event> {
        actorState.animationSettings.deathAnimation = DatId.specialDeath
        return emptyList()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        val player = ActorStateManager.player()

        if (player.isDead()) {
            return listOf(ActorDamagedEvent(
                sourceId = actorState.id,
                targetId = actorState.id,
                amount = 9999,
                damageType = AttackDamageType.Static,
                emitDamageText = false,
                actionContext = null,
            ))
        }

        if (!actorState.isEngaged() && (player.isEngaged() || hasAnyEnmity(player.id))) {
            engageReceptacle()
        }

        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Silence, StatusEffect.Paralysis)
        if (wantsToUseRejuvenation()) { aggregate.tpRequirementBypass = true }
    }

    override fun wantsToUseSkill(): Boolean {
        return if (wantsToUseRejuvenation()) { true } else { super.wantsToUseSkill() }
    }

    override fun selectSkill(): SkillSelection? {
        if (wantsToUseRejuvenation()) {
            return SkillSelection(mskillRejuvenation_3366, actorState)
        }

        val target = ActorStateManager.playerTarget() ?: ActorStateManager[actorState.getTargetId()] ?: return null
        return SkillSelector.selectSkill(actorState, getSkills(), target)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillRejuvenation_3366) { hasUsedRejuvenation = true }
        return emptyList()
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.None
    }

    private fun engageReceptacle() {
        val receptacle = ActorStateManager.filter { it.monsterId == mobMemoryReceptacle_53 }.firstOrNull() ?: return
        actorState.getEnmityTable().add(receptacle.id, ActorEnmity(totalEnmity = 30_000))
    }

    private fun wantsToUseRejuvenation(): Boolean {
        return actorState.getHpp() < 0.5f && !hasUsedRejuvenation
    }

}

class MobSelhteusController: ActorController {

    private val delegateController = FollowPartyController()
    private val desiredPosition = Vector3f(x=0.00f,y=0.75f,z=2.5f)

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        if (!actorState.isEngaged()) { return delegateController.getVelocity(actorState, elapsedFrames) }
        if (actorState.getTargetingDistance(desiredPosition) < 1f) { return Vector3f.ZERO }
        return velocityVectorTo(actorState.position, desiredPosition, actorState.getMovementSpeed(), elapsedFrames)
    }

}

class MobReceptacleBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val emptySeedTimer = FrameTimer(period = 30.seconds, initial = 4.seconds)
    private val children = ArrayList<ActorPromise>()

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isEngaged() && children.isEmpty()) {
            spawnChildren()
        }

        if (actorState.isEngaged()) {
            emptySeedTimer.update(elapsedFrames)
        }

        if (childrenAreObsolete()) {
            actorState.appearanceState = 2
        }

        return super.update(elapsedFrames)
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep)
        aggregate.subtleBlow += 75

        if (!childrenAreObsolete()) {
            aggregate.physicalDamageTaken = -100
            aggregate.magicalDamageTaken = -100
        }

        if (selhteusIsObsolete()) {
            aggregate.magicAttackBonus += 10_000
        }
    }

    override fun wantsToUseSkill(): Boolean {
        return emptySeedTimer.isReady()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillEmptySeed_286) { emptySeedTimer.reset() }
        return emptyList()
    }

    override fun getSkills(): List<SkillId> {
        return listOf(mskillEmptySeed_286)
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.None
    }

    private fun childrenAreObsolete(): Boolean {
        return children.isNotEmpty() && children.all { it.isObsolete() }
    }

    private fun selhteusIsObsolete(): Boolean {
        return getSelhteus() == null
    }

    private fun spawnChildren() {
        actorState.getEnmityTable().add(ActorStateManager.playerId, ActorEnmity(totalEnmity = 1))
        getSelhteus()?.let { actorState.getEnmityTable().add(it.id, ActorEnmity(totalEnmity = 20)) }

        children += V0MonsterHelper.spawnMonster(MonsterDefinitions[mobProcreator_54], position = Vector3f(x=14.50f,y=0.00f,z=0.00f))
        children += V0MonsterHelper.spawnMonster(MonsterDefinitions[mobCumulator_55], position = Vector3f(x=0.00f,y=0.00f,z=-14.50f))
        children += V0MonsterHelper.spawnMonster(MonsterDefinitions[mobAgonizer_56], position = Vector3f(x=-14.50f,y=0.00f,z=0.00f))

        for (child in children) {
            child.onReady { it.syncEnmity(actorState) }
        }
    }

    private fun getSelhteus(): ActorState? {
        val playerParty = PartyManager[ActorStateManager.playerId]
        return playerParty.getAllState().firstOrNull { it.monsterId == mobSelhteus_52 }
    }

}

abstract class BaseEmptinessBehavior(actorState: ActorState): V0MonsterController(actorState) {

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep)
        aggregate.knockBackResistance += 100
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.Object
    }

}

class MobProcreatorBehavior(actorState: ActorState): BaseEmptinessBehavior(actorState) {

    private val children = ArrayList<ActorPromise>()

    override fun getSkills(): List<SkillId> {
        return if (wantsToUseFission()) { listOf(mskillFission_499) } else { super.getSkills() }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill != mskillFission_499) { return emptyList() }

        children += V0MonsterHelper.spawnMonster(MonsterDefinitions[mobOffspring_57], position = actorState.position)
        children += V0MonsterHelper.spawnMonster(MonsterDefinitions[mobOffspring_57], position = actorState.position)
        children += V0MonsterHelper.spawnMonster(MonsterDefinitions[mobOffspring_57], position = actorState.position)

        for (child in children) {
            child.onReady { it.syncEnmity(actorState) }
        }

        return emptyList()
    }

    override fun onDefeated(): List<Event> {
        children.forEach { it.onReady(GameV0Helpers::defeatActor) }
        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        super.applyMonsterBehaviorBonuses(aggregate)

        val childCount = children.count { it.isAlive() }
        aggregate.physicalDamageTaken -= 25 * childCount
        aggregate.magicalDamageTaken -= 25 * childCount
    }

    private fun wantsToUseFission(): Boolean {
        return children.isEmpty() && actorState.getHpp() <= 0.5f
    }

}

class MobOffspringController(actorState: ActorState): V0MonsterController(actorState) {

    private val actionDelay = FrameTimer(5.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        actionDelay.update(elapsedFrames)
        if (actionDelay.isNotReady()) { return emptyList() }

        return super.update(elapsedFrames)
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.Passive
    }

}

class MobCumulatorBehavior(actorState: ActorState): BaseEmptinessBehavior(actorState) {

    override fun getSkills(): List<SkillId> {
        return if (Random.nextBoolean()) { listOf(mskillCarousel_978) } else { super.getSkills() }
    }

}

class MobAgonizerBehavior(actorState: ActorState): BaseEmptinessBehavior(actorState) {

    override fun getSkills(): List<SkillId> {
        val hasDebuffs = actorState.getStatusEffects().any { it.canErase }
        return if (hasDebuffs) { listOf(mskillWindsofPromyvion_989) } else { super.getSkills() }
    }

}