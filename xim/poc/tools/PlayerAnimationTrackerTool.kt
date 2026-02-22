package xim.poc.tools

import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement
import xim.poc.*
import xim.poc.game.ActorStateManager
import xim.poc.game.GameClient
import xim.resource.DatId
import xim.resource.EffectResource
import xim.resource.EffectRoutineResource
import xim.util.toTruncatedString

object PlayerAnimationTrackerTool {

    private var setup = false

    fun update() {
        if (!setup) {
            setup = true
            setup()
        }

        (document.getElementById("clearActors") as HTMLButtonElement).onclick = {
            ActorStateManager.getAll().filterValues { it.monsterId != null || it.isStaticNpc() || it.name == "NpcTool" }
                .forEach { ActorManager.remove(it.key) }
        }

        val playerActor = ActorManager.player()
        val targetActor = ActorManager[playerActor.target] ?: playerActor

        val animCoordinator = targetActor.actorModel?.skeletonAnimationCoordinator ?: return

        for (i in 0..7) {
            val animator = animCoordinator.animations[i]
            if (animator == null) {
                (document.getElementById("anim${i}") as HTMLInputElement).value = "N/A"
                continue
            }

            val transition = animator.transition
            if (transition != null) {
                (document.getElementById("anim${i}") as HTMLInputElement).value = "transition -> ${transition.next.animation.id}"
                continue
            }

            val current = animator.currentAnimation ?: continue
            (document.getElementById("anim${i}") as HTMLInputElement).value = "${current.animation.id} : ${animator.currentAnimation?.currentFrame?.toTruncatedString(2)} (done: ${animator.currentAnimation?.isDoneLooping()})"
        }

        (document.getElementById("moveLocked") as HTMLInputElement).value = targetActor.actorModel!!.isMovementLocked().toString()
        (document.getElementById("animLocked") as HTMLInputElement).value = targetActor.actorModel!!.isAnimationLocked().toString()
    }

    private fun setup() {
        val input = document.getElementById("animInput") as HTMLInputElement
        val button = document.getElementById("animPlay") as HTMLButtonElement

        button.onclick = {
            val routineId = DatId(input.value)

            val player = ActorManager.player()
            val routine = player.actorModel?.model?.let {
                it.getAnimationDirectories() + it.getMainBattleAnimationDirectory() + it.getSubBattleAnimationDirectory() + GlobalDirectory.directoryResource
            }?.filterNotNull()?.firstNotNullOfOrNull { it.getNullableChildRecursivelyAs(routineId, EffectRoutineResource::class) }

            if (routine != null) { EffectManager.registerActorRoutine(player, ActorContext(player.id), routine) } else { println("Didn't find $routineId") }

            if (routine == null) {
                val effect = player.actorModel?.model?.let {
                    it.getAnimationDirectories() + it.getMainBattleAnimationDirectory() + it.getSubBattleAnimationDirectory() + GlobalDirectory.directoryResource
                }?.filterNotNull()?.firstNotNullOfOrNull { it.getNullableChildRecursivelyAs(routineId, EffectResource::class) }

                if (effect != null) { EffectManager.registerEffect(ActorAssociation(player), effect) }
            }
        }

        val appearance = document.getElementById("appearanceInput") as HTMLInputElement
        appearance.value = "0"
        appearance.onchange = { adjustAppearance(appearance.value.toIntOrNull()) }

        val skelPlay = document.getElementById("skelPlay") as HTMLButtonElement
        skelPlay.onclick = { setSkeletonAnimation() }

        val emotePlay = document.getElementById("emotePlay") as HTMLButtonElement
        emotePlay.onclick = { playEmote() }

        val sitChairPlay = document.getElementById("sitChairPlay") as HTMLButtonElement
        sitChairPlay.onclick = { playSitChair() }
    }

    private fun playEmote() {
        val emoteInput0 = document.getElementById("emoteId0") as HTMLInputElement
        val emoteInput1 = document.getElementById("emoteId1") as HTMLInputElement

        val id0 = emoteInput0.value.toIntOrNull() ?: return
        val id1 = emoteInput1.value.toIntOrNull() ?: return

        ActorManager.player().playEmote(id0, id1)
    }

    private fun playSitChair() {
        val chairInput = document.getElementById("sitChairIndex") as HTMLInputElement
        val index = chairInput.value.toIntOrNull() ?: return
        GameClient.submitSitChairRequest(ActorStateManager.playerId, index)
    }

    private fun setSkeletonAnimation() {
        val input = document.getElementById("skelInput") as HTMLInputElement

        val player = ActorManager.player()
        val targetActor = ActorManager[player.target] ?: player

        val model = targetActor.actorModel ?: return

        model.lockAnimation(300f)
        model.setSkeletonAnimation(DatId(input.value), targetActor.getAllAnimationDirectories(), loopParams = LoopParams.lowPriorityLoop(), transitionParams = TransitionParams())
    }

    private fun adjustAppearance(value: Int?) {
        val player = ActorStateManager.player()
        val targetActor = ActorStateManager[player.targetState.targetId] ?: player
        targetActor.appearanceState = value ?: 0
    }

}