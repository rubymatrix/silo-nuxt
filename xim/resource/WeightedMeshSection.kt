package xim.resource

import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.browser.ParserContext
import xim.poc.gl.ByteColor
import xim.poc.gl.GlBufferBuilder
import xim.poc.gl.MeshBuffer
import xim.poc.gl.RenderState
import xim.util.OnceLogger
import kotlin.math.min

class WeightedChunk(val positions: ArrayList<Vector3f>, val normals: ArrayList<Vector3f>)

class WeightedMesh(
    val id: DatId,
    val textureName: String,
    val localDir: DirectoryResource,

    private val numPrimitives: Int,

    private val chunks: ArrayList<WeightedChunk>,

    private val colors: ArrayList<ByteColor>,
    private val texCoords: ArrayList<Vector2f>,

    private val positionIndices: ArrayList<Int>,
    private val normalIndices: ArrayList<Int>,
    private val enableAlphaDiscard: Boolean,
) {

    private var prevBuffer: MeshBuffer? = null

    private fun getWeightCount() = chunks.size

    fun getWeightedBuffer(weights: Array<Float>) : MeshBuffer {
        if (weights.size < getWeightCount()) { OnceLogger.warn("[$id] Not enough weights. Needed ${getWeightCount()} but found ${weights.size}") }
        prevBuffer?.release()
        prevBuffer = toMeshBuffer(weights)
        return prevBuffer!!
    }

    fun release() {
        prevBuffer?.release()
    }

    private fun toMeshBuffer(weights: Array<Float>): MeshBuffer {
        val normalizedWeights = FloatArray(size = min(weights.size, chunks.size))
        val usableWeights = weights.take(normalizedWeights.size)
        val weightSum = usableWeights.sum()

        if (weightSum > 1e-7f) {
            for (i in normalizedWeights.indices) { normalizedWeights[i] = usableWeights[i] / weightSum }
        } else {
            normalizedWeights[0] = 1f
        }

        val glBufferBuilder = GlBufferBuilder(numPrimitives * 3)

        for (i in 0 until numPrimitives * 3) {
            val position = Vector3f()
            val normal = Vector3f()

            chunks.forEachIndexed { idx, chunk ->
                position += chunk.positions[positionIndices[i]] * normalizedWeights.getOrElse(idx) { 0.0f }
                normal += chunk.normals[normalIndices[i]] * normalizedWeights.getOrElse(idx) { 0.0f }
            }

            normal.normalizeInPlace()

            val texCoord = texCoords[i]

            glBufferBuilder.appendParticleVertex(ParticleMeshVertex(
                position = position,
                normal = normal,
                colorMask = colors[i],
                texCoordU = texCoord.x,
                texCoordV = texCoord.y
            ))
        }

        return MeshBuffer(
            numVertices = numPrimitives * 3,
            meshType = MeshType.TriMesh,
            textureStage0 = TextureLink.of(textureName, localDir),
            glBuffer =  glBufferBuilder.build(),
            renderState = RenderState(discardThreshold = if (enableAlphaDiscard) { 0.375f } else { null })
        )
    }

}

class WeightedMeshSection(val sectionHeader: SectionHeader, val parserContext: ParserContext) : ResourceParser {

    lateinit var weightedMesh: WeightedMesh

    override fun getResource(byteReader: ByteReader): ParserResult {
        read(byteReader)
        val resource = WeightedMeshResource(sectionHeader.sectionId, weightedMesh)
        return ParserResult.from(resource)
    }

    private fun read(byteReader: ByteReader) {
        byteReader.offsetFromDataStart(sectionHeader, 0x0)

        val unk1 = byteReader.next16()
        if (unk1 != 1) { oops(byteReader, "$unk1") }

        val meshConfig = byteReader.next8()
        val enableAlphaDiscard = (meshConfig and 0x80) != 0
        val numWeightedSections = meshConfig and 0x0F

        if (numWeightedSections > 5) {
            OnceLogger.warn("[${sectionHeader.sectionId}] More weights than expected? $numWeightedSections @ $byteReader")
        }

        val offsetExtension = byteReader.next8() * 0x10000

        val numPositions = byteReader.next16()
        val numNormals = byteReader.next16()

        val indexOffset = byteReader.next16() + offsetExtension + sectionHeader.dataStartPosition
        val numPrimitives = byteReader.next16()
        val numVertices = numPrimitives * 3

        val colorOffset = byteReader.next16() + sectionHeader.dataStartPosition
        val uvOffset = byteReader.next16() + offsetExtension + sectionHeader.dataStartPosition

        val textureName = byteReader.nextString(0x10)

        val chunks = ArrayList<WeightedChunk>(numWeightedSections)

        for (i in 0 until numWeightedSections) {
            val chunk = WeightedChunk(positions = ArrayList(numPositions), normals = ArrayList(numNormals))
            chunks += chunk

            for (j in 0 until numPositions) {
                chunk.positions += byteReader.nextVector3f()
            }

            for (j in 0 until numNormals) {
                val packedNormals = byteReader.next32()

                val x = ((packedNormals shl 0x16) shr 0x16) / 512f
                val y = ((packedNormals shl 0x0C) shr 0x16) / 512f
                val z = ((packedNormals shl 0x02) shr 0x16) / 512f

                chunk.normals += Vector3f(x, y, z)
            }
        }

        byteReader.position = colorOffset
        val colorScaling = if (parserContext.zoneResource) { 2 } else { 1 }

        val colors = ArrayList<ByteColor>()
        for (i in 0 until numVertices) {
            colors.add(byteReader.nextBGRA().multiply(colorScaling).clamp(0xFF))
        }

        byteReader.position = uvOffset
        val uvs = ArrayList<Vector2f>()
        for (i in 0 until  numVertices) {
            uvs.add(byteReader.nextVector2f())
        }

        val positionIndices = ArrayList<Int>()
        val normalIndices = ArrayList<Int>()

        byteReader.position = indexOffset
        for (i in 0 until numVertices) {
            val positionIndex = byteReader.next16()
            if (positionIndex > numPositions) { oops(byteReader, "Position index too large $positionIndex") }
            positionIndices.add(positionIndex)
        }

        for (i in 0 until numVertices) {
            val normalIndex = byteReader.next16()
            if (normalIndex > numNormals) { oops(byteReader, "Normal index too large $normalIndex") }
            normalIndices.add(normalIndex)
        }

        weightedMesh = WeightedMesh(
            id = sectionHeader.sectionId,
            textureName = textureName,
            localDir = sectionHeader.localDir,
            numPrimitives = numPrimitives,
            chunks = chunks,
            colors = colors,
            texCoords = uvs,
            positionIndices = positionIndices,
            normalIndices = normalIndices,
            enableAlphaDiscard = enableAlphaDiscard
        )
    }

}
