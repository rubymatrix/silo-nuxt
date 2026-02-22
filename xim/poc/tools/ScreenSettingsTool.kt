package xim.poc.tools

import web.dom.document
import web.html.HTMLInputElement
import xim.poc.UiResourceManager
import xim.poc.browser.LocalStorage

object ScreenSettingsTool {

    private var setup = false

    private val windowWidth by lazy { document.getElementById("WindowWidth") as HTMLInputElement }
    private val windowHeight by lazy { document.getElementById("WindowHeight") as HTMLInputElement }
    private val resMultiplier by lazy { document.getElementById("ResMultiplier") as HTMLInputElement }
    private val aspectRatio by lazy { document.getElementById("aspectRatio") as HTMLInputElement }
    private val aspectRatioEnabled by lazy { document.getElementById("aspectRatioEnable") as HTMLInputElement }

    private val uiScale by lazy { document.getElementById("UiScale") as HTMLInputElement }

    private val windowStyle by lazy { document.getElementById("WindowStyle") as HTMLInputElement }

    private val cameraInvertX by lazy { document.getElementById("cameraX") as HTMLInputElement }
    private val cameraInvertY by lazy { document.getElementById("cameraY") as HTMLInputElement }
    private val cameraCollision by lazy { document.getElementById("cameraCollision") as HTMLInputElement }
    private val cameraScreenShake by lazy { document.getElementById("cameraScreenShake") as HTMLInputElement }
    private val mouseSensitivity by lazy { document.getElementById("mouseSensitivity") as HTMLInputElement }
    private val arrowSensitivity by lazy { document.getElementById("arrowSensitivity") as HTMLInputElement }

    private val bumpMapEnabled by lazy { document.getElementById("bumpMapEnabled") as HTMLInputElement }

    fun setup() {
        if (setup) { return }
        setup = true

        val config = LocalStorage.getConfiguration().screenSettings
        val cameraConfig = LocalStorage.getConfiguration().cameraSettings
        
        windowWidth.value = config.windowWidth.toString()
        windowHeight.value = config.windowHeight.toString()
        resMultiplier.value = config.resolution.toString()
        aspectRatio.value = config.aspectRatio.toString()
        aspectRatioEnabled.checked = config.aspectRatioEnabled
        
        uiScale.value = config.uiScale.toString()
        windowStyle.value = config.windowStyle.toString()

        cameraInvertX.value = cameraConfig.invertX.toString()
        cameraInvertY.value = cameraConfig.invertY.toString()
        cameraCollision.value = cameraConfig.collision.toString()
        cameraScreenShake.value = cameraConfig.screenShake.toString()

        bumpMapEnabled.checked = config.bumpMapEnabled

        windowWidth.onchange = { LocalStorage.changeConfiguration { it.screenSettings.windowWidth = windowWidth.value.toIntOrNull() ?: 1280 } }
        windowHeight.onchange = { LocalStorage.changeConfiguration { it.screenSettings.windowHeight = windowHeight.value.toIntOrNull() ?: 1280 } }
        resMultiplier.onchange = { LocalStorage.changeConfiguration { it.screenSettings.resolution = resMultiplier.value.toFloatOrNull() ?: 1f } }
        aspectRatio.onchange = { LocalStorage.changeConfiguration { it.screenSettings.aspectRatio = aspectRatio.value.toFloatOrNull() ?: 1f } }
        aspectRatioEnabled.onchange = { LocalStorage.changeConfiguration { it.screenSettings.aspectRatioEnabled = aspectRatioEnabled.checked } }

        uiScale.onchange = { LocalStorage.changeConfiguration { it.screenSettings.uiScale = uiScale.value.toFloatOrNull() ?: 1f } }
        windowStyle.onchange = { updateWindowStyle() }

        cameraInvertX.onchange = { LocalStorage.changeConfiguration { it.cameraSettings.invertX = cameraInvertX.checked } }
        cameraInvertY.onchange = { LocalStorage.changeConfiguration { it.cameraSettings.invertY = cameraInvertY.checked } }
        cameraCollision.onchange = { LocalStorage.changeConfiguration { it.cameraSettings.collision = cameraCollision.checked } }
        cameraScreenShake.onchange = { LocalStorage.changeConfiguration { it.cameraSettings.screenShake = cameraScreenShake.value.toIntOrNull() ?: 100 } }
        mouseSensitivity.onchange = { LocalStorage.changeConfiguration { it.cameraSettings.mouseSensitivity = mouseSensitivity.value.toFloatOrNull() ?: 1f } }
        arrowSensitivity.onchange = { LocalStorage.changeConfiguration { it.cameraSettings.arrowSensitivity = arrowSensitivity.value.toFloatOrNull() ?: 1f } }

        bumpMapEnabled.onchange = { LocalStorage.changeConfiguration { it.screenSettings.bumpMapEnabled = bumpMapEnabled.checked } }
    }
    
    private fun updateWindowStyle() {
        val windowStyle = windowStyle.value.toIntOrNull() ?: 1
        LocalStorage.changeConfiguration { it.screenSettings.windowStyle = windowStyle }
        UiResourceManager.setWindowStyle(windowStyle)
    }

}