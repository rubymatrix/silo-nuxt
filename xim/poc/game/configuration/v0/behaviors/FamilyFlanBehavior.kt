package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.CombatStat
import xim.poc.game.configuration.ActorDamagedContext
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillAmorphicSpikes_1568
import xim.poc.game.event.AttackDamageType
import xim.poc.game.event.Event
import xim.util.FrameTimer
import xim.util.addInPlace
import xim.util.multiplyInPlace
import kotlin.time.Duration.Companion.seconds

private enum class FlanState(val appearanceState: Int) {
    Smooth(0),
    Spiky(2),
}

class FamilyFlanBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val damageTracker = HashMap<AttackDamageType, Int>()
    private val formChangeCooldown = FrameTimer(15.seconds)

    init {
        spellTimer = FrameTimer(20.seconds)
    }

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isEngaged()) { manageForm(elapsedFrames) }
        return super.update(elapsedFrames)
    }

    override fun onDamaged(context: ActorDamagedContext): List<Event> {
        damageTracker.addInPlace(context.damageType, context.damageAmount)
        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        when (getForm()) {
            FlanState.Smooth -> {
                aggregate.magicalDamageTaken -= 25
                aggregate.physicalDamageTaken += 25
                aggregate.multiplicativeStats.multiplyInPlace(CombatStat.int, 1.1f)
            }
            FlanState.Spiky -> {
                aggregate.magicalDamageTaken += 25
                aggregate.physicalDamageTaken -= 25
                aggregate.multiplicativeStats.multiplyInPlace(CombatStat.str, 1.1f)
            }
        }
    }

    override fun getSkills(): List<SkillId> {
        return super.getSkills() + when (getForm()) {
            FlanState.Smooth -> emptyList()
            FlanState.Spiky -> listOf(mskillAmorphicSpikes_1568)
        }
    }

    private fun manageForm(elapsedFrames: Float) {
        formChangeCooldown.update(elapsedFrames)
        if (!formChangeCooldown.isReady()) { return }

        formChangeCooldown.reset()

        val physicalDamage = damageTracker[AttackDamageType.Physical] ?: 0
        val magicalDamage = damageTracker[AttackDamageType.Magical] ?: 0

        damageTracker.clear()

        val form = if (physicalDamage > magicalDamage) { FlanState.Spiky } else { FlanState.Smooth }
        actorState.appearanceState = form.appearanceState
    }

    private fun getForm(): FlanState {
        return FlanState.values().first { it.appearanceState == actorState.appearanceState }
    }

}