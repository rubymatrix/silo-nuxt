package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.FollowPartyController
import xim.poc.PetController
import xim.poc.game.*
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.getEquipment
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.*
import xim.poc.game.configuration.v0.constants.petWyvern
import xim.poc.game.configuration.v0.synthesis.ActorSynthesisComponent
import xim.poc.game.event.*
import xim.poc.ui.DamageTextManager
import xim.resource.*
import xim.resource.table.AbilityInfoTable.toAbilityInfo
import xim.resource.table.SpellInfoTable.toSpellInfo
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.seconds

object PlayerBehaviorId: BehaviorId

private class PlayerPet(
    val itemId: InternalItemId,
    val petPromise: ActorPromise,
)

class PlayerBehavior(val actorState: ActorState): ActorBehaviorController {

    private val pets = HashMap<EquipSlot, PlayerPet>()
    private val delegate = AutoAttackController(actorState)

    private val buffDecayTimer = FrameTimer(1.seconds)

    override fun update(elapsedFrames: Float): List<Event> {
        val output = ArrayList<Event>()

        buffDecayTimer.update(elapsedFrames)
        if (buffDecayTimer.isReady()) {
            decayBuffs()
            buffDecayTimer.reset()
        }

        if (!actorState.isDead()) {
            checkPets()
            checkWyvern()
        }

        return output + delegate.update(elapsedFrames)
    }

    override fun onAutoAttackExecuted() {
        val bonuses = CombatBonusAggregator[actorState]

        if (bonuses.restraint > 0) {
            val status = actorState.getOrGainStatusEffect(StatusEffect.Restraint)
            status.counter = (status.counter + bonuses.restraint).coerceAtMost(100)
            status.linkedSkillId = skillRestraint_764
        }

        if (bonuses.impetus > 0) {
            val status = actorState.getOrGainStatusEffect(StatusEffect.Impetus)
            status.counter = (status.counter + bonuses.impetus).coerceAtMost(50)
            status.linkedSkillId = skillImpetus_781
        }
    }

    override fun onSkillBeginCharging(castingState: CastingState): List<Event> {
        if (castingState.skill is SpellSkillId) { actorState.expireStatusEffect(StatusEffect.Impetus) }

        val rangeInfo = GameV0.getSkillRangeInfo(actorState, castingState.skill)
        val targetState = ActorStateManager[castingState.targetId]
        if (targetState != null && rangeInfo.type == AoeType.Cone) { actorState.faceToward(targetState) }

        return emptyList()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        val bonuses = CombatBonusAggregator[actorState]
        val results = ArrayList<Event>()

        val abilityInfo = (primaryTargetContext.skill as? AbilitySkillId)?.toAbilityInfo()
        val isWeaponSkill = abilityInfo?.type == AbilityType.WeaponSkill

        if (isWeaponSkill && GameEngine.rollAgainst(bonuses.occSpontaneity)) {
            val status = actorState.gainStatusEffect(StatusEffect.Spontaneity, duration = 10.seconds, sourceId = actorState.id)
            status.counter += 1
        }

        if (isWeaponSkill && GameEngine.rollAgainst(bonuses.occImmanence)) {
            val status = actorState.gainStatusEffect(StatusEffect.Immanence, duration = 10.seconds, sourceId = actorState.id)
            status.counter += 1
        }

        if (isWeaponSkill) {
            actorState.expireStatusEffect(StatusEffect.Restraint)
        }

        val spellInfo = (primaryTargetContext.skill as? SpellSkillId)?.toSpellInfo()
        if (spellInfo != null && spellInfo.element != SpellElement.None) {
            results += StatusEffectConsumeChargeEvent(
                sourceId = primaryTargetContext.sourceState.id,
                statusEffect = StatusEffect.Immanence,
                expireOnZero = true,
            )
        }

        return results
    }

    override fun onAttackExecuted(actorAttackedEvent: ActorAttackedEvent): List<Event> {
        val events = ArrayList<Event>()

        val spellInfo = (actorAttackedEvent.skill as? SpellSkillId)?.toSpellInfo()
        if (spellInfo != null && spellInfo.magicType == MagicType.BlueMagic) {
            if (actorAttackedEvent.damageType == AttackDamageType.Physical) {
                events += StatusEffectConsumeChargeEvent(actorState.id, StatusEffect.ChainAffinity)
            } else if (actorAttackedEvent.damageType == AttackDamageType.Magical) {
                events += StatusEffectConsumeChargeEvent(actorState.id, StatusEffect.BurstAffinity)
            }
        }

        return events
    }

    override fun onDamaged(context: ActorDamagedContext): List<Event> {
        val synthesisState = actorState.getComponentAs(ActorSynthesisComponent::class)
        if (synthesisState != null) { synthesisState.interrupt() }

        val bonuses = CombatBonusAggregator[actorState]
        if (bonuses.retaliation > 0 && context.damageAmount > 0) {
            val hppDamage = bonuses.retaliation * context.damageAmount / actorState.getMaxHp()
            val status = actorState.getOrGainStatusEffect(StatusEffect.Retaliation)
            status.counter = (status.counter + 2 * hppDamage).coerceAtMost(100)
            status.linkedSkillId = skillRetaliation_738
        }

        return super.onDamaged(context)
    }

    override fun onStatusEffectGained(statusEffectState: StatusEffectState) {
        DamageTextManager.statusEvent(actorState, statusEffectState)
        super.onStatusEffectGained(statusEffectState)
    }

    fun onGearChanged(changedEquipment: Map<EquipSlot, InternalItemId?>): List<Event> {
        if (changedEquipment.containsKey(EquipSlot.Main) || changedEquipment.containsKey(EquipSlot.Sub)) {
            actorState.setTp(0)
            actorState.expireStatusEffect(StatusEffect.Impetus)
        }

        val abilities = GameV0.getActorAbilityList(actorState.id, AbilityType.WeaponSkill)
        if (!abilities.contains(skillBerserk_543)) { actorState.expireStatusEffect(StatusEffect.Berserk) }

        return emptyList()
    }

    private fun checkPets() {
        pets.entries.removeAll { it.value.petPromise.isObsolete() }
        listOf(EquipSlot.Neck, EquipSlot.LEar, EquipSlot.REar).forEach(this::checkPet)
    }

    private fun checkPet(equipSlot: EquipSlot) {
        val item = actorState.getEquipment(equipSlot) ?: return releasePet(equipSlot)
        val itemPet = ItemDefinitions[item].pet ?: return releasePet(equipSlot)

        val currentPet = pets[equipSlot]

        if (currentPet != null && currentPet.itemId != item.internalId) { return releasePet(equipSlot) }

        if (currentPet != null && currentPet.itemId == item.internalId) { return }

        if (GameV0.isInBaseCamp()) { return releasePet(equipSlot) }

        if (GameV0Helpers.hasAnyEnmity(actorState.id)) { return }

        pets[equipSlot] = PlayerPet(item.internalId, summonPet(itemPet))
    }

    private fun summonPet(itemPet: ItemPet): ActorPromise {
        val monsterDefinition = MonsterDefinitions[itemPet.petId]

        return V0MonsterHelper.spawnMonster(
            monsterDefinition = monsterDefinition,
            position = actorState.position + Vector3f.NegX * 2f,
            actorType = ActorType.AllyNpc,
            movementControllerFn = { FollowPartyController() },
            mutator = {
                it.copy(
                    popRoutines = listOf(itemPet.spawnAnimation),
                    dependentSettings = DependentSettings(actorState.id, ActorTrust),
                )
            }
        ).onReady {
            val party = PartyManager[actorState.id]
            party.addMember(it.id)

            it.faceToward(actorState)
            it.targetable = false
        }
    }

    private fun releasePet(equipSlot: EquipSlot) {
        pets[equipSlot]?.petPromise?.onReady {
            GameEngine.submitEvent(TrustReleaseEvent(sourceId = actorState.id, targetId = it.id))
        }
    }

    private fun decayBuffs() {
        val retaliation = actorState.getStatusEffect(StatusEffect.Retaliation)
        if (retaliation != null) { actorState.consumeStatusEffectCharge(StatusEffect.Retaliation, expireOnZero = true, consumeAmount = 1) }
    }

    private fun checkWyvern() {
        val item = actorState.getEquipment(EquipSlot.Main) ?: return releaseWyvern()
        if (item.info().skill() != Skill.PoleArm) { return releaseWyvern() }

        val currentWyvern = ActorStateManager[actorState.pet]

        if (currentWyvern != null && currentWyvern.monsterId != petWyvern) { return releaseWyvern() }

        if (currentWyvern != null && currentWyvern.monsterId == petWyvern) { return }

        if (GameV0.isInBaseCamp()) { return releaseWyvern() }

        if (GameV0Helpers.hasAnyEnmity(actorState.id)) { return }

        summonWyvern()
    }

    private fun summonWyvern(): ActorPromise {
        val monsterDefinition = MonsterDefinitions[petWyvern]

        val wyvernActorId = ActorStateManager.nextId()
        actorState.pet = wyvernActorId

        return GameEngine.submitCreateActorState(InitialActorState(
            name = monsterDefinition.name,
            presetId = wyvernActorId,
            monsterId = petWyvern,
            type = ActorType.AllyNpc,
            position = actorState.position,
            modelLook = monsterDefinition.look,
            movementController = PetController(),
            behaviorController = monsterDefinition.behaviorId,
            dependentSettings = DependentSettings(actorState.id, ActorPet),
            popRoutines = listOf(DatId.spop),
        ))
    }

    private fun releaseWyvern() {
        GameEngine.submitEvent(PetReleaseEvent(actorState.id))
    }

}