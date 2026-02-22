package xim.resource.table

import xim.poc.browser.DatLoader
import xim.poc.tools.ZoneConfig
import xim.resource.ByteReader


data class MusicSettings(
    val musicId: Int? = null,
    val battleSoloMusicId: Int? = null,
    val battlePartyMusicId: Int? = null,
)

data class ZoneSettings(
    val zoneId: Int,
    val musicSettings: MusicSettings,
)

// https://github.com/LandSandBoat/server/blob/base/sql/zone_settings.sql
object ZoneSettingsTable: LoadableResource {

    private lateinit var table: Map<Int, ZoneSettings>
    private var preloaded = false

    override fun preload() {
        if (preloaded) { return }
        preloaded = true
        loadTable()
    }

    override fun isFullyLoaded() : Boolean {
        return this::table.isInitialized
    }

    fun getZoneIds(): Collection<Int> {
        return table.keys
    }

    operator fun get(zoneConfig: ZoneConfig) : ZoneSettings {
        if (zoneConfig.customDefinition?.zoneSettings != null) { return zoneConfig.customDefinition.zoneSettings }

        if (zoneConfig.mogHouseSetting != null) {
            return ZoneSettings(zoneId = zoneConfig.zoneId, musicSettings = MusicSettings(musicId = 126))
        }

        val zoneId = zoneConfig.zoneId
        return table[zoneId] ?: ZoneSettings(zoneId, musicSettings = MusicSettings())
    }

    private fun loadTable() {
        DatLoader.load("landsandboat/ZoneSettingsTable.DAT").onReady { parse(it.getAsBytes()) }
    }

    private fun parse(byteReader: ByteReader) {
        val table = HashMap<Int, ZoneSettings>()

        while (byteReader.hasMore()) {
            val settings = ZoneSettings(
                zoneId = byteReader.next32(),
                musicSettings = MusicSettings(
                    musicId = nullIfZero(byteReader.next32()),
                    battleSoloMusicId = nullIfZero(byteReader.next32()),
                    battlePartyMusicId = nullIfZero(byteReader.next32()),
                ),
            )

            table[settings.zoneId] = settings
        }

        this.table = table
    }

    private fun nullIfZero(value: Int): Int? {
        return if (value == 0) { null } else { value }
    }

}