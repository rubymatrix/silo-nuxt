package xim.poc.game.configuration.v0.mining

import xim.poc.game.ActorState
import xim.poc.game.ActorStateComponent
import xim.poc.game.ComponentUpdateResult

class GatheringNodeStateComponent(val gatheringConfiguration: GatheringConfiguration): ActorStateComponent {
    override fun update(actorState: ActorState, elapsedFrames: Float): ComponentUpdateResult {
        return ComponentUpdateResult(removeComponent = false)
    }
}

fun ActorState.getGatheringConfiguration(): GatheringConfiguration {
    return getComponentAs(GatheringNodeStateComponent::class)?.gatheringConfiguration ?:
        throw IllegalStateException("Gathering configuration was not set on $name")
}

fun ActorState.isGatheringNode(): Boolean {
    return getComponentAs(GatheringNodeStateComponent::class) != null
}