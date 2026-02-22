package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.ActorStateManager
import xim.poc.game.AttackContext
import xim.poc.game.SkillChainStep
import xim.poc.game.configuration.ActorDamagedContext
import xim.poc.game.configuration.constants.SkillId
import xim.poc.ui.ChatLog
import xim.poc.ui.DamageTextManager
import xim.poc.ui.ShiftJis

class ActorDamagedEvent(
    val sourceId: ActorId,
    val targetId: ActorId,
    val amount: Int,
    val type: ActorResourceType = ActorResourceType.HP,
    val emitDamageText: Boolean = true,
    val actionContext: AttackContext? = null,
    val skill: SkillId? = null,
    val skillChainStep: SkillChainStep? = null,
    val damageType: AttackDamageType,
    val additionalEffect: Boolean = false,
): Event {

    override fun apply(): List<Event> {
        val sourceState = ActorStateManager[sourceId] ?: return emptyList()

        val targetState = ActorStateManager[targetId] ?: return emptyList()
        if (targetState.isDead()) { return emptyList() }

        val damageContext = ActorDamagedContext(
            damageAmount = amount,
            attacker = sourceState,
            skill = skill,
            skillChainStep = skillChainStep,
            damageType = damageType,
            actionContext = actionContext,
        )

        val adjustedAmount = targetState.behaviorController.onIncomingDamage(damageContext) ?: amount

        when (type) {
            ActorResourceType.HP -> targetState.consumeHp(adjustedAmount)
            ActorResourceType.MP -> targetState.consumeMp(adjustedAmount)
            ActorResourceType.TP -> targetState.consumeTp(adjustedAmount)
        }

        val defeatedTarget = targetState.isDead()

        val outEvents = ArrayList<Event>()
        outEvents += RestingEndEvent(targetId)
        outEvents += SitChairEndEvent(targetId)

        if (defeatedTarget) {
            outEvents += ActorDefeatedEvent(defeated = targetId, defeatedBy = sourceId, actionContext = actionContext)
        } else {
            outEvents += targetState.behaviorController.onDamaged(damageContext)
        }

        val displayType = when (type) {
            ActorResourceType.HP -> ""
            ActorResourceType.MP -> "MP "
            ActorResourceType.TP -> "TP "
        }

        if (!emitDamageText || actionContext == null) { return outEvents }

        if (additionalEffect) {
            AttackContext.compose(actionContext) {
                ChatLog("Additional Effect: $adjustedAmount ${displayType}damage.", actionContext = actionContext)
                DamageTextManager.append(this)
            }
        } else {
            AttackContext.compose(actionContext) {
                val criticalPunctuation = if (actionContext.criticalHit()) { "!" } else { "." }
                val source = if (sourceState.name.isBlank()) { "" } else { "${sourceState.name}${ShiftJis.rightArrow}" }
                val line = "${source}${targetState.name} $adjustedAmount ${displayType}damage$criticalPunctuation"

                if (skillChainStep != null) {
                    ChatLog.skillChain(line, skillChainStep = skillChainStep)
                } else {
                    ChatLog.addLine(line, actionContext = actionContext)
                }

                DamageTextManager.append(this)
            }
        }

        return outEvents
    }

}