package xim.poc.game.configuration.assetviewer

import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.GameEngine
import xim.poc.game.PartyManager
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.CastAbilityStart
import xim.poc.game.event.CastRangedAttackStart
import xim.poc.game.event.CastSpellStart
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

object AssetViewerTrustBehaviors {

    fun register() {
        ActorBehaviors.register(TrustBehaviorId(spellKupipi_898.id)) { HealerBehavior(it) }
        ActorBehaviors.register(TrustBehaviorId(spellCurilla_902.id)) { CurillaBehavior(it) }
        ActorBehaviors.register(TrustBehaviorId(spellJoachim_911.id)) { RangedAttackerBehavior(it) }
        ActorBehaviors.register(TrustBehaviorId(spellSemihLafihna_940.id)) { RangedAttackerBehavior(it) }
        ActorBehaviors.register(TrustBehaviorId(spellTenzenII_1014.id)) { RangedAttackerBehavior(it) }
    }

}

interface TrustBehaviorController: ActorBehaviorController {

    fun getEngagedDistance(): Pair<Float, Float>? {
        return null
    }

}

class HealerBehavior(val actor: ActorState): TrustBehaviorController {

    private val healingSpell = spellCure_1

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actor.isIdleOrEngaged()) { return emptyList() }

        val party = PartyManager[actor.id]

        val members = party.getAllState()
        val lowestHealthMember = members.minByOrNull { it.getHpp() } ?: return emptyList()

        if (lowestHealthMember.getHpp() > 0.5f) { return emptyList() }
        return listOf(CastSpellStart(actor.id, lowestHealthMember.id, healingSpell))
    }

    override fun getEngagedDistance(): Pair<Float, Float> {
        return Pair(8f, 9f)
    }

}

class RangedAttackerBehavior(val actor: ActorState): TrustBehaviorController {

    private val cooldown = FrameTimer(1.seconds, ZERO)

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actor.isIdleOrEngaged()) { return emptyList() }
        if (!actor.isEngaged()) { return emptyList() }

        if (!GameEngine.canBeginSkill(actor.id, rangedAttack)) { return emptyList() }

        cooldown.update(elapsedFrames)
        if (cooldown.isNotReady()) { return emptyList() }
        cooldown.reset()

        val targetId = actor.targetState.targetId ?: return emptyList()
        return listOf(CastRangedAttackStart(actor.id, targetId))
    }

    override fun getEngagedDistance(): Pair<Float, Float> {
        return Pair(8f, 9f)
    }

}

class CurillaBehavior(val actor: ActorState): TrustBehaviorController {

    private val autoAttackDelegate = AutoAttackController(actor)

    override fun update(elapsedFrames: Float): List<Event> {
        val output = useProvokeIfNeeded()
        if (output.isNotEmpty()) { return output }

        return autoAttackDelegate.update(elapsedFrames)
    }

    private fun useProvokeIfNeeded(): List<Event> {
        if (!actor.isIdleOrEngaged()) { return emptyList() }
        if (!actor.isEngaged()) { return emptyList() }

        val target = ActorStateManager[actor.targetState.targetId] ?: return emptyList()
        if (target.targetState.targetId == actor.id) { return emptyList() }

        return listOf(CastAbilityStart(actor.id, target.id, skillProvoke_547))
    }

}
