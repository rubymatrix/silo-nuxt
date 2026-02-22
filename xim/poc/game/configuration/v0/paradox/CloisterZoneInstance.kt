package xim.poc.game.configuration.v0.paradox

import xim.math.Vector3f
import xim.poc.ActorManager
import xim.poc.SceneManager
import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.EngagedState
import xim.poc.game.configuration.*
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.ZoneLogic
import xim.poc.game.configuration.v0.constants.*
import xim.poc.game.configuration.v0.navigation.BattleLocationNavigator
import xim.poc.game.configuration.v0.navigation.BattleLocationPather
import xim.poc.game.configuration.v0.paradox.CloisterDefinitions.definitions
import xim.poc.game.configuration.v0.tower.Boundary
import xim.poc.game.configuration.v0.tower.BoundaryConditions
import xim.poc.game.configuration.v0.tower.PushWall
import xim.poc.game.configuration.v0.zones.PathingSettings
import xim.poc.gl.ByteColor
import xim.poc.tools.ZoneConfig
import xim.resource.DatId
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

class CloisterDefinition(
    val zoneId: Int,
    val monsterId: MonsterId,
    val startPosition: Vector3f,
    val enemyLocation: Vector3f,
    val crystalLocation: Vector3f,
    val facingDirection: Vector3f,
    val fightBoundary: Boundary,
    val fightRadius: Float = 30f,
)

private fun makeStandardCloister(zoneId: Int, monsterId: MonsterId): CloisterDefinition {
    return CloisterDefinition(
        zoneId = zoneId,
        monsterId = monsterId,
        startPosition = Vector3f(x = 19.11f, y = -4.18f, z = -35.99f),
        enemyLocation = Vector3f(x = 22f, y = -18.14f, z = 30f),
        crystalLocation = Vector3f(x = 21.19f, y = -4.18f, z = -37.03f),
        facingDirection = Vector3f.SouthWest,
        fightBoundary = PushWall(
            center = Vector3f(x = 10f, y = -22f, z = 15f),
            direction = Vector3f.NorthEast,
            condition = BoundaryConditions.playerHasEnmity
        )
    )
}

object CloisterDefinitions {

    val definitions = mapOf(
        CrystalType.Gales to makeStandardCloister(zoneId = 201, monsterId = mobGaruda_100_015),
        CrystalType.Storms to makeStandardCloister(zoneId = 202, monsterId = mobRamuh_100_025),
        CrystalType.Frost to makeStandardCloister(zoneId = 203, monsterId = mobShiva_100_020),
        CrystalType.Flames to makeStandardCloister(zoneId = 207, monsterId = mobIfrit_100_000),
        CrystalType.Tremors to makeStandardCloister(zoneId = 209, monsterId = mobTitan_100_005),
        CrystalType.Tides to makeStandardCloister(zoneId = 211, monsterId = mobLeviathan_100_010),
        CrystalType.Light to CloisterDefinition(zoneId = 170, monsterId = mobCarbuncle_100_030,
            startPosition = Vector3f(x=-346.00f,y=-50.00f,z=340.00f),
            crystalLocation = Vector3f(x=-336.80f,y=-52.50f,z=340f),
            enemyLocation = Vector3f(x=-380.00f,y=-52.59f,z=420.00f),
            fightBoundary = PushWall(Vector3f(x=-380.52f,y=-51.82f,z=376.50f), Vector3f.North, condition = BoundaryConditions.playerHasEnmity),
            fightRadius = 50f,
            facingDirection = Vector3f.South,
        ),
        CrystalType.Dark to CloisterDefinition(zoneId = 170, monsterId = mobFenrir_100_035,
            startPosition = Vector3f(x=-26.14f, y=12f, z=-20f),
            crystalLocation = Vector3f(x=-16.94f,y=9.50f,z=-20f),
            enemyLocation = Vector3f(x=-60.14f, y=9.41f, z=60f),
            fightBoundary = PushWall(Vector3f(x=-60.66f, y=10.18f, z=16.5f), Vector3f.North, condition = BoundaryConditions.playerHasEnmity),
            fightRadius = 50f,
            facingDirection = Vector3f.South,
        )
    )

    operator fun get(crystalType: CrystalType): CloisterDefinition {
        return definitions[crystalType] ?: throw IllegalStateException("No such definition: $crystalType")
    }

}

class CloisterZoneInstance(private val crystalType: CrystalType): ZoneLogic {

    companion object {
        fun matchToStartingPosition(zoneConfig: ZoneConfig): CrystalType? {
            return definitions.filter { it.value.zoneId == zoneConfig.zoneId }
                .minByOrNull { Vector3f.distance(it.value.startPosition, zoneConfig.startPosition!!) }
                ?.key
        }
    }

    private val definition = CloisterDefinitions[crystalType]

    private val floorEntities = ArrayList<FloorEntity>()
    private val enemy = AvatarEntity(definition)
    private val navigator: BattleLocationNavigator

    private var hasWarpedToStart = false

    init {
        floorEntities += enemy
        floorEntities += CloisterExit(definition.crystalLocation, crystalType)
        navigator = BattleLocationPather.generateNavigator(PathingSettings(root = definition.enemyLocation, radius = definition.fightRadius))
        ActorStateManager.player().position.copyFrom(definition.startPosition)
        if (crystalType == CrystalType.Dark) { SceneManager.getCurrentScene().openDoor(doorId = DatId("_4q0")) }
    }

    override fun update(elapsedFrames: Float) {
        floorEntities.forEach { it.update(elapsedFrames) }
        definition.fightBoundary.apply(ActorStateManager.player().position)
        enemy.promise.onReady(this::checkDead)
        navigator.update(elapsedFrames)
    }

    override fun cleanUp() {
        floorEntities.forEach { it.cleanup() }
    }

    override fun getEntryPosition(): ZoneConfig {
        return ZoneConfig(zoneId = definition.zoneId, startPosition = definition.startPosition)
    }

    override fun toNew(): ZoneLogic {
        return CloisterZoneInstance(crystalType)
    }

    override fun getCurrentNavigator(): BattleLocationNavigator {
        return navigator
    }

    override fun getMusic(): Int? {
        return if (enemy.promise.isObsolete()) { null } else { 193 }
    }

    private fun checkDead(actorState: ActorState) {
        if (!actorState.isDead()) { return }

        if (hasWarpedToStart) { return }
        hasWarpedToStart = true

        val script = listOf(
            RunOnceEventItem { ActorStateManager.player().setEngagedState(EngagedState.State.Disengaged) },
            WaitRoutine(5.seconds),

            ActorRoutineEventItem(fileTableIndex = 0x1395, actorId = ActorStateManager.playerId, eagerlyComplete = true),
            WaitRoutine(Fps.secondsToFrames(1.9f)),
            FadeOutEvent(1.seconds),
            WarpSameZoneEventItem(definition.startPosition),
            RunOnceEventItem { ActorManager.player().renderState.effectColor = ByteColor.zero },
            WaitRoutine(Fps.secondsToFrames(0.2f)),
            ActorRoutineEventItem(fileTableIndex = 0x1396, actorId = ActorStateManager.playerId, eagerlyComplete = true),
            FadeInEvent(1.seconds),
        )

        EventScriptRunner.runScript(EventScript(script))
    }

}