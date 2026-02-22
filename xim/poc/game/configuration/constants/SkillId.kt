package xim.poc.game.configuration.constants

import kotlinx.serialization.Serializable

sealed interface SkillId

@Serializable
value class SpellSkillId(val id: Int): SkillId

value class AbilitySkillId(val id: Int): SkillId

value class MobSkillId(val id: Int): SkillId

value class ItemSkillId(val id: Int): SkillId

value class RangedAttackSkillId(val id: Int): SkillId