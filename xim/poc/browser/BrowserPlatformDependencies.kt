package xim.poc.browser

import xim.poc.gl.GLDrawer
import xim.poc.gl.GLProgramFactory
import xim.poc.gl.GLShaderFactory
import xim.poc.gl.ScreenSettingsSupplier

class BrowserPlatformDependencies {

    companion object {

        fun get(canvasId: String) : PlatformDependencies? {
            val screenSettings = LocalStorage.getConfiguration().screenSettings
            val screenSettingsSupplier = ScreenSettingsSupplier(width = screenSettings.windowWidth, height = screenSettings.windowHeight)

            val window = JsWindow(canvasId = canvasId, screenSettingsSupplier = screenSettingsSupplier)
            val webgl = window.initializeAndGetWebGl()

            if (webgl == null) {
                ErrorElement.show("Failed to initialize.\nThe browser failed to return a WebGL2 context.")
                return null
            }

            val keyboard = JsKeyboard()

            val drawer = GLDrawer(
                webgl = webgl,
                programFactory = GLProgramFactory(webgl),
                shaderFactory = GLShaderFactory(webgl),
                screenSettingsSupplier = screenSettingsSupplier,
            )

            val jsViewExecutor = JsViewExecutor(window)

            return PlatformDependencies(
                window = window,
                keyboard = keyboard,
                drawer = drawer,
                screenSettingsSupplier = screenSettingsSupplier,
                viewExecutor = jsViewExecutor
            )
        }
    }

}