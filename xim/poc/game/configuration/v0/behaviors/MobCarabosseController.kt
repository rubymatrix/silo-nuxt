package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillSelection
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private enum class CarabosseForm(val transitionSkill: MobSkillId?, val mobSkills: List<MobSkillId>, val spells: List<SpellSkillId>) {
    None(transitionSkill = null, mobSkills = listOf(mskillZephyrArrow_1937, mskillLetheArrows_1938), spells = emptyList()),
    Winter(transitionSkill = mskillWinterBreeze_1942, mobSkills = emptyList(), spells = listOf(spellTornado_208)),
    Summer(transitionSkill = mskillSummerBreeze_1940, mobSkills = listOf(mskillCyclonicTurmoil_1943, mskillCyclonicTorrent_1944), spells = emptyList()),
    Spring(transitionSkill = mskillSpringBreeze_1939, mobSkills = emptyList(), spells = listOf(spellParalyze_58, spellStun_252, spellSlowII_79, spellGravity_216)),
    Autumn(transitionSkill = mskillAutumnBreeze_1941, mobSkills = emptyList(), spells = listOf(spellBlink_53, spellRegenII_110, spellHasteII_511)),
}

class MobCarabosseController(actorState: ActorState): V0MonsterController(actorState) {

    private var hasUsedBenediction = false
    private var currentForm = CarabosseForm.None
    private val wantsToChangeForms = FrameTimer(period = 20.seconds, initial = Duration.ZERO)

    init {
        spellTimer = FrameTimer(5.seconds)
    }

    override fun update(elapsedFrames: Float): List<Event> {
        wantsToChangeForms.update(elapsedFrames)
        return super.update(elapsedFrames)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillBenediction_1230) {
            hasUsedBenediction = true
            return emptyList()
        }

        val resultForm = CarabosseForm.values().firstOrNull { primaryTargetContext.skill == it.transitionSkill }
        if (resultForm != null) {
            currentForm = resultForm
            wantsToChangeForms.reset()
            return emptyList()
        }

        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.refresh += 10
        aggregate.fastCast += 33
        aggregate.spellInterruptDown += 100

        aggregate.resist(StatusEffect.Sleep, 100)
        aggregate.resist(StatusEffect.Silence, 100)

        when (currentForm) {
            CarabosseForm.None -> { }
            CarabosseForm.Winter -> {
                aggregate.magicAttackBonus += 50
            }
            CarabosseForm.Summer -> {
                aggregate.doubleAttack += 50
            }
            CarabosseForm.Spring -> {
            }
            CarabosseForm.Autumn -> {
                aggregate.regen += (actorState.getMaxHp() * 0.01f).roundToInt()
                aggregate.magicalDamageTaken -= 100
            }
        }
    }

    override fun wantsToUseSkill(): Boolean {
        return if (!hasUsedBenediction && actorState.getHpp() < 0.5f) { true } else { super.wantsToUseSkill() }
    }

    override fun getSkills(): List<SkillId> {
        return if (hasUsedBenediction && wantsToChangeForms.isReady()) {
            listOf(mskillSpringBreeze_1939, mskillSummerBreeze_1940, mskillAutumnBreeze_1941, mskillWinterBreeze_1942)
                .filter { it != currentForm.transitionSkill }
        } else {
            currentForm.mobSkills
        }
    }

    override fun selectSkill(): SkillSelection? {
        return if (!hasUsedBenediction && actorState.getHpp() <= 0.5) {
            SkillSelection(mskillBenediction_1230, actorState)
        } else {
            super.selectSkill()
        }
    }

    override fun getSpells(): List<SkillId> {
        return currentForm.spells
    }

}