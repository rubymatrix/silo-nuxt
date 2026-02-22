package xim.poc.browser

import onStart
import web.dom.Element
import web.dom.document
import web.html.HTMLElement
import web.keyboard.Key
import web.keyboard.KeyCode
import web.performance.performance
import web.uievents.*
import web.window.window
import xim.math.Vector2f
import xim.poc.browser.Keyboard.KeyState
import xim.poc.tools.FrameTool
import kotlin.math.abs

private class JsKeyState(val keyState: KeyState, val modifiers: Set<ModifierKey> = emptySet())

class JsKeyboard : Keyboard {

    companion object {
        private const val mouseEventId = -1
    }

    private val keyStates = mutableMapOf<KeyCode, JsKeyState>()
    private val bufferedKeyUp = HashMap<KeyCode, KeyboardEvent<*>>()
    private val bufferedKeyDown = HashMap<KeyCode, KeyboardEvent<*>>()
    private val bufferedClicks = ArrayList<ClickEvent>()

    private val touches = HashMap<Int, TouchData>()
    private val clicks = ArrayList<ClickEvent>()

    private val wheelEvents = ArrayList<WheelData>()
    private val bufferedWheelEvents = ArrayList<WheelData>()

    private val canvas by lazy { document.getElementById("canvas") as HTMLElement }

    private var mousePosition: PointerPosition? = null
    private var bufferedMousePosition: PointerPosition? = null

    init {
        window.onkeyup = this::onKeyUp
        window.onkeydown = this::onKeyDown

        canvas.oncontextmenu = { it.preventDefault() }

        canvas.onmousedown = { onMouseDown(it as MouseEvent<Element>) }
        canvas.onmousemove = { onMouseMove(it as MouseEvent<Element>) }
        canvas.onmouseleave = { onMouseEnd(it as MouseEvent<Element>) }
        canvas.onmouseup = { onMouseEnd(it as MouseEvent<Element>) }

        canvas.onwheel = { onWheel(it as WheelEvent<Element>) }

        canvas.ontouchstart = { onTouchStart(it as TouchEvent<Element>) }
        canvas.ontouchmove = { onTouchMove(it as TouchEvent<Element>) }
        canvas.ontouchend = { onTouchEnd(it as TouchEvent<Element>) }
        canvas.ontouchcancel = { onTouchEnd(it as TouchEvent<Element>) }
    }

    private fun onKeyUp(event: KeyboardEvent<*>) {
        val targetId = (event.target as HTMLElement).id
        if (targetId == "canvas" || targetId == "") {
            event.stopPropagation()
            event.preventDefault()
        } else {
            return
        }

        bufferedKeyUp[event.code] = event
    }

    private fun onKeyDown(event: KeyboardEvent<*>) {
        onStart()

        if (event.code == Key.F11) { return }

        val targetId = (event.target as HTMLElement).id
        if (targetId == "canvas" || targetId == "") {
            event.stopPropagation()
            event.preventDefault()
        } else {
            return
        }

        if (event.code == Key.Backspace) {
            FrameTool.disableCanvasFit()
        }

        bufferedKeyDown[event.code] = event
    }

    private fun onMouseDown(mouseEvent: MouseEvent<Element>) {
        val (x, y) = getMousePosition(mouseEvent)
        val (nX, nY) = getNormalizedMousePosition(mouseEvent)

        val rightClick = mouseEvent.button == MouseButton.SECONDARY

        touches[mouseEventId] = TouchData(
            startTime = performance.now(),
            rightClick = rightClick,
            startingX = x,
            startingY = y,
            normalizedStartingX = nX,
            normalizedStartingY = nY,
            mouseEvent = true,
        )
    }

    private fun onMouseMove(mouseEvent: MouseEvent<Element>) {
        val (x, y) = getMousePosition(mouseEvent)
        val (nX, nY) = getNormalizedMousePosition(mouseEvent)

        bufferedMousePosition = PointerPosition(
            screenPosition = Vector2f(x.toFloat(), y.toFloat()),
            normalizedScreenPosition = Vector2f(nX.toFloat(), nY.toFloat())
        )

        val data = touches[mouseEventId] ?: return

        data.normalizedX = nX
        data.normalizedY = nY
    }

    private fun onMouseEnd(mouseEvent: MouseEvent<Element>) {
        val data = touches[mouseEventId] ?: return

        val (dX, dY) = data.getDeltaFromStart()
        val clickTime = performance.now() - data.startTime

        if (abs(dX) < 0.005 && abs(dY) < 0.005) {
            val pos = Vector2f(data.startingX.toFloat(), data.startingY.toFloat())
            val nPos = Vector2f(data.normalizedStartingX.toFloat(), data.normalizedStartingY.toFloat())
            bufferedClicks += ClickEvent(clickTime = clickTime, screenPosition = pos, normalizedScreenPosition = nPos, rightClick = data.rightClick, touchEvent = false)
        }

        touches.remove(mouseEventId)
    }

    private fun getMousePosition(mouseEvent: MouseEvent<Element>): Pair<Double, Double> {
        val target = mouseEvent.currentTarget
        val rect = target.getBoundingClientRect()

        val x = (mouseEvent.clientX - rect.left)
        val y = (mouseEvent.clientY - rect.top)

        return Pair(x, y)
    }

    private fun getNormalizedMousePosition(mouseEvent: MouseEvent<Element>): Pair<Double, Double> {
        val target = mouseEvent.currentTarget
        val rect = target.getBoundingClientRect()

        val x = (mouseEvent.clientX - rect.left) / rect.width
        val y = (mouseEvent.clientY - rect.top) / rect.height

        return Pair(x, y)
    }

    private fun onTouchStart(event: TouchEvent<Element>) {
        event.preventDefault()

        for (touch in event.touches) {
            if (touches.containsKey(touch.identifier)) { continue }

            val (x, y) = getTouchPosition(touch, event.currentTarget)
            val (nX, nY) = getNormalizedTouchPosition(touch, event.currentTarget)

            touches[touch.identifier] = TouchData(
                startTime = performance.now(),
                rightClick = false,
                startingX = x,
                startingY = y,
                normalizedStartingX = nX,
                normalizedStartingY = nY,
                mouseEvent = false,
            )
        }
    }

    private fun onTouchMove(event: TouchEvent<Element>) {
        event.preventDefault()

        for (touch in event.touches) {
            val data = touches[touch.identifier] ?: continue
            val (x, y) = getNormalizedTouchPosition(touch, event.currentTarget)
            data.normalizedX = x
            data.normalizedY = y
        }
    }

    private fun onTouchEnd(event: TouchEvent<Element>) {
        event.preventDefault()

        for (touch in event.changedTouches) {
            val data = touches.remove(touch.identifier) ?: continue

            val (dX, dY) = data.getDeltaFromStart()
            val clickTime = performance.now() - data.startTime

            if (abs(dX) < 0.005 && abs(dY) < 0.005) {
                val pos = Vector2f(data.startingX.toFloat(), data.startingY.toFloat())
                val nPos = Vector2f(data.normalizedStartingX.toFloat(), data.normalizedStartingY.toFloat())
                bufferedClicks += ClickEvent(clickTime = clickTime, screenPosition = pos, normalizedScreenPosition = nPos, rightClick = data.rightClick, touchEvent = true)
            }
        }
    }

    private fun getTouchPosition(touch: Touch, target: Element): Pair<Double, Double> {
        val rect = target.getBoundingClientRect()

        val x = (touch.clientX - rect.left)
        val y = (touch.clientY - rect.top)

        return Pair(x, y)
    }

    private fun getNormalizedTouchPosition(touch: Touch, target: Element): Pair<Double, Double> {
        val rect = target.getBoundingClientRect()

        val x = (touch.clientX - rect.left) / rect.width
        val y = (touch.clientY - rect.top) / rect.height

        return Pair(x, y)
    }

    private fun onWheel(wheelEvent: WheelEvent<Element>) {
        val targetId = (wheelEvent.target as? HTMLElement)?.id ?: return
        if (targetId != "canvas") { return }

        wheelEvent.stopPropagation()
        wheelEvent.preventDefault()

        if (wheelEvent.deltaY < 0) {
            bufferedWheelEvents += WheelData(WheelDirection.Up)
        } else if (wheelEvent.deltaY > 0) {
            bufferedWheelEvents += WheelData(WheelDirection.Down)
        }
    }

    override fun poll() {
        clicks.clear()
        clicks += bufferedClicks
        bufferedClicks.clear()

        wheelEvents.clear()
        wheelEvents += bufferedWheelEvents
        bufferedWheelEvents.clear()

        mousePosition = bufferedMousePosition ?: mousePosition
        bufferedMousePosition = null

        touches.values.forEach { it.onNewFrame() }

        val itr = keyStates.iterator()
        while (itr.hasNext()) {
            val next = itr.next()
            val state = next.value.keyState

            if (state == KeyState.RELEASED) {
                itr.remove()
            } else if (bufferedKeyUp.containsKey(next.key)) {
                keyStates[next.key] = JsKeyState(KeyState.RELEASED)
            } else if (state == KeyState.PRESSED) {
                keyStates[next.key] = JsKeyState(KeyState.REPEATED)
            }

            bufferedKeyUp.remove(next.key)
            bufferedKeyDown.remove(next.key)
        }

        for ((key, event) in bufferedKeyDown) {
            keyStates[key] = JsKeyState(KeyState.PRESSED, toModifiers(event))
        }
    }

    override fun getClickEvents(): List<ClickEvent> {
        return clicks
    }

    override fun getWheelEvents(): List<WheelData> {
        return wheelEvents
    }

    override fun getPointerPosition(): PointerPosition? {
        return mousePosition
    }

    override fun clear() {
        keyStates.clear()
    }

    fun getKeyState(gameKey: GameKey): KeyState {
        val keybind = LocalStorage.getConfiguration().keyboardSettings.gameKeys[gameKey] ?: return KeyState.NONE
        val keyState = keyStates[KeyCode(keybind.keyCode)] ?: return KeyState.NONE
        return keyState.keyState
    }

    override fun isKeyPressed(keybind: Keybind): Boolean {
        val keyCode = KeyCode(keybind.keyCode)

        val state = keyStates[keyCode] ?: return false
        if (state.keyState != KeyState.PRESSED) { return false }

        return state.modifiers.contains(keybind.modifierKey)
    }

    override fun isKeyPressed(gameKey: GameKey): Boolean {
        val keyState = getKeyState(gameKey)
        return keyState == KeyState.PRESSED
    }

    override fun isKeyPressedOrRepeated(gameKey: GameKey): Boolean {
        val keyState = getKeyState(gameKey)
        return keyState == KeyState.PRESSED || keyState == KeyState.REPEATED
    }

    override fun getTouchData(): Collection<TouchData> {
        return touches.values
    }

    private fun toModifiers(event: KeyboardEvent<*>): Set<ModifierKey> {
        val events = HashSet<ModifierKey>()
        if (event.altKey) { events += ModifierKey.Alt }
        if (event.ctrlKey) { events += ModifierKey.Control }
        if (event.shiftKey) { events += ModifierKey.Shift }
        if (events.isEmpty()) { events += ModifierKey.None }
        return events
    }

}
