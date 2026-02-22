package xim.poc.browser;

import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.DEPTH_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.STENCIL_BUFFER_BIT
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import web.gl.WebGL2RenderingContext
import xim.math.Vector2f
import xim.poc.MenuStacks
import xim.poc.UiElementHelper
import xim.poc.gl.FrameBufferManager
import xim.poc.gl.ScreenSettingsSupplier
import xim.poc.tools.UiPosition
import xim.poc.tools.UiPositionTool
import kotlin.js.json
import kotlin.math.floor
import kotlin.math.roundToInt

private val webgl2Config = json(
    Pair("premultipliedAlpha", false),
    Pair("alpha", false),
    Pair("stencil", true),
    Pair("powerPreference", "high-performance"),
    Pair("preserveDrawingBuffer", true),
    Pair("desynchronized", true)
)

class JsWindow(
    private val canvasId: String,
    private val screenSettingsSupplier: ScreenSettingsSupplier,
) : Window {

    companion object {
        lateinit var instance: JsWindow
    }

    private val canvas = document.getElementById(canvasId) as HTMLCanvasElement
    private val canvasContainer = document.getElementById("canvas-container") as HTMLDivElement

    private lateinit var webgl: WebGL2RenderingContext

    var fitScreen = false

    init {
        canvas.width = screenSettingsSupplier.width
        canvas.height = screenSettingsSupplier.height
        instance = this
    }

    fun initializeAndGetWebGl(): WebGL2RenderingContext? {
        webgl = canvas.getContext("webgl2", webgl2Config) as? WebGL2RenderingContext ?: return null
        return webgl
    }

    fun clear() {
        webgl.clearColor(0f, 0f, 0f, 0f)
        webgl.clear(COLOR_BUFFER_BIT or DEPTH_BUFFER_BIT or STENCIL_BUFFER_BIT)

        val configuration = LocalStorage.getConfiguration()

        val (canvasWindowWidth, canvasWindowHeight) = adjustWindowSize(configuration)
        val resolutionMultiplier = configuration.screenSettings.resolution

        val requestedResolutionWidth = (canvasWindowWidth * resolutionMultiplier).roundToInt()
        val requestedResolutionHeight = (canvasWindowHeight * resolutionMultiplier).roundToInt()

        val actualResolutionWidth = floor(canvas.clientWidth * window.devicePixelRatio.coerceAtLeast(1.0)).roundToInt()
        val actualResolutionHeight = floor(canvas.clientHeight * window.devicePixelRatio.coerceAtLeast(1.0)).roundToInt()

        val (resolutionWidth, resolutionHeight) = if (document.fullscreenElement != null) {
            Pair(actualResolutionWidth, actualResolutionHeight)
        } else {
            Pair(requestedResolutionWidth, requestedResolutionHeight)
        }

        val scale = configuration.screenSettings.uiScale
        UiElementHelper.globalUiScale.copyFrom(Vector2f(scale, scale))

        UiElementHelper.offsetScaling.x = resolutionWidth / (800f * UiElementHelper.globalUiScale.x)
        UiElementHelper.offsetScaling.y = resolutionHeight / (600f * UiElementHelper.globalUiScale.y)

        MenuStacks.LogStack.menuStack.offset.copyFrom(UiPositionTool.getOffset(UiPosition.ChatLog))
        MenuStacks.PartyStack.menuStack.offset.copyFrom(UiPositionTool.getOffset(UiPosition.Party))

        if (canvas.width != resolutionWidth || canvas.height != resolutionHeight) {
            canvas.width = resolutionWidth
            canvas.height = resolutionHeight

            screenSettingsSupplier.width = resolutionWidth
            screenSettingsSupplier.height = resolutionHeight
        }

        adjustPerspective(configuration)
        FrameBufferManager.changeScreenSize(resolutionWidth, resolutionHeight)
    }

    fun flush() {
        webgl.flush()
    }

    private fun adjustWindowSize(configuration: LocalConfiguration): Pair<Int, Int> {
        val (windowWidth, windowHeight) = if (fitScreen) {
            window.innerWidth to window.innerHeight
        } else {
            configuration.screenSettings.windowWidth to configuration.screenSettings.windowHeight
        }

        canvas.style.width = "${windowWidth}px"
        canvasContainer.style.width = canvas.style.width

        canvas.style.height = "${windowHeight}px"
        canvasContainer.style.height = canvas.style.height

        return Pair(windowWidth, windowHeight)
    }

    private fun adjustPerspective(configuration: LocalConfiguration) {
        val aspectRatioEnable = configuration.screenSettings.aspectRatioEnabled
        val aspectRatio = configuration.screenSettings.aspectRatio

        screenSettingsSupplier.aspectRatio = if (aspectRatioEnable) { aspectRatio } else { screenSettingsSupplier.width.toFloat() / screenSettingsSupplier.height.toFloat() }
    }

}
