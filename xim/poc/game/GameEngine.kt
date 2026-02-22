package xim.poc.game

import xim.poc.ActionTargetFilter
import xim.poc.ActorId
import xim.poc.Collider
import xim.poc.game.actor.components.getRecastDelay
import xim.poc.game.configuration.EventScriptRunner
import xim.poc.game.configuration.SkillAppliers
import xim.poc.game.configuration.SkillRangeInfo
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.*
import xim.resource.*
import xim.resource.InventoryItems.toItemInfo
import xim.resource.table.AbilityInfoTable.toAbilityInfo
import xim.resource.table.AbilityNameTable
import xim.resource.table.MobSkillInfoTable.toMobSkillInfoOrNull
import xim.resource.table.MobSkillNameTable
import xim.resource.table.SpellInfoTable.toSpellInfo
import xim.resource.table.SpellNameTable
import xim.util.Stack
import kotlin.random.Random
import kotlin.time.Duration

object GameEngine {

    private val events = Stack<Event>()
    private var setup = false

    fun setup() {
        if (setup) { return }
        setup = true

        events += GameState.getGameMode().setup()
        applyEvents()
    }

    fun tick(elapsedFrames: Float) {
        EventScriptRunner.update(elapsedFrames)
        GameState.update(elapsedFrames)

        val pushableActors = ActorStateManager.getAll().filter { it.value.type == ActorType.Enemy && !it.value.isDead() }.values
        Collider.pushAgainst(pushableActors, bbRadius = 1.5f, maxPushPerFrame = 0.05f, elapsedFrames = elapsedFrames)

        val allActors = ActorStateManager.getAll().filter { !it.value.disabled }

        events += allActors.map { TickActorEvent(elapsedFrames, it.key) }
        applyEvents()

        events += allActors.flatMap { it.value.behaviorController.update(elapsedFrames) }
        applyEvents()

        PartyManager.update(elapsedFrames)
    }

    fun submitEvent(event: Event) {
        events += event
    }

    fun submitEvents(event: List<Event>) {
        events += event
    }

    private fun applyEvents() {
        while (!events.isEmpty()) {
            val event = events.pop()
            events += event.apply()
        }
    }

    fun submitCreateActorState(initialActorState: InitialActorState): ActorPromise {
        val promise = ActorPromise()
        events += ActorCreateEvent(initialActorState, promise)
        return promise
    }

    fun submitDeleteActor(it: ActorId?) {
        it ?: return
        events += ActorDeleteEvent(it)
    }

    fun getSkillTargetFilter(skill: SkillId): ActionTargetFilter {
        return ActionTargetFilter(getSkillTargetFlags(skill))
    }

    fun getSkillTargetFlags(skill: SkillId): Int {
        return when (skill) {
            is SpellSkillId -> skill.toSpellInfo().targetFlags
            is MobSkillId -> skill.toMobSkillInfoOrNull()?.targetFlag ?: TargetFlag.Enemy.flag
            is AbilitySkillId -> skill.toAbilityInfo().targetFlags
            is ItemSkillId -> skill.toItemInfo().targetFlags
            is RangedAttackSkillId -> TargetFlag.Enemy.flag
        }
    }

    fun getSkillEffectedTargetFlags(actorState: ActorState, skill: SkillId): Int {
        return GameState.getGameMode().getSkillEffectedTargetFlags(actorState, skill) ?: getSkillTargetFlags(skill)
    }

    fun canBeginAction(actorState: ActorState): Boolean {
        return !actorState.isDead() && !actorState.isOccupied() && !actorState.hasStatusActionLock()
    }

    fun canBeginSkill(actorId: ActorId, skill: SkillId): Boolean {
        val actorState = ActorStateManager[actorId] ?: return false
        if (!canBeginAction(actorState)) { return false }
        return canExecuteSkill(actorState, skill)
    }

    fun canBeginSkillOnTarget(actor: ActorState, target: ActorState, skill: SkillId): Boolean {
        if (!canBeginSkill(actor.id, skill)) { return false }

        if (!actor.behaviorController.ignoresSkillRangeChecks(skill)) {
            val rangeInfo = GameState.getGameMode().getSkillRangeInfo(actor, skill)
            if (actor.getTargetingDistance(target) > rangeInfo.maxTargetDistance) { return false }
        }

        val targetFilter = getSkillTargetFilter(skill)
        if (!targetFilter.targetFilter(actor, target)) { return false }

        return true
    }

    fun canExecuteSkill(actorState: ActorState, skill: SkillId): Boolean {
        if (actorState.isDead() || actorState.hasStatusActionLock()) { return false }

        if (!SkillAppliers.checkValid(actorState, skill)) { return false }

        val cost = getSkillBaseCost(actorState, skill)
        if (!canPayAbilityCost(actorState, cost)) { return false }

        return when (skill) {
            is AbilitySkillId -> canUseAbility(actorState, skill)
            is MobSkillId -> canUseMobSkill(actorState, skill)
            is SpellSkillId -> canCastSpell(actorState, skill)
            is ItemSkillId -> true
            is RangedAttackSkillId -> true
        }
    }

    private fun canCastSpell(actorState: ActorState, skill: SpellSkillId): Boolean {
        if (actorState.hasStatusEffect(StatusEffect.Silence)) { return false }

        val recastDelay = actorState.getRecastDelay(skill)
        if (recastDelay != null && !recastDelay.isComplete()) { return false }

        val spellInfo = skill.toSpellInfo()
        if (spellInfo.magicType == MagicType.Trust) {
            val actorParty = PartyManager[actorState.id]
            if (actorParty.size() >= 6) { return false }
        }

        return true
    }

    private fun canUseAbility(actorState: ActorState, skill: AbilitySkillId): Boolean {
        if (actorState.hasStatusEffect(StatusEffect.Amnesia)) { return false }

        val recastDelay = actorState.getRecastDelay(skill)
        if (recastDelay != null && !recastDelay.isComplete()) { return false }

        val info = skill.toAbilityInfo()
        if (info.type != AbilityType.PetCommand) { return true }

        val pet = ActorStateManager[actorState.pet] ?: return false
        return !pet.isOccupied() && !pet.isDead()
    }

    private fun canUseMobSkill(actorState: ActorState, skill: SkillId): Boolean {
        return true
    }

    private fun canPayAbilityCost(actor: ActorState, cost: AbilityCost): Boolean {
        val bonusAggregate = getCombatBonusAggregate(actor)

        return when (cost.type) {
            AbilityCostType.Tp -> bonusAggregate.tpRequirementBypass || cost.value <= actor.getTp()
            AbilityCostType.Mp -> cost.value <= actor.getMp()
        }
    }

    private fun releaseTrusts(actor: ActorState): List<Event> {
        return getTrusts(actor.id).map { TrustReleaseEvent(actor.id, it.id) }
    }

    fun getTrusts(actorId: ActorId): List<ActorState> {
        return PartyManager[actorId].getAllState().filter { it.owner == actorId }
    }

    fun releaseDependents(actor: ActorState): List<Event> {
        val releaseEvents = ArrayList<Event>()
        releaseEvents += releaseTrusts(actor)
        releaseEvents += BubbleReleaseEvent(actor.id)
        releaseEvents += PetReleaseEvent(actor.id)
        return releaseEvents
    }

    fun getSpellList(actorId: ActorId): List<SpellSkillId> {
        ActorStateManager[actorId] ?: return emptyList()
        return GameState.getGameMode().getActorSpellList(actorId)
    }

    fun getActorAbilityList(actorId: ActorId, abilityType: AbilityType): List<AbilitySkillId> {
        return GameState.getGameMode().getActorAbilityList(actorId, abilityType)
    }

    fun getRangeInfo(actor: ActorState, skill: SkillId): SkillRangeInfo {
        return GameState.getGameMode().getSkillRangeInfo(actor, skill)
    }

    fun getRangedAttackRangeInfo(actor: ActorState): SkillRangeInfo {
        return getRangeInfo(actor, rangedAttack)
    }

    fun getSkillBaseCost(actorState: ActorState, skill: SkillId): AbilityCost {
        return GameState.getGameMode().getSkillBaseCost(actorState, skill)
    }

    fun getSkillUsedCost(actor: ActorState, skill: SkillId): AbilityCost {
        return GameState.getGameMode().getSkillUsedCost(actor, skill)
    }

    fun getActorEffectTickResult(actorState: ActorState): ActorEffectTickResult {
        return GameState.getGameMode().getActorEffectTickResult(actorState)
    }

    fun getAutoAttackRecast(source: ActorState): Float {
        return GameState.getGameMode().getAutoAttackInterval(source)
    }

    fun computeCombatStats(actorId: ActorId): CombatStats {
        return GameState.getGameMode().getActorCombatStats(actorId)
    }

    fun getCombatBonusAggregate(actor: ActorState): CombatBonusAggregate {
        return GameState.getGameMode().getActorCombatBonuses(actor)
    }

    fun getMaxTp(actor: ActorState): Int {
        return GameState.getGameMode().getMaxTp(actor)
    }

    fun rollParry(attacker: ActorState, defender: ActorState): Boolean {
        return GameState.getGameMode().rollParry(attacker, defender)
    }

    fun rollCounter(attacker: ActorState, defender: ActorState): AttackCounterEffect? {
        return GameState.getGameMode().rollCounter(attacker, defender)
    }

    fun rollGuard(attacker: ActorState, defender: ActorState): Boolean {
        return GameState.getGameMode().rollGuard(attacker, defender)
    }

    fun rollCastingInterrupt(attacker: ActorState, defender: ActorState): Boolean {
        return GameState.getGameMode().rollSpellInterrupted(attacker, defender)
    }

    fun rollStatusEffect(targetState: ActorState, attackStatusEffect: AttackStatusEffect): Boolean {
        if (!attackStatusEffect.canResist) { return true }
        val bonuses = getCombatBonusAggregate(targetState)
        val resistance = bonuses.allStatusResistRate + bonuses.statusResistances.getOrElse(attackStatusEffect.statusEffect) { 0 }
        return (attackStatusEffect.baseChance * resistance.toPenaltyMultiplier() ) >= Random.nextDouble()
    }

    fun getStatusEffectDuration(targetState: ActorState, attackStatusEffect: AttackStatusEffect): Duration? {
        if (attackStatusEffect.baseDuration == null) { return null }
        val bonuses = getCombatBonusAggregate(targetState)
        val durationMultiplier = bonuses.statusDurationMultipliers.getOrElse(attackStatusEffect.statusEffect) { 1f }
        return attackStatusEffect.baseDuration * durationMultiplier.toDouble()
    }

    fun checkParalyzeProc(actorState: ActorState): Boolean {
        return GameState.getGameMode().rollParalysis(actorState)
    }

    fun getSkillLogName(skill: SkillId): String? {
        val name = when (skill) {
            is SpellSkillId -> SpellNameTable[skill.id]?.first()
            is AbilitySkillId -> AbilityNameTable[skill.id]?.first()
            is ItemSkillId -> InventoryItems[skill.id].name
            is RangedAttackSkillId -> "Ranged Attack"
            is MobSkillId -> {
                val logged = skill.toMobSkillInfoOrNull()?.logged ?: false
                if (logged) { MobSkillNameTable[skill.id] } else { null }
            }
        }

        return if (name.isNullOrBlank() || name == ".") { null } else { name }
    }

    fun SkillId.displayName() = getSkillLogName(this) ?: ""

    fun rollAgainst(odds: Int): Boolean {
        return Random.nextDouble(0.0, 100.0) < odds
    }

}