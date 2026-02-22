package xim.util

object OncePerFrame {

    private val keys = HashSet<String>()

    fun onNewFrame() {
        keys.clear()
    }

    fun oncePerFrame(key: String, action: () -> Unit) {
        if (keys.contains(key)) { return }
        keys += key
        action.invoke()
    }

    operator fun invoke(key: String, action: () -> Unit) {
        oncePerFrame(key, action)
    }

}

object OnceLogger {

    var enabled = false
    private val messages: HashSet<String> = HashSet()

    fun clear() {
        messages.clear()
    }

    fun info(message: String) {
        if (enabled) { ifFirst(message, console::info) }
    }

    fun warn(message: String) {
        if (enabled) { ifFirst(message, console::warn) }
    }

    fun error(message: String) {
        if (enabled) { ifFirst(message, console::error) }
    }

    private fun ifFirst(message: String, fn: (String) -> Unit) {
        if (messages.contains(message)) {
            return
        }

        messages.add(message)
        fn.invoke(message)
    }

}