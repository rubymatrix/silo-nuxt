package xim.poc.tools

import web.dom.document
import web.html.HTMLDivElement
import web.html.HTMLInputElement
import web.keyboard.Key
import web.keyboard.KeyCode
import web.uievents.KeyboardEvent
import xim.poc.browser.*

object KeybindTool {

    private val hotbarConfig1 by lazy { document.getElementById("hotbarConfig1") as HTMLDivElement }
    private val hotbarConfig2 by lazy { document.getElementById("hotbarConfig2") as HTMLDivElement }
    private val hotbarConfig3 by lazy { document.getElementById("hotbarConfig3") as HTMLDivElement }
    private val gameKeyConfig by lazy { document.getElementById("gameKeyConfig") as HTMLDivElement }

    private val modifierKeys = setOf(Key.ShiftLeft, Key.ShiftRight, Key.AltLeft, Key.AltRight, Key.ControlLeft, Key.ControlRight, Key.MetaLeft, Key.MetaRight)
    private val illegalKeys = setOf(Key.F11, Key.Backspace)

    private var setup = false

    fun setup() {
        if (setup) { return }
        setup = true

        setupInputs(hotbarConfig1, 1)
        setupInputs(hotbarConfig2, 2)
        setupInputs(hotbarConfig3, 3)
        setupGameKeyInputs()
    }

    fun defaultGameKeyKeybinds(): HashMap<GameKey, Keybind> {
        return HashMap(GameKey.values().associateWith { Keybind(defaultKeyCode(it).toString(), ModifierKey.None) })
    }

    fun defaultHotbarKeybinds(modifierKey: ModifierKey): ArrayList<Keybind> {
        val keybinds = (1 .. 10).map { it % 10 }.map { Keybind("Digit$it", modifierKey) }
        return ArrayList(keybinds)
    }

    private fun setupInputs(hotbarConfigContainer: HTMLDivElement, row: Int) {
        val keyboardSettings = LocalStorage.getConfiguration().keyboardSettings

        val bindings = when (row) {
            1 -> keyboardSettings.hotbar1Bindings
            2 -> keyboardSettings.hotbar2Bindings
            3 -> keyboardSettings.hotbar3Bindings
            else -> return
        }

        for (column in 1 .. 10) {
            val div = document.createElement("div") as HTMLDivElement
            div.classList.add("config")
            hotbarConfigContainer.appendChild(div)

            val span = document.createElement("span")
            span.innerText = "Hotbar $row: $column"
            div.appendChild(span)

            val input = document.createElement("input") as HTMLInputElement
            input.classList.add("medInput")
            input.onkeydown = { handleInput(it, input, row, column) }
            div.appendChild(input)

            updateInput(input, bindings[column - 1])
        }
    }

    private fun handleInput(event: KeyboardEvent<*>, input: HTMLInputElement, row: Int, column: Int) {
        val keybind = eventToKeyBind(event) ?: return
        updateInput(input, keybind)

        LocalStorage.changeConfiguration {
            val bindings = toBindings(row, it.keyboardSettings)
            bindings[column - 1] = keybind
        }

        event.preventDefault()
        event.stopPropagation()
    }

    private fun setupGameKeyInputs() {
        val keyboardSettings = LocalStorage.getConfiguration().keyboardSettings

        for (gameKey in GameKey.values()) {
            val div = document.createElement("div") as HTMLDivElement
            div.classList.add("config")
            gameKeyConfig.appendChild(div)

            val span = document.createElement("span")
            span.innerText = gameKey.displayText
            div.appendChild(span)

            val input = document.createElement("input") as HTMLInputElement
            input.classList.add("medInput")
            input.onkeydown = { handleInput(it, input, gameKey) }
            div.appendChild(input)

            val binding = keyboardSettings.gameKeys[gameKey] ?: continue
            updateInput(input, binding)
        }
    }

    private fun handleInput(event: KeyboardEvent<*>, input: HTMLInputElement, gameKey: GameKey) {
        val keybind = eventToKeyBind(event) ?: return
        updateInput(input, keybind)

        LocalStorage.changeConfiguration {
            it.keyboardSettings.gameKeys[gameKey] = keybind
        }

        event.preventDefault()
        event.stopPropagation()
    }

    private fun eventToKeyBind(event: KeyboardEvent<*>): Keybind? {
        if (modifierKeys.contains(event.code)) { return null }
        if (illegalKeys.contains(event.code)) { return null }

        val modifier = if (event.shiftKey) {
            ModifierKey.Shift
        } else if (event.altKey) {
            ModifierKey.Alt
        } else if (event.ctrlKey) {
            ModifierKey.Control
        } else {
            ModifierKey.None
        }

        val code = event.code.toString()
        return Keybind(code, modifier)
    }

    private fun updateInput(input: HTMLInputElement, keybind: Keybind) {
        val prefix = when (keybind.modifierKey) {
            ModifierKey.None -> ""
            ModifierKey.Shift -> "Shift + "
            ModifierKey.Alt -> "Alt + "
            ModifierKey.Control -> "Control + "
        }

        input.value = "${prefix}${keybind.keyCode}"
    }

    private fun toBindings(row: Int, keyboardSettings: KeyboardSettings): ArrayList<Keybind> {
        return when (row) {
            1 -> keyboardSettings.hotbar1Bindings
            2 -> keyboardSettings.hotbar2Bindings
            3 -> keyboardSettings.hotbar3Bindings
            else -> throw IllegalStateException("Invalid row: $row")
        }
    }

    private fun defaultKeyCode(gameKey: GameKey): KeyCode {
        return when (gameKey) {
            GameKey.UiLeft -> Key.ArrowLeft
            GameKey.UiRight -> Key.ArrowRight
            GameKey.UiUp -> Key.ArrowUp
            GameKey.UiDown -> Key.ArrowDown
            GameKey.UiEnter -> Key.Enter
            GameKey.UiExit -> Key.Escape
            GameKey.CamLeft -> Key.ArrowLeft
            GameKey.CamRight -> Key.ArrowRight
            GameKey.CamUp -> Key.ArrowUp
            GameKey.CamDown -> Key.ArrowDown
            GameKey.CamZoomIn -> Key.PageUp
            GameKey.CamZoomOut -> Key.PageDown
            GameKey.MoveForward -> Key.KeyW
            GameKey.MoveLeft -> Key.KeyA
            GameKey.MoveBackward -> Key.KeyS
            GameKey.MoveRight -> Key.KeyD
            GameKey.Disengage -> Key.KeyH
            GameKey.TargetLock -> Key.KeyH
            GameKey.TargetCycle -> Key.Tab
            GameKey.Rest -> Key.KeyH
            GameKey.ToggleMap -> Key.KeyM
            GameKey.FaceTarget -> Key.KeyG
            GameKey.UiContext -> Key.KeyF
            GameKey.Pause -> Key.KeyP
            GameKey.TimeSlow -> Key.Space
            GameKey.Autorun -> Key.KeyR
            GameKey.DebugClip -> Key.Slash
            GameKey.DebugGravity -> Key.Period
            GameKey.DebugSpeed -> Key.Comma
            GameKey.TargetSelf -> Key.F1
            GameKey.TargetParty2 -> Key.F2
            GameKey.TargetParty3 -> Key.F3
            GameKey.TargetParty4 -> Key.F4
            GameKey.TargetParty5 -> Key.F5
            GameKey.TargetParty6 -> Key.F6
            GameKey.OpenMainMenu -> Key.Minus
            GameKey.OpenEquipMenu -> Key.KeyC
            GameKey.OpenInventoryMenu -> Key.KeyI
            GameKey.UiConfig -> Key.KeyU
        }
    }

}