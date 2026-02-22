package xim.poc.game.configuration.assetviewer

import xim.poc.game.ActorState
import xim.poc.game.configuration.ActorBehaviorController
import xim.poc.game.configuration.AutoAttackController
import xim.poc.game.configuration.BehaviorId
import xim.poc.game.event.Event

object PlayerBehaviorId: BehaviorId

class AssetViewerPlayerBehavior(val actorState: ActorState): ActorBehaviorController {

    private val delegate = AutoAttackController(actorState)

    private var fishingAttempt: FishingAttemptInstance? = null

    override fun update(elapsedFrames: Float): List<Event> {
        val fishingState = actorState.getFishingState()
        if (fishingState != null) {
            fishingAttempt = fishingAttempt ?: FishingAttemptInstance(actorState, fishingState)
            fishingAttempt?.update(elapsedFrames)
            return emptyList()
        } else {
            fishingAttempt = null
        }

        return delegate.update(elapsedFrames)
    }

}