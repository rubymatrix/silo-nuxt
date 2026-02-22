package xim.poc.tools

import xim.poc.AxisAlignedBoundingBox
import xim.poc.camera.CameraReference
import xim.poc.gl.Drawer
import xim.poc.BoundingBox
import xim.poc.gl.*
import xim.resource.*


class BoxCommand(val box: BoundingBox, val color: ByteColor)

object BoxDrawingTool {
    
    private val boxCommands = ArrayList<BoxCommand>()
    
    fun enqueue(boxCommand: BoxCommand) {
        boxCommands += boxCommand
    }

    fun enqueue(box: BoundingBox, color: ByteColor) {
        boxCommands += BoxCommand(box = box, color = color)
    }

    fun enqueue(box: AxisAlignedBoundingBox, color: ByteColor) {
        boxCommands += BoxCommand(box = BoundingBox.skewed(box.min, box.max), color = color)
    }

    fun render(drawer: Drawer) {
        drawer.setupXim(CameraReference.getInstance())
        boxCommands.forEach { drawBox(drawer, it) }
        boxCommands.clear()
    }

    private fun drawBox(drawer: Drawer, boxCommand: BoxCommand) {
        val numVerts = 3 * 12

        val node = boxCommand.box
        val color = boxCommand.color

        val glBufferBuilder = GlBufferBuilder(numVerts)

        // Bottom
        glBufferBuilder.appendColored(node.vertices[0], node.vertices[1], node.vertices[2], color)
        glBufferBuilder.appendColored(node.vertices[2], node.vertices[3], node.vertices[0], color)

        // Front
        glBufferBuilder.appendColored(node.vertices[0], node.vertices[1], node.vertices[5], color)
        glBufferBuilder.appendColored(node.vertices[5], node.vertices[4], node.vertices[0], color)

        // Right
        glBufferBuilder.appendColored(node.vertices[1], node.vertices[2], node.vertices[6], color)
        glBufferBuilder.appendColored(node.vertices[6], node.vertices[5], node.vertices[1], color)

        // Left
        glBufferBuilder.appendColored(node.vertices[0], node.vertices[3], node.vertices[7], color)
        glBufferBuilder.appendColored(node.vertices[7], node.vertices[4], node.vertices[0], color)

        // Back
        glBufferBuilder.appendColored(node.vertices[3], node.vertices[2], node.vertices[6], color)
        glBufferBuilder.appendColored(node.vertices[6], node.vertices[7], node.vertices[3], color)

        // Top
        glBufferBuilder.appendColored(node.vertices[5], node.vertices[6], node.vertices[7], color)
        glBufferBuilder.appendColored(node.vertices[7], node.vertices[4], node.vertices[5], color)

        val buffer = glBufferBuilder.build()
        val mesh = MeshBuffer(
            numVertices = numVerts,
            meshType = MeshType.TriMesh,
            glBuffer = buffer,
            textureStage0 = null,
            renderState = RenderState(blendEnabled = true))

        drawer.drawXim(DrawXimCommand(meshes = listOf(mesh)))
        mesh.release()
    }
    
}