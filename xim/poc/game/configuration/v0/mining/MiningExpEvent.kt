package xim.poc.game.configuration.v0.mining

import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.MiscEffects
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.GameV0SaveStateHelper
import xim.poc.game.configuration.v0.ItemDefinitions
import xim.poc.game.event.Event
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import kotlin.math.roundToInt

class MiningExpEvent(
    val itemId: Int,
    val context: AttackContext,
    val expMultiplier: Float,
): Event {

    override fun apply(): List<Event> {
        val miningState = GameV0SaveStateHelper.getState().mining
        val itemDefinition = ItemDefinitions.definitionsById[itemId] ?: return emptyList()

        val levelDelta = itemDefinition.internalLevel - miningState.level
        val levelDeltaMultiplier = ((4f + levelDelta) / 4f).coerceAtLeast(0f)

        val expGained = (10 * levelDeltaMultiplier * expMultiplier).roundToInt()

        if (expGained <= 0) { return emptyList() }

        miningState.currentExp += expGained

        AttackContext.compose(context) {
            ChatLog.addLine("Gained $expGained mining EXP.", ChatLogColor.Info)
        }


        while (true) {
            val expNext = GameV0.getExperiencePointsNeeded(miningState.level)
            if (miningState.currentExp < expNext) { break }

            miningState.level += 1
            miningState.currentExp -= expNext

            AttackContext.compose(context) {
                MiscEffects.playEffect(ActorStateManager.playerId, MiscEffects.Effect.MasterLevelUp)
                ChatLog.addLine("Attained mining level ${miningState.level}!", ChatLogColor.Info)
            }
        }

        return emptyList()
    }

}