package xim.poc

import xim.util.Fps
import kotlin.time.Duration

private class ScreenFadeAction(val fadeParams: FadeParameters, val callback: () -> Unit)

object ScreenFader {

    private var action: ScreenFadeAction? = null

    fun update(elapsedFrames: Float) {
        val completed = action?.fadeParams?.update(elapsedFrames)
        if (completed == true) {
            action?.callback?.invoke()
        }
    }

    fun draw() {
        val params = action?.fadeParams ?: return
        val opacity = params.getOpacity()
        UiElementHelper.drawBlackScreenCover(1f - opacity)
    }

    fun fadeOut(duration: Duration, callback: () -> Unit = {}) {
        val frameDuration = Fps.millisToFrames(duration.inWholeMilliseconds)
        val params = FadeParameters.fadeOut(frameDuration)
        action = ScreenFadeAction(params, callback)
    }

    fun fadeIn(duration: Duration, callback: () -> Unit = {}) {
        val frameDuration = Fps.millisToFrames(duration.inWholeMilliseconds)
        val params = FadeParameters.fadeIn(frameDuration)
        action = ScreenFadeAction(params, callback)
    }

}