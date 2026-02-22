package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.*
import xim.poc.game.configuration.ActorDamagedContext
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.SpellSkillId
import xim.poc.game.configuration.constants.mskillCrossthrash_1425
import xim.poc.game.configuration.constants.mskillRoar_14
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.Event
import xim.resource.SpellElement
import xim.resource.table.SpellInfoTable.toSpellInfo
import xim.util.FrameTimer
import xim.util.multiplyInPlace
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class MobVidalaBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    private var bonus = 0

    override fun onInitialized(): List<Event> {
        spellTimer = FrameTimer(20.seconds, initial = ZERO)
        return super.onInitialized()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.spellInterruptDown += 80 + 5 * difficulty.offset
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Bind)

        aggregate.fastCast += 3 * difficulty.value

        for (stat in CombatStat.values()) {
            if (stat == CombatStat.maxHp || stat == CombatStat.maxMp) { continue }
            aggregate.multiplicativeStats.multiplyInPlace(stat, 1f + 0.1f * bonus * difficulty.value)
        }

        if (targetIsPetrifiedOrTerrorized()) {
            aggregate.tpRequirementBypass = true
        }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillRoar_14) {
            AttackContext.compose(primaryTargetContext.context) { MiscEffects.playEffect(actorState.id, effect = MiscEffects.Effect.LevelUp) }
            bonus += 1
        }

        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun onDamaged(context: ActorDamagedContext): List<Event> {
        val nullifyBonus = (context.skill is SpellSkillId && context.skill.toSpellInfo().element == SpellElement.Wind) ||
                (context.skillChainStep is SkillChainStep && context.skillChainStep.attribute.elements.contains(SpellElement.Wind))

        if (bonus > 0 && nullifyBonus) {
            AttackContext.compose(context.actionContext) { MiscEffects.playEffect(actorState.id, effect = MiscEffects.Effect.LevelDown) }
            bonus = 0
        }

        return super.onDamaged(context)
    }

    override fun wantsToUseSkill(): Boolean {
        return targetIsPetrifiedOrTerrorized() || super.wantsToUseSkill()
    }

    override fun getSkills(): List<SkillId> {
        return if (targetIsPetrifiedOrTerrorized()) { listOf(mskillCrossthrash_1425) } else { super.getSkills() }
    }

    private fun targetIsPetrifiedOrTerrorized(): Boolean {
        val target = ActorStateManager[actorState.getTargetId()] ?: return false
        return target.hasStatusEffect(StatusEffect.Petrify) || target.hasStatusEffect(StatusEffect.Terror)
    }

}