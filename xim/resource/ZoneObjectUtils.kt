package xim.resource

class ZoneObjectLevelOfDetail(private val zoneObject: ZoneObject, localDir: DirectoryResource) {

    companion object {
        fun hasLevelOfDetail(zoneObject: ZoneObject) =
            zoneObject.id.endsWith("_l") || zoneObject.id.endsWith("_m") || zoneObject.id.endsWith("_h")
    }

    private val highLod: List<ZoneMeshResource>
    private val medLod: List<ZoneMeshResource>
    private val lowLod: List<ZoneMeshResource>

    init {
        val baseId = zoneObject.id.substring(0, zoneObject.id.length - 2)
        val highLevelOfDetail = localDir.getZoneMeshResourceByNameAs(baseId + "_h")
        val medLevelOfDetail = localDir.getZoneMeshResourceByNameAs(baseId + "_m")
        val lowLevelOfDetail = localDir.getZoneMeshResourceByNameAs(baseId + "_l")

        highLod = listOfNotNull(highLevelOfDetail, medLevelOfDetail, lowLevelOfDetail)
        medLod = listOfNotNull(medLevelOfDetail, highLevelOfDetail, lowLevelOfDetail)
        lowLod = listOfNotNull(lowLevelOfDetail, medLevelOfDetail, highLevelOfDetail)
    }

    fun getResource(distance: Float): ZoneMeshResource {
        val meshByPriority = if (distance < zoneObject.highDefThreshold) {
            highLod
        } else if (distance < zoneObject.midDefThreshold) {
            medLod
        } else {
            lowLod
        }

        return meshByPriority[0]
    }

}