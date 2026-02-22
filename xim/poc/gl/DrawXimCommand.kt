package xim.poc.gl

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.camera.DecalCamera
import xim.resource.*

class DecalOptions(
    val decalCamera: DecalCamera,
    val blendFunc: BlendFunc,
    val decalTexture: TextureLink? = null,
    val color: ByteColor = ByteColor.half,
)

data class DrawXimCommand(

    val meshes: List<MeshBuffer>,

    val fogParams: FogParams = FogParams.noOpFog,
    val lightingParams: LightingParams = LightingParams.noOpLighting,
    val pointLights: List<PointLight> = emptyList(),

    val skeleton: SkeletonInstance? = null,

    val modelTransform: Matrix4f? = null,
    val translate: Vector3f = Vector3f.ZERO,
    val rotation: Vector3f = Vector3f.ZERO,
    val scale: Vector3f = Vector3f(1f, 1f, 1f),
    val effectTransform: Matrix4f? = null,
    val effectColor: Color = Color.NO_MASK,

    val positionBlendWeight: Float = 0f,
    val uvTranslate: Vector3f = Vector3f.ZERO,
    val meshNum: Int? = null,
    val wrapEffect: ActorWrapEffect? = null,

    val decalOptions: DecalOptions? = null,
    val forceDisableDepthMask: Boolean = false,
)