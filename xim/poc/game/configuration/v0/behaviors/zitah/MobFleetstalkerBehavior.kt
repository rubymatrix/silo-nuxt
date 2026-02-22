package xim.poc.game.configuration.v0.behaviors.zitah

import xim.poc.ActorManager
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.GameV0Helpers
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.constants.mobFleetstalkerEarthElemental_288_503
import xim.poc.game.configuration.v0.constants.mobFleetstalkerFireElemental_288_502
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.Event
import xim.poc.gl.ByteColor
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MobFleetstalkerBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private val elementals = HashMap<MonsterId, ActorPromise>()
    private var forcedRoar = false

    override fun onInitialized(): List<Event> {
        dynamicStatusResistance = StatusResistanceTracker().also {
            it[StatusEffect.Stun] = OccurrenceLimitStrategy(resetInterval = 10.minutes, maxOccurrencesInInterval = 3)
        }
        return super.onInitialized()
    }

    override fun update(elapsedFrames: Float): List<Event> {
        updateAppearanceState()
        return super.update(elapsedFrames)
    }

    override fun wantsToUseSkill(): Boolean {
        return forcedRoar || super.wantsToUseSkill()
    }

    override fun getSkills(): List<SkillId> {
        val fireElementalIsAlive = elementals[mobFleetstalkerFireElemental_288_502]?.isAlive() == true

        return if (wantsToUseBeastruction()) {
            listOf(mskillBeastruction_2670)
        } else if (forcedRoar || elementals.isEmpty()) {
            listOf(mskillSoulshatteringRoar_2666)
        } else if (fireElementalIsAlive) {
            super.getSkills() + listOf(mskillHellfireArrow_3250, mskillIncensedPummel_3251)
        } else {
            super.getSkills()
        }
    }

    override fun getSpells(): List<SkillId> {
        val earthElementalIsAlive = elementals[mobFleetstalkerEarthElemental_288_503]?.isAlive() == true
        return if (earthElementalIsAlive) { return listOf(spellQuakeII_211, spellStoneja_499) } else { emptyList() }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillSoulshatteringRoar_2666) {
            forcedRoar = false
            spawnElementals()
        } else if (primaryTargetContext.skill == mskillBeastruction_2670) {
            forcedRoar = true
            elementals.entries.removeAll { it.value.isObsolete() }
        }

        return super.onSkillExecuted(primaryTargetContext)
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.Object
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Silence)
        aggregate.spellInterruptDown += 100
        aggregate.knockBackResistance += 100
        aggregate.refresh += 10

        if (hasHighDefenseBoost()) {
            aggregate.fastCast += 60
            aggregate.physicalDamageTaken -= 100
            aggregate.magicalDamageTaken -= 100
        }

        if (hasHighAttackBoost()) {
            aggregate.regain += 100
            aggregate.mobSkillFastCast += 100
        }

        if (forcedRoar) {
            aggregate.tpRequirementBypass = true
        }
    }

    override fun getSkillCastTimeOverride(skill: SkillId?): Duration? {
        return when(skill) {
            mskillBeastruction_2670 -> 0.5.seconds
            else -> null
        }
    }

    private fun spawnElementals() {
        elementals.getOrPut(mobFleetstalkerFireElemental_288_502) {
            V0MonsterHelper.spawnMonster(
                monsterDefinition = MonsterDefinitions[mobFleetstalkerFireElemental_288_502],
                position = actorState.position,
            ).onReady {
                (it.behaviorController as? MobFleetstalkerElementalBehavior)?.leader = actorState
            }
        }

        elementals.getOrPut(mobFleetstalkerEarthElemental_288_503) {
            V0MonsterHelper.spawnMonster(
                monsterDefinition = MonsterDefinitions[mobFleetstalkerEarthElemental_288_503],
                position = actorState.position,
            ).onReady {
                (it.behaviorController as? MobFleetstalkerElementalBehavior)?.leader = actorState
            }
        }
    }

    private fun wantsToUseBeastruction(): Boolean {
        val elementalStates = elementals.values.mapNotNull { it.resolveIfReady() }
        return elementalStates.isNotEmpty() && elementalStates.all { it.isDead() }
    }

    private fun hasHighDefenseBoost(): Boolean {
        val defenseBoost = actorState.getStatusEffect(StatusEffect.Protect)
        return defenseBoost != null && defenseBoost.counter >= 50
    }

    private fun hasHighAttackBoost(): Boolean {
        val attackBoost = actorState.getStatusEffect(StatusEffect.AttackBoost)
        return attackBoost != null && attackBoost.counter >= 50
    }

    private fun updateAppearanceState() {
        actorState.appearanceState = if (!actorState.isDead() && hasHighDefenseBoost()) { 1 } else { 0 }

        if (actorState.isDead()) {
            ActorManager[actorState.id]?.actorModel?.customModelSettings?.blurConfig = null
            return
        }

        val blur = if (hasHighAttackBoost()) {
            standardBlurConfig(ByteColor(0x80, 0x20, 0x20, 0x20), radius = 150f)
        } else {
            null
        }

        ActorManager[actorState.id]?.actorModel?.customModelSettings?.blurConfig = blur
    }

}

class MobFleetstalkerElementalBehavior(actorState: ActorState): V0MonsterController(actorState) {

    lateinit var leader: ActorState

    override fun update(elapsedFrames: Float): List<Event> {
        if (leader.isDead()) {
            GameV0Helpers.defeatActor(actorState)
            return emptyList()
        }

        actorState.syncEnmity(leader)

        if (actorState.isDead()) { return emptyList() }

        applyBuff()
        return super.update(elapsedFrames)
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.allStatusResistRate += 100
        aggregate.regain += 100
        aggregate.movementSpeed -= 90
    }

    private fun applyBuff() {
        val potency = actorState.timeSinceCreate().inWholeSeconds.toInt()

        when (actorState.monsterId) {
            mobFleetstalkerFireElemental_288_502 -> applyAttackBuff(potency)
            mobFleetstalkerEarthElemental_288_503 -> applyDefenseBuff(potency)
        }
    }

    private fun applyAttackBuff(potency: Int) {
        val attackBoost = leader.getOrGainStatusEffect(StatusEffect.AttackBoost, duration = 1.seconds)
        attackBoost.potency = potency
        attackBoost.counter = potency
        attackBoost.displayCounter = true

        val mab = leader.getOrGainStatusEffect(StatusEffect.MagicAtkBoost, duration = 1.seconds)
        mab.potency = potency
        mab.counter = potency
        mab.displayCounter = true
    }

    private fun applyDefenseBuff(potency: Int) {
        val defenseBoost = leader.getOrGainStatusEffect(StatusEffect.Protect, duration = 1.seconds)
        defenseBoost.potency = potency
        defenseBoost.counter = potency
        defenseBoost.displayCounter = true

        val magicDefenseBoost = leader.getOrGainStatusEffect(StatusEffect.Shell, duration = 1.seconds)
        magicDefenseBoost.potency = potency
        magicDefenseBoost.counter = potency
        magicDefenseBoost.displayCounter = true
    }

    override fun onIncomingDamage(context: ActorDamagedContext): Int? {
        return if (context.damageAmount > actorState.getMaxHp()) {
            floor(actorState.getMaxHp() * 0.99).roundToInt()
        } else {
            null
        }
    }

}