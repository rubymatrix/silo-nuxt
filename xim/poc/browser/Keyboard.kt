package xim.poc.browser;

import xim.math.Vector2f

class TouchData(
    val startTime: Double,
    val startingX: Double,
    val startingY: Double,
    val normalizedStartingX: Double,
    val normalizedStartingY: Double,
    val mouseEvent: Boolean,
    val rightClick: Boolean = false,
) {

    var normalizedX = normalizedStartingX
    var normalizedY = normalizedStartingY

    private var prevNormalizedX = normalizedStartingX
    private var prevNormalizedY = normalizedStartingY

    private var frameDeltaX = 0.0
    private var frameDeltaY = 0.0

    fun getDeltaFromStart(): Pair<Double, Double> {
        return (normalizedX - normalizedStartingX to normalizedY - normalizedStartingY)
    }

    fun getFrameDelta(): Pair<Double, Double> {
        return (frameDeltaX to frameDeltaY)
    }

    fun onNewFrame() {
        frameDeltaX = normalizedX - prevNormalizedX
        frameDeltaY = normalizedY - prevNormalizedY

        prevNormalizedX = normalizedX
        prevNormalizedY = normalizedY
    }

    fun isScreenTouch(): Boolean {
        return !mouseEvent
    }

    fun isControlTouch(): Boolean {
        return isScreenTouch() && normalizedStartingX > 0.5
    }

}

data class ClickEvent(
    val clickTime: Double,
    val screenPosition: Vector2f,
    val normalizedScreenPosition: Vector2f,
    val rightClick: Boolean,
    val touchEvent: Boolean,
) {

    var consumed = false

    fun isLongClick(): Boolean {
        return touchEvent && clickTime > 250
    }

}

data class PointerPosition(
    val screenPosition: Vector2f,
    val normalizedScreenPosition: Vector2f,
)

enum class WheelDirection {
    Up,
    Down,
}

enum class GameKey(val displayText: String) {
    UiLeft("Menu Left"),
    UiRight("Menu Right"),
    UiUp("Menu Up"),
    UiDown("Menu Down"),
    UiEnter("Menu Confirm"),
    UiExit("Menu Cancel"),

    CamLeft("Camera Left"),
    CamRight("Camera Right"),
    CamUp("Camera Up"),
    CamDown("Camera Down"),
    CamZoomIn("Zoom In"),
    CamZoomOut("Zoom Out"),

    MoveForward("Move Forward"),
    MoveLeft("Move Left"),
    MoveBackward("Move Back"),
    MoveRight("Move Right"),

    Disengage("Disengage"),
    TargetLock("Target Lock"),
    TargetCycle("Target Cycle"),
    Rest("Rest"),
    ToggleMap("Toggle Map"),
    FaceTarget("Face Target"),
    UiContext("UI Context"),

    Pause("Pause"),
    TimeSlow("Slowdown"),

    Autorun("Auto-run"),

    OpenMainMenu("Main Menu"),
    OpenEquipMenu("Equip Menu"),
    OpenInventoryMenu("Inventory Menu"),

    TargetSelf("Target Self"),
    TargetParty2("Party Mem. 2"),
    TargetParty3("Party Mem. 3"),
    TargetParty4("Party Mem. 4"),
    TargetParty5("Party Mem. 5"),
    TargetParty6("Party Mem. 6"),

    DebugClip("Debug Clip"),
    DebugGravity("Debug Gravity"),
    DebugSpeed("Debug Speed"),

    UiConfig("UI Config"),
}

data class WheelData(val direction: WheelDirection) {
    var consumed = false
}

interface Keyboard {

    enum class KeyState {
        PRESSED,
        RELEASED,
        REPEATED,
        NONE;
    }

    fun poll()

    fun clear()

    fun isKeyPressed(keybind: Keybind): Boolean

    fun isKeyPressed(gameKey: GameKey): Boolean

    fun isKeyPressedOrRepeated(gameKey: GameKey): Boolean

    fun getTouchData(): Collection<TouchData>

    fun getClickEvents(): List<ClickEvent>

    fun getWheelEvents(): List<WheelData>

    fun getPointerPosition(): PointerPosition?

}
