package xim.poc.tools

import kotlinx.serialization.Serializable
import xim.math.Vector3f
import xim.poc.ActorManager
import xim.poc.SceneManager
import xim.poc.ScreenFader
import xim.poc.game.ActorStateManager
import xim.poc.game.GameClient
import xim.poc.game.configuration.CustomZoneDefinition
import xim.poc.game.configuration.ZoneDefinitionManager
import xim.resource.DatId
import xim.resource.ZoneInteraction
import xim.util.OnceLogger
import kotlin.time.Duration.Companion.seconds

@Serializable
enum class MogHouseConfig(val rentalIndex: Int, val homeIndex: Int? = null, val entryIds: Set<String>) {
    Jeuno(rentalIndex = 0x164, entryIds = setOf("zmrn", "zmrp", "zmrr", "zmrt")),
    Sandoria(rentalIndex = 0x165, homeIndex = 0x185, entryIds = setOf("zmr1", "zmr3", "zmr5")),
    Bastok(rentalIndex = 0x166, homeIndex = 0x186 , entryIds = setOf("zmrb", "zmrd", "zmrh", "zmrj")),
    Windurst(rentalIndex = 0x184, homeIndex = 0x187, entryIds = setOf("zmr7", "zmr9", "zmrf", "zmrl")),
    AhtUrhgan(rentalIndex = 0x13A, entryIds = setOf("zmrv", "zmrx")),
    SandoriaS(rentalIndex = 0x147E0, entryIds = setOf("zms1")),
    BastokS(rentalIndex = 0x12B, entryIds = setOf("zms3", "zms5")),
    WindurstS(rentalIndex = 0x13F, entryIds = setOf("zms7")),
    Adoulin(rentalIndex = 0x188, entryIds = setOf("zms9", "zmsb")),
}

@Serializable
enum class MogHouseSecondFloorConfig(val displayName: String, val fileIndex: Int) {
    Sandoria("San d'Oria", fileIndex = 0x1475E),
    Bastok("Bastok", fileIndex = 0x1475F),
    Windurst("Windurst", fileIndex = 0x14760),
    Patio("Patio", fileIndex = 0x14761),
}

@Serializable
data class MogHouseSetting(
    val baseModel: MogHouseConfig,
    val secondFloorModel: MogHouseSecondFloorConfig? = null,
    val entryId: DatId? = null,
)

data class ZoneConfig(
    val zoneId: Int,
    val entryId: DatId? = null,
    val startPosition: Vector3f? = null,
    val customDefinition: CustomZoneDefinition? = null,
    val mogHouseSetting: MogHouseSetting? = null,
) {
    fun matches(other: ZoneConfig?): Boolean {
        other ?: return false
        return zoneId == other.zoneId && mogHouseSetting == other.mogHouseSetting
    }
}

enum class CustomZoneConfig(val config: ZoneConfig) {
    MogGarden(ZoneConfig(280, startPosition = Vector3f(0f, 0f, 0f))),
    PsoXja_Elevator(ZoneConfig(9, startPosition = Vector3f(300f, 0f, -50f))),
    Valkurm_Cave(ZoneConfig(103, startPosition = Vector3f(718f, -8f, -180f))),
    Valkurm_Beach(ZoneConfig(103, startPosition = Vector3f(377f, 4.3f, -188f))),
    Beaucedine(ZoneConfig(111, startPosition = Vector3f(-12.5f, -60f, -86f))),
    Uleguerand(ZoneConfig(5, startPosition = Vector3f(-328f, -176f, -40.6f))),
    EastAdoulin(ZoneConfig(257, startPosition = Vector3f(-66f, -1f, -21f))),
    Sarutabartua_West(ZoneConfig(115, startPosition = Vector3f(320f, -4f, -36f))),
    Sarutabartua_East(ZoneConfig(116, startPosition = Vector3f(-116f, -4f, -520f))),
    EmpParadox(ZoneConfig(36, startPosition = Vector3f(500f, 0f, 500f))),
}

class ZoneChangeOptions(val fullyRevive: Boolean = false, val fade: Boolean = true, val pop: Boolean = true)
class DestinationZoneConfig(val zoneConfig: ZoneConfig, val options: ZoneChangeOptions = ZoneChangeOptions())

object ZoneChanger {

    private var destinationZoneConfig: DestinationZoneConfig? = null

    fun isChangingZones(): Boolean {
        return destinationZoneConfig != null
    }

    fun onZoneIn() {
        movePlayerToStartingPosition()

        val pop = destinationZoneConfig?.options?.pop ?: false
        if (pop) { ActorManager.player().onReadyToDraw { it.playRoutine(DatId.pop) } }

        val fade = destinationZoneConfig?.options?.fade ?: true
        if (fade) {
            ScreenFader.fadeIn(1.seconds) { destinationZoneConfig = null }
        } else {
            destinationZoneConfig = null
        }
    }

    fun beginChangeZone(interaction: ZoneInteraction, options: ZoneChangeOptions = ZoneChangeOptions()) {
        val destinationZoneLine = interaction.destId!!
        return beginChangeZone(destinationZoneLine, options)
    }

    private fun beginChangeZone(destinationZoneLine: DatId, options: ZoneChangeOptions = ZoneChangeOptions()) {
        if (isChangingZones()) { return }

        val zoneId = destinationZoneLine.toZoneId()
        val currentZoneId = SceneManager.getCurrentScene().getMainArea().id

        val zoneInteractions = SceneManager.getCurrentScene().getZoneInteractions()
        val intraZoneInteraction = zoneInteractions.firstOrNull { it.sourceId == destinationZoneLine }

        val mogHouseEntry = toMogHouseEntry(destinationZoneLine)

        val zoneConfig = if (mogHouseEntry != null) {
            val mogHouseSetting = MogHouseSetting(entryId = destinationZoneLine, baseModel = mogHouseEntry)
            ZoneConfig(zoneId = currentZoneId, entryId = destinationZoneLine, startPosition = Vector3f.ZERO, mogHouseSetting = mogHouseSetting)
        } else if (intraZoneInteraction != null) {
            ZoneConfig(zoneId = currentZoneId, entryId = destinationZoneLine)
        } else {
            ZoneConfig(zoneId = zoneId, entryId = destinationZoneLine)
        }

        println("Changing zones via [${destinationZoneLine}]")
        beginChangeZone(zoneConfig, options)
    }

    fun beginChangeZone(toZone: ZoneConfig, options: ZoneChangeOptions = ZoneChangeOptions()) {
        if (isChangingZones()) { return }

        val destination = toZone.copy(customDefinition = toZone.customDefinition ?: ZoneDefinitionManager[toZone.zoneId])
        destinationZoneConfig = DestinationZoneConfig(destination, options)

        if (options.fade) {
            ScreenFader.fadeOut(1.seconds) { GameClient.submitRequestZoneChange(destinationZoneConfig!!) }
        } else {
            GameClient.submitRequestZoneChange(destinationZoneConfig!!)
        }
    }

    private fun movePlayerToStartingPosition() {
        val zoneConfig = destinationZoneConfig?.zoneConfig ?: return

        val startingPosition = if (zoneConfig.startPosition != null) {
            zoneConfig.startPosition
        } else if (zoneConfig.entryId != null) {
            val destEntry = SceneManager.getCurrentScene().getZoneInteractions()
                .firstOrNull { it.sourceId == zoneConfig.entryId }

            if (destEntry != null) {
                destEntry.position + Vector3f(0f, destEntry.size.y/2f, 0f)
            } else {
                OnceLogger.warn("[${zoneConfig.entryId}] Couldn't find entry");
                Vector3f()
            }
        } else {
            val firstEntry = SceneManager.getCurrentScene().getZoneInteractions()
                .firstOrNull { it.isZoneEntrance() }

            if (firstEntry != null) {
                firstEntry.position + Vector3f(0f, firstEntry.size.y/2f, 0f)
            } else {
                OnceLogger.warn("[${zoneConfig.zoneId}] Didn't find any entry-points")
                getFallbackStartingPosition() ?: Vector3f(0f, -50f, 0f)
            }
        }

        ActorStateManager.player().position.copyFrom(startingPosition)
    }

    private fun getFallbackStartingPosition(): Vector3f? {
        val scene = SceneManager.getCurrentScene()

        val npcNames = listOf("Home Point", "Cavernous Maw", "Dimensional Portal", "Spatial Displacement", "Undulating Confluence", "Somnial Threshold", "Goblin Footprint", "Planar Rift", "Moogle")
        val zoneNpcs = scene.getNpcs().npcs

        for (npcName in npcNames) {
            val first = zoneNpcs.firstOrNull { it.name.contains(npcName) } ?: continue
            return first.info.position
        }

        return null
    }

    private fun toMogHouseEntry(entryId: DatId): MogHouseConfig? {
        return MogHouseConfig.values().firstOrNull { it.entryIds.contains(entryId.id) }
    }

}