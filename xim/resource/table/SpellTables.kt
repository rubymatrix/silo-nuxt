package xim.resource.table

import xim.poc.browser.DatLoader
import xim.poc.game.configuration.constants.SpellSkillId
import xim.poc.game.configuration.constants.spellSearingTempest_719
import xim.poc.game.configuration.constants.spellUproot_747
import xim.resource.ByteReader
import xim.resource.MagicType
import xim.resource.SpellInfo
import xim.resource.SpellListResource

val SpellNameTable = StringTable("ROM/181/73.DAT")

// https://github.com/LandSandBoat/server/blob/base/sql/spell_list.sql
object SpellAnimationTable: LoadableResource {

    const val fileTableOffset = 0xAF0

    private lateinit var table: MutableMap<Int, Int>
    private var preloaded = false

    override fun preload() {
        if (preloaded) { return }
        preloaded = true
        loadTable()
    }

    override fun isFullyLoaded() : Boolean {
        return this::table.isInitialized
    }

    operator fun get(spellInfo: SpellInfo): Int {
        return get(spellInfo.index)
    }

    operator fun get(spellId: Int): Int {
        val spellInfo = SpellInfoTable[spellId]
        if (spellInfo.magicType == MagicType.Trust) { return 0xe9b }

        val index = table[spellId] ?: 0
        return fileTableOffset + index
    }

    fun mutate(spellId: Int, spellAnimationId: Int) {
        table[spellId] = spellAnimationId
    }

    private fun loadTable() {
        DatLoader.load("landsandboat/SpellAnimationTable.DAT").onReady { parse(it.getAsBytes()) }
    }

    private fun parse(byteReader: ByteReader) {
        val entries = HashMap<Int, Int>()

        var counter = 0
        while (byteReader.hasMore()) {
            entries[counter++] = byteReader.next16()
        }

        entries.putAll(additionalConfiguration().mapKeys { it.key.id })
        table = entries
    }

    private fun additionalConfiguration(): Map<SpellSkillId, Int> {
        return mapOf(
            spellSearingTempest_719 to 0x3E4,
            spellUproot_747 to 0x3B0,
        )
    }

}

object SpellInfoTable: LoadableResource {

    private lateinit var spellListResource: SpellListResource
    private var preloaded = false

    override fun preload() {
        if (preloaded) { return }
        preloaded = true
        loadTable()
    }

    override fun isFullyLoaded() : Boolean {
        return this::spellListResource.isInitialized
    }

    fun hasInfo(spellId: Int): Boolean {
        return spellListResource.spells[spellId] != null
    }

    fun getSpellInfo(spellId: Int) : SpellInfo {
        return spellListResource.spells[spellId] ?: throw IllegalStateException("No info for: $spellId")
    }

    fun mutate(spellId: Int, spellInfo: SpellInfo) {
        spellListResource.spells[spellId] = spellInfo
    }

    operator fun get(spellId: Int) = getSpellInfo(spellId)

    private fun loadTable() {
        DatLoader.load("ROM/118/114.DAT").onReady {
            spellListResource = it.getAsResource().getOnlyChildByType(SpellListResource::class)
        }
    }

    fun SpellSkillId.toSpellInfo(): SpellInfo {
        return SpellInfoTable[id]
    }

}