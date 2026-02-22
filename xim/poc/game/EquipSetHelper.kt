package xim.poc.game

import xim.poc.browser.LocalStorage
import xim.poc.game.actor.components.Equipment

object EquipSetHelper {

    private val styleSheet by lazy { loadStyleSheet() }

    fun getStyleSheet(): Equipment {
        return styleSheet
    }

    fun isStyleSheet(equipment: Equipment): Boolean {
        return styleSheet === equipment
    }

    private fun loadStyleSheet(): Equipment {
        return LocalStorage.getPlayerEquipmentSet(0) ?: throw IllegalStateException("Equip-set wasn't initialized")
    }

}