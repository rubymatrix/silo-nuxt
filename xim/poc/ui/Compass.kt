package xim.poc.ui

import xim.math.Matrix4f
import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.*
import xim.poc.camera.CameraReference
import kotlin.math.atan2

object Compass {

    fun draw() {
        drawWeather()
        drawCompass()
        drawClock()
    }

    private fun drawCompass() {
        val camera = CameraReference.getInstance()
        val areaTransform = SceneManager.getCurrentScene().getAreaTransform()

        val viewCamera = if (areaTransform != null) { camera.transform(areaTransform) } else { camera }

        val viewDir = viewCamera.getViewVector().withY(0f).normalize()
        val rot = -atan2(viewDir.z, viewDir.x)

        val transform = Matrix4f().rotateYInPlace(rot)
        val horizontalOffset = transform.transformInPlace(Vector3f(32f, 0f, 0f))
        val verticalOffset = transform.transformInPlace(Vector3f(0f, 0f, 32f))

        val basePosition = MenuStacks.LogStack.menuStack.currentPosition + Vector2f(64f, -64f)

        UiElementHelper.drawUiElement("menu    compass ", index = 0, position = basePosition, scale = Vector2f(0.25f, 0.25f), rotation = -rot)
        UiElementHelper.drawUiElement("menu    compass ", index = 1, position = basePosition + Vector2f(-verticalOffset.x, -verticalOffset.z))
        UiElementHelper.drawUiElement("menu    compass ", index = 2, position = basePosition + Vector2f(+verticalOffset.x, +verticalOffset.z))
        UiElementHelper.drawUiElement("menu    compass ", index = 3, position = basePosition + Vector2f(+horizontalOffset.x, +horizontalOffset.z))
        UiElementHelper.drawUiElement("menu    compass ", index = 4, position = basePosition + Vector2f(-horizontalOffset.x, -horizontalOffset.z))
    }

    private fun drawClock() {
        val basePosition = MenuStacks.LogStack.menuStack.currentPosition + Vector2f(20f, -20f)

        val dayOfWeekIndex = 106 + when (EnvironmentManager.getDayOfWeek()) {
            DayOfWeek.Fire -> 0
            DayOfWeek.Earth -> 3
            DayOfWeek.Water -> 5
            DayOfWeek.Wind -> 2
            DayOfWeek.Ice -> 1
            DayOfWeek.Lightning -> 4
            DayOfWeek.Light -> 6
            DayOfWeek.Dark -> 7
        }

        UiElementHelper.drawUiElement("menu    frames  ", index = dayOfWeekIndex, basePosition)

        val seconds = EnvironmentManager.getClock().currentTimeOfDayInSeconds()
        val minutes = ((seconds / 60) % 60).toString().padStart(2, '0')
        val hours = ((seconds / 60) / 60).toString()

        val coordinates = MapDrawer.getPlayerMapCoordinates()

        UiElementHelper.drawString("${hours}:${minutes}   (${coordinates.first}-${coordinates.second})", offset = basePosition + Vector2f(18f, 0f), Font.FontShp)
    }

    private fun drawWeather() {
        val currentWeather = EnvironmentManager.getWeather()
        val weather = Weather.values().firstOrNull { it.id == currentWeather.id } ?: return

        val indices = when (weather) {
            Weather.fine -> emptyList()
            Weather.suny -> emptyList()
            Weather.clod -> emptyList()
            Weather.mist -> emptyList()
            Weather.dryw -> listOf(0)
            Weather.heat -> listOf(0, 0)
            Weather.snow -> listOf(1)
            Weather.bliz -> listOf(1, 1)
            Weather.wind -> listOf(2)
            Weather.stom -> listOf(2, 2)
            Weather.dust -> listOf(3)
            Weather.sand -> listOf(3, 3)
            Weather.thdr -> listOf(4)
            Weather.bolt -> listOf(4, 4)
            Weather.rain -> listOf(5)
            Weather.squl -> listOf(5, 5)
            Weather.aura -> listOf(6)
            Weather.ligt -> listOf(6, 6)
            Weather.fogd -> listOf(7)
            Weather.dark -> listOf(7, 7)
        }

        val basePosition = MenuStacks.LogStack.menuStack.currentPosition + Vector2f(24f, -108f)

        for (i in indices.indices) {
            UiElementHelper.drawUiElement("font    usgaiji ", index = indices[i], basePosition + Vector2f(16f, 0f) * i.toFloat())
        }
    }

}