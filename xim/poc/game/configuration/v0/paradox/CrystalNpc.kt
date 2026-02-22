package xim.poc.game.configuration.v0.paradox

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.ModelLook
import xim.poc.NoOpActorController
import xim.poc.game.ActorPromise
import xim.poc.game.ActorStateManager
import xim.poc.game.ActorType
import xim.poc.game.GameEngine
import xim.poc.game.configuration.*
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.game.event.InitialActorState
import xim.poc.gl.ByteColor
import xim.poc.gl.Color
import xim.poc.tools.ZoneConfig
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.ShiftJis
import xim.resource.Particle
import xim.util.Fps
import kotlin.time.Duration.Companion.seconds

private val baseColor = Color(ByteColor(0xBC, 0xBC, 0xBC, 0x80))

enum class CrystalType(val displayName: String, val color: Color, val exitName: String = displayName,) {
    Flames("Fire Protocrystal", Color(ByteColor(0xF0, 0x3A, 0x1C, 0x80))),
    Frost("Ice Protocrystal", Color(ByteColor(0x8C ,0xF0, 0xF0, 0x80))),
    Gales("Wind Protocrystal", Color(ByteColor(0x44, 0xF0, 0x76, 0x80))),
    Tremors("Earth Protocrystal", Color(ByteColor(0xF0, 0xBE, 0x32, 0x80))),
    Storms("Thunder Protocrystal", Color(ByteColor(0xB4, 0x58, 0xE6, 0x80))),
    Tides("Water Protocrystal", Color(ByteColor(0x42, 0x4C, 0xF0, 0x80))),
    Light("Light Protocrystal", Color(ByteColor(0xBC, 0xBC, 0xBC, 0x80)), "Moon Spiral"),
    Dark("Dark Protocrystal", Color(ByteColor(0x40, 0x40, 0x40, 0x80)), "Moon Spiral"),
}

class CrystalNpc(val location: Vector3f, val type: CrystalType): FloorEntity {

    val promise = spawn()

    override fun update(elapsedFrames: Float) {
        val actor = ActorManager[promise.getIfReady()] ?: return
        actor.effectOverride.colorOverride = this::colorFunction
    }

    override fun cleanup() {
        promise.onReady { GameEngine.submitDeleteActor(it.id) }
    }

    private fun colorFunction(particle: Particle, color: Color): Color {
        particle.specularParams = particle.specularParams?.copy(color = Color(
            r = 0.05f * type.color.r() / baseColor.r(),
            g = 0.05f * type.color.g() / baseColor.g(),
            b = 0.05f * type.color.b() / baseColor.b(),
            a = 1f,
        ))

        return Color(
            r = 0.80f * color.r() * type.color.r() / baseColor.r(),
            g = 0.80f * color.g() * type.color.g() / baseColor.g(),
            b = 0.80f * color.b() * type.color.b() / baseColor.b(),
            a = color.a(),
        )
    }

    private fun spawn(): ActorPromise {
        return GameEngine.submitCreateActorState(InitialActorState(
            name = type.displayName,
            type = ActorType.StaticNpc,
            position = location,
            modelLook = ModelLook.npc(0x971),
            movementController = NoOpActorController(),
            behaviorController = NoActionBehaviorId,
        )).onReady { GameV0.interactionManager.registerInteraction(it.id, CrystalInteraction(this)) }
    }

}

class CrystalInteraction(val crystalNpc: CrystalNpc): NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        if (!canEnter()) { return }

        val zoneDef = CloisterDefinitions[crystalNpc.type]

        val script = EventScript(
            listOf(
                ActorRoutineEventItem(fileTableIndex = 0x1139F, ActorStateManager.playerId, eagerlyComplete = true),
                WaitRoutine(Fps.secondsToFrames(1.9f)),
                FadeOutEvent(1.seconds),
                WarpZoneEventItem(ZoneConfig(zoneId = zoneDef.zoneId), fade = false),
                RunOnceEventItem { GameV0.setZoneLogic { CloisterZoneInstance(crystalNpc.type) } },
                WaitRoutine(Fps.secondsToFrames(0.2f)),
                RunOnceEventItem { ActorManager.player().renderState.effectColor = ByteColor.zero },
                ActorRoutineEventItem(fileTableIndex = 0x113A0, actorId = ActorStateManager.playerId, eagerlyComplete = true),
                FadeInEvent(1.seconds),
            )
        )

        EventScriptRunner.runScript(script)
    }

    private fun canEnter(): Boolean {
        if (crystalNpc.type != CrystalType.Dark && crystalNpc.type != CrystalType.Light) { return true }

        val remaining = CloisterDefinitions.definitions.filter { it.key != CrystalType.Dark && it.key != CrystalType.Light }
            .filter { !GameV0Helpers.hasDefeated(it.value.monsterId) }

        if (remaining.isEmpty()) { return true }

        ChatLog("Nothing happens...\nYou sense malicious energy from the: ${remaining.keys.joinToString { "\n  ${ShiftJis.solidSquare}" + it.displayName }}", ChatLogColor.Info)
        return false
    }

}