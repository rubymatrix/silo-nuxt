package xim.poc.ui

import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.*
import xim.poc.game.*
import xim.poc.game.GameEngine.displayName
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.event.ActorDamagedEvent
import xim.poc.game.event.ActorHealedEvent
import xim.poc.gl.ByteColor
import xim.resource.table.StatusEffectNameTable
import xim.util.Fps
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private sealed interface FlyoverText {
    val worldSpacePosition: Vector3f
    var textAge: Float
    var alpha: Float
}

private data class DamageText(
    override val worldSpacePosition: Vector3f,
    val color: ByteColor,
    val skillId: SkillId?,
    val amount: Int,
    val scale: Float,
    val player: Boolean,
    val skillChainAttribute: SkillChainAttribute? = null,
    val status: StatusEffectState? = null,
): FlyoverText {
    override var textAge: Float = 0f
    override var alpha: Float = 1f
}

private data class StatusText(
    override val worldSpacePosition: Vector3f,
    val color: ByteColor,
    val status: StatusEffectState,
): FlyoverText {
    override var textAge: Float = 0f
    override var alpha: Float = 1f
}

object DamageTextManager {

    private val texts = ArrayDeque<FlyoverText>()
    private var concurrentTextOffset = 0f

    fun append(event: ActorDamagedEvent) {
        val position = getWorldSpaceDisplay(event.targetId) ?: return

        val player = event.targetId == ActorStateManager.playerId

        val color = if (player) {
            ByteColor(0x80, 0x40, 0x30, 0x80)
        } else {
            ByteColor(0x80, 0x70, 0x60, 0x80)
        }

        val scale = if (player) {
            val playerState = ActorStateManager.player()
            1f + (event.amount.toFloat() / playerState.getMaxHp()).coerceAtMost(1f)
        } else {
            1f
        }

        texts += DamageText(
            worldSpacePosition = position,
            color = color,
            skillId = event.skill,
            amount = event.amount,
            player = player,
            scale = scale,
            skillChainAttribute = event.actionContext?.skillChainStep?.attribute,
        )
    }

    fun append(event: ActorHealedEvent) {
        val position = getWorldSpaceDisplay(event.targetId) ?: return

        val player = event.targetId == ActorStateManager.playerId

        val color = if (player) {
            ByteColor(0x20, 0x50, 0x80, 0x80)
        } else {
            ByteColor(0x60, 0x70, 0x80, 0x80)
        }

        texts += DamageText(
            worldSpacePosition = position,
            color = color,
            skillId = event.actionContext?.skill,
            amount = event.amount,
            player = player,
            scale = 1f
        )
    }

    fun statusEvent(actorState: ActorState, statusEffectState: StatusEffectState) {
        if (!actorState.isPlayer()) { return }
        val position = getWorldSpaceDisplay(actorState.id) ?: return

        val color = if (statusEffectState.statusEffect.debuff) {
            ByteColor(0x80, 0x40, 0x30, 0x80)
        } else {
            ByteColor(0x20, 0x50, 0x80, 0x80)
        }

        texts += StatusText(
            worldSpacePosition = position,
            color = color,
            status = statusEffectState,
        )
    }

    fun draw(elapsedFrames: Float) {
        concurrentTextOffset = 0f
        update(elapsedFrames)

        if (GameState.getGameMode() != GameV0) { return }
        texts.takeLast(16).forEach(this::draw)
    }

    private fun getWorldSpaceDisplay(actorId: ActorId): Vector3f? {
        val actor = ActorManager[actorId] ?: return null

        val position = Vector3f(actor.displayPosition)
        val yOffset = actor.getScale() * (actor.actorModel?.getSkeleton()?.resource?.size?.y ?: 0f)
        position.y += yOffset.coerceIn(-2f, 2f)

        position.y -= concurrentTextOffset
        concurrentTextOffset -= 0.15f

        return position
    }

    private fun update(elapsedFrames: Float) {
        texts.forEach {
            it.textAge += elapsedFrames
            it.worldSpacePosition.y -= 0.5f * (elapsedFrames/60f)
            if (it.textAge > Fps.toFrames(1.seconds)) { it.alpha -= elapsedFrames/60f }
        }

        texts.removeAll { it.textAge >= Fps.toFrames(2.seconds) }
    }

    private fun draw(flyoverText: FlyoverText) {
        when (flyoverText) {
            is DamageText -> draw(flyoverText)
            is StatusText -> draw(flyoverText)
        }
    }

    private fun draw(damageText: DamageText) {
        val skillLogName = damageText.skillId?.displayName()
            ?: damageText.skillChainAttribute?.name
            ?: ""

        val alpha = getFade(damageText)
        val screenPosition = toScreenPosition(damageText) ?: return

        val scale = Vector2f(damageText.scale, damageText.scale)

        UiElementHelper.drawString(
            text = "$skillLogName ${damageText.amount}",
            offset = screenPosition,
            font = Font.FontShp,
            color = damageText.color.withAlpha(alpha),
            scale = scale,
        )
    }

    private fun draw(statusText: StatusText) {
        val alpha = getFade(statusText)
        val screenPosition = toScreenPosition(statusText) ?: return

        UiElementHelper.drawStatusEffect(statusEffectState = statusText.status, position = screenPosition, color = ByteColor.half.withAlpha(alpha))

        UiElementHelper.drawString(
            text = StatusEffectNameTable.first(statusText.status.statusEffect.id),
            offset = screenPosition + Vector2f(20f, 4f),
            font = Font.FontShp,
            color = statusText.color.withAlpha(alpha),
        )
    }

    private fun getFade(flyoverText: FlyoverText): Int {
        return (0x60 * flyoverText.alpha).roundToInt().coerceAtLeast(0)
    }

    private fun toScreenPosition(flyoverText: FlyoverText): Vector2f? {
        val screenPosition = flyoverText.worldSpacePosition.toScreenSpace() ?: return null
        screenPosition.x *= MainTool.platformDependencies.screenSettingsSupplier.width
        screenPosition.y *= MainTool.platformDependencies.screenSettingsSupplier.height
        return screenPosition
    }

}