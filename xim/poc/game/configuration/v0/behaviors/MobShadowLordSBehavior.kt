package xim.poc.game.configuration.v0.behaviors

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.ActorController
import xim.poc.ActorId
import xim.poc.DefaultEnemyController
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.*
import xim.poc.game.configuration.v0.constants.mobShadowEye_123
import xim.poc.game.configuration.v0.constants.mobShadowImage_124
import xim.poc.game.configuration.v0.constants.mobShadowLordClone_122
import xim.poc.game.event.AttackEffects
import xim.poc.game.event.AttackStatusEffect
import xim.poc.game.event.Event
import xim.resource.AoeType
import xim.util.Fps
import xim.util.FrameTimer
import xim.util.PI_f
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

private class WarpSettings(val forceSkill: Boolean)

class MobShadowLordSBehavior(actorState: ActorState): V0MonsterController(actorState) {

    companion object {
        private val arenaCenter = Vector3f(x = -456.5f, y = -167.19f, z = -240f)
    }

    private val dualWieldStateChangeTimer = FrameTimer(3.seconds, initial = ZERO)

    private var warpStateMachine: WarpStateMachine<WarpSettings>? = null
    private var forceSkill = false

    private val spawnedMonsters = ArrayList<ActorPromise>()

    private var hasSpawnedShadows = false
    private var hasUsedSpellWall = false
    private var hasUsedSomaWall = false
    private var hasUsedDoomArc = false

    override fun update(elapsedFrames: Float): List<Event> {
        updateAppearanceState()

        if (actorState.isIdleOrEngaged()) { dualWieldStateChangeTimer.update(elapsedFrames) }
        if (!dualWieldStateChangeTimer.isReady()) { return emptyList() }

        spawnedMonsters.removeAll { it.isObsolete() }
        actorState.targetable = !anyShadowCloneIsAlive() && !anyShadowEyeIsAlive()

        val currentWarp = warpStateMachine
        if (currentWarp != null) {
            currentWarp.update(elapsedFrames)
            if (!currentWarp.isComplete()) { return emptyList() }

            forceSkill = currentWarp.warpContext.forceSkill
            warpStateMachine = null
        }

        if (!isWarping() && actorState.isIdleOrEngaged() && wantsToWarpToCenter()) {
            warpStateMachine = WarpStateMachine(
                actorState = actorState,
                destination = FixedWarpDestination(arenaCenter),
                invisibleTime = ZERO,
                warpContext = WarpSettings(forceSkill = true),
            )
        }

        if (!isWarping() && actorState.isIdleOrEngaged() && wantsToWarpToPlayer()) {
            val invisibleTime = if (anyShadowCloneIsAlive()) { 2.seconds } else { ZERO }
            warpStateMachine = WarpStateMachine(
                actorState = actorState,
                destination = TargetWarpDestination(actorState, distanceFromTarget = 4f),
                invisibleTime = invisibleTime,
                warpContext = WarpSettings(forceSkill = true),
            )
        }

        return super.update(elapsedFrames)
    }

    override fun onDefeated(): List<Event> {
        for (promise in spawnedMonsters) {
            promise.onReady(GameV0Helpers::defeatActor)
        }
        return emptyList()
    }

    override fun getSkills(): List<SkillId> {
        if (wantsToSpawnShadow()) {
            return listOf(mskillSpawnShadow_2287)
        } else if (wantsToUseSpellWall()) {
            return listOf(mskillSpellWall_2282)
        } else if (wantsToUseSomaWall()) {
            return listOf(mskillSomaWall_2288)
        } else if (wantsToUseDoomArc()) {
            return listOf(mskillDoomArc_2289)
        }

        return if (isDualWieldState()) {
            listOf(mskillCruelSlash_2281, mskillCrossSmash_2285, mskillBlightingBlitz_2286)
        } else {
            listOf(mskillViciousKick_2279, mskillBoonVoid_2280, mskillImplosion_2283, mskillUmbralOrb_2284)
        }
    }

    override fun getSpells(): List<SkillId> {
        return listOf(spellFiraja_496, spellBlizzaja_497, spellThundaja_500)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        forceSkill = false

        if (primaryTargetContext.skill == mskillSpawnShadow_2287) {
            hasSpawnedShadows = true
            spawnShadowClones()
        }

        if (primaryTargetContext.skill == mskillSpellWall_2282) {
            hasUsedSpellWall = true
            spawnShadowEyes()
        }

        if (primaryTargetContext.skill == mskillSomaWall_2288) {
            hasUsedSomaWall = true
            spawnShadowImages()
        }

        if (primaryTargetContext.skill == mskillDoomArc_2289) {
            hasUsedDoomArc = true
        }

        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.refresh += 10
        aggregate.knockBackResistance = 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Bind, StatusEffect.Petrify)

        if (forceSkill) {
            aggregate.mobSkillFastCast += 50
            aggregate.tpRequirementBypass = true
        }

        if (wantsToUseSomaWall()) {
            aggregate.tpRequirementBypass = true
        }

        if (anyShadowImageIsAlive() || wantsToUseDoomArc()) {
            aggregate.physicalDamageTaken -= 75
            aggregate.magicalDamageTaken -= 75
            aggregate.tpRequirementBypass = true
        }

        if (wantsToSpawnShadow() || wantsToUseDoomArc() || wantsToUseSomaWall() || wantsToUseSpellWall()) {
            aggregate.fullResist(StatusEffect.Stun)
        }

    }

    override fun wantsToUseSkill(): Boolean {
        if (isWarping() || wantsToWarpToCenter() || anyShadowEyeIsAlive() || !dualWieldStateChangeTimer.isReady()) { return false }
        return forceSkill || wantsToUseSomaWall() || anyShadowImageIsAlive() || wantsToSpawnShadow() || wantsToUseDoomArc() || super.wantsToUseSkill()
    }

    override fun wantsToCastSpell(): Boolean {
        return anyShadowEyeIsAlive()
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.Object
    }

    override fun isRotationLocked(): Boolean {
        return anyShadowEyeIsAlive()
    }

    override fun onReadyToAutoAttack(): List<Event>? {
        return if (anyShadowEyeIsAlive()) { emptyList() } else { super.onReadyToAutoAttack() }
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        if (skill != mskillDoomArc_2289) { return null }
        return SkillRangeInfo(maxTargetDistance = 100f, effectRadius = 100f, type = AoeType.Source)
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId != mskillDoomArc_2289) { return null }
        return SkillApplier(targetEvaluator = V0MobSkillDefinitions.basicPhysicalDamage(attackEffects = AttackEffects(
                attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Doom, baseDuration = 30.seconds, canResist = false))
        )) { 1f })
    }

    fun isWarping(): Boolean {
        return warpStateMachine != null
    }

    fun anyShadowCloneIsAlive(): Boolean {
        return spawnedMonsters.mapNotNull { ActorStateManager[it.getIfReady()] }
            .any { it.monsterId == mobShadowLordClone_122 }
    }

    fun anyShadowEyeIsAlive(): Boolean {
        return spawnedMonsters.mapNotNull { ActorStateManager[it.getIfReady()] }
            .any { it.monsterId == mobShadowEye_123 }
    }

    private fun anyShadowImageIsAlive(): Boolean {
        return spawnedMonsters.mapNotNull { ActorStateManager[it.getIfReady()] }
            .any { it.monsterId == mobShadowImage_124 }
    }

    private fun updateAppearanceState() {
        val previousState = actorState.appearanceState

        actorState.appearanceState = if (anyShadowEyeIsAlive()) {
            1
        } else if (anyShadowImageIsAlive() || wantsToUseDoomArc()) {
            3
        } else if (actorState.getHpp() <= 0.6f) {
            2
        } else {
            0
        }

        if (previousState < 2 && actorState.appearanceState >= 2) {
            dualWieldStateChangeTimer.reset()
        }
    }

    private fun isDualWieldState(): Boolean {
        return actorState.appearanceState >= 2
    }

    private fun wantsToWarpToPlayer(): Boolean {
        if (forceSkill || !actorState.isEngaged() || isWarping() || anyShadowEyeIsAlive() || wantsToUseSpellWall()) {
            return false
        }

        if (anyShadowCloneIsAlive()) { return true }

        val distance = actorState.getTargetingDistance(ActorStateManager.player())
        return distance > 5f
    }

    private fun wantsToWarpToCenter(): Boolean {
        if (!actorState.isEngaged() || !wantsToUseSpellWall()) { return false }
        return Vector3f.distance(actorState.position, arenaCenter) > 1f
    }

    private fun wantsToSpawnShadow(): Boolean {
        return !hasSpawnedShadows && isDualWieldState()
    }

    private fun spawnShadowClones() {
        for (i in 0 until 4) { spawnCloneShadow() }
    }

    private fun spawnCloneShadow() {
        val shadowDefinition = MonsterDefinitions[mobShadowLordClone_122]

        spawnedMonsters += V0MonsterHelper.spawnMonster(
            monsterDefinition = shadowDefinition,
            position = Vector3f(actorState.position),
            actorType = ActorType.Enemy,
        ).onReady {
            it.getEnmityTable().syncFrom(actorState.getEnmityTable())
        }
    }

    fun wantsToUseSpellWall(): Boolean {
        return !hasUsedSpellWall && actorState.getHpp() <= 0.80f
    }

    private fun spawnShadowEyes() {
        val shadowDefinition = MonsterDefinitions[mobShadowEye_123]

        for (direction in MobShadowEyeBehavior.EyeDirection.values()) {
            spawnedMonsters += V0MonsterHelper.spawnMonster(
                monsterDefinition = shadowDefinition,
                position = Vector3f(actorState.position),
                actorType = ActorType.Enemy,
            ).onReady {
                it.syncEnmity(actorState)

                it.behaviorController as MobShadowEyeBehavior
                it.behaviorController.parent = actorState.id
                it.behaviorController.direction = direction
            }
        }
    }

    private fun wantsToUseSomaWall(): Boolean {
        return !hasUsedSomaWall && actorState.getHpp() <= 0.40f
    }

    private fun spawnShadowImages() {
        val definition = MonsterDefinitions[mobShadowImage_124]
        val offsets = ArrayList<Vector3f>()

        for (i in 0 .. 2) {
            offsets += Matrix4f().rotateYInPlace((i / 3f) * 2f * PI_f).transform(Vector3f(8f, 0f, 0f))
        }

        for (i in offsets.indices) {
            val offset = offsets[i]

            spawnedMonsters += V0MonsterHelper.spawnMonster(
                monsterDefinition = definition,
                position = arenaCenter + offset,
                actorType = ActorType.Enemy,
            ).onReady {
                it.syncEnmity(actorState)

                it.behaviorController as MobShadowImageBehavior
                it.behaviorController.parentId = actorState.id
                it.behaviorController.executionDelay = FrameTimer(0.5.seconds + 0.75.seconds * i)
            }
        }
    }

    private fun wantsToUseDoomArc(): Boolean {
        return !hasUsedDoomArc && actorState.getHpp() <= 0.2f
    }

}

class MobShadowLordSMovementController: ActorController {

    private val delegateController = DefaultEnemyController()

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        val behavior = actorState.behaviorController
        if (behavior !is MobShadowLordSBehavior) { return delegateController.getVelocity(actorState, elapsedFrames) }

        if (behavior.isWarping() || behavior.anyShadowCloneIsAlive() || behavior.wantsToUseSpellWall() || behavior.anyShadowEyeIsAlive()) {
            return Vector3f.ZERO
        }

        return delegateController.getVelocity(actorState, elapsedFrames)
    }

}

class MobShadowLordCloneBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val initialAutoAttackDelay = FrameTimer(8.seconds).resetRandom(4.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        initialAutoAttackDelay.update(elapsedFrames)
        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.autoAttackScale = 0.75f
    }

    override fun getSkills(): List<SkillId> {
        return emptyList()
    }

    override fun onReadyToAutoAttack(): List<Event>? {
        if (!initialAutoAttackDelay.isReady()) { return emptyList() }
        return super.onReadyToAutoAttack()
    }

}

class MobShadowEyeBehavior(actorState: ActorState): V0MonsterController(actorState) {

    enum class EyeDirection { Left, Right }

    var parent: ActorId? = null
    var direction = EyeDirection.Left

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actorState.isDead()) { updatePosition() }
        return super.update(elapsedFrames)
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun getSpells(): List<SkillId> {
        return listOf(spellFlare_204, spellFreeze_206, spellBurst_212)
    }

    override fun wantsToCastSpell(): Boolean {
        val baseDelay = when (direction) {
            EyeDirection.Left -> 3.seconds
            EyeDirection.Right -> 4.seconds
        }

        return actorState.age > Fps.toFrames(baseDelay)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep)
        aggregate.refresh += 10
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.None
    }

    private fun updatePosition() {
        val parent = ActorStateManager[parent] ?: return

        val parentFacingDirection = Matrix4f().rotateYInPlace(parent.rotation).transform(Vector3f.X)
        val crossDirection = parentFacingDirection.cross(Vector3f.NegY).normalizeInPlace()

        val sign = when (direction) {
            EyeDirection.Left -> -3f
            EyeDirection.Right -> 3f
        }

        actorState.position.copyFrom(parent.position + (crossDirection * sign))
    }

}

class MobShadowImageBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val lifeSpan = FrameTimer(60.seconds)

    var executionDelay = FrameTimer(ZERO)
    var pendingSkill: SkillId? = null
    var parentId: ActorId? = null

    override fun update(elapsedFrames: Float): List<Event> {
        lifeSpan.update(elapsedFrames)
        if (lifeSpan.isReady() && actorState.isIdleOrEngaged()) {
            GameV0Helpers.defeatActor(actorState)
            return emptyList()
        }

        if (actorState.isIdleOrEngaged()) {
            actorState.faceToward(ActorStateManager.player())
        }

        if (pendingSkill == null) { copySkillFromParent() }
        executionDelay.update(elapsedFrames)
        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.tpRequirementBypass = true
    }

    override fun selectSkill(): SkillSelection? {
        val pending = pendingSkill ?: return null
        return SkillSelection(pending,ActorStateManager.player())
    }

    override fun wantsToUseSkill(): Boolean {
        return executionDelay.isReady()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        pendingSkill = null
        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun ignoresSkillRangeChecks(skill: SkillId): Boolean {
        return true
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.None
    }

    private fun copySkillFromParent() {
        val parent = ActorStateManager[parentId] ?: return
        val parentCastingState = parent.getCastingState() ?: return

        if (!parentCastingState.isCharging()) { return }

        pendingSkill = parentCastingState.skill
        executionDelay.reset()
    }

}