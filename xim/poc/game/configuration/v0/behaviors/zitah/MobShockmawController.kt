package xim.poc.game.configuration.v0.behaviors.zitah

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.game.*
import xim.poc.game.configuration.OccurrenceLimitStrategy
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillSelection
import xim.poc.game.configuration.StatusResistanceTracker
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.intPotency
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.behaviors.zitah.ComboList.combos
import xim.poc.game.event.Event
import xim.poc.game.event.StatusEffectGainedEvent
import xim.util.FrameTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private object ComboList {
    val combos = listOf(
        listOf(mskillAethericPull_3308, mskillEcholocation_2612, mskillDeepSeaDirge_2613),

        listOf(mskillBaleenGurge_2615, mskillWaterspout_2618, mskillAngrySeas_2619),
        listOf(mskillBaleenGurge_2615, mskillWaterspout_2618, mskillAngrySeas_2619),

        listOf(mskillCaudalCapacitor_2614, mskillDepthCharge_2616, mskillBlowholeBlast_2617),
        listOf(mskillCaudalCapacitor_2614, mskillDepthCharge_2616, mskillBlowholeBlast_2617),

        listOf(mskillBaleenGurge_2615, mskillCaudalCapacitor_2614, mskillTharSheBlows_2620),
        listOf(mskillCaudalCapacitor_2614, mskillBaleenGurge_2615, mskillTharSheBlows_2620),
    )
}

class MobShockmawController(actorState: ActorState): V0MonsterController(actorState) {

    private val moveQueue = ArrayDeque<MobSkillId>()
    private val comboExecutionTimer = FrameTimer(1.seconds)

    private var freshCombo = false

    override fun onInitialized(): List<Event> {
        actorState.faceToward(ActorStateManager.player())

        spellTimer = FrameTimer(40.seconds)

        dynamicStatusResistance = StatusResistanceTracker().also {
            it[StatusEffect.Stun] = OccurrenceLimitStrategy(resetInterval = 10.minutes, maxOccurrencesInInterval = 3)
        }

        return emptyList()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actorState.isOccupied() && !actorState.isDead()) {
            comboExecutionTimer.update(elapsedFrames)
            pull(elapsedFrames)
        }
        return super.update(elapsedFrames)
    }

    override fun wantsToUseSkill(): Boolean {
        return readyForCombo() || super.wantsToUseSkill()
    }

    override fun selectSkill(): SkillSelection? {
        if (moveQueue.isEmpty()) { selectCombo() }
        val nextSkill = moveQueue.first()
        val target = ActorStateManager[selectEngageTarget()] ?: return null
        return SkillSelection(nextSkill, target)
    }

    override fun wantsToCastSpell(): Boolean {
        return moveQueue.isEmpty() && super.wantsToCastSpell()
    }

    override fun getSpells(): List<SkillId> {
        return listOf(spellBurstII_213, spellFloodII_215)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill !is MobSkillId) { return emptyList() }

        freshCombo = false
        moveQueue.removeFirst()
        comboExecutionTimer.reset()

        return getSpikesEffect(primaryTargetContext)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify)
        aggregate.spellInterruptDown += 100
        aggregate.refresh += 10

        if (readyForCombo()) { aggregate.tpRequirementBypass = true }

        super.applyMonsterBehaviorBonuses(aggregate)
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        if (freshCombo) { return null }

        if (skill == mskillTharSheBlows_2620) { return 1.seconds }

        val speedUp = 1.0 - actorState.getHpp()
        return 2.seconds - (0.66.seconds * speedUp)
    }

    private fun selectCombo() {
        moveQueue += combos.random()
        freshCombo = true
    }

    private fun readyForCombo(): Boolean {
        return moveQueue.isNotEmpty() && comboExecutionTimer.isReady()
    }

    private fun pull(elapsedFrames: Float) {
        val target = ActorStateManager[actorState.getTargetId()] ?: return

        val facingDirection = Matrix4f().rotateYInPlace(actorState.rotation).transform(Vector3f.X)
        val desiredDest = actorState.position + facingDirection * 4f

        if (Vector3f.distance(target.position, desiredDest) <= 0.5f) { return }

        val d = (desiredDest - target.position).normalizeInPlace()
        target.position += d * 0.125f * elapsedFrames
    }

    private fun getSpikesEffect(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        val statusState = if (primaryTargetContext.skill == mskillCaudalCapacitor_2614) {
            StatusEffectState(StatusEffect.ShockSpikes).also {
                it.potency = intPotency(actorState, 0.05f)
                it.secondaryPotency = 0.33f
                it.setRemainingDuration(9.seconds)
            }
        } else if (primaryTargetContext.skill == mskillBaleenGurge_2615) {
            StatusEffectState(StatusEffect.DelugeSpikes).also {
                it.potency = intPotency(actorState, 0.1f)
                it.setRemainingDuration(9.seconds)
            }
        } else if (primaryTargetContext.skill == mskillAethericPull_3308) {
            StatusEffectState(StatusEffect.DreadSpikes).also {
                it.potency = intPotency(actorState, 0.1f)
                it.setRemainingDuration(9.seconds)
            }
        } else {
            return emptyList()
        }

        return listOf(StatusEffectGainedEvent(
            targetId = actorState.id,
            statusEffectState = statusState,
            context = primaryTargetContext.context
        ))
    }

}