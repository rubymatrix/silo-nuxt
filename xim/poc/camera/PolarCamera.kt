package xim.poc.camera

import xim.math.Matrix4f
import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.*
import xim.poc.browser.GameKey
import xim.poc.browser.Keyboard
import xim.poc.browser.LocalStorage
import xim.poc.browser.WheelDirection
import xim.poc.game.UiStateHelper
import xim.poc.gl.ScreenSettingsSupplier
import xim.util.fallOff
import xim.util.toRads
import kotlin.math.*

private class TargetLock {
    var locked = false
    val target = Vector3f()
}

class PolarCamera (var target: Vector3f, var targetOffset: Vector3f, val screenSettingsSupplier: ScreenSettingsSupplier) : Camera {

    companion object {
        private const val increment = 0.05f

        private val fovIncrement = 1f.toRads()
        private val minFov = 25.0f.toRads()
        private val maxFov = 75.0f.toRads()

        const val near = 0.05f
        const val far = 4096f // Max sky-box is 2029.5

        private const val radiusMin = 4.0f
        private const val radiusMax = 6.0f

        private const val cullFar = 200f
    }

    private var phi = PI
    private var theta = PI/1.8
    private var fov = maxFov
    private var previousRadius = radiusMax

    private val baseEye = Vector3f()
    private val eye = Vector3f()
    private val fovAdjustedEye = Vector3f()

    private val shakeOffset = Vector3f()
    private val targetLock = TargetLock()

    private val viewMatrix = Matrix4f()
    private val viewDirection = Vector3f()

    private lateinit var frustum: Frustum

    init {
        updateEye(elapsedFrames = 0f)
    }

    override fun getViewMatrix(): Matrix4f {
        return viewMatrix
    }

    override fun getProjectionMatrix(aspectRatio: Float?): Matrix4f {
        val projectionMatrix = Matrix4f()
        projectionMatrix.perspective(fov, aspectRatio ?: screenSettingsSupplier.aspectRatio, near, far)
        return projectionMatrix
    }

    override fun getPosition(): Vector3f {
        return eye
    }

    override fun getFovAdjustedPosition(): Vector3f {
        return fovAdjustedEye
    }

    override fun getViewVector(): Vector3f {
        return Vector3f(viewDirection)
    }

    override fun getFoV(): Float {
        return fov
    }

    override fun lock(enable: Boolean, position: Vector3f?) {
        targetLock.locked = enable
        if (enable) { position?.let { targetLock.target.copyFrom(it) } }
    }

    override fun setTarget(target: Vector3f) {
        this.target = target
    }

    private fun getTargetPosition(): Vector3f {
        return if (targetLock.locked) { targetLock.target + targetOffset } else { target + targetOffset }
    }

    override fun getLensFlareRay(lightPos: Vector3f): Vector3f? {
        val centerNearPlane = eye + viewDirection * near
        val near = Plane(viewDirection, centerNearPlane)

        val distanceToPlane = near.normal.dot(lightPos) + near.constant

        return eye + (viewDirection * (distanceToPlane * 0.5f))
    }

    override fun getWorldSpaceRay(screenSpacePos: Vector2f): Ray {
        val leftVec = Vector3f.UP.cross(viewDirection).normalize()
        val upVec = viewDirection.cross(leftVec).normalize()

        val halfNearPlaneHeight = 2 * tan(fov /2) * near
        val halfNearPlaneWidth = halfNearPlaneHeight * (screenSettingsSupplier.width.toFloat() / screenSettingsSupplier.height.toFloat())
        val centerNearPlane = eye + viewDirection * near

        val halfFarPlaneHeight = 2* tan(fov /2) * cullFar
        val halfFarPlaneWidth = halfFarPlaneHeight * (screenSettingsSupplier.width.toFloat() / screenSettingsSupplier.height.toFloat())
        val centerFarPlane = eye + viewDirection * cullFar

        val tX = -(screenSpacePos.x - 0.5f)
        val tY = -(screenSpacePos.y - 0.5f)

        val nearPlanePos = centerNearPlane + (leftVec * tX * halfNearPlaneWidth) + (upVec * tY * halfNearPlaneHeight)
        val farPlanePos = centerFarPlane + (leftVec * tX * halfFarPlaneWidth) + (upVec * tY * halfFarPlaneHeight)

        return Ray(nearPlanePos, (farPlanePos - nearPlanePos).normalizeInPlace())
    }

    override fun isVisible(box: Box): Boolean {
        return frustum.intersects(box.getVertices())
    }

    override fun update(keyboard: Keyboard, elapsedFrames: Float) {
        var strafing = false

        if (!UiStateHelper.cursorLocked()) {
            strafing = updateFromKeyboardTouches(elapsedFrames, keyboard)
            updateFromKeyboardButtons(elapsedFrames, keyboard)
            updateFromWheelEvents(elapsedFrames, keyboard)
        }

        ActorManager.player().setStrafing(strafing)

        updateEye(elapsedFrames)
    }

    private fun updateEye(elapsedFrames: Float) {
        val target = getTargetPosition()

        if (previousRadius < radiusMin) {
            previousRadius = (previousRadius + 0.1f * elapsedFrames).coerceAtMost(radiusMin)
        }

        val radius = Vector3f.distance(target, baseEye).coerceIn(previousRadius, radiusMax)
        adjustBaseEye(target, radius)

        viewDirection.copyFrom((target - baseEye).normalizeInPlace())

        val adjustedRadius = getAdjustedRadiusFromCollision(target, radius)
        if (adjustedRadius != null) { adjustBaseEye(target, adjustedRadius) }

        val newRadius = adjustedRadius ?: radius

        eye.copyFrom(baseEye + shakeOffset)
        val targetWithOffset = target + shakeOffset
        viewMatrix.lookAt(eye, targetWithOffset)

        val fakeRadius = newRadius * (1f - fov.fallOff(0f, maxFov))
        fovAdjustedEye.x = (targetWithOffset.x) + (fakeRadius*sin(theta)*cos(phi)).toFloat()
        fovAdjustedEye.y = (targetWithOffset.y) + (fakeRadius*cos(theta)).toFloat()
        fovAdjustedEye.z = (targetWithOffset.z) + (fakeRadius*sin(theta)*sin(phi)).toFloat()

        frustum = getFrustum()
        shakeOffset.copyFrom(Vector3f.ZERO)

        previousRadius = newRadius
    }

    private fun getAdjustedRadiusFromCollision(target: Vector3f, baseRadius: Float): Float? {
        if (!LocalStorage.getConfiguration().cameraSettings.collision) { return null }

        val ray = Ray(origin = target, direction = viewDirection * -1f)
        val (_, nearestLocalCollision) = Collider.nearestLocalCollision(ray = ray, ignoreHitWalls = true, includeInteractionCollision = true, maxSteps = 2) ?: return null

        val collisionDistance = (nearestLocalCollision.distance - 0.25f).coerceAtLeast(0.5f)
        if (collisionDistance >= baseRadius) { return null }

        return collisionDistance
    }

    private fun adjustBaseEye(target: Vector3f, radius: Float) {
        baseEye.x = target.x + (radius * sin(theta) * cos(phi)).toFloat()
        baseEye.y = target.y + (radius * cos(theta)).toFloat()
        baseEye.z = target.z + (radius * sin(theta) * sin(phi)).toFloat()
    }

    private fun updateFromKeyboardTouches(elapsedFrames: Float, keyboard: Keyboard): Boolean {
        var strafe = false

        val sensitivity = LocalStorage.getConfiguration().cameraSettings.mouseSensitivity

        for (event in keyboard.getTouchData()) {
            if (event.isScreenTouch() && event.normalizedStartingX < 0.5) { continue }

            val (x, y) = event.getFrameDelta()
            val direction = Vector3f(x.toFloat(), 0f, y.toFloat()) * 50f

            if (direction.z < 0.1 || direction.z > 0.1) {
                incrementTheta(sensitivity * elapsedFrames * direction.z)
            }

            if (direction.x < 0.1 || direction.x > 0.1) {
                incrementPhi(sensitivity * elapsedFrames * direction.x * 2)
            }

            if (event.rightClick) {
                strafe = true
            }
        }

        return strafe
    }

    private fun updateFromKeyboardButtons(elapsedFrames: Float, keyboard: Keyboard) {
        val sensitivity = LocalStorage.getConfiguration().cameraSettings.arrowSensitivity

        if (keyboard.isKeyPressedOrRepeated(GameKey.CamUp)) {
            incrementTheta(-elapsedFrames * sensitivity)
        }
        if (keyboard.isKeyPressedOrRepeated(GameKey.CamRight)) {
            incrementPhi(elapsedFrames * sensitivity)
        }
        if (keyboard.isKeyPressedOrRepeated(GameKey.CamDown)) {
            incrementTheta(elapsedFrames * sensitivity)
        }
        if (keyboard.isKeyPressedOrRepeated(GameKey.CamLeft)) {
            incrementPhi(-elapsedFrames * sensitivity)
        }
        if (keyboard.isKeyPressedOrRepeated(GameKey.CamZoomIn)) {
            incrementFov(-elapsedFrames * sensitivity)
        }
        if (keyboard.isKeyPressedOrRepeated(GameKey.CamZoomOut)) {
            incrementFov(elapsedFrames * sensitivity)
        }
    }

    private fun updateFromWheelEvents(elapsedFrames: Float, keyboard: Keyboard) {
        if (UiStateHelper.hasActiveUi()) { return }

        val event = keyboard.getWheelEvents().lastOrNull() ?: return
        if (event.consumed) { return }

        when (event.direction) {
            WheelDirection.Up -> incrementFov(-3f)
            WheelDirection.Down -> incrementFov(3f)
        }
    }

    override fun applyShake(shakeFactor: Float) {
        val adjustedShakeFactor = shakeFactor * (LocalStorage.getConfiguration().cameraSettings.screenShake / 100.0f)
        shakeOffset.y = max(adjustedShakeFactor, shakeOffset.y)
    }

    override fun transform(areaTransform: AreaTransform): Camera {
        val transformed = PolarCamera(areaTransform.transform.transform(target), targetOffset, screenSettingsSupplier)
        transformed.eye.copyFrom(areaTransform.transform.transform(eye))

        transformed.viewDirection.copyFrom((transformed.target - transformed.eye).normalizeInPlace())

        transformed.phi = phi
        transformed.theta = theta

        transformed.frustum = transformed.getFrustum()

        return transformed
    }

    private fun incrementTheta(elapsedFrames: Float) {
        val invertY = LocalStorage.getConfiguration().cameraSettings.invertY
        theta += (elapsedFrames * increment) * if (invertY) { -1 } else { 1 }
        if (theta >= 7 * PI / 8) {
            theta = 7 * PI / 8 - 0.001f
        } else if (theta <= PI / 8) {
            theta = PI / 8 + 0.001f
        }
        val angle = cos(theta)
        if (angle == 1.0) {
            theta += 0.0001f
        } else if (angle == -1.0) {
            theta -= 0.0001f
        }
    }

    private fun incrementPhi(elapsedFrames: Float) {
        val invertX = LocalStorage.getConfiguration().cameraSettings.invertX
        phi += (elapsedFrames * increment) * if (invertX) { -1 } else { 1 }
        if (phi > 2 * PI) {
            phi -= 2 * PI
        } else if (phi < 0) {
            phi += 2 * PI
        }
    }

    private fun incrementFov(elapsedFrames: Float) {
        fov += elapsedFrames * fovIncrement
        fov = fov.coerceIn(minFov, maxFov)
    }

    private fun getFrustum(): Frustum {
        val leftVec = Vector3f.UP.cross(viewDirection).normalize()
        val upVec = viewDirection.cross(leftVec).normalize()

        val halfNearPlaneHeight = 2 * tan(fov /2) * near
        val halfNearPlaneWidth = halfNearPlaneHeight * (screenSettingsSupplier.width.toFloat() / screenSettingsSupplier.height.toFloat())
        val centerNearPlane = eye + viewDirection * near

        val halfFarPlaneHeight = 2* tan(fov /2) * cullFar
        val halfFarPlaneWidth = halfFarPlaneHeight * (screenSettingsSupplier.width.toFloat() / screenSettingsSupplier.height.toFloat())
        val centerFarPlane = eye + viewDirection * cullFar

        val nearBottomLeft =    centerNearPlane + leftVec * halfNearPlaneWidth - upVec * halfNearPlaneHeight
        val nearBottomRight =   centerNearPlane - leftVec * halfNearPlaneWidth - upVec * halfNearPlaneHeight
        val nearTopLeft =       centerNearPlane + leftVec * halfNearPlaneWidth + upVec * halfNearPlaneHeight
        val nearTopRight =      centerNearPlane - leftVec * halfNearPlaneWidth + upVec * halfNearPlaneHeight

        val farBottomLeft =     centerFarPlane + leftVec * halfFarPlaneWidth - upVec * halfFarPlaneHeight
        val farBottomRight =    centerFarPlane - leftVec * halfFarPlaneWidth - upVec * halfFarPlaneHeight
        val farTopLeft =        centerFarPlane + leftVec * halfFarPlaneWidth + upVec * halfFarPlaneHeight
        val farTopRight =       centerFarPlane - leftVec * halfFarPlaneWidth + upVec * halfFarPlaneHeight

        val near = Plane(viewDirection, centerNearPlane)
        val far = Plane(viewDirection * -1f, eye + (viewDirection * far))

        val bottom = Plane(farBottomLeft, nearBottomLeft, farBottomRight)
        val top = Plane(farTopLeft, farTopRight, nearTopLeft)

        val right = Plane(farTopRight, farBottomRight, nearBottomRight)
        val left = Plane(nearBottomLeft, farBottomLeft, farTopLeft)

        return Frustum(left = left, right = right, top = top, bottom = bottom, near = near, far = far)
    }

}