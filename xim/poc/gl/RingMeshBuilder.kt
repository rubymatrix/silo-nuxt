package xim.poc.gl

import xim.math.Vector3f
import xim.resource.MeshType
import xim.resource.RingParams
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


object RingMeshBuilder {

    private val computedMeshes = HashMap<RingParams, List<MeshBuffer>>()

    fun buildMesh(ringParams: RingParams) : List<MeshBuffer> {
        return computedMeshes.getOrPut(ringParams) { buildMeshInternal(ringParams) }
    }

    private fun buildMeshInternal(ringParams: RingParams) : List<MeshBuffer> {
        if (ringParams.verticesPerLayer == 0) { return emptyList() }

        // Build positions
        val allPositions = ArrayList<ArrayList<Vector3f>>()
        for (i in 0 until ringParams.numLayers) {
            val radius = ringParams.layerRadius[i]

            val layerPositions = ArrayList<Vector3f>()
            allPositions.add(layerPositions)

            val thetaStep = (2* PI / ringParams.verticesPerLayer).toFloat()

            for (j in 0 until ringParams.verticesPerLayer) {
                val theta = thetaStep * j
                layerPositions.add(Vector3f(
                    x = radius * cos(theta),
                    y = -radius * sin(theta),
                    z = 0f,
                ))
            }
        }

        val meshes = ArrayList<MeshBuffer>()
        for (i in 0 until ringParams.numLayers - 1) {
            val numVertices = (ringParams.verticesPerLayer + 1) * 2
            val builder = GlBufferBuilder(numVertices)

            for (j in 0 until ringParams.verticesPerLayer) {
                builder.appendColoredPosition(allPositions[i][j], ringParams.layerColor[i])
                builder.appendColoredPosition(allPositions[i+1][j], ringParams.layerColor[i+1])
            }

            builder.appendColoredPosition(allPositions[i][0], ringParams.layerColor[i])
            builder.appendColoredPosition(allPositions[i+1][0], ringParams.layerColor[i+1])

            val glBuffer = builder.build()
            meshes.add(MeshBuffer(
                    numVertices = numVertices,
                    meshType = MeshType.TriStrip,
                    glBuffer = glBuffer,
                    textureStage0 = ringParams.textureLink,
            ))
        }

        return meshes
    }

}