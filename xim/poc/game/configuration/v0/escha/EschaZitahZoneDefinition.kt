package xim.poc.game.configuration.v0.escha

import xim.poc.game.configuration.CustomZoneDefinition
import xim.poc.gl.ByteColor
import xim.poc.gl.Color
import xim.resource.*
import xim.resource.table.MusicSettings
import xim.resource.table.ZoneNpcList
import xim.resource.table.ZoneSettings

object EschaZitahZoneDefinition {

    private const val zoneId = 288

    private val staticNpcList = ZoneNpcList(
        resourceId = "",
        npcs = emptyList(),
        npcsByDatId = emptyMap(),
    )

    private val weatherFine = DirectoryResource(parent = null, id = DatId.weatherFine).also {
        val (envHeader, envResource) = makeEnvironmentResource(it, DatId("0000"))
        it.addChild(envHeader, envResource)

        val (ambHeader, ambResource) = makeAmbienceResource(it)
        it.addChild(ambHeader, ambResource)
    }

    val definition = CustomZoneDefinition(
        zoneId = zoneId,
        staticNpcList = staticNpcList,
        customWeather = mapOf(DatId.weather to mapOf(
            DatId.weatherFine to weatherFine,
        ))
    )

    private fun makeEnvironmentResource(localDir: DirectoryResource, datId: DatId): Pair<SectionHeader, EnvironmentResource> {
        val header = SectionHeader()
        header.sectionId = datId
        header.sectionType = SectionType.S2F_Environment
        header.localDir = localDir

        val skyBox = SkyBox(
            spokes = 16,
            radius = 2029.5f,
            slices = listOf(
                SkyBoxSlice(color = ByteColor(192, 192, 192, 128), elevation = 0f),
                SkyBoxSlice(color = ByteColor(192, 192, 192, 128), elevation = 0.035f),
                SkyBoxSlice(color = ByteColor(192, 192, 192, 128), elevation = 0.096f),
                SkyBoxSlice(color = ByteColor(192, 192, 192, 128), elevation = 0.193f),
                SkyBoxSlice(color = ByteColor(192, 192, 192, 128), elevation = 0.298f),
                SkyBoxSlice(color = ByteColor(192, 192, 192, 128), elevation = 0.474f),
                SkyBoxSlice(color = ByteColor(192, 192, 192, 128), elevation = 0.736f),
                SkyBoxSlice(color = ByteColor(192, 192, 192, 128), elevation = 1f),
            ),
        )

        val modelLighting = LightConfig(
            sunLightColor = ByteColor(78, 95, 97, 128),
            moonLightColor = ByteColor(28, 33, 34, 128),
            ambientColor = ByteColor(97, 124, 135, 128),
            fogColor = ByteColor(192, 192, 192, 128),
            fogEnd = 104f,
            fogStart = 9.8f,
            diffuseMultiplier = 1.58f,
        )

        val terrainLighting = LightConfig(
            sunLightColor = ByteColor(134, 134, 134, 128),
            moonLightColor = ByteColor(53, 53, 53, 128),
            ambientColor = ByteColor(88, 88, 88, 128),
            fogColor = ByteColor(192, 192, 192, 128),
            fogEnd = 104f,
            fogStart = 9.8f,
            diffuseMultiplier = 1.58f,
        )

        val environmentLighting = EnvironmentLighting(
            modelLighting = modelLighting,
            terrainLighting = terrainLighting,
            indoors = false,
        )

        return header to EnvironmentResource(
            id = datId,
            skyBox = skyBox,
            environmentLighting = environmentLighting,
            drawDistance = 140f,
            clearColor = Color(0.75f, 0.75f, 0.75f, 0.75f),
        )
    }

    private fun makeAmbienceResource(localDir: DirectoryResource): Pair<SectionHeader, DatEntry> {
        val header = SectionHeader()
        header.sectionId = DatId("0000")
        header.sectionType = SectionType.S3D_SoundEffectPointer
        header.localDir = localDir

        val resource = SoundPointerResource(
            id = DatId("0000"),
            soundId = 38071,
            folderId = "038",
            fileId = "038071",
        )

        return header to resource
    }

}