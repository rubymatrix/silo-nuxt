package xim.poc.game.configuration.v0.behaviors

import xim.poc.EnvironmentManager
import xim.poc.game.*
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillSelection
import xim.poc.game.configuration.SkillSelector
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.Event
import xim.resource.DatId
import xim.util.FrameTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class MobTchakkaController(actorState: ActorState): V0MonsterController(actorState) {

    private var verveCount = 0

    private var auraSkillsUsed = 0
    private val queuedAuraSkills = ArrayDeque<SkillId>()
    private val auraAbilityCooldown = FrameTimer(3.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        adjustWeather()

        if (hasAura() && actorState.isIdleOrEngaged()) {
            auraAbilityCooldown.update(elapsedFrames)
            handleAuraSkillQueue()
        }

        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.spellInterruptDown = 100
        aggregate.refresh += 100
        aggregate.movementSpeed += 50

        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Bind, StatusEffect.Paralysis)

        if (hasAura()) {
            aggregate.fullResist(StatusEffect.Stun)
        }

        if (hasAura() || wantsToEnableAura()) {
            aggregate.tpRequirementBypass = true
            aggregate.mobSkillFastCast += 50
        }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillCarcharianVerve_2758) {
            verveCount += 1
            auraSkillsUsed = 0
            actorState.appearanceState = 1
            auraAbilityCooldown.reset(4.seconds)
        } else if (queuedAuraSkills.isNotEmpty() && primaryTargetContext.skill == queuedAuraSkills.first()) {
            auraSkillsUsed += 1
            queuedAuraSkills.removeFirstOrNull()
            auraAbilityCooldown.reset()
        }

        return emptyList()
    }

    override fun onSkillInterrupted(skill: SkillId): List<Event> {
        if (skill == mskillProtolithicPuncture_2755) {
            MiscEffects.playExclamationProc(actorState.id, ExclamationProc.Blue)
            actorState.setTp(0)
        }

        return emptyList()
    }

    override fun onDefeated(): List<Event> {
        actorState.appearanceState = 0
        adjustWeather()
        return emptyList()
    }

    override fun wantsToUseSkill(): Boolean {
        return if (wantsToEnableAura()) {
            true
        } else if (hasAura()) {
            auraAbilityCooldown.isReady()
        } else {
            actorState.getTpp() >= 0.5f
        }
    }

    override fun selectSkill(): SkillSelection? {
        val skills = if (wantsToEnableAura()) {
            listOf(mskillCarcharianVerve_2758)
        } else if (queuedAuraSkills.isNotEmpty()) {
            listOf(queuedAuraSkills.first())
        } else {
            listOf(mskillProtolithicPuncture_2755, mskillAquaticLance_2756, mskillPelagicCleaver_2757)
        }

        return SkillSelector.selectSkill(actorState, skills)
    }

    override fun onReadyToAutoAttack(): List<Event>? {
        return if (hasAura() || wantsToEnableAura()) { emptyList() } else { super.onReadyToAutoAttack() }
    }

    private fun hasAura(): Boolean {
        return actorState.appearanceState == 1
    }

    private fun wantsToEnableAura(): Boolean {
        return !hasAura() && when (verveCount) {
            0 -> actorState.getHpp() <= 0.80f
            1 -> actorState.getHpp() <= 0.40f
            else -> false
        }
    }

    private fun adjustWeather() {
        val weather = if (hasAura()) { DatId.weatherRain } else { DatId.weatherSunny }
        EnvironmentManager.switchWeather(weather)
    }

    private fun handleAuraSkillQueue() {
        if (auraSkillsUsed >= 6) {
            actorState.appearanceState = 0
        } else if (queuedAuraSkills.isEmpty()) {
            populateAuraSkills()
        }
    }

    private fun populateAuraSkills() {
        queuedAuraSkills += mskillMarineMayhem_2760
        val magicSkill = if (GameEngine.canBeginSkill(actorState.id, spellBlizzara_830)) { spellBlizzara_830 } else { spellWatera_838 }

        if (Random.nextBoolean()) {
            queuedAuraSkills += mskillTidalGuillotine_2759
            queuedAuraSkills += magicSkill
        } else {
            queuedAuraSkills += magicSkill
            queuedAuraSkills += mskillTidalGuillotine_2759
        }
    }

}
