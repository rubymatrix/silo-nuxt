package xim.poc.game.configuration.v0.pet

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.*
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.event.Event
import xim.poc.game.event.MainHandAutoAttack
import xim.resource.SpellElement
import kotlin.math.roundToInt

data class WyvernBreathCommand(
    val skill: SkillId,
    val targetId: ActorId,
)

class PetWyvernBehavior(actorState: ActorState): V0MonsterController(actorState) {

    companion object {
        val elementalBreaths = listOf(skillFlameBreath_1158, skillFrostBreath_1159, skillGustBreath_1160, skillSandBreath_1161, skillLightningBreath_1162, skillHydroBreath_1163)
    }

    private val breathQueue = ArrayDeque<WyvernBreathCommand>()

    override fun update(elapsedFrames: Float): List<Event> {
        val owner = ActorStateManager[actorState.owner]
        if (owner == null) {
            GameV0Helpers.defeatActor(actorState)
            return emptyList()
        }

        transferTpToOwner()

        if (actorState.isIdle() && Vector3f.distance(owner.position, actorState.position) > 20f) {
            actorState.position.copyFrom(owner.position - Vector3f.X * 2f)
        }

        return super.update(elapsedFrames)
    }

    override fun getWeapon(): Pair<Int, Int> {
        val owner = getOwner() ?: return super.getWeapon()
        return DamageCalculator.getBaseWeaponPowerAndDelay(owner, type = MainHandAutoAttack()) ?: return super.getWeapon()
    }

    override fun getCombatStats(): CombatStats {
        val owner = getOwner() ?: return super.getCombatStats()
        return owner.combatStats
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        val owner = getOwner() ?: return
        val ownerBonuses = CombatBonusAggregator[owner]

        aggregate.regen += actorState.combatStats.maxHp/20

        aggregate.haste += ownerBonuses.haste
        aggregate.doubleAttack += ownerBonuses.doubleAttack
        aggregate.tripleAttack += ownerBonuses.tripleAttack
        aggregate.quadrupleAttack += ownerBonuses.quadrupleAttack
        aggregate.storeTp += ownerBonuses.storeTp
        aggregate.magicAttackBonus += ownerBonuses.magicAttackBonus
        aggregate.magicBurstDamage += ownerBonuses.magicBurstDamage
        aggregate.magicBurstDamageII += ownerBonuses.magicBurstDamageII
        aggregate.curePotency += ownerBonuses.curePotency
    }

    override fun selectEngageTarget(): ActorId? {
        val owner = getOwner() ?: return null
        if (!owner.isEngaged()) { return null }

        val target = ActorStateManager[owner.getTargetId()] ?: return null
        if (target.getNullableEnmityTable()?.hasEnmity(owner.id) != true) { return null }

        return target.id
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId !is AbilitySkillId) { return null }

        if (skillId.id in skillFlameBreath_1158.id .. skillHydroBreath_1163.id) {
            return SkillApplier(targetEvaluator = { breathApplier(it) })
        }

        return null
    }

    override fun wantsToUseSkill(): Boolean {
        return breathQueue.isNotEmpty()
    }

    override fun selectSkill(): SkillSelection? {
        val next = breathQueue.firstOrNull() ?: return null

        val skill = when (next.skill) {
            skillSmitingBreath_830 -> selectElementalBreath()
            skillRestoringBreath_831 -> skillHealingBreath_1152
            else -> return null
        }

        val target = ActorStateManager[next.targetId]
        if (target == null || !GameEngine.canBeginSkillOnTarget(actorState, target, skill)) {
            breathQueue.removeFirst()
            return null
        }

        return SkillSelection(skill, target)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == breathQueue.firstOrNull()?.skill) { breathQueue.removeFirst() }
        return emptyList()
    }

    override fun onAttacked(context: ActorAttackedContext): List<Event> {
        return emptyList()
    }

    override fun onIncomingDamage(context: ActorDamagedContext): Int? {
        return if (context.attacker.getTargetId() != actorState.id) {
            context.damageAmount/10
        } else {
            null
        }
    }

    override fun onDamaged(context: ActorDamagedContext): List<Event> {
        return emptyList()
    }

    fun enqueueBreath(command: WyvernBreathCommand) {
        breathQueue += command
    }

    private fun selectElementalBreath(): SkillId {
        val target = ActorStateManager[actorState.getTargetId()] ?: return elementalBreaths.random()

        val current = target.skillChainTargetState.skillChainState
        if (current !is SkillChainStep) { return elementalBreaths.random() }

        val elements = current.attribute.elements
        val validBreaths = ArrayList<SkillId>()

        if (elements.contains(SpellElement.Fire)) { validBreaths += skillFlameBreath_1158 }
        if (elements.contains(SpellElement.Ice)) { validBreaths += skillFrostBreath_1159 }
        if (elements.contains(SpellElement.Wind)) { validBreaths += skillGustBreath_1160 }
        if (elements.contains(SpellElement.Earth)) { validBreaths += skillSandBreath_1161 }
        if (elements.contains(SpellElement.Lightning)) { validBreaths += skillLightningBreath_1162 }
        if (elements.contains(SpellElement.Water)) { validBreaths += skillHydroBreath_1163 }

        return validBreaths.randomOrNull() ?: elementalBreaths.random()
    }

    private fun breathApplier(context: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        val owner = getOwner() ?: return emptyList()
        val potency = owner.getMainJobLevel().level.toFloat()

        return V0SpellDefinitions.spellResultToEvents(context, SpellDamageCalculator.computeDamage(
            potencyFn = { potency },
            skill = context.skill,
            attacker = owner,
            defender = context.targetState,
            originalTarget = context.primaryTargetState,
            attackStat = CombatStat.int,
            defendStat = CombatStat.int,
            context = context.context,
            numHits = 1,
        ))
    }

    private fun transferTpToOwner() {
        val owner = getOwner() ?: return

        val ownerGain = (0.5 * actorState.getTp()).roundToInt()
        owner.gainTp(ownerGain)

        actorState.setTp(0)
    }

    private fun getOwner(): ActorState? {
        return ActorStateManager[actorState.owner]
    }

}