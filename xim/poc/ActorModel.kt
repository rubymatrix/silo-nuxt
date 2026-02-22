package xim.poc

import xim.math.Vector3f
import xim.poc.camera.CameraReference
import xim.poc.gl.MeshBuffer
import xim.resource.*

enum class FootState {
    Grounded,
    Lifting,
    Ungrounded,
    Landing,
    ;

    fun next(isTouching: Boolean): FootState {
        return when (this) {
            Grounded -> if (isTouching) { Grounded } else { Lifting }
            Lifting -> if (isTouching) { Grounded } else { Ungrounded }
            Landing -> if (isTouching) { Grounded } else { Ungrounded }
            Ungrounded -> if (isTouching) { Landing } else { Ungrounded }
        }
    }
}

class CustomModelSettings(
    var blurConfig: BlurConfig? = null,
    var scale: Float = 1f,
    var effectScale: Float? = null,
    var hideMain: Boolean = false,
)

data class SlotVisibilityOverride(val slot: Int, val hidden: Boolean, val ifEngaged: Boolean)

class ModelSlotVisibilityState {

    private val visibilityState = HashMap<Int, SlotVisibilityOverride>()

    fun apply(slotVisibilityOverride: SlotVisibilityOverride) {
        visibilityState[slotVisibilityOverride.slot] = slotVisibilityOverride
    }

    fun getOverrides(): List<SlotVisibilityOverride> {
        return visibilityState.values.toList()
    }

}

class ModelLock(var duration: Float, val validityCheck: () -> Boolean = { true })

class ActorModel(val actor: Actor, val model: Model) {

    private var skeletonInstance: SkeletonInstance? = null
    val skeletonAnimationCoordinator = SkeletonAnimationCoordinator()
    val skeletonAnimationThrottler = SkeletonAnimationThrottler(actor)

    private val defaultVisibilityStates = ModelSlotVisibilityState()
    private val hiddenModelSlotIds = HashSet<Int>()

    private var leftFootState: FootState = FootState.Grounded
    private var rightFootState: FootState = FootState.Grounded

    private var animationLocks = ArrayList<ModelLock>()
    private var movementLocks = ArrayList<ModelLock>()
    private var facingLocks = ArrayList<ModelLock>()

    private var displayRangedDuration = 0f

    var customModelSettings = CustomModelSettings()

    var idleAnimationMode = 0
    var battleAnimationMode = 0
    var walkingAnimationMode = 0
    var runningAnimationMode = 0

    fun update(elapsedFrames: Float) {
        if (skeletonInstance == null) {
            val resource = model.getSkeletonResource() ?: return
            skeletonInstance = SkeletonInstance(resource)
        }

        skeletonAnimationCoordinator.update(elapsedFrames)
        updateLocks(elapsedFrames, movementLocks)
        updateLocks(elapsedFrames, animationLocks)
        updateLocks(elapsedFrames, facingLocks)
        displayRangedDuration -= elapsedFrames

        leftFootState = leftFootState.next(skeletonInstance!!.isLeftFootTouchingGround())
        rightFootState = rightFootState.next(skeletonInstance!!.isRightFootTouchingGround())
    }

    private fun updateLocks(elapsedFrames: Float, locks: ArrayList<ModelLock>) {
        locks.forEach { it.duration -= elapsedFrames }
        locks.removeAll { it.duration <= 0f || !it.validityCheck.invoke() }
    }

    fun getMeshes(): List<MeshBuffer> {
        val directories = model.getMeshResources()
        val resources = directories.flatMap { it.collectByTypeRecursive(SkeletonMeshResource::class) }.toMutableList()

        val hiddenIds = getHiddenSlotIds().map { DatId("wep$it") }
        resources.removeAll { hiddenIds.contains(it.id) }

        val occluded = resources.map { it.occlusionType }.toSet()
        return resources.flatMap { it.meshes }.filter { !isOccluded(it, occluded) }
    }

    fun getHiddenModelSlots(): Set<ItemModelSlot> {
        return getHiddenSlotIds().mapNotNull { when(it) {
            0 -> ItemModelSlot.Main
            1 -> ItemModelSlot.Sub
            2 -> ItemModelSlot.Range
            else -> null
        } }.toSet()
    }

    fun lockAnimation(duration: Float) {
        lockAnimation(ModelLock(duration))
    }

    fun lockAnimation(modelLock: ModelLock) {
        animationLocks += modelLock
    }

    fun isAnimationLocked(): Boolean {
        return animationLocks.isNotEmpty()
    }

    fun lockMovement(duration: Float) {
        lockMovement(ModelLock(duration))
    }

    fun lockMovement(modelLock: ModelLock) {
        movementLocks += modelLock
    }

    fun isMovementLocked() : Boolean {
        return movementLocks.isNotEmpty()
    }

    fun lockFacing(duration: Float) {
        lockFacing(ModelLock(duration))
    }

    fun lockFacing(modelLock: ModelLock) {
        facingLocks += modelLock
    }

    fun isFacingLocked() : Boolean {
        return facingLocks.isNotEmpty()
    }

    fun clearAnimations() {
        skeletonAnimationCoordinator.clear()
    }

    fun getSkeleton(): SkeletonInstance? {
        return skeletonInstance
    }

    fun setSkeletonAnimation(datId: DatId, animationDirs: List<DirectoryResource>, loopParams: LoopParams = LoopParams.lowPriorityLoop(), transitionParams: TransitionParams, modelSlotVisibilityState: ModelSlotVisibilityState? = null) {
        val resources = fetchAnimations(datId, animationDirs)

        if (transitionParams.inBetween != null) {
            transitionParams.resolvedInBetween = fetchAnimations(transitionParams.inBetween, animationDirs).associateBy { it.id.finalDigit() ?: 0 }
        }

        skeletonAnimationCoordinator.registerAnimation(resources, loopParams, transitionParams, modelSlotVisibilityState)
    }

    fun transitionToMoving(datId: DatId, animationDirs: List<DirectoryResource>, transitionParams: TransitionParams) {
        setSkeletonAnimation(datId, animationDirs, LoopParams.lowPriorityLoop(), transitionParams)
    }

    fun transitionToIdleOnStopMoving(datId: DatId, animationDirs: List<DirectoryResource>) {
        val idleResources = fetchAnimations(datId, animationDirs)
        skeletonAnimationCoordinator.registerIdleAnimation(idleResources, requireTransitionOut = false)
    }

    fun transitionToIdleOnCompleted(idleId: DatId, animationDirs: List<DirectoryResource>) {
        val alreadyIdle = skeletonAnimationCoordinator.animations.filterNotNull()
            .mapNotNull { it.currentAnimation?.animation?.id }
            .all { it.parameterizedMatch(idleId) }

        if (alreadyIdle) { return }

        if (!skeletonAnimationCoordinator.hasCompleteTransitionOutAnimations()) { return }

        val idleResources = fetchAnimations(idleId, animationDirs)
        skeletonAnimationCoordinator.registerIdleAnimation(idleResources, requireTransitionOut = true)
    }

    fun forceTransitionToIdle(idleId: DatId, transitionTime: Float, animationDirs: List<DirectoryResource>) {
        displayRangedDuration = 0f
        val idleResources = fetchAnimations(idleId, animationDirs)
        skeletonAnimationCoordinator.registerAnimation(idleResources,
            LoopParams.lowPriorityLoop(),
            TransitionParams(transitionInTime = transitionTime, transitionOutTime = 0f)
        )
    }

    private fun fetchAnimations(datId: DatId, animationDirs: List<DirectoryResource>): List<SkeletonAnimationResource> {
        return animationDirs.map { it.root() }
            .flatMap { it.collectByTypeRecursive(SkeletonAnimationResource::class) }
            .filter { it.id.parameterizedMatch(datId) }
            .distinctBy { it.id }
    }

    fun getJointPosition(standardPosition: Int): Vector3f? {
        val skeleton = getSkeleton() ?: return null
        return skeleton.getStandardJointPosition(standardPosition)
    }

    fun getFootInfoDefinition() : InfoDefinition? {
        return model.getMovementInfo()
    }

    fun getCollidingFoot(): StandardPosition? {
        return if (leftFootState == FootState.Landing) {
            StandardPosition.LeftFoot
        } else if (rightFootState == FootState.Landing) {
            StandardPosition.RightFoot
        } else {
            null
        }
    }

    fun displayRanged(duration: Float) {
        if (hiddenModelSlotIds.contains(2)) { return }
        displayRangedDuration = duration
    }

    fun getBlurConfig(): BlurConfig? {
        return customModelSettings.blurConfig ?: model.getBlurConfig()
    }

    fun setDefaultModelVisibility(slotVisibilityOverride: SlotVisibilityOverride) {
        defaultVisibilityStates.apply(slotVisibilityOverride)
    }

    fun toggleModelVisibility(slot: Int, hidden: Boolean) {
        if (hidden) { hiddenModelSlotIds += slot } else { hiddenModelSlotIds -= slot }
        if (slot == 2) { displayRangedDuration = 0f }
    }

    private fun isOccluded(meshBuffer: MeshBuffer, occlusion: Set<Int>): Boolean {
        val renderProperties = meshBuffer.skeletalMeshProperties ?: return false

        // TODO - do occlusions 0x01, 0x11, 0x21, and 0x31 do anything?
        return when (renderProperties.displayTypeFlag) {
            0 -> false
            /* hair 1*/ 1 -> occlusion.contains(0x02) || occlusion.contains(0x03) || occlusion.contains(0x04) || occlusion.contains(0x05) || occlusion.contains(0x06)
            /* hair 2*/ 2 -> occlusion.contains(0x04) || occlusion.contains(0x05) || occlusion.contains(0x06)
            /* hair 3*/ 3 -> occlusion.contains(0x04) || occlusion.contains(0x05) || occlusion.contains(0x06)
            /* face  */ 4 -> occlusion.contains(0x05)
            /* wrist */ 5 -> occlusion.contains(0x12)
            /* pants */ 6 -> occlusion.contains(0x32)
            /* shins */ 7 -> occlusion.contains(0x22)
            else -> throw IllegalStateException("Unknown display type: ${renderProperties.displayTypeFlag}")
        }

    }

    private fun getHiddenSlotIds(): Set<Int> {
        if (actor.getMount() != null) { return setOf(0, 1, 2) }

        // Precedence is: ActorModel visibility > Display-Ranged > Effect Visibility.
        // Ranged is hidden by default.
        val hiddenSlots = mutableSetOf(2)

        val engaged = actor.isDisplayEngagedOrEngaging()

        val overrideStates = defaultVisibilityStates.getOverrides() +
                skeletonAnimationCoordinator.getSlotVisibilityOverrides().flatMap { it.getOverrides() }

        for (override in overrideStates)  {
            if (override.ifEngaged && !engaged) { continue }
            if (override.hidden) {
                hiddenSlots += override.slot
            } else {
                hiddenSlots -= override.slot
            }
        }

        hiddenSlots.addAll(hiddenModelSlotIds)

        if (!hiddenSlots.contains(2) && displayRangedDuration > 0f) { return setOf(0, 1) }

        return hiddenSlots
    }

    fun getMainAttackIds(appearanceState: Int): List<DatId> {
        val dir = model.getMainBattleAnimationDirectory() ?: return emptyList()

        if (appearanceState != 0) {
            val ids = collectIdsByPrefix(dir, "a${appearanceState}i")
            if (ids.isNotEmpty()) { return ids }
        }

        return collectIdsByPrefix(dir, "ati")
    }

    fun getSubAttackIds(appearanceState: Int): List<DatId> {
        val dir = model.getSubBattleAnimationDirectory() ?: return emptyList()

        if (appearanceState != 0) {
            val ids = collectIdsByPrefix(dir, "b${appearanceState}i")
            if (ids.isNotEmpty()) { return ids }
        }

        return collectIdsByPrefix(dir, "bti")
    }

    private fun collectIdsByPrefix(dir: DirectoryResource, prefix: String): List<DatId> {
        return dir.collectByTypeRecursive(EffectRoutineResource::class)
            .map { it.id }
            .filter { it.id.startsWith(prefix) }
    }

}
