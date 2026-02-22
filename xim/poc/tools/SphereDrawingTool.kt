package xim.poc.tools

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.gl.*
import xim.resource.MeshType
import kotlin.math.PI

object SphereDrawingTool {

    private class DrawSphereCommand(val center: Vector3f, val radius: Float, val color: ByteColor, val transform: Matrix4f? = null)

    private val commands = ArrayList<DrawSphereCommand>()
    private val meshes by lazy { makeSphereMesh() }

    fun drawSphere(center: Vector3f, radius: Float, color: ByteColor, transform: Matrix4f? = null) {
        commands += DrawSphereCommand(center, radius, color, transform)
    }

    fun render(drawer: Drawer) {
        commands.forEach { render(drawer, it) }
        commands.clear()
    }

    private fun render(drawer: Drawer, command: DrawSphereCommand) {
        val translate = if (command.transform != null) {
            command.transform.transform(command.center)
        } else {
            command.center
        }

        val scale = if (command.transform != null) {
            command.transform.transform(Vector3f.ONE * command.radius, w = 0f)
        } else {
            Vector3f.ONE * command.radius
        }

        drawer.drawXim(DrawXimCommand(
            meshes = meshes,
            scale = scale,
            translate = translate,
            effectColor = Color(command.color),
        ))
    }

    private fun makeSphereMesh(): List<MeshBuffer> {
        val rotationY = ArrayList<Matrix4f>()
        val spokes = 32

        val thetaStep = (2 * PI / spokes).toFloat()
        for (i in 0 until spokes) {
            val theta = thetaStep * i
            rotationY.add(Matrix4f().rotateYInPlace(theta))
        }

        val layers = ArrayList<ArrayList<Vector3f>>()
        val elevations = (0 .. 30).map { -1f + it/15f }

        for (i in elevations.indices) {
            val layer = ArrayList<Vector3f>(spokes)
            layers.add(layer)

            // 0 -> 0; 1 -> 0.5 PI
            val phi = -0.5f * PI.toFloat() * elevations[i]
            val rotationZ = Matrix4f().rotateZInPlace(phi)

            for (j in 0 until spokes) {
                val position = Vector3f(1f, 0f, 0f)
                rotationZ.transformInPlace(position)
                rotationY[j].transformInPlace(position)
                layer.add(position)
            }
        }

        val meshes = ArrayList<MeshBuffer>()
        for (i in 0 until elevations.size - 1) {
            val bufferBuilder = GlBufferBuilder((spokes + 1) * 2)

            for (j in 0 until spokes) {
                bufferBuilder.appendColoredPosition(layers[i][j], ByteColor.half)
                bufferBuilder.appendColoredPosition(layers[i + 1][j], ByteColor.half)
            }

            bufferBuilder.appendColoredPosition(layers[i][0], ByteColor.half)
            bufferBuilder.appendColoredPosition(layers[i + 1][0], ByteColor.half)

            val buffer = bufferBuilder.build()
            meshes += MeshBuffer(
                numVertices = (spokes + 1) * 2,
                meshType = MeshType.TriStrip,
                renderState = RenderState(blendEnabled = true),
                glBuffer = buffer,
            )
        }

        return meshes
    }

}