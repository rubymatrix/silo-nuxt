package xim.poc

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.ParticleType.*
import xim.poc.camera.CameraReference
import xim.poc.camera.DecalCamera
import xim.poc.gl.*
import xim.poc.gl.RenderState
import xim.poc.tools.ParticleGenTool
import xim.poc.tools.ZoneObjectTool
import xim.poc.tools.isCheckBox
import xim.resource.*
import xim.util.Timer

class DecalEffect(val id: DatId, val fbo: GLFrameBuffer, val blendFunc: BlendFunc, val worldSpacePosition: Vector3f)

class LensFlareEffect(val particle: Particle, val textureFactor: Color)

class PostEffects(val decalEffects: ArrayList<DecalEffect> = ArrayList(), val lensFlareEffects: ArrayList<LensFlareEffect> = ArrayList())

private class DecalContext(val decalMode: Boolean = false, val decalEffects: HashMap<ParticleGenerator, DecalEffect> = HashMap())

private enum class ParticleType {
    Normal,
    Decal,
    Haze,
}

object ParticleDrawer {

    private val memoizedLightingParams = HashMap<DatId?, LightingParams>()

    fun drawAllParticlesAndGetDecals(effectLighting: EffectLighting): PostEffects {
        val postEffects = PostEffects()

        memoizedLightingParams.clear()

        val particlesByType = EffectManager.getAllParticles().groupBy { getParticleType(it.particle) }

        Timer.time("drawParticles.$Normal") { drawParticles(particlesByType[Normal] ?: emptyList(), postEffects, effectLighting, Normal) }

        renderHazeTexture()
        Timer.time("drawParticles.$Haze") { drawParticles(particlesByType[Haze] ?: emptyList(), postEffects, effectLighting, Haze) }

        Timer.time("drawParticles.$Decal") { drawParticles(particlesByType[Decal] ?: emptyList(), postEffects, effectLighting, Decal) }

        FrameBufferManager.bindScreenBuffer()

        return postEffects
    }

    private fun getParticleType(particle: Particle): ParticleType {
        return if (particle.config.decalEffect) { Decal } else if (particle.isDistortion() || particle.config.hazeEffect) { Haze } else { Normal }
    }

    private fun drawParticles(particleContexts: List<ParticleContext>, postEffects: PostEffects, effectLighting: EffectLighting, mode: ParticleType) {
        val disableAlpha = isCheckBox("MagicAlpha")
        val disableLensFlare = !isCheckBox("lensFlareEnabled")
        val decalContext = DecalContext(mode == Decal)

        if (mode == Decal) {
            MainTool.drawer.setupXimParticle(viewCamera = DecalCamera(Vector3f.UP*10f), projectionCamera = CameraReference.getInstance())
        } else {
            MainTool.drawer.setupXimParticle(CameraReference.getInstance())
        }

        val cameraPosition = CameraReference.getInstance().getPosition()

        particleContexts.asSequence()
            .mapNotNull { toDrawCommand(it, cameraPosition = cameraPosition, effectLighting = effectLighting, disableAlpha = disableAlpha) }
            .sortedByDescending { toDrawPriority(it) }
            .forEach { drawParticle(it, disableLensFlare, postEffects, decalContext) }

        postEffects.decalEffects += decalContext.decalEffects.values
    }

    private fun drawParticle(it: DrawXimParticleCommand, disableLensFlare: Boolean, postEffects: PostEffects, decalContext: DecalContext) {
        if (it.particle.isLensFlare() && !disableLensFlare) {
            queryLensFlare(it, postEffects)
        } else if (it.particle.occlusionSettings != null && !disableLensFlare) {
            queryOcclusion(it, decalContext)
        } else {
            draw(it, decalContext)
        }
    }

    private fun toDrawCommand(particleContext: ParticleContext, cameraPosition: Vector3f, effectLighting: EffectLighting, disableAlpha: Boolean = false): DrawXimParticleCommand? {
        val particle = particleContext.particle

        ParticleGenTool.add(particle)

        if (!particle.hasMeshes()) { return null }
        if (!ParticleGenTool.isDrawingEnabled(particle)) { return null }
        if (particle.isExpired()) { return null }
        if (particle.drawDistanceCulled) { return null }

        if (particle.association is ActorAssociation) {
            if (!ActorManager.isVisible(particle.association.actor.id) && !ActorManager.isVisible(particle.association.context.primaryTargetId)) { return null }
        }

        if (particle.association is ActorAssociation) {
            val slot = particle.association.context.modelSlot
            val model = particle.association.actor.actorModel
            if (slot != null && model != null && model.getHiddenModelSlots().contains(slot)) { return null }
        }

        val pointLights = if (particle.association is ZoneAssociation) {
            particle.attachedPointLights.flatMap { effectLighting.getPointLights(it, characterMode = false) }
        } else {
            emptyList()
        }

        val renderState = if (disableAlpha) {
            RenderState(blendEnabled = false, blendFunc = BlendFunc.Src_InvSrc_Add)
        } else {
            RenderState(blendEnabled = true, blendFunc = particle.blendFunc)
        }

        val lightingParams = computeLightingParams(particle)

        val worldTransform = particle.getWorldSpaceTransform()
        val particleTransform = particle.getParticleSpaceOrientationTransform()
        val distanceFromCamera = Vector3f.distance(particle.getWorldSpacePosition(), cameraPosition)

        val textureFactor = particle.getColor().withMultipliedAlpha(particleContext.opacity)
        textureFactor.clamp()

        return DrawXimParticleCommand(
            particle = particle,
            lightingParams = lightingParams,
            particleTransform = particleTransform,
            worldTransform = worldTransform,
            billBoardType = particle.config.billBoardType,
            depthMask = particle.config.depthMask,
            ignoreTextureAlpha = particle.config.ignoreTextureAlpha,
            textureFactor = textureFactor,
            renderState = renderState,
            texStage0Translate = particle.texCoordTranslate,
            specularParams = particle.getSpecularParams(),
            distanceFromCamera = distanceFromCamera,
            pointLight = pointLights,
        )
    }

    private fun draw(command: DrawXimParticleCommand, decalContext: DecalContext) {
        if (decalContext.decalMode) { bindDecalEffect(decalContext, command.particle, command.worldTransform) }
        MainTool.drawer.drawXimParticle(command)
    }

    private fun bindDecalEffect(decalContext: DecalContext, particle: Particle, worldTransform: Matrix4f): DecalEffect? {
        var decalEffect = decalContext.decalEffects[particle.creator]

        if (decalEffect == null) {
            val buffer = FrameBufferManager.claimBuffer() ?: return null
            decalEffect = DecalEffect(particle.datId, buffer, particle.deferredBlendFunc, Vector3f())
            decalContext.decalEffects[particle.creator] = decalEffect
            decalEffect.fbo.bind().clear()
        } else {
            decalEffect.fbo.bind()
        }

        decalEffect.worldSpacePosition.copyFrom(worldTransform.getTranslationVector())
        worldTransform.zeroTranslationInPlace()

        return decalEffect
    }

    private fun computeLightingParams(particle: Particle): LightingParams {
        if (!particle.config.lightingEnabled && !particle.isFogEnabled()) {
            return LightingParams.noOpLighting
        }

        // TODO - for zone particles, check if it's in the sub-area
        val environmentId = particle.creator.def.environmentId
        var environmentLighting = memoizedLightingParams.getOrPut(environmentId ?: DatId.zero) { EnvironmentManager.getMainAreaTerrainLighting(environmentId) }

        if (particle.creator.attachment.isWorldSpaceParticleInShipScene()) {
            // For these particles, directional lighting is messed up. In game, it looks like they:
            // 1. Applied the inverse-transform to the direction (with the translation-component)
            // 2. Did not normalize the resulting direction (would be meaningless anyway due to the translation-component)
            // 3. Overwrite the y-component of the direction with the camera's view-direction's y-component (correcting the sign for sun/moon)
            // The result is that directional lighting is broken on the x and z axis, and changes wrt the camera's angle on the y-axis...
            // For now, just disable directional lighting instead of trying to implement this behavior.
            environmentLighting = environmentLighting.copy(lights = emptyList())
        }

        val fog = if (ZoneObjectTool.disableFogForDebug()) {
            LightingParams.noOpLighting.fog
        } else if (!particle.isFogEnabled()) {
            LightingParams.noOpLighting.fog
        } else if (particle.blendFunc == BlendFunc.Src_One_Add) {
            environmentLighting.fog.copy(color = ByteColor.zero)
        } else {
            environmentLighting.fog
        }

        val lighting = if (particle.config.lightingEnabled) {
            environmentLighting
        } else {
            LightingParams.noOpLighting
        }

        return LightingParams(lights = lighting.lights, ambientColor = lighting.ambientColor, fog = fog)
    }

    private fun toDrawPriority(command: DrawXimParticleCommand): Float {
        // This is a small hack, particularly for the sun-rise in [Mount Zhayolm].
        // [sun3] and [wlt1] have equivalent depth configurations, but [wlt1] is order-sensitive.
        // Giving a small boost to inv-src produces a more stable effect.
        val tieBreaker = if (command.particle.blendFunc == BlendFunc.Src_InvSrc_Add) { -0.01f } else { 0f }

        // The projection is reversed for camera-space particles
        val projectionBias = if (command.particle.config.localPositionInCameraSpace) {
            -command.particle.projectionBias.param0
        } else {
            command.particle.projectionBias.param0
        }

        // This is a very rough approximation
        return tieBreaker + command.distanceFromCamera + projectionBias +
                if (command.particle.config.lowPriorityDraw) {
                    // Arbitrarily chosen large value - needs to be less than 20,000 for [Bibiki Bay]'s ocean
                    10_000f
                } else if (command.particle.config.drawPriorityOffset) {
                    // It seems to give a small offset. Not sure if this value is supposed to be relative to something.
                    // It's needed for [Ro'Maeve]'s fountain effects.
                    -10f
                } else {
                    0f
                }
    }

    private fun renderHazeTexture() {
        FrameBufferManager.bindAndClearScreenHazeBuffer()
        MainTool.drawer.drawScreenBuffer(sourceBuffer = FrameBufferManager.getCurrentScreenBuffer(), destBuffer = FrameBufferManager.getHazeBuffer())
        FrameBufferManager.bindScreenBuffer()
    }

    private fun queryLensFlare(command: DrawXimParticleCommand, postEffects: PostEffects) {
        val previous = OcclusionManager.consumeQuery(command.particle)
        if (previous != null && previous > 0) { postEffects.lensFlareEffects += LensFlareEffect(command.particle, command.textureFactor) }

        // TODO - this isn't an accurate simulation.
        // In game, it seems to draw a fixed-size screen-space quad for the occlusion-test.
        // The opacity of the particle is relative to the percentage of pixels that pass the test.
        // However, WebGL only supports ANY_SAMPLES, so here, the effect is either on or off...
        val lensCommand = command.copy(colorMask = false, billBoardType = BillBoardType.XYZ)
        OcclusionManager.executeQuery(command.particle) { draw(lensCommand, DecalContext(false)) }
    }

    private fun queryOcclusion(command: DrawXimParticleCommand, decalContext: DecalContext) {
        val previous = OcclusionManager.consumeQuery(command.particle)
        if (previous != null && previous > 0) { draw(command, decalContext) }

        // TODO - this isn't an accurate simulation (see above)
        val occlusionSettings = command.particle.occlusionSettings ?: return
        val modifiedTransform = Matrix4f().scaleInPlace(occlusionSettings.size)

        val queryCommand = command.copy(colorMask = false, depthMask = false, particleTransform = modifiedTransform, projectionBias = ProjectionZBias(-1f, 0f))
        OcclusionManager.executeQuery(command.particle) { draw(queryCommand, DecalContext(false)) }
    }

}