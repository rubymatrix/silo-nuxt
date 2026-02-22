package xim.poc.game

import xim.poc.MainTool
import xim.poc.browser.GameKey
import xim.poc.game.configuration.GameLogic
import xim.poc.game.configuration.assetviewer.AssetViewer

object GameState {

    var gameSpeed = 1.0f
        private set

    private var gameLogic: GameLogic = AssetViewer

    fun update(elapsedFrames: Float) {
        gameLogic.update(elapsedFrames)
        updateGameSpeed()
    }

    fun setGameMode(gameLogic: GameLogic) {
        this.gameLogic = gameLogic
    }

    fun getGameMode(): GameLogic {
        return gameLogic
    }

    fun isDebugMode(): Boolean {
        return gameLogic.configuration.debugControlsEnabled
    }

    private fun updateGameSpeed() {
        if (MainTool.platformDependencies.keyboard.isKeyPressed(GameKey.Pause)) {
            gameSpeed = if (gameSpeed != 0f) { 0f } else { 1f }
        } else if (MainTool.platformDependencies.keyboard.isKeyPressed(GameKey.TimeSlow)) {
            gameSpeed = if (gameSpeed != 0.25f) { 0.25f } else { 1f }
        }
    }

}