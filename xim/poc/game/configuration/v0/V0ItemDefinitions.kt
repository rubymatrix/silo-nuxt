package xim.poc.game.configuration.v0

import xim.poc.game.ActorStateManager
import xim.poc.game.CombatStats
import xim.poc.game.GameEngine.displayName
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillAppliers
import xim.poc.game.configuration.SkillFailureReason
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.ItemDefinitions.ethers
import xim.poc.game.configuration.v0.ItemDefinitions.potions
import xim.poc.game.configuration.v0.interactions.ItemId
import xim.poc.game.event.*
import xim.poc.ui.ChatLog
import xim.resource.InventoryItemType
import xim.resource.InventoryItems
import xim.resource.InventoryItems.toItemInfo
import xim.util.Fps
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

object V0ItemDefinitions {

    fun register() {
        // Stackable items
        (listOf(2892, 2893, 2894, 2895, 2896, 2897, 2898) + potions + ethers).forEach {
            InventoryItems.mutate(it) { item -> item.copy(stackSize = 999) }
        }

        // Dusty Potion/Ether aren't marked as "usable items"
        (listOf(5431, 5432)).forEach {
            InventoryItems.mutate(it) { item -> item.copy(itemType = InventoryItemType.UsableItem) }
        }

        // Medicine
        potions.forEach { itemId ->
            SkillAppliers[ItemSkillId(itemId)] = SkillApplier(validEvaluator = checkMedicated(), targetEvaluator = {
                applyMedicated(it)
                potionApplier(it)
            })
        }

        ethers.forEach { itemId ->
            SkillAppliers[ItemSkillId(itemId)] = SkillApplier(validEvaluator = checkMedicated(), targetEvaluator = {
                applyMedicated(it)
                etherApplier(it)
            })
        }

        (itemFireCluster_4104.id .. itemDarkCluster_4111.id).forEach {
            SkillAppliers[ItemSkillId(it)] = SkillApplier(targetEvaluator = gainCrystals(it - 8))
        }

        InventoryItems.mutate(itemRemedy_4155.id) { item -> item.copy(description = "This potion remedies status ailments.") }
        SkillAppliers[itemRemedy_4155] = remedyApplier(amountToRemove = 1)

        SkillAppliers[itemPanacea_4149] = remedyApplier(amountToRemove = 5)

        // Dragon Chronicles
        SkillAppliers[itemDragonChronicles_4198] = SkillApplier(targetEvaluator = {
            listOf(ActorGainExpEvent(actorId = it.sourceState.id, expAmount = 10000, actionContext = it.context))
        })

        // Icarus Wing
        SkillAppliers[itemIcarusWing_4213] = SkillApplier(validEvaluator = checkMedicated(), targetEvaluator = {
            applyMedicated(it)
            listOf(ActorHealedEvent(sourceId = it.sourceState.id, targetId = it.targetState.id, amount = 1000, actionContext = it.context, healType = ActorResourceType.TP))
        })

    }

    fun getCastTime(skill: SkillId): Duration? {
        return 0.5.seconds
    }

    private fun potionApplier(context: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        val skillId = context.skill as? ItemSkillId ?: return emptyList()
        val itemDefinition = ItemDefinitions.definitionsById[skillId.id] ?: return emptyList()

        val healAmount = CombatStats.defaultBaseStats.maxHp * GameV0Helpers.getPlayerLevelStatMultiplier(itemDefinition.internalLevel)
        return listOf(ActorHealedEvent(
            sourceId = context.sourceState.id,
            targetId = context.targetState.id,
            amount = healAmount.roundToInt(),
            actionContext = context.context
        ))
    }

    private fun etherApplier(context: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        val skillId = context.skill as? ItemSkillId ?: return emptyList()
        val itemDefinition = ItemDefinitions.definitionsById[skillId.id] ?: return emptyList()

        val healAmount = 10 + 3 * (itemDefinition.internalLevel/5)
        return listOf(ActorHealedEvent(
            sourceId = context.sourceState.id,
            targetId = context.targetState.id,
            amount = healAmount,
            healType = ActorResourceType.MP,
            actionContext = context.context
        ))
    }

    private fun remedyApplier(amountToRemove: Int): SkillApplier {
        return SkillApplier(
            validEvaluator = {
                val statusEffects = it.sourceState.getStatusEffects().any { s -> s.canEsuna || s.canErase }
                val medicated = isMedicated(it)
                if (medicated != null) { medicated } else if (!statusEffects) { SkillFailureReason.GenericFailure } else { null }
            },
            targetEvaluator = {
                applyMedicated(it)
                val status = it.targetState.getStatusEffects().firstOrNull { s -> s.canEsuna || s.canErase }
                if (status != null) { it.targetState.expireStatusEffect(status.statusEffect) }
                emptyList()
            })
    }

    private fun applyMedicated(context: SkillApplierHelper.TargetEvaluatorContext, duration: Duration = 1.minutes) {
        val statusEffect = context.targetState.gainStatusEffect(StatusEffect.Medicated, duration = duration)
        ChatLog.statusEffectGained(actorName = context.sourceState.name, statusEffect = statusEffect.statusEffect, actionContext = context.context)
    }

    private fun checkMedicated(): SkillApplierHelper.SkillUsableEvaluator {
        return SkillApplierHelper.SkillUsableEvaluator(this::isMedicated)
    }

    private fun isMedicated(context: SkillApplierHelper.SkillUsableEvaluatorContext): SkillFailureReason? {
        return if (context.sourceState.hasStatusEffect(StatusEffect.Medicated)) { SkillFailureReason.Medicated } else { null }
    }

    private fun gainCrystals(itemId: ItemId): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            listOf(InventoryGainEvent(
                sourceId = it.sourceState.id,
                itemId = itemId,
                quantity = 12,
                context = it.context,
            ))
        }
    }


    fun toDescription(skill: ItemSkillId): String {
        val player = ActorStateManager.player()
        val castTime = GameV0Helpers.getItemCastTime(player, skill)
        val baseCastTime = Fps.framesToSeconds(castTime)

        val description = skill.toItemInfo().description.prependIndent("  ").trimEnd()

        val spellName = skill.displayName()
        return "$spellName\n$description\nCast: ${baseCastTime.toString(DurationUnit.SECONDS, decimals = 1)}"
    }

}