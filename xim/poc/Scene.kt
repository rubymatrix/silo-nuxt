package xim.poc

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.AreaType.*
import xim.poc.FestivalHelper.checkFestivals
import xim.poc.browser.DatLoader
import xim.poc.browser.DatWrapper
import xim.poc.browser.GameKey
import xim.poc.browser.ParserContext
import xim.poc.camera.Camera
import xim.poc.game.*
import xim.poc.game.actor.components.DoorState
import xim.poc.game.actor.components.getDoorState
import xim.poc.game.event.DoorOpenEvent
import xim.poc.game.event.InitialActorState
import xim.poc.tools.ZoneConfig
import xim.poc.tools.ZoneObjectTool
import xim.poc.tools.isCheckBox
import xim.resource.*
import xim.resource.table.*
import xim.util.OnceLogger.error
import kotlin.js.Date

sealed interface SceneCollisionResult
object NoCollision: SceneCollisionResult
class ElevatorCollisionResult(val elevatorId: ActorId, val collisionProperty: CollisionProperty?): SceneCollisionResult
class TerrainCollisionResult(val collisionProperties: Map<Area, List<CollisionProperty>>): SceneCollisionResult

enum class AreaType { MainArea, SubArea, ShipArea }

/* accessCount is used to distinguish between left/right panels for a given door */
class ModelTransforms(val transforms: HashMap<Int, ModelTransform> = HashMap(), var accessCount: Int = 0)
data class ModelTransform(val translation: Vector3f = Vector3f(), val rotation: Vector3f = Vector3f())

class AreaTransform (val transform: Matrix4f, val inverseTransform: Matrix4f)

class Area(val resourceId: String, val root: DirectoryResource, val id: Int, val areaType: AreaType) {

    val modelTransforms = HashMap<DatId, ModelTransforms>()

    private val zoneResource = root.getFirstChildByTypeRecursively(ZoneResource::class) ?: throw IllegalStateException("[$resourceId][${root.id}] Failed to find a ZoneResource")
    private val interactions = root.collectByTypeRecursive(ZoneInteractionResource::class).flatMap { it.interactions }

    private var bumpMapResource: DatWrapper? = null
    private var loadedBumpMapResource = false

    val transform = Matrix4f()
    var invTransform = Matrix4f()

    fun getZoneResource(): ZoneResource {
        return zoneResource
    }

    fun getInteractions(): List<ZoneInteraction> {
        return interactions
    }

    fun getVisibleZoneObjects(camera: Camera, cullContext: CullContext, cullingTableIndex: Int?): Set<ZoneObjId>? {
        val zoneResource = getZoneResource()

        val collisionCullingTables = (ZoneObjectTool.cullingGroupOverride() ?: cullingTableIndex)
            ?.let { zoneResource.zoneCullingTables.getOrNull(it) }
            ?: emptySet()

        val visibleIds = Culler.getZoneObjects(camera, zoneResource, cullContext, collisionCullingTables)
        return visibleIds.ifEmpty { null }
    }

    fun getModelTransform(datId: DatId): ModelTransforms? {
        return modelTransforms[datId]
    }

    fun getModelTransformForRendering(datId: DatId): ModelTransform? {
        val transforms = modelTransforms[datId] ?: return null

        val access = transforms.accessCount
        transforms.accessCount += 1

        return transforms.transforms[access]
    }

    fun updateModelTransform(datId: DatId, index: Int, updater: (ModelTransform) -> Unit) {
        val transforms = modelTransforms.getOrPut(datId) { ModelTransforms() }
        val transform = transforms.transforms.getOrPut(index) { ModelTransform() }
        updater.invoke(transform)
    }

    fun getBumpMapDirectory(): DirectoryResource? {
        return bumpMapResource?.getAsResourceIfReady()
    }

    fun runEffectRoutine(datPath: List<DatId>): EffectRoutineInstance? {
        val datQueue = ArrayDeque(datPath)
        val resourceId = datQueue.removeLast()

        var currentDir = root
        while (datQueue.isNotEmpty()) { currentDir = currentDir.getNullableSubDirectory(datQueue.removeFirst()) ?: return null }

        val effectRoutineResource = currentDir.getNullableChildAs(resourceId, EffectRoutineResource::class) ?: return null
        return EffectManager.registerRoutine(ZoneAssociation(this), effectRoutineResource)
    }

    fun registerEffects() {
        val effectRoot = root.getNullableSubDirectory(DatId("data")) ?: root

        if (effectRoot.hasSubDirectory(DatId.effect)) {
            val effectDir = effectRoot.getSubDirectory(DatId.effect)
            registerEffectsRecursively(effectDir)
        }

        if (effectRoot.hasSubDirectory(DatId.model)) {
            val modelDir = effectRoot.getSubDirectory(DatId.model)
            registerEffectsRecursively(modelDir)
        }
    }

    private fun registerEffectsRecursively(directoryResource: DirectoryResource) {
        directoryResource.collectByTypeRecursive(EffectResource::class)
            .filter { it.particleGenerator.autoRun }
            .forEach { effect -> EffectManager.registerEffect(ZoneAssociation(this), effect) }

        directoryResource.collectByTypeRecursive(EffectRoutineResource::class)
            .filter { it.effectRoutineDefinition.autoRunHeuristic }
            .forEach { EffectManager.registerRoutine(ZoneAssociation(this), it) }
    }

    fun loadBumpMapResource() {
        if (loadedBumpMapResource) { return }
        loadedBumpMapResource = true

        val path = ZoneIdToResourceId.getBumpMapResourcePath(id, areaType) ?: return
        println("Loading bump-maps for [$areaType][$id] -> $path")
        bumpMapResource = DatLoader.load(path, ParserContext.optionalResource).onReady { it.getAsResource(); BumpMapLinks.clear() }
    }

}

private class SubAreaManager(initialSubArea: Int?, ids: List<Int>) {

    val subAreaIds = ids.filter { it != 0 }.toSet()
    val subAreas = HashMap<Int, Area>()

    private var activeSubArea: Int? = null

    init {
        subAreaIds.forEach { fetchSubArea(subAreaId = it, activate = it == initialSubArea) }
    }

    fun isFullyLoaded(): Boolean {
        return subAreaIds.size == subAreas.size
    }

    fun getLoadedSubArea(): Area? {
        return subAreas[activeSubArea]
    }

    fun update(subAreaId: Int?) {
        if (activeSubArea == subAreaId) { return }
        unloadCurrent()

        if (subAreaId == null) { return }

        println("Activating sub-area (0x${subAreaId.toString(0x10)})")
        activeSubArea = subAreaId
        subAreas[subAreaId]?.registerEffects()

        // This is necessary for NPC lighting, since lighting-links may come from the newly loaded sub-area collision
        FrameCoherence.clearCachedNpcCollision()
    }

    private fun unloadCurrent() {
        val current = getLoadedSubArea() ?: return
        activeSubArea = null

        println("Deactivating sub-area (0x${current.id.toString(0x10)})")
        EffectManager.clearEffects(ZoneAssociation(current))
    }

    private fun fetchSubArea(subAreaId: Int, activate: Boolean) {
        val path = ZoneIdToResourceId.getSubAreaResourcePath(subAreaId)

        println("Fetching sub-area (0x${subAreaId.toString(0x10)}) -> $path")

        DatLoader.load(path, parserContext = ParserContext(zoneResource = true)).onReady {
            subAreas[subAreaId] = Area(path, it.getAsResource(), subAreaId, SubArea)
            if (activate) { update(subAreaId) }
        }
    }

}

class Scene(val config: ZoneConfig, initialSubArea: Int?) {

    companion object {
        val collisionBoxSize = Vector3f(0.5f, 0.5f, 0.5f)
        val npcCollisionSize = Vector3f(0.1f, 0.1f, 0.1f)
        val interactionBoxSize = Vector3f(0.05f, 1f, 0.05f)
    }

    private lateinit var mainArea: Area

    private var shipArea: Area? = null
    private var shipRoute: Route? = null
    private var shipRouteProgress = 0f

    private lateinit var zoneNpcList: ZoneNpcList
    private lateinit var subAreaManager: SubAreaManager
    private val zoneNpcState = HashMap<ActorId, ActorPromise>()

    private var currentInteractions: Set<ZoneInteraction> = emptySet()
    private var subAreaId: Int? = initialSubArea

    init {
        val resource = ZoneIdToResourceId.getMainAreaResourcePath(config) ?: throw IllegalStateException("$config doesn't have a resource?")

        DatLoader.load(resource, parserContext = ParserContext(zoneResource = true)).onReady {
            mainArea = Area(resource, it.getAsResource(), config.zoneId, MainArea).also { area -> area.registerEffects() }
            maybeRegisterShipArea()

            val subAreaIds = mainArea.getInteractions().filter { z -> z.isSubArea() }.map { z -> z.param }
            subAreaManager = SubAreaManager(initialSubArea = initialSubArea, ids = subAreaIds)
        }

        ZoneNpcTableProvider.fetchNpcDat(config) {
            zoneNpcList = it
            populateNpcActors()
        }
    }

    fun isFullyLoaded(): Boolean {
        return this::mainArea.isInitialized && this::zoneNpcList.isInitialized
                && this::subAreaManager.isInitialized && subAreaManager.isFullyLoaded()
    }

    fun getMainArea() = mainArea

    fun getSubArea() = subAreaManager.getLoadedSubArea()

    fun getAreas() = listOfNotNull(mainArea, shipArea, getSubArea())

    fun isCurrentSubArea(subAreaId: Int): Boolean {
        return this.subAreaId == subAreaId
    }

    fun getMainAreaRootDirectory(): DirectoryResource {
        return mainArea.root
    }

    fun getNpcs(): ZoneNpcList {
        return zoneNpcList
    }

    fun getNpc(npcDatId: DatId): ActorPromise? {
        val npc = zoneNpcList.npcsByDatId[npcDatId] ?: return null
        return getNpc(npc.actorId)
    }

    fun getNpc(actorId: ActorId): ActorPromise? {
        return zoneNpcState[actorId]
    }

    private fun populateNpcActors() {
        for (npc in zoneNpcList.npcs) {
            try {
                createNpc(npc)
            } catch (e: Exception) {
                error("Failed to create npc $npc\n${e.message}")
            }
        }
    }

    private fun createNpc(npc: Npc) {
        val components = if (npc.info.datId?.isDoorId() == true) {
            listOf(DoorState())
        } else {
            emptyList()
        }

        zoneNpcState[npc.actorId] = GameEngine.submitCreateActorState(InitialActorState(
            name = npc.name,
            type = ActorType.StaticNpc,
            position = npc.info.position,
            modelLook = npc.info.look,
            rotation = npc.info.rotation,
            presetId = npc.actorId,
            npcInfo = npc.info,
            appearanceState = NpcTable.getDefaultAppearanceState(npc.info.look) ?: 0,
            targetable = npc.name.isNotBlank(),
            components = components,
        )).onReady {
            checkFestivals(it)
        }
    }

    fun update(elapsedFrames: Float) {
        subAreaManager.update(subAreaId)

        mainArea.modelTransforms.forEach { it.value.accessCount = 0 }

        if (!isCheckBox("pauseShip")) { updateShipRoute(elapsedFrames) }
    }

    fun getFirstZoneInteractionBySourceId(sourceId: DatId): ZoneInteraction? {
        return getZoneInteractions().firstOrNull { it.sourceId == sourceId }
    }

    fun getZoneInteractions(): List<ZoneInteraction> {
        return mainArea.getInteractions()
    }

    fun moveActor(actorState: ActorState, elapsedFrames: Float) : SceneCollisionResult {
        if (!actorState.shouldApplyMovement()) {
            return NoCollision
        }

        if (GameState.isDebugMode() && actorState.isPlayer() && MainTool.platformDependencies.keyboard.isKeyPressedOrRepeated(GameKey.DebugClip)) {
            actorState.position += actorState.velocity + Vector3f(0f, 0.1f * elapsedFrames, 0f)
            return NoCollision
        }

        val areas = getAreas()

        val collisionInteractions = if (actorState.isPlayer()) { getInteractionCollision() } else { emptyList() }

        val collisionSize = if (actorState.isStaticNpc()) { npcCollisionSize } else { collisionBoxSize }

        if (actorState.velocity.magnitudeSquare() > 1e-5f) {
            val context = CollisionContext(collisionSize = collisionSize, gravityPass = false, interactions = collisionInteractions)
            Collider.updatePosition(areas, actorState, actorState.velocity, context)
        }

        if (GameState.isDebugMode() && actorState.isPlayer() && MainTool.platformDependencies.keyboard.isKeyPressedOrRepeated(GameKey.DebugGravity)) {
            return NoCollision
        }

        val elevatorResult = checkElevatorInteraction(actorState)
        if (elevatorResult != null) {
            val (elevatorInteraction, elevatorState) = elevatorResult
            actorState.position.y = elevatorState.position.y

            val collisionProperty = mapInteractionToCollisionProperty(elevatorInteraction)
            return ElevatorCollisionResult(elevatorId = elevatorState.id, collisionProperty = collisionProperty)
        }

        if (actorState.isStaticNpc()) {
            return snapToNearestFloor(actorState)
        }

        if (actorState.lastCollisionResult.isInFreeFall()) {
            val offsetPosition = Vector3f(actorState.position).also { it.y -= 1f }
            if (getNearestFloorPosition(offsetPosition) == null) { return NoCollision }
        }

        val vertical = Vector3f(0f, 0.33f * elapsedFrames, 0f)
        val context = CollisionContext(collisionSize = collisionSize, gravityPass = true, interactions = collisionInteractions)
        val gravityTerrainCollision = Collider.updatePosition(areas, actorState, vertical, context)
        return if (gravityTerrainCollision.isEmpty()) { NoCollision } else { TerrainCollisionResult(gravityTerrainCollision) }
    }

    fun getNearestFloorPosition(position: Vector3f): Vector3f? {
        return getNearestFloor(position)?.position
    }

    private fun getNearestFloor(position: Vector3f): RayCollision? {
        return Collider.nearestFloor(position + Vector3f(0f, -0.01f, 0f))
    }

    private fun snapToNearestFloor(actorState: ActorState): SceneCollisionResult {
        val ray = Ray(origin = actorState.position + Vector3f(0f, -1f, 0f), direction = Vector3f.Y)
        val (area, collision) = Collider.nearestLocalCollision(ray) ?: return NoCollision
        actorState.position.copyFrom(collision.position)
        return TerrainCollisionResult(mapOf(area to listOf(collision.collisionProperty)))
    }

    fun getVisibleZoneObjects(camera: Camera, cullContext: CullContext) : Map<Area, Set<ZoneObjId>?> {
        return getAreas().associateWith {
            val cullingTableIndex = getCullingTableIndex(it, camera, cullContext)
            val areaTransform = getAreaTransform(it)
            val cullingCamera = if (areaTransform != null) { camera.transform(areaTransform) } else { camera }
            it.getVisibleZoneObjects(cullingCamera, cullContext, cullingTableIndex)
        }
    }

    private fun getCullingTableIndex(area: Area, camera: Camera, cullContext: CullContext): Int? {
        if (area != mainArea) { return null }

        return when (cullContext) {
            ZoneObjectCullContext -> {
                val ray = Ray(origin = camera.getPosition(), direction = Vector3f.Y)
                val (_, cameraFloorCollision) = Collider.nearestLocalCollision(ray = ray, ignoreHitWalls = true, includeInteractionCollision = true, mainAreaOnly = true) ?: return null
                cameraFloorCollision.collisionProperty.cullingTableIndex
            }
            is ShadowCullContext -> {
                val latestCollision = cullContext.actorState.lastCollisionResult.collisionsByArea
                latestCollision[mainArea]?.firstNotNullOfOrNull { it.cullingTableIndex }
            }
            DecalCullContext -> null
        }
    }

    fun checkInteractions(): ZoneInteraction? {
        val player = ActorStateManager.player()
        val interactions = checkInteractions(player) { true }.toSet()

        val newInteractions = interactions - currentInteractions
        currentInteractions = interactions

        manageSubArea(newInteractions)
        player.zone = player.zone?.copy(subAreaId = subAreaId)

        return shouldZone()
    }

    fun getModelTransformForRendering(area: Area, datId: DatId): ModelTransform? {
        val areaTransform = area.getModelTransformForRendering(datId)
        if (area == mainArea || areaTransform != null) { return areaTransform }
        return mainArea.getModelTransformForRendering(datId)
    }

    private fun shouldZone(): ZoneInteraction? {
        return currentInteractions.firstOrNull { it.isZoneLine() }
    }

    private fun manageSubArea(newInteractions: Set<ZoneInteraction>) {
        val subAreas = newInteractions.filter { it.isSubArea() }

        // Some zones have a large "clear" interaction that intersects with everything
        // There should be at most one intersection with a non-zero param
        val nonZeroSubAreaCount = subAreas.distinctBy { it.param }.count { it.param != 0 }
        if (nonZeroSubAreaCount > 1) { throw IllegalStateException("How can this be") }
        if (subAreas.isEmpty()) { return }

        if (subAreas.all { it.param == 0 }) {
            subAreaId = null
            return
        }

        val subAreaInteraction = if (subAreas.size == 1) { subAreas.first() } else { subAreas.first { it.param != 0 } }
        subAreaId = subAreaInteraction.param
    }

    private fun checkElevatorInteraction(actorState: ActorState): Pair<ZoneInteraction, ActorState>? {
        val interaction = checkInteractions(actorState) { it.sourceId.isElevatorId() }.firstOrNull() ?: return null
        val elevatorState = getNpc(interaction.sourceId)?.resolveIfReady() ?: return null
        return interaction to elevatorState
    }

    private fun interactsWith(actorBox: BoundingBox, zoneInteraction: ZoneInteraction): Boolean {
        return SatCollider.boxBoxOverlap(zoneInteraction.boundingBox, actorBox)
    }

    private fun maybeRegisterShipArea() {
        val modelDir = mainArea.root.getSubDirectory(DatId.model)

        val shipDir = modelDir.getNullableSubDirectory(DatId.ship) ?: return
        shipArea = Area(mainArea.resourceId, shipDir, mainArea.id, ShipArea)

        shipRoute = getMainAreaRootDirectory().getSubDirectory(DatId.model)
            .collectByType(RouteResource::class)
            .firstOrNull()
            ?.route

        if (shipRoute == null) { error("Ship area has no routes?") }

        println("Registered ship area! -> ${shipArea!!.getZoneResource().id}")
    }

    fun getAreaTransform(area: Area? = null): AreaTransform? {
        val ship = shipArea ?: return null
        if (area == shipArea) { return null }

        return AreaTransform(ship.transform, ship.invTransform)
    }

    fun setShipRoute(id: DatId) {
        shipRoute = getMainAreaRootDirectory().getNullableChildRecursivelyAs(id, RouteResource::class)?.route ?: return
        shipRouteProgress = 0f
    }

    fun setShipRouteProgress(progress: Float) {
        val route = shipRoute ?: return
        shipRouteProgress = route.totalLength * progress
        updateShipRoute(0f)
    }

    private fun updateShipRoute(elapsedFrames: Float) {
        val ship = shipArea ?: return
        val route = shipRoute ?: return

        shipRouteProgress += elapsedFrames

        val position = route.getPosition(shipRouteProgress)
        val yRotation = route.getFacingDir(shipRouteProgress)

        ship.transform.identity()
            .translateInPlace(position)
            .rotateYInPlace(yRotation)

        ship.invTransform.identity()
            .rotateYInPlace(-yRotation)
            .translateInPlace(position * -1f)
    }

    fun getInteractionCollision(): List<ZoneInteraction> {
        return getZoneInteractions().filter { needsCollision(it) }
    }

    fun mapInteractionToCollisionProperty(interaction: ZoneInteraction): CollisionProperty {
        val zoneObject = mainArea.getZoneResource().meshesByEffectLink[interaction.sourceId]

        return CollisionProperty(
            mapId = interaction.mapId,
            environmentId = zoneObject?.environmentLink,
            cullingTableIndex = zoneObject?.cullingTableIndex,
            terrainType = interaction.terrainType ?: TerrainType.Object,
            lightIndices = zoneObject?.pointLightIndex ?: emptyList(),
        )
    }

    private fun needsCollision(interaction: ZoneInteraction): Boolean {
        if (!interaction.isDoor()) { return false }
        val actor = getInteractionActor(interaction) ?: return true
        return !actor.getDoorState().open
    }

    fun getInteractionActor(interaction: ZoneInteraction): ActorState? {
        val npc = zoneNpcList.npcsByDatId[interaction.sourceId] ?: return null
        return ActorStateManager[npc.actorId]
    }

    fun openDoor(doorId: DatId, requester: ActorId? = null) {
        val doorState = getNpc(doorId)
        doorState?.onReady { GameEngine.submitEvent(DoorOpenEvent(sourceId = requester, doorId = doorId)) }
    }

    fun canFish(actorState: ActorState): Boolean {
        for (i in 2 .. 4) {
            val fishingPosition = actorState.position + actorState.getFacingDirection() * i.toFloat()
            val nearestFloor = getNearestFloor(fishingPosition) ?: continue

            val terrainType = nearestFloor.collisionProperty.terrainType
            if (terrainType == TerrainType.ShallowWater || terrainType == TerrainType.DeepWater) { return true }
        }

        return false
    }

    fun checkFishingInteractions(actorState: ActorState): ZoneInteraction? {
        return checkInteractions(actorState) { it.isFishingArea() }.firstOrNull()
    }

    private fun checkInteractions(actorState: ActorState, filter: (ZoneInteraction) -> Boolean): List<ZoneInteraction> {
        val boundingBox = BoundingBox.from(actorState.position, Vector3f(0f, actorState.rotation, 0f), interactionBoxSize, verticallyCentered = false)
        return getZoneInteractions().filter { filter.invoke(it) && interactsWith(boundingBox, it) }
    }

}

object SceneManager {

    private var currentScene: Scene? = null

    private var reloadRequested = false

    fun getCurrentScene(): Scene {
        return currentScene ?: throw IllegalStateException("Scene is not initialized")
    }

    fun getNullableCurrentScene(): Scene? {
        return currentScene
    }

    fun loadScene(config: ZoneConfig, initialSubArea: Int? = null) {
        if (currentScene != null) { throw IllegalStateException("Need to unload current scene first") }
        currentScene = Scene(config, initialSubArea)
    }

    fun isFullyLoaded(): Boolean {
        return currentScene?.isFullyLoaded() == true
    }

    fun unloadScene() {
        currentScene = null
        reloadRequested = false
    }

    fun requestReload() {
        reloadRequested = true
    }

    fun isReloadRequested(): Boolean {
        return reloadRequested
    }

}

object FestivalHelper {

    private val starlightFestivalLooks = setOf(0x4d8, 0x4d9, 0x4da, 0x4db, 0x4dc, 0x4dd, 0x4df, 0x4e0)
    val starlightFestivalActive by lazy { checkFestival(month = 11) }

    fun checkFestivals(actorState: ActorState) {
        val look = actorState.getBaseLook()
        if (look.type != 0) { return }

        if (starlightFestivalActive && starlightFestivalLooks.contains(look.modelId)) {
            actorState.disabled = false
            actorState.visible = true
        }
    }

    private fun checkFestival(month: Int): Boolean {
        return Date().getMonth() == month
    }

}