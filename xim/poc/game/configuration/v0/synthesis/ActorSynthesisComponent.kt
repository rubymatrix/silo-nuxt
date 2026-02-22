package xim.poc.game.configuration.v0.synthesis

import xim.poc.game.*
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.event.Event
import xim.util.Fps

class SynthesisResult(
    val type: SynthesisResultType,
    val item: InventoryItem? = null,
)

class ActorSynthesisComponent(
    val recipeId: RecipeId,
    val quantity: Int,
    val attempt: ActorSynthesisAttempt,
): ActorStateComponent {

    companion object {
        private val noInterruptDuration = Fps.secondsToFrames(3)
        private val craftingDuration = Fps.secondsToFrames(10)
    }

    private var craftingTime = 0f

    var interrupted = false
        private set

    var result: SynthesisResultType? = null
        private set

    override fun update(actorState: ActorState, elapsedFrames: Float): ComponentUpdateResult {
        craftingTime += elapsedFrames

        return if (isReadyForResult() && result == null) {
            ComponentUpdateResult(
                events = listOf(SynthesisCompleteEvent(actorState.id)),
                removeComponent = false,
            )
        } else if (isComplete()) {
            attempt.complete = true
            actorState.clearActionState(attempt)
            ComponentUpdateResult(removeComponent = true)
        } else {
            ComponentUpdateResult(removeComponent = false)
        }
    }

    fun interrupt() {
        if (canInterrupt()) { interrupted = true }
    }

    fun setResult(result: SynthesisResultType) {
        if (this.result != null) { return }

        this.result = result
        attempt.result = result

        craftingTime = craftingDuration.coerceAtLeast(craftingDuration)
    }

    fun isComplete(): Boolean {
        val totalDuration = getTotalDuration() ?: return false
        return craftingTime >= totalDuration
    }

    private fun getTotalDuration(): Float? {
        val result = result ?: return null

        return when (result) {
            SynthesisResultType.Break -> Fps.secondsToFrames(12)
            SynthesisResultType.NormalQuality -> Fps.secondsToFrames(15)
            SynthesisResultType.HighQuality -> Fps.secondsToFrames(15)
        }
    }

    private fun isReadyForResult(): Boolean {
        return interrupted || craftingTime >= craftingDuration
    }

    private fun canInterrupt(): Boolean {
        return this.result == null && craftingTime >= noInterruptDuration
    }

}

