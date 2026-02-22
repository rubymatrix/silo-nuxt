package xim.poc

import xim.poc.gl.BlendFunc
import xim.poc.gl.Color

data class ScreenFlashCommand(val color: Color)

object ScreenFlasher {

    private val commands = ArrayList<ScreenFlashCommand>()

    fun addScreenFlash(command: ScreenFlashCommand) {
        commands += command
    }

    fun draw() {
        commands.forEach { UiElementHelper.drawScreenOverlay(it.color.withMultiplied(2f), BlendFunc.Src_One_Add) }
        commands.clear()
    }

}