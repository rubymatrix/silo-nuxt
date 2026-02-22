package xim.poc.tools

import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement

fun isCheckBox(name: String): Boolean {
    return CheckBoxes.isChecked(name)
}

object CheckBoxes {

    private val boxes = HashMap<String, Boolean>()

    fun isChecked(name: String): Boolean {
        return boxes.getOrPut(name) {
            val element = document.getElementById(name) ?: throw IllegalStateException("No such checkbox $name")
            val checkbox = element as HTMLInputElement

            checkbox.onchange = { boxes[name] = checkbox.checked; Unit }
            checkbox.checked
        }
    }

}
