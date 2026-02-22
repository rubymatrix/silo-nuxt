package xim.poc.tools

import web.dom.Element
import web.dom.document

object DebugToolsManager {

    var debugEnabled = false

    fun showDebugOnlyTools() {
        val debugElements = document.getElementsByClassName("debugTool")

        val elements = ArrayList<Element>()
        val iterator = debugElements.iterator()
        while (iterator.hasNext()) { elements += iterator.next() }

        elements.forEach { it.classList.remove("debugTool") }
    }

    fun showLocalHostTools() {
        val debugElements = document.getElementsByClassName("localTool")

        val elements = ArrayList<Element>()
        val iterator = debugElements.iterator()
        while (iterator.hasNext()) { elements += iterator.next() }

        elements.forEach { it.classList.remove("localTool") }
    }


}