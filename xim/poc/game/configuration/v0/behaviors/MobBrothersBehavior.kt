package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.CombatStat
import xim.poc.game.configuration.MonsterId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.v0.getEnmityTable
import xim.poc.game.event.Event
import xim.util.multiplyInPlace
import kotlin.math.roundToInt

class MobBrothersBehavior(actorState: ActorState, val brotherId: MonsterId): V0MonsterController(actorState) {

    override fun update(elapsedFrames: Float): List<Event> {
        val brother = getBrother()
        if (brother != null && brother.isEngaged()) { actorState.getEnmityTable().syncFrom(brother.getEnmityTable()) }
        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        val brother = getBrother() ?: return

        aggregate.tpRequirementBypass = actorState.getCastingState() != null || brother.isChargingCast()

        val healthDelta = 100f * (brother.getHpp() - actorState.getHpp()).coerceAtLeast(0f)
        val defenseBonus = (healthDelta * 5f).roundToInt().coerceAtMost(100)

        aggregate.multiplicativeStats.multiplyInPlace(CombatStat.str, 0.75f)

        aggregate.physicalDamageTaken -= defenseBonus
        aggregate.magicalDamageTaken -= defenseBonus
    }

    override fun wantsToUseSkill(): Boolean {
        return super.wantsToUseSkill() || getBrother()?.isChargingCast() == true
    }

    override fun getSkills(): List<SkillId> {
        val baseSkills = super.getSkills()
        val brotherSkill = getBrother()?.getCastingState()?.skill ?: return baseSkills
        return baseSkills - brotherSkill
    }

    private fun getBrother(): ActorState? {
        return ActorStateManager.getAll().values.firstOrNull { it.monsterId == brotherId }
    }

}