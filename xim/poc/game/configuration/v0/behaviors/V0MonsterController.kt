package xim.poc.game.configuration.v0.behaviors

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.v0.ActorEnmity
import xim.poc.game.configuration.v0.V0AbilityCost
import xim.poc.game.configuration.v0.getEnmityTable
import xim.poc.game.event.*
import xim.util.FrameTimer
import xim.util.multiplyInPlace
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object V0DefaultMonsterBehavior: BehaviorId

open class V0MonsterController(val actorState: ActorState): ActorMonsterController {

    val autoAttackDelegate = AutoAttackController(actorState)
    var spellTimer = FrameTimer(20.seconds).resetRandom(lowerBound = 5.seconds)
    var dynamicStatusResistance = StatusResistanceTracker().also {
        it[StatusEffect.Stun] = OccurrenceLimitStrategy(resetInterval = 25.seconds, maxOccurrencesInInterval = 3)
    }

    override fun update(elapsedFrames: Float): List<Event> {
        dynamicStatusResistance.update(elapsedFrames)

        val desiredEngageTarget = selectEngageTarget()
        if (desiredEngageTarget != null && (!actorState.isEngaged() || actorState.targetState.targetId != desiredEngageTarget)) {
            return listOf(BattleEngageEvent(actorState.id, desiredEngageTarget))
        } else if (actorState.isEngaged() && desiredEngageTarget == null) {
            return listOf(BattleDisengageEvent(actorState.id))
        }

        if (wantsToUseSkill()) {
            if (!eligibleToUseSkill()) { return emptyList() }
            val chosenSkillEvents = useSkill(selectSkill())
            if (chosenSkillEvents.isNotEmpty()) { return chosenSkillEvents }
        }

        spellTimer.update(elapsedFrames)
        if (wantsToCastSpell()) {
            if (!eligibleToUseSkill()) { return emptyList() }
            spellTimer.reset()
            val chosenSpellEvents = useSkill(selectSpell())
            if (chosenSpellEvents.isNotEmpty()) { return chosenSpellEvents }
        }

        val standardAutoAttack = autoAttackDelegate.update(elapsedFrames)
        if (standardAutoAttack.isEmpty()) { return emptyList() }

        val autoAttackOverride = onReadyToAutoAttack()
        if (autoAttackOverride != null) { return autoAttackOverride }

        val autoAttackAbilities = getAutoAttackAbilities()
        if (autoAttackAbilities.isNotEmpty()) {
            return useSkill(SkillSelector.selectSkill(actorState, autoAttackAbilities))
        }

        return standardAutoAttack
    }

    final override fun onStatusEffectGained(statusEffectState: StatusEffectState) {
        dynamicStatusResistance.onStatusGained(statusEffectState)
        super.onStatusEffectGained(statusEffectState)
    }

    final override fun applyBehaviorBonuses(aggregate: CombatBonusAggregate) {
        for ((status, resistance) in dynamicStatusResistance.getResistance()) {
            aggregate.resist(status, resistance)
        }

        for ((status, duration) in dynamicStatusResistance.getDurationMultiplier()) {
            aggregate.statusDurationMultipliers.multiplyInPlace(status, duration)
        }

        applyMonsterBehaviorBonuses(aggregate)
    }

    open fun getCombatStats(): CombatStats {
        return MonsterDefinitions[actorState.monsterId]?.baseCombatStats ?: CombatStats.defaultBaseStats
    }

    open fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
    }

    override fun onAttacked(context: ActorAttackedContext): List<Event> {
        actorState.getEnmityTable().add(context.attacker.id, ActorEnmity(totalEnmity = 5))
        return super.onAttacked(context)
    }

    override fun onDamaged(context: ActorDamagedContext): List<Event> {
        actorState.getEnmityTable().add(context.attacker.id, ActorEnmity(totalEnmity = context.damageAmount))
        return super.onDamaged(context)
    }

    open fun getWeapon(): Pair<Int, Int> {
        val definition = MonsterDefinitions[actorState.monsterId] ?: return (3 to 240)
        return definition.baseDamage to definition.baseDelay
    }

    open fun getSkills(): List<SkillId> {
        return MonsterDefinitions[actorState.monsterId]?.mobSkills ?: return emptyList()
    }

    open fun selectSkill(): SkillSelection? {
        return SkillSelector.selectSkill(actorState, skills = getSkills())
    }

    open fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        return null
    }

    open fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        return null
    }

    open fun getSkillCostOverride(skill: MobSkillId): V0AbilityCost? {
        return null
    }

    open fun getSkillEffectedTargetType(skillId: SkillId): Int? {
        return null
    }

    open fun getSpells(): List<SkillId> {
        return GameEngine.getSpellList(actorState.id)
    }

    open fun selectSpell(): SkillSelection? {
        return SkillSelector.selectSkill(actorState, skills = getSpells())
    }

    open fun getAutoAttackTypes(): List<AutoAttackType> {
        return listOf(MainHandAutoAttack(subType = AutoAttackSubType.None))
    }

    open fun onReadyToAutoAttack(): List<Event>? {
        return null
    }

    open fun getAutoAttackAbilities(): List<SkillId> {
        return MonsterDefinitions[actorState.monsterId]?.autoAttackSkills ?: return emptyList()
    }

    open fun wantsToUseSkill(): Boolean {
        val hpp = actorState.getHpp()
        val tpp = actorState.getTpp()
        return if (hpp > 0.75f) { tpp > 0.6f } else if (hpp > 0.5f) { tpp > 0.45f } else { tpp > 0.3f }
    }

    open fun wantsToCastSpell(): Boolean {
        return spellTimer.isReady()
    }

    open fun selectEngageTarget(): ActorId? {
        return actorState.getEnmityTable().getHighestEnmityTarget()
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return if (actorState.isDead()) {
            ActorCollisionType.None
        } else {
            ActorCollisionType.Actor
        }
    }

    override fun shouldPerformAggroCheck(): Boolean {
        return true
    }

    override fun hasDirectionalAutoAttacks(): Boolean {
        return !actorState.facesTarget
    }

    override fun performAggroCheck() {
        if (actorState.isEngaged()) { return }
        val targetId = MonsterAggroHelper.getAggroTarget(actorState) ?: return
        actorState.getEnmityTable().add(targetId, ActorEnmity(totalEnmity = 1))
    }

    private fun eligibleToUseSkill(): Boolean {
        if (actorState.isOccupied() || actorState.isDead()) { return false }
        val model = ActorManager[actorState.id] ?: return true
        return !model.isMovementOrAnimationLocked()
    }

    private fun useSkill(skillSelection: SkillSelection?): List<Event> {
        skillSelection ?: return emptyList()
        val event = SkillSelector.skillToEvent(actorState, skillSelection.skill, skillSelection.targetState.id) ?: return emptyList()
        return listOf(event)
    }

}