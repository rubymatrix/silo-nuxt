package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.basicMagicalWs
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.basicPhysicalDamage
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.singleStatus
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.sourceAppearanceStateChange
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.sourceCurrentHpDamage
import xim.poc.game.event.ActorAttackedEvent
import xim.poc.game.event.AttackDamageType
import xim.poc.game.event.AttackStatusEffect
import kotlin.time.Duration.Companion.seconds

object MobClusterBehavior {

    fun slingBomb(): SkillApplierHelper.TargetEvaluator {
        return basicPhysicalDamage { 1f + 0.4f * getBombsRemaining(it.attacker) }
    }

    fun formationAttack(): SkillApplierHelper.TargetEvaluator {
        return basicPhysicalDamage(numHits = { getBombsRemaining(it.sourceState) }, ftpSpread = true, ftp = { 0.8f })
    }

    fun circleOfFlames(): SkillApplierHelper.TargetEvaluator {
        return basicMagicalWs(attackEffects = singleStatus(attackStatusEffect = AttackStatusEffect(
            statusEffect = StatusEffect.Weight, baseDuration = 15.seconds) { it.statusState.potency = 20 }
        )) { 0.5f * getBombsRemaining(it.attacker) }
    }

    fun selfDestructSingle(destState: Int, hppUsed: Float): SkillApplier {
        return SkillApplier(
            additionalSelfEvaluator = SkillApplierHelper.TargetEvaluator.compose(
                sourceAppearanceStateChange(destState),
                sourceCurrentHpDamage(percent = hppUsed, damageType = AttackDamageType.Static)
            ),
            targetEvaluator = basicMagicalWs { 2f }
        )
    }

    fun selfDestructFull(): SkillApplier {
        return SkillApplier(
            additionalSelfEvaluator = {
                val damage = listOf(it.sourceState.getHp())
                listOf(ActorAttackedEvent(sourceId = it.sourceState.id, targetId = it.sourceState.id, damageAmount = damage, damageType = AttackDamageType.Static, skill = it.skill, actionContext = it.context))
            },
            targetEvaluator = {
                val damage = listOf(it.sourceState.getHp())
                listOf(ActorAttackedEvent(sourceId = it.sourceState.id, targetId = it.targetState.id, damageAmount = damage, damageType = AttackDamageType.Magical, skill = it.skill, actionContext = it.context))
            }
        )
    }

    private fun getBombsRemaining(actorState: ActorState): Int {
        return when(actorState.appearanceState) {
            0 -> 3
            1 -> 2
            2 -> 1
            else -> 0
        }
    }

}

class FamilyClusterController(actorState: ActorState): V0MonsterController(actorState) {

    override fun getSkills(): List<SkillId> {
        return getValidSkills()
    }

    private fun getValidSkills(): List<SkillId> {
        if (actorState.appearanceState == 0) {
            if (actorState.getHpp() < 0.66f) { return listOf(mskillSelfDestruct_315) }
            if (actorState.getHpp() < 0.20f) { return listOf(mskillSelfDestruct_316) }
        }

        if (actorState.appearanceState == 1) {
            if (actorState.getHpp() < 0.35f) { return listOf(mskillSelfDestruct_317) }
            if (actorState.getHpp() < 0.20f) { return listOf(mskillSelfDestruct_318) }
        }

        if (actorState.appearanceState == 2) {
            return listOf(mskillSelfDestruct_319)
        }

        if (!actorState.hasStatusEffect(StatusEffect.Haste)) {
            return listOf(mskillRefueling_313)
        }

        if (actorState.appearanceState < 2) {
            return listOf(mskillSlingBomb_311, mskillFormationAttack_312, mskillCircleofFlames_314)
        }

        return emptyList()
    }

}
