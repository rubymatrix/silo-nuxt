package xim.poc.game.configuration.v0.synthesis

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.ActorSynthesisAttempt
import xim.poc.game.AttackContext
import xim.poc.game.event.Event

class SynthesisStartEvent(
    val sourceId: ActorId,
    val recipeId: RecipeId,
    val quantity: Int,
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()
        val recipe = V0SynthesisRecipes[recipeId] ?: return emptyList()

        val context = AttackContext()

        val synthesisAction = ActorSynthesisAttempt(type = recipe.getSynthesisType(), context = context)
        if (!actorState.initiateAction(synthesisAction)) { return emptyList() }

        val synthesisState = ActorSynthesisComponent(recipeId = recipeId, quantity = quantity, attempt = synthesisAction)
        actorState.components[ActorSynthesisComponent::class] = synthesisState

        return emptyList()
    }

}