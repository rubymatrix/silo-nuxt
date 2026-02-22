package xim.poc.game

import kotlinx.serialization.Serializable
import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.Area
import xim.poc.CollisionProperty
import xim.poc.SynthesisType
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.configuration.ApplierSuccessResult
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.SpellSkillId
import xim.poc.game.configuration.constants.spellNull_0
import xim.poc.game.event.Event
import xim.resource.DatId
import xim.resource.SpellElement
import xim.util.Fps
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface ActorActionState {
    fun preventsMovement(): Boolean = true
    fun cancelledByMovement(): Boolean = false
}

object ActorNoopState: ActorActionState {
    override fun preventsMovement(): Boolean { return false }
}

enum class AutoAttackDirection(val id: DatId) {
    North(DatId("atnn")),
    NorthEast(DatId("atne")),
    East(DatId("atee")),
    SouthEast(DatId("ates")),
    South(DatId("atss")),
    SouthWest(DatId("atsw")),
    West(DatId("atww")),
    NorthWest(DatId("atwn")),
}

enum class GatheringType {
    Harvesting,
    Logging,
    Mining,
}

class ActorGatheringAttempt(
    val type: GatheringType,
    val context: AttackContext,
): ActorActionState

enum class FishingState(val active: Boolean = false) {
    Waiting,
    Hooked,
    ActiveCenter(active = true),
    ActiveLeft(active = true),
    ActiveRight(active = true),
    SuccessFish,
    SuccessMonster,
    Cancel,
    BreakRod,
    BreakLine,
}

enum class FishSize {
    Small,
    Large,
}

class ActorFishingAttempt(
    var currentState: FishingState = FishingState.Waiting,
    var fishSize: FishSize = FishSize.Small,
    var fishHpp: Float = 1f,
): ActorActionState

enum class SynthesisResultType {
    Break,
    NormalQuality,
    HighQuality,
}

class ActorSynthesisAttempt(
    val type: SynthesisType,
    val context: AttackContext,
    var result: SynthesisResultType? = null,
    var complete: Boolean = false,
): ActorActionState

class ActorRestingState(
    var kneeling: Boolean = true,
): ActorActionState {
    override fun cancelledByMovement(): Boolean = true
}

sealed interface CastingResult

object CastingInterrupted: CastingResult
class CastingComplete(val applierResult: ApplierSuccessResult, val castingContext: CastingStateContext): CastingResult

class CastingStateContext(
    val itemId: InternalItemId? = null,
    val rangedItemId: InternalItemId? = null,
    val ammoItemId: InternalItemId? = null,
    val targetAoeCenter: Vector3f? = null,
    val movementLockOverride: Int? = null,
)

class CastingState(
    val castTime: Float,
    val sourceId: ActorId,
    val targetId: ActorId,
    val skill: SkillId,
    val lockTime: Float,
    val context: CastingStateContext = CastingStateContext(),
): ActorActionState {

    private val totalTime = castTime + lockTime
    private var currentTime = 0f
    private var executed = false

    var result: CastingResult? = null

    private val targetFilter = GameEngine.getSkillTargetFilter(skill)

    fun canInterrupt(): Boolean {
        return skill is SpellSkillId && !isReadyToExecute() && !isComplete()
    }

    fun update(elapsedFrames: Float) {
        if (isComplete()) { return }
        currentTime += elapsedFrames
        if (!isTargetValid()) { result = CastingInterrupted }
    }

    fun progress(): Float {
        return currentTime/castTime
    }

    fun percentProgress(): Int {
        return (progress() * 100f).toInt()
    }

    fun getTotalProgress(): Float {
        return currentTime/totalTime
    }

    fun isCharging(): Boolean {
        return result == null
    }

    fun remainingChargeTime(): Duration {
        val remaining = (castTime - currentTime).coerceAtLeast(0f)
        return Fps.framesToSeconds(remaining)
    }

    fun adjustProgressForward(progressInFrames: Float) {
        currentTime = currentTime.coerceAtLeast(progressInFrames)
    }

    fun eagerlyComplete() {
        currentTime = castTime
    }

    fun isReadyToExecute(): Boolean {
        return !executed && (result != null || currentTime >= castTime)
    }

    fun onExecute() {
        executed = true
        currentTime = currentTime.coerceAtLeast(castTime)
    }

    fun isComplete(): Boolean {
        return executed && currentTime >= totalTime
    }

    fun isTargetValid(): Boolean {
        return targetFilter.targetFilter(sourceId, targetId)
    }

    override fun preventsMovement(): Boolean {
        val actorType = ActorStateManager[sourceId]?.type ?: return true
        return actorType != ActorType.Pc
    }

}

class ActorSitChairState(val index: Int, val chairId: ActorId): ActorActionState {
    var sitting = true
    override fun cancelledByMovement(): Boolean = true
}

class ComponentUpdateResult(
    val events: List<Event> = emptyList(),
    val removeComponent: Boolean = false,
)

interface ActorStateComponent {
    fun update(actorState: ActorState, elapsedFrames: Float): ComponentUpdateResult
}

class EngagedState {

    enum class State {
        Disengaged,
        Engaged,
    }

    var state = State.Disengaged

}

class TargetState(val targetId: ActorId?, val locked: Boolean)

class DeathState {
    var framesSinceDeath = 0f

    fun hasBeenDeadFor(duration: Duration): Boolean {
        return timeSinceDeath() >= duration
    }

    fun timeSinceDeath(): Duration {
        return Fps.framesToSeconds(framesSinceDeath)
    }
}

class StatusEffectState(val statusEffect: StatusEffect) {

    var sourceId: ActorId? = null
    var remainingDuration: Float? = null

    var counter = 0
    var linkedStatus: StatusEffect? = null
    var linkedSkillId: SkillId = spellNull_0

    var potency = 0
    var secondaryPotency = 0f

    var preventsAction: Boolean = false
    var preventsMovement: Boolean = false

    var canDispel = statusEffect.canDispel
    var canErase = statusEffect.canErase
    var canEsuna = statusEffect.canEsuna
    var displayCounter = statusEffect.displayCounter

    init {
        if (statusEffect == StatusEffect.Stun || statusEffect == StatusEffect.Sleep || statusEffect == StatusEffect.Petrify || statusEffect == StatusEffect.Terror) {
            preventsMovement = true
            preventsAction = true
        } else if (statusEffect == StatusEffect.Bind) {
            preventsMovement = true
        } else if (statusEffect == StatusEffect.Costume || statusEffect == StatusEffect.CostumeDebuff) {
            preventsAction = true
        }
    }

    fun isExpired(): Boolean {
        return remainingDuration?.let { it <= 0f } ?: false
    }

    fun copyFrom(other: StatusEffectState) {
        remainingDuration = other.remainingDuration
        counter = other.counter
        linkedStatus = other.linkedStatus
        linkedSkillId = other.linkedSkillId
        potency = other.potency
        secondaryPotency = other.secondaryPotency
        preventsAction = other.preventsAction
        preventsMovement = other.preventsMovement
    }

    fun setRemainingDuration(duration: Duration) {
        remainingDuration = Fps.toFrames(duration)
    }

}

class EffectTickTimer {

    companion object {
        private val tickInterval = Fps.secondsToFrames(3)
    }

    private var remaining = tickInterval

    fun update(elapsedFrames: Float): Boolean {
        remaining -= elapsedFrames
        if (remaining > 0) { return false }

        remaining += tickInterval
        return true
    }

}

class ActorCollision(val collisionsByArea: Map<Area, List<CollisionProperty>> = emptyMap(), val freeFallDuration: Float = 0f) {

    fun isInFreeFall(): Boolean {
        return freeFallDuration > 0f
    }

    fun getEnvironment(): Pair<Area?, DatId?> {
        for (perAreaCollisionProperties in collisionsByArea) {
            val first = perAreaCollisionProperties.value.firstOrNull { it.environmentId != null } ?: continue
            return Pair(perAreaCollisionProperties.key, first.environmentId)
        }

        return Pair(null, null)
    }

}

@Serializable
data class JobLevel(var exp: Int, var level: Int) {

    fun gainExp(amount: Int, maximumLevel: Int) {
        exp += amount
        val next = getExpNeeded()

        if (exp < next) { return }

        if (level == maximumLevel) {
            exp = next - 1
            return
        }

        exp -= next
        level += 1

        val nextNext = getExpNeeded()
        exp = exp.coerceAtMost(nextNext - 1)
    }

    fun loseExp(amount: Int) {
        exp -= amount

        if (exp < 0) {
            level -= 1
            exp += getExpNeeded()
            exp = exp.coerceAtLeast(0)
        }
    }

    fun getExpNeeded(): Int {
        return GameState.getGameMode().getExperiencePointsNeeded(level)
    }

}

@Serializable
class JobLevels {

    private val levels = Job.values()
        .associateWith { JobLevel(exp = 0, level = 1) }
        .toMutableMap()

    operator fun get(job: Job?): JobLevel? {
        return levels[job]
    }

    fun copyFrom(other: JobLevels) {
        levels.clear()
        levels.putAll(other.levels)
    }

}

@Serializable
data class JobState(var mainJob: Job = Job.Nop, var subJob: Job? = null) {
    fun copyFrom(other: JobState) {
        this.mainJob = other.mainJob
        this.subJob = other.subJob
    }
}

enum class SkillChainAttribute(val level: Int, val elements: List<SpellElement>) {
    Transfixion(level = 1, elements = listOf(SpellElement.Light)),
    Compression(level = 1, elements = listOf(SpellElement.Dark)),
    Liquefaction(level = 1, elements = listOf(SpellElement.Fire)),
    Scission(level = 1, elements = listOf(SpellElement.Earth)),
    Reverberation(level = 1, elements = listOf(SpellElement.Water)),
    Detonation(level = 1, elements = listOf(SpellElement.Wind)),
    Induration(level = 1, elements = listOf(SpellElement.Ice)),
    Impaction(level = 1, elements = listOf(SpellElement.Lightning)),
    Gravitation(level = 2, elements = listOf(SpellElement.Dark, SpellElement.Earth)),
    Distortion(level = 2, elements = listOf(SpellElement.Water, SpellElement.Ice)),
    Fusion(level = 2, elements = listOf(SpellElement.Fire, SpellElement.Light)),
    Fragmentation(level = 2, elements = listOf(SpellElement.Lightning, SpellElement.Wind)),
    Light(level = 3, elements = listOf(SpellElement.Fire, SpellElement.Wind, SpellElement.Lightning, SpellElement.Light)),
    Darkness(level = 3, elements = listOf(SpellElement.Water, SpellElement.Ice, SpellElement.Earth, SpellElement.Dark)),
    Light2(level = 3, elements = listOf(SpellElement.Fire, SpellElement.Wind, SpellElement.Lightning, SpellElement.Light)),
    Darkness2(level = 3, elements = listOf(SpellElement.Water, SpellElement.Ice, SpellElement.Earth, SpellElement.Dark)),
    Radiance(level = 4, elements = listOf(SpellElement.Fire, SpellElement.Wind, SpellElement.Lightning, SpellElement.Light)),
    Umbra(level = 4, elements = listOf(SpellElement.Water, SpellElement.Ice, SpellElement.Earth, SpellElement.Dark)),
}

class SkillChainRequest(
    val attacker: ActorState,
    val defender: ActorState,
    val currentState: SkillChainState?,
    val skill: SkillId,
)

sealed interface SkillChainState {
    val window: Duration
    var age: Float

    fun isSkillChainWindowOpen(): Boolean {
        return Fps.framesToSeconds(age) >= 3.seconds
    }
}

class SkillChainOpen(val attributes: List<SkillChainAttribute>, override val window: Duration): SkillChainState {
    override var age = 0f
}

class SkillChainStep(val step: Int, val attribute: SkillChainAttribute, override val window: Duration): SkillChainState {
    override var age = 0f
}

class SkillChainTargetState {

    var skillChainState: SkillChainState? = null
        private set

    fun update(elapsedFrames: Float) {
        val currentStep = skillChainState ?: return
        currentStep.age += elapsedFrames
        if (currentStep.age > Fps.toFrames(currentStep.window)) { skillChainState = null }
    }

    fun checkResult(attacker: ActorState, defender: ActorState, skill: SkillId): SkillChainState? {
        return GameState.getGameMode().getSkillChainResult(SkillChainRequest(
                attacker = attacker,
                defender = defender,
                currentState = skillChainState,
                skill = skill,
        ))
    }

    fun isSkillChainWindowOpen(): Boolean {
        return skillChainState?.isSkillChainWindowOpen() ?: false
    }

    fun applySkill(attacker: ActorState, defender: ActorState, skill: SkillId): SkillChainState? {
        skillChainState = checkResult(attacker, defender, skill) ?: return null
        return skillChainState
    }

    fun canMagicBurst(spellElement: SpellElement): Boolean {
        val current = skillChainState as? SkillChainStep ?: return false
        return current.attribute.elements.any { it == spellElement }
    }

}

class AnimationSettings(val popRoutines: List<DatId>? = null) {
    var deathAnimation: DatId? = null
}

enum class CurrencyType(val displayName: String) {
    Gil(displayName = "G"),
}

@Serializable
data class Currency(val currencies: MutableMap<CurrencyType, Int> = HashMap())
