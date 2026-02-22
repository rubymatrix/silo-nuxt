package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.ActorManager
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.constants.mobCerberusFetter_70
import xim.poc.game.event.Event
import xim.poc.gl.ByteColor
import xim.resource.AoeType
import xim.util.FrameTimer
import xim.util.multiplyInPlace
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private enum class HowlState {
    None,
    LavaSpitMode,
    GatesOfHadesMode,
    DoubleAttackMode,
}

private class PendingFetter(val position: Vector3f, var remaining: FrameTimer)

class MobCerberusBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val pendingFetters = ArrayList<PendingFetter>()
    private val spawnedMonsters = ArrayList<ActorPromise>()

    private val howlTimer = FrameTimer(30.seconds, initial = 3.seconds)

    private var howlState = HowlState.None
    private val howlQueue = ArrayDeque<HowlState>()

    private var howlCount = 0
    private var levelUpCount = 0

    private val lavaSpitTimer = FrameTimer(6.seconds)
    private val gatesOfHadesTimer = FrameTimer(16.seconds)

    override fun onInitialized(): List<Event> {
        howlQueue += HowlState.GatesOfHadesMode
        actorState.faceToward(ActorStateManager.player())
        return emptyList()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actorState.isEngaged()) { return super.update(elapsedFrames) }

        handlePendingFetters(elapsedFrames)
        spawnedMonsters.removeAll { it.isObsolete() }
        howlTimer.update(elapsedFrames)

        if (howlState == HowlState.GatesOfHadesMode) {
            gatesOfHadesTimer.update(elapsedFrames)
        } else {
            gatesOfHadesTimer.reset()
        }

        if (howlState == HowlState.LavaSpitMode) {
            lavaSpitTimer.update(elapsedFrames)
        } else {
            lavaSpitTimer.reset()
        }

        setBlur()
        return super.update(elapsedFrames)
    }

    override fun onDefeated(): List<Event> {
        for (promise in spawnedMonsters) {
            promise.onReady(GameV0Helpers::defeatActor)
        }
        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Bind)

        val levelUpBonus = 1f + 0.125f * levelUpCount
        aggregate.multiplicativeStats.multiplyInPlace(CombatStat.str, levelUpBonus)
        aggregate.multiplicativeStats.multiplyInPlace(CombatStat.int, levelUpBonus)

        if (howlState == HowlState.DoubleAttackMode) {
            aggregate.haste += 33
        }

        if (wantsToUseModeSkill()) {
            aggregate.tpRequirementBypass = true
        }
    }

    override fun wantsToUseSkill(): Boolean {
        return wantsToUseModeSkill() || super.wantsToUseSkill()
    }

    override fun getSkills(): List<SkillId> {
        if (howlState == HowlState.LavaSpitMode && !lavaSpitTimer.isReady()) { return emptyList() }

        return if (howlTimer.isReady()) {
            listOf(mskill_1636)
        } else if (gatesOfHadesTimer.isReady()) {
            listOf(mskillGatesofHades_1534)
        } else if (lavaSpitTimer.isReady()) {
            listOf(mskillLavaSpit_1529)
        } else {
            listOf(mskillSulfurousBreath_1530, mskillScorchingLash_1531, mskillUlulation_1532, mskillMagmaHoplon_1533)
        }
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        return if (skill == mskillGatesofHades_1534) { SkillRangeInfo(100f, 100f, AoeType.Source) } else { null }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        when (primaryTargetContext.skill) {
            mskillLavaSpit_1529 -> handleLavaSpit(primaryTargetContext)
            mskillGatesofHades_1534 -> handleGatesOfHades()
            mskill_1636 -> handleHowl(primaryTargetContext)
            else -> { }
        }

        return emptyList()
    }

    private fun wantsToUseModeSkill(): Boolean {
        return howlTimer.isReady() || gatesOfHadesTimer.isReady() || lavaSpitTimer.isReady()
    }

    private fun setBlur() {
        if (actorState.isDead()) {
            ActorManager[actorState.id]?.actorModel?.customModelSettings?.blurConfig = null
            return
        }

        val blur = when (howlState) {
            HowlState.GatesOfHadesMode -> standardBlurConfig(ByteColor(0x80, 0x00, 0x00, 0x20))
            HowlState.LavaSpitMode -> standardBlurConfig(ByteColor(0x60, 0x60, 0x20, 0x20))
            HowlState.DoubleAttackMode -> standardBlurConfig(ByteColor(0x00, 0x00, 0x80, 0x20))
            else -> null
        }

        ActorManager[actorState.id]?.actorModel?.customModelSettings?.blurConfig = blur
    }

    private fun handleLavaSpit(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext) {
        lavaSpitTimer.reset()
        val position = primaryTargetContext.sourceState.getCastingState()?.context?.targetAoeCenter ?: return
        pendingFetters += PendingFetter(position = Vector3f(position), remaining = FrameTimer(2.seconds))
    }

    private fun handlePendingFetters(elapsedFrames: Float) {
        val pendingIterator = pendingFetters.iterator()
        while (pendingIterator.hasNext()) {
            val pendingFetter = pendingIterator.next()

            pendingFetter.remaining.update(elapsedFrames)
            if (pendingFetter.remaining.isNotReady()) { continue }

            spawnFetter(pendingFetter.position)
            pendingIterator.remove()
        }
    }

    private fun handleGatesOfHades() {
        gatesOfHadesTimer.reset()
    }

    private fun handleHowl(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext) {
        howlTimer.reset()
        howlCount += 1

        if (howlCount % 3 == 0) {
            AttackContext.compose(primaryTargetContext.context) { MiscEffects.playEffect(actorState.id, effect = MiscEffects.Effect.LevelUp) }
            levelUpCount += 1
        }

        if (howlQueue.isEmpty()) {
            val howlStates = HowlState.values().toList().filter { it != HowlState.None }
            howlQueue.addAll(howlStates.shuffled())

            // Avoid back-to-back same states
            howlQueue.remove(howlState)
            if (howlState != HowlState.None) { howlQueue.addLast(howlState) }
        }

        howlState = howlQueue.removeFirst()
        autoAttackDelegate.reset()
    }

    private fun spawnFetter(position: Vector3f) {
        val fetterDefinition = MonsterDefinitions[mobCerberusFetter_70]

        spawnedMonsters += V0MonsterHelper.spawnMonster(
            monsterDefinition = fetterDefinition,
            position = position,
            actorType = ActorType.StaticNpc,
            movementControllerFn = { NoOpActorController() },
        )
    }

}

class MobCerberusFetterBehavior(val actorState: ActorState): ActorBehaviorController {

    private val lifeTimer = FrameTimer(60.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isDead()) { return emptyList() }

        lifeTimer.update(elapsedFrames)
        if (lifeTimer.isReady()) {
            GameV0Helpers.defeatActor(actorState)
            return emptyList()
        }

        val player = ActorStateManager.player()
        if (player.isDead()) { return emptyList() }

        val distance = actorState.getTargetingDistance(player)
        if (distance <= 3f) { applyBurn(player) }

        return emptyList()
    }

    private fun applyBurn(target: ActorState) {
        val statusEffectState = target.getOrGainStatusEffect(StatusEffect.Burn, 1.seconds)
        statusEffectState.potency = (target.getMaxHp() * 0.2f).roundToInt()
        statusEffectState.secondaryPotency = 0.8f
    }

}