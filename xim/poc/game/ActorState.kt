package xim.poc.game

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.ModelLook
import xim.poc.Mount
import xim.poc.game.configuration.ActorBehaviors
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.event.ActorResourceType
import xim.poc.game.event.InitialActorState
import xim.resource.table.NpcInfo
import xim.util.Fps
import xim.util.Fps.secondsToFrames
import xim.util.FrameTimer
import xim.util.PI_f
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

enum class ActorType {
    Pc,
    AllyNpc,
    Enemy,
    StaticNpc,
    Effect,
}

class ActorState(val id: ActorId, initialActorState: InitialActorState) {

    var age = 0.0

    val name = initialActorState.name
    val type = initialActorState.type

    var zone = initialActorState.zoneSettings?.copy()

    val position = Vector3f(initialActorState.position)
    var rotation = initialActorState.rotation
    var scale = initialActorState.scale
    var targetingSize = initialActorState.targetSize
    var autoAttackRange = initialActorState.autoAttackRange

    val velocity = Vector3f()
    val effectVelocity = Vector3f()
    var lastCollisionResult: ActorCollision = ActorCollision()

    var appearanceState = 0

    var disabled: Boolean = initialActorState.npcInfo?.isDefaultDisabled() ?: false
    var visible: Boolean = !disabled

    val movementController = initialActorState.movementController
    val staticPosition = initialActorState.staticPosition
    val facesTarget = initialActorState.facesTarget

    private val npcInfo = initialActorState.npcInfo

    private var baseLook = initialActorState.modelLook.copy()

    var actionState: ActorActionState = ActorNoopState
        private set

    private val engagedState = EngagedState()
    private val deathState = DeathState()
    private val statusEffects = HashMap<StatusEffect, StatusEffectState>()

    val frozenTimer = FrameTimer(ZERO)
    val effectTickTimer = EffectTickTimer()
    val skillChainTargetState = SkillChainTargetState()

    val jobState = JobState()
    val jobLevels = JobLevels()

    var targetable = initialActorState.targetable
    var targetState = TargetState(targetId = null, locked = false)
        private set
    val maxTargetDistance = initialActorState.maxTargetDistance

    val owner = initialActorState.dependentSettings?.ownerId
    val dependentType = initialActorState.dependentSettings?.type

    var mountedState: Mount? = null
    var pet: ActorId? = null
    var bubble: ActorId? = null
    var fishingRod: ActorId? = null

    val monsterId = initialActorState.monsterId
    val behaviorController = ActorBehaviors.createController(initialActorState.behaviorController, this)

    var combatStats: CombatStats = CombatStats.defaultBaseStats
        private set

    private var hp = combatStats.maxHp
    private var mp = combatStats.maxMp
    private var tp = 0

    var displayHp = true

    val animationSettings = AnimationSettings(initialActorState.popRoutines)

    val components = HashMap<KClass<out ActorStateComponent>, ActorStateComponent>()

    fun initiateAction(newState: ActorActionState): Boolean {
        return if (isDead()) {
            false
        } else if (actionState == ActorNoopState) {
            actionState = newState
            true
        } else {
            false
        }
    }

    fun clearActionState(state: ActorActionState) {
        if (actionState == state) {
            actionState = ActorNoopState
        }
    }

    fun isOccupied(): Boolean {
        return actionState != ActorNoopState
    }

    fun <T: ActorStateComponent> getComponentAs(type: KClass<T>): T? {
        val component = components[type] ?: return null
        return if (type.isInstance(component)) { type.cast(component) } else { null }
    }

    fun <T: ActorStateComponent> getRequiredComponentAs(type: KClass<T>): T {
        return getComponentAs(type) ?: throw IllegalStateException("$name does not have required component: $type")
    }

    fun <T: ActorStateComponent> getOrCreateComponentAs(type: KClass<T>, fn: () -> T): T {
        val component = components.getOrPut(type, fn)
        return type.cast(component)
    }

    fun getCastingState(): CastingState? {
        return actionState as? CastingState
    }

    fun isChargingCast(): Boolean {
        val current = getCastingState() ?: return false
        return current.isCharging()
    }

    fun setEngagedState(state: EngagedState.State) {
        engagedState.state = state
    }

    fun isEngaged(): Boolean {
        return engagedState.state == EngagedState.State.Engaged
    }

    fun getHp(): Int {
        return hp
    }

    fun setHp(amount: Int) {
        hp = amount.coerceIn(0, combatStats.maxHp)
    }

    fun consumeHp(cost: Int) {
        setHp(hp - cost)
    }

    fun gainHp(amount: Int) {
        setHp(hp + amount)
    }

    fun getMaxHp(): Int {
        return combatStats.maxHp
    }

    fun getHpp(): Float {
        return getHp().toFloat() / getMaxHp().toFloat()
    }

    fun isDead(): Boolean {
        return hp == 0
    }

    fun getMp(): Int {
        return mp
    }

    fun setMp(amount: Int) {
        mp = amount.coerceIn(0, combatStats.maxMp)
    }

    fun consumeMp(cost: Int) {
        setMp(mp - cost)
    }

    fun gainMp(amount: Int) {
        setMp(mp + amount)
    }

    fun getMaxMp(): Int {
        return combatStats.maxMp
    }

    fun getMpp(): Float {
        return getMp().toFloat() / getMaxMp().toFloat()
    }

    fun gainTp(amount: Int) {
        setTp(tp + amount)
    }

    fun consumeTp(amount: Int) {
        setTp(tp - amount)
    }

    fun setTp(amount: Int) {
        tp = amount.coerceIn(0, GameEngine.getMaxTp(this))
    }

    fun getTp(): Int {
        return tp
    }

    fun getTpp(): Float {
        return tp.toFloat() / GameEngine.getMaxTp(this)
    }

    fun timeSinceCreate(): Duration {
        return Fps.framesToSeconds(age.toFloat())
    }

    fun timeSinceDeath(): Duration {
        return deathState.timeSinceDeath()
    }

    fun hasBeenDeadFor(duration: Duration): Boolean {
        if (!isDead()) { return false }
        return deathState.hasBeenDeadFor(duration)
    }

    fun updateDeathTimer(elapsedFrames: Float) {
        if (!isDead()) {
            deathState.framesSinceDeath = 0f
        } else {
            deathState.framesSinceDeath += elapsedFrames
        }
    }

    fun setBaseLook(modelLook: ModelLook) {
        this.baseLook = modelLook
    }

    fun getBaseLook(): ModelLook {
        return baseLook
    }

    fun getCurrentLook(): ModelLook {
        val costume = statusEffects[StatusEffect.CostumeDebuff]
            ?: statusEffects[StatusEffect.Costume]
            ?: return baseLook

        return ModelLook.npc(costume.counter)
    }

    fun getControllerVelocity(elapsedFrames: Float): Vector3f {
        return movementController.getVelocity(this, elapsedFrames)
    }

    fun isPc(): Boolean {
        return type == ActorType.Pc
    }

    fun isPlayer(): Boolean {
        return id == ActorStateManager.playerId
    }

    fun getNpcInfo(): NpcInfo? {
        return npcInfo
    }

    fun isShip(): Boolean {
        return getNpcInfo()?.isShip() == true
    }

    fun isStaticNpc(): Boolean {
        return type == ActorType.StaticNpc || type == ActorType.Effect
    }

    fun shouldApplyMovement(): Boolean {
        if (staticPosition) { return false }

        if (type == ActorType.Effect) { return false }

        getNpcInfo() ?: return true

        if (!ActorManager.isVisible(id)) { return false }
        return !isDoor() && !isElevator() && !isShip()
    }

    fun isDoor(): Boolean {
        return getNpcInfo()?.datId?.isDoorId() ?: return false
    }

    fun isElevator(): Boolean {
        return getNpcInfo()?.datId?.isElevatorId() ?: return false
    }

    fun isWalking(): Boolean {
        return isEnemy() && !isEngaged()
    }

    fun getMovementSpeed(): Float {
        val bonus = GameEngine.getCombatBonusAggregate(this)
        val walkModifier = if (isWalking()) { 0.25f } else { 1f }
        return walkModifier * (7.5f / secondsToFrames(1)) * bonus.movementSpeed.toMultiplier()
    }

    fun isDependent(): Boolean {
        return owner != null
    }

    fun getTargetingDistance(other: ActorState): Float {
        val distance = Vector3f.distance(position, other.position) - (targetingSize + other.targetingSize)
        return distance.coerceAtLeast(0f)
    }

    fun getTargetingDistance(point: Vector3f): Float {
        val distance = Vector3f.distance(position, point) - (targetingSize)
        return distance.coerceAtLeast(0f)
    }

    fun faceToward(node: ActorState) {
        faceToward(node.position)
    }

    fun faceToward(target: Vector3f) {
        setRotation(target - position)
    }

    fun faceDirection(direction: Vector3f) {
        faceToward(position + direction)
    }

    fun getFacingAngle(other: ActorState): Float {
        val targetDir = (other.position - position).normalizeInPlace()
        val facingDir = Vector3f(cos(rotation), 0f, -sin(rotation))
        val projection = targetDir.dot(facingDir)
        return acos(projection.coerceIn(-1f, 1f))
    }

    fun getSignedFacingAngle(targetPosition: Vector3f): Float {
        val targetDir = (targetPosition - position).normalizeInPlace()
        val facingDir = Vector3f(cos(rotation), 0f, -sin(rotation))

        val projection = targetDir.dot(facingDir)
        val determinate = (facingDir.x*targetDir.z - facingDir.z*targetDir.x)

        return atan2(determinate, projection)
    }

    fun isFacingTowards(other: ActorState, halfAngle: Float = PI_f/4): Boolean {
        return getFacingAngle(other) < halfAngle
    }

    fun getFacingDirection(): Vector3f {
        return Matrix4f().rotateYInPlace(rotation).transform(Vector3f.X)
    }

    fun setRotation(direction: Vector3f) {
        if (effectVelocity.magnitudeSquare() > 0f) { return }
        if (isDead()) { return }
        if (type != ActorType.Pc && isOccupied()) { return }
        if (hasStatusMovementLock()) { return }

        val actorModel = ActorManager[id]
        if (actorModel != null && actorModel.isFacingLocked()) { return }

        val horizontal = direction.withY(0f)
        if (horizontal.magnitudeSquare() < 1e-6f) { return }

        val dir = horizontal.normalize()
        rotation = -atan2(dir.z, dir.x)
    }

    fun getTargetId(): ActorId? {
        return targetState.targetId
    }

    fun setTargetState(targetId: ActorId?, locked: Boolean) {
        targetState = if (targetId == null) {
            TargetState(null, false)
        } else {
            TargetState(targetId, locked)
        }
    }

    fun isEnemy(): Boolean {
        return type == ActorType.Enemy
    }

    fun updateStatusEffects(elapsedFrames: Float): Collection<StatusEffectState> {
        val removed = HashMap<StatusEffect, StatusEffectState>()

        for ((id, effect) in statusEffects) {
            if (effect.remainingDuration == null) { continue }

            effect.remainingDuration = effect.remainingDuration!! - elapsedFrames
            if (effect.isExpired()) { removed[id] = effect }

            if (effect.statusEffect == StatusEffect.Doom) {
                val applier = ActorStateManager[effect.sourceId]
                if (applier == null || applier.isDead()) { removed[id] = effect }
            }
        }

        removed.forEach { statusEffects.remove(it.key) }
        return removed.values
    }

    fun getOrGainStatusEffect(status: StatusEffect, duration: Duration? = null): StatusEffectState {
        return (getStatusEffect(status) ?: gainStatusEffect(status)).also {
            if (duration != null) { it.setRemainingDuration(duration) }
        }
    }

    fun gainStatusEffect(status: StatusEffect, duration: Duration? = null, sourceId: ActorId? = null): StatusEffectState {
        val state = StatusEffectState(status)
        state.sourceId = sourceId

        if (duration != null) { state.remainingDuration = Fps.toFrames(duration) }

        return gainStatusEffect(state)
    }

    fun gainStatusEffect(state: StatusEffectState): StatusEffectState {
        statusEffects[state.statusEffect] = state
        behaviorController.onStatusEffectGained(state)

        return state
    }

    fun getStatusEffects(): List<StatusEffectState> {
        return statusEffects.values.toList().filter { !it.isExpired() }
    }

    fun getStatusEffect(status: StatusEffect?): StatusEffectState? {
        status ?: return null
        return getStatusEffect(status)
    }

    fun getStatusEffect(statusEffect: StatusEffect): StatusEffectState? {
        val status = statusEffects[statusEffect] ?: return null
        return if (status.isExpired()) { null } else { status }
    }

    fun hasStatusEffect(status: StatusEffect): Boolean {
        return getStatusEffect(status) != null
    }

    fun expireStatusEffect(status: StatusEffect?): Boolean {
        val statusEffectState = statusEffects[status] ?: return false
        statusEffectState.remainingDuration = 0f
        return true
    }

    fun expireStatusEffects(predicate: (StatusEffectState) -> Boolean) {
        statusEffects.filter { predicate.invoke(it.value) }.forEach { it.value.remainingDuration = 0f }
    }

    fun consumeStatusEffectCharge(statusEffect: StatusEffect, expireOnZero: Boolean = true, consumeAmount: Int = 1): Boolean {
        val state = getStatusEffect(statusEffect) ?: return false

        val consumedCharge = state.counter > 0
        state.counter -= consumeAmount

        if (expireOnZero && state.counter <= 0) { expireStatusEffect(statusEffect) }

        return consumedCharge
    }

    fun getTargetDirectionVector(): Vector3f? {
        val targetActor = ActorStateManager[targetState.targetId] ?: return null
        return (targetActor.position - position).also { it.y = 0f }.normalizeInPlace()
    }

    fun getDirectionalAutoAttack(targetState: ActorState): AutoAttackDirection {
        val signedAngle = getSignedFacingAngle(targetState.position) / (PI_f/8f)

        return if (signedAngle <= -7f) {
            AutoAttackDirection.South
        } else if (signedAngle <= -5f) {
            AutoAttackDirection.SouthEast
        } else if (signedAngle <= -3f) {
            AutoAttackDirection.East
        } else if (signedAngle <= -1f) {
            AutoAttackDirection.NorthEast
        } else if (signedAngle <= 1f) {
            AutoAttackDirection.North
        } else if (signedAngle <= 3f) {
            AutoAttackDirection.NorthWest
        } else if (signedAngle <= 5f) {
            AutoAttackDirection.West
        } else if (signedAngle <= 7f) {
            AutoAttackDirection.SouthWest
        } else {
            AutoAttackDirection.South
        }
    }

    fun isIdle(): Boolean {
        return !isDead() && !isEngaged() && !isOccupied()
    }

    fun isIdleOrEngaged(): Boolean {
        return !isDead() && !isOccupied()
    }

    fun hasStatusActionLock(): Boolean {
        return getStatusEffects().any { it.preventsAction } || isStaggerFrozen()
    }

    fun hasStatusMovementLock(): Boolean {
        return getStatusEffects().any { it.preventsMovement } || isStaggerFrozen()
    }

    fun getJobLevel(job: Job?): JobLevel? {
        return jobLevels[job]
    }

    fun getMainJobLevel(): JobLevel {
        return getJobLevel(jobState.mainJob) ?: throw IllegalStateException("[$name] No main job level?")
    }

    fun getSubJobLevel(): Int? {
        val mainJobLevel = getMainJobLevel()
        val level = getJobLevel(jobState.subJob)?.level ?: return null
        return level.coerceAtMost(mainJobLevel.level / 2).coerceAtLeast(1)
    }

    fun updateCombatStats(combatStats: CombatStats) {
        this.combatStats = combatStats
        hp = hp.coerceAtMost(combatStats.maxHp)
        mp = mp.coerceAtMost(combatStats.maxMp)
    }

    fun getResource(resourceType: ActorResourceType): Int {
        return when (resourceType) {
            ActorResourceType.HP -> getHp()
            ActorResourceType.MP -> getMp()
            ActorResourceType.TP -> getTp()
        }
    }

    fun getMaxResource(resourceType: ActorResourceType): Int {
        return when (resourceType) {
            ActorResourceType.HP -> getMaxHp()
            ActorResourceType.MP -> getMaxMp()
            ActorResourceType.TP -> GameEngine.getMaxTp(this)
        }
    }

    fun getSkillApplier(skill: SkillId): SkillApplier? {
        return behaviorController.getSkillApplierOverride(skill)
    }

    fun isStaggerFrozen(): Boolean {
        return frozenTimer.isNotReady()
    }

    fun isAnimationFrozen(): Boolean {
        return hasStatusEffect(StatusEffect.Petrify) || hasStatusEffect(StatusEffect.Terror) || isStaggerFrozen()
    }

    fun isResting(): Boolean {
        return actionState is ActorRestingState
    }

    fun isFishing(): Boolean {
        return actionState is ActorFishingAttempt
    }

    fun getFishingState(): ActorFishingAttempt? {
        return actionState as? ActorFishingAttempt
    }

    fun isSittingOnChair(): Boolean {
        return actionState is ActorSitChairState
    }

}