package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.skillDoubleUp_635
import xim.poc.ui.ChatLog
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class CorsairRollEvent(
    val abilityId: SkillId,
    val status: StatusEffect,
    val sourceId: ActorId,
    val context: AttackContext,
): Event {

    override fun apply(): List<Event> {
        val sourceState = ActorStateManager[sourceId] ?: return emptyList()

        if (abilityId == skillDoubleUp_635) {
            doubleUp(sourceState)
        } else {
            roll(sourceState)
        }

        return emptyList()
    }

    private fun roll(sourceState: ActorState) {
        if (sourceState.hasStatusEffect(StatusEffect.DoubleUpChance)) { return }

        sourceState.gainStatusEffect(status)

        val doubleUpChance = sourceState.gainStatusEffect(StatusEffect.DoubleUpChance)
        doubleUpChance.linkedSkillId = abilityId
        doubleUpChance.linkedStatus = status

        doubleUp(sourceState)
    }

    private fun doubleUp(sourceState: ActorState) {
        val doubleUpStatus = sourceState.getStatusEffect(StatusEffect.DoubleUpChance) ?: return
        val linkedStatus = sourceState.getStatusEffect(doubleUpStatus.linkedStatus) ?: return

        var rollValue = 1 + Random.nextInt(0, 6)

        // For testing, always hit 11 before busting
        if (GameState.isDebugMode() && linkedStatus.counter < 11 && linkedStatus.counter + rollValue > 11) {
            rollValue = 11 - linkedStatus.counter
        }

        val rollSum = (linkedStatus.counter + rollValue).coerceIn(1, 12)
        linkedStatus.counter = rollSum

        if (rollSum < 12) {
            AttackContext.compose(context) { ChatLog.addLine("Roll Value: $rollSum") }
        } else {
            sourceState.expireStatusEffect(StatusEffect.DoubleUpChance)
            sourceState.expireStatusEffect(doubleUpStatus.linkedStatus)
            sourceState.gainStatusEffect(StatusEffect.Bust, 30.seconds)
            AttackContext.compose(context) { ChatLog.addLine("Roll Value: bust!") }
        }

        context.rollSumFlag = rollSum
        context.effectArg = rollValue
    }

}