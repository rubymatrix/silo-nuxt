package xim.poc.game.configuration

import xim.resource.DatId
import xim.resource.DirectoryResource
import xim.resource.table.ZoneNpcList
import xim.resource.table.ZoneSettings

data class CustomZoneDefinition(
    val zoneId: Int,
    val zoneMapId: Int? = null,
    val displayName: String? = null,
    val zoneResourcePath: String? = null,
    val zoneSettings: ZoneSettings? = null,
    val staticNpcList: ZoneNpcList? = null,
    val customWeather: Map<DatId, Map<DatId, DirectoryResource>> = emptyMap(),
)

object ZoneDefinitionManager {

    private val zones = HashMap<Int, CustomZoneDefinition>()

    init {
        zones.forEach { validateDefinition(it.value) }
    }

    operator fun plusAssign(customZoneDefinition: CustomZoneDefinition) {
        zones[customZoneDefinition.zoneId] = customZoneDefinition
    }

    operator fun get(zoneId: Int): CustomZoneDefinition? {
        return zones[zoneId]
    }

    private fun validateDefinition(definition: CustomZoneDefinition) {
        if (definition.staticNpcList != null) {
            val uniqueIds = definition.staticNpcList.npcs.map { it.id }.toSet()
            if (uniqueIds.size != definition.staticNpcList.npcs.size) { throw IllegalStateException("[${definition.zoneId}][${definition.displayName}] Npcs don't have unique IDs") }
        }
    }

}
