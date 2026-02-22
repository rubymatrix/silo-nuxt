package xim.poc.tools

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDetailsElement
import org.w3c.dom.HTMLInputElement
import xim.math.Vector3f
import xim.poc.*
import xim.poc.game.*
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.navigation.BattleLocationNavigator
import xim.poc.game.configuration.v0.navigation.BattleLocationPather
import xim.poc.game.configuration.v0.zones.PathingSettings
import xim.poc.game.event.InitialActorState
import xim.poc.gl.ByteColor
import xim.util.toTruncatedString

object BattleLocationTool {

    private val details by lazy { document.getElementById("blHelper") as HTMLDetailsElement }

    private val blCopy by lazy { document.getElementById("blCopy") as HTMLButtonElement }

    private val blSpawnSnap by lazy { document.getElementById("blSpawnSnap") as HTMLButtonElement }
    private val blEntranceSnap by lazy { document.getElementById("blEntranceSnap") as HTMLButtonElement }
    private val blExitSnap by lazy { document.getElementById("blExitSnap") as HTMLButtonElement }
    private val blTreasureSnap by lazy { document.getElementById("blTreasureSnap") as HTMLButtonElement }

    private val blSpawn by lazy { document.getElementById("blSpawn") as HTMLInputElement }
    private val blEntrance by lazy { document.getElementById("blEntrance") as HTMLInputElement }
    private val blExit by lazy { document.getElementById("blExit") as HTMLInputElement }
    private val blTreasure by lazy { document.getElementById("blTreasure") as HTMLInputElement }

    private val vSpawn = Vector3f()
    private val vEntrance = Vector3f()
    private val vExit = Vector3f()
    private val vTreasure = Vector3f()

    private var aSpawn: ActorPromise? = null
    private var aEntrance: ActorPromise? = null
    private var aExit: ActorPromise? = null
    private var aTreasure: ActorPromise? = null

    private val boxLock by lazy { document.getElementById("boxLock") as HTMLInputElement }
    private val boxCurrentPos by lazy { document.getElementById("boxCurrentPos") as HTMLInputElement }
    private val boxX by lazy { document.getElementById("boxX") as HTMLInputElement }
    private val boxY by lazy { document.getElementById("boxY") as HTMLInputElement }
    private val boxZ by lazy { document.getElementById("boxZ") as HTMLInputElement }

    private val autoPath by lazy { document.getElementById("autoPath") as HTMLButtonElement }
    private val clearPath by lazy { document.getElementById("clearPath") as HTMLButtonElement }
    private val spherePath by lazy { document.getElementById("spherePath") as HTMLButtonElement }
    private var autoPathGen: BattleLocationNavigator? = null

    private val clickCollisionInput by lazy { document.getElementById("clickCollisionPos") as HTMLInputElement }
    private var clickPosition: Vector3f? = null

    private val boxPosition = Vector3f()

    private var setup = false

    fun setup() {
        if (setup) { return }
        setup = true

        blCopy.onclick = { output() }
        blSpawnSnap.onclick = { aSpawn = snap(blSpawn, vSpawn, aSpawn); Unit }
        blEntranceSnap.onclick = { aEntrance = snap(blEntrance, vEntrance, aEntrance); Unit }
        blExitSnap.onclick = { aExit = snap(blExit, vExit, aExit); Unit }
        blTreasureSnap.onclick = { aTreasure = snap(blTreasure, vTreasure, aTreasure); Unit }
        autoPath.onclick = { generatePath() }
        clearPath.onclick = { autoPathGen = null; Unit }
        spherePath.onclick = { fromBattleLocationBoundary() }
    }

    fun update() {
        if (!details.open) { return }
        drawBox()

        ClickHandler.registerWorldClickListener(this::getClickCollision)
        clickPosition?.let { SphereDrawingTool.drawSphere(center = it, radius = 0.25f, color = ByteColor.opaqueB) }

        val target = ActorStateManager.playerTarget()?.position
        autoPathGen?.draw(target)
        autoPathGen?.drawGrid()
    }

    private fun snap(input: HTMLInputElement, vector3f: Vector3f, current: ActorPromise?): ActorPromise {
        val player = ActorStateManager.player()

        vector3f.copyFrom(player.position)

        current?.onReady { GameEngine.submitDeleteActor(it.id)  }

        input.value = "Vector3f(x=${player.position.x.toTruncatedString(2)}f,y=${player.position.y.toTruncatedString(2)}f,z=${player.position.z.toTruncatedString(2)}f)"

        return GameEngine.submitCreateActorState(InitialActorState(
            name = input.id,
            type = ActorType.StaticNpc,
            position = Vector3f(player.position),
            modelLook = ModelLook.npc(0x864),
        ))
    }

    private fun drawBox() {
        val scaleX = boxX.value.toFloatOrNull() ?: return
        val scaleY = boxY.value.toFloatOrNull() ?: return
        val scaleZ = boxZ.value.toFloatOrNull() ?: return

        if (!boxLock.checked) {
            boxPosition.copyFrom(ActorStateManager.player().position)
        }

        boxCurrentPos.value = "Vector3f(x=${boxPosition.x.toTruncatedString(2)}f,y=${boxPosition.y.toTruncatedString(2)}f,z=${boxPosition.z.toTruncatedString(2)}f)"

        BoxDrawingTool.enqueue(
            box = AxisAlignedBoundingBox.scaled(
                scale = Vector3f(scaleX, scaleY, scaleZ) * 2f,
                position = boxPosition,
                verticallyCentered = false
            ),
            color = ByteColor(80, 0, 0, 40),
        )
    }

    private fun output() {
        val scene = SceneManager.getCurrentScene()
        val boxSize = Vector3f(boxX.value.toFloatOrNull() ?: 0f, 0f, boxZ.value.toFloatOrNull() ?: 0f)

        val out = """
            = BattleLocation(
                startingPosition = ZoneConfig(zoneId = ${scene.config.zoneId}, startPosition = ${toString(vSpawn)}),
                entrancePosition = ${toString(vEntrance)},
                entranceLook = ModelLook.npc(0x975),
                exitPosition = ${toString(vExit)},
                exitLook = ModelLook.npc(0x9BC),
                treasureChestPosition = ${toString(vTreasure)},
                spawnerArea = SpawnArea(${toString(boxPosition)}, ${toString(boxSize)}), 
                pathingSettings = PathingSettings(${toString(boxPosition)}, radius = 30f),
                boundaries = listOf(
                    EncompassingSphere(center = ${toString(boxPosition)}, radius = 30f)
                ),
            )
        """.trimIndent()

        window.navigator.clipboard.writeText(out)
    }

    private fun toString(v: Vector3f): String {
        return "Vector3f(x=${v.x.toTruncatedString(2)}f,y=${v.y.toTruncatedString(2)}f,z=${v.z.toTruncatedString(2)}f)"
    }

    private fun generatePath() {
        autoPathGen = BattleLocationPather.generateNavigator(PathingSettings(ActorStateManager.player().position, 50f))
        autoPathGen?.printGrid()
    }

    private fun fromBattleLocationBoundary() {
        val navigator = GameV0.getNavigator() ?: return

        val before = window.performance.now()
        autoPathGen = BattleLocationPather.generateNavigator(navigator.pathingSettings)
        val after = window.performance.now()

        println("Pathing time: ${after - before}")
    }

    private fun getClickCollision(ray: Ray): Boolean {
        clickPosition = RayGridCollider.collideWithTerrain(ray = ray, ignoreHitWalls = true, maxSteps = 20)?.position
        clickCollisionInput.value = clickPosition?.let { toString(it) } ?: "Miss!"
        return false
    }

}