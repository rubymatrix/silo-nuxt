package xim.poc.game.event

import xim.math.Vector3f
import xim.poc.ActorController
import xim.poc.ActorId
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.configuration.AutoAttackOnlyBehaviorId
import xim.poc.game.configuration.BehaviorId
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.MonsterId
import xim.poc.tools.MogHouseSetting
import xim.resource.DatId
import xim.resource.table.NpcInfo

data class DependentSettings(
    val ownerId: ActorId,
    val type: ActorDependentType,
)

data class JobSettings(
    val mainJob: Job,
    val subJob: Job?,
)

data class ZoneSettings(
    val zoneId: Int,
    val subAreaId: Int? = null,
    val entryId: DatId? = null,
    val mogHouseSetting: MogHouseSetting? = null,
)

data class InitialActorState(
    val name: String,
    val position: Vector3f,
    val modelLook: ModelLook,
    val type: ActorType,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val targetSize: Float = 0f,
    val autoAttackRange: Float = 4.5f,
    val maxTargetDistance: Float? = null,
    val zoneSettings: ZoneSettings? = null,
    val behaviorController: BehaviorId = AutoAttackOnlyBehaviorId,
    val movementController: ActorController = NoOpActorController(),
    val presetId: ActorId? = null,
    val dependentSettings: DependentSettings? = null,
    val jobSettings: JobSettings? = null,
    val jobLevels: JobLevels? = null,
    val npcInfo: NpcInfo? = null,
    val monsterId: MonsterId? = null,
    val appearanceState: Int? = null,
    val targetable: Boolean = true,
    val staticPosition: Boolean = false,
    val facesTarget: Boolean = true,
    val popRoutines: List<DatId>? = null,
    val displayHp: Boolean? = null,
    val components: List<ActorStateComponent> = emptyList(),
)

class ActorCreateEvent(
    val initialActorState: InitialActorState,
    val createCallback: ActorPromise? = null
): Event {

    override fun apply(): List<Event> {
        val state = ActorStateManager.create(initialActorState)

        val outputEvents = ArrayList<Event>()

        if (state.owner != null) {
            state.animationSettings.deathAnimation = DatId.specialDeath
        }

        if (initialActorState.jobSettings != null) {
            state.jobState.mainJob = initialActorState.jobSettings.mainJob
            state.jobState.subJob = initialActorState.jobSettings.subJob
        }

        if (initialActorState.jobLevels != null) {
            state.jobLevels.copyFrom(initialActorState.jobLevels)
        }

        if (initialActorState.appearanceState != null) {
            state.appearanceState = initialActorState.appearanceState
        }

        if (initialActorState.jobLevels == null && initialActorState.monsterId != null) {
            val monsterDefinition = MonsterDefinitions[initialActorState.monsterId]
            state.jobLevels[Job.Nop]?.level = monsterDefinition.baseLevel
        }

        if (initialActorState.displayHp == null) {
            state.displayHp = if (initialActorState.npcInfo != null) {
                initialActorState.npcInfo.shouldDisplayNameAndHp()
            } else {
                initialActorState.type != ActorType.StaticNpc && initialActorState.type != ActorType.Effect
            }
        }

        state.components += initialActorState.components.associateBy { it::class }

        outputEvents += ActorUpdateBaseLookEvent(state.id, initialActorState.modelLook)

        createCallback?.fulfill(state)
        outputEvents += ActorPostCreateEvent(state.id)

        return outputEvents
    }

}