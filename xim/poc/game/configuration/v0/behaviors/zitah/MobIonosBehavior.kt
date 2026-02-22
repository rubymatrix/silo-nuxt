package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.game.ActorState
import xim.poc.game.AutoAttackEffect
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillGeoticSpin_3249
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.event.AttackAddedEffectType
import xim.poc.game.event.AttackStatusEffect
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MobIonosBehavior(val difficulty: EschaDifficulty, actorState: ActorState): V0MonsterController(actorState) {

    private val skillQueue = ArrayDeque<SkillId>()

    override fun getSkills(): List<SkillId> {
        if (skillQueue.isEmpty()) {
            skillQueue += super.getSkills().random()
            skillQueue += mskillGeoticSpin_3249
        }

        return listOf(skillQueue.removeFirst())
    }

    override fun wantsToUseSkill(): Boolean {
        return actorState.getTpp() > 0.3f
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        if (skill == mskillGeoticSpin_3249) { return 2.seconds - 50.milliseconds * difficulty.value }
        return super.getSkillCastTimeOverride(skill)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.knockBackResistance += 100
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Bind)

        aggregate.haste += 8 * difficulty.value
        aggregate.storeTp += 25

        aggregate.autoAttackEffects += AutoAttackEffect(0, AttackAddedEffectType.Earth, statusEffect = AttackStatusEffect(
            statusEffect = StatusEffect.Curse,
            baseDuration = 10.seconds,
            baseChance = 1f
        ) { it.statusState.potency = 20 + 5 * difficulty.value })
    }

}