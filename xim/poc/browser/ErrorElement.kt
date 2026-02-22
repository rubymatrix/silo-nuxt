package xim.poc.browser

import web.dom.document
import web.html.HTMLDivElement
import web.html.HTMLElement

object ErrorElement {

    private val element by lazy { document.getElementById("error-container") as HTMLDivElement }

    fun show(message: String) {
        element.style.display = "block"
        setChildText(message)
    }

    fun hide() {
        element.style.display = "none"
        setChildText("")
    }

    private fun setChildText(message: String) {
        val child = element.firstElementChild as? HTMLElement ?: return
        child.innerText = message
    }

}