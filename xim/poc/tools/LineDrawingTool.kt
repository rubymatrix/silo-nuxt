package xim.poc.tools

import xim.poc.camera.CameraReference
import xim.poc.gl.DrawXimCommand
import xim.poc.gl.Drawer
import xim.math.Vector3f
import xim.poc.SkewBoundingBox
import xim.poc.gl.ByteColor
import xim.poc.gl.GlBufferBuilder
import xim.poc.gl.MeshBuffer
import xim.resource.MeshType

object LineDrawingTool {

    private class Line(val start: Vector3f, val end: Vector3f, val startThickness: Float, val endThickness: Float, val color: ByteColor)

    private val lines = ArrayList<Line>()

    fun drawLine(start: Vector3f, end: Vector3f, startThickness: Float = 0.025f, endThickness: Float = startThickness, color: ByteColor = ByteColor.opaqueR) {
        lines.add(Line(start, end, startThickness, endThickness, color))
    }

    fun renderLines(drawer: Drawer) {
        drawer.setupXim(CameraReference.getInstance())
        lines.forEach { renderLine(drawer, it) }
        lines.clear()
    }

    private fun renderLine(drawer: Drawer, line: Line) {
        val numVerts = 3 * 12
        val glBufferBuilder = GlBufferBuilder(numVerts)
        val node = SkewBoundingBox(line.start, line.end, line.startThickness, line.endThickness)
        val color = line.color

        // Front face
        glBufferBuilder.appendColored(node.nearBottomLeft, node.nearBottomRight, node.nearTopRight, color)
        glBufferBuilder.appendColored(node.nearTopLeft, node.nearBottomLeft, node.nearTopRight, color)

        // Far face
        glBufferBuilder.appendColored(node.farBottomLeft, node.farBottomRight, node.farTopRight, color)
        glBufferBuilder.appendColored(node.farTopLeft, node.farBottomLeft, node.farTopRight, color)

        // Right face
        glBufferBuilder.appendColored(node.nearBottomRight, node.farBottomRight, node.farTopRight, color)
        glBufferBuilder.appendColored(node.nearTopRight, node.farTopRight, node.nearBottomRight, color)

        // Left face
        glBufferBuilder.appendColored(node.nearBottomLeft, node.farBottomLeft, node.farTopLeft, color)
        glBufferBuilder.appendColored(node.nearTopLeft, node.farTopLeft, node.nearBottomLeft, color)

        // Top face
        glBufferBuilder.appendColored(node.nearTopLeft, node.nearTopRight, node.farTopRight, color)
        glBufferBuilder.appendColored(node.farTopRight, node.nearTopLeft, node.farTopLeft, color)

        // Bottom face
        glBufferBuilder.appendColored(node.nearBottomLeft, node.nearBottomRight, node.farBottomRight, color)
        glBufferBuilder.appendColored(node.farBottomRight, node.nearBottomLeft, node.farBottomLeft, color)

        val buffer = glBufferBuilder.build()
        val bbMesh = MeshBuffer(
            numVertices = numVerts,
            meshType = MeshType.TriMesh,
            glBuffer = buffer,
            textureStage0 = null,
        )

        drawer.drawXim(
            DrawXimCommand(
                meshes = listOf(bbMesh),
            )
        )

        bbMesh.release()
    }

}