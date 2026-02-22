package xim.util

object ToggleLogger {

    var toggle = false

    fun log(str: String) {
        if (toggle) { println(str) }
    }

}