package xim.poc.camera

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.AreaTransform
import xim.poc.Box
import xim.poc.MainTool
import xim.poc.browser.Keyboard

class StaticCamera(val position: Vector3f, val viewDir: Vector3f, val fov: Float) : Camera {

    override fun update(keyboard: Keyboard, elapsedFrames: Float) {
    }

    override fun getViewMatrix(): Matrix4f {
        return Matrix4f().lookAtDirection(position, viewDir)
    }

    override fun getProjectionMatrix(aspectRatio: Float?): Matrix4f {
        val screenAspectRatio = MainTool.platformDependencies.screenSettingsSupplier.aspectRatio
        return Matrix4f().perspective(fov, aspectRatio ?: screenAspectRatio, PolarCamera.near, PolarCamera.far)
    }

    override fun getPosition(): Vector3f {
        return position
    }

    override fun getViewVector(): Vector3f {
        return Vector3f(viewDir)
    }

    override fun getLensFlareRay(lightPos: Vector3f): Vector3f? {
        return null
    }

    override fun isVisible(box: Box): Boolean {
        throw NotImplementedError()
    }

    override fun transform(areaTransform: AreaTransform): Camera {
        throw NotImplementedError()
    }

}