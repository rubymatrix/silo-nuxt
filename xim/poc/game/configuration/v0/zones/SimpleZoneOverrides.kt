package xim.poc.game.configuration.v0.zones

import xim.poc.game.configuration.CustomZoneDefinition
import xim.resource.table.Npc
import xim.resource.table.NpcTable
import xim.resource.table.ZoneNpcList
import xim.resource.table.ZoneSettingsTable

object SimpleZoneOverrides {

    val definitions = makeOverrides()

    private fun makeOverrides(): List<CustomZoneDefinition> {
        return ZoneSettingsTable.getZoneIds().map { makeOverrides(it) }
    }

    private fun makeOverrides(zoneId: Int): CustomZoneDefinition {
        return  CustomZoneDefinition(zoneId = zoneId, staticNpcList = makeNpcOverride(zoneId = zoneId))
    }

    private fun makeNpcOverride(zoneId: Int): ZoneNpcList {
        val defaultNpcs = NpcTable.getNpcInfoByZone(zoneId).values

        val includeNpcs = ArrayList<Npc>()

        for (defaultNpc in defaultNpcs) {
            val datId = defaultNpc.datId ?: continue
            if (!datId.isElevatorId() && !datId.isDoorId()) { continue }
            includeNpcs += Npc(id = defaultNpc.id, name = "", info = defaultNpc.copy(status = 0))
        }

        val byDatId = includeNpcs.associateBy { it.info.datId!! }

        return ZoneNpcList(resourceId = "", npcs = includeNpcs, npcsByDatId = byDatId)
    }

}