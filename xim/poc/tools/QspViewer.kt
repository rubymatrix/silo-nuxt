package xim.poc.tools

import kotlinx.browser.document
import org.w3c.dom.HTMLSelectElement
import xim.poc.BoundingBox
import xim.poc.gl.ByteColor
import xim.resource.SpacePartitioningNode
import xim.resource.ZoneResource

object QspViewer {

    fun drawQsp(zoneDat: ZoneResource) {
        val mySelect = document.getElementById("depth") as HTMLSelectElement
        val desiredDepth = mySelect.value.toInt()
        drawQsp(zoneDat.zoneSpaceTreeRoot, 0, desiredDepth)
    }

    private fun drawQsp(node: SpacePartitioningNode, depth: Int, desiredDepth: Int) {
        if (depth == desiredDepth || node.leafNode) {
            drawQspNode(node)
        } else {
            node.children.filterNotNull().forEach { drawQsp(it, depth + 1, desiredDepth) }
        }
    }

    private fun drawQspNode(node: SpacePartitioningNode) {
        BoxDrawingTool.enqueue(BoxCommand(BoundingBox(node.boundingBox.points), ByteColor(30, 0, 0, 30)))
    }


}