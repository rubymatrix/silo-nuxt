package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillLeafstorm_75
import xim.poc.game.configuration.constants.mskillSlumberPowder_430
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.constants.mobCherrySapling_9
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.Event
import xim.util.Fps
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class MobCherryTreeBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private var spawnedSapling: ActorPromise? = null
    private var timeSinceSpawned = 0f

    private val spawnWait = FrameTimer(45.seconds, initial = ZERO)

    override fun onInitialized(): List<Event> {
        spellTimer = FrameTimer(period = 25.seconds)
        return super.onInitialized()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isEngaged() && shouldSpawnSapling(elapsedFrames)) {
            spawnWait.reset()
            spawnSapling()
            timeSinceSpawned = 0f
        }

        if (spawnedSapling.isAlive()) {
            timeSinceSpawned += elapsedFrames
        }

        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.fastCast -= 25

        val targetState = ActorStateManager[actorState.getTargetId()] ?: return
        if (targetState.hasStatusEffect(StatusEffect.Sleep)) { aggregate.magicAttackBonus += 100 }
    }

    override fun onDefeated(): List<Event> {
        spawnedSapling?.onReady(GameV0Helpers::defeatActor)
        return emptyList()
    }

    override fun wantsToUseSkill(): Boolean {
        return actorState.getTpp() >= 0.5f
    }

    override fun selectSkill(): SkillSelection? {
        if (spawnedSapling.isNullOrObsolete() || Fps.framesToSeconds(timeSinceSpawned) < 15.seconds) {
            return super.selectSkill()
        }

        triggerSaplings()
        return SkillSelector.selectSkill(actorState, listOf(mskillLeafstorm_75))
    }

    private fun triggerSaplings() {
        spawnedSapling?.ifReady {
            if (it.behaviorController is MobCherrySaplingBehavior) {
                it.behaviorController.readySyncedAbility = true
            }
        }
    }

    private fun shouldSpawnSapling(elapsedFrames: Float): Boolean {
        spawnWait.update(elapsedFrames)
        return spawnWait.isReady() && spawnedSapling.isNullOrObsolete()
    }

    private fun spawnSapling() {
        val monsterDefinition = MonsterDefinitions[mobCherrySapling_9]

        spawnedSapling = V0MonsterHelper.spawnMonster(
            monsterDefinition = monsterDefinition,
            position = Vector3f(x=-161.34f,y=-15.61f,z=308.55f),
        ).onReady {
            it.syncEnmity(actorState)
        }
    }

}

class MobCherrySaplingBehavior(actorState: ActorState): V0MonsterController(actorState) {

    var readySyncedAbility = false

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.tpRequirementBypass = true
        aggregate.mobSkillFastCast += 300
        aggregate.movementSpeed -= 25
    }

    override fun wantsToUseSkill(): Boolean {
        return readySyncedAbility
    }

    override fun getSkills(): List<SkillId> {
        return listOf(mskillSlumberPowder_430)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillSlumberPowder_430) { readySyncedAbility = false }
        return emptyList()
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.Passive
    }

}
