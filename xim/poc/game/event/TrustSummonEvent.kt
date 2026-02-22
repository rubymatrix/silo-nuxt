package xim.poc.game.event

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ModelLook
import xim.poc.TrustController
import xim.poc.game.ActorStateManager
import xim.poc.game.ActorTrust
import xim.poc.game.ActorType
import xim.poc.game.PartyManager
import xim.poc.game.configuration.BehaviorId
import xim.poc.game.configuration.TrustBehaviorId
import xim.resource.DatId
import xim.resource.table.SpellInfoTable
import xim.resource.table.SpellNameTable
import xim.resource.table.TrustTable

class TrustSummonEvent(
    val sourceId: ActorId,
    val spellId: Int,
    val behaviorId: BehaviorId = TrustBehaviorId(spellId),
): Event {

    override fun apply(): List<Event> {
        val actorState = ActorStateManager[sourceId] ?: return emptyList()

        val party = PartyManager[actorState.id]
        if (party.size() >= 6) { return emptyList() }

        val spellInfo = SpellInfoTable[spellId]
        val name = SpellNameTable[spellInfo.index]?.first() ?: "???"

        val position = Vector3f().copyFrom(actorState.position)
        position += Matrix4f().rotateYInPlace(actorState.rotation).transformInPlace(Vector3f(2f, 0f, 0f))

        val lookId = TrustTable.getModelId(spellInfo)
        val npcLook = ModelLook.npc(lookId)

        val trustActorId = ActorStateManager.nextId()
        party.addMember(trustActorId)

        val initialActorState = InitialActorState(
            presetId = trustActorId,
            name = name,
            type = ActorType.AllyNpc,
            position = position,
            modelLook = npcLook,
            movementController = TrustController(),
            behaviorController = behaviorId,
            dependentSettings = DependentSettings(actorState.id, ActorTrust),
            popRoutines = listOf(DatId.spop, DatId.init)
        )

        return listOf(ActorCreateEvent(initialActorState))
    }

}