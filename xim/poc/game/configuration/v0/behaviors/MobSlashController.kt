package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.GameEngine
import xim.poc.game.configuration.ActorDamagedContext
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.event.AttackDamageType
import xim.poc.game.event.Event
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.util.Fps
import xim.util.addInPlace
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private data class DamageMeasure(
    var autoAttackDamage: Int = 0,
    var damageBySkill: MutableMap<SkillId, Int> = HashMap(),
    var damageBySkillChain: Int = 0,
)

open class DpsMeasure(actorState: ActorState): V0MonsterController(actorState) {

    private val totalDamageMeasure = DamageMeasure()
    private var timeSinceEngaged = 0f

    private var hpAtLastReport = -1
    private var lastReport = 0f
    private var measureSinceLastReport = DamageMeasure()

    override fun update(elapsedFrames: Float): List<Event> {
        if (hpAtLastReport < 0) { hpAtLastReport = actorState.getHp() }

        if (actorState.isEngaged()) {
            timeSinceEngaged += elapsedFrames
            maybeEmitDps()
        }

        return super.update(elapsedFrames)
    }

    override fun onDefeated(): List<Event> {
        val time = Fps.framesToSeconds(timeSinceEngaged)
        val damagePerMs = actorState.getMaxHp().toDouble() / time.inWholeMilliseconds.toDouble()

        val info = """
            === Defeated in: $time. DPS: ${damagePerMs*1000} ===
            Auto-attack: ${totalDamageMeasure.autoAttackDamage}
            Skillchain: ${totalDamageMeasure.damageBySkillChain}
            ${totalDamageMeasure.damageBySkill.mapKeys { GameEngine.getSkillLogName(it.key) }}
        """.trimIndent()

        ChatLog(info, chatLogColor = ChatLogColor.SystemMessage)
        console.info(info)

        return emptyList()
    }

    override fun onDamaged(context: ActorDamagedContext): List<Event> {
        listOf(totalDamageMeasure, measureSinceLastReport).forEach {
            if (context.damageType == AttackDamageType.SkillChain) {
                it.damageBySkillChain += context.damageAmount
            } else if (context.damageType == AttackDamageType.Physical && context.skill == null) {
                it.autoAttackDamage += context.damageAmount
            } else if (context.skill != null) {
                it.damageBySkill.addInPlace(context.skill, context.damageAmount, defaultValue = 0)
            }
        }

        return emptyList()
    }

    private fun maybeEmitDps() {
        val timeSinceLastReport = Fps.framesToSeconds(timeSinceEngaged - lastReport)
        if (timeSinceLastReport < 10.seconds) { return }

        val hpDiff = hpAtLastReport - actorState.getHp()

        val info = """
            === Damage: $hpDiff | DPS: ${(hpDiff.toDouble() / timeSinceLastReport.inWholeMilliseconds.toDouble() * 1000).roundToInt()} ===
            Auto-attack: ${measureSinceLastReport.autoAttackDamage}
            Skillchain: ${measureSinceLastReport.damageBySkillChain}
            ${measureSinceLastReport.damageBySkill.mapKeys { GameEngine.getSkillLogName(it.key) }}
        """.trimIndent()

        console.info(info)

        measureSinceLastReport = DamageMeasure()

        hpAtLastReport = actorState.getHp()
        lastReport = timeSinceEngaged
    }

}

class MobSlashController(actorState: ActorState): DpsMeasure(actorState) {

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun wantsToCastSpell(): Boolean {
        return false
    }

    override fun wantsToUseSkill(): Boolean {
        return false
    }

}