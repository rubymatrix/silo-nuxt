package xim.poc.browser

import xim.poc.gl.Drawer
import xim.poc.gl.ScreenSettingsSupplier

class PlatformDependencies (
    val window: Window,
    val keyboard: Keyboard,
    val screenSettingsSupplier: ScreenSettingsSupplier,
    val viewExecutor: ViewExecutor,
    val drawer: Drawer
)
