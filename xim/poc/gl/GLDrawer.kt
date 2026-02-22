package xim.poc.gl

import js.typedarrays.Uint8Array
import web.gl.*
import web.gl.WebGL2RenderingContext.Companion.ARRAY_BUFFER
import web.gl.WebGL2RenderingContext.Companion.BACK
import web.gl.WebGL2RenderingContext.Companion.BLEND
import web.gl.WebGL2RenderingContext.Companion.BYTE
import web.gl.WebGL2RenderingContext.Companion.CCW
import web.gl.WebGL2RenderingContext.Companion.CULL_FACE
import web.gl.WebGL2RenderingContext.Companion.CW
import web.gl.WebGL2RenderingContext.Companion.DEPTH_TEST
import web.gl.WebGL2RenderingContext.Companion.FLOAT
import web.gl.WebGL2RenderingContext.Companion.FUNC_ADD
import web.gl.WebGL2RenderingContext.Companion.FUNC_REVERSE_SUBTRACT
import web.gl.WebGL2RenderingContext.Companion.LEQUAL
import web.gl.WebGL2RenderingContext.Companion.LESS
import web.gl.WebGL2RenderingContext.Companion.NEAREST
import web.gl.WebGL2RenderingContext.Companion.ONE
import web.gl.WebGL2RenderingContext.Companion.ONE_MINUS_SRC_ALPHA
import web.gl.WebGL2RenderingContext.Companion.POLYGON_OFFSET_FILL
import web.gl.WebGL2RenderingContext.Companion.REPEAT
import web.gl.WebGL2RenderingContext.Companion.RGBA
import web.gl.WebGL2RenderingContext.Companion.SRC_ALPHA
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_2D
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_MAG_FILTER
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_MIN_FILTER
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_WRAP_S
import web.gl.WebGL2RenderingContext.Companion.TEXTURE_WRAP_T
import web.gl.WebGL2RenderingContext.Companion.TRIANGLES
import web.gl.WebGL2RenderingContext.Companion.TRIANGLE_FAN
import web.gl.WebGL2RenderingContext.Companion.TRIANGLE_STRIP
import web.gl.WebGL2RenderingContext.Companion.UNIFORM_BUFFER
import web.gl.WebGL2RenderingContext.Companion.UNSIGNED_BYTE
import web.gl.WebGLRenderingContext.Companion.ZERO
import xim.math.Matrix3f
import xim.math.Matrix4f
import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.camera.Camera
import xim.poc.camera.CameraReference
import xim.poc.camera.DecalCamera
import xim.poc.camera.StaticCamera
import xim.resource.*
import xim.util.OnceLogger.warn
import xim.util.toRads
import kotlin.math.pow
import kotlin.math.sqrt

enum class Mode {
    None,
    Draw2d,
    DrawXim,
    DrawXimSkinned,
    DrawXimParticle,
    DrawXimDecal,
    DrawXimLensFlare,
}

class GLDrawer(
    private val webgl: WebGL2RenderingContext,
    private val programFactory: GLProgramFactory,
    private val shaderFactory: GLShaderFactory,
    private val screenSettingsSupplier: ScreenSettingsSupplier,
) : Drawer {

    private val viewMatrix = Matrix4f()
    private val modelMatrix = Matrix4f()
    private val projectionMatrix = Matrix4f()

    private val ximProgram: GLProgram
    private val ximSkinnedProgram: GLProgram
    private val ximParticleProgram: GLProgram
    private val ximUiProgram: GLProgram
    private val ximDecalProgram: GLProgram
    private val ximLensFlareProgram: GLProgram

    private val defaultTexture: WebGLTexture
    private val blackTexture: WebGLTexture
    private val blackCubemap: WebGLTexture

    var currentMode: Mode = Mode.None

    init {
        val vertShader = shaderFactory.getGlShader(XimUiShader.basicVertSource, "vert")
        val fragShader = shaderFactory.getGlShader(XimUiShader.basicFragSource, "frag")
        ximUiProgram = programFactory.getGLProgram(vertShader, fragShader)

        val ximVert = shaderFactory.getGlShader(XimShader.vertShader, "vert")
        val ximFrag = shaderFactory.getGlShader(XimShader.fragShader, "frag")
        ximProgram = programFactory.getGLProgram(ximVert, ximFrag)

        val ximSVert = shaderFactory.getGlShader(XimSkinnedShader.vertShader, "vert")
        val ximSFrag = shaderFactory.getGlShader(XimSkinnedShader.fragShader, "frag")
        ximSkinnedProgram = programFactory.getGLProgram(ximSVert, ximSFrag)

        val ximPVert = shaderFactory.getGlShader(XimParticleShader.vertShader, "vert")
        val ximPFrag = shaderFactory.getGlShader(XimParticleShader.fragShader, "frag")
        ximParticleProgram = programFactory.getGLProgram(ximPVert, ximPFrag)

        val ximDVert = shaderFactory.getGlShader(XimDecalShader.vertShader, "vert")
        val ximDFrag = shaderFactory.getGlShader(XimDecalShader.fragShader, "frag")
        ximDecalProgram = programFactory.getGLProgram(ximDVert, ximDFrag)

        val ximLVert = shaderFactory.getGlShader(XimLensFlareShader.vertShader, "vert")
        val ximLFrag = shaderFactory.getGlShader(XimLensFlareShader.fragShader, "frag")
        ximLensFlareProgram = programFactory.getGLProgram(ximLVert, ximLFrag)

        defaultTexture = makeSingleColorTexture(0x80)
        blackTexture = makeSingleColorTexture(0x00)
        blackCubemap = makeSingleColorCubeTexture(0x00)
    }

    private fun applyXimVertexAttribFormat() {
        (0 .. 8).forEach { webgl.enableVertexAttribArray(it) }

        webgl.vertexAttribPointer(0, 3, FLOAT, false, GlBufferBuilder.stride, 0)
        webgl.vertexAttribPointer(1, 3, FLOAT, false, GlBufferBuilder.stride, 12)
        webgl.vertexAttribPointer(2, 3, FLOAT, false, GlBufferBuilder.stride, 24)
        webgl.vertexAttribPointer(3, 3, FLOAT, false, GlBufferBuilder.stride, 36)
        webgl.vertexAttribPointer(4, 2,  FLOAT, false, GlBufferBuilder.stride, 48)
        webgl.vertexAttribPointer(5, 1,  FLOAT, false, GlBufferBuilder.stride, 56)
        webgl.vertexAttribPointer(6, 1,  BYTE, false, GlBufferBuilder.stride, 60)
        webgl.vertexAttribPointer(7, 1,  BYTE, false, GlBufferBuilder.stride, 61)
        webgl.vertexAttribPointer(8, 4, UNSIGNED_BYTE, true, GlBufferBuilder.stride, 62)
    }

    private fun applyXimUiVertexAttribFormat() {
        val locations = XimUiShader.getLocations(ximUiProgram, webgl)
        XimUiShader.getAttributeLocations(ximUiProgram, webgl).forEach { webgl.enableVertexAttribArray(it) }

        webgl.vertexAttribPointer(locations.position, 3, FLOAT, false, 24, 0)
        webgl.vertexAttribPointer(locations.textureCoords, 2, FLOAT, false, 24, 12)
        webgl.vertexAttribPointer(locations.vertexColor, 4, UNSIGNED_BYTE, true, 24, 20)
    }

    override fun drawXim(cmd: DrawXimCommand) {
        if (currentMode != Mode.DrawXim) { throw IllegalStateException("Wrong mode") }

        if (cmd.modelTransform != null) {
            modelMatrix.copyFrom(cmd.modelTransform)
        } else {
            modelMatrix.identity()
            modelMatrix.translateInPlace(cmd.translate.x, cmd.translate.y, cmd.translate.z)
            modelMatrix.rotateZYXInPlace(cmd.rotation.x, cmd.rotation.y, cmd.rotation.z)
            modelMatrix.scaleInPlace(cmd.scale.x, cmd.scale.y, cmd.scale.z)
        }

        if (cmd.effectTransform != null) {
            modelMatrix.multiplyInPlace(cmd.effectTransform)
        }

        val locations = XimShader.getLocations(ximProgram, webgl)
        ximProgram.setUniformMatrix4f(locations.uModelMatrix, modelMatrix.m)

        for (i in 0 until 4) {
            val pl = if (i >= cmd.pointLights.size) { UniformPointLightData.noOp } else { UniformPointLightData(cmd.pointLights[i]) }
            locations.pointLight[i].set(pl)
        }

        val diffuseLights = cmd.lightingParams.lights
        if (diffuseLights.size >= 2) {
            locations.diffuseLight1.set(UniformDiffuseLightData(diffuseLights[1]))
        } else {
            locations.diffuseLight1.set(UniformDiffuseLightData.noOp)
        }

        if (diffuseLights.size >= 1) {
            locations.diffuseLight0.set(UniformDiffuseLightData(diffuseLights[0]))
        } else {
            locations.diffuseLight0.set(UniformDiffuseLightData.noOp)
        }

        ximProgram.setUniform4f(locations.ambientLightColor, cmd.lightingParams.ambientColor.rgba)
        ximProgram.setUniform4f(locations.uColorMask, cmd.effectColor.rgba)

        locations.fog.set(UniformFogData(cmd.fogParams))

        for (mesh in cmd.meshes) {
            if (cmd.meshNum != null && cmd.meshes.indexOf(mesh) != cmd.meshNum) {
                continue
            }

            if (mesh.renderState.useBackFaceCulling) {
                // If an object is mirrored, then flip the winding
                val face = if (cmd.scale.x * cmd.scale.y * cmd.scale.z >= 0f) { CW } else { CCW }
                webgl.enable(CULL_FACE)
                webgl.frontFace(face)
                webgl.cullFace(BACK)
            } else {
                webgl.disable(CULL_FACE)
            }

            if (mesh.renderState.blendEnabled) {
                webgl.enable(BLEND)
                webgl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)
                webgl.blendEquation(FUNC_ADD)
                webgl.depthMask(false)
            } else {
                webgl.disable(BLEND)
                webgl.depthMask(mesh.renderState.depthMask)
            }

            if (mesh.renderState.discardThreshold != null) {
                ximProgram.setUniform(locations.discardThreshold, 0.375f)
            } else {
                ximProgram.setUniform(locations.discardThreshold, 0.0f)
            }

            if (mesh.blendVertexPosition) {
                ximProgram.setUniform(locations.positionBlendWeight, cmd.positionBlendWeight)
            } else {
                ximProgram.setUniform(locations.positionBlendWeight, 0f)
            }

            if (mesh.renderState.zBias != ZBiasLevel.Normal) {
                // FFXI uses zbias = 8
                webgl.enable(POLYGON_OFFSET_FILL)
                webgl.polygonOffset(mesh.renderState.zBias.value * -1f, 1f)
            } else {
                webgl.disable(POLYGON_OFFSET_FILL)
            }

            val diffuseTexture = getTextureOrDefault(mesh.textureStage0)
            ximProgram.bindTexture(locations.diffuseTexture, 0, diffuseTexture)

            val normalTexture = BumpMapLinks.getBumpMap(mesh.textureStage0?.name)
            if (normalTexture != null) {
                ximProgram.bindTexture(locations.normalTexture, 1, normalTexture.id())
                ximProgram.setUniform(locations.enableNormalTexture, 1f)
            } else {
                ximProgram.setUniform(locations.enableNormalTexture, 0f)
            }

            val drawType = when (mesh.meshType) {
                MeshType.TriStrip -> TRIANGLE_STRIP
                MeshType.TriMesh -> TRIANGLES
                MeshType.TriFan -> TRIANGLE_FAN
            }

            webgl.bindVertexArray(mesh.getVertexAttribObject(this::setupStandardVao))
            webgl.drawArrays(drawType, 0, mesh.numVertices)
        }
    }

    override fun setupXim(camera: Camera) {
        currentMode = Mode.DrawXim

        ximProgram.bind()

        webgl.enable(DEPTH_TEST)
        webgl.depthFunc(LESS)

        val width = screenSettingsSupplier.width
        val height = screenSettingsSupplier.height
        webgl.viewport(0, 0, width, height)

        val locations = XimShader.getLocations(ximProgram, webgl)

        projectionMatrix.copyFrom(camera.getProjectionMatrix())
        ximProgram.setUniformMatrix4f(locations.uProjMatrix, projectionMatrix.m)

        viewMatrix.copyFrom(camera.getViewMatrix())
        ximProgram.setUniformMatrix4f(locations.uViewMatrix, viewMatrix.m)

        webgl.depthMask(true)
    }

    override fun drawXimSkinned(cmd: DrawXimCommand) {
        if (currentMode != Mode.DrawXimSkinned) { throw IllegalStateException("Wrong mode") }
        val locations = XimSkinnedShader.getLocations(ximSkinnedProgram, webgl)

        if (cmd.modelTransform != null) {
            modelMatrix.copyFrom(cmd.modelTransform)
        } else {
            modelMatrix.identity()
            modelMatrix.translateInPlace(cmd.translate.x, cmd.translate.y, cmd.translate.z)
            modelMatrix.rotateZYXInPlace(cmd.rotation.x, cmd.rotation.y, cmd.rotation.z)
            modelMatrix.scaleInPlace(cmd.scale.x, cmd.scale.y, cmd.scale.z)
        }

        ximSkinnedProgram.setUniformMatrix4f(locations.uModelMatrix, modelMatrix.m)

        for (i in 0 until 4) {
            // In FFXI, point-lights are applied to actors in a weird way in order to integrate with shadow mechanics
            // The result is that point-lights have less effect on actors - simulate this by adding a constant attenuation factor
            val pl = if (i >= cmd.pointLights.size) {
                UniformPointLightData.noOp
            } else {
                UniformPointLightData(cmd.pointLights[i], constAttenuation = 0.5f)
            }
            locations.pointLights[i].set(pl)
        }

        val diffuseLights = cmd.lightingParams.lights
        if (diffuseLights.size >= 2) {
            locations.diffuseLight1.set(UniformDiffuseLightData(diffuseLights[1]))
        } else {
            locations.diffuseLight1.set(UniformDiffuseLightData.noOp)
        }

        if (diffuseLights.size >= 1) {
            locations.diffuseLight0.set(UniformDiffuseLightData(diffuseLights[0]))
        } else {
            locations.diffuseLight0.set(UniformDiffuseLightData.noOp)
        }

        locations.fog.set(UniformFogData(cmd.fogParams))

        ximSkinnedProgram.setUniform4f(locations.uColorMask, cmd.effectColor.rgba)

        val cubeMap = DirectoryResource.getGlobalTexture("cubemap spec    ") ?: return
        ximSkinnedProgram.bindCubeTexture(locations.uSpecularTexture, 2, cubeMap.textureReference.id())

        val wrapTexture = getTextureOrDefault(cmd.wrapEffect?.textureLink?.getIfPresent())
        ximSkinnedProgram.bindTexture(locations.uWrapTexture, 1, wrapTexture)

        val wrapColor = cmd.wrapEffect?.color ?: ByteColor.zero
        ximSkinnedProgram.setUniform4f(locations.uWrapTextureColor, wrapColor.toRgbaArray())

        val wrapOffset = cmd.wrapEffect?.uvTranslation ?: Vector2f()
        ximSkinnedProgram.setUniform2f(locations.uWrapTextureOffset, wrapOffset)

        XimSkinnedShader.setJointMatrices(ximSkinnedProgram, cmd.skeleton!!.joints, webgl)

        for (mesh in cmd.meshes) {
            if (cmd.meshNum != null && cmd.meshes.indexOf(mesh) != cmd.meshNum) { continue }

            val meshColor = Color(mesh.skeletalMeshProperties?.tFactor ?: ByteColor.half)
            ximSkinnedProgram.setUniform4f(locations.uEffectColor, meshColor.rgba)

            val depthMasked = if (mesh.renderState.blendEnabled) {
                webgl.enable(BLEND)
                webgl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)
                webgl.blendEquation(FUNC_ADD)
                false
            } else {
                webgl.disable(BLEND)
                true
            }

            webgl.depthMask(!cmd.forceDisableDepthMask && depthMasked)

            if (mesh.renderState.discardThreshold != null) {
                ximSkinnedProgram.setUniform(locations.uDiscardThreshold, mesh.renderState.discardThreshold)
            } else {
                ximSkinnedProgram.setUniform(locations.uDiscardThreshold, 0.0f)
            }

            if (mesh.skeletalMeshProperties?.specularHighlightEnabled == true) {
                ximSkinnedProgram.setUniform(locations.uComputeSpecular, 1.0f)
            } else {
                ximSkinnedProgram.setUniform(locations.uComputeSpecular, 0.0f)
            }

            val ambientMultiplier = mesh.skeletalMeshProperties?.ambientMultiplier ?: 1.0f
            val ambientColor = cmd.lightingParams.ambientColor.withMultiplied(ambientMultiplier).clamp()
            ximSkinnedProgram.setUniform4f(locations.ambientLightColor, ambientColor.rgba)

            if (mesh.renderState.zBias != ZBiasLevel.Normal) {
                // FFXI uses zbias = 8
                webgl.enable(POLYGON_OFFSET_FILL)
                webgl.polygonOffset(mesh.renderState.zBias.value * -1f, 1f)
            } else {
                webgl.disable(POLYGON_OFFSET_FILL)
            }

            val diffuseTexture = getTextureOrDefault(mesh.textureStage0)
            ximSkinnedProgram.bindTexture(locations.uDiffuseTexture, 0, diffuseTexture)

            val drawType = when (mesh.meshType) {
                MeshType.TriStrip -> TRIANGLE_STRIP
                MeshType.TriMesh -> TRIANGLES
                MeshType.TriFan -> TRIANGLE_FAN
            }

            webgl.bindVertexArray(mesh.getVertexAttribObject(this::setupStandardVao))
            webgl.drawArrays(drawType, 0, mesh.numVertices)
        }
    }

    override fun setupXimSkinned(camera: Camera) {
        if (currentMode == Mode.DrawXimSkinned) { return }
        currentMode = Mode.DrawXimSkinned

        ximSkinnedProgram.bind()

        webgl.enable(DEPTH_TEST)
        webgl.depthFunc(LESS)
        webgl.depthMask(true)

        val width = screenSettingsSupplier.width
        val height = screenSettingsSupplier.height
        webgl.viewport(0, 0, width, height)

        val locations = XimSkinnedShader.getLocations(ximSkinnedProgram, webgl)

        projectionMatrix.copyFrom(camera.getProjectionMatrix())
        ximSkinnedProgram.setUniformMatrix4f(locations.uProjMatrix, projectionMatrix.m)

        viewMatrix.copyFrom(camera.getViewMatrix())
        ximSkinnedProgram.setUniformMatrix4f(locations.uViewMatrix, viewMatrix.m)

        webgl.disable(CULL_FACE)
    }

    override fun drawXimParticle(cmd: DrawXimParticleCommand) {
        if (currentMode != Mode.DrawXimParticle) { throw IllegalStateException("Wrong mode") }

        if (cmd.renderState.blendEnabled) {
            webgl.enable(BLEND)
            when (cmd.renderState.blendFunc) {
                BlendFunc.One_Zero -> { webgl.blendFunc(ONE, ZERO); webgl.blendEquation(FUNC_ADD) }
                BlendFunc.Src_InvSrc_Add -> { webgl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA); webgl.blendEquation(FUNC_ADD) }
                BlendFunc.Src_One_Add -> { webgl.blendFunc(SRC_ALPHA, ONE); webgl.blendEquation(FUNC_ADD) }
                BlendFunc.Src_One_RevSub -> { webgl.blendFunc(SRC_ALPHA, ONE); webgl.blendEquation(FUNC_REVERSE_SUBTRACT) }
                BlendFunc.Zero_InvSrc_Add -> { webgl.blendFunc(ZERO, ONE_MINUS_SRC_ALPHA); webgl.blendEquation(FUNC_ADD) }
            }

            // Distortion particles always override the blendFunc, but not the blendEquation
            if (cmd.particle.isDistortion()) { webgl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA) }
        } else {
            webgl.disable(BLEND)
        }

        webgl.depthMask(cmd.depthMask)
        webgl.enable(DEPTH_TEST)
        webgl.depthFunc(LEQUAL)
        if (!cmd.colorMask) { webgl.colorMask(false, false, false, false) }

        val locations = XimParticleShader.getLocations(ximParticleProgram, webgl)

        val modelView = Matrix4f()
        cmd.worldTransform.multiply(cmd.particleTransform, modelMatrix)
        ximParticleProgram.setUniformMatrix4f(locations.uModelMatrix, modelMatrix.m)

        val zAxisCamera = if (cmd.particle.config.localPositionInCameraSpace) {
            StaticCamera(CameraReference.getInstance().getPosition(), Vector3f.NegZ, fov = 60f.toRads())
        } else {
            null
        }

        val particleViewMatrix = zAxisCamera?.getViewMatrix() ?: viewMatrix
        ximParticleProgram.setUniformMatrix4f(locations.uViewMatrix, particleViewMatrix.m)

        particleViewMatrix.multiply(modelMatrix, modelView)

        // Specular params
        if (cmd.specularParams != null && cmd.specularParams.enabled && cmd.specularParams.textureResource != null) {
            ximParticleProgram.setUniform(locations.uComputeSpecular, true)

            val specularTexture = cmd.specularParams.textureResource
            val (specular, highlight) = if (specularTexture.name.startsWith("cubemap")) {
                Pair(blackTexture, getTextureOrDefault(specularTexture))
            } else {
                Pair(getTextureOrDefault(specularTexture), blackCubemap)
            }

            ximParticleProgram.bindTexture(locations.uTextureSpecular, 2, specular)
            ximParticleProgram.bindCubeTexture(locations.uTextureHighlight, 3, highlight)

            ximParticleProgram.setUniformMatrix3f(locations.uSpecularMatrix, cmd.specularParams.specularTransform.m)
            ximParticleProgram.setUniform4f(locations.uSpecularColor, cmd.specularParams.color.rgba)

            val inverseModelViewMatrix = Matrix3f.truncate(modelView).invert().transpose()
            ximParticleProgram.setUniformMatrix3f(locations.uInvTransViewModelMatrix, inverseModelViewMatrix.m)
        } else {
            ximParticleProgram.setUniform(locations.uComputeSpecular, false)
        }

        // In FFXI, the bill-boarded particles don't strictly face the camera
        // It seems to just use the particle's local space
        // If the particle itself has any rotation, it must be retained
        when(cmd.billBoardType) {
            BillBoardType.XYZ -> {
                Matrix4f.lookAtNegZ.multiplyUpper3x3(cmd.particleTransform, modelView)
            }
            BillBoardType.XZ -> {
                val transform = Matrix4f()
                transform.m[4] = viewMatrix.m[4]
                transform.m[5] = viewMatrix.m[5]
                transform.m[6] = viewMatrix.m[6]

                val billboard = Matrix4f()
                transform.multiply(cmd.particleTransform, billboard)
                modelView.copyUpperLeft(billboard)
            }
            BillBoardType.Camera, BillBoardType.Movement, BillBoardType.MovementHorizontal -> {
                // the orientation doesn't negate the view-transform's orientation
            }
            BillBoardType.None -> { }
        }

        ximParticleProgram.setUniformMatrix4f(locations.uModelViewMatrix, modelView.m)

        val projection = Matrix4f().copyFrom(zAxisCamera?.getProjectionMatrix() ?: projectionMatrix)

        // See Notes[Sec 0x30] - this is a rough approximation
        val depthBias = if (cmd.particle.config.lowPriorityDraw) { -0.1f }  else if (cmd.particle.config.drawPriorityOffset) { -0.01f } else { cmd.projectionBias.param0 }
        val distance = Vector3f.distance(Vector3f.ZERO, modelView.getTranslationVector())

        val d = if (distance <= 30f) { distance } else { 30f + sqrt(distance - 30f) }
        val depthBiasScalingFactor = 0.5f.pow(d / 5f)
        projection.m[14] += (depthBias * 0.03f) * depthBiasScalingFactor

        ximParticleProgram.setUniformMatrix4f(locations.uProjMatrix, projection.m)

        ximParticleProgram.setUniform4f(locations.textureFactor, cmd.textureFactor.rgba)
        ximParticleProgram.setUniform(locations.ignoreTextureAlpha, cmd.ignoreTextureAlpha)

        ximParticleProgram.setUniform(locations.computeLighting, cmd.particle.config.lightingEnabled)
        if (cmd.particle.config.lightingEnabled) {
            val diffuseLights = cmd.lightingParams.lights
            locations.diffuseLight0.set(diffuseLights.getOrNull(0)?.let { UniformDiffuseLightData(it) } ?: UniformDiffuseLightData.noOp)
            locations.diffuseLight1.set(diffuseLights.getOrNull(1)?.let { UniformDiffuseLightData(it) } ?: UniformDiffuseLightData.noOp)
            ximParticleProgram.setUniform4f(locations.ambientLightColor, cmd.lightingParams.ambientColor.rgba)
            ximParticleProgram.setUniform(locations.computeLighting, 1f)

            val pl = cmd.pointLight.firstOrNull()?.let { UniformPointLightData(it) } ?: UniformPointLightData.noOp
            locations.pointLight.set(pl)
        }

        ximParticleProgram.setUniform(locations.computeFog, cmd.particle.config.fogEnabled)
        if (cmd.particle.config.fogEnabled) {
            locations.fog.set(UniformFogData(cmd.lightingParams.fog))
        }

        ximParticleProgram.setUniform2f(locations.textureStage0Translate, cmd.texStage0Translate)


        val hazeSwitch = if (cmd.particle.config.hazeEffect) { 1f } else if ( cmd.particle.isDistortion()) { 0.5f } else { 0.0f }
        ximParticleProgram.setUniform(locations.hazeEffect, hazeSwitch)

        if (hazeSwitch > 0f) {
            // TODO: this isn't correct for particles that are distortion, but not haze
            val previousTransform = Matrix4f().copyFrom(cmd.particle.previousFrameTransform ?: modelView)
                .translateInPlace(x = cmd.particle.hazeOffset.x)

            ximParticleProgram.setUniformMatrix4f(locations.previousFrameTransform, previousTransform.m)
            cmd.particle.previousFrameTransform = modelView
        }

        val particleBatch = cmd.particle.getParticleBatch()
        XimParticleShader.setBatchOffsets(particleBatch?.batchOffsets, webgl)

        // Draw meshes
        for (mesh in cmd.particle.getMeshes()) {
            val textureLink = if (cmd.particle.config.hazeEffect) { FrameBufferManager.getHazeBuffer().texture } else { mesh.textureStage0 }
            val textureStage0 = getTextureOrDefault(textureLink)
            ximParticleProgram.bindTexture(locations.textureStage0, 0, textureStage0)

            val threshold = if (cmd.renderState.blendFunc == BlendFunc.One_Zero) {
                0.0f
            } else if (mesh.renderState.discardThreshold != null) {
                0.375f
            } else if (cmd.depthMask) {
                0.01f
            } else {
                0.0f
            }

            ximParticleProgram.setUniform(locations.discardThreshold, threshold)

            val drawType = when( mesh.meshType ) {
                MeshType.TriStrip -> TRIANGLE_STRIP
                MeshType.TriMesh -> TRIANGLES
                MeshType.TriFan -> TRIANGLE_FAN
            }

            webgl.bindVertexArray(mesh.getVertexAttribObject(this::setupStandardVao))
            webgl.drawArraysInstanced(drawType, 0, mesh.numVertices, particleBatch?.batchCount ?: 1)
        }

        if (!cmd.colorMask) { webgl.colorMask(true, true, true, true) }
    }

    override fun setupXimParticle(viewCamera: Camera, projectionCamera: Camera) {
        currentMode = Mode.DrawXimParticle

        ximParticleProgram.bind()

        if (viewCamera is DecalCamera) {
            webgl.viewport(0, 0, GLFrameBuffer.size, GLFrameBuffer.size)
            projectionMatrix.copyFrom(projectionCamera.getProjectionMatrix(aspectRatio = 1f))
        } else {
            webgl.viewport(0, 0, screenSettingsSupplier.width, screenSettingsSupplier.height)
            projectionMatrix.copyFrom(projectionCamera.getProjectionMatrix())
        }

        val locations = XimParticleShader.getLocations(ximParticleProgram, webgl)
        ximParticleProgram.setUniformMatrix4f(locations.uProjMatrix, projectionMatrix.m)

        viewMatrix.copyFrom(viewCamera.getViewMatrix())

        webgl.disable(POLYGON_OFFSET_FILL)
        webgl.disable(CULL_FACE)

        ximParticleProgram.bindTexture(locations.uTextureSpecular, 2, blackTexture)
        ximParticleProgram.bindCubeTexture(locations.uTextureHighlight, 3, blackCubemap)

        webgl.bindBuffer(UNIFORM_BUFFER, locations.batchOffsetBuffer)
    }

    override fun drawXimUi(cmd: DrawXimUiCommand) {
        if (currentMode != Mode.Draw2d) { throw IllegalStateException("Wrong mode") }

        when (cmd.blendFunc) {
            BlendFunc.One_Zero -> { webgl.blendFunc(ONE, ZERO); webgl.blendEquation(FUNC_ADD) }
            BlendFunc.Src_InvSrc_Add -> { webgl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA); webgl.blendEquation(FUNC_ADD) }
            BlendFunc.Src_One_Add -> { webgl.blendFunc(SRC_ALPHA, ONE); webgl.blendEquation(FUNC_ADD) }
            BlendFunc.Src_One_RevSub -> { webgl.blendFunc(SRC_ALPHA, ONE); webgl.blendEquation(FUNC_REVERSE_SUBTRACT) }
            BlendFunc.Zero_InvSrc_Add -> { webgl.blendFunc(ZERO, ONE_MINUS_SRC_ALPHA); webgl.blendEquation(FUNC_ADD) }
        }

        val (clipLowerLeft, clipUpperRight) = if (cmd.clipSize == null) {
            Pair(Vector2f(0f, 0f), Vector2f(Float.MAX_VALUE, Float.MAX_VALUE))
        } else {
            val x0 = cmd.clipSize.x * cmd.scale.x
            val y0 = cmd.clipSize.y * cmd.scale.y
            val x1 = x0 + cmd.clipSize.z * cmd.scale.x
            val y1 = y0 + cmd.clipSize.w * cmd.scale.y
            Pair(Vector2f(x0, y0), Vector2f(x1, y1))
        }

        modelMatrix.identity()
        modelMatrix.scaleInPlace(cmd.scale.x, cmd.scale.y, 1f)
        modelMatrix.translateInPlace(cmd.position.x, cmd.position.y, 0f)
        modelMatrix.rotateZInPlace(cmd.rotation)
        modelMatrix.scaleInPlace(cmd.elementScale.x, cmd.elementScale.y, 1f)

        val perTextureOffsets = UiElementBuffer.allocate(cmd.uiElement)

        val locations = XimUiShader.getLocations(ximUiProgram, webgl)
        ximUiProgram.setUniformMatrix4f(locations.uModelMatrix, modelMatrix.m)
            .setUniform2f(locations.clipLowerLeft, clipLowerLeft.x, clipLowerLeft.y)
            .setUniform2f(locations.clipUpperRight, clipUpperRight.x, clipUpperRight.y)
            .setUniform4f(locations.colorMask, cmd.colorMask.rgba)

        for ((textureName, bufferOutput) in perTextureOffsets) {
            val texture = getTexture(textureName)
            val textureReference: TextureReference = if (texture == null) {
                warn("[UI] Couldn't find [${textureName}].")
                TextureReference(defaultTexture, 1, 1, "default ui")
            } else {
                texture.textureReference
            }

            ximUiProgram.bindTexture(locations.texture, 0, textureReference.id())

            webgl.drawArrays(TRIANGLES, first = bufferOutput.bufferOffset*6, count = bufferOutput.allocated*6)
        }
    }

    override fun setupXimUi() {
        currentMode = Mode.Draw2d

        ximUiProgram.bind()
        webgl.bindVertexArray(UiElementBuffer.getVao { applyXimUiVertexAttribFormat() })

        webgl.enable(BLEND)

        webgl.disable(DEPTH_TEST)
        webgl.disable(CULL_FACE)

        val screenWidth = screenSettingsSupplier.width
        val screenHeight = screenSettingsSupplier.height

        webgl.viewport(0, 0, screenWidth, screenHeight)

        projectionMatrix.identity()
        projectionMatrix.ortho(0f, screenWidth.toFloat(), screenHeight.toFloat(), 0f, -1f, 100f)

        val locations = XimUiShader.getLocations(ximUiProgram, webgl)
        ximUiProgram.setUniformMatrix4f(locations.uProjMatrix, projectionMatrix.m)

        viewMatrix.identity()
        ximUiProgram.setUniformMatrix4f(locations.uViewMatrx, viewMatrix.m)
    }

    override fun drawXimDecal(cmd: DrawXimCommand) {
        if (currentMode != Mode.DrawXimDecal) { throw IllegalStateException("Wrong mode") }
        val decalOptions = cmd.decalOptions ?: throw IllegalStateException("Decal options not provided")

        if (cmd.modelTransform != null) {
            modelMatrix.copyFrom(cmd.modelTransform)
        } else {
            modelMatrix.identity()
            modelMatrix.translateInPlace(cmd.translate.x, cmd.translate.y, cmd.translate.z)
            modelMatrix.rotateZYXInPlace(cmd.rotation.x, cmd.rotation.y, cmd.rotation.z)
            modelMatrix.scaleInPlace(cmd.scale.x, cmd.scale.y, cmd.scale.z)
        }

        if (cmd.effectTransform != null) {
            modelMatrix.multiplyInPlace(cmd.effectTransform)
        }

        val locations = XimDecalShader.getLocations(ximProgram, webgl)
        ximDecalProgram.setUniformMatrix4f(locations.uModelMatrix, modelMatrix.m)

        ximDecalProgram.setUniformMatrix4f(locations.uDecalProjMatrix, decalOptions.decalCamera.getProjectionMatrix().m)
        ximDecalProgram.setUniformMatrix4f(locations.uDecalViewMatrix, decalOptions.decalCamera.getViewMatrix().m)

        ximDecalProgram.setUniform4f(locations.textureFactor, Color(decalOptions.color).rgba)

        for (mesh in cmd.meshes) {
            if (mesh.renderState.blendEnabled) { continue }

            if (mesh.renderState.useBackFaceCulling) {
                // If an object is mirrored, then flip the winding
                val face = if (cmd.scale.x * cmd.scale.y * cmd.scale.z >= 0f) { CW } else { CCW }
                webgl.enable(CULL_FACE)
                webgl.frontFace(face)
                webgl.cullFace(BACK)
            } else {
                webgl.disable(CULL_FACE)
            }

            webgl.enable(BLEND)
            when (decalOptions.blendFunc) {
                BlendFunc.One_Zero -> { webgl.blendFunc(ONE, ZERO); webgl.blendEquation(FUNC_ADD) }
                BlendFunc.Src_InvSrc_Add -> { webgl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA); webgl.blendEquation(FUNC_ADD) }
                BlendFunc.Src_One_Add -> { webgl.blendFunc(SRC_ALPHA, ONE); webgl.blendEquation(FUNC_ADD) }
                BlendFunc.Src_One_RevSub -> { webgl.blendFunc(SRC_ALPHA, ONE); webgl.blendEquation(FUNC_REVERSE_SUBTRACT) }
                BlendFunc.Zero_InvSrc_Add -> { webgl.blendFunc(ZERO, ONE_MINUS_SRC_ALPHA); webgl.blendEquation(FUNC_ADD) }
            }

            if (mesh.blendVertexPosition) {
                ximDecalProgram.setUniform(locations.positionBlendWeight, cmd.positionBlendWeight)
            } else {
                ximDecalProgram.setUniform(locations.positionBlendWeight, 0f)
            }

            webgl.enable(POLYGON_OFFSET_FILL)
            webgl.polygonOffset(-1f, 1f)

            val diffuseTexture = getTextureOrDefault(decalOptions.decalTexture)
            ximDecalProgram.bindTexture(locations.diffuseTexture, 0, diffuseTexture)

            val drawType = when (mesh.meshType) {
                MeshType.TriStrip -> TRIANGLE_STRIP
                MeshType.TriMesh -> TRIANGLES
                MeshType.TriFan -> TRIANGLE_FAN
            }

            webgl.bindVertexArray(mesh.getVertexAttribObject(this::setupStandardVao))
            webgl.drawArrays(drawType, 0, mesh.numVertices)
        }
    }

    override fun setupXimDecal(worldCamera: Camera) {
        currentMode = Mode.DrawXimDecal

        ximDecalProgram.bind()

        webgl.enable(DEPTH_TEST)
        webgl.depthFunc(LESS)
        webgl.depthMask(false)

        val width = screenSettingsSupplier.width
        val height = screenSettingsSupplier.height
        webgl.viewport(0, 0, width, height)

        val locations = XimDecalShader.getLocations(ximDecalProgram, webgl)

        projectionMatrix.copyFrom(worldCamera.getProjectionMatrix())
        ximDecalProgram.setUniformMatrix4f(locations.uProjMatrix, projectionMatrix.m)

        viewMatrix.copyFrom(worldCamera.getViewMatrix())
        ximDecalProgram.setUniformMatrix4f(locations.uViewMatrix, viewMatrix.m)
    }

    override fun drawXimLensFlare(cmd: DrawXimCommand) {
        if (currentMode != Mode.DrawXimLensFlare) { throw IllegalStateException("Wrong mode") }
        val locations = XimLensFlareShader.getLocations(ximLensFlareProgram, webgl)

        modelMatrix.identity()
        modelMatrix.translateInPlace(cmd.translate.x, cmd.translate.y, cmd.translate.z)
        modelMatrix.rotateZYXInPlace(cmd.rotation.x, cmd.rotation.y, cmd.rotation.z)
        modelMatrix.scaleInPlace(cmd.scale.x, cmd.scale.y, cmd.scale.z)
        ximLensFlareProgram.setUniformMatrix4f(locations.uModelMatrix, modelMatrix.m)

        ximLensFlareProgram.setUniform4f(locations.textureFactor, cmd.effectColor.rgba)

        for (mesh in cmd.meshes) {
            if (cmd.meshNum != null && cmd.meshes.indexOf(mesh) != cmd.meshNum) {
                continue
            }

            val diffuseTexture = getTextureOrDefault(mesh.textureStage0)
            ximLensFlareProgram.bindTexture(locations.textureStage0, 0, diffuseTexture)

            val drawType = when (mesh.meshType) {
                MeshType.TriStrip -> TRIANGLE_STRIP
                MeshType.TriMesh -> TRIANGLES
                MeshType.TriFan -> TRIANGLE_FAN
            }

            webgl.bindVertexArray(mesh.getVertexAttribObject(this::setupStandardVao))
            webgl.drawArrays(drawType, 0, mesh.numVertices)
        }
    }

    override fun setupXimLensFlare() {
        currentMode = Mode.DrawXimLensFlare

        ximLensFlareProgram.bind()

        webgl.enable(BLEND)
        webgl.blendFunc(SRC_ALPHA, ONE)
        webgl.blendEquation(FUNC_ADD)

        webgl.disable(DEPTH_TEST)
        webgl.disable(CULL_FACE)

        val screenWidth = screenSettingsSupplier.width
        val screenHeight = screenSettingsSupplier.height

        webgl.viewport(0, 0, screenWidth, screenHeight)

        projectionMatrix.identity()
        projectionMatrix.ortho(0f, screenWidth.toFloat(), screenHeight.toFloat(), 0f, -1f, 100f)

        val locations = XimLensFlareShader.getLocations(ximLensFlareProgram, webgl)
        ximLensFlareProgram.setUniformMatrix4f(locations.uProjMatrix, projectionMatrix.m)

        viewMatrix.identity()
    }

    override fun drawScreenBuffer(sourceBuffer: GLScreenBuffer, destBuffer: GLScreenBuffer?, options: DrawXimScreenOptions) {
        currentMode = Mode.Draw2d

        ximUiProgram.bind()
        webgl.bindVertexArray(UiElementBuffer.getVao { applyXimUiVertexAttribFormat() })

        if (options.blendEnabled) {
            webgl.enable(BLEND)
            webgl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)
        } else {
            webgl.disable(BLEND)
        }

        webgl.disable(DEPTH_TEST)
        webgl.disable(CULL_FACE)

        val srcTexture = getTexture(sourceBuffer.name) ?: return
        val dummyElement = UiElement.basic1x1Flipped(srcTexture.name, uvWidth = srcTexture.textureReference.width, uvHeight = srcTexture.textureReference.height)

        val (screenWidth, screenHeight) = if (destBuffer == null) {
            Pair(srcTexture.textureReference.width, srcTexture.textureReference.height)
        } else {
            val destTexture = getTexture(destBuffer.name) ?: return
            Pair(destTexture.textureReference.width, destTexture.textureReference.height)
        }

        webgl.viewport(0, 0, screenWidth, screenHeight)

        projectionMatrix.identity()
        projectionMatrix.ortho(0f, screenWidth.toFloat(), screenHeight.toFloat(), 0f, -1f, 100f)

        val locations = XimUiShader.getLocations(ximUiProgram, webgl)
        ximUiProgram.setUniformMatrix4f(locations.uProjMatrix, projectionMatrix.m)

        viewMatrix.identity()
        ximUiProgram.setUniformMatrix4f(locations.uViewMatrx, viewMatrix.m)

        drawXimUi(DrawXimUiCommand(
            uiElement = dummyElement,
            position = options.position,
            colorMask = options.colorMask,
            scale = Vector2f(1f, 1f),
            elementScale = Vector2f(screenWidth.toFloat(), screenHeight.toFloat())
        ))

        currentMode = Mode.None
    }

    override fun finish() {
        currentMode = Mode.None
        webgl.depthMask(true)
    }

    private fun setupStandardVao(vbo: WebGLBuffer): WebGLVertexArrayObject {
        val vao = webgl.createVertexArray()!!
        webgl.bindVertexArray(vao)

        webgl.bindBuffer(ARRAY_BUFFER, vbo)
        applyXimVertexAttribFormat()

        return vao
    }

    private fun makeSingleColorTexture(fill: Int): WebGLTexture {
        val context = GlDisplay.getContext()
        val textureId = context.createTexture() ?: throw IllegalStateException()

        context.bindTexture(TEXTURE_2D, textureId)
        context.texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, NEAREST as GLint)
        context.texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, NEAREST as GLint)
        context.texParameteri(TEXTURE_2D, TEXTURE_WRAP_S, REPEAT as GLint)
        context.texParameteri(TEXTURE_2D, TEXTURE_WRAP_T, REPEAT as GLint)

        val data = Uint8Array(4)
        for (i in 0 until data.length) { data[i] = fill.toByte() }

        context.texImage2D(
            target = TEXTURE_2D,
            level = 0,
            internalformat = RGBA as GLint,
            width = 1,
            height = 1,
            border = 0,
            format = RGBA,
            type = UNSIGNED_BYTE,
            pixels = data
        )

        return textureId
    }

    private fun makeSingleColorCubeTexture(fill: Int): WebGLTexture {
        val context = GlDisplay.getContext()
        val textureId = context.createTexture() ?: throw IllegalStateException()

        context.bindTexture(WebGL2RenderingContext.TEXTURE_CUBE_MAP, textureId)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_CUBE_MAP, TEXTURE_MAG_FILTER, WebGL2RenderingContext.LINEAR as GLint)
        context.texParameteri(WebGL2RenderingContext.TEXTURE_CUBE_MAP, TEXTURE_MIN_FILTER, WebGL2RenderingContext.LINEAR as GLint)

        val blankData = Uint8Array(4)
        for (i in 3 until blankData.length step 4) { blankData[i] = fill.toByte() }

        val blankFaces = listOf(
            WebGL2RenderingContext.TEXTURE_CUBE_MAP_NEGATIVE_X,
            WebGL2RenderingContext.TEXTURE_CUBE_MAP_POSITIVE_X,
            WebGL2RenderingContext.TEXTURE_CUBE_MAP_NEGATIVE_Y,
            WebGL2RenderingContext.TEXTURE_CUBE_MAP_POSITIVE_Y,
            WebGL2RenderingContext.TEXTURE_CUBE_MAP_NEGATIVE_Z,
            WebGL2RenderingContext.TEXTURE_CUBE_MAP_POSITIVE_Z
        )

        for (face in blankFaces) {
            context.texImage2D(
                target = face,
                level = 0,
                internalformat = WebGL2RenderingContext.RGBA as GLint,
                width = 1,
                height = 1,
                border = 0,
                format = WebGL2RenderingContext.RGBA,
                type = WebGL2RenderingContext.UNSIGNED_BYTE,
                pixels = blankData
            )
        }

        return textureId
    }

    private fun getTexture(textureName: String): TextureResource? {
        return DirectoryResource.getGlobalTexture(textureName)
    }

    private fun getTextureOrDefault(textureLink: TextureLink?) : WebGLTexture {
        if (textureLink == null) {
            return defaultTexture
        }

        val textureResource = textureLink.getOrPut()
        if (textureResource != null) { return textureResource.textureReference.id() }

        return defaultTexture
    }

    private fun getTextureOrDefault(textureResource: TextureResource?): WebGLTexture {
        return textureResource?.textureReference?.id() ?: defaultTexture
    }

}
