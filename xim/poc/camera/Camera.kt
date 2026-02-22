package xim.poc.camera;

import xim.poc.browser.Keyboard
import xim.math.Matrix4f
import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.*

object CameraReference {
    private var camera: Camera? = null

    fun setInstance(camera: Camera) {
        CameraReference.camera = camera
    }

    fun getInstance(): Camera {
        return camera!!
    }
}

interface Camera {

    fun update(keyboard: Keyboard, elapsedFrames: Float)

    fun getViewMatrix() : Matrix4f

    fun getProjectionMatrix(aspectRatio: Float? = null) : Matrix4f

    fun getPosition(): Vector3f

    fun getFovAdjustedPosition(): Vector3f = getPosition()

    fun getViewVector(): Vector3f

    fun getLensFlareRay(lightPos: Vector3f): Vector3f?

    fun getWorldSpaceRay(screenSpacePos: Vector2f): Ray? { return null }

    fun isVisible(box: Box): Boolean

    fun lock(enable: Boolean, position: Vector3f? = null) { }

    fun applyShake(shakeFactor: Float) {  }

    fun transform(areaTransform: AreaTransform): Camera

    fun getFoV(): Float? { return null }

    fun setTarget(target: Vector3f) { }

}
