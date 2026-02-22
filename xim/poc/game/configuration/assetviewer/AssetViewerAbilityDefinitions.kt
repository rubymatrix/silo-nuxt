package xim.poc.game.configuration.assetviewer

import xim.poc.game.ActorStateManager
import xim.poc.game.StatusEffect
import xim.poc.game.actor.components.getEquipment
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillAppliers
import xim.poc.game.configuration.SkillFailureReason
import xim.poc.game.configuration.assetviewer.AssetViewerSkillApplierHelpers.releasePet
import xim.poc.game.configuration.assetviewer.AssetViewerSkillApplierHelpers.sourceHpPercentDamage
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.*
import xim.resource.AbilityType
import xim.resource.EquipSlot
import xim.resource.table.AbilityInfoTable

object AssetViewerAbilitySkills {

    fun register() {
        registerDefaults()

        SkillAppliers[rangedAttack] = SkillApplier(
            validEvaluator = {
                val castingState = it.sourceState.getCastingState() ?: return@SkillApplier null

                val currentRanged = it.sourceState.getEquipment(EquipSlot.Range)
                val rangedMatch = currentRanged?.internalId == castingState.context.rangedItemId

                val currentAmmo = it.sourceState.getEquipment(EquipSlot.Ammo)
                val ammoMatch = currentAmmo?.internalId == castingState.context.ammoItemId

                if (rangedMatch && ammoMatch) { null } else { SkillFailureReason.GenericFailure }
            },
            targetEvaluator = {
                listOf(AutoAttackEvent(sourceId = it.sourceState.id, targetId = it.targetState.id, rangedAttack = true))
            },
        )

        // Mijin Gakure
        SkillAppliers[skillMijinGakure_540] = SkillApplier(
            targetEvaluator = sourceHpPercentDamage(PotencyConfiguration(potency = 0.5f)),
            additionalSelfEvaluator = sourceHpPercentDamage(PotencyConfiguration(potency = 1f)),
        )

        // Provoke
        SkillAppliers[skillProvoke_547] = SkillApplier(targetEvaluator = {
            listOf(BattleEngageEvent(it.targetState.id, it.sourceState.id))
        })

        // Call Wyvern
        SkillAppliers[skillCallWyvern_573] = SkillApplier(
            targetEvaluator = { listOf(PetReleaseEvent(it.sourceState.id), PetSummonEvent(it.sourceState.id, name = "Wyvern", lookId = 0x18)) }
        )

        // Assault
        SkillAppliers[skillAssault_600] = SkillApplier(targetEvaluator = {
            val petId = it.sourceState.pet
            if (petId != null) { listOf(BattleEngageEvent(petId, it.targetState.id)) } else { emptyList() }
        })

        // Retreat
        SkillAppliers[skillRetreat_601] = SkillApplier(targetEvaluator = {
            val petId = it.sourceState.pet
            if (petId != null) { listOf(BattleDisengageEvent(petId)) } else { emptyList() }
        })

        // Release
        SkillAppliers[skillRelease_602] = SkillApplier(targetEvaluator = releasePet())

        // Fighter's Roll
        SkillAppliers[skillFightersRoll_610] = makeCorsairRoll(StatusEffect.FightersRoll)

        // Double-up
        SkillAppliers[skillDoubleUp_635] =  makeCorsairDoubleUp()
    }

    private fun registerDefaults() {
        for (i in 0 until AbilityInfoTable.getAbilityCount()) {
            val info = AbilityInfoTable[i]

            SkillAppliers[AbilitySkillId(i)] = if (info.type == AbilityType.WeaponSkill) {
                weaponSkillApplier()
            } else if (info.type == AbilityType.PetAbility || info.type == AbilityType.PetWard) {
                SkillApplierHelper.makeApplier(targetEvaluator = { makePetAbilityApplier(it) })
            } else {
                SkillApplierHelper.makeApplier(targetEvaluator = SkillApplierHelper.TargetEvaluator.noop())
            }
        }
    }

    private fun makeCorsairRoll(statusEffect: StatusEffect): SkillApplier {
        return SkillApplier(
            validEvaluator = {
                if (it.sourceState.getStatusEffect(StatusEffect.DoubleUpChance) != null) { SkillFailureReason.GenericFailure } else { null }
            },
            primaryTargetEvaluator = {
                listOf(CorsairRollEvent(abilityId = it.skill, status = statusEffect, sourceId = it.sourceState.id, context = it.context)) },
            targetEvaluator = {
                listOf(CorsairRollTargetEvent(statusEffect = statusEffect, rollerId = it.sourceState.id, targetId = it.targetState.id, context = it.context))
            })
    }

    private fun makeCorsairDoubleUp(): SkillApplier {
        return SkillApplier(
            validEvaluator = {
                if (it.sourceState.getStatusEffect(StatusEffect.DoubleUpChance) == null) { SkillFailureReason.GenericFailure } else { null }
            },
            primaryTargetEvaluator = {
                val doubleUpState = it.sourceState.getStatusEffect(StatusEffect.DoubleUpChance) ?: return@SkillApplier emptyList()
                val linkedStatus = doubleUpState.linkedStatus ?: return@SkillApplier emptyList()
                listOf(CorsairRollEvent(abilityId = it.skill, status = linkedStatus, sourceId = it.sourceState.id, context = it.context)) },
            targetEvaluator = {
                val doubleUpState = it.sourceState.getStatusEffect(StatusEffect.DoubleUpChance) ?: return@SkillApplier emptyList()
                val linkedStatus = doubleUpState.linkedStatus ?: return@SkillApplier emptyList()
                listOf(CorsairRollTargetEvent(statusEffect = linkedStatus, rollerId = it.sourceState.id, targetId = it.targetState.id, context = it.context))
            })
    }

    private fun weaponSkillApplier(): SkillApplier {
        return SkillApplier(
            targetEvaluator = {
                listOf(
                    ActorAttackedEvent(
                        sourceId = it.sourceState.id,
                        targetId = it.targetState.id,
                        damageAmount = listOf(3),
                        damageType = AttackDamageType.Physical,
                        actionContext = it.context,
                        skill = it.skill,
                    ),
                )
            }
        )
    }

    private fun makePetAbilityApplier(it: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        val pet = ActorStateManager[it.sourceState.pet] ?: return emptyList()
        return listOf(CastAbilityStart(
            sourceId = pet.id,
            targetId = it.targetState.id,
            skill = it.skill,
        ))
    }

}