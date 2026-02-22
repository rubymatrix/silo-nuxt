package xim.poc.game.configuration.assetviewer

import xim.poc.ActorManager
import xim.poc.FishingAnimationHelper
import xim.poc.FishingArrowAnimation
import xim.poc.FishingArrowAnimation.*
import xim.poc.MainTool
import xim.poc.browser.GameKey
import xim.poc.game.*
import xim.poc.game.FishingState.*
import xim.poc.game.event.FishingEndEvent
import xim.resource.DatId
import xim.util.Fps
import xim.util.FrameTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private enum class ArrowConfig(val start: FishingArrowAnimation, val success: FishingArrowAnimation, val fade: FishingArrowAnimation, val fishDamage: Int) {
    Left(LeftArrow, LeftArrowSuccess, LeftArrowFade, fishDamage = 1),
    Right(RightArrow, RightArrowSuccess, RightArrowFade, fishDamage = 1),
    GoldLeft(GoldLeftArrow, GoldLeftArrowSuccess, GoldLeftArrowFade, fishDamage = 3),
    GoldRight(GoldRightArrow, GoldRightArrowSuccess, GoldRightArrowFade, fishDamage = 3)
}

class FishingAttemptInstance(val actorState: ActorState, val fishingState: ActorFishingAttempt) {

    private val waitingTimer = FrameTimer(period = 8.seconds).resetRandom(lowerBound = 3.seconds)
    private var stateElapsedFrames = 0f

    private var fishHp = 10
    private var fishMaxHp = 10

    private var currentArrowConfig: ArrowConfig = ArrowConfig.Left

    fun update(elapsedFrames: Float) {
        stateElapsedFrames += elapsedFrames
        fishingState.fishHpp = (fishHp.toFloat() / fishMaxHp.toFloat()).coerceAtLeast(0f)

        when (fishingState.currentState) {
            Waiting -> handleWaiting(elapsedFrames)
            Hooked -> handleHooked()
            ActiveCenter -> handleActiveCenter()
            ActiveLeft -> handleActiveLeft()
            ActiveRight -> handleActiveRight()
            SuccessFish -> handleSuccessFish()
            SuccessMonster -> handleSuccessMonster()
            Cancel -> handleCancel()
            BreakRod -> handleBreakRod()
            BreakLine -> handleBreakLine()
        }
    }

    private fun handleWaiting(elapsedFrames: Float) {
        waitingTimer.update(elapsedFrames)
        if (waitingTimer.isNotReady()) { return }

        if (Random.nextDouble() < 0.25) {
            fishingState.fishSize = FishSize.Large
            fishHp = 45
            fishMaxHp = 45
        } else {
            fishingState.fishSize = FishSize.Small
            fishHp = 15
            fishMaxHp = 15
        }

        if (Random.nextDouble() > 0.1) { changeState(Hooked) } else { changeState(Cancel) }
    }

    private fun handleHooked() {
        if (Fps.framesToSeconds(stateElapsedFrames) < 0.5.seconds) { return }

        if (keyPressed(GameKey.UiEnter)) {
            changeState(ActiveCenter)
        } else if (keyPressed(GameKey.MoveBackward, GameKey.MoveForward, GameKey.MoveLeft, GameKey.MoveRight, GameKey.UiExit)) {
            changeState(Cancel)
        }
    }

    private fun handleActiveCenter() {
        if (Fps.framesToSeconds(stateElapsedFrames) < 1.seconds) { return }

        if (fishHp <= 0) {
            changeState(listOf(SuccessFish, SuccessMonster, BreakRod, BreakLine, Cancel).random())
            return
        }

        val sweatAnimation = when (fishingState.fishSize) {
            FishSize.Small -> DatId("hits")
            FishSize.Large -> DatId("hitl")
        }
        ActorManager[actorState.id]?.playRoutine(sweatAnimation)

        if (Random.nextBoolean()) {
            changeState(ActiveRight)
            currentArrowConfig = listOf(ArrowConfig.Left, ArrowConfig.GoldLeft).random()
            FishingAnimationHelper.playArrowAnimation(actorState.id, currentArrowConfig.start)
        } else {
            changeState(ActiveLeft)
            currentArrowConfig = listOf(ArrowConfig.Right, ArrowConfig.GoldRight).random()
            FishingAnimationHelper.playArrowAnimation(actorState.id, currentArrowConfig.start)
        }

    }

    private fun handleActiveLeft() {
        if (Fps.framesToSeconds(stateElapsedFrames) < 0.2.seconds) { return }

        if (keyPressed(GameKey.MoveLeft) || Fps.framesToSeconds(stateElapsedFrames) > 3.seconds) {
            FishingAnimationHelper.playArrowAnimation(actorState.id, currentArrowConfig.fade)
            changeState(ActiveCenter)
        } else if (keyPressed(GameKey.MoveRight)) {
            fishHp -= currentArrowConfig.fishDamage
            FishingAnimationHelper.playArrowAnimation(actorState.id, currentArrowConfig.success)
            changeState(ActiveCenter)
        }
    }

    private fun handleActiveRight() {
        if (Fps.framesToSeconds(stateElapsedFrames) < 0.2.seconds) { return }

        if (keyPressed(GameKey.MoveRight) || Fps.framesToSeconds(stateElapsedFrames) > 3.seconds) {
            FishingAnimationHelper.playArrowAnimation(actorState.id, currentArrowConfig.fade)
            changeState(ActiveCenter)
        } else if (keyPressed(GameKey.MoveLeft)) {
            fishHp -= currentArrowConfig.fishDamage
            FishingAnimationHelper.playArrowAnimation(actorState.id, currentArrowConfig.success)
            changeState(ActiveCenter)
        }
    }

    private fun handleSuccessFish() {
        if (Fps.framesToSeconds(stateElapsedFrames) < 3.25.seconds) { return }
        complete()
    }

    private fun handleSuccessMonster() {
        if (Fps.framesToSeconds(stateElapsedFrames) < 3.25.seconds) { return }
        complete()
    }

    private fun handleCancel() {
        if (Fps.framesToSeconds(stateElapsedFrames) < 3.25.seconds) { return }
        complete()
    }

    private fun handleBreakRod() {
        if (Fps.framesToSeconds(stateElapsedFrames) < 3.25.seconds) { return }
        complete()
    }

    private fun handleBreakLine() {
        if (Fps.framesToSeconds(stateElapsedFrames) < 3.25.seconds) { return }
        complete()
    }

    private fun changeState(newState: FishingState) {
        fishingState.currentState = newState
        stateElapsedFrames = 0f
    }

    private fun complete() {
        GameEngine.submitEvent(FishingEndEvent(actorState.id))
    }

    private fun keyPressed(vararg gameKey: GameKey): Boolean {
        return gameKey.any { MainTool.platformDependencies.keyboard.isKeyPressed(it) }
    }

}