package xim.resource.table

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.EquipmentLook
import xim.poc.ItemModelSlot
import xim.poc.ModelLook
import xim.poc.browser.DatLoader
import xim.poc.browser.DatWrapper
import xim.poc.tools.ZoneConfig
import xim.resource.ByteReader
import xim.resource.DatId
import xim.util.OnceLogger
import xim.util.PI_f

private fun npcIdToZoneId(id: Int): Int {
    return (id ushr 12) and 0xFFF
}

class ZoneNpcList(
    val resourceId: String,
    val npcs: List<Npc>,
    val npcsByDatId: Map<DatId, Npc>,
)

data class Npc(
    val id: Int,
    val name: String,
    val info: NpcInfo,
) {
    val actorId = ActorId(id)
}

data class NpcInfo(
    val id: Int,
    val rotation: Float,
    val position: Vector3f,
    val flag: Int,
    val nameVis: Int,
    val status: Int,
    val entityFlags: Int,
    val look: ModelLook,
    val datId: DatId?,
    val nameId: Int,
    val spawnAnimations: List<DatId>? = null,
) {

    fun hasShadow(): Boolean {
        // TODO - is this correct? It doesn't seem to capture all cases
        return flag and 0x8000 == 0
    }

    fun shouldDisplayNameAndHp(): Boolean {
        return entityFlags and 0x0008 != 0
    }

    fun isShip(): Boolean {
        return look.type == 0x04
    }

    fun isDefaultDisabled(): Boolean {
        return status == 0x02 || status == 0x06
    }

}

//https://raw.githubusercontent.com/LandSandBoat/server/base/sql/npc_list.sql
object NpcTable: LoadableResource {

    private lateinit var table: Map<Int, NpcInfo>
    private val npcsByZoneId = HashMap<Int, HashMap<Int, NpcInfo>>()

    private var preloaded = false

    override fun preload() {
        if (preloaded) { return }
        preloaded = true
        loadTable()
    }

    override fun isFullyLoaded() : Boolean {
        return this::table.isInitialized
    }

    fun getNpcModelIndex(npcLook: ModelLook): Int {
        if (npcLook.type != 0 && npcLook.type != 6) { throw IllegalStateException("But it isn't an NPC type: $npcLook") }
        return getNpcModelIndex(npcLook.modelId)
    }

    fun getAdditionalAnimationId(npcLook: ModelLook): Int? {
        if (npcLook.modelId == 0x974) {
            // Waypoints
            return 0x113af
        }

        return null
    }

    fun getDefaultAppearanceState(npcLook: ModelLook): Int? {
        return when (npcLook.modelId) {
            0x96F -> 1  // Planar Rift -> 'Idle'
            0x98F -> 1  // Ethereal Junction -> 'Idle'
            else -> null
        }
    }

    fun getNpcModelIndex(modelId: Int): Int {
        return when {
            modelId < 0x5DC -> 0x514 + modelId
            modelId < 0xBB8 -> 0xC477 + modelId
            modelId < 0xC79 -> 0x17A8B + modelId
            else -> 0x180F2 + modelId // Speculated
        }
    }

    fun getNpcInfoByZone(zoneId: Int): Map<Int, NpcInfo> {
        return npcsByZoneId[zoneId] ?: emptyMap()
    }

    private fun loadTable() {
        DatLoader.load("landsandboat/NpcTable-V3.DAT").onReady { parse(it.getAsBytes()) }
    }

    private fun parse(byteReader: ByteReader) {
        val table = HashMap<Int, NpcInfo>()

        while (byteReader.hasMore()) {
            val start = byteReader.position

            try {
                val settings = NpcInfo(
                    id = byteReader.next32(),
                    rotation = (byteReader.nextFloat()/255f) * 2*PI_f,
                    position = byteReader.nextVector3f(),
                    flag = byteReader.next16(),
                    nameVis = byteReader.next8(),
                    status = byteReader.next8(),
                    entityFlags = byteReader.next16(),
                    look = parseModelLook(byteReader),
                    datId = byteReader.nextDatId().toNullIfZero(),
                    nameId = byteReader.next16(),
                )

                table[settings.id] = settings

                val zoneMap = npcsByZoneId.getOrPut(npcIdToZoneId(settings.id)) { HashMap() }
                zoneMap[settings.id] = settings
            } catch (e: Exception) {
                OnceLogger.warn("[NpcTable] Failed to parse @ $byteReader. $e")
            }

            byteReader.position = start + 0x34
        }

        this.table = table
    }

    private fun parseModelLook(byteReader: ByteReader): ModelLook {
        val type = byteReader.next16()

        val modelId = byteReader.next16()
        val equipment = EquipmentLook()

        val lookModelId = if (type == 1) {
            (modelId ushr 8) and 0xFF
        } else {
            modelId
        }

        for (i in 0 until 8) {
            val prefixedModelId = byteReader.next16()
            val itemModelSlot = ItemModelSlot.toSlot(prefixedModelId)
            val itemModelId = prefixedModelId and 0x0FFF
            equipment[itemModelSlot] = itemModelId
        }

        if (type == 1) {
            equipment[ItemModelSlot.Face] = modelId and 0xFF
        }

        return ModelLook(type, lookModelId, equipment)
    }

}

object ZoneNpcTableProvider {

    private fun toResourceId(zoneId: Int): Int {
        return if (zoneId < 0x100) {
            0x1a40 + zoneId
        } else {
            0x151db + (zoneId - 0x100)
        }
    }

    fun fetchNpcDat(zoneConfig: ZoneConfig, callback: (ZoneNpcList) -> Unit) {
        if (zoneConfig.mogHouseSetting != null) {
            callback(ZoneNpcList("", emptyList(), emptyMap()))
            return
        }

        val custom = zoneConfig.customDefinition?.staticNpcList
        if (custom != null) {
            callback.invoke(custom)
            return
        }

        val zoneNpcResourceId = toResourceId(zoneConfig.zoneId)
        val resourcePath = FileTableManager.getFilePath(zoneNpcResourceId) ?: throw IllegalStateException("No NPC def? ${zoneNpcResourceId.toString(0x10)}")
        DatLoader.load(resourcePath).onReady { callback.invoke(parseNpcs(zoneConfig, it)) }
    }

    private fun parseNpcs(zoneConfig: ZoneConfig, datWrapper: DatWrapper): ZoneNpcList {
        val names = parseNpcNameMap(datWrapper)

        val npcList = ArrayList<Npc>()
        val zoneNpcInfo = NpcTable.getNpcInfoByZone(zoneConfig.zoneId)

        for ((id, info) in zoneNpcInfo) {
            val tableName = names[id]
            val name = if (!tableName.isNullOrBlank()) { tableName } else { generateFallbackName(info) }
            npcList += Npc(id, name, info)
        }

        val npcsByDatId = npcList.filter { it.info.datId != null }
            .associateBy { it.info.datId!! }

        return ZoneNpcList(datWrapper.resourceName, npcList, npcsByDatId)
    }

    private fun parseNpcNameMap(datWrapper: DatWrapper): Map<Int, String> {
        val byteReader = datWrapper.getAsBytes()
        val npcNames = HashMap<Int, String>()

        while (byteReader.hasMore()) {
            val npcName = byteReader.nextString(0x1C).substringBefore(0.toChar())
            val id = byteReader.next32()
            npcNames[id] = npcName
        }

        return npcNames
    }

    private fun generateFallbackName(npcInfo: NpcInfo): String {
        if (npcInfo.isShip()) {
            return npcInfo.nameId.toString(0x10)
        }

        if (npcInfo.datId != null) {
            if (npcInfo.datId.isDoorId()) {
                return "Door [${npcInfo.datId.id}]"
            } else if (npcInfo.datId.isElevatorId()) {
                return "Elevator [${npcInfo.datId.id}]"
            }
        }

        return npcInfo.id.toString(0x10)
    }

}