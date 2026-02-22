package xim.poc.browser

interface ViewExecutor {

    fun beginDrawing(loopFunction: (t: Double) -> Unit)

}