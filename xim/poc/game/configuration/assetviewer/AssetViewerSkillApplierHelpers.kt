package xim.poc.game.configuration.assetviewer

import xim.poc.game.AttackContext
import xim.poc.game.GameEngine
import xim.poc.game.GameEngine.displayName
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplierHelper.TargetEvaluator
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.*
import xim.poc.ui.ChatLog
import xim.resource.DatId
import xim.resource.SpellElement
import xim.resource.SpellInfo
import xim.resource.table.SpellInfoTable.toSpellInfo
import kotlin.math.roundToInt
import kotlin.time.Duration

data class PotencyConfiguration(val potency: Float)

object AssetViewerSkillApplierHelpers {

    fun basicElementDamage(config: PotencyConfiguration): TargetEvaluator {
        return TargetEvaluator {
            val element = when (it.skill) {
                is SpellSkillId -> it.skill.toSpellInfo().element
                else -> SpellElement.None
            }

            val burst = it.primaryTargetState.skillChainTargetState.canMagicBurst(element)
            val magicBurstBonus = if (burst) { it.context.magicBurst = true; 2f } else { 1f }

            val damageAmount = listOf((5 * config.potency * magicBurstBonus).roundToInt())
            val outputEvents = ActorAttackedEvent(sourceId = it.sourceState.id, targetId = it.targetState.id, damageAmount = damageAmount, damageType = AttackDamageType.Magical, skill = it.skill, actionContext = it.context)
            listOf(outputEvents)
        }
    }

    fun basicHealingMagic(config: PotencyConfiguration): TargetEvaluator {
        return TargetEvaluator {
            val healAmount = (5 * config.potency).roundToInt()
            val outputEvents = ActorHealedEvent(sourceId = it.sourceState.id, targetId = it.targetState.id, amount = healAmount, actionContext = it.context)
            listOf(outputEvents)
        }
    }

    fun sourceHpPercentDamage(config: PotencyConfiguration): TargetEvaluator {
        return TargetEvaluator {
            val damageAmount = listOf((it.sourceState.getHp() * config.potency).roundToInt())
            val outputEvents = ActorAttackedEvent(sourceId = it.sourceState.id, targetId = it.targetState.id, damageAmount = damageAmount, damageType = AttackDamageType.Static, skill = it.skill, actionContext = it.context)
            listOf(outputEvents)
        }
    }

    fun basicEnhancingMagicStatusEffect(status: StatusEffect, baseDuration: Duration? = null): TargetEvaluator {
        return TargetEvaluator {
            it.sourceState.gainStatusEffect(status, baseDuration)
            AttackContext.compose(it.context) { ChatLog.statusEffectGained(it.sourceState.name, status, it.context) }
            emptyList()
        }
    }

    fun summonLuopan(spellInfo: SpellInfo): TargetEvaluator {
        return TargetEvaluator {
            val offenseOffset = if (it.targetState.isEnemy()) { 0x8 } else { 0x0 }
            val lookId = 0xB22 + spellInfo.element.index + offenseOffset
            listOf(
                PetReleaseEvent(it.sourceState.id),
                PetSummonEvent(it.sourceState.id, lookId = lookId, name = "Luopan", entranceAnimation = DatId.pop, stationary = true),
            )
        }
    }

    fun summonBubble(spellInfo: SpellInfo): TargetEvaluator {
        return TargetEvaluator {
            listOf(BubbleReleaseEvent(it.sourceState.id), BubbleGainEvent(it.sourceState.id, spellInfo.index),)
        }
    }

    fun summonPet(lookId: Int): TargetEvaluator {
        return TargetEvaluator {
            val name = it.skill.displayName()
            listOf(PetReleaseEvent(it.sourceState.id), PetSummonEvent(it.sourceState.id, name = name, lookId = lookId))
        }
    }

    fun releasePet(): TargetEvaluator {
        return TargetEvaluator {
            listOf(PetReleaseEvent(it.sourceState.id))
        }
    }

}