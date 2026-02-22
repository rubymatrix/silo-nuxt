package xim.poc.game.configuration.v0.escha

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.GameV0Helpers.hasAnyEnmity
import xim.poc.game.configuration.v0.GameV0SaveStateHelper
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.game.event.InitialActorState
import xim.poc.gl.ByteColor
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.MapDrawer
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

private fun makeScript(destination: Vector3f): EventScript {
    return EventScript(
        listOf(
            ActorRoutineEventItem(fileTableIndex = 0x114a0, actorId = ActorStateManager.playerId, eagerlyComplete = true),
            WaitRoutine(Fps.secondsToFrames(1.9f)),
            FadeOutEvent(1.seconds),
            WarpSameZoneEventItem(destination),
            RunOnceEventItem { ActorManager.player().renderState.effectColor = ByteColor.zero },
            WaitRoutine(Fps.secondsToFrames(0.2f)),
            ActorRoutineEventItem(fileTableIndex = 0x114a1, actorId = ActorStateManager.playerId, eagerlyComplete = true),
            FadeInEvent(1.seconds),
        )
    )
}

class EschaPortalEntity(
    val configuration: EschaPortalConfiguration,
    val zoneConfiguration: EschaConfiguration,
): FloorEntity {

    private val promise = spawn()

    override fun update(elapsedFrames: Float) { }

    override fun cleanup() {
        promise.cleanup()
    }

    private fun spawn(): ActorPromise {
        return GameEngine.submitCreateActorState(
            InitialActorState(
                name = configuration.displayName,
                type = ActorType.Effect,
                position = configuration.location,
                modelLook = ModelLook.npc(0x9BB),
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
                maxTargetDistance = 10f,
            )
        ).onReady {
            GameV0.interactionManager.registerInteraction(it.id, EschaPortalInteraction(configuration, zoneConfiguration))
        }
    }
}

class EschaPortalInteraction(
    val eschaPortalConfiguration: EschaPortalConfiguration,
    val zoneConfiguration: EschaConfiguration,
): NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        if (hasAnyEnmity(ActorStateManager.playerId)) {
            ChatLog("Now is not the time!", ChatLogColor.Error)
            return
        }

        val ziTahState = GameV0SaveStateHelper.getState().eschaZiTahState
        if (!ziTahState.visitedPortals.contains(eschaPortalConfiguration.index)) {
            ziTahState.visitedPortals += eschaPortalConfiguration.index
            MiscEffects.playExclamationProc(ActorStateManager.playerId, ExclamationProc.White)
            ChatLog("Enabled ${eschaPortalConfiguration.displayName}!", ChatLogColor.SystemMessage)
        }

        UiStateHelper.openQueryMode(
            prompt = "Where will you go?",
            options = getOptions(),
            callback = this::handleQueryResponse,
            drawFn = this::drawMapMarker
        )
    }

    private fun getOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()
        options += QueryMenuOption("Nowhere.", value = eschaPortalConfiguration.index)

        val ziTahState = GameV0SaveStateHelper.getState().eschaZiTahState

        for (i in zoneConfiguration.portals.indices) {
            val portalConfiguration = zoneConfiguration.portals[i]
            if (portalConfiguration == eschaPortalConfiguration) { continue }
            if (!ziTahState.visitedPortals.contains(portalConfiguration.index)) { continue }
            options += QueryMenuOption(portalConfiguration.displayName, i)
        }

        return options
    }

    private fun handleQueryResponse(response: QueryMenuOption?): QueryMenuResponse {
        if (response == null || response.value == eschaPortalConfiguration.index) { return QueryMenuResponse.pop }

        val portal = zoneConfiguration.portals.getOrNull(response.value) ?: return QueryMenuResponse.popAll

        val script = makeScript(portal.destination)
        EventScriptRunner.runScript(script)

        return QueryMenuResponse.popAll
    }

    private fun drawMapMarker(currentOption: QueryMenuOption?) {
        currentOption ?: return
        val selectedPortal = zoneConfiguration.portals[currentOption.value]

        MapDrawer.drawMapMarker(selectedPortal.location, uiElementIndex = 155, priority = 1)
        MapDrawer.forceDraw()
    }

}
