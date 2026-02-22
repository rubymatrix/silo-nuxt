package xim.poc.game.event

import xim.poc.ActorId
import xim.poc.game.*
import xim.poc.ui.ChatLog
import xim.resource.EquipSlot

sealed interface AutoAttackType {
    fun equipSlot(): EquipSlot
}

enum class AutoAttackSubType {
    None,
    H2HOffHand,
    H2HKick,
}

class MainHandAutoAttack(val subType: AutoAttackSubType = AutoAttackSubType.None): AutoAttackType {
    override fun equipSlot() = EquipSlot.Main
}

object OffHandAutoAttack: AutoAttackType {
    override fun equipSlot() = EquipSlot.Sub
}

object RangedAutoAttack: AutoAttackType {
    override fun equipSlot() = EquipSlot.Range
}

enum class AttackAddedEffectType(val contextDisplay: Int?, val damageResource: ActorResourceType) {
    Fire(1, ActorResourceType.HP),
    Ice(2, ActorResourceType.HP),
    Wind(3, ActorResourceType.HP),
    Earth(4, ActorResourceType.HP),
    Lightning(5, ActorResourceType.HP),
    Water(6, ActorResourceType.HP),
    Light(7, ActorResourceType.HP),
    Dark(8, ActorResourceType.HP),
    Poison(10, ActorResourceType.HP),
    Curse(17, ActorResourceType.HP),
    Aspir(21, ActorResourceType.MP),
    Drain(22, ActorResourceType.HP),
}

enum class AttackRetaliationEffectType(val contextDisplay: Int?, val damageResource: ActorResourceType) {
    BlazeSpikes(1, ActorResourceType.HP),
    IceSpikes(2, ActorResourceType.HP),
    DreadSpikes(3, ActorResourceType.HP),
    DarkSpikes(4, ActorResourceType.HP), // ?
    ShockSpikes(5, ActorResourceType.HP),
    Reprisal(6, ActorResourceType.HP),
    WindSpikes(7, ActorResourceType.HP),
    StoneSpikes(8, ActorResourceType.HP),
    WaterSpikes(9, ActorResourceType.HP),
    BlindSpikes(10, ActorResourceType.HP), // ?
}

class AddedEffectContext(
    val attacker: ActorState,
    val defender: ActorState,
    val context: AttackContext,
)

data class AutoAttackAddedEffect(
    val displayType: AttackAddedEffectType,
    val onProc: (AddedEffectContext) -> List<Event>,
)

data class AutoAttackRetaliationEffect(
    val damage: Int,
    val type: AttackRetaliationEffectType,
    val statusEffect: AttackStatusEffect? = null,
)

data class AutoAttackResult(
    val sourceTpGained: Int,
    val damageDone: Int,
    val criticalHit: Boolean,
    val targetId: ActorId,
    val targetTpGained: Int,
    val type: AutoAttackType,
    val addedEffects: List<AutoAttackAddedEffect> = emptyList(),
    val retaliationEffects: List<AutoAttackRetaliationEffect> = emptyList(),
)

class AutoAttackEvent(
    val sourceId: ActorId,
    val targetId: ActorId? = null,
    val rangedAttack: Boolean = false,
): Event {

    override fun apply(): List<Event> {
        val sourceState = ActorStateManager[sourceId] ?: return emptyList()
        if (sourceState.isDead()) { return emptyList() }

        val targetState = ActorStateManager[targetId ?: sourceState.targetState.targetId] ?: return emptyList()
        if (targetState.isDead()) { return emptyList() }

        if (GameEngine.checkParalyzeProc(sourceState)) {
            ChatLog.paralyzed(sourceState.name)
            return emptyList()
        }

        val outputEvents = ArrayList<Event>()

        val results = if (rangedAttack) {
            GameState.getGameMode().getRangedAttackResult(sourceState, targetState)
        } else {
            GameState.getGameMode().getAutoAttackResult(sourceState, targetState)
        }

        for (result in results) {
            outputEvents += ActorAutoAttackedEvent(sourceId = sourceId, result = result, totalAttacksInRound = results.size)
        }

        return outputEvents
    }

}