package xim.resource.table

import xim.poc.AreaType
import xim.poc.tools.ZoneConfig

object ZoneIdToResourceId {

    fun getMainAreaResourcePath(zoneConfig: ZoneConfig): String? {
        val custom = zoneConfig.customDefinition?.zoneResourcePath
        if (custom != null) { return custom }

        val zoneId = zoneConfig.zoneId

        val fileTableIndex = if (zoneConfig.mogHouseSetting != null) {
            zoneConfig.mogHouseSetting.secondFloorModel?.fileIndex ?: zoneConfig.mogHouseSetting.baseModel.rentalIndex
        } else if (zoneId < 0x100) {
            0x64 + zoneId
        } else {
            0x147B3 + (zoneId - 0x100)
        }

        return FileTableManager.getFilePath(fileTableIndex)
    }

    fun getSubAreaResourcePath(subAreaId: Int): String {
        // Everything except [Escha - Ru'Aun] fits in the first section of the file-table. Not sure if these ranges are exact.
        val fileTableOffset = if (subAreaId < 0x271) { 0x64 } else { 0x14768 - 0x271 }

        val fileTableIndex = subAreaId + fileTableOffset
        return FileTableManager.getFilePath(fileTableIndex) ?: throw IllegalStateException("No such sub-area: $fileTableIndex")
    }

    fun getBumpMapResourcePath(zoneId: Int, areaType: AreaType): String? {
        return when (areaType) {
            AreaType.MainArea -> getMainAreaBumpMapPath(zoneId)
            AreaType.SubArea -> getSubAreaBumpMapPath(zoneId)
            AreaType.ShipArea -> null
        }
    }

    private fun getMainAreaBumpMapPath(zoneId: Int): String? {
        // TODO - Bump-maps in [Silver Knife] seem broken
        if (zoneId == 283) { return null }

        val fileTableOffset = if (zoneId < 0x100) { 0x9b97 } else { 0x149d3 }
        return FileTableManager.getFilePath(fileTableOffset + zoneId)
    }

    private fun getSubAreaBumpMapPath(subAreaId: Int): String? {
        // Everything except [Escha - Ru'Aun] (which doesn't seem to have bumps) fits in the first section.
        val fileTableOffset = if (subAreaId < 0x271) { 0x9b97 } else { return null }
        return FileTableManager.getFilePath(subAreaId + fileTableOffset)
    }

}

object ZoneNameTable: LoadableResource {

    private val zoneNameTable = StringTable(resourceName = "ROM/165/84.DAT", bitMask = 0xFF.toByte())

    override fun preload() {
        zoneNameTable.preload()
    }

    override fun isFullyLoaded(): Boolean {
        return zoneNameTable.isFullyLoaded()
    }

    fun getAllFirst(): List<String> {
        return zoneNameTable.getAllFirst()
    }

    fun first(zoneConfig: ZoneConfig): String {
        return if (zoneConfig.mogHouseSetting?.secondFloorModel != null) {
            "Mog House 2F"
        } else if (zoneConfig.mogHouseSetting?.baseModel != null) {
            "Mog House 1F"
        } else {
            zoneConfig.customDefinition?.displayName ?: zoneNameTable.first(zoneConfig.zoneId)
        }
    }

}