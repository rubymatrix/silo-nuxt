package xim.poc

import xim.math.Vector3f
import xim.poc.audio.AudioManager
import xim.poc.browser.DatLoader
import xim.poc.browser.ParserContext
import xim.poc.camera.CameraReference
import xim.poc.game.*
import xim.poc.game.actor.components.ElevatorStatus
import xim.poc.game.actor.components.getDoorState
import xim.poc.game.actor.components.getElevatorState
import xim.poc.game.actor.components.isDualWield
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.*
import xim.poc.gl.ByteColor
import xim.poc.tools.PlayerLookTool
import xim.resource.*
import xim.resource.InventoryItems.toItemInfo
import xim.resource.table.FileTableManager
import xim.resource.table.MainDll
import xim.resource.table.NpcInfo
import xim.resource.table.NpcTable
import xim.resource.table.SpellInfoTable.toSpellInfo
import xim.util.Fps
import xim.util.OnceLogger.warn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.time.Duration.Companion.seconds

value class ActorId(val id: Int)

private enum class EngageAnimationState {
    NotEngaged,
    Engaged,
    Engaging,
    Disengaging,
}

private enum class CastingAnimationState {
    None,
    Charging,
    Completing,
}

enum class Direction {
    None,
    Forward,
    Left,
    Right,
    Backward,
}

fun interface ReadyToDrawAction {
    fun invoke(actor: Actor)
}

class RenderState {
    var effectColor: ByteColor = ByteColor.half
    val wrapEffect = ActorWrapEffect()
    var forceHideShadow: Boolean = false
}

class Actor private constructor(val id: ActorId, val state: ActorState) {

    companion object {
        fun createFrom(actorState: ActorState): Actor {
            return Actor(actorState.id, actorState)
        }
    }

    private var displayDoorOpen = false
    private var displayResting = false
    private var displayGathering = false
    private var displaySitChair = false

    private var synthesisAnimationState: SynthesisAnimationStateMachine? = null

    private val readyToDrawActions = ArrayList<ReadyToDrawAction>()
    private val routineQueue = ActorRoutineQueue(this)

    var displayFacingDir = 0f
        private set

    var displayFacingSkew = 0f
        private set

    private var turnAmount = 0f
    private var destSkewAmount = 0f
    private var strafing = false

    val displayPosition = Vector3f()
    val displayPositionOffset = Vector3f()

    val previousPosition = Vector3f()
    val movement = Vector3f()

    var displayDead = false
    var displayAppearanceState = 0

    val currentVelocity: Vector3f = Vector3f()
    var stoppedMoving = false

    val target: ActorId?
        get() = state.targetState.targetId

    var subTarget: ActorId? = null

    private var engageAnimationState = EngageAnimationState.NotEngaged
    private var castingAnimationState = CastingAnimationState.None
    private var elevatorAnimationState = state.getElevatorState()?.currentStatus
    private var fishingAnimationState: FishingState? = null

    val renderState = RenderState()
    val effectOverride = ActorContextEffectOverride()

    private var displayLook = ModelLook.blank()
    var actorModel: ActorModel? = null
        private set

    private val skeletonBoundingBox = ArrayList<BoundingBox>()

    private val hasDftIdle = DatLink<SkeletonAnimationResource>(DatId("dft0"))

    init {
        onReadyToDraw { startAutoRunParticles() }
        syncFromState()
        initDisplay()
        update(0f)
    }

    fun syncFromState() {
        // TODO - resting, synthesis, etc
        displayFacingDir = state.rotation
        displayPosition.copyFrom(state.position)
        displayAppearanceState = state.appearanceState
        displayDead = state.isDead()
    }

    fun update(elapsedFrames: Float) {
        updateVelocity(state.velocity)

        if (elapsedFrames > 0f) { previousPosition.copyFrom(displayPosition) }
        displayPosition.copyFrom(state.position + displayPositionOffset)
        if (elapsedFrames > 0f) { movement.copyFrom(displayPosition - previousPosition) }

        updateDestinationFacingDir(state.rotation)

        if (state.isParticle()) {
            syncFromParticle()
        }

        if (readyToDrawActions.isNotEmpty() && isReadyToDraw()) {
            readyToDrawActions.forEach { it.invoke(this) }
            readyToDrawActions.clear()
        }

        routineQueue.update(elapsedFrames)

        updateModelDisplay()

        updateRestingDisplay(state)

        updateSitChairDisplay(state)

        updateAppearanceState(state)

        updateEngageDisplay(state)

        updateDoorDisplay(state)

        updateElevatorDisplay(state)

        updateSynthesisAnimationState(synthesisAnimationState)

        updateCastingState(state)

        updateGatheringState()

        updateFishingState()

        updateFacingDir(elapsedFrames)
        updateFacingSkew(elapsedFrames)
    }

    fun getSkeletonBoundingBox(index: Int = 0): BoundingBox? {
        return skeletonBoundingBox.getOrNull(index)
    }

    fun updateSkeletonBoundingBoxes(box: List<BoundingBox>) {
        skeletonBoundingBox.clear()
        skeletonBoundingBox.addAll(box)
    }

    fun isPlayer(): Boolean {
        return id == ActorStateManager.playerId
    }

    fun getNpcInfo(): NpcInfo? {
        return state.getNpcInfo()
    }

    fun isTranslucent(): Boolean {
        return renderState.effectColor.a < 127
    }

    fun isDead(): Boolean {
        return state.isDead()
    }

    fun isDisplayedDead(): Boolean {
        return displayDead
    }

    fun setStrafing(toggle: Boolean) {
        strafing = toggle
    }

    fun isStrafing(): Boolean {
        return strafing
    }

    private fun updateCastingState(actorState: ActorState) {
        val castingState = actorState.getCastingState()
        if (castingState == null) {
            castingAnimationState = CastingAnimationState.None
            return
        }

        if (castingState.isCharging() && !castingState.isReadyToExecute()) {
            startCastingSkill(castingState.skill)
            return
        }

        val castingResult = castingState.result ?: return

        when (castingResult) {
            is CastingComplete -> executeSkill(castingResult)
            CastingInterrupted -> interruptSkill(castingState)
        }
    }

    private fun startCastingSkill(skill: SkillId) {
        if (castingAnimationState == CastingAnimationState.Charging) { return }
        castingAnimationState = CastingAnimationState.Charging

        EffectDisplayer.preloadSkillResource(skill)

        when (skill) {
            is SpellSkillId -> startCasting(skill.toSpellInfo())
            is MobSkillId -> readySkill()
            is AbilitySkillId -> readySkill()
            is ItemSkillId -> startUsingItem(skill.toItemInfo())
            is RangedAttackSkillId -> startRangedAttack()
        }
    }

    private fun startRangedAttack() {
        enqueueModelRoutine(DatId("calg"), actorContext = makeStandardContext(), options = RoutineOptions(highPriority = true))
    }

    private fun startCasting(spellInfo: SpellInfo) {
        val animationId = DatId.castId(spellInfo) ?: return
        enqueueModelRoutine(animationId, actorContext = makeStandardContext(), options = RoutineOptions(highPriority = true))
    }

    private fun startUsingItem(inventoryItemInfo: InventoryItemInfo) {
        enqueueModelRoutine(DatId.castId(inventoryItemInfo), actorContext = makeStandardContext(), options = RoutineOptions(highPriority = true))
    }

    private fun readySkill() {
        enqueueModelRoutine(DatId("cate"), actorContext = makeStandardContext(), options = RoutineOptions(highPriority = true))
    }

    private fun executeSkill(castingComplete: CastingComplete) {
        if (castingAnimationState == CastingAnimationState.Completing) { return }
        castingAnimationState = CastingAnimationState.Completing

        EffectDisplayer.displaySkill(
            sourceId = state.id,
            skillId = castingComplete.applierResult.executedSkill,
            primaryTargetId = castingComplete.applierResult.primaryTargetId,
            allTargetIds = castingComplete.applierResult.allTargetIds,
            actionContext = castingComplete.applierResult.contexts,
            castingContext = castingComplete.castingContext,
        )
    }

    private fun interruptSkill(castingState: CastingState) {
        if (castingAnimationState == CastingAnimationState.Completing) { return }
        castingAnimationState = CastingAnimationState.Completing

        val interruptAnimationId = (when (castingState.skill) {
            is SpellSkillId -> DatId.stopCastId(castingState.skill.toSpellInfo())
            is MobSkillId -> DatId("spte")
            is AbilitySkillId -> DatId("spte")
            is ItemSkillId -> DatId("spit")
            is RangedAttackSkillId -> DatId("splg")
        }) ?: return

        enqueueModelRoutine(interruptAnimationId)
    }

    fun transitionToIdle(transitionTime: Float) {
        actorModel?.forceTransitionToIdle(idleId = getIdleAnimationId(), transitionTime = transitionTime, animationDirs = getAllAnimationDirectories())
    }

    fun getIdleAnimationId(): DatId {
        val model = actorModel?.model
        val isChocobo = model is PcModel && model.raceGenderConfig.chocobo
        if (isChocobo || state.isMount()) { return DatId("chi?") }

        val mountDef = state.mountedState?.getInfo()
        if (mountDef != null) { return DatId("${mountDef.poseType}un?") }

        if (state.isStaticNpc() && hasDftIdle()) {
            return DatId("dft?")
        }

        if (isDisplayedDead() && state.owner == null) {
            return getAnimationModeVariant(DatId("cor?"), actorModel?.idleAnimationMode) { DatId("${it}cr?") }
        }

        return if (engageAnimationState == EngageAnimationState.Engaged || engageAnimationState == EngageAnimationState.Disengaging) {
            getAnimationModeVariant(DatId("btl?"), actorModel?.battleAnimationMode) { DatId("${it}tl?") }
        } else {
            getAnimationModeVariant(DatId("idl?"), actorModel?.idleAnimationMode) { DatId("${it}dl?") }
        }
    }

    private fun hasDftIdle(): Boolean {
        if (!isReadyToDraw()) { return false }
        hasDftIdle.getOrPut { getAllAnimationDirectories().firstNotNullOfOrNull { d -> d.getNullableChildRecursivelyAs(it, SkeletonAnimationResource::class) } }
        return hasDftIdle.getIfPresent() != null
    }

    private fun updateSynthesisAnimationState(currentStateMachine: SynthesisAnimationStateMachine?) {
        val currentSynthesisAttempt = state.actionState as? ActorSynthesisAttempt

        if (currentStateMachine == null && currentSynthesisAttempt != null) {
            synthesisAnimationState = SynthesisAnimationStateMachine(this, currentSynthesisAttempt)
        }

        currentStateMachine ?: return

        currentStateMachine.update()
        if (currentStateMachine.isComplete()) { synthesisAnimationState = null }
    }

    private fun updateGatheringState() {
        val attempt = state.actionState as? ActorGatheringAttempt

        if (!displayGathering && attempt != null) {
            onGatheringAttempt(attempt.type)
        } else if (attempt == null) {
            displayGathering = false
        }
    }

    private fun updateFishingState() {
        val currentState = state.getFishingState()?.currentState
        val currentAnimation = fishingAnimationState

        if (currentState == currentAnimation) { return }

        if (currentState == null) {
            fishingAnimationState = null
            return
        }

        val animationId = when (currentState) {
            FishingState.Waiting -> DatId("fsh0")
            FishingState.Hooked -> DatId("fsh1")
            FishingState.SuccessFish -> DatId("fsh2")
            FishingState.BreakRod -> DatId("fsh3")
            FishingState.BreakLine -> DatId("fsh4")
            FishingState.SuccessMonster -> DatId("fsh5")
            FishingState.Cancel -> DatId("fsh6")
            FishingState.ActiveCenter -> DatId("fsh7")
            FishingState.ActiveRight -> DatId("fsh8")
            FishingState.ActiveLeft -> DatId("fsh9")
        }

        if (!currentState.active) {
            listOf(DatId("fsh7"), DatId("fsh8"), DatId("fsh9")).forEach { stopRoutine(it) }
        }

        enqueueModelRoutine(animationId, options = RoutineOptions(highPriority = true))
        fishingAnimationState = currentState

        val fishingRodActor = ActorManager[state.fishingRod]
        fishingRodActor?.enqueueModelRoutine(animationId, options = RoutineOptions(highPriority = true))
    }

    private fun engage() {
        if (engageAnimationState == EngageAnimationState.Engaged) { return }
        engageAnimationState = EngageAnimationState.Engaged

        routineQueue.enqueueItemModelRoutine(ItemModelSlot.Main, DatId("!w01"))
        routineQueue.enqueueItemModelRoutine(ItemModelSlot.Sub, DatId("!w11"))
    }

    private fun disengage() {
        engageAnimationState = EngageAnimationState.NotEngaged
    }

    fun fadeAway() {
        playRoutine(DatId.disappear) { renderState.effectColor = ByteColor.zero }
    }

    fun onRevive() {
        displayDead = false
        actorModel?.clearAnimations()
        transitionToIdle(0f)
    }

    private fun isFullyOutOfCombat(): Boolean {
        return engageAnimationState == EngageAnimationState.NotEngaged
    }

    fun isDisplayEngaged(): Boolean {
        return engageAnimationState == EngageAnimationState.Engaged
    }

    fun isDisplayEngagedOrEngaging(): Boolean {
        return engageAnimationState == EngageAnimationState.Engaged || engageAnimationState == EngageAnimationState.Engaging
    }

    fun getAllAnimationDirectories() : List<DirectoryResource> {
        val model = actorModel?.model ?: return emptyList()

        if (isFullyOutOfCombat()) { return model.getAnimationDirectories() }

        val main = model.getMainBattleAnimationDirectory()
        val sub = model.getSubBattleAnimationDirectory()

        return listOfNotNull(main, sub) + model.getAnimationDirectories()
    }

    fun isReadyToDraw() : Boolean {
        val model = actorModel?.model ?: return false

        val mountReady = ActorManager[getMount()?.id]?.isReadyToDraw() ?: true
        if (!mountReady) { return false }

        if (isFullyOutOfCombat()) { return model.isReadyToDraw() }

        if (model.getMainBattleAnimationDirectory() == null) { return false }
        if (isDualWield() && model.getSubBattleAnimationDirectory() == null) { return false }

        return true
    }

    fun getMovementDirection(): Direction {
        val targetDirection = if (isTargetLocked()) {
            state.getTargetDirectionVector() ?: return Direction.None
        } else if (isStrafing()) {
            CameraReference.getInstance().getViewVector()
        } else {
            return Direction.None
        }

        val movementVelocity = Vector3f().copyFrom(currentVelocity).also { it.y = 0f }
        if (movementVelocity.magnitudeSquare() <= 1e-5) {
            return Direction.None
        }

        val normalizedVelocity = movementVelocity.normalizeInPlace()
        val cosAngle = normalizedVelocity.dot(targetDirection)

        // Prefer to run forward > horizontal > backward
        return if (cosAngle >= 0.25f ) {
            return Direction.Forward
        } else if (cosAngle >= -0.75f) {
            val right = targetDirection.cross(Vector3f.UP).normalizeInPlace()
            val horizontalCosAngle = normalizedVelocity.dot(right)
            if (horizontalCosAngle >= 0f) {
                Direction.Right
            } else {
                Direction.Left
            }
        } else {
            Direction.Backward
        }
    }

    fun getMovementAnimation(): DatId {
        if (state.isWalking()) {
            return getAnimationModeVariant(DatId("wlk?"), actorModel?.runningAnimationMode) { DatId("${it}lk?") }
        }

        return when (getMovementDirection()) {
            Direction.None, Direction.Forward -> getAnimationModeVariant(DatId("run?"), actorModel?.runningAnimationMode) { DatId("${it}un?") }
            Direction.Left -> DatId("mvl?")
            Direction.Right -> DatId("mvr?")
            Direction.Backward -> DatId("mvb?")
        }
    }

    fun getJointPosition(index: Int): Vector3f {
        return actorModel?.getJointPosition(index) ?: Vector3f.ZERO
    }

    fun getWorldSpaceJointPosition(index: Int): Vector3f {
        return displayPosition + getJointPosition(index)
    }

    private fun updateDestinationFacingDir(dest: Float) {
        turnAmount = dest - displayFacingDir
        if (abs(turnAmount) > PI ) {
            turnAmount = if (sign(turnAmount) == -1f ) {
                turnAmount + 2 * PI.toFloat()
            } else {
                turnAmount - 2 * PI.toFloat()
            }
        }

        if (movement.magnitude() < 1e-4f) { return }
        val movementDirection = movement.normalize()

        val c = movementDirection.dot(Vector3f.UP)
        destSkewAmount = if (abs(c) > 0.6f) { 0f } else { -c }
    }

    private fun updateFacingDir(elapsedFrames: Float) {
        if (abs(turnAmount) == 0f) { return }
        val stepSize = if (state.isShip()) { 0.001f } else { 0.05f }

        val stepAmount = sign(turnAmount) * stepSize * elapsedFrames * PI.toFloat()
        if (abs(turnAmount) < abs(stepAmount) ) {
            displayFacingDir += turnAmount
            turnAmount = 0f
            return
        }

        displayFacingDir += stepAmount
        turnAmount -= stepAmount

        if (displayFacingDir < -PI) {
            displayFacingDir += 2 * PI.toFloat()
        } else if (displayFacingDir > PI) {
            displayFacingDir -= 2 * PI.toFloat()
        }
    }

    private fun updateFacingSkew(elapsedFrames: Float) {
        val delta = displayFacingSkew - destSkewAmount

        val absDelta = abs(delta)
        if (absDelta < 1e-3f) { return }

        displayFacingSkew -= (0.025f * sign(delta) * elapsedFrames).coerceAtMost(absDelta)
    }

    fun isDualWield(): Boolean {
        return state.isDualWield()
    }

    private fun updateModelDisplay() {
        val stateLook = state.getCurrentLook()

        val needsNewModel = displayLook.type != stateLook.type || displayLook.modelId != stateLook.modelId

        displayLook = stateLook.copy()
        val currentModel = actorModel?.model

        if (needsNewModel) {
            if (currentModel is PcModel) { currentModel.updateEquipment(EquipmentLook()) } // Clear equipment effects
            actorModel = createActorModel()
            onReadyToDraw { transitionToIdle(0f) }
        } else if (state.type == ActorType.Pc && currentModel is PcModel) {
            val equipmentLook = PlayerLookTool.getDebugOverride(this) ?: stateLook.equipment
            currentModel.updateEquipment(equipmentLook)
        }
    }

    private fun createActorModel(): ActorModel? {
        return when (displayLook.type) {
            0 -> {
                val additionalAnimationId = FileTableManager.getFilePath(NpcTable.getAdditionalAnimationId(displayLook))
                val npcModel = NpcModel.fromNpcLook(displayLook, listOfNotNull(additionalAnimationId)) ?: return null
                ActorModel(this, npcModel)
            }
            1 -> {
                displayLook.race ?: return null
                ActorModel(this, PcModel(displayLook, this))
            }
            2, 3 -> {
                val datId = getNpcInfo()?.datId ?: return null
                val scene = SceneManager.getCurrentScene()
                ActorModel(this, ZoneObjectModel(datId, scene))
            }
            4 -> {
                val nameId = state.getNpcInfo()?.nameId ?: return null
                val model = NpcModel.fromName(nameId) ?: return null
                ActorModel(this, model)
            }
            6 -> {
                val npcWithBaseModel = NpcWithBaseModel.fromLook(displayLook) ?: return null
                ActorModel(this, npcWithBaseModel)
            }
            ModelLook.fileTableIndexType -> {
                val npcModel = NpcModel.fromNpcLook(displayLook) ?: return null
                ActorModel(this, npcModel)
            }
            ModelLook.particleType -> {
                val particle = (state.dependentType as? ActorParticle)?.particle ?: return null
                val directory = particle.dummyActorLink?.directory ?: return null
                return ActorModel(this, ParticleModel(directory))
            }
            else -> {
                null
            }
        }
    }

    fun enqueueRoutine(context: ActorContext, options: RoutineOptions = RoutineOptions(), routineProvider: () -> EffectRoutineResource?) {
        routineQueue.enqueueRoutine(context, options, routineProvider)
    }

    fun enqueueModelRoutine(routineId: DatId, actorContext: ActorContext = makeStandardContext(), options: RoutineOptions = RoutineOptions()) {
        routineQueue.enqueueRoutine(actorContext, options) { findAnimationRoutine(routineId) }
    }

    fun enqueueModelRoutineIfReady(routineId: DatId, actorContext: ActorContext = makeStandardContext(), options: RoutineOptions = RoutineOptions()): Boolean {
        val routine = findAnimationRoutine(routineId)
        routineQueue.enqueueRoutine(actorContext, options, routine)
        return routine != null
    }

    fun enqueueClearEffectsRoutine() {
        routineQueue.enqueueRoutine(context = makeStandardContext(), effectRoutineResource = null, options = RoutineOptions(
            callback = { EffectManager.clearEffects(ActorAssociation(this)) }
        ))
    }

    fun hasEnqueuedRoutines(): Boolean {
        return routineQueue.hasEnqueuedRoutines()
    }

    fun stopRoutine(routineId: DatId) {
        EffectManager.forEachEffectForAssociation(ActorAssociation(this)) {
            if (it.routineId == routineId) { it.stop() }
        }
    }

    fun playRoutine(routineId: DatId, routineCompleteCallback: RoutineCompleteCallback? = null): EffectRoutineInstance? {
        val routine = findAnimationRoutine(routineId)

        return if (routine == null) {
            routineCompleteCallback?.onComplete()
            null
        } else {
            playRoutine(routine).also { it.registerAllEffectsCompletedCallback(routineCompleteCallback) }
        }
    }

    private fun playRoutine(routine: EffectRoutineResource): EffectRoutineInstance {
        return EffectManager.registerActorRoutine(this, makeStandardContext(), routine)
    }

    private fun loopRoutine(routine: DatId): EffectRoutineInstance? {
        val output = playRoutine(routine)
        output?.repeatOnSequencesCompleted = true
        return output
    }

    fun playEmote(mainId: Int, subId: Int, routineCompleteCallback: RoutineCompleteCallback? = null) {
        val raceGenderConfig = displayLook.race ?: return
        val fileIndexId = MainDll.getBaseEmoteAnimationIndex(raceGenderConfig) + mainId

        val filePath = FileTableManager.getFilePath(fileIndexId)
        if (filePath == null) {
            warn("Couldn't resolve emote: $mainId/$subId")
            return
        }

        DatLoader.load(filePath, parserContext = ParserContext.optionalResource).onReady {
            val emoteId = DatId("em0${subId}")
            val routine = it.getAsResource().getNullableChildRecursivelyAs(emoteId, EffectRoutineResource::class)

            if (routine != null) {
                val routineInstance = EffectManager.registerActorRoutine(this, makeStandardContext(), routine)
                routineInstance.registerAllEffectsCompletedCallback(routineCompleteCallback)
            }
        }
    }

    private fun findLocalAnimationRoutine(routineId: DatId): EffectRoutineResource? {
        return actorModel?.model?.getAnimationDirectories()?.firstNotNullOfOrNull { it.getNullableChildAs(routineId, EffectRoutineResource::class) }
    }

    private fun findAnimationRoutine(routineId: DatId): EffectRoutineResource? {
        return findLocalAnimationRoutine(routineId) ?: GlobalDirectory.directoryResource.getNullableChildRecursivelyAs(routineId, EffectRoutineResource::class)
    }

    fun onReadyToDraw(readyToDrawAction: ReadyToDrawAction) {
        readyToDrawActions += readyToDrawAction
    }

    private fun playSoundPrefixed(prefix: String) {
        val randomMatch = getAllAnimationDirectories()
            .flatMap { it.collectByType(SoundPointerResource::class) }
            .filter { it.id.id.startsWith(prefix) }
            .randomOrNull() ?: return

        AudioManager.playSoundEffect(randomMatch, ActorAssociation(this, makeStandardContext()), positionFn = { displayPosition })
    }

    fun getPetId() = state.pet

    fun getMount() = state.mountedState

    private fun startAutoRunParticles() {
        val model = actorModel?.model
        if (model !is NpcModel) { return }
        if (state.owner != null) { return }

        val association = ActorAssociation(this, context = makeStandardContext())
        model.resource.getAsResource().collectByTypeRecursive(EffectResource::class)
            .filter { it.particleGenerator.autoRun }
            .map { EffectManager.registerEffect(association, it) }
            .forEach { it.update(elapsedFrames = 0f) }
    }

    fun isDoor(): Boolean {
        return state.isDoor()
    }

    private fun updateRestingDisplay(state: ActorState) {
        val restingState = state.actionState as? ActorRestingState

        if (displayResting && (restingState == null || !restingState.kneeling)) {
            displayResting = false
            enqueueModelRoutineIfReady(DatId.stopResting)
        } else if (!displayResting && (restingState != null && restingState.kneeling)) {
            displayResting = true
            enqueueModelRoutineIfReady(DatId.startResting)
        }
    }

    private fun updateSitChairDisplay(state: ActorState) {
        val sitChairState = state.actionState as? ActorSitChairState

        if (displaySitChair && (sitChairState == null || !sitChairState.sitting)) {
            displaySitChair = false
            enqueueModelRoutineIfReady(DatId("chi2"))
            ActorManager[sitChairState?.chairId]?.enqueueModelRoutine(DatId("chi2"))
        } else if (!displaySitChair && (sitChairState != null && sitChairState.sitting)) {
            displaySitChair = enqueueModelRoutineIfReady(DatId("chi0"))
        }
    }

    private fun updateDoorDisplay(state: ActorState) {
        val doorState = state.getDoorState()

        if (displayDoorOpen && !doorState.open) {
            displayDoorOpen = false
            enqueueModelRoutineIfReady(DatId.close) || enqueueModelRoutineIfReady(DatId.eventEnd)
        } else if (!displayDoorOpen && doorState.open) {
            displayDoorOpen = true
            enqueueModelRoutineIfReady(DatId.open) || enqueueModelRoutineIfReady(DatId.eventStart)
        }
    }

    private fun updateElevatorDisplay(state: ActorState) {
        val newState = state.getElevatorState()?.currentStatus ?: return
        if (newState == elevatorAnimationState) { return }

        val currentState = elevatorAnimationState
        if (currentState == null) {
            elevatorAnimationState = newState
            return
        }

        val routineId = when (newState) {
            ElevatorStatus.IdleTop -> DatId("mv11")
            ElevatorStatus.IdleBottom -> DatId("mv00")
            ElevatorStatus.Descending -> DatId("mv10")
            ElevatorStatus.Ascending -> DatId("mv01")
        }

        enqueueModelRoutineIfReady(routineId)
        elevatorAnimationState = newState
    }

    private fun updateEngageDisplay(state: ActorState) {
        if (state.isDead()) { return }

        if (state.isEngaged() && engageAnimationState == EngageAnimationState.NotEngaged) {
            onEngage()
        } else if (!state.isEngaged() && engageAnimationState == EngageAnimationState.Engaged) {
            onDisengage()
        }
    }

    private fun onEngage() {
        engageAnimationState = EngageAnimationState.Engaging

        if (state.isEnemy()) {
            transitionToIdle(7.5f)
            playSoundPrefixed("idl")
        }

        routineQueue.enqueueItemModelRoutine(ItemModelSlot.Main, DatId("!w00"))
        routineQueue.enqueueItemModelRoutine(ItemModelSlot.Sub, DatId("!w10"))

        val mainWeaponInfo = actorModel?.model?.getMainWeaponInfo()
        if (mainWeaponInfo != null && getState().isIdleOrEngaged()) {
            routineQueue.enqueueMainBattleRoutine(DatId("in ${mainWeaponInfo.weaponAnimationSubType}"), id) { engage() }
        } else {
            engage()
        }
    }

    private fun onDisengage() {
        engageAnimationState = EngageAnimationState.Disengaging

        routineQueue.enqueueItemModelRoutine(ItemModelSlot.Main, DatId("!w02"))
        routineQueue.enqueueItemModelRoutine(ItemModelSlot.Sub, DatId("!w12"))

        if (isDead()) {
            disengage()
            return
        }

        if (isPlayer()) { UiStateHelper.popActionContext() }

        val mainWeaponInfo = actorModel?.model?.getMainWeaponInfo()
        val onComplete = { disengage() }

        if (mainWeaponInfo != null) {
            routineQueue.enqueueMainBattleRoutine(DatId("out${mainWeaponInfo.weaponAnimationSubType}"), id, callback = onComplete)
        } else {
            onComplete.invoke()
        }
    }

    fun onDisplayDeath() {
        if (isPlayer()) { UiStateHelper.clear() }
        var deadRoutineId = state.animationSettings.deathAnimation ?: DatId("dead")

        val deadRoutine = findAnimationRoutine(deadRoutineId)
        if (deadRoutine == null) { deadRoutineId = DatId("dea${displayAppearanceState}") }

        enqueueModelRoutineIfReady(deadRoutineId, options = RoutineOptions { displayDead = true })
    }

    fun displayAutoAttack(autoAttackType: AutoAttackType, attackContext: AttackContext, targetState: ActorState, totalAttacksInRound: Int) {
        // Assume about ~60 frames per auto-attack, with a little pause between rounds
        val autoAttackInterval = GameEngine.getAutoAttackRecast(state)
        val framesPerAttack = (autoAttackInterval / (totalAttacksInRound + 1)).coerceIn(30f, 60f)
        val playbackRate = 60f / framesPerAttack

        val voiceOptions = RoutineOptions(blocking = false, expiryDuration = 5.seconds)
        routineQueue.enqueueMainBattleRoutine(DatId("atk0"), id, AttackContext(), voiceOptions)

        val animationOptions = RoutineOptions(expiryDuration = 5.seconds, playbackRate = playbackRate)
        when (autoAttackType) {
            is MainHandAutoAttack -> onAttackMainHand(attackContext, targetState, animationOptions, autoAttackType.subType)
            is OffHandAutoAttack -> onAttackSubHand(attackContext, targetState, animationOptions)
            is RangedAutoAttack -> onRangedAttack(attackContext, targetState)
        }
    }

    private fun onAttackMainHand(attackContext: AttackContext, targetState: ActorState, options: RoutineOptions, subType: AutoAttackSubType) {
        if (subType == AutoAttackSubType.H2HOffHand) {
            routineQueue.enqueueMainBattleRoutine(targetState.id, attackContext, options) {
                actorModel?.getSubAttackIds(displayAppearanceState)?.randomOrNull() ?: DatId("bti0")
            }
            return
        }

        if (subType == AutoAttackSubType.H2HKick) {
            routineQueue.enqueueMainBattleRoutine(targetState.id, attackContext, options) {
                listOf(DatId("cti0"), DatId("dti0")).random()
            }
            return
        }

        routineQueue.enqueueMainBattleRoutine(targetState.id, attackContext, options) {
            if (state.behaviorController.hasDirectionalAutoAttacks()) {
                return@enqueueMainBattleRoutine state.getDirectionalAutoAttack(targetState).id
            }

            when(getMovementDirection()) {
                Direction.None -> actorModel?.getMainAttackIds(displayAppearanceState)?.randomOrNull() ?: DatId("ati0")
                Direction.Forward -> DatId("atf0")
                Direction.Left -> DatId("atl0")
                Direction.Right -> DatId("atr0")
                Direction.Backward -> DatId("atb0")
            }
        }
    }

    private fun onAttackSubHand(attackContext: AttackContext, targetState: ActorState, options: RoutineOptions) {
        routineQueue.enqueueSubBattleRoutine(targetState.id, attackContext, options) {
            when(getMovementDirection()) {
                Direction.None -> actorModel?.getSubAttackIds(displayAppearanceState)?.randomOrNull() ?: DatId("bti0")
                Direction.Forward -> DatId("btf0")
                Direction.Left -> DatId("btl0")
                Direction.Right -> DatId("btr0")
                Direction.Backward -> DatId("btb0")
            }
        }
    }

    private fun onRangedAttack(attackContext: AttackContext, targetState: ActorState) {
        val context = ActorContext(id, targetState.id, attackContexts = AttackContexts.single(targetState.id, attackContext))
        enqueueModelRoutine(DatId("shlg"), context)
    }

    fun getState(): ActorState {
        return state
    }

    private fun updateVelocity(newVelocity: Vector3f) {
        stoppedMoving = currentVelocity.magnitudeSquare() > 0f && newVelocity.magnitudeSquare() == 0f
        currentVelocity.copyFrom(newVelocity)
    }

    fun isTargetLocked(): Boolean {
        return state.targetState.locked
    }

    fun isDisplayInvisible(): Boolean {
        return renderState.effectColor.a == 0
    }

    private fun isMovementLocked(): Boolean {
        return actorModel?.isMovementLocked() == true
    }

    private fun isAnimationLocked(): Boolean {
        return actorModel?.isAnimationLocked() == true
    }

    fun isFacingLocked(): Boolean {
        return actorModel?.isFacingLocked() == true
    }

    fun isMovementOrAnimationLocked(): Boolean {
        return isMovementLocked() || isAnimationLocked()
    }

    private fun makeStandardContext(): ActorContext {
        val effectContext = AttackContext(appearanceState = state.appearanceState, appearanceCurrentDisplayState = displayAppearanceState)
        return ActorContext(id, attackContexts = AttackContexts.single(id, effectContext))
    }

    fun getScale(): Float {
        val footInfo = actorModel?.getFootInfoDefinition()
        val baseInfoScale = if (state.isStaticNpc() && !state.isSitChair()) { footInfo?.staticNpcScale } else { footInfo?.scale }

        val infoScale = if (baseInfoScale != null) { baseInfoScale / 100f } else { 1f }
        return state.scale * infoScale
    }

    private fun updateAppearanceState(actorState: ActorState) {
        val appearanceState = actorState.appearanceState
        if (displayAppearanceState == appearanceState) { return }

        val context = makeStandardContext()
        val routineId = getInitRoutine()

        displayAppearanceState = appearanceState
        enqueueModelRoutine(routineId, context)
    }

    private fun getInitRoutine(): DatId {
        return when (state.appearanceState) {
            0 -> DatId("init")
            else -> DatId("ini${state.appearanceState}")
        }
    }

    private fun getPopRoutine(): DatId? {
        val statePopId = DatId("pop${state.appearanceState}")
        return if (findLocalAnimationRoutine(statePopId) != null) { statePopId } else { null }
    }

    private fun initDisplay() {
        if (state.monsterId != null) {
            val monsterDefinition = MonsterDefinitions[state.monsterId]
            val effectScale = monsterDefinition.customModelSettings.effectScale
            if (effectScale != null) { effectOverride.scaleMultiplier = Vector3f(effectScale, effectScale, effectScale) }
            onReadyToDraw { actorModel?.customModelSettings = monsterDefinition.customModelSettings }
        }

        // TODO - try to standardize static/standard
        if (state.getNpcInfo() != null) {
            initNpcDisplay(state)
        } else {
            initStandardDisplay()
        }

        if (state.type == ActorType.StaticNpc) {
            onReadyToDraw { loopRoutine(DatId("@scd")) }
        }

    }

    private fun initNpcDisplay(actorState: ActorState) {
        val npcInfo = actorState.getNpcInfo() ?: return
        val npcLook = npcInfo.look

        if (npcLook.type == 0 || npcLook.type == 6) {
            onReadyToDraw {
                transitionToIdle(0f)
                val spawnAnimations = npcInfo.spawnAnimations ?: listOf(DatId("efon"), DatId.pop)
                spawnAnimations.forEach { anim -> it.playRoutine(anim) }
            }
        } else if (npcLook.type == 1) {
            onReadyToDraw { playRoutine(DatId.pop) }
        } else if (npcLook.type == 2) {
            onReadyToDraw {
                playRoutine(DatId.closed)
                playRoutine(DatId("inte"))
            }
        } else if (npcLook.type == 3 || npcLook.type == 4) {
            // No-op
        } else {
            warn("[${actorState.name}] Unknown NPC type: ${npcLook.type}")
        }
    }

    private fun initStandardDisplay() {
        val shouldPop = state.age <= Fps.toFrames(1.seconds) // Arbitrarily selected duration
        onReadyToDraw { initStandardDisplayInternal(shouldPop) }
    }

    private fun initStandardDisplayInternal(shouldPop: Boolean) {
        if (state.isDead()) {
            enqueueModelRoutineIfReady(DatId("corp"))
            return
        }

        if (shouldPop && state.animationSettings.popRoutines != null) {
            renderState.effectColor = ByteColor.zero
            state.animationSettings.popRoutines.forEach { enqueueModelRoutineIfReady(it) }
            return
        }

        val popRoutine = getPopRoutine()
        if (shouldPop && popRoutine != null) {
            renderState.effectColor = ByteColor.zero
            enqueueModelRoutineIfReady(popRoutine)
            if (displayLook.type != 0x06) { return } // [init] is needed for trolls...
        }

        enqueueModelRoutineIfReady(getInitRoutine())
        onReadyToDraw { transitionToIdle(0f) }
    }

    private fun getAnimationModeVariant(defaultId: DatId, mode: Int?, nameFn: (Int) -> DatId): DatId {
        return if (mode == null || mode == 0) { defaultId } else { nameFn.invoke(mode) }
    }

    private fun onGatheringAttempt(type: GatheringType) {
        displayGathering = true

        when (type) {
            GatheringType.Harvesting -> playEmote(7, 0)
            GatheringType.Logging -> playEmote(5, 0)
            GatheringType.Mining -> playEmote(6, 0)
        }
    }

    private fun syncFromParticle() {
        val particle = (state.dependentType as? ActorParticle)?.particle ?: return
        val baseAssociation = particle.association as? ActorAssociation ?: return

        displayPosition.copyFrom(particle.getWorldSpacePosition())

        displayFacingDir = baseAssociation.actor.displayFacingDir + particle.rotation.y
        turnAmount = 0f

        state.scale = particle.scale.x

        renderState.effectColor = ByteColor(particle.getColor())
    }

}
