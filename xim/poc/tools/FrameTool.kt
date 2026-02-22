package xim.poc.tools

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import org.w3c.dom.*
import org.w3c.files.FileReader
import org.w3c.files.get
import xim.math.Vector3f
import xim.poc.MainTool
import xim.poc.browser.JsWindow
import xim.poc.browser.LocalStorage
import xim.poc.game.ActorStateManager
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.ShiftJis
import kotlin.js.Date

object FrameTool {

    private var setup = false

    fun setup() {
        if (setup) { return }
        setup = true

        val nextButton = document.getElementById("next") as HTMLButtonElement
        nextButton.onclick = {
            MainTool.internalLoop(0.25f, 0.25f)
        }

        (document.getElementById("hideSettings") as HTMLButtonElement).onclick = { enableCanvasFit() }

        val fullScreen = document.getElementById("fullScreen") as HTMLButtonElement
        fullScreen.onclick = {
            val canvas = document.getElementById("canvas") as HTMLCanvasElement
            canvas.requestFullscreen()
        }

        val resetConfig = document.getElementById("resetConfig") as HTMLButtonElement
        resetConfig.onclick = {
            if (window.confirm("Recommended to export first!\nReally erase all stored local configuration (game data, screen settings, etc)?")) {
                LocalStorage.resetConfiguration()
            }
        }

        val exportConfig = document.getElementById("exportConfig") as HTMLAnchorElement
        exportConfig.onclick = {
            val conf = LocalStorage.exportConfig()
            exportConfig.href = "data:text/plain,$conf"
            exportConfig.download = "xim-${Date.now()}.txt"
            Unit
        }

        val importConfig = document.getElementById("importConfig") as HTMLInputElement
        importConfig.value = ""
        importConfig.onchange = {
            val file = importConfig.files?.get(0)
            if (file != null) {
                val fileReader = FileReader()
                fileReader.readAsText(file)
                fileReader.onloadend = { LocalStorage.importConfig(fileReader.result as String) }
            }
        }

        val goButton = document.getElementById("goButton") as HTMLButtonElement
        goButton.onclick = {
            val goValue = (document.getElementById("go") as HTMLInputElement).value

            try {
                val parts = goValue.split('(', ')')[1].split(',').map { it.trim() }
                val x = parts[0].split("x=").last().split("f").first().toFloat()
                val y = parts[1].split("y=").last().split("f").first().toFloat()
                val z = parts[2].split("z=").last().split("f").first().toFloat()

                ActorStateManager.player().position.copyFrom(Vector3f(x, y, z))
            } catch (e: Exception) { console.error("Failed to parse: $goValue") }
        }
    }

    private fun enableCanvasFit() {
        val body = (document.getElementById("body") as HTMLElement)
        body.addClass("no-margin")

        val mainContainer = (document.getElementById("main-container") as HTMLDivElement)
        mainContainer.removeClass("main-container-grid")

        val toolContainer = (document.getElementById("tool-container") as HTMLDivElement)
        toolContainer.style.display = "none"

        val canvasContainer = (document.getElementById("canvas-container") as HTMLDivElement)
        canvasContainer.removeClass("canvas-container-default")

        JsWindow.instance.fitScreen = true
        ChatLog("${ShiftJis.solidDownTriangle} Press 'Backspace' to disable screen-fit.", ChatLogColor.SystemMessage)
        ChatLog("${ShiftJis.solidDownTriangle} Press 'u' to open the UI configuration widget.", ChatLogColor.SystemMessage)
    }

    fun disableCanvasFit() {
        val body = (document.getElementById("body") as HTMLElement)
        body.removeClass("no-margin")

        val mainContainer = (document.getElementById("main-container") as HTMLDivElement)
        mainContainer.addClass("main-container-grid")

        val toolContainer = (document.getElementById("tool-container") as HTMLDivElement)
        toolContainer.style.display = "inline"

        val canvasContainer = (document.getElementById("canvas-container") as HTMLDivElement)
        canvasContainer.addClass("canvas-container-default")

        JsWindow.instance.fitScreen = false
    }

}