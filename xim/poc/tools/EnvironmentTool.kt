package xim.poc.tools

import kotlinx.browser.document
import kotlinx.dom.clear
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import xim.poc.DayOfWeek
import xim.poc.EnvironmentManager
import xim.poc.MoonPhase
import xim.resource.DatId
import kotlin.js.Date
import kotlin.math.roundToInt

object EnvironmentTool {

    fun update() {
        if (isCheckBox("todOverride")) { return }

        val hourInput = document.getElementById("todHour") as HTMLInputElement
        hourInput.value = (EnvironmentManager.getClock().currentTimeOfDayInSeconds() / 60 / 60).toString()

        val minuteInput = document.getElementById("todMinute") as HTMLInputElement
        minuteInput.value = (EnvironmentManager.getClock().currentTimeOfDayInSeconds() / 60 % 60).toString()

        val moonPhaseSelect = document.getElementById("moonPhase") as HTMLSelectElement
        moonPhaseSelect.value = EnvironmentManager.moonPhase.index.toString()

        val daySelect = document.getElementById("day") as HTMLSelectElement
        daySelect.value = EnvironmentManager.dayOfWeek.index.toString()

        val weatherSelect = document.getElementById("weatherSelect") as HTMLSelectElement
        weatherSelect.value = EnvironmentManager.getWeather().id
    }

    fun setup() {
        val moonPhaseSelect = document.getElementById("moonPhase") as HTMLSelectElement
        moonPhaseSelect.onchange = {
            EnvironmentManager.moonPhase = MoonPhase.values().first { it.index == moonPhaseSelect.value.toInt() }
            Unit
        }

        val daySelect = document.getElementById("day") as HTMLSelectElement
        daySelect.onchange = {
            EnvironmentManager.dayOfWeek = DayOfWeek.values().first { it.index == daySelect.value.toInt() }
            Unit
        }

        val weatherSelect = document.getElementById("weatherSelect") as HTMLSelectElement
        weatherSelect.clear()
        EnvironmentManager.weatherTypes.forEach {
            val option = document.createElement("option") as HTMLOptionElement
            option.value = it.id
            option.text = it.id
            weatherSelect.appendChild(option)
        }

        weatherSelect.onchange = { EnvironmentManager.switchWeather(DatId(weatherSelect.value)) }

        val hourInput = document.getElementById("todHour") as HTMLInputElement
        hourInput.onchange = { EnvironmentManager.setCurrentHour(hourInput.value.toInt()) }

        val minuteInput = document.getElementById("todMinute") as HTMLInputElement
        minuteInput.onchange = {
            if (minuteInput.value.toInt() == 60) {
                hourInput.value = ((hourInput.value.toInt() + 1) % 24).toString()
                minuteInput.value = 0.toString()
            } else if (minuteInput.value.toInt() == -1) {
                hourInput.value = ((hourInput.value.toInt() - 1) % 24).toString()
                minuteInput.value = 59.toString()
            }

            if (isCheckBox("todOverride")) {
                EnvironmentManager.setCurrentHour(hourInput.value.toInt())
                EnvironmentManager.setCurrentMinute(minuteInput.value.toInt())
            }
        }

        if (isCheckBox("todOverride")) {
            EnvironmentManager.setCurrentHour(hourInput.value.toInt())
            EnvironmentManager.setCurrentMinute(minuteInput.value.toInt())
        } else {
            val date = Date()

            val seconds = (date.getHours() * 60 * 60) + (date.getMinutes() * 60) + date.getSeconds()
            val inGameMinutes = (seconds / 2.4).roundToInt()

            EnvironmentManager.setCurrentMinute(inGameMinutes % 60)
            EnvironmentManager.setCurrentHour((inGameMinutes/60) % 24)
        }
    }

}