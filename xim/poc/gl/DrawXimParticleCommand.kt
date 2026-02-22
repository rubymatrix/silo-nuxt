package xim.poc.gl

import xim.math.Matrix3f
import xim.math.Matrix4f
import xim.math.Vector2f
import xim.resource.*

class SpecularParams(
    val enabled: Boolean = false,
    val textureResource: TextureResource? = null,
    val color: Color = Color.ZERO,
    val specularTransform: Matrix3f = Matrix3f(),
)

data class DrawXimParticleCommand(

    val particle: Particle,
    val lightingParams: LightingParams,
    val pointLight: List<PointLight> = emptyList(),

    val texStage0Translate: Vector2f = Vector2f(),
    val particleTransform: Matrix4f = Matrix4f(),
    val worldTransform: Matrix4f = Matrix4f(),

    val distanceFromCamera: Float,

    val billBoardType: BillBoardType = BillBoardType.None,
    val depthMask: Boolean = false,
    val colorMask: Boolean = true,
    val ignoreTextureAlpha: Boolean = false,

    val projectionBias: ProjectionZBias = particle.projectionBias,
    val textureFactor: Color = Color.NO_MASK,
    val specularParams: SpecularParams? = null,
    val renderState: RenderState,
)