package xim.poc.game.configuration.v0.behaviors.zitah

import xim.math.Vector3f
import xim.poc.ModelLook
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.MobSkills
import xim.poc.game.configuration.v0.V0MobSkillDefinitions
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.V0SpellDefinitions.applyBasicBuff
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.constants.mobPazuzuFetter_288_501
import xim.poc.game.event.AttackEffects
import xim.poc.game.event.AttackStatusEffect
import xim.poc.game.event.Event
import xim.resource.AoeType
import xim.resource.SpellElement
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class FetterConfig(val spellElement: SpellElement, val buff: StatusEffect, val debuff: StatusEffect, val look: ModelLook) {
    Fire(spellElement = SpellElement.Fire, buff = StatusEffect.Barblizzard, debuff = StatusEffect.Burn, look = ModelLook.npc(0x085B)),
    Ice(spellElement = SpellElement.Ice, buff = StatusEffect.Baraero, debuff = StatusEffect.Frost, look = ModelLook.npc(0x85C)),
    Wind(spellElement = SpellElement.Wind, buff = StatusEffect.Barstone, debuff = StatusEffect.Choke, look = ModelLook.npc(0x85D)),
    Earth(spellElement = SpellElement.Earth, buff = StatusEffect.Barthunder, debuff = StatusEffect.Rasp, look = ModelLook.npc(0x85E)),
    Lightning(spellElement = SpellElement.Lightning, buff = StatusEffect.Barwater, debuff = StatusEffect.Shock, look = ModelLook.npc(0x85F)),
    Water(spellElement = SpellElement.Water, buff = StatusEffect.Barfire, debuff = StatusEffect.Drown, look = ModelLook.npc(0x860)),
}

private class PendingFetter(val position: Vector3f, val config: FetterConfig, val skillId: SkillId)

class MobPazuzuController(actorState: ActorState): V0MonsterController(actorState) {

    companion object {
        private val elementalSequence = listOf(
            mskillSearingHalitus_2530,
            mskillCripplingRime_2533,
            mskillDivestingGale_2531,
            mskillKurnugiCollapse_2529,
            mskillBoltofPerdition_2532,
            mskillDiluvialWake_2528,
        )
    }

    private val moveQueue = ArrayDeque(elementalSequence.shuffled())
    private var followUpSpell: SpellSkillId? = null

    private var pendingFetter: PendingFetter? = null
    private val spawnedFetters = HashMap<SpellElement, ActorPromise>()

    private var hasUsedMeikyo = false
    private var hasUsedOblivion = false

    override fun onInitialized(): List<Event> {
        dynamicStatusResistance = StatusResistanceTracker().also {
            it[StatusEffect.Stun] = OccurrenceLimitStrategy(resetInterval = 10.minutes, maxOccurrencesInInterval = 3)
        }
        return super.onInitialized()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Silence)
        aggregate.spellInterruptDown += 100
        aggregate.knockBackResistance += 100
        aggregate.refresh += 10
        aggregate.fastCast += 80
        aggregate.magicAttackBonus += 10

        if (actorState.hasStatusEffect(StatusEffect.MeikyoShisui)) {
            aggregate.physicalDamageTaken -= 50
            aggregate.magicalDamageTaken -= 50
        }

        if (wantsToUseMeikyo() || wantsToUseOblivion() || moveQueue.isNotEmpty()) { aggregate.tpRequirementBypass = true }
    }

    override fun onDefeated(): List<Event> {
        spawnedFetters.values.forEach(this::releaseFetter)
        return emptyList()
    }

    override fun onSkillBeginCharging(castingState: CastingState): List<Event> {
        val skillId = castingState.skill

        if (skillId == followUpSpell) {
            followUpSpell = null
            return emptyList()
        }

        if (skillId !is MobSkillId) return emptyList()
        val target = ActorStateManager[castingState.targetId] ?: return emptyList()

        val fetterConfig = FetterConfig.values().firstOrNull { it.spellElement == MobSkills[skillId].element } ?: return emptyList()
        pendingFetter = PendingFetter(Vector3f(target.position), fetterConfig, skillId)

        return emptyList()
    }

    override fun wantsToCastSpell(): Boolean {
        return followUpSpell != null
    }

    override fun getSpells(): List<SkillId> {
        return listOfNotNull(followUpSpell)
    }

    override fun wantsToUseSkill(): Boolean {
        if (actorState.timeSinceCreate() < 3.seconds) { return false }
        if (wantsToUseOblivion() || wantsToUseMeikyo() || moveQueue.isNotEmpty()) { return true }
        return super.wantsToUseSkill()
    }

    override fun getSkills(): List<SkillId> {
        if (wantsToUseMeikyo()) { return listOf(mskillMeikyoShisui_474) }
        if (wantsToUseOblivion()) { return listOf(mskillOblivionsMantle_2534) }
        val queuedSkill = moveQueue.firstOrNull() ?: return super.getSkills()
        return listOf(queuedSkill)
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (moveQueue.isEmpty() && primaryTargetContext.skill is MobSkillId) {
            followUpSpell = when (MobSkills[primaryTargetContext.skill].element) {
                SpellElement.Fire -> spellFiraja_496
                SpellElement.Ice -> spellBlizzaja_497
                SpellElement.Wind -> spellAeroja_498
                SpellElement.Earth -> spellStoneja_499
                SpellElement.Lightning -> spellThundaja_500
                SpellElement.Water -> spellWaterja_501
                else -> null
            }
        }

        if (primaryTargetContext.skill == moveQueue.firstOrNull()) {
            moveQueue.removeFirst()
        }

        if (primaryTargetContext.skill == mskillMeikyoShisui_474) {
            hasUsedMeikyo = true
            followUpSpell = null
            moveQueue += elementalSequence.shuffled()
        }

        if (primaryTargetContext.skill == mskillOblivionsMantle_2534) {
            hasUsedOblivion = true
        }

        if (moveQueue.isEmpty() && actorState.hasStatusEffect(StatusEffect.MeikyoShisui)) {
            actorState.expireStatusEffect(StatusEffect.MeikyoShisui)
        }

        maybeSpawnFetter(primaryTargetContext.skill)

        return emptyList()
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        return if (skillId == mskillMeikyoShisui_474) {
            SkillApplier(targetEvaluator = applyBasicBuff(StatusEffect.MeikyoShisui))
        } else if (skillId == mskillOblivionsMantle_2534) {
            SkillApplier(targetEvaluator = V0MobSkillDefinitions.basicPhysicalDamage(attackEffects = AttackEffects(
                attackStatusEffects = listOf(AttackStatusEffect(StatusEffect.Doom, baseDuration = 60.seconds, canResist = false))
            )) { 1f })
        } else {
            null
        }
    }

    override fun getSkillRangeOverride(skill: SkillId): SkillRangeInfo? {
        return if (actorState.hasStatusEffect(StatusEffect.MeikyoShisui) || skill == mskillOblivionsMantle_2534) {
            SkillRangeInfo(maxTargetDistance = 50f, effectRadius = 50f, type = AoeType.Source, tracksTarget = false)
        } else {
            null
        }
    }

    private fun maybeSpawnFetter(skillId: SkillId) {
        val currentConfig = pendingFetter ?: return
        if (currentConfig.skillId != skillId) {
            pendingFetter = null
            return
        }

        val currentFetter = spawnedFetters[currentConfig.config.spellElement]
        if (currentFetter != null) { return }

        spawnedFetters[currentConfig.config.spellElement] = V0MonsterHelper.spawnMonster(
            monsterDefinition = MonsterDefinitions[mobPazuzuFetter_288_501],
            position = currentConfig.position,
            look = currentConfig.config.look,
            actorType = ActorType.StaticNpc,
        ).onReady {
            (it.behaviorController as? MobPazuzuFetter)?.fetterConfig = currentConfig.config
            proximityCheck(it)
        }
    }

    private fun releaseFetter(actorPromise: ActorPromise) {
        actorPromise.onReady(GameV0Helpers::defeatActor)
    }

    private fun wantsToUseMeikyo(): Boolean {
        return !hasUsedMeikyo && actorState.getHpp() <= 0.5f
    }

    private fun wantsToUseOblivion(): Boolean {
        return !hasUsedOblivion && actorState.getHpp() <= 0.3f
    }

    private fun proximityCheck(newFetter: ActorState) {
        spawnedFetters.values.mapNotNull { it.resolveIfReady() }
            .filter { it.id != newFetter.id }
            .filter { it.getTargetingDistance(newFetter) <= 2.75f }
            .forEach(GameV0Helpers::defeatActor)
    }

}

class MobPazuzuFetter(actorState: ActorState): V0MonsterController(actorState) {

    lateinit var fetterConfig: FetterConfig

    override fun update(elapsedFrames: Float): List<Event> {
        if (actorState.isDead()) { return emptyList() }
        val player = ActorStateManager.player()

        val distance = actorState.getTargetingDistance(player)
        if (distance <= 3f) { applyStatus(player) }

        return super.update(elapsedFrames)
    }

    private fun applyStatus(target: ActorState) {
        if (target.isDead()) { return }

        val buff = target.getOrGainStatusEffect(fetterConfig.buff, 1.seconds)
        buff.potency = 100

        val debuff = target.getOrGainStatusEffect(fetterConfig.debuff, 1.seconds)
        debuff.potency = (target.getMaxHp() * 0.1f).roundToInt()
        debuff.secondaryPotency = 0.95f
    }

}