package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.configuration.ActorDamagedContext
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillGasShell_1315
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

class FamilyUragniteController(actorState: ActorState): V0MonsterController(actorState) {

    private val stateTime = FrameTimer(20.seconds)

    private val gasShellCooldown = FrameTimer(5.seconds)
    private var gasShellCounterToggled = false

    override fun update(elapsedFrames: Float): List<Event> {
        if (!actorState.isEngaged()) {
            startShow()
        } else {
            stateTime.update(elapsedFrames)
            gasShellCooldown.update(elapsedFrames)
        }

        if (stateTime.isReady() && actorState.isIdleOrEngaged()) {
            toggleState()
        }

        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        if (!isHiding()) { return }

        aggregate.regen = (actorState.getMaxHp() * 0.05f).roundToInt()
        aggregate.physicalDamageTaken -= 50
        aggregate.magicalDamageTaken -= 50
        aggregate.movementSpeed -= 100
        aggregate.tpRequirementBypass = true
    }

    override fun onReadyToAutoAttack(): List<Event>? {
        return if (isHiding()) { emptyList() } else { super.onReadyToAutoAttack() }
    }

    override fun onDamaged(context: ActorDamagedContext): List<Event> {
        if (isHiding()) { gasShellCounterToggled = true }
        return emptyList()
    }

    override fun wantsToUseSkill(): Boolean {
        return if (isHiding()) { gasShellCounterToggled && gasShellCooldown.isReady() } else { super.wantsToUseSkill() }
    }

    override fun getSkills(): List<SkillId> {
        return if (isHiding()) { listOf(mskillGasShell_1315) } else { super.getSkills() }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillGasShell_1315) {
            gasShellCounterToggled = false
            gasShellCooldown.reset()
        }
        return emptyList()
    }

    private fun startHide() {
        actorState.appearanceState = 1
        stateTime.reset()
        gasShellCooldown.reset()
    }

    private fun startShow() {
        actorState.appearanceState = 0
        stateTime.reset()
        gasShellCounterToggled = false
    }

    private fun toggleState() {
        if (isHiding()) { startShow() } else { startHide() }
    }

    private fun isHiding(): Boolean {
        return actorState.appearanceState == 1
    }

}