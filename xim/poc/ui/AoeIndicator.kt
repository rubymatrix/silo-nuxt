package xim.poc.ui

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.configuration.NoActionBehaviorId
import xim.poc.game.configuration.SkillRangeInfo
import xim.poc.game.configuration.SkillTargetEvaluator
import xim.poc.game.event.InitialActorState
import xim.poc.gl.Color
import xim.resource.AoeType
import xim.resource.DatId
import xim.resource.Particle

enum class AoeIndicatorColor {
    Normal,
    Caution,
    Danger,
}

private class ClaimedIndicator(val indicator: AoeIndicator, val completionFn: () -> Boolean)

object AoeIndicators {

    private val indicators = ArrayList<AoeIndicator>(8)
    private val claimed = ArrayList<ClaimedIndicator>()

    init {
        for (i in 0 until 8) { indicators += AoeIndicator() }
    }

    fun update() {
        val itr = claimed.iterator()
        while (itr.hasNext()) {
            val it = itr.next()
            it.indicator.update()

            if (!it.completionFn.invoke()) { continue }

            it.indicator.hide()
            indicators += it.indicator
            itr.remove()
        }
    }

    fun claim(sourceId: ActorId, targetId: ActorId, aoeSettings: SkillRangeInfo, colorMode: AoeIndicatorColor, castingState: CastingState? = null, completionFn: () -> Boolean) {
        if (aoeSettings.type == AoeType.None) { return }
        val indicator = indicators.removeLastOrNull() ?: return
        indicator.configure(sourceId, targetId, aoeSettings, castingState, colorMode)
        indicator.show()
        claimed += ClaimedIndicator(indicator, completionFn)
    }

}

class AoeIndicator {

    private var indicatorReference: ActorPromise? = null

    private var shouldShow = false
    private var displayEnabled = false

    private var sourceId: ActorId? = null
    private var targetId: ActorId? = null
    private var castingState: CastingState? = null

    private var aoeSettings = SkillRangeInfo(0f, 0f, type = AoeType.None)
    private val scale = Vector3f(10f, 1f, 10f)
    private var colorMode = AoeIndicatorColor.Normal

    fun update() {
        if (indicatorReference.isNullOrObsolete()) { return makeActor() }
        val indicator = ActorStateManager[indicatorReference?.getIfReady()] ?: return

        val sourceState = ActorStateManager[sourceId] ?: return hide()
        val targetState = ActorStateManager[targetId] ?: return hide()

        indicator.rotation = computeRotation(sourceState)
        indicator.position.copyFrom(computePosition(sourceState, targetState))
        indicator.position.y -= 0.01f

        ActorManager[indicator.id]?.syncFromState()
        if (shouldShow) { enableDisplay() } else { disableDisplay() }
    }

    fun configure(sourceId: ActorId, targetId: ActorId, aoeSettings: SkillRangeInfo, castingState: CastingState? = null, colorMode: AoeIndicatorColor = AoeIndicatorColor.Normal) {
        this.sourceId = sourceId
        this.targetId = targetId
        this.colorMode = colorMode
        this.castingState = castingState

        val previousSettings = this.aoeSettings
        this.aoeSettings = aoeSettings

        scale.x = aoeSettings.effectRadius
        scale.z = aoeSettings.effectRadius

        val sourceTargetSize = ActorStateManager[sourceId]?.targetingSize
        if (sourceTargetSize != null && sourceCentered()) {
            scale.x += sourceTargetSize
            scale.z += sourceTargetSize
        }

        if (previousSettings.type != aoeSettings.type) { resetDisplay() }
    }

    fun hide() {
        shouldShow = false
        disableDisplay()
    }

    fun show() {
        shouldShow = true
        enableDisplay()
    }

    private fun makeActor() {
        indicatorReference = GameEngine.submitCreateActorState(
            InitialActorState(
                name = "",
                type = ActorType.Effect,
                position = Vector3f(),
                modelLook = ModelLook.npc(0x990),
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
                popRoutines = emptyList(),
            )
        )
    }

    private fun sourceCentered(): Boolean {
        return aoeSettings.type.isSourceCentered()
    }

    private fun colorFn(particle: Particle, input: Color): Color {
        val colorModeOverride = if (colorMode == AoeIndicatorColor.Danger) {
            if (isPlayerInEffectRange()) { AoeIndicatorColor.Danger } else { AoeIndicatorColor.Caution }
        } else {
            colorMode
        }

        return when (colorModeOverride) {
            AoeIndicatorColor.Normal -> input
            AoeIndicatorColor.Caution -> Color(input.b(), input.b(), input.r(), input.a() * 2f)
            AoeIndicatorColor.Danger -> Color(input.b(), input.g(), input.r(), input.a() * 2f)
        }
    }

    private fun computeRotation(sourceState: ActorState): Float {
        val skillRotation = aoeSettings.fixedRotation ?: 0f
        return sourceState.rotation - skillRotation
    }

    private fun computePosition(sourceState: ActorState, targetState: ActorState): Vector3f {
        val effectPosition = if (sourceCentered()) {
            sourceState.position
        } else {
            castingState?.context?.targetAoeCenter ?: targetState.position
        }

        return if (sourceState.isPlayer()) {
            effectPosition
        } else {
            Vector3f(effectPosition).withY(ActorStateManager.player().position.y)
        }
    }

    private fun isPlayerInEffectRange(): Boolean {
        val source = ActorStateManager[sourceId] ?: return false
        val target = ActorStateManager[targetId] ?: return false

        return SkillTargetEvaluator.isInEffectRange(
            rangeInfo = aoeSettings,
            source = source,
            primaryTarget = target,
            additionalTarget = ActorStateManager.player(),
        )
    }

    private fun resetDisplay() {
        disableDisplay()
        enableDisplay()
    }

    private fun enableDisplay() {
        if (displayEnabled) { return }

        val currentActor = ActorManager[indicatorReference?.getIfReady()] ?: return
        currentActor.effectOverride.scaleMultiplier = scale
        currentActor.effectOverride.colorOverride = this::colorFn
        currentActor.onReadyToDraw { it.enqueueModelRoutine(routineId()) }

        displayEnabled = true
    }

    private fun disableDisplay() {
        if (!displayEnabled) { return }

        val currentActor = ActorManager[indicatorReference?.getIfReady()] ?: return
        currentActor.onReadyToDraw { it.enqueueClearEffectsRoutine() }

        displayEnabled = false
    }

    private fun routineId(): DatId {
        return if (aoeSettings.type == AoeType.Cone) { DatId("con1") } else { DatId("con0") }
    }

}