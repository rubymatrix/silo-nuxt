package xim.poc.game.configuration.v0

import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.getEquipment
import xim.poc.game.actor.components.isDualWield
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.v0.GameV0Helpers.hasAnyEnmity
import xim.poc.game.configuration.v0.ItemTraitCustomId.*
import xim.poc.game.configuration.v0.mining.isGatheringNode
import xim.poc.game.event.AttackAddedEffectType
import xim.poc.game.event.AttackRetaliationEffectType
import xim.poc.game.event.AttackStatusEffect
import xim.resource.EquipSlot
import xim.resource.SpellElement
import xim.util.addInPlace
import xim.util.multiplyInPlace
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

object CombatBonusAggregator {

    private val computed = HashMap<ActorId, CombatBonusAggregate>()

    fun clear() {
        computed.clear()
    }

    operator fun get(actorState: ActorState): CombatBonusAggregate {
        return computed.getOrPut(actorState.id) { computeActorCombatBonuses(actorState) }
    }

    fun clear(actorState: ActorState) {
        computed.remove(actorState.id)
    }

    fun <T> shallowBonusScope(actorState: ActorState, scope: (CombatBonusAggregate) -> T): T {
        val original = get(actorState)

        val scopeBonus = original.copy()
        computed[actorState.id] = scopeBonus

        val output = scope.invoke(scopeBonus)
        computed[actorState.id] = original

        return output
    }

    private fun computeActorCombatBonuses(actor: ActorState): CombatBonusAggregate {
        val aggregate = CombatBonusAggregate(actor)
        aggregateStatusEffects(actor, aggregate)
        aggregateEquipmentStats(actor, aggregate)
        aggregateEquipmentTraits(actor, aggregate)
        aggregateEquipmentAugmentEffects(actor, aggregate)

        val applyRestingTick = if (actor.type == ActorType.Pc) {
            actor.isResting() || !hasAnyEnmity(actor.id)
        } else {
            actor.isIdle() && !actor.isGatheringNode()
        }

        if (applyRestingTick && !actor.hasStatusEffect(StatusEffect.Disease)) {
            aggregate.regen += (actor.getMaxHp() * 0.1f).roundToInt()
            aggregate.refresh += (actor.getMaxMp() * 0.1f).roundToInt()
        }

        if (actor.isPlayer()) {
            aggregate.regen += 1
            aggregate.refresh += 1
        }

        if (actor.isDualWield()) {
            aggregate.dualWield += 10
        }

        if (actor.monsterId != null) {
            val defn = MonsterDefinitions[actor.monsterId]
            defn.baseBonusApplier.invoke(aggregate)
        }

        val castingState = actor.getCastingState()
        if (castingState != null && castingState.isCharging()) {
            addCastingBonus(actor, aggregate, castingState)
        }

        actor.behaviorController.applyBehaviorBonuses(aggregate)
        return aggregate
    }

    private fun aggregateEquipmentAugmentEffects(actorState: ActorState, aggregate: CombatBonusAggregate) {
        for ((_, item) in actorState.getEquipment()) {
            if (item == null) { continue }
            addEquipmentAugmentEffects(item, aggregate)
            addEquipmentCapacityAugments(item, aggregate)
            addEquipmentSlottedAugments(item, aggregate)

            val definition = ItemDefinitions[item]
            definition.staticAugments?.forEach { aggregate.aggregate(it.first, it.second) }
        }
    }

    private fun addEquipmentAugmentEffects(item: InventoryItem, aggregate: CombatBonusAggregate) {
        val augments = item.augments ?: return
        for (augmentId in augments.augmentIds) {
            val def = ItemAugmentDefinitions[augmentId]
            aggregate.aggregate(def.attribute, def.valueFn.calculate(item))
        }
    }

    private fun addEquipmentCapacityAugments(item: InventoryItem, aggregate: CombatBonusAggregate) {
        val augments = item.fixedAugments ?: return
        for ((augmentId, augment) in augments.augments) {
            aggregate.aggregate(augmentId, augment.potency)
        }
    }

    private fun addEquipmentSlottedAugments(item: InventoryItem, aggregate: CombatBonusAggregate) {
        val augments = item.slottedAugments ?: return
        for ((_, augment) in augments.augments) {
            aggregate.aggregate(augment.augmentId, augment.potency)
        }
    }

    private fun aggregateEquipmentStats(actor: ActorState, aggregate: CombatBonusAggregate) {
        val equipment = actor.getEquipment()

        for ((_, item) in equipment) {
            item ?: continue
            val itemDefinition = ItemDefinitions[item]
            aggregate.aggregate(itemDefinition.combatStats)
        }
    }

    private fun aggregateEquipmentTraits(actor: ActorState, aggregate: CombatBonusAggregate) {
        val equipment = actor.getEquipment()

        for ((slot, item) in equipment) {
            item ?: continue
            val itemDefinition = ItemDefinitions[item]

            for (trait in itemDefinition.traits) {
                when (trait.itemTraitId) {
                    is ItemTraitAugment -> aggregateItemTraitAugment(aggregate, trait, trait.itemTraitId.id, slot)
                    is ItemTraitCustom -> aggregateItemTraitCustom(aggregate, trait, trait.itemTraitId.id, slot)
                }
            }
        }
    }

    private fun aggregateItemTraitAugment(aggregate: CombatBonusAggregate, trait: ItemTrait, augmentId: AugmentId, equipSlot: EquipSlot) {
        when (trait.handRestriction) {
            AugmentRestriction.None -> aggregate.aggregate(augmentId, trait.potency)
            AugmentRestriction.MainOnly -> if (equipSlot == EquipSlot.Main) { aggregate.aggregate(augmentId, trait.potency) }
            AugmentRestriction.HandOnly -> aggregate[equipSlot].aggregate(augmentId, trait.potency)
        }
    }

    private fun aggregateItemTraitCustom(aggregate: CombatBonusAggregate, trait: ItemTrait, customId: ItemTraitCustomId, equipSlot: EquipSlot) {
        if (trait.handRestriction == AugmentRestriction.MainOnly && equipSlot != EquipSlot.Main) { return }

        when (customId) {
            Wyvern -> Unit
            SpontaneityConserveMp -> Unit
            SpontaneityMbbII -> Unit
            Retaliation -> aggregate.retaliation += trait.potency
            WeaponSkillSpontaneity -> aggregate.occSpontaneity += trait.potency
            WeaponSkillImmanence -> aggregate.occImmanence += trait.potency
            ConvertDamageToMp -> aggregate[equipSlot].occDamageToMp += trait.potency
            ExtendsSkillChainDuration -> aggregate.skillChainWindowBonus += trait.potency.seconds
            Restraint -> aggregate.restraint += trait.potency
            BluTpGain -> aggregate.blueMagicTpGain += trait.potency
            Impetus -> aggregate.impetus += trait.potency
        }

        if (customId == SpontaneityConserveMp && aggregate.actorState.hasStatusEffect(StatusEffect.Spontaneity) && equipSlot == EquipSlot.Main) {
             aggregate.conserveMp += trait.potency
        }

        if (customId == SpontaneityMbbII && aggregate.actorState.hasStatusEffect(StatusEffect.Spontaneity)  && equipSlot == EquipSlot.Main) {
            aggregate.magicBurstDamageII += trait.potency
        }
    }

    private fun addCastingBonus(actor: ActorState, aggregate: CombatBonusAggregate, castingState: CastingState) {
        var resistsStatusActionLock = false

        if (castingState.skill is MobSkillId) {
            val mobSkillInfo = MobSkills[castingState.skill]
            resistsStatusActionLock = !mobSkillInfo.interruptable
        }

        if (resistsStatusActionLock) {
            aggregate.fullResist(StatusEffect.Stun, StatusEffect.Sleep, StatusEffect.Petrify, StatusEffect.Terror)
        }
    }

    private fun aggregateStatusEffects(actor: ActorState, aggregate: CombatBonusAggregate) {
        val statuses = actor.getStatusEffects()
        statuses.forEach { aggregate.aggregate(it) }
    }

    private fun CombatBonusAggregate.aggregate(augmentId: AugmentId, value: Int) {
        when (augmentId) {
            AugmentId.HP -> additiveStats.maxHp += value
            AugmentId.MP -> additiveStats.maxMp += value
            AugmentId.STR -> additiveStats.str += value
            AugmentId.DEX -> additiveStats.dex += value
            AugmentId.VIT -> additiveStats.vit += value
            AugmentId.AGI -> additiveStats.agi += value
            AugmentId.INT -> additiveStats.int += value
            AugmentId.MND -> additiveStats.mnd += value
            AugmentId.CHR -> additiveStats.chr += value
            AugmentId.CriticalHitRate -> criticalHitRate += value
            AugmentId.EnemyCriticalHitRate -> enemyCriticalHitRate += value
            AugmentId.Haste -> haste += value
            AugmentId.SpellInterruptDown -> spellInterruptDown += value
            AugmentId.PhysicalDamageTaken -> physicalDamageTaken += value
            AugmentId.MagicalDamageTaken -> magicalDamageTaken += value
            AugmentId.DamageTaken -> { physicalDamageTaken += value; magicalDamageTaken += value }
            AugmentId.LatentRegain -> { if (hasAnyEnmity(actorState.id)) { regain += value } }
            AugmentId.TpBonus -> tpBonus += value
            AugmentId.ParryingRate -> parryRate += value
            AugmentId.Regen -> regen += value
            AugmentId.Refresh -> refresh += value
            AugmentId.MagicAttackBonus -> magicAttackBonus += value
            AugmentId.FastCast -> fastCast += value
            AugmentId.StoreTp -> storeTp += value
            AugmentId.DoubleAttack -> doubleAttack += value
            AugmentId.TripleAttack -> tripleAttack += value
            AugmentId.QuadrupleAttack -> quadrupleAttack += value
            AugmentId.DualWield -> dualWield += value
            AugmentId.SubtleBlow -> subtleBlow += value
            AugmentId.WeaponSkillDamage -> weaponSkillDamage += value
            AugmentId.CriticalHitDamage -> criticalHitDamage += value
            AugmentId.SkillChainDamage -> skillChainDamage += value
            AugmentId.ConserveTp -> conserveTp += value
            AugmentId.MagicBurstDamage -> magicBurstDamage += value
            AugmentId.CriticalTpGain -> criticalTpGain += value
            AugmentId.ElementalWeaponSkillDamage -> elementalWeaponSkillDamage += value
            AugmentId.DoubleDamage -> doubleDamage += value
            AugmentId.FollowUpAttack -> followUpAttack += value
            AugmentId.WeaponDamage -> Unit
            AugmentId.AlterEgo -> Unit
            AugmentId.Blank -> Unit
            AugmentId.OccAttack2x -> Unit
            AugmentId.OccAttack2x3x -> Unit
            AugmentId.OccAttack2x4x -> Unit
            AugmentId.OccDoubleDamage -> Unit
            AugmentId.SaveTp -> saveTp += value
            AugmentId.MagicBurstII -> magicBurstDamageII += value
            AugmentId.ConserveMp -> conserveMp += value
            AugmentId.KickAttacks -> kickAttackRate += value
        }
    }

    private fun CombatBonusSubAggregate.aggregate(augmentId: AugmentId, value: Int) {
        when (augmentId) {
            AugmentId.OccAttack2x -> occAttack2x += value
            AugmentId.OccAttack2x3x -> { occAttack2x += value; occAttack3x += value }
            AugmentId.OccAttack2x4x -> { occAttack2x += value; occAttack3x += value; occAttack4x += value }
            AugmentId.OccDoubleDamage -> occDoubleDamage += value
            else -> Unit
        }
    }

    private fun CombatBonusAggregate.aggregate(combatStats: CombatStats) {
        additiveStats.add(combatStats)
    }

    private fun CombatBonusAggregate.aggregate(statusEffectState: StatusEffectState) {
        when (statusEffectState.statusEffect) {
            StatusEffect.Poison -> regen -= statusEffectState.potency
            StatusEffect.Weight -> movementSpeed -= statusEffectState.potency
            StatusEffect.Slow -> haste -= statusEffectState.potency
            StatusEffect.Dia -> handleDia(statusEffectState)
            StatusEffect.Bio -> handleBio(statusEffectState)
            StatusEffect.Plague -> {
                refresh -= statusEffectState.potency
                regain -= statusEffectState.potency * 10
            }
            StatusEffect.Haste -> haste += statusEffectState.potency
            StatusEffect.Burn -> handleElementalDoT(statusEffectState, CombatStat.int)
            StatusEffect.Frost -> handleElementalDoT(statusEffectState, CombatStat.agi)
            StatusEffect.Choke -> handleElementalDoT(statusEffectState, CombatStat.vit)
            StatusEffect.Mounted -> movementSpeed += 100
            StatusEffect.StrDown -> multiplicativeStats.multiplyInPlace(CombatStat.str, statusEffectState.secondaryPotency)
            StatusEffect.DexDown -> multiplicativeStats.multiplyInPlace(CombatStat.dex, statusEffectState.secondaryPotency)
            StatusEffect.AgiDown -> multiplicativeStats.multiplyInPlace(CombatStat.agi, statusEffectState.secondaryPotency)
            StatusEffect.VitDown -> multiplicativeStats.multiplyInPlace(CombatStat.vit, statusEffectState.secondaryPotency)
            StatusEffect.MndDown -> multiplicativeStats.multiplyInPlace(CombatStat.mnd, statusEffectState.secondaryPotency)
            StatusEffect.HPDown -> multiplicativeStats.multiplyInPlace(CombatStat.maxHp, statusEffectState.secondaryPotency)
            StatusEffect.Regen, StatusEffect.Regen2 -> regen += statusEffectState.potency
            StatusEffect.Berserk -> {
                multiplicativeStats.multiplyInPlace(CombatStat.str, 1.5f)
                multiplicativeStats.multiplyInPlace(CombatStat.vit, 0.5f)
            }
            StatusEffect.AttackBoost, StatusEffect.AttackBoost2 -> multiplicativeStats.multiplyInPlace(CombatStat.str, statusEffectState.potency.toMultiplier())
            StatusEffect.EvasionBoost -> evasionRate += statusEffectState.potency
            StatusEffect.DefenseBoost -> physicalDamageTaken -= statusEffectState.potency
            StatusEffect.AttackDown -> multiplicativeStats.multiplyInPlace(CombatStat.str, statusEffectState.potency.toPenaltyMultiplier())
            StatusEffect.DefenseDown -> multiplicativeStats.multiplyInPlace(CombatStat.vit, statusEffectState.potency.toPenaltyMultiplier())
            StatusEffect.MagicDefDown -> magicalDamageTaken += statusEffectState.potency
            StatusEffect.MagicAtkDown -> magicAttackBonus -= statusEffectState.potency
            StatusEffect.MagicAtkBoost, StatusEffect.MagicAtkBoost2 -> magicAttackBonus += statusEffectState.potency
            StatusEffect.MagicDefBoost, StatusEffect.MagicDefBoost2 -> magicalDamageTaken -= statusEffectState.potency
            StatusEffect.Enaspir -> autoAttackEffects += AutoAttackEffect(statusEffectState.potency, AttackAddedEffectType.Aspir)
            StatusEffect.IceSpikes -> handleIceSpikes(statusEffectState)
            StatusEffect.ShiningRuby -> { physicalDamageTaken -= statusEffectState.potency; magicalDamageTaken -= statusEffectState.potency; }
            StatusEffect.Regain -> regain += statusEffectState.potency
            StatusEffect.Shell -> magicalDamageTaken -= statusEffectState.potency
            StatusEffect.ShockSpikes -> handleShockSpikes(statusEffectState)
            StatusEffect.BlazeSpikes -> handleBlazeSpikes(statusEffectState)
            StatusEffect.DelugeSpikes -> handleDelugeSpikes(statusEffectState)
            StatusEffect.Warcry -> multiplicativeStats.multiplyInPlace(CombatStat.str, statusEffectState.potency.toMultiplier())
            StatusEffect.DreadSpikes -> handleDreadSpikes(statusEffectState)
            StatusEffect.Drown -> handleElementalDoT(statusEffectState, CombatStat.str)
            StatusEffect.Boost -> boost += statusEffectState.potency
            StatusEffect.Enfire -> autoAttackEffects += AutoAttackEffect(statusEffectState.potency, AttackAddedEffectType.Fire)
            StatusEffect.Refresh -> refresh += statusEffectState.potency
            StatusEffect.Curse -> {
                multiplicativeStats.multiplyInPlace(CombatStat.maxHp, statusEffectState.potency.toPenaltyMultiplier())
                multiplicativeStats.multiplyInPlace(CombatStat.maxMp, statusEffectState.potency.toPenaltyMultiplier())
                movementSpeed -= statusEffectState.potency
            }
            StatusEffect.CounterBoost -> counterRate += statusEffectState.potency
            StatusEffect.Protect -> physicalDamageTaken -= statusEffectState.potency
            StatusEffect.MPDown -> multiplicativeStats.multiplyInPlace(CombatStat.maxMp, statusEffectState.secondaryPotency)
            StatusEffect.IntDown -> multiplicativeStats.multiplyInPlace(CombatStat.int, statusEffectState.secondaryPotency)
            StatusEffect.Rasp -> handleElementalDoT(statusEffectState, CombatStat.dex)
            StatusEffect.Elegy -> haste -= statusEffectState.potency
            StatusEffect.March -> haste += statusEffectState.potency
            StatusEffect.Ballad -> refresh += statusEffectState.potency
            StatusEffect.Vorseal -> GameV0.applyVorsealBonus(this)
            StatusEffect.Chainspell -> fastCast += 1000
            StatusEffect.MultiStrikes -> handleMultiStrikes(statusEffectState)
            StatusEffect.HundredFists -> haste += 1000
            StatusEffect.Shock -> handleElementalDoT(statusEffectState, CombatStat.mnd)
            StatusEffect.Impetus -> {
                criticalHitDamage += statusEffectState.counter/2
                criticalHitRate += statusEffectState.counter
            }
            StatusEffect.Counterstance -> {
                counterRate += 50
                multiplicativeStats.multiplyInPlace(CombatStat.vit, 0.5f)
            }
            StatusEffect.BloodRage -> {
                retaliation += statusEffectState.potency/2
                multiplicativeStats.multiplyInPlace(CombatStat.maxHp, (statusEffectState.potency/2).toMultiplier())
            }
            StatusEffect.Retaliation -> boost += statusEffectState.counter/2
            StatusEffect.WarriorsCharge -> {
                tpBonus += statusEffectState.potency * 5
                criticalHitRate += statusEffectState.potency / 2
            }
            StatusEffect.Addle -> fastCast -= statusEffectState.potency
            StatusEffect.Barfire -> elementalResistance.addInPlace(SpellElement.Fire, statusEffectState.potency)
            StatusEffect.Barblizzard -> elementalResistance.addInPlace(SpellElement.Ice, statusEffectState.potency)
            StatusEffect.Baraero -> elementalResistance.addInPlace(SpellElement.Wind, statusEffectState.potency)
            StatusEffect.Barstone -> elementalResistance.addInPlace(SpellElement.Earth, statusEffectState.potency)
            StatusEffect.Barthunder -> elementalResistance.addInPlace(SpellElement.Lightning, statusEffectState.potency)
            StatusEffect.Barwater -> elementalResistance.addInPlace(SpellElement.Water, statusEffectState.potency)
            StatusEffect.Enthunder -> autoAttackEffects += AutoAttackEffect(statusEffectState.potency, AttackAddedEffectType.Lightning)
            StatusEffect.StrBoost -> multiplicativeStats.multiplyInPlace(CombatStat.str, statusEffectState.secondaryPotency)
            StatusEffect.DexBoost -> multiplicativeStats.multiplyInPlace(CombatStat.dex, statusEffectState.secondaryPotency)
            StatusEffect.VitBoost -> multiplicativeStats.multiplyInPlace(CombatStat.vit, statusEffectState.secondaryPotency)
            StatusEffect.AgiBoost -> multiplicativeStats.multiplyInPlace(CombatStat.agi, statusEffectState.secondaryPotency)
            StatusEffect.IntBoost -> multiplicativeStats.multiplyInPlace(CombatStat.int, statusEffectState.secondaryPotency)
            StatusEffect.MndBoost -> multiplicativeStats.multiplyInPlace(CombatStat.mnd, statusEffectState.secondaryPotency)
            StatusEffect.ChrBoost -> multiplicativeStats.multiplyInPlace(CombatStat.chr, statusEffectState.secondaryPotency)
            else -> {}
        }
    }

    private fun CombatBonusAggregate.handleDia(statusEffectState: StatusEffectState) {
        regen -= statusEffectState.potency
        multiplicativeStats.multiplyInPlace(CombatStat.vit, statusEffectState.secondaryPotency)
    }

    private fun CombatBonusAggregate.handleBio(statusEffectState: StatusEffectState) {
        regen -= statusEffectState.potency
        multiplicativeStats.multiplyInPlace(CombatStat.str, statusEffectState.secondaryPotency)
    }

    private fun CombatBonusAggregate.handleElementalDoT(statusEffectState: StatusEffectState, combatStat: CombatStat) {
        regen -= statusEffectState.potency
        multiplicativeStats.multiplyInPlace(combatStat, statusEffectState.secondaryPotency)
    }

    private fun CombatBonusAggregate.handleIceSpikes(statusEffectState: StatusEffectState) {
        autoAttackRetaliationEffects += RetaliationBonus(
            power = statusEffectState.potency,
            type = AttackRetaliationEffectType.IceSpikes,
            statusEffect = AttackStatusEffect(StatusEffect.Paralysis, baseDuration = 3.seconds, baseChance = statusEffectState.secondaryPotency) {
                it.statusState.potency = 50
            }
        )
    }

    private fun CombatBonusAggregate.handleShockSpikes(statusEffectState: StatusEffectState) {
        autoAttackRetaliationEffects += RetaliationBonus(
            power = statusEffectState.potency,
            type = AttackRetaliationEffectType.ShockSpikes,
            statusEffect = AttackStatusEffect(StatusEffect.Stun, baseDuration = 2.seconds, baseChance = statusEffectState.secondaryPotency)
        )
    }

    private fun CombatBonusAggregate.handleBlazeSpikes(statusEffectState: StatusEffectState) {
        autoAttackRetaliationEffects += RetaliationBonus(
            power = statusEffectState.potency,
            type = AttackRetaliationEffectType.BlazeSpikes,
        )
    }

    private fun CombatBonusAggregate.handleDelugeSpikes(statusEffectState: StatusEffectState) {
        autoAttackRetaliationEffects += RetaliationBonus(
            power = statusEffectState.potency,
            type = AttackRetaliationEffectType.WaterSpikes,
        )
    }

    private fun CombatBonusAggregate.handleDreadSpikes(statusEffectState: StatusEffectState) {
        autoAttackRetaliationEffects += RetaliationBonus(
            power = statusEffectState.potency,
            type = AttackRetaliationEffectType.DreadSpikes,
        )
    }

    private fun CombatBonusAggregate.handleMultiStrikes(statusEffectState: StatusEffectState) {
        when (statusEffectState.counter) {
            2 -> doubleAttack += statusEffectState.potency
            3 -> tripleAttack += statusEffectState.potency
            4 -> quadrupleAttack += statusEffectState.potency
        }
    }


}

