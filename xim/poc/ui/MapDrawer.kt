package xim.poc.ui

import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.*
import xim.poc.browser.DatLoader
import xim.poc.browser.DatWrapper
import xim.poc.browser.GameKey
import xim.poc.game.ActorCollision
import xim.poc.gl.Color.Companion.HALF_ALPHA
import xim.poc.tools.UiPosition
import xim.poc.tools.UiPositionTool
import xim.resource.DirectoryResource
import xim.resource.UiElementResource
import xim.resource.table.FileTableManager
import xim.resource.table.ZoneMap
import xim.resource.table.ZoneMapTable
import kotlin.math.floor
import kotlin.math.roundToInt

class MapMarkerCommand(
    val worldSpacePosition: Vector3f,
    val uiElementIndex: Int,
    val priority: Int = 0,
    val uiElementGroup: String = "menu    keytops3",
)

object MapDrawer {

    private val loaded = HashMap<String, DatWrapper>()

    private var drawMap = false
    private var forceDrawMap = false
    private var mostRecentMap: ZoneMap? = null

    private val enqueuedMarkers = ArrayDeque<MapMarkerCommand>()

    fun toggle() {
        drawMap = !drawMap
    }

    fun forceDraw() {
        forceDrawMap = true
    }

    fun drawMapMarker(worldSpacePosition: Vector3f, uiElementIndex: Int, priority: Int = 0) {
        drawMapMarker(MapMarkerCommand(worldSpacePosition, uiElementIndex, priority))
    }

    fun drawMapMarker(command: MapMarkerCommand) {
        enqueuedMarkers += command
    }

    fun getPlayerMapCoordinates(): Pair<String, String> {
        val zoneMap = mostRecentMap ?: return Pair("?", "?")

        val mapPosition = getMapPosition(ActorManager.player().displayPosition, zoneMap) + zoneMap.offset * -1f

        val mapPosX = floor(15f * mapPosition.x / 512f).roundToInt()
        val mapPosY = floor(15f * mapPosition.y / 512f).roundToInt()

        return Pair(('A' + mapPosX).toString(), (1 + mapPosY).toString())
    }

    fun clear() {
        loaded.clear()
        mostRecentMap = null
    }

    fun draw(playerCollision: ActorCollision) {
        drawInternal(playerCollision)
        enqueuedMarkers.clear()
        forceDrawMap = false
    }

    private fun drawInternal(playerCollision: ActorCollision) {
        toggleOnKeyPress()
        val mapId = getCollision(playerCollision)?.mapId ?: return
        val zone = SceneManager.getCurrentScene().config

        val zoneMap = ZoneMapTable[zone, mapId]
        mostRecentMap = zoneMap
        if (zoneMap == null) { return }

        val fileName = FileTableManager.getFilePath(zoneMap.fileId) ?: throw IllegalStateException("Map not found?")

        val wrapper = DatLoader.load(fileName)
        loaded[fileName] = wrapper

        val resource = wrapper.getAsResourceIfReady()
        if (resource != null) { draw(zoneMap, resource) }
    }

    private fun toggleOnKeyPress() {
        val pressed = MainTool.platformDependencies.keyboard.isKeyPressed(GameKey.ToggleMap)
        if (!pressed) { return }
        toggle()
    }

    private fun getCollision(actorCollision: ActorCollision): CollisionProperty? {
        val currentScene = SceneManager.getCurrentScene()

        val collisionsByMain = actorCollision.collisionsByArea[currentScene.getMainArea()]
        if (!collisionsByMain.isNullOrEmpty()) {
            return collisionsByMain.first()
        }

        val subArea = currentScene.getSubArea() ?: return null
        val collisionsBySub = actorCollision.collisionsByArea[subArea]
        if (!collisionsBySub.isNullOrEmpty()) {
            return collisionsBySub.first()
        }

        return null
    }

    private fun draw(zoneMap: ZoneMap, directoryResource: DirectoryResource) {
        if (!drawMap && !forceDrawMap) { return }

        val resource = directoryResource.getOnlyChildByType(UiElementResource::class)
        val group = resource.uiElementGroup

        val offset = UiPositionTool.getOffset(UiPosition.Map) + zoneMap.offset * -1f
        UiElementHelper.drawUiElement(lookup = group.name, index = 0, position = offset, color = HALF_ALPHA)

        enqueuedMarkers.sortBy { it.priority }
        val markerOffset = Vector2f(-8f, -8f)

        for (markerCommand in enqueuedMarkers) {
            val markerMapPos = getMapPosition(markerCommand.worldSpacePosition, zoneMap) + markerOffset
            UiElementHelper.drawUiElement(lookup = markerCommand.uiElementGroup, index = markerCommand.uiElementIndex, position = markerMapPos + offset, color = HALF_ALPHA)
        }

        val actor = ActorManager.player()
        val mapPos = getMapPosition(actor.displayPosition, zoneMap)
        UiElementHelper.drawUiElement(lookup = "menu    keytops3", index = 159, position = mapPos + offset, color = HALF_ALPHA, rotation = actor.displayFacingDir)
    }

    private fun getMapPosition(worldPos: Vector3f, zoneMap: ZoneMap): Vector2f {
        return Vector2f(0.5f + worldPos.x / zoneMap.size * 512f, -0.5f + -worldPos.z / zoneMap.size * 512f)
    }

}