package xim.poc.game.configuration.v0.behaviors.zitah

import xim.math.Vector3f
import xim.poc.ActorManager
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0MobSkillDefinitions
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.constants.mobWrathareClone_288_506
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.AttackAddedEffectType
import xim.poc.game.event.Event
import xim.poc.gl.ByteColor
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MobWrathareBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val skillCooldown = FrameTimer(10.seconds, initial = ZERO)

    private var readyDeath = false
    private val clones = ArrayList<ActorPromise>()

    override fun onInitialized(): List<Event> {
        dynamicStatusResistance = StatusResistanceTracker().also {
            it[StatusEffect.Stun] = OccurrenceLimitStrategy(resetInterval = 10.minutes, maxOccurrencesInInterval = 3)
        }
        return super.onInitialized()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        skillCooldown.update(elapsedFrames)
        inflictDoom()
        maybeProcDoom()
        return super.update(elapsedFrames)
    }

    override fun wantsToUseSkill(): Boolean {
        return skillCooldown.isReady()
    }

    override fun wantsToCastSpell(): Boolean {
        return readyDeath || super.wantsToCastSpell()
    }

    override fun getSpells(): List<SkillId> {
        return if (readyDeath) {
            listOf(spellDeath_367)
        } else {
            listOf(spellTemper_493, spellHasteII_511)
        }
    }

    override fun onSkillBeginCharging(castingState: CastingState): List<Event> {
        if (castingState.skill == spellDeath_367) { readyDeath = false }
        return emptyList()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill is MobSkillId) {
            skillCooldown.reset()
        }

        if (primaryTargetContext.skill == mskillWhirlClaws_3) {
            spawnClones()
        } else if (primaryTargetContext.skill == mskillDustCloud_2) {
            readyDeath = true
        } else if (primaryTargetContext.skill == mskillFootKick_1) {
            actorState.gainStatusEffect(StatusEffect.Blink).also { it.counter = 12 }
        }

        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify)
        aggregate.spellInterruptDown += 100
        aggregate.refresh += 10

        aggregate.autoAttackEffects += AutoAttackEffect(effectPower = 0, effectType = AttackAddedEffectType.Curse)
        aggregate.autoAttackScale = 0.5f
        aggregate.haste += 35

        super.applyMonsterBehaviorBonuses(aggregate)
    }

    override fun onAutoAttackExecuted() {
        val target = ActorStateManager[actorState.getTargetId()] ?: return
        decrementDoomCounter(target)
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.Object
    }

    override fun getSkillApplierOverride(skillId: SkillId): SkillApplier? {
        if (skillId != spellDeath_367) { return null }
        return SkillApplier(targetEvaluator = V0MobSkillDefinitions.basicMagicalWs { 5f })
    }

    private fun spawnClones() {
        clones.removeAll { it.isObsolete() }
        val clonesToSummon = 6 - clones.size

        for (i in 0 until clonesToSummon) {
            clones += V0MonsterHelper.spawnMonster(
                monsterDefinition = MonsterDefinitions[mobWrathareClone_288_506],
                position = Vector3f(actorState.position),
                actorType = ActorType.Enemy,
            ).onReady {
                (it.behaviorController as? MobWrathareCloneBehavior)?.leader = actorState
            }
        }
    }

    private fun inflictDoom() {
        val (target, existingDoom) = getTargetDoom()
        if (target == null || target.isDead() || existingDoom != null) { return }

        val doom = target.getOrGainStatusEffect(StatusEffect.Doom)
        doom.displayCounter = true
        doom.counter = 66
        doom.sourceId = actorState.id
    }

    private fun maybeProcDoom() {
        val (target, doom) = getTargetDoom()
        if (target == null || doom == null) { return }
        if (!target.isDead() && doom.counter <= 0) { GameV0Helpers.defeatActor(target) }
    }

    private fun getTargetDoom(): Pair<ActorState?, StatusEffectState?> {
        val target = ActorStateManager[actorState.getTargetId()] ?: return null to null
        val doom = target.getStatusEffect(StatusEffect.Doom)
        return target to doom
    }

}

class MobWrathareCloneBehavior(actorState: ActorState): V0MonsterController(actorState) {

    lateinit var leader: ActorState

    override fun update(elapsedFrames: Float): List<Event> {
        adjustModel()
        actorState.syncEnmity(leader)

        if (!actorState.isDead() && leader.isDead()) {
            GameV0Helpers.defeatActor(actorState)
            return emptyList()
        }

        return super.update(elapsedFrames)
    }

    override fun onAutoAttackExecuted() {
        val target = ActorStateManager[actorState.getTargetId()] ?: return
        decrementDoomCounter(target)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.allStatusResistRate += 100
        aggregate.autoAttackEffects += AutoAttackEffect(effectPower = 0, effectType = AttackAddedEffectType.Curse)
        aggregate.haste += 35
    }

    override fun getSkills(): List<SkillId> {
        return emptyList()
    }

    private fun adjustModel() {
        val actor = ActorManager[actorState.id]?.actorModel ?: return
        actor.customModelSettings.blurConfig = standardBlurConfig(blurs = 1, color = ByteColor(0x00, 0x00, 0x00, 0x7F))
        actor.customModelSettings.hideMain = true
    }

}

private fun decrementDoomCounter(target: ActorState) {
    val doom = target.getStatusEffect(StatusEffect.Doom) ?: return
    doom.counter = (doom.counter - 1).coerceAtLeast(0)
}