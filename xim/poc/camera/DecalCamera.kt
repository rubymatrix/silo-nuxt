package xim.poc.camera

import xim.math.Matrix4f
import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.AreaTransform
import xim.poc.AxisAlignedBoundingBox
import xim.poc.Box
import xim.poc.SatCollider
import xim.poc.browser.Keyboard

class DecalCamera(decalPosition: Vector3f, val projectionSize: Vector2f = Vector2f(1f, 1f)): Camera {

    companion object {
        private const val near = 0.05f
        private const val far = 1.05f
    }

    private val projectionDirection = Vector3f.Y

    private val eye = decalPosition + (projectionDirection * -1f)
    private val target = eye + projectionDirection * far
    private val viewBox = getBox()

    override fun getViewMatrix(): Matrix4f {
        val mat = Matrix4f()
        mat.lookAt(eye, target, worldUp = Vector3f.Z)
        return mat
    }

    override fun getProjectionMatrix(aspectRatio: Float?): Matrix4f {
        val projectionMatrix = Matrix4f()
        projectionMatrix.ortho(left = -projectionSize.x, right = projectionSize.x, top = projectionSize.y, bottom = -projectionSize.y, near = near, far = far)
        return projectionMatrix
    }

    override fun getPosition(): Vector3f {
        return eye
    }

    override fun getViewVector(): Vector3f {
        return Vector3f(projectionDirection)
    }

    override fun getLensFlareRay(lightPos: Vector3f): Vector3f? {
        return null
    }

    override fun isVisible(box: Box): Boolean {
        return SatCollider.boxBoxOverlap(viewBox, box)
    }

    override fun transform(areaTransform: AreaTransform): Camera {
        return this
    }

    override fun update(keyboard: Keyboard, elapsedFrames: Float) {
    }

    private fun getBox(): AxisAlignedBoundingBox {
        val min = Vector3f(target.x - projectionSize.x, target.y - 0.25f, target.z - projectionSize.y)
        val max = Vector3f(target.x + projectionSize.x, target.y + 0.25f, target.z + projectionSize.y)
        return AxisAlignedBoundingBox(min, max)
    }

}