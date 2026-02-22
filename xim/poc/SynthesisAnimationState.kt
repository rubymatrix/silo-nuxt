package xim.poc

import xim.poc.browser.DatLoader
import xim.poc.browser.DatWrapper
import xim.poc.game.ActorSynthesisAttempt
import xim.poc.game.SynthesisResultType
import xim.resource.DatId
import xim.resource.EffectRoutineResource
import xim.resource.table.FileTableManager

enum class SynthesisType(val animationOffset: Int) {
    Water(0),
    Wind(1),
    Fire(2),
    Earth(3),
    Lightning(4),
    Ice(5),
    Light(6),
    Dark(7),
    ;

    companion object {

        fun fromItemId(itemId: Int): SynthesisType {
            return when (itemId) {
                4096 -> Fire
                4097 -> Ice
                4098 -> Wind
                4099 -> Earth
                4100 -> Lightning
                4101 -> Water
                4102 -> Light
                4103 -> Dark
                else -> throw IllegalStateException("Not a crystal! $itemId")
            }
        }
    }

}

enum class SynthesisAnimationState {
    Start,
    Kneeling,
    StartCrafting,
    CraftingInProgress,
    EndCrafting,
    EndCraftingInProgress,
    CraftingComplete,
    Standing,
    Complete,
}

class SynthesisAnimationStateMachine(
    val actor: Actor,
    val attempt: ActorSynthesisAttempt,
) {

    private val resource: DatWrapper

    private var currentState = SynthesisAnimationState.Start
    private lateinit var resultType: SynthesisResultType

    init {
        val synthesisType = attempt.type
        val resourcePath = FileTableManager.getFilePath(0x1340 + synthesisType.animationOffset) ?: throw IllegalStateException("No path for crystal type: $synthesisType?")
        resource = DatLoader.load(resourcePath)
    }

    fun update() {
        when (currentState) {
            SynthesisAnimationState.Start -> handleStart()
            SynthesisAnimationState.StartCrafting -> handleStartCrafting()
            SynthesisAnimationState.CraftingInProgress -> handleCraftingInProgress()
            SynthesisAnimationState.EndCrafting -> handleEndCrafting()
            SynthesisAnimationState.CraftingComplete -> handleCraftingComplete()
            else -> { } // no-op
        }
    }

    fun isComplete(): Boolean {
        return currentState == SynthesisAnimationState.Complete && attempt.complete
    }

    private fun handleStart() {
        val options = RoutineOptions(callback = { currentState = SynthesisAnimationState.StartCrafting })
        actor.enqueueModelRoutine(DatId.startResting, options = options)
        currentState = SynthesisAnimationState.Kneeling
    }

    private fun handleStartCrafting() {
        if (!resource.isReady()) { return }

        val first = resource.getAsResource().getNullableChildRecursivelyAs(DatId("frst"), EffectRoutineResource::class) ?: throw IllegalStateException("No frst? ${resource.resourceName}")
        EffectManager.registerActorRoutine(actor, ActorContext(actor.id), first)
        currentState = SynthesisAnimationState.CraftingInProgress
    }

    private fun handleCraftingInProgress() {
        resultType = attempt.result ?: return

        val stop = resource.getAsResource().getNullableChildRecursivelyAs(DatId("stcr"), EffectRoutineResource::class) ?: throw IllegalStateException("No stop? ${resource.resourceName}")
        EffectManager.registerActorRoutine(actor, ActorContext(actor.id), stop)
        currentState = SynthesisAnimationState.EndCrafting
    }

    private fun handleEndCrafting() {
        if (!resource.isReady()) { return }

        val resultDat = mapResultToAnimation()
        val resultAnim = resource.getAsResource().getNullableChildRecursivelyAs(resultDat, EffectRoutineResource::class) ?: throw IllegalStateException("No $resultType? ${resource.resourceName}")
        val result = EffectManager.registerActorRoutine(actor, ActorContext(actor.id), resultAnim)

        result.registerAllEffectsCompletedCallback { currentState = SynthesisAnimationState.CraftingComplete }
        currentState = SynthesisAnimationState.EndCraftingInProgress
    }

    private fun handleCraftingComplete() {
        val options = RoutineOptions(callback = { currentState = SynthesisAnimationState.Complete })
        actor.enqueueModelRoutine(DatId.stopResting, options = options)
        currentState = SynthesisAnimationState.Standing
        attempt.context.invokeCallback()
    }

    private fun mapResultToAnimation(): DatId {
        return when (resultType) {
            SynthesisResultType.Break -> DatId.synthesisBreak
            SynthesisResultType.NormalQuality -> DatId.synthesisNq
            SynthesisResultType.HighQuality -> DatId.synthesisHq
        }
    }

}