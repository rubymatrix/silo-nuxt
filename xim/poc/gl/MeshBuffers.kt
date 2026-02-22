package xim.poc.gl

import js.buffer.ArrayBuffer
import js.typedarrays.Float32Array
import js.typedarrays.Uint8Array
import web.gl.WebGL2RenderingContext.Companion.ARRAY_BUFFER
import web.gl.WebGL2RenderingContext.Companion.STATIC_DRAW
import web.gl.WebGLBuffer
import web.gl.WebGLVertexArrayObject
import xim.math.Vector3f
import xim.resource.*
import xim.util.interpolate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

enum class ZBiasLevel(val value: Int) {
    Normal(0), High(5), VeryHigh(10)
}

data class RenderState(
    val blendEnabled: Boolean = false,
    val blendFunc: BlendFunc = BlendFunc.Src_InvSrc_Add,
    val zBias: ZBiasLevel = ZBiasLevel.Normal,
    val discardThreshold: Float? = null,
    val useBackFaceCulling: Boolean = false,
    val depthMask: Boolean = true,
)

enum class BlendFunc {
    One_Zero,
    Src_InvSrc_Add,
    Src_One_Add,
    Src_One_RevSub,
    Zero_InvSrc_Add,
}

data class ByteColor(val r: Int, val g: Int, val b: Int, val a: Int) {
    companion object {
        val zero = ByteColor(0x00, 0x00, 0x00, 0x00)
        val half = ByteColor(0x80, 0x80, 0x80, 0x80)
        val opaqueR = ByteColor(0x80, 0x00, 0x00, 0x80)
        val opaqueG = ByteColor(0x00, 0x80, 0x00, 0x80)
        val opaqueB = ByteColor(0x00, 0x00, 0x80, 0x80)

        val grey = ByteColor(0x60, 0x60, 0x60, 0x80)
        val alpha25 = ByteColor(0x80, 0x80, 0x80, 0x20)
        val alpha50 = ByteColor(0x80, 0x80, 0x80, 0x40)
        val alpha75 = ByteColor(0x80, 0x80, 0x80, 0x60)

        fun interpolate(c0: ByteColor, c1: ByteColor, t: Float): ByteColor {
            return ByteColor(
                c0.r.interpolate(c1.r, t),
                c0.g.interpolate(c1.g, t),
                c0.b.interpolate(c1.b, t),
                c0.a.interpolate(c1.a, t),
            )
        }

    }

    constructor(color: Color): this(
        r = (color.r()*255f).roundToInt(),
        g = (color.g()*255f).roundToInt(),
        b = (color.b()*255f).roundToInt(),
        a = (color.a()*255f).roundToInt(),
    )

    fun toRgbaArray(): Float32Array {
        val arr = Float32Array(4)
        arr[0] = r / 255f
        arr[1] = g / 255f
        arr[2] = b / 255f
        arr[3] = a / 255f
        return arr
    }

    fun multiplyRGB(factor: Int): ByteColor {
        return ByteColor(r * factor, g * factor, b * factor, a)
    }

    fun multiply(factor: Int): ByteColor {
        return ByteColor(r * factor, g * factor, b * factor, a * factor)
    }

    fun clamp(max: Int): ByteColor {
        return ByteColor(r.coerceAtMost(max), g.coerceAtMost(max), b.coerceAtMost(max), a.coerceAtMost(max))
    }

    fun withVariance(amount: Int): ByteColor {
        return ByteColor(
            r = (r + Random.nextInt(amount*2) - amount).coerceIn(0x00, 0xFF),
            g = (g + Random.nextInt(amount*2) - amount).coerceIn(0x00, 0xFF),
            b = (b + Random.nextInt(amount*2) - amount).coerceIn(0x00, 0xFF),
            a = a
        )
    }

    override fun toString(): String {
        return "RGBA($r, $g, $b, $a)"
    }

    fun withAlpha(alpha: Int): ByteColor {
        return copy(a = alpha)
    }

}

data class MeshBuffer(val numVertices: Int,
                      val meshType: MeshType,
                      val textureStage0: TextureLink? = null,
                      val textureStage1: TextureLink? = null,
                      val textureStage2: TextureLink? = null,
                      val renderState: RenderState = RenderState(),
                      val skeletalMeshProperties: RenderProperties? = null,
                      val blendVertexPosition: Boolean = false,
                      private val glBuffer: WebGLBuffer) {

    var released = false

    private var vertexAttribObject: WebGLVertexArrayObject? = null

    fun release() {
        if (!released) {
            val webGl = GlDisplay.getContext()
            webGl.deleteBuffer(glBuffer)
            webGl.deleteVertexArray(vertexAttribObject)
        }
        released = true
    }

    fun getVertexAttribObject(provider: (WebGLBuffer) -> WebGLVertexArrayObject): WebGLVertexArrayObject {
        if (released) { throw IllegalStateException("Already released mesh buffer") }
        if (vertexAttribObject == null) { vertexAttribObject = provider.invoke(glBuffer) }
        return vertexAttribObject!!
    }

}

class GlBufferBuilder(numVertices: Int) {

    companion object {
        const val stride = 0x44
    }

    private val buffer = ArrayBuffer(numVertices * stride)
    private val floatView = Float32Array(buffer)
    private val byteView = Uint8Array(buffer)
    private var position = 0

    fun appendColor(colorMask: ByteColor) {
        appendByte(colorMask.r.toByte())
        appendByte(colorMask.g.toByte())
        appendByte(colorMask.b.toByte())
        appendByte(colorMask.a.toByte())
    }

    fun appendByte(b: Int) {
        appendByte(b.toByte())
    }

    fun appendByte(b: Byte) {
        byteView[position] = b
        position += 1
    }

    fun appendFloat(f: Float) {
        floatView[position/4] = f
        position += 4
    }

    fun appendVector3f(vector3f: Vector3f) {
        appendFloat(vector3f.x)
        appendFloat(vector3f.y)
        appendFloat(vector3f.z)
    }

    fun appendSkinnedMeshVertex(meshVertex: MeshVertex) {
        appendSkinnedMeshVertex(meshVertex.vertex, meshVertex.u, meshVertex.v, meshVertex.color)
    }

    fun appendSkinnedMeshVertex(vertex: Vertex, u: Float, v: Float, color: ByteColor) {
        appendVector3f(vertex.p0)
        appendVector3f(vertex.p1)
        appendVector3f(vertex.n0)
        appendVector3f(vertex.n1)
        appendFloat(u)
        appendFloat(v)
        appendFloat(vertex.joint0Weight)
        appendByte(vertex.jointIndex0 ?: 0)
        appendByte(vertex.jointIndex1 ?: 0)
        appendColor(color)

        // unused - alignment
        appendByte(0)
        appendByte(0)
    }

    fun appendZoneMeshVertex(vertex: ZoneMeshSection.Vertex, tangent: Vector3f) {
        appendVector3f(vertex.p0)
        appendVector3f(vertex.p1)
        appendVector3f(vertex.n0)
        appendVector3f(tangent)
        appendFloat(vertex.texCoordU)
        appendFloat(vertex.texCoordV)
        appendFloat(1.0f)
        appendByte(0)
        appendByte(0)

        appendColor(vertex.colorMask)

        // unused - alignment
        appendByte(0)
        appendByte(0)
    }

    fun appendParticleVertex(vertex: ParticleMeshVertex) {
        appendVector3f(vertex.position)
        appendVector3f(Vector3f.ZERO)
        appendVector3f(vertex.normal)
        appendVector3f(Vector3f.ZERO)
        appendFloat(vertex.texCoordU)
        appendFloat(vertex.texCoordV)
        appendFloat(1.0f)
        appendByte(0)
        appendByte(0)

        appendColor(vertex.colorMask)

        // unused - alignment
        appendByte(0)
        appendByte(0)
    }

    fun appendCollisionVertex(position: Vector3f, direction: Vector3f, color: ByteColor) {
        appendVector3f(position)
        appendVector3f(Vector3f.ZERO)
        appendVector3f(direction)
        appendVector3f(Vector3f.ZERO)
        appendFloat(0f)
        appendFloat(0f)
        appendFloat(1.0f)
        appendByte(0)
        appendByte(0)

        appendColor(color)

        // unused - alignment
        appendByte(0)
        appendByte(0)
    }

    fun appendQspNode(p0: Vector3f, p1: Vector3f, p2: Vector3f) {
        appendQspVertex(p0)
        appendQspVertex(p1)
        appendQspVertex(p2)
    }

    fun appendColored(p0: Vector3f, p1: Vector3f, p2: Vector3f, color: ByteColor) {
        appendColoredPosition(p0, color)
        appendColoredPosition(p1, color)
        appendColoredPosition(p2, color)
    }

    fun appendColoredPosition(position: Vector3f, byteColor: ByteColor) {
        appendVector3f(position)
        appendVector3f(Vector3f.ZERO)
        appendVector3f(Vector3f.UP)
        appendVector3f(Vector3f.ZERO)
        appendFloat(0f)
        appendFloat(0f)
        appendFloat(1.0f)
        appendByte(0)
        appendByte(0)

        appendColor(byteColor)

        // unused - alignment
        appendByte(0)
        appendByte(0)
    }

    fun appendSpriteSheetVertex(position: Vector3f, byteColor: ByteColor, texCoordU: Float, texCoordV: Float) {
        appendVector3f(position)
        appendVector3f(Vector3f.ZERO)

        appendVector3f(Vector3f.UP)
        appendVector3f(Vector3f.ZERO)

        appendFloat(texCoordU)
        appendFloat(texCoordV)

        appendFloat(1.0f)
        appendByte(0)
        appendByte(0)

        appendColor(byteColor)

        // unused - alignment
        appendByte(0)
        appendByte(0)
    }


    fun appendQspVertex(position: Vector3f) {
        val col = position.normalize()

        appendVector3f(position)
        appendVector3f(Vector3f.ZERO)
        appendVector3f(col)
        appendVector3f(Vector3f.ZERO)
        appendFloat(0f)
        appendFloat(0f)
        appendFloat(1.0f)
        appendByte(0)
        appendByte(0)

        val mask = ByteColor(abs(col.x * 255f).toInt(), abs(col.y * 255f).toInt(), abs(col.z * 255).toInt(), 0x40)
        appendColor(mask)

        // unused - alignment
        appendByte(0)
        appendByte(0)
    }


    fun build(): WebGLBuffer {
        if (position != buffer.byteLength) {
            throw IllegalStateException("Didn't fill the buffer? $position/${buffer.byteLength}")
        }

        val context = GlDisplay.getContext()
        val bufferId = context.createBuffer()!!

        context.bindBuffer(ARRAY_BUFFER, bufferId)
        context.bufferData(ARRAY_BUFFER, buffer, STATIC_DRAW)

        return bufferId
    }

    fun overwrite(bufferId: WebGLBuffer) {
        if (position != buffer.byteLength) { throw IllegalStateException("Didn't fill the buffer? $position/${buffer.byteLength}") }
        val context = GlDisplay.getContext()
        context.bindBuffer(ARRAY_BUFFER, bufferId)
        context.bufferSubData(ARRAY_BUFFER, dstByteOffset = 0, buffer)
    }

}
