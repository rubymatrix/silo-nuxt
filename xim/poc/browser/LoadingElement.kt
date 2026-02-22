package xim.poc.browser

import web.dom.document
import web.html.HTMLDivElement
import web.html.HTMLElement
import xim.poc.UiElementHelper

object LoadingElement {

    private val element by lazy { document.getElementById("init-container") as HTMLDivElement }

    fun show() {
        element.style.display = "block"

        val child = element.firstElementChild as? HTMLElement ?: return
        child.innerText = "Initializing engine" + ".".repeat(1 + UiElementHelper.currentCursorIndex(5))
    }

    fun hide() {
        element.style.display = "none"
    }

}