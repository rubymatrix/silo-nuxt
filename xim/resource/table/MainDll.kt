package xim.resource.table

import js.typedarrays.Uint8Array
import xim.poc.RaceGenderConfig
import xim.poc.browser.DatLoader
import xim.resource.ByteReader
import xim.resource.table.DllOffsetHints.actionAnimationFileTableOffsetHint
import xim.resource.table.DllOffsetHints.battleAnimationFileTableOffsetHint
import xim.resource.table.DllOffsetHints.battleSkirtAnimationFileTableOffsetHint
import xim.resource.table.DllOffsetHints.battleSkirtDwAnimationFileTableOffsetHint
import xim.resource.table.DllOffsetHints.danceSkillAnimationFileTableOffsetHint
import xim.resource.table.DllOffsetHints.dualWieldMainHandFileTableOffsetHint
import xim.resource.table.DllOffsetHints.dualWieldOffHandFileTableOffsetHint
import xim.resource.table.DllOffsetHints.emoteAnimationOffsetHint
import xim.resource.table.DllOffsetHints.equipmentLookupTableOffsetHint
import xim.resource.table.DllOffsetHints.fishingRodFileTableOffsetHint
import xim.resource.table.DllOffsetHints.raceConfigLookupTableOffsetHint
import xim.resource.table.DllOffsetHints.weaponSkillAnimationFileTableOffsetHint
import xim.resource.table.DllOffsetHints.zoneDecryptTable1OffsetHint
import xim.resource.table.DllOffsetHints.zoneDecryptTable2OffsetHint
import xim.resource.table.DllOffsetHints.zoneMapTableOffsetHint

private class DllOffsets(
    val battleAnimationFileTableOffset: Int,
    val dualWieldMainHandFileTableOffset: Int,
    val dualWieldOffHandFileTableOffset: Int,
    val battleSkirtAnimationFileTableOffset: Int,
    val battleSkirtDwAnimationFileTableOffset: Int,
    val emoteAnimationOffset: Int,
    val equipmentLookupTableOffset: Int,
    val raceConfigLookupTableOffset: Int,
    val weaponSkillAnimationFileTableOffset: Int,
    val danceSkillAnimationFileTableOffset: Int,
    val actionAnimationFileTableOffset: Int,
    val fishingRodFileTableOffset: Int,
    val zoneDecryptTable1Offset: Int,
    val zoneDecryptTable2Offset: Int,
    val zoneMapTableOffset: Int,
)

private object DllOffsetHints {
    val battleAnimationFileTableOffsetHint: UInt = 0xC825C825U
    val dualWieldMainHandFileTableOffsetHint: UInt = 0x6F9F6F9FU
    val dualWieldOffHandFileTableOffsetHint: UInt = 0xEF9DEF9DU
    val battleSkirtAnimationFileTableOffsetHint: UInt = 0x4826C826U
    val battleSkirtDwAnimationFileTableOffsetHint: UInt = 0xEF9F6FA0U
    val emoteAnimationOffsetHint: UInt = 0x48274827U
    val equipmentLookupTableOffsetHint: UInt = 0xA81B0000U
    val raceConfigLookupTableOffsetHint: UInt = 0xA01BA01BU
    val weaponSkillAnimationFileTableOffsetHint: UInt = 0xCB81CB81U
    val danceSkillAnimationFileTableOffsetHint: UInt = 0xB9E2B9E2U
    val actionAnimationFileTableOffsetHint: UInt = 0xCB96CB96U
    val fishingRodFileTableOffsetHint: UInt = 0x8B998B99U
    val zoneDecryptTable1OffsetHint: UInt = 0xE2E506A9U
    val zoneDecryptTable2OffsetHint: UInt = 0xB8C5F784U
    val zoneMapTableOffsetHint: ULong = 0x6400000100010100U
}

object MainDll: LoadableResource {

    private lateinit var dll: ByteReader
    private lateinit var offsets: DllOffsets

    override fun preload() {
        DatLoader.load("FFXiMain.dll").onReady {
            dll = it.getAsBytes()
            offsets = loadOffsets()
        }
    }

    override fun isFullyLoaded(): Boolean {
        return this::dll.isInitialized
    }

    fun getZoneDecryptTable1(): Uint8Array {
        return getBytes(offset = offsets.zoneDecryptTable1Offset, size = 0x100)
    }

    fun getZoneDecryptTable2(): Uint8Array {
        return getBytes(offset = offsets.zoneDecryptTable2Offset, size = 0x100)
    }

    fun getZoneMapTableReader(): ByteReader {
        return getByteReader(offset = offsets.zoneMapTableOffset, size = 0x2D64)
    }

    fun getEquipmentLookupTable(): ByteReader {
        return getByteReader(offset = offsets.equipmentLookupTableOffset, size = 0x1B0 * 0x10)
    }

    fun getBaseBattleAnimationIndex(raceGenderConfig: RaceGenderConfig): Int {
        return dll.read16(offsets.battleAnimationFileTableOffset + raceGenderConfig.index * 2)
    }

    fun getBaseSkirtAnimationIndex(raceGenderConfig: RaceGenderConfig, dualWield: Boolean): Int {
        val offset = if (dualWield) { offsets.battleSkirtDwAnimationFileTableOffset } else { offsets.battleSkirtAnimationFileTableOffset }
        return dll.read16(offset + raceGenderConfig.index * 4 + 2)
    }

    fun getBaseWeaponSkillAnimationIndex(raceGenderConfig: RaceGenderConfig): Int {
        return dll.read16(offsets.weaponSkillAnimationFileTableOffset + raceGenderConfig.index * 2)
    }

    fun getBaseRaceConfigIndex(raceGenderConfig: RaceGenderConfig): Int {
        return dll.read16(offsets.raceConfigLookupTableOffset + raceGenderConfig.index * 2)
    }

    fun getBaseDanceSkillAnimationIndex(raceGenderConfig: RaceGenderConfig): Int {
        return dll.read16(offsets.danceSkillAnimationFileTableOffset + raceGenderConfig.index * 2)
    }

    fun getBaseDualWieldMainHandAnimationIndex(raceGenderConfig: RaceGenderConfig): Int {
        return dll.read16(offsets.dualWieldMainHandFileTableOffset + raceGenderConfig.index * 2)
    }

    fun getBaseDualWieldOffHandAnimationIndex(raceGenderConfig: RaceGenderConfig): Int {
        return dll.read16(offsets.dualWieldOffHandFileTableOffset + raceGenderConfig.index * 2)
    }

    fun getBaseEmoteAnimationIndex(raceGenderConfig: RaceGenderConfig): Int {
        return dll.read16(offsets.emoteAnimationOffset + raceGenderConfig.index * 2)
    }

    fun getActionAnimationIndex(raceGenderConfig: RaceGenderConfig): Int {
        return dll.read16(offsets.actionAnimationFileTableOffset + raceGenderConfig.index * 2)
    }

    fun getBaseFishingRodIndex(raceGenderConfig: RaceGenderConfig): Int {
        return dll.read16(offsets.fishingRodFileTableOffset + raceGenderConfig.index * 2)
    }

    private fun getBytes(offset: Int, size: Int): Uint8Array {
        return dll.subBuffer(offset = offset, size = size)
    }

    private fun getByteReader(offset: Int, size: Int): ByteReader {
        return ByteReader(getBytes(offset, size))
    }

    private fun loadOffsets(): DllOffsets {
        return DllOffsets(
            battleAnimationFileTableOffset = findOffset(battleAnimationFileTableOffsetHint),
            dualWieldMainHandFileTableOffset = findOffset(dualWieldMainHandFileTableOffsetHint),
            dualWieldOffHandFileTableOffset = findOffset(dualWieldOffHandFileTableOffsetHint),
            battleSkirtAnimationFileTableOffset = findOffset(battleSkirtAnimationFileTableOffsetHint),
            battleSkirtDwAnimationFileTableOffset = findOffset(battleSkirtDwAnimationFileTableOffsetHint),
            emoteAnimationOffset = findOffset(emoteAnimationOffsetHint),
            equipmentLookupTableOffset = findOffset(equipmentLookupTableOffsetHint),
            raceConfigLookupTableOffset = findOffset(raceConfigLookupTableOffsetHint),
            weaponSkillAnimationFileTableOffset = findOffset(weaponSkillAnimationFileTableOffsetHint),
            danceSkillAnimationFileTableOffset = findOffset(danceSkillAnimationFileTableOffsetHint),
            actionAnimationFileTableOffset = findOffset(actionAnimationFileTableOffsetHint),
            fishingRodFileTableOffset = findOffset(fishingRodFileTableOffsetHint),
            zoneDecryptTable1Offset = findOffset(zoneDecryptTable1OffsetHint),
            zoneDecryptTable2Offset = findOffset(zoneDecryptTable2OffsetHint),
            zoneMapTableOffset = findOffset(zoneMapTableOffsetHint),
        )
    }

    private fun findOffset(hint: UInt): Int {
        dll.position = 0x30000
        val signedHint = hint.toInt()

        for (i in 0 until 0xC000) {
            if (dll.next32BE() == signedHint) {
                return dll.position - 0x04
            }
        }

        throw IllegalStateException("Failed to find offset for ${hint.toString(0x10)}")
    }

    private fun findOffset(hint: ULong): Int {
        dll.position = 0x30000
        val signedHint = hint.toLong()

        for (i in 0 until 0x6000) {
            if (dll.next64BE() == signedHint) {
                return dll.position - 0x08
            }
        }

        throw IllegalStateException("Failed to find offset for ${hint.toString(0x10)}")
    }

}