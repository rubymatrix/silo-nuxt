package xim.poc

import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.camera.CameraReference
import xim.poc.gl.*
import xim.poc.tools.ZoneNpcTool
import xim.poc.tools.ZoneObjectTool
import xim.resource.*
import xim.util.PI_f
import xim.util.toDegrees
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object ActorDrawer {

    private var frameCounter = 0f

    fun update(elapsedFrames: Float) {
        frameCounter += elapsedFrames
        if (frameCounter > 360f) { frameCounter -= 360f }
    }

    fun drawActor(actor: Actor, effectLighting: EffectLighting) {
        val actorModel = actor.actorModel ?: return

        if (!actor.isReadyToDraw()) { return }
        if (!actor.state.visible) { return }

        val effectLights = ArrayList<PointLight>()
        var lightingParams: LightingParams = EnvironmentManager.getModelLighting(SceneManager.getCurrentScene().getMainArea(), null)

        for (perAreaCollisionProperties in actor.getState().lastCollisionResult.collisionsByArea) {
            val zoneResource = perAreaCollisionProperties.key.getZoneResource()

            val lightingEnvId = perAreaCollisionProperties.value.firstOrNull { it.environmentId != null }?.environmentId
            if (lightingEnvId != null) { lightingParams = EnvironmentManager.getModelLighting(perAreaCollisionProperties.key, lightingEnvId) }

            effectLights.addAll(perAreaCollisionProperties.value.asSequence()
                .flatMap { it.lightIndices }
                .distinct()
                .map { zoneResource.pointLightLinks[it] }
                .flatMap { effectLighting.getPointLights(it, characterMode = true) })
        }

        val fog = if (ZoneObjectTool.disableFogForDebug()) { FogParams.noOpFog } else { lightingParams.fog }

        val skeleton = actorModel.getSkeleton()
        if (skeleton == null) {
            drawFurniture(actor, actorModel, lightingParams, effectLights)
            return
        }

        val meshes = actorModel.getMeshes()

        val drawCommand = DrawXimCommand(
            meshes = meshes,
            lightingParams = lightingParams,
            fogParams = fog,
            skeleton = skeleton,
            translate = actor.displayPosition,
            pointLights = effectLights,
            effectColor = Color(actor.renderState.effectColor),
            wrapEffect = actor.renderState.wrapEffect,
        )

        val blurConfig = actorModel.getBlurConfig()
        if (blurConfig != null) { drawBlur(actor, blurConfig, drawCommand) }

        if (actorModel.customModelSettings.hideMain) { return }

        if (actor.isTranslucent()) {
            drawTranslucent(drawCommand)
        } else {
            MainTool.drawer.drawXimSkinned(drawCommand)
        }
    }

    private fun drawTranslucent(drawCommand: DrawXimCommand) {
        FrameBufferManager.bindBlurBuffer()
        MainTool.drawer.drawXimSkinned(drawCommand.copy(
            effectColor = drawCommand.effectColor.withAlpha(0.5f)
        ))

        val blitAlpha = Color.NO_MASK.withAlpha(drawCommand.effectColor.a())
        val options = DrawXimScreenOptions(colorMask = blitAlpha, blendEnabled = true)

        FrameBufferManager.bindScreenBuffer()
        MainTool.drawer.drawScreenBuffer(FrameBufferManager.getBlurBuffer(), FrameBufferManager.getCurrentScreenBuffer(), options = options)

        MainTool.drawer.setupXimSkinned(CameraReference.getInstance())
    }

    private fun drawBlur(actor: Actor, blurConfig: BlurConfig, drawCommand: DrawXimCommand) {
        val camera = CameraReference.getInstance()
        val cameraSpacePos = camera.getViewMatrix().transform(actor.displayPosition)
        if (cameraSpacePos.z > 0) { return }

        val fov = camera.getFoV() ?: return
        val zoomFactor = (abs(cameraSpacePos.z) + (fov.toDegrees() - 25f) / 5f).coerceAtLeast(16f)

        FrameBufferManager.bindBlurBuffer()
        MainTool.drawer.drawXimSkinned(drawCommand.copy(forceDisableDepthMask = true))

        FrameBufferManager.bindAndClearScreenHazeBuffer()
        MainTool.drawer.drawScreenBuffer(sourceBuffer = FrameBufferManager.getBlurBuffer(), destBuffer = FrameBufferManager.getHazeBuffer())

        FrameBufferManager.bindScreenBuffer()

        blurConfig.blurs.forEachIndexed { index, blur ->
            val color = Color(blur.color).withMultiplied(2f)
            val offset = getBlurOffset(index, blur, blurConfig, zoomFactor)
            val options = DrawXimScreenOptions(position = offset, colorMask = color, blendEnabled = true)
            MainTool.drawer.drawScreenBuffer(FrameBufferManager.getHazeBuffer(), FrameBufferManager.getCurrentScreenBuffer(), options = options)
        }

        MainTool.drawer.setupXimSkinned(CameraReference.getInstance())
    }

    private fun getBlurOffset(index: Int, blur: Blur, blurConfig: BlurConfig, zoomFactor: Float): Vector2f {
        val angle = 2 * PI_f * ((frameCounter / 360f) + (index.toFloat() / blurConfig.blurs.size.toFloat()))
        return Vector2f(x = cos(angle), y = sin(angle)) * (blur.offset / zoomFactor)
    }

    private fun drawFurniture(actor: Actor, actorModel: ActorModel, lightingParams: LightingParams, effectLights: ArrayList<PointLight>) {
        val model = actorModel.model
        if (model !is NpcModel) { return }

        val meshes = model.getMeshResources()
            .flatMap { it.collectByTypeRecursive(ZoneMeshResource::class) }
            .flatMap { it.meshes }

        val command = DrawXimCommand(
            meshes = meshes,
            translate = actor.displayPosition,
            rotation = Vector3f(0f, actor.displayFacingDir, 0f),
            lightingParams = lightingParams,
            pointLights = effectLights,
        )

        MainTool.drawer.setupXim(CameraReference.getInstance())
        MainTool.drawer.drawXim(command)
        MainTool.drawer.setupXimSkinned(CameraReference.getInstance())
    }

}