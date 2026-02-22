package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.EnvironmentManager
import xim.poc.game.ActorPromise
import xim.poc.game.ActorState
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.ActorCollisionType
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0MobSkillDefinitions.basicPhysicalDamage
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.constants.mobYumcaxWatcher_102
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.Event
import xim.resource.DatId
import xim.util.FrameTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MobYumcaxBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private var hasUsedUproot = false

    private val pets = ArrayList<ActorPromise>()
    private val skillRotation = ArrayDeque<MobSkillId>()

    override fun onDefeated(): List<Event> {
        disableAura()
        killPets()
        return emptyList()
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.Object
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.refresh += 5
        aggregate.knockBackResistance = 100

        if (wantsToUseUproot() || wantsToUseTimber()) {
            aggregate.tpRequirementBypass = true
        }

        if (wantsToUseUproot() || isAuraEnabled()) {
            aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Stun, StatusEffect.Petrify, StatusEffect.Slow, StatusEffect.Amnesia, StatusEffect.Paralysis)
        }

        if (isAuraEnabled()) {
            aggregate.physicalDamageTaken += 100
            aggregate.magicalDamageTaken += 100
        }
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId != mskillTiiimbeeer_2806) { return null }
        return SkillApplier(targetEvaluator = basicPhysicalDamage { 50f })
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        if (skill != mskillTiiimbeeer_2806) { return null }
        return 60.seconds
    }

    override fun wantsToUseSkill(): Boolean {
        return wantsToUseTimber() || wantsToUseUproot() || actorState.getTpp() >= 0.5f
    }

    override fun getSkills(): List<SkillId> {
        if (wantsToUseUproot()) {
            return listOf(mskillUproot_2803)
        } else if (wantsToUseTimber()) {
            return listOf(mskillTiiimbeeer_2806)
        }

        if (skillRotation.isEmpty()) {
            val skills = listOf(mskillRootoftheProblem_2801, mskillPottedPlant_2802, mskillCanopierce_2804, mskillCanopierce_2804, mskillFireflyFandango_2805).shuffled()
            skillRotation += skills
        }

        return listOf(skillRotation.removeFirst())
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        when (primaryTargetContext.skill) {
            mskillPottedPlant_2802 -> spawnPet(primaryTargetContext)
            mskillUproot_2803 -> onExecutedUproot()
            mskillTiiimbeeer_2806 -> disableAura()
            else -> {}
        }

        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun onReadyToAutoAttack(): List<Event>? {
        return if (isAuraEnabled()) { emptyList() } else { super.onReadyToAutoAttack() }
    }

    private fun wantsToUseUproot(): Boolean {
        return !hasUsedUproot && actorState.getHpp() < 0.33f
    }

    private fun onExecutedUproot() {
        hasUsedUproot = true
        enableAura()
        actorState.setHp(actorState.getMaxHp())
        pets.map { it.onReady { petState -> petState.syncEnmity(actorState) } }
    }

    private fun wantsToUseTimber(): Boolean {
        return isAuraEnabled()
    }

    private fun isAuraEnabled(): Boolean {
        return actorState.appearanceState == 1
    }

    private fun enableAura() {
        EnvironmentManager.switchWeather(DatId.weatherDusty)
        actorState.appearanceState = 1
    }

    private fun disableAura() {
        EnvironmentManager.switchWeather(DatId.weatherSunny)
        actorState.appearanceState = 0
    }

    private fun spawnPet(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext) {
        if (pets.count { it.isAlive() } >= 5) { return }

        val petDefinition = MonsterDefinitions[mobYumcaxWatcher_102]

        val spawnPosition = primaryTargetContext.sourceState.getCastingState()?.context?.targetAoeCenter
            ?: primaryTargetContext.targetState.position

        pets += V0MonsterHelper.spawnMonster(
            monsterDefinition = petDefinition,
            position = Vector3f(spawnPosition),
        )
    }

    private fun killPets() {
        pets.forEach { it.onReady(GameV0Helpers::defeatActor) }
    }

}

class MobYumcaxWatcherBehavior(actorState: ActorState): FamilyPanoptBehavior(actorState) {

    private val abilityTimer = FrameTimer(8.seconds).resetRandom(1.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isIdleOrEngaged() && actorState.isEngaged()) { abilityTimer.update(elapsedFrames) }
        return super.update(elapsedFrames)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.tpRequirementBypass = true
    }

    override fun wantsToUseSkill(): Boolean {
        return abilityTimer.isReady()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        abilityTimer.reset()
        return emptyList()
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun getSkills(): List<SkillId> {
        return super.getSkills() + listOf(spellStonegaIII_191)
    }

}