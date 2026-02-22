package xim.poc.game.configuration.assetviewer

import xim.poc.ActorId
import xim.resource.DatId

interface NpcTask {
    fun update(elapsedFrames: Float)
    fun isComplete(): Boolean
}

object NpcInteractions {

    private val interactions = mapOf(
        // Elevator Controllers
        ActorId(0x108d0f7) to ElevatorSwitch(DatId("@3x0")), // Fort Ghelsba
        ActorId(0x108d0f8) to ElevatorSwitch(DatId("@3x0")), // Fort Ghelsba

        ActorId(0x108f18e) to ElevatorSwitch(DatId("@3z0")), // Palborough
        ActorId(0x108f18f) to ElevatorSwitch(DatId("@3z0")), // Palborough

        ActorId(0x10951e2) to ElevatorSwitch(DatId("@450")), // Davoi
        ActorId(0x10951e3) to ElevatorSwitch(DatId("@450")), // Davoi

        // Ship Controllers
        ActorId(0x1002151) to BasicShipController(ActorId(0x1002154), DatId("seq0") to DatId("seq1")), // Carpenter's Landing
        ActorId(0x1002152) to BasicShipController(ActorId(0x1002154), DatId("seq2") to DatId("seq3")), // Carpenter's Landing
        ActorId(0x1002153) to BasicShipController(ActorId(0x1002154), DatId("seq4") to DatId("seq5")), // Carpenter's Landing

        ActorId(0x100417e) to BasicShipController(ActorId(0x1004180), DatId("seq0") to DatId("seq1")), // Bibiki Bay
        ActorId(0x100417f) to BasicShipController(ActorId(0x1004180), DatId("seq2") to DatId("seq3")), // Bibiki Bay

        ActorId(0x10e806c) to BasicShipController(ActorId(0x10e806e)), // Port Sandy
        ActorId(0x10ec017) to BasicShipController(ActorId(0x10ec063)), // Port Bastok
        ActorId(0x10f0048) to BasicShipController(ActorId(0x10f00a4)), // Port Windurst

        ActorId(0x10f6013) to BasicShipController(ActorId(0x10f6068), DatId("seq0") to DatId("seq1")), // Jeuno->Sandy airship controller
        ActorId(0x10f601d) to BasicShipController(ActorId(0x10f6068), DatId("seq2") to DatId("seq3")), // Jeuno->Bastok airship controller
        ActorId(0x10f6027) to BasicShipController(ActorId(0x10f6068), DatId("seq4") to DatId("seq5")), // Jeuno->Windurst airship controller
        ActorId(0x10f6033) to BasicShipController(ActorId(0x10f6068), DatId("seq6") to DatId("seq7")), // Jeuno->Kazham airship controller

        ActorId(0x10f803c) to BasicShipController(ActorId(0x10f8040)), // Selbina
        ActorId(0x10f903a) to BasicShipController(ActorId(0x10f903e)), // Mhaura
        ActorId(0x10fa022) to BasicShipController(ActorId(0x10fa068)), // Kazham
        ActorId(0x10fc025) to BasicShipController(ActorId(0x10fc039)), // Norg
    )

    fun interact(npcId: ActorId): Boolean {
        val interaction = interactions[npcId] ?: return false
        interaction.onInteraction(npcId)
        return true
    }

}

