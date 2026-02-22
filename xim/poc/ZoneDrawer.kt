package xim.poc

import xim.math.Matrix4f
import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.audio.AudioManager
import xim.poc.camera.Camera
import xim.poc.camera.CameraReference
import xim.poc.camera.DecalCamera
import xim.poc.game.ActorStateManager
import xim.poc.game.ActorType
import xim.poc.gl.BlendFunc
import xim.poc.gl.ByteColor
import xim.poc.gl.DecalOptions
import xim.poc.gl.DrawXimCommand
import xim.poc.tools.BoxCommand
import xim.poc.tools.BoxDrawingTool
import xim.poc.tools.ZoneObjectTool
import xim.resource.*
import xim.util.OnceLogger

object ZoneDrawer {

    fun drawZoneObjects(area: Area, zoneObjectFilter: Set<ZoneObjId>?, drawDistance: Float, camera: Camera, effectLighting: EffectLighting? = null) {
        val areaTransform = SceneManager.getCurrentScene().getAreaTransform(area)

        val zoneResource = area.getZoneResource()

        val hasDebugOverride = ZoneObjectTool.drawZoneDebugView(MainTool.drawer, area, zoneResource, effectLighting)
        if (hasDebugOverride) { return }

        for (zoneObj in zoneResource.objectDrawOrder) {
            // Objects that have an effect-link are not culled by frustum-culling or culling-tables (but they can be culled by distance)
            if (zoneObjectFilter != null && !zoneObjectFilter.contains(zoneObj.index) && zoneObj.effectLink == null) { continue }
            drawZoneObject(area = area, obj = zoneObj, zoneResource = zoneResource, envDrawDistance = drawDistance, camera = camera, areaTransform = areaTransform, effectLighting = effectLighting)
        }
    }

    fun drawZoneObject(area: Area, obj: ZoneObject, zoneResource: ZoneResource, envDrawDistance: Float, camera: Camera, areaTransform: AreaTransform? = null, meshNum: Int? = null, effectLighting: EffectLighting? = null, decal: DecalOptions? = null, debug: Boolean = false) {
        val objPosition = if (areaTransform != null) { areaTransform.inverseTransform.transform(obj.position) } else { obj.position }

        val distance = Vector3f.distance(ActorManager.player().displayPosition, objPosition)

        val drawDistanceCulled = distance > envDrawDistance || (obj.lowDefThreshold != 0f && distance > obj.lowDefThreshold)
        if (!ZoneObjectTool.disableDrawDistanceForDebug() && drawDistanceCulled) {
            return
        }

        if (obj.skipDuringDecalRendering && decal != null) {
            return
        }

        var effectTransform: Matrix4f? = null

        if (obj.effectLink != null) {
            if (decal != null) { return }

            if (obj.effectLink.id.isDoorId()) {
                val transform = SceneManager.getCurrentScene().getModelTransformForRendering(area, obj.effectLink.id) ?: ModelTransform()
                effectTransform = Matrix4f().translateInPlace(transform.translation).rotateZYXInPlace(transform.rotation)
            } else if (obj.effectLink.id.isElevatorId()) {
                val elevatorNpc = SceneManager.getCurrentScene().getNpcs().npcsByDatId[obj.effectLink.id]
                val elevatorActor = ActorStateManager[elevatorNpc?.actorId]
                if (elevatorActor != null) { obj.position.y = elevatorActor.position.y }
            } else {
                val result = obj.effectLink.getOrPut { EffectManager.getFirstEffectForAssociation(ZoneAssociation(area), it) }
                if (result != null) { return } else {
                    OnceLogger.warn("[${obj.id}] failed to resolve ${obj.effectLink.id}")
                }
            }
        }

        if (obj.fileIdLink != null && SceneManager.getCurrentScene().isCurrentSubArea(obj.fileIdLink)) {
            // Defer to the "real" object in the sub-area
            return
        }

        val meshResource = obj.resolveMesh(distance, area.getZoneResource().localDir)
        if (meshResource == null) {
            OnceLogger.error("[${obj.id}] Failed to resolve mesh-resource. Did it have an effect-link [${obj.effectLink?.id}] or file-link [${obj.fileIdLink}] instead?")
            return
        }

        if (meshResource.meshes.any { it.released }) {
            throw IllegalStateException("[${area.id.toString(0x10)}][${obj.id}] is already released")
        }

        if (meshResource.boundingBox1 == null) { return }

        if (obj.getPrecomputedBoundingBox() == null) {
            val objBoundingBox = obj.getBoundingBox(meshResource.boundingBox1)
            if (!camera.isVisible(objBoundingBox)) { return }
        }

        if (debug) { BoxDrawingTool.enqueue(BoxCommand(obj.getBoundingBox(meshResource.boundingBox1), ByteColor(0x80, 0x0, 0x0, 0x20))) }

        val pointLights = obj.pointLightIndex.mapNotNull {
            val pointLightId = zoneResource.pointLightLinks[it]
            effectLighting?.getPointLights(pointLightId, characterMode = false)
        }.flatten()

        val terrainLightingParams = EnvironmentManager.getTerrainLighting(area, obj)
        val fog = if (ZoneObjectTool.disableFogForDebug()) { FogParams.noOpFog } else { terrainLightingParams.fog }

        val transform = Matrix4f()
            .translateInPlace(obj.position)
            .rotateZYXInPlace(obj.rotation)
            .scaleInPlace(obj.scale)

        if (areaTransform != null) {
            areaTransform.inverseTransform.multiply(transform, transform)
        }

        val command = DrawXimCommand(
            meshes = meshResource.meshes,
            modelTransform = transform,
            translate = obj.position,
            rotation = obj.rotation,
            scale = obj.scale,
            effectTransform = effectTransform,
            meshNum = meshNum,
            fogParams = fog,
            lightingParams = terrainLightingParams,
            pointLights = pointLights,
            positionBlendWeight = WindFactor.getWindFactor(),
            decalOptions = decal,
        )

        if (decal != null) { MainTool.drawer.drawXimDecal(command) } else { MainTool.drawer.drawXim(command) }
    }

    fun drawActorShadows(drawDistance: Float) {
        MainTool.drawer.setupXimDecal(CameraReference.getInstance())

        for (actor in ActorManager.getVisibleActors()) {
            if (actor.actorModel?.getFootInfoDefinition() == null) { continue }
            if (actor.actorModel?.getSkeleton() == null) { continue }
            if (actor.getState().type == ActorType.Effect) { continue }
            if (actor.renderState.forceHideShadow) { continue }
            if (actor.getNpcInfo()?.hasShadow() == false) { continue }
            if (actor.getMount() != null) { continue }

            val color = ByteColor.half.copy(a = actor.renderState.effectColor.a)
            if (color.a == 0) { continue }

            val shadowCamera = DecalCamera(actor.displayPosition, projectionSize = Vector2f(0.33f, 0.33f))
            val decalObjectsByArea = FrameCoherence.getActorShadowObjects(actor.id) { SceneManager.getCurrentScene().getVisibleZoneObjects(shadowCamera, ShadowCullContext(actorState = actor.getState())) }

            val shadowOption = DecalOptions(shadowCamera, decalTexture = GlobalDirectory.shadowTexture, blendFunc = BlendFunc.Src_One_RevSub, color = color)
            decalObjectsByArea.forEach { drawAllZoneDecal(it.key, it.value ?: emptySet(), decalOptions = shadowOption, drawDistance = drawDistance, camera = shadowCamera) }
        }
    }

    fun drawDecalEffects(decalEffects: Collection<DecalEffect>, drawDistance: Float) {
        MainTool.drawer.setupXimDecal(CameraReference.getInstance())

        for (decalEffect in decalEffects) {
            val decalCamera = DecalCamera(decalEffect.worldSpacePosition, projectionSize = Vector2f(3.33f, 3.33f)) // Projection size is based on Carbuncle's summoning circle
            val decalOptions = DecalOptions(decalCamera, decalTexture = decalEffect.fbo.texture, blendFunc = decalEffect.blendFunc)
            val decalObjectsByArea = SceneManager.getCurrentScene().getVisibleZoneObjects(decalCamera, DecalCullContext)
            decalObjectsByArea.forEach { drawAllZoneDecal(it.key, it.value ?: emptySet(), decalOptions = decalOptions, drawDistance = drawDistance, camera = decalCamera) }
        }
    }


    private fun drawAllZoneDecal(area: Area, zoneObjectFilter: Set<ZoneObjId>, drawDistance: Float, camera: Camera, decalOptions: DecalOptions? = null) {
        val zoneResource = area.getZoneResource()

        for (zoneObjId in zoneObjectFilter) {
            val zoneObj = zoneResource.zoneObj[zoneObjId]
            drawZoneObject(area = area, obj = zoneObj, zoneResource = zoneResource, envDrawDistance = drawDistance, camera = camera, decal = decalOptions)
        }
    }

    fun emitFootSteps(actor: Actor, systemEffects: DirectoryResource, zoneDat: DirectoryResource) {
        if (actor.isDisplayInvisible() || actor.getState().lastCollisionResult.isInFreeFall()) { return }

        val collisionProperties = actor.getState().lastCollisionResult.collisionsByArea.flatMap { it.value }
        if (collisionProperties.isEmpty()) { return }

        val actorModel = actor.actorModel ?: return
        val movementInfo = actorModel.getFootInfoDefinition() ?: return

        if (actorModel.skeletonAnimationCoordinator.isTransitioning()) { return }

        val footTouching = actorModel.getCollidingFoot() ?: return
        val footTouchingJoint = actorModel.getSkeleton()?.getJoint(footTouching) ?: return

        val terrainType = collisionProperties[0].terrainType

        val actorContext = ActorContext(originalActor = actor.id, primaryTargetId = actor.id, joint = footTouchingJoint, terrainEffect = true)
        val actorAssociation = ActorAssociation(actor, actorContext)
        AudioManager.playFootEffect(actor, actorAssociation, terrainType, zoneDat.getSubDirectory(DatId.fses))

        if (movementInfo.movementType != MovementType.Walking) { return }

        if (terrainType.hasFootMark) {
            val footMarkEffect = systemEffects.getNullableChildRecursivelyAs(DatId("fmrk"), EffectResource::class) ?: return
            EffectManager.registerEffect(actorAssociation, footMarkEffect)
        }

        val footEffectDir = zoneDat.getSubDirectory(DatId.fses).getNullableSubDirectory(DatId.fefs) ?: return
        val footTerrainEffect = footEffectDir.getNullableChildAs(terrainType.toFootEffectId(), EffectResource::class) ?: return
        EffectManager.registerEffect(actorAssociation, footTerrainEffect)
    }

    fun drawLensFlares(effects: List<LensFlareEffect>) {
        MainTool.drawer.setupXimLensFlare()
        effects.forEach { drawLensFlare(it) }
    }

    private fun drawLensFlare(effect: LensFlareEffect) {
        val lineStart = effect.particle.getWorldSpacePosition().toScreenSpace() ?: return

        val toCenter = Vector2f(0.5f, 0.5f) - lineStart
        val lineEnd = lineStart + (toCenter*2f)

        val meshProvider = effect.particle.meshProvider
        if (meshProvider !is SpriteSheetMeshProvider) { return }

        val sss = MainTool.platformDependencies.screenSettingsSupplier
        val spriteSheet = meshProvider.spriteSheet

        val scale = Vector3f(sss.width / 32f, sss.height / 32f, 1f)

        for ((mesh, offset) in spriteSheet.meshes.zip(spriteSheet.offsets)) {

            val position = lineStart * (1f - offset) + lineEnd * offset
            val worldPosition = Vector2f(position.x * sss.width, position.y * sss.height)

            MainTool.drawer.drawXimLensFlare(DrawXimCommand(
                meshes = listOf(mesh),
                translate = Vector3f(worldPosition.x, worldPosition.y, 0f),
                scale = scale,
                effectColor = effect.textureFactor,
            ))
        }
    }

}