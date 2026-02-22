package xim.poc.game.configuration

import kotlinx.serialization.Serializable
import xim.math.Vector3f
import xim.poc.*
import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.CombatStats
import xim.poc.game.actor.components.SlottedAugment
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.SpellSkillId
import xim.poc.game.configuration.v0.ItemRankSettings
import xim.poc.gl.ByteColor
import xim.resource.Blur
import xim.resource.BlurConfig
import xim.resource.MagicType
import xim.resource.table.SpellInfoTable.toSpellInfo

@Serializable
value class MonsterId(val id: Int)

fun standardBlurConfig(color: ByteColor, radius: Float = 70f, blurs: Int = 5): BlurConfig {
    val configs = ArrayList<Blur>()
    repeat(blurs) { configs += Blur(radius, color) }
    return BlurConfig(configs)
}

enum class MonsterAggroType {
    Sight,
    Sound,
    Magic,
}

data class MonsterAggroSetting(val type: MonsterAggroType, val range: Float, val enabled: (ActorState, ActorState) -> Boolean = { _, _ -> true })

data class MonsterAggroConfig(val settings: List<MonsterAggroSetting> = emptyList()) {
    companion object {

        val none = MonsterAggroConfig()

        val standardSightAggro = MonsterAggroConfig(listOf(
            MonsterAggroSetting(type =  MonsterAggroType.Sight, range = 10f)
        ))

        val extendedSightAggro = MonsterAggroConfig(listOf(
            MonsterAggroSetting(type =  MonsterAggroType.Sight, range = 20f)
        ))

        val standardSoundAggro = MonsterAggroConfig(listOf(
            MonsterAggroSetting(type =  MonsterAggroType.Sound, range = 6f)
        ))

        val extendedSoundAggro = MonsterAggroConfig(listOf(
            MonsterAggroSetting(type =  MonsterAggroType.Sound, range = 12f)
        ))

        val standardMagicAggro = MonsterAggroConfig(listOf(
            MonsterAggroSetting(type =  MonsterAggroType.Magic, range = 15f)
        ))

        fun compose(c0: MonsterAggroConfig, c1: MonsterAggroConfig): MonsterAggroConfig {
            return MonsterAggroConfig(c0.settings + c1.settings)
        }
    }

}

data class ItemDropSlot(
    val itemId: Int?,
    val quantity: Int = 1,
    val temporary: Boolean = false,
    val slottedAugment: SlottedAugment? = null,
    val rankSettings: ItemRankSettings? = null,
) {
    companion object {
        val none: ItemDropSlot = ItemDropSlot(itemId = null)
    }
}

data class MonsterDefinition(
    val id: MonsterId,
    val name: String,
    val look: ModelLook,
    val behaviorId: BehaviorId,
    val movementControllerFn: () -> ActorController = { DefaultEnemyController() },
    val staticPosition: Boolean = false,
    val facesTarget: Boolean = true,
    val targetSize: Float = 0f,
    val autoAttackRange: Float = 4.5f,
    val targetable: Boolean = true,
    val mobSkills: List<SkillId> = emptyList(),
    val mobSpells: List<SpellSkillId> = emptyList(),
    val autoAttackSkills: List<SkillId> = emptyList(),
    val customModelSettings: CustomModelSettings = CustomModelSettings(),
    val baseLevel: Int = 1,
    val baseDamage: Int = 3,
    val baseDelay: Int = 180,
    val baseCombatStats: CombatStats = CombatStats(20, 20, 5, 5, 5, 5, 5, 5, 5),
    val notoriousMonster: Boolean = false,
    val baseAppearanceState: Int = 0,
    val aggroConfig: MonsterAggroConfig = MonsterAggroConfig.none,
    val expRewardScale: Float = 1f,
    val rpRewardScale: Float? = null,
    val baseBonusApplier: (CombatBonusAggregate) -> Unit = { },
    val engageMusicId: Int? = null,
    val onSpawn: ((ActorState) -> Unit)? = null
)

class MonsterInstance(val monsterDefinition: MonsterDefinition, val actorId: ActorId)

object MonsterDefinitions {

    private val definitions = HashMap<MonsterId, MonsterDefinition>()

    operator fun get(monsterId: MonsterId?): MonsterDefinition? {
        monsterId ?: return null
        return get(monsterId)
    }

    operator fun get(monsterId: MonsterId): MonsterDefinition {
        return definitions[monsterId] ?: throw IllegalStateException("[$monsterId] Undefined monster")
    }

    operator fun set(monsterId: MonsterId, monsterDefinition: MonsterDefinition) {
        if (definitions.containsKey(monsterId)) { throw IllegalStateException("Monster definition $monsterId is already defined by ${definitions[monsterId]}") }
        definitions[monsterId] = monsterDefinition
    }

    fun registerAll(definitions: List<MonsterDefinition>) {
        definitions.forEach { set(it.id, it) }
    }

}

object MonsterAggroHelper {

    fun getAggroTarget(source: ActorState): ActorId? {
        if (source.isEngaged() || source.monsterId == null) { return null }

        val definition = MonsterDefinitions[source.monsterId]

        val config = definition.aggroConfig
        if (config.settings.isEmpty()) { return null }

        if (source.behaviorController is ActorMonsterController && !source.behaviorController.shouldPerformAggroCheck()) {
            return null
        }

        for (setting in config.settings) {
            val nearbyEnemies = ActorStateManager.getNearbyActors(source.position, maxDistance = setting.range)
                .filter { !it.isDead() && ActionTargetFilter.areEnemies(source, it) }

            val nearestValidEnemy = when (setting.type) {
                MonsterAggroType.Sight -> nearbyEnemies.filter { source.isFacingTowards(it) }
                MonsterAggroType.Sound -> nearbyEnemies
                MonsterAggroType.Magic -> nearbyEnemies.filter { checkMagicAggro(it) }
            }.minByOrNull { Vector3f.distance(source.position, it.position) }

            if (nearestValidEnemy != null) {
                return nearestValidEnemy.id
            }
        }

        return null
    }

    private fun checkMagicAggro(targetState: ActorState): Boolean {
        val skill = targetState.getCastingState()?.skill ?: return false
        if (skill !is SpellSkillId) { return false }

        return when (skill.toSpellInfo().magicType) {
            MagicType.WhiteMagic -> true
            MagicType.BlackMagic -> true
            MagicType.Summoning -> true
            MagicType.BlueMagic -> true
            else -> false
        }
    }

}