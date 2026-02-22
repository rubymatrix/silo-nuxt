package xim.poc.game.event

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ModelLook
import xim.poc.game.*
import xim.resource.table.SpellInfoTable
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class BubbleGainEvent(
    val sourceId: ActorId,
    val spellId: Int,
): Event {

    override fun apply(): List<Event> {
        val source = ActorStateManager[sourceId] ?: return emptyList()
        val spellInfo = SpellInfoTable[spellId]

        source.gainStatusEffect(StatusEffect.Indicolure, 30.seconds)

        val position = Vector3f().copyFrom(source.position)

        val offenseOffset = if (Random.nextBoolean()) { 0x8 } else { 0x0 } // TODO: split Indi spells
        val lookId = 0x77C + spellInfo.element.index + offenseOffset
        val npcLook = ModelLook.npc(lookId)

        val bubbleActorId = ActorStateManager.nextId()
        source.bubble = bubbleActorId

        val initialActorState = InitialActorState(
            presetId = bubbleActorId,
            name = "(Bubble)",
            type = ActorType.Effect,
            position = position,
            modelLook = npcLook,
            dependentSettings = DependentSettings(sourceId, ActorBubble),
        )

        return listOf(ActorCreateEvent(initialActorState))
    }

}