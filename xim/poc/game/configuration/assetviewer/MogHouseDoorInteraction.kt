package xim.poc.game.configuration.assetviewer

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.EffectManager
import xim.poc.SceneManager
import xim.poc.ZoneAssociation
import xim.poc.game.QueryMenuOption
import xim.poc.game.QueryMenuResponse
import xim.poc.game.UiStateHelper
import xim.poc.game.configuration.EventScript
import xim.poc.game.configuration.EventScriptRunner
import xim.poc.game.configuration.RunOnceEventItem
import xim.poc.game.configuration.WaitRoutine
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.tools.MogHouseSecondFloorConfig
import xim.poc.tools.ZoneChanger
import xim.poc.tools.ZoneConfig
import xim.resource.DatId
import xim.resource.DirectoryResource
import xim.resource.EffectRoutineResource
import xim.resource.table.ZoneNameTable
import kotlin.time.Duration.Companion.seconds

private val entranceZoneMap = listOf(
    DatId("zmr1") to 230,
    DatId("zmr3") to 231,
    DatId("zmr5") to 232,
    DatId("zmr7") to 238,
    DatId("zmr9") to 239,
    DatId("zmrb") to 235,
    DatId("zmrd") to 235,
    DatId("zmrf") to 240,
    DatId("zmrh") to 234,
    DatId("zmrj") to 236,
    DatId("zmrl") to 241,
    DatId("zmrn") to 243,
    DatId("zmrp") to 244,
    DatId("zmrr") to 245,
    DatId("zmrt") to 246,
    DatId("zmrv") to 48,
    DatId("zmrx") to 50,
    DatId("zms1") to 80,
    DatId("zms3") to 87,
    DatId("zms5") to 87,
    DatId("zms7") to 94,
    DatId("zms9") to 256,
    DatId("zmsb") to 257,
)

object MogHouseDoorInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        UiStateHelper.openQueryMode("Where to?", getMainOptions(), callback = this::handleMainResponse)
    }

    private fun getMainOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Stay in your room.", value = -1)

        val entrance = SceneManager.getCurrentScene().config.mogHouseSetting?.entryId
        val hasMatchingEntrance = entranceZoneMap.any { it.first == entrance }
        if (hasMatchingEntrance) { options += QueryMenuOption("Area you entered from.", value = 0) }

        options += QueryMenuOption("Change floors.", value = 1)
        options += QueryMenuOption("Select an area to exit to.", value = 2)

        return options
    }

    private fun handleMainResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null || response.value < 0) { return QueryMenuResponse.pop }

        if (response.value == 0) {
            val entrance = SceneManager.getCurrentScene().config.mogHouseSetting?.entryId
            val matchingEntrance = entranceZoneMap.firstOrNull { it.first == entrance } ?: return QueryMenuResponse.pop

            exitTo(ZoneConfig(zoneId = matchingEntrance.second, entryId = matchingEntrance.first))
            return QueryMenuResponse.popAll
        }

        if (response.value == 1) {
            return handleChangeFloor()
        }

        if (response.value == 2) {
            UiStateHelper.openQueryMode("Which exit?", getExitOptions(), callback = this::handleExitResponse)
        }

        return QueryMenuResponse.noop
    }

    private fun handleChangeFloor(): QueryMenuResponse {
        val currentSceneConfig = SceneManager.getCurrentScene().config
        val currentMogHouseConfig = currentSceneConfig.mogHouseSetting ?: return QueryMenuResponse.pop

        return if (currentMogHouseConfig.secondFloorModel != null) {
            val updatedMogHouseConfig = currentMogHouseConfig.copy(secondFloorModel = null)
            exitTo(currentSceneConfig.copy(mogHouseSetting = updatedMogHouseConfig, startPosition = Vector3f.ZERO))
            QueryMenuResponse.popAll
        } else {
            UiStateHelper.openQueryMode("Which type?", getSecondFloorOptions(), callback = this::handleSecondFloorResponse)
            QueryMenuResponse.noop
        }
    }

    private fun getSecondFloorOptions(): List<QueryMenuOption> {
        return MogHouseSecondFloorConfig.values().map { QueryMenuOption(text = it.displayName, value = it.ordinal) }
    }

    private fun handleSecondFloorResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null) { return QueryMenuResponse.pop }
        val secondFloorModel = MogHouseSecondFloorConfig.values().getOrNull(response.value) ?: return QueryMenuResponse.pop

        val currentSceneConfig = SceneManager.getCurrentScene().config

        val currentMogHouseConfig = currentSceneConfig.mogHouseSetting ?: return QueryMenuResponse.pop
        val updatedMogHouseConfig = currentMogHouseConfig.copy(secondFloorModel = secondFloorModel)

        exitTo(currentSceneConfig.copy(mogHouseSetting = updatedMogHouseConfig, startPosition = Vector3f.ZERO))
        return QueryMenuResponse.popAll
    }

    private fun getExitOptions(): List<QueryMenuOption> {
        return entranceZoneMap.mapIndexed { index, it ->
            QueryMenuOption(text = ZoneNameTable.first(ZoneConfig(zoneId = it.second)), value = index)
        }
    }

    private fun handleExitResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null) { return QueryMenuResponse.pop }
        val entrance = entranceZoneMap.getOrNull(response.value) ?: return QueryMenuResponse.pop

        exitTo(ZoneConfig(zoneId = entrance.second, entryId = entrance.first))
        return QueryMenuResponse.popAll
    }

    private fun exitTo(zoneConfig: ZoneConfig) {
        val exitScript = EventScript(listOf(
            RunOnceEventItem { playOpenDoor() },
            WaitRoutine(1.5.seconds),
            RunOnceEventItem { ZoneChanger.beginChangeZone(toZone = zoneConfig) },
        ))

        EventScriptRunner.runScript(exitScript)
    }

    private fun playOpenDoor() {
        val mainArea = SceneManager.getCurrentScene().getMainArea()
        val doorDirectory = mainArea.root.getNullableChildRecursivelyAs(DatId("_720"), DirectoryResource::class) ?: return
        val openRoutine = doorDirectory.getNullableChildAs(DatId.open, EffectRoutineResource::class) ?: return
        EffectManager.registerRoutine(effectAssociation = ZoneAssociation(mainArea), effectRoutineResource = openRoutine)
    }

}