package xim.poc.gl

import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.DEPTH_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.STENCIL_BUFFER_BIT
import web.gl.WebGL2RenderingContext
import xim.poc.EnvironmentManager
import xim.poc.MainTool

object FrameBufferManager {

    private const val maxBuffers = 16

    private val availableBuffers = ArrayList<GLFrameBuffer>()
    private val claimedBuffers = ArrayList<GLFrameBuffer>()

    private var initialized = false

    private lateinit var screenBuffer: GLScreenBuffer
    private lateinit var screenHazeBuffer: GLScreenBuffer
    private lateinit var screenBlurBuffer: GLScreenBuffer

    fun setup() {
        if (initialized) { return }
        initialized = true

        val screenSettings = MainTool.platformDependencies.screenSettingsSupplier
        initializeScreenBuffers(screenSettings.width, screenSettings.height)

        for (i in 0 until maxBuffers) { availableBuffers += createBuffer(i.toString()) }
        screenHazeBuffer = GLScreenBuffer(GlDisplay.getContext(), "fbuffer haze    ", width = 512, height = 512)
    }

    fun isReady(): Boolean {
        return initialized && screenBuffer.isReady() && screenBlurBuffer.isReady() && availableBuffers.all { it.isReady() } && screenHazeBuffer.isReady()
    }

    fun releaseClaimedBuffers() {
        if (!isReady()) { return }

        availableBuffers.addAll(claimedBuffers)
        claimedBuffers.clear()
    }

    fun claimBuffer(): GLFrameBuffer? {
        if (!isReady()) {
            return null
        }

        val claimed = availableBuffers.removeFirstOrNull() ?: return null
        claimedBuffers += claimed

        return claimed
    }

    fun unbind() {
        GlDisplay.getContext().bindFramebuffer(WebGL2RenderingContext.FRAMEBUFFER, null)
    }

    fun bindScreenBuffer() {
        screenBuffer.bind()
    }

    fun bindAndClearScreenHazeBuffer() {
        screenHazeBuffer.bind()
        val webgl = GlDisplay.getContext()

        webgl.clearColor(0f, 0f, 0f, 0f)
        webgl.clear(COLOR_BUFFER_BIT or DEPTH_BUFFER_BIT or STENCIL_BUFFER_BIT)
    }

    fun bindAndClearScreenBuffer() {
        bindScreenBuffer()

        val clearColor = EnvironmentManager.clearColor
        val webgl = GlDisplay.getContext()

        webgl.clearColor(clearColor.r(), clearColor.g(), clearColor.b(), clearColor.a())
        webgl.clear(COLOR_BUFFER_BIT or DEPTH_BUFFER_BIT or STENCIL_BUFFER_BIT)
    }

    fun changeScreenSize(width: Int, height: Int) {
        if (!isReady()) { return }

        if (screenBuffer.width == width && screenBuffer.height == height) { return }

        screenBuffer.delete()
        screenBlurBuffer.delete()

        initializeScreenBuffers(width, height)
    }

    fun getCurrentScreenBuffer(): GLScreenBuffer {
        return screenBuffer
    }

    fun getBlurBuffer(): GLScreenBuffer {
        return screenBlurBuffer
    }

    fun bindBlurBuffer() {
        screenBlurBuffer.bind()

        val webgl = GlDisplay.getContext()
        webgl.clearColor(0f, 0f, 0f, 0f)
        webgl.clear(COLOR_BUFFER_BIT)
    }

    fun getHazeBuffer(): GLScreenBuffer {
        return screenHazeBuffer
    }

    private fun initializeScreenBuffers(width: Int, height: Int) {
        val webgl = GlDisplay.getContext()
        screenBuffer = GLScreenBuffer(webgl, "fbuffer screen  ", width, height)
        screenBlurBuffer = GLScreenBuffer(webgl, "fbuffer blur    ", width, height, screenBuffer.getDepthBuffer())
    }

    private fun createBuffer(name: String): GLFrameBuffer {
        return GLFrameBuffer(GlDisplay.getContext(), name)
    }

}